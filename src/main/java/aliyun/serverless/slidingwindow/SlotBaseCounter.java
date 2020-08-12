package aliyun.serverless.slidingwindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SlotBaseCounter {
    private int slotSize;
    private AtomicInteger[] slotCounter;
//    private AtomicInteger[] exceptionSlotCounter;
    public BlockingQueue<Long>[] timeCounter;
    private static final Logger logger = LoggerFactory.getLogger(SlotBaseCounter.class);

    public SlotBaseCounter(int slotSize) {
        slotSize = slotSize < 1 ? 1 : slotSize;
        this.slotSize = slotSize;
        this.slotCounter = new AtomicInteger[slotSize];
//        this.exceptionSlotCounter = new AtomicInteger[slotSize];
        this.timeCounter = new BlockingQueue[slotSize];
        for (int i = 0; i < this.slotSize; i++) {
            slotCounter[i] = new AtomicInteger(0);
//            exceptionSlotCounter[i] = new AtomicInteger(0);
            timeCounter[i] = new LinkedBlockingQueue<>();
        }
    }

    public void increaseSlot(int head) {
        slotCounter[head].incrementAndGet();
//        timeCounter[head].offer(costTime);
    }

    public void addTime(int head,Long costTime) {
        timeCounter[head].offer(costTime);
    }

//    public void increaseExceptionSlot(int index){
//        exceptionSlotCounter[index].incrementAndGet();
//    }

    public void wipeSlot(int head) {
        slotCounter[head].set(0);
//        exceptionSlotCounter[head].set(0);
        timeCounter[head].clear();
    }

    public int totalCount(int index) {
//        return Arrays.stream(slotCounter).mapToInt(slotCounter -> slotCounter.get()).sum();
        return slotCounter[index].get();
    }
//    public int exceptionTotalCount(int index) {
//        return exceptionSlotCounter[index].get();
//    }

    public double meanTime(int index){
        return timeCounter[index].stream().collect(Collectors.averagingLong(aa->aa));
    }


    @Override
    public String toString() {
        return Arrays.toString(slotCounter);
    };

    public void printCostTime(){
//        logger.info(Arrays.toString());
    }

    public static void main(String[] args) {
        AtomicInteger[] slotCounter = new AtomicInteger[9];
        for (int i = 0; i <9 ; i++) {
            slotCounter[i] = new AtomicInteger(i);
        }
        System.out.println(Arrays.toString(slotCounter));
    }
}
