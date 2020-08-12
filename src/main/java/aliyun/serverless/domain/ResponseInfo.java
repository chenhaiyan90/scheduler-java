package aliyun.serverless.domain;

public class ResponseInfo {
    public String id;
    public String functionName;
    public String containerId;

    public ResponseInfo(String id, String containerId) {
        this.id = id;
        this.containerId = containerId;
    }
}
