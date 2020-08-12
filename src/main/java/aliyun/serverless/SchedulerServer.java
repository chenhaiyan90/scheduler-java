package aliyun.serverless;

import aliyun.serverless.domain.ResponseInfo;
import aliyun.serverless.domain.Router;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schedulerproto.SchedulerGrpc;
import schedulerproto.SchedulerOuterClass.AcquireContainerReply;
import schedulerproto.SchedulerOuterClass.AcquireContainerRequest;
import schedulerproto.SchedulerOuterClass.ReturnContainerReply;
import schedulerproto.SchedulerOuterClass.ReturnContainerRequest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;



public class SchedulerServer {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerServer.class);


    private final int port;
    private final Server server;
    private final ResourceManagerClient rmClient;
    private final Router router;

    public SchedulerServer(int port) throws IOException {
        this.port = port;
        this.rmClient = ResourceManagerClient.New();
        this.router = new Router(this.rmClient);
        this.server = ServerBuilder.forPort(port)
                                   .addService(new SchedulerService(this.router))
                                   .build();
        logger.info("init schedulerServer end");
        System.out.println("system.out to start.......");

    }

    public void start() throws IOException {
        server.start();
        logger.info("Started scheduler server listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                SchedulerServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("Scheduler server shut down.");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        SchedulerServer server = new SchedulerServer(10600);
        server.start();
        server.blockUntilShutdown();
    }

    private static class SchedulerService extends SchedulerGrpc.SchedulerImplBase {

        public final Router router;
        public SchedulerService(Router router) {
            this.router = router;
        }

        @Override
        public void acquireContainer(AcquireContainerRequest request,
                                     StreamObserver<AcquireContainerReply> responseObserver) {
            try {

                AcquireContainerReply reply = router.acquireContainer(request);
                responseObserver.onNext(reply);
            } catch (Throwable throwable) {
                logger.error("acquireContainer error :reqId={}",request.getRequestId());
                logger.error(throwable.getMessage());
                responseObserver.onError(throwable);
            }
            responseObserver.onCompleted();
        }

        @Override
        public void returnContainer(ReturnContainerRequest request,
                                    StreamObserver<ReturnContainerReply> responseObserver) {

            try {
                router.ReturnContainer(new ResponseInfo(request.getRequestId(), request.getContainerId()));
                responseObserver.onNext(ReturnContainerReply.newBuilder().build());
            } catch (Throwable throwable) {
                logger.error("returnContainer error :reqId={}",request.getRequestId());
                logger.error(throwable.getMessage());
                responseObserver.onError(throwable);
            }
            responseObserver.onCompleted();
        }
    }
}
