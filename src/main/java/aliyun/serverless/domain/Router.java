package aliyun.serverless.domain;

import aliyun.serverless.ResourceManagerClient;
import aliyun.serverless.core.CreateContainerTask;
import aliyun.serverless.core.FunctionStatistic;
import aliyun.serverless.core.RemoveContainerTask;
import aliyun.serverless.core.SchedulerParams;
import nodeservoceproto.NodeServiceOuterClass.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resourcemanagerproto.ResourceManagerOuterClass;
import resourcemanagerproto.ResourceManagerOuterClass.NodeDesc;
import resourcemanagerproto.ResourceManagerOuterClass.ReserveNodeReply;
import resourcemanagerproto.ResourceManagerOuterClass.ReserveNodeRequest;
import schedulerproto.SchedulerOuterClass.AcquireContainerReply;
import schedulerproto.SchedulerOuterClass.AcquireContainerRequest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Router {
    private static final Logger logger = LoggerFactory.getLogger(Router.class);

    public ConcurrentHashMap<String, Long> requestCostTimeMap;
    public ConcurrentHashMap<String,Nodeinfo> nodeMap;
    public ConcurrentHashMap<String,ConcurrentHashMap<String,ContainerInfo>> functionMap;
    public ConcurrentHashMap<String, BlockingQueue<ContainerInfo>> funAvaContainerSetMap;

    public ConcurrentHashMap<String,String> requestMap;
    public ResourceManagerClient rmClient;

    public ConcurrentHashMap<String, FunctionStatistic> statisticMap;
    private ScheduledExecutorService ses;
    private ScheduledExecutorService containerNodeSes;

    private ExecutorService createContainerService;
    private ExecutorService removeContainerService;
    private volatile boolean removeNodeFlag;


    private AtomicInteger createContainerNumByReq;
    private AtomicInteger hasContainerReqNum;

    private volatile int loopNumber;
    private volatile int loopNumber2;
    private volatile long lastRemoveNodeMs;
    public Router(ResourceManagerClient rmClient) {
        this.rmClient = rmClient;
        this.removeNodeFlag=false;
        createContainerNumByReq = new AtomicInteger(0);
        hasContainerReqNum = new AtomicInteger(0);
        lastRemoveNodeMs=System.currentTimeMillis();
        requestCostTimeMap = new ConcurrentHashMap<>();
        nodeMap = new ConcurrentHashMap();
        functionMap = new ConcurrentHashMap();
        requestMap = new ConcurrentHashMap();
        statisticMap = new ConcurrentHashMap<>();
        loopNumber=0;
        ses = Executors.newSingleThreadScheduledExecutor();
        containerNodeSes = Executors.newSingleThreadScheduledExecutor();


        removeContainerService=new ThreadPoolExecutor(
                5, 10, 1000, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),Executors.defaultThreadFactory(),new ThreadPoolExecutor.AbortPolicy());

        createContainerService=new ThreadPoolExecutor(
                5, 50, 1000, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),Executors.defaultThreadFactory(),new ThreadPoolExecutor.AbortPolicy());
        ses.scheduleAtFixedRate(() -> {
            logger.info("******schedule task--{}******",++loopNumber);
            try {
                logger.info("由请求创建容器的数量为{},已有准备好容器的请求数为{}",createContainerNumByReq,hasContainerReqNum);

                statisticMap.entrySet().stream().forEach(staticMap -> {
                    FunctionStatistic fs=staticMap.getValue();
                    fs.swc.advance();
                    fs.lastNumber=fs.preNumber;
                    fs.preNumber = staticMap.getValue().swc.totalCount();
                    //先收集上个周期的请求数
                    fs.functionFeature.collectData(fs.preNumber);

                    if (fs.functionFeature.createContainerLoopNum == fs.functionFeature.loopNum&&fs.functionFeature.requestType==RequestType.CYCLE) {
                        ConcurrentHashMap<String,ContainerInfo> fmObj = functionMap.get(fs.functionName);
                        logger.info("function-Create={},异步创建container,number={}",fs.functionName,fs.functionFeature.requestNumSecond);
                        int toCreateContainer=fs.functionFeature.requestNumSecond-fmObj.keySet().size();
                        logger.info("function={},已有容器数{},需要创建的容器数量{}",fs.functionName,functionMap.get(fs.functionName).keySet().size(),toCreateContainer);
                        for (int i = 0; i < toCreateContainer; i++) {
                            createContainerService.execute(new CreateContainerTask(this,fs));
                        }
                    }

                    fs.functionFeature.analyseData(fs.createContainerTime,fs.functionExecTime);

                    if (fs.functionFeature.isRemoveContainerImmediately) {
                        fs.functionFeature.isRemoveContainerImmediately=false;
                        logger.info("立刻移除当前function:{}的container",fs.functionName);
                        removeContainerService.execute(new RemoveContainerTask(this,fs));
                    }
                });

            } catch (Exception e) {
                System.out.println("计算出错" + e.getMessage());
            }
        }, 0, SchedulerParams.LOOP_TIME, TimeUnit.MILLISECONDS);

        containerNodeSes.scheduleAtFixedRate(() -> {
            logger.info("******container node task--{}******",++loopNumber2);
            try {long avaMemoryTotal=0;
                long memoryTotal=0;
                long avaDiskTotal=0;
                long diskTotal=0;

                for (Nodeinfo nodeinfo : nodeMap.values()) {
                    GetStatsReply statsReply = nodeinfo.nodeClient.getStats(GetStatsRequest.newBuilder().setRequestId(nodeinfo.nodeId).build());
                    if ((loopNumber2 - 60) % 30 == 0) {
                        logger.info("nodeInfo[nodeid={},cpuUsage:{},avaMemory:{},avaDisk:{},diskTotalInodes:{}]",nodeinfo.nodeId,statsReply.getNodeStats().getCpuUsagePct(),
                                statsReply.getNodeStats().getAvailableMemoryInBytes(),statsReply.getNodeStats().getDiskAvailInBytes(),
                                statsReply.getNodeStats().getDiskTotalInodes());
                        List<ContainerStats> containerStatsList = statsReply.getContainerStatsListList();
                        if (containerStatsList != null && !containerStatsList.isEmpty()) {
                            for (ContainerStats stats : containerStatsList) {
                                logger.info("containid={},cpuUsagePct={}",stats.getContainerId(),stats.getCpuUsagePct());
                            }
                        }
                    }
                    avaMemoryTotal +=statsReply.getNodeStats().getAvailableMemoryInBytes();
                    memoryTotal+=statsReply.getNodeStats().getTotalMemoryInBytes();
                    avaDiskTotal+=statsReply.getNodeStats().getDiskAvailInBytes();
                    diskTotal += statsReply.getNodeStats().getDiskTotalInBytes();
                }

                if (!nodeMap.isEmpty()) {
                    logger.info("memoryUseRate={},diskUsrRate={}",(double)avaMemoryTotal/memoryTotal,(double)avaDiskTotal/diskTotal);
                    if ((double) avaMemoryTotal / memoryTotal < 0.75) {
                        logger.info("resource useRate low,remove empty node");
//                        shiftContainer();
                        removeEmptyNode();
                    }
                }

            } catch (Exception e) {
                System.out.println("containerNodeSes 计算出错" + e.getMessage());
            }
        }, 0, SchedulerParams.LOOP_TIME, TimeUnit.MILLISECONDS);


    }

    public AcquireContainerReply acquireContainer(AcquireContainerRequest request) {
        ContainerInfo res=null;
        RequestMetrics requestMetrics=new RequestMetrics(request.getFunctionName(),request.getRequestId());
        requestMetrics.setAcquireContainerTime();
        requestMap.put(request.getRequestId(), request.getFunctionName());
        functionMap.putIfAbsent(request.getFunctionName(), new ConcurrentHashMap());
        statisticMap.putIfAbsent(request.getFunctionName(), new FunctionStatistic(request));
        funAvaContainerSetMap.putIfAbsent(request.getFunctionName(), new LinkedBlockingQueue<>());

        statisticMap.get(request.getFunctionName()).swc.increase();
        ConcurrentHashMap<String,ContainerInfo> fmObj = functionMap.get(request.getFunctionName());
        BlockingQueue<ContainerInfo> containerQueue = funAvaContainerSetMap.get(request.getFunctionName());

        res=containerQueue.poll();

        if (res == null) {
            Nodeinfo node = getNode(request.getAccountId(), request.getFunctionConfig().getMemoryInBytes(),requestMetrics);
            long createNodeBegin=System.currentTimeMillis();
            requestMetrics.setIsCreateContainer(true);
            CreateContainerReply replyc=node.nodeClient.createContainer(CreateContainerRequest.newBuilder()
                            .setName(request.getFunctionName()+System.currentTimeMillis()+ new Random(8).nextLong())
                    .setFunctionMeta(FunctionMeta.newBuilder()
                            .setFunctionName(request.getFunctionName())
                            .setHandler(request.getFunctionConfig().getHandler())
                            .setTimeoutInMs(request.getFunctionConfig().getTimeoutInMs())
                            .setMemoryInBytes(request.getFunctionConfig().getMemoryInBytes())
                            .build())
                    .setRequestId(request.getRequestId())
                    .build());
            requestMetrics.createContainerCostTime=System.currentTimeMillis()-createNodeBegin;
            node.nodeFunctionNum.get(request.getFunctionName()).incrementAndGet();
            res=new ContainerInfo(replyc.getContainerId(),
                    node.address,node.port,node.nodeId,new HashMap<String, Integer>(),request.getFunctionConfig().getMemoryInBytes(),request.getFunctionName());
            res.requests.put(request.getRequestId(), 1);
            createContainerNumByReq.getAndAdd(1);
            fmObj.put(res.id, res);
        }else {
            if (statisticMap.get(request.getFunctionName()).functionFeature.requestType == RequestType.CYCLE) {
                logger.error("request not warm,current loop={},function={}",statisticMap.get(request.getFunctionName()).functionFeature.loopNum,
                        request.getFunctionName());
            }
        }
        requestMetrics.setAcquireContainerBackTime();
        statisticMap.get(request.getFunctionName()).requestMetricsMap.put(request.getRequestId(),requestMetrics);
        nodeMap.get(res.nodeId).containerSet.add(res.id);
        return AcquireContainerReply.newBuilder()
                .setNodeId(res.nodeId)
                .setNodeAddress(res.address)
                .setNodeServicePort(res.port)
                .setContainerId(res.id).build();


    }
    private Nodeinfo getNode(String accountId,long memoryReq,RequestMetrics requestMetrics){
        for (Nodeinfo ndi : nodeMap.values()) {
            ndi.nodeFunctionNum.putIfAbsent(requestMetrics.functionName, new AtomicInteger(0));
        }
        List<Nodeinfo> nodeInfos=new ArrayList<>(nodeMap.values());
        Collections.sort(nodeInfos, new Comparator<Nodeinfo>() {
            @Override
            public int compare(Nodeinfo o1, Nodeinfo o2) {
                if (o1.nodeFunctionNum.get(requestMetrics.functionName).get() != o2.nodeFunctionNum.get(requestMetrics.functionName).get()) {
                    return o1.nodeFunctionNum.get(requestMetrics.functionName).get() - o2.nodeFunctionNum.get(requestMetrics.functionName).get();
                }else {
                    return o1.availableMemInBytes<o2.availableMemInBytes?1:-1;
                }
            }
        });
        for (Nodeinfo ni : nodeInfos) {
            Nodeinfo nodeinfo = nodeMap.get(ni.nodeId);
            try {
                if (nodeinfo.tryLock(20, TimeUnit.MILLISECONDS)) {
                    if (nodeinfo.availableMemInBytes > memoryReq) {
                        nodeinfo.availableMemInBytes-=memoryReq;
                        nodeinfo.unlock();
                        requestMetrics.setIsCreateNode(false);
                        return nodeinfo;
                    }
                    nodeinfo.unlock();
                }
            } catch (InterruptedException e) {
                logger.info(e.getMessage());
            }

        }
        requestMetrics.setIsCreateNode(true);
        long createNodeBegin=System.currentTimeMillis();
        ReserveNodeReply reply = rmClient.reserveNode(ReserveNodeRequest.newBuilder().setAccountId(accountId).build());
        NodeDesc nodeDesc = reply.getNode();
        Nodeinfo nodeinfo = newNode(nodeDesc.getId(), nodeDesc.getAddress(), nodeDesc.getNodeServicePort(), nodeDesc.getMemoryInBytes());
        logger.info("new node,id={},memory={}",nodeinfo.nodeId,nodeinfo.availableMemInBytes);
        nodeMap.put(nodeDesc.getId(), nodeinfo);
        requestMetrics.createNodeCostTime=System.currentTimeMillis()-createNodeBegin;
        nodeinfo.lock();
        if (nodeinfo.availableMemInBytes > memoryReq) {
            nodeinfo.availableMemInBytes-=memoryReq;
            nodeinfo.unlock();
            return nodeinfo;
        }
        nodeinfo.unlock();
        return nodeinfo;

    }

    private Nodeinfo newNode(String nodeId, String address,long port,long memory) {
        try {
            Nodeinfo nodeinfo = new Nodeinfo(nodeId,address,memory,(int)port);
            return nodeinfo;
        } catch (Exception e) {
            logger.info("create node {} error :",nodeId);
            return null;
        }
    }
    public void ReturnContainer(ResponseInfo res) {

        String functionName=requestMap.get(res.id);

        FunctionStatistic functionStatistic = statisticMap.get(functionName);

        RequestMetrics requestMetrics= functionStatistic.requestMetricsMap.get(res.id);
        if (requestMetrics != null) {
            requestMetrics.setReturnContainerTime();
            requestMetrics.computeReqExecCostTime();
            functionStatistic.functionExecTime=requestMetrics.reqExecCostTime;
            if (requestMetrics.isCreateContainer) {
                functionStatistic.createContainerTime=requestMetrics.createContainerCostTime;
            }
        }
        ConcurrentHashMap<String,ContainerInfo> ctaMap = functionMap.get(functionName);
        if (ctaMap == null) {
            logger.info("error containerMap is null functionName={}*****************",functionName);
            return;
        }
        ContainerInfo containerInfo = ctaMap.get(res.containerId);
        if (containerInfo == null) {
            logger.info("error container is null functionName={}*****************",functionName);
            return;
        }
        if (functionStatistic.functionFeature.loopNum>5&&functionStatistic.functionFeature.isReturnRemoveContainer) {
            logger.info("return container then remove it,function={}",functionName);
            removeContainer(containerInfo,functionStatistic.functionMemoryInBytes);
            ctaMap.remove(res.containerId);
        }else {
            funAvaContainerSetMap.get(functionName).offer(containerInfo);
        }
        requestMap.remove(res.id);
        if(functionStatistic.functionFeature.loopNum>5&&functionStatistic.functionFeature.isReturnRemoveContainer){
            List<ContainerInfo> otherList = new ArrayList<>();
            funAvaContainerSetMap.get(functionName).drainTo(otherList);
            for(ContainerInfo other:otherList) {
                removeOtherEmptyContainer(other,ctaMap,functionStatistic.functionMemoryInBytes);
            }

        }
    }

    public void removeOtherEmptyContainer(ContainerInfo otherContainer,ConcurrentHashMap<String, ContainerInfo> ctaMap, long functionMemory) {
            removeContainer(otherContainer,functionMemory);
            ctaMap.remove(otherContainer.id);

    }

    private void removeContainer(ContainerInfo containerInfo,long functionMemoryInBytes){
        Nodeinfo nodeinfo = nodeMap.get(containerInfo.nodeId);
        RemoveContainerReply removeContainerReply=nodeinfo.nodeClient.removeContainer(RemoveContainerRequest.newBuilder().setContainerId(containerInfo.id).build());
        nodeinfo.lock();
        nodeinfo.availableMemInBytes+=functionMemoryInBytes;
        nodeinfo.containerSet.remove(containerInfo.id);
        nodeinfo.nodeFunctionNum.get(containerInfo.functionName).decrementAndGet();
        nodeinfo.unlock();
    }

    private Set<String> getCopySet(Set keySet) {
        Set copy = new TreeSet();
        copy.addAll(keySet);
        return copy;
    }

