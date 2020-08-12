package aliyun.serverless.core;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import nodeservoceproto.NodeServiceGrpc;
import nodeservoceproto.NodeServiceOuterClass.*;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NodeClient {
//    private final Channel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub blockingStub;
    private static final Logger logger = Logger.getLogger(NodeClient.class.getName());

    public NodeClient(String host,int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host,port)
                .usePlaintext()
                .build();

        blockingStub = NodeServiceGrpc.newBlockingStub(channel);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("ResourceManager client shut down.");
        }));
    }


    public  CreateContainerReply createContainer(CreateContainerRequest request){
        return blockingStub.createContainer(request);
    }

    public GetStatsReply getStats(GetStatsRequest request) {
        return blockingStub.getStats(request);
    }

    public RemoveContainerReply removeContainer(RemoveContainerRequest request) {
        return blockingStub.removeContainer(request);
    }

    public Iterator<InvokeFunctionReply> invokeFunction(InvokeFunctionRequest request) {
        return blockingStub.invokeFunction(request);
    }
}
