package aliyun.serverless.core;

import aliyun.serverless.domain.ContainerInfo;
import aliyun.serverless.domain.Nodeinfo;
import aliyun.serverless.domain.Router;
import nodeservoceproto.NodeServiceOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resourcemanagerproto.ResourceManagerOuterClass;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateContainerTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CreateContainerTask.class);


    public Router router;
    public FunctionStatistic functionStatistic;

    public CreateContainerTask(Router router,FunctionStatistic functionStatistic) {
        this.router = router;
        this.functionStatistic=functionStatistic;
    }

    @Override
    public void run() {

        Nodeinfo node = getNode(functionStatistic.accountId, functionStatistic.functionMemoryInBytes);

        ConcurrentHashMap<String,ContainerInfo> fmObj = router.functionMap.get(functionStatistic.functionName);

        NodeServiceOuterClass.CreateContainerReply replyc=node.nodeClient.createContainer(NodeServiceOuterClass.CreateContainerRequest.newBuilder()
                .setName(functionStatistic.functionName+System.currentTimeMillis()+ new Random(8).nextLong())
                .setFunctionMeta(NodeServiceOuterClass.FunctionMeta.newBuilder()
                        .setFunctionName(functionStatistic.functionName)
                        .setHandler(functionStatistic.handle)
                        .setTimeoutInMs(functionStatistic.timeoutInMs)
                        .setMemoryInBytes(functionStatistic.functionMemoryInBytes)
                        .build())
                .setRequestId("createContainer-"+System.currentTimeMillis()+ new Random(8).nextLong())
                .build());
        ContainerInfo res=new ContainerInfo(replyc.getContainerId(),
                node.address,node.port,node.nodeId,new HashMap<String, Integer>(),functionStatistic.functionMemoryInBytes,functionStatistic.functionName);
//        res.requests.put(request.getRequestId(), 1);
        router.funAvaContainerSetMap.get(functionStatistic.functionName).offer(res);
        node.nodeFunctionNum.get(functionStatistic.functionName).incrementAndGet();
        fmObj.put(res.id, res);
    }
    private Nodeinfo getNode(String accountId, long memoryReq){
        router.nodeMap.entrySet().stream().forEach(entry->{
            entry.getValue().nodeFunctionNum.putIfAbsent(functionStatistic.functionName, new AtomicInteger(0));
        });
        List<Nodeinfo> nodeInfos=new ArrayList<>(router.nodeMap.values());

        Collections.sort(nodeInfos, new Comparator<Nodeinfo>() {
            @Override
            public int compare(Nodeinfo o1, Nodeinfo o2) {
                if (o1.nodeFunctionNum.get(functionStatistic.functionName).get() != o2.nodeFunctionNum.get(functionStatistic.functionName).get()) {
                    return o1.nodeFunctionNum.get(functionStatistic.functionName).get() - o2.nodeFunctionNum.get(functionStatistic.functionName).get();
                }else {
                    return o1.availableMemInBytes>o2.availableMemInBytes?1:-1;
                }
            }
        });
        for (Nodeinfo ni : nodeInfos) {
            Nodeinfo nodeinfo = router.nodeMap.get(ni.nodeId);
            try {
                if (nodeinfo.tryLock(20, TimeUnit.MILLISECONDS)) {
                    if (nodeinfo.availableMemInBytes > memoryReq) {
                        nodeinfo.availableMemInBytes-=memoryReq;
                        nodeinfo.unlock();
                        return nodeinfo;
                    }
                    nodeinfo.unlock();
                }
            } catch (InterruptedException e) {
                logger.info(e.getMessage());
            }
        }
        ResourceManagerOuterClass.ReserveNodeReply reply = router.rmClient.reserveNode(ResourceManagerOuterClass.ReserveNodeRequest.newBuilder().setAccountId(accountId).build());
        ResourceManagerOuterClass.NodeDesc nodeDesc = reply.getNode();
        Nodeinfo nodeinfo = newNode(nodeDesc.getId(), nodeDesc.getAddress(), nodeDesc.getNodeServicePort(), nodeDesc.getMemoryInBytes());
        logger.info("new node,id={},memory={}",nodeinfo.nodeId,nodeinfo.availableMemInBytes);
        router.nodeMap.put(nodeDesc.getId(), nodeinfo);
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
}
