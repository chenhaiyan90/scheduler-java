package aliyun.serverless.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestMetrics {
    private static final Logger logger = LoggerFactory.getLogger(RequestMetrics.class);
    public String functionName;
    public volatile long functionExecTime;
    public String requestId;
    public long acquireContainerTime;
    public long acquireContainerBackTime;
    public boolean isCreateNode;
    public boolean isCreateContainer;
    public long returnContainerTime;
    public long createNodeCostTime;
    public long createContainerCostTime;
    public long reqExecCostTime;

    public RequestMetrics(String functionName, String requestId) {
        this.functionName = functionName;
        this.requestId = requestId;
        this.isCreateNode=false;
        this.isCreateContainer=false;
    }

    public void setAcquireContainerTime() {
        acquireContainerTime=System.currentTimeMillis();
    }
    public void setAcquireContainerBackTime() {
        acquireContainerBackTime=System.currentTimeMillis();
    }
    public void setReturnContainerTime() {
        returnContainerTime=System.currentTimeMillis();
    }
    public void computeReqExecCostTime(){
        reqExecCostTime=returnContainerTime-acquireContainerBackTime;
    }
    public void setIsCreateNode(boolean flag) {
        isCreateNode=flag;
    }
    public void setIsCreateContainer(boolean flag) {
        isCreateContainer=flag;
    }

    public void printMetrics() {
        logger.info(toString());
    }

    @Override
    public String toString() {
        return "RequestMetrics{" +
                "functionName='" + functionName + '\'' +
                ", requestId='" + requestId + '\'' +
                ", acquireContainerTime=" + acquireContainerTime +
                ", acquireContainerBackTime=" + acquireContainerBackTime +
                ", returnContainerTime=" + returnContainerTime +
                ", reqExecCostTime=" + reqExecCostTime +
                ", isCreateNode=" + isCreateNode +
                ", isCreateContainer=" + isCreateContainer +
                ", createNodeCostTime=" + createNodeCostTime +
                ", createContainerCostTime=" + createContainerCostTime +
                '}';
    }
}
