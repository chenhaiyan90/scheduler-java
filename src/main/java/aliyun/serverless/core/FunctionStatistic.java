package aliyun.serverless.core;

import aliyun.serverless.domain.RequestMetrics;
import aliyun.serverless.domain.RequestType;
import aliyun.serverless.slidingwindow.SlidingWindowCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schedulerproto.SchedulerOuterClass;

import java.util.concurrent.ConcurrentHashMap;

public class FunctionStatistic {
    public String accountId;
    public long functionMemoryInBytes;
    public long timeoutInMs;
    public String handle;
    public ConcurrentHashMap<String, RequestMetrics> requestMetricsMap;
    public SlidingWindowCounter swc;
    public volatile long functionExecTime;
    public volatile long createContainerTime;
//    public public

    public FunctionFeature functionFeature;

    public String functionName;
    public Double meanCostTime;
    public RequestType requestType;
    public int cycleTep;
    private static final Logger logger = LoggerFactory.getLogger(FunctionStatistic.class);

    public int preNumber;
    public int lastNumber;



    public FunctionStatistic(SchedulerOuterClass.AcquireContainerRequest request) {

        this.functionName=request.getFunctionName();
        this.accountId=request.getAccountId();
        this.functionMemoryInBytes=request.getFunctionConfig().getMemoryInBytes();
        this.timeoutInMs=request.getFunctionConfig().getTimeoutInMs();
        this.handle=request.getFunctionConfig().getHandler();

        this.swc = new SlidingWindowCounter(SchedulerParams.SLIDING_WINDOW_SIZE);
        this.requestMetricsMap = new ConcurrentHashMap<>();
        this.functionExecTime=0;
        this.createContainerTime=0;
        this.functionFeature=new FunctionFeature(functionName);
    }

    public void printMetrics(){
        logger.info("function:{},metrics:{}", functionName,swc.slotBaseCounter.toString());
    }
}
