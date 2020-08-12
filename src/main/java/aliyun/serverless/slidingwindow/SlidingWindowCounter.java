package aliyun.serverless.slidingwindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlidingWindowCounter {

    private static final Logger logger = LoggerFactory.getLogger(SlidingWindowCounter.class);
    public volatile SlotBaseCounter slotBaseCounter;
    private volatile int windowSize;
    public volatile int head;
    private volatile int lastIndex;

    public SlidingWindowCounter(int windowSize) {
        resizeWindow(windowSize);
    }

    public synchronized void resizeWindow(int windowSize) {
        this.windowSize = windowSize;
        this.slotBaseCounter = new SlotBaseCounter(windowSize);
        this.head = 0;
    }

    public void increase() {
        slotBaseCounter.increaseSlot(head);
    }

    public void addCostTime(Long time){
        slotBaseCounter.addTime(head,time);
    }

//    public void increaseException(){
//        slotBaseCounter.increaseExceptionSlot(head);
//    }

    public int totalAndAdvance() {
        int total = totalCount();
        advance();
        return total;
    }

    public void advance() {
        int tail = (head + 1) % windowSize;
        slotBaseCounter.wipeSlot(tail);
        lastIndex = head;
        head = tail;
    }

    public int totalCount() {
        return slotBaseCounter.totalCount(lastIndex);
    }

    public int currentCount() {
        return slotBaseCounter.totalCount(head);
    }

//    public int totalExceptionCount(){
//        return slotBaseCounter.exceptionTotalCount(lastIndex);
//    }
    public double meanTime(){
        return slotBaseCounter.meanTime(lastIndex);
    }

}