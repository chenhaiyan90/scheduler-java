package aliyun.serverless.core;

import aliyun.serverless.domain.ContainerInfo;
import aliyun.serverless.domain.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RemoveContainerTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RemoveContainerTask.class);


    public Router router;
    public FunctionStatistic functionStatistic;

    public RemoveContainerTask(Router router, FunctionStatistic functionStatistic) {
        this.router = router;
        this.functionStatistic=functionStatistic;
    }

    @Override
    public void run() {

        List<ContainerInfo> otherList = new ArrayList<>();
        router.funAvaContainerSetMap.get(functionStatistic.functionName).drainTo(otherList);
        for(ContainerInfo other:otherList) {
            router.removeOtherEmptyContainer(other,router.functionMap.get(functionStatistic.functionName),functionStatistic.functionMemoryInBytes);
        }
    }


}
