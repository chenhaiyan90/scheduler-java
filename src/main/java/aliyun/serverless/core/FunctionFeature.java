package aliyun.serverless.core;

import aliyun.serverless.domain.RequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FunctionFeature {
    private static final Logger logger = LoggerFactory.getLogger(FunctionFeature.class);

    public String functionName;
    public RequestType requestType;
    public RequestType emptyType;

    public int preReqNum;

    List<Integer> reqNumList;//有请求数的集合
    public int noRequestCycleNum;//当前没请求数的次数
    List<Integer> noRequestCycleNumList;//没请求数的周期次数

    public volatile int loopNum;//开始计算的次数

    public int cycleTep;//没请求时的步长
    public int requestNumSecond;//有请求时每秒的请求数

    public int[] cycleArr;

    public volatile boolean isReturnRemoveContainer;

    public volatile boolean isRemoveContainerImmediately;

    public int createContainerLoopNum;

    public FunctionFeature(String functionName) {
        this.functionName=functionName;
        this.loopNum=1;
        this.reqNumList = new ArrayList<>();
        this.noRequestCycleNum = 0;
        this.noRequestCycleNumList = new ArrayList<>();
        this.isReturnRemoveContainer=false;
        this.isRemoveContainerImmediately=false;
    }
    public void collectData(int reqNum) {
        if (reqNum != 0) {
            reqNumList.add(reqNum);
            if (loopNum != 1) {
                noRequestCycleNumList.add(noRequestCycleNum);
            }
            noRequestCycleNum=0;
        }else {
            noRequestCycleNum++;
        }
        preReqNum = reqNum;

        if (reqNumList.size() > 25) {
            reqNumList = reqNumList.subList(10, reqNumList.size());
        }
        if (noRequestCycleNumList.size() > 25) {
            noRequestCycleNumList = noRequestCycleNumList.subList(10, noRequestCycleNumList.size());
        }

    }

    public void analyseData(long createContainerTime,long functionExecTime) {
        if (loopNum>5) {
            Set<Integer> reqNumSet = new HashSet<Integer>(reqNumList);
            Set<Integer> noRequestCycleNumSet = new HashSet<Integer>(noRequestCycleNumList);
            if (preReqNum != 0) {
                if (reqNumSet.size() == 0) {
                    requestType=RequestType.UNKNOWN;
                    requestNumSecond=0;
                } else if (reqNumSet.size() == 1) {
                    requestType=RequestType.CYCLE;
                    requestNumSecond = reqNumList.get(0);
                } else if (reqNumSet.size() == 2) {
                    requestType=RequestType.CYCLE;
                    requestNumSecond = new TreeSet<Integer>(reqNumSet).pollLast();
                }else {
                    if (requestType == null) {
                        requestType=RequestType.UNKNOWN;
                    }
                    requestNumSecond = reqNumList.get(reqNumList.size() - 2);
                }

                if (noRequestCycleNumSet.size() == 0) {
                    emptyType=RequestType.CYCLE;
                    cycleTep=0;
                }
                else if (noRequestCycleNumSet.size() == 1) {
                    emptyType=RequestType.CYCLE;
                    cycleTep = noRequestCycleNumList.get(0);
                }else if (noRequestCycleNumSet.size() == 2) {
                    emptyType=RequestType.CYCLE;
                    cycleTep = new TreeSet<Integer>(noRequestCycleNumSet).pollFirst();
                }else {
                    if (requestType == null) {
                        requestType=RequestType.UNKNOWN;
                    }
                    cycleTep = new TreeSet<Integer>(noRequestCycleNumSet).pollFirst();
                }

                //计算周期的数组
                if ((requestType == RequestType.CYCLE)&& emptyType==RequestType.CYCLE) {
                    cycleArr=new int[1+cycleTep];
                    cycleArr[0] = requestNumSecond;
                    if (cycleArr.length > 1) {
                        for (int i = 1; i < cycleArr.length; i++) {
                            cycleArr[i]=0;
                        }
                    }
                    //例如[5,0,0,0,0,5,0,0,0,0,5,0,0,0,0]
                    long emptyTime=cycleTep*SchedulerParams.LOOP_TIME;
                    logger.info("1.1 function:{},mean same reqNum,same empty tep,cycleArr={},emptyTime={},createContainerTime={},functionExecTime={}",functionName,cycleArr,emptyTime,createContainerTime,functionExecTime);
                    if (cycleTep!=0&&emptyTime > createContainerTime&&emptyTime>functionExecTime) {
                        isReturnRemoveContainer=true;
                        int preCreateNum = (int) Math.ceil(createContainerTime / SchedulerParams.LOOP_TIME);
                            //loopNum+(4-1)+1
                        createContainerLoopNum = loopNum + (cycleTep - preCreateNum);
                        logger.info("current reqNum={},loopNum={},preCreateContainerloopNum={}",preReqNum,loopNum,createContainerLoopNum);
                    }else {
                        isReturnRemoveContainer=false;
                    }
                    logger.info("2function data:{},reqNumList={},notReqNumList={},isRemoveContainer={}",functionName,reqNumList,noRequestCycleNumList,isReturnRemoveContainer);
                } else if (requestType == RequestType.CYCLE || requestType == RequestType.DOUBLE_CYCLE) {
                    logger.info("3function:{},mean same reqNum,different empty tep",functionName);
                    cycleArr=new int[1+cycleTep];
                    cycleArr[0] = requestNumSecond;
                    if (cycleArr.length > 1) {
                        for (int i = 1; i < cycleArr.length; i++) {
                            cycleArr[i]=0;
                        }
                    }
                    //例如[5,0,0,5,0,0,0,0,5,0,0,0,0]
                    long emptyTime=cycleTep*SchedulerParams.LOOP_TIME;
                    if (emptyTime > createContainerTime&&emptyTime>functionExecTime) {
                        isReturnRemoveContainer=true;
                        int preCreateNum = (int) Math.ceil(createContainerTime / SchedulerParams.LOOP_TIME);
                        if (preReqNum != 0) {
                            //loopNum+(4-1)+1
                            createContainerLoopNum = loopNum + (cycleTep - preCreateNum);
                            logger.info("1.2 function data:{},current reqNum={},loopNum={},preCreateContainerloopNum={},emptyTime={},createContainerTime={},functionExecTime={}",functionName,preReqNum,loopNum,createContainerLoopNum,emptyTime,createContainerTime,functionExecTime);
                        }
                    }else {
                        isReturnRemoveContainer=false;
                    }
                    logger.info("4 function data:{},reqNumList={},notReqNumList={},isRemoveContainer={}",functionName,reqNumList,noRequestCycleNumList,isReturnRemoveContainer);
                } else if (emptyType == RequestType.CYCLE ) {
                    cycleArr=new int[3+cycleTep];
                    cycleArr[0]=reqNumList.get(reqNumList.size()-3);
                    cycleArr[1]=reqNumList.get(reqNumList.size()-2);
                    cycleArr[2]=reqNumList.get(reqNumList.size()-1);
                    if (cycleArr.length > 3) {
                        for (int i = 3; i < cycleArr.length; i++) {
                            cycleArr[i]=0;
                        }
                    }
                    isReturnRemoveContainer=false;
                    logger.info("5 function data:{},reqNumList={},notReqNumList={},isRemoveContainer={}",functionName,reqNumList,noRequestCycleNumList,isReturnRemoveContainer);
                }else {
                    isReturnRemoveContainer=false;
                    logger.info("6 function data:{},reqNumList={},notReqNumList={},isRemoveContainer={}",functionName,reqNumList,noRequestCycleNumList,isReturnRemoveContainer);
                }
            }

            if (noRequestCycleNum > cycleTep&&noRequestCycleNum>3) {
                isRemoveContainerImmediately=true;
                requestType=RequestType.UNKNOWN;
            }

        }

        loopNum++;
    }


}
