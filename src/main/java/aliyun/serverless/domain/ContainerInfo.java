package aliyun.serverless.domain;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ContainerInfo extends ReentrantLock {

    public String id;
    public String address;
    public int port;
    public String nodeId;
    public Map<String,Integer> requests;
    public long memoryInBytes;
    public String functionName;

    public ContainerInfo(String id, String address, int port, String nodeId, Map<String, Integer> requests,long memoryInBytes,String functionName) {
        this.id = id;
        this.address = address;
        this.port = port;
        this.nodeId = nodeId;
        this.requests = requests;
        this.memoryInBytes=memoryInBytes;
        this.functionName=functionName;
    }
}