//    private void shiftContainer() {
//        List<Nodeinfo> nodeinfos=nodeMap.values().stream().sorted(Comparator.comparingLong(Nodeinfo::getAvailableMemInBytes).reversed())
//                .collect(Collectors.toList());
//        Nodeinfo nodeinfo = nodeinfos.get(0);
//        for (String containId : nodeinfo.containerSet) {
//            for (ConcurrentHashMap<String, ContainerInfo> map : functionMap.values()) {
//                if (map.containsKey(containId)) {
//                    ContainerInfo containerInfo = map.get(containId);
//                    containerInfo.lock();
//                    boolean isRemoveContainer=removeContainer(containerInfo, containerInfo.memoryInBytes);
//                    if (isRemoveContainer) {
//                        functionMap.get(containerInfo.functionName).remove(containerInfo.id);
//                    }
//                    containerInfo.unlock();
//                }
//            }
//        }
//
//    }
    private void removeEmptyNode() {
        for (String nodeId:getCopySet(nodeMap.keySet())) {
            Nodeinfo nodeinfo=nodeMap.get(nodeId);
            try {
                if (nodeinfo.tryLock(20,TimeUnit.MILLISECONDS)) {
                    try {
                        logger.info("node={},avaMemory={},totalMemory={},isEquqls={}",nodeinfo.nodeId,nodeinfo.availableMemInBytes,nodeinfo.totalMemory,nodeinfo.totalMemory==nodeinfo.availableMemInBytes);
                        if (nodeinfo.availableMemInBytes == nodeinfo.totalMemory) {
                            logger.info("node {}没有被使用,",nodeinfo.nodeId);
                            GetStatsReply statsReply = nodeinfo.nodeClient.getStats(GetStatsRequest.newBuilder().setRequestId(nodeId).build());
                            if(statsReply.getContainerStatsListCount()==0){
                                nodeMap.remove(nodeId);
                                rmClient.releaseNode(ResourceManagerOuterClass.ReleaseNodeRequest.newBuilder().
                                        setId(nodeId).setRequestId(String.valueOf(new Random(1000).nextLong())).build());
                                logger.info("获取nodes {} 上的容器数为0,remove it",nodeinfo.nodeId);
                                lastRemoveNodeMs=System.currentTimeMillis();
                            }
                        }
                    }catch (Exception e){
                        logger.error("get node stats exception");
                    }finally {
                        nodeinfo.unlock();
                    }
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }

    }

}
