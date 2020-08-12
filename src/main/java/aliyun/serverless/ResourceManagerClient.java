package aliyun.serverless;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resourcemanagerproto.ResourceManagerGrpc;
import resourcemanagerproto.ResourceManagerGrpc.ResourceManagerBlockingStub;
import resourcemanagerproto.ResourceManagerOuterClass.*;

import java.util.concurrent.TimeUnit;


public class ResourceManagerClient {


    private static final Logger logger = LoggerFactory.getLogger(ResourceManagerClient.class);

    private final ResourceManagerBlockingStub blockingStub;

    public ResourceManagerClient(Channel channel) {
        blockingStub = ResourceManagerGrpc.newBlockingStub(channel);
    }

    public ReserveNodeReply reserveNode(ReserveNodeRequest req) {
        return blockingStub.reserveNode(req);
    }

    public ReleaseNodeReply releaseNode(ReleaseNodeRequest req) {
        return blockingStub.releaseNode(req);
    }

    public GetNodesUsageReply getNodesUsage(GetNodesUsageRequest req) {
        return blockingStub.getNodesUsage(req);
    }

    public static ResourceManagerClient New() {
        String rmEndpoint = System.getenv("RESOURCE_MANAGER_ENDPOINT");
        if (null == rmEndpoint) {
            rmEndpoint = "0.0.0.0:10400";
        }
        ManagedChannel channel = ManagedChannelBuilder.forTarget(rmEndpoint).usePlaintext().build();
        ResourceManagerClient client = new ResourceManagerClient(channel);
        logger.info("Connected to ResourceManager server at " + rmEndpoint);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("ResourceManager client shut down.");
        }));

        return client;
    }
}
