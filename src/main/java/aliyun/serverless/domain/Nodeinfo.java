package aliyun.serverless.domain;

import aliyun.serverless.core.NodeClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Nodeinfo extends ReentrantLock {
    public String nodeId;
    public String address;
    public volatile long availableMemInBytes;
    public ConcurrentHashMap<String, AtomicInteger> nodeFunctionNum;

    public long getAvailableMemInBytes() {
        return availableMemInBytes;
    }

    public volatile long totalMemory;
    public int port;
    public NodeClient nodeClient;
    public ConcurrentSkipListSet<String> containerSet;
    public int containerNum;

    public int getContainerNum() {
        return containerNum;
    }

    public Nodeinfo(String nodeId, String address, long availableMemInBytes, int port) {
        this.nodeId = nodeId;
        this.address = address;
        this.availableMemInBytes = availableMemInBytes;
        this.totalMemory=availableMemInBytes;
        this.port = port;
        this.nodeClient = new NodeClient(address, port);
        this.containerSet = new ConcurrentSkipListSet();
        this.nodeFunctionNum = new ConcurrentHashMap<>();
    }
}
