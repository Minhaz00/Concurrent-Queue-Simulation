import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("unchecked")
public class GroceryQueues {
    private final LinkedBlockingQueue<Customer>[] queues;
    private final Lock[] queueLocks;
    private final int maxQueueLength;
    private final AtomicInteger totalServed = new AtomicInteger(0);
    private final AtomicInteger totalLeftUnserved = new AtomicInteger(0);
    private final AtomicInteger totalCustomers = new AtomicInteger(0);
    private final AtomicInteger totalServiceTime = new AtomicInteger(0);
    private final ExecutorService cashierPool;  // Thread pool for cashiers

    public GroceryQueues(int numQueues, int maxQueueLength) {
        this.queues = new LinkedBlockingQueue[numQueues];
        this.queueLocks = new ReentrantLock[numQueues];
        for (int i = 0; i < numQueues; i++) {
            queues[i] = new LinkedBlockingQueue<>(maxQueueLength);
            queueLocks[i] = new ReentrantLock();
        }
        this.maxQueueLength = maxQueueLength;
        this.cashierPool = Executors.newFixedThreadPool(numQueues);  // Thread pool
    }

    // public void addCustomer(Customer customer) {
    //     totalCustomers.incrementAndGet();
    //     int shortestQueueIndex = -1;
    //     int shortestQueueSize = Integer.MAX_VALUE;

    //     // Find the shortest queue
    //     for (int i = 0; i < queues.length; i++) {
    //         queueLocks[i].lock();
    //         try {
    //             if (queues[i].size() < shortestQueueSize) {
    //                 shortestQueueIndex = i;
    //                 shortestQueueSize = queues[i].size();
    //             }
    //         } finally {
    //             queueLocks[i].unlock();
    //         }
    //     }

    //     if (shortestQueueIndex != -1 && shortestQueueSize < maxQueueLength) {
    //         queueLocks[shortestQueueIndex].lock();
    //         try {
    //             queues[shortestQueueIndex].offer(customer);
    //         } finally {
    //             queueLocks[shortestQueueIndex].unlock();
    //         }
    //     } else {
    //         customer.setServed(false);
    //         totalLeftUnserved.incrementAndGet();
    //     }
    // }

    public void addCustomer(Customer customer) {
        totalCustomers.incrementAndGet();
        int shortestQueueIndex = -1;
        int shortestQueueSize = Integer.MAX_VALUE;
        long maxWaitTime = 10000; // 10 seconds in milliseconds

        // time when the customer arrives
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            shortestQueueIndex = -1;
            shortestQueueSize = Integer.MAX_VALUE;

            // Find the shortest queue
            for (int i = 0; i < queues.length; i++) {
                queueLocks[i].lock();
                try {
                    if (queues[i].size() < shortestQueueSize) {
                        shortestQueueIndex = i;
                        shortestQueueSize = queues[i].size();
                    }
                } finally {
                    queueLocks[i].unlock();
                }
            }

            // If we found a queue with space, add the customer
            if (shortestQueueIndex != -1 && shortestQueueSize < maxQueueLength) {
                queueLocks[shortestQueueIndex].lock();
                try {
                    queues[shortestQueueIndex].offer(customer);
                    return; // Customer has been added to the queue, exit the method
                } finally {
                    queueLocks[shortestQueueIndex].unlock();
                }
            }

            // Wait a short time before trying again
            try {
                Thread.sleep(100); // Wait 100ms before checking again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                break;
            }
        }

        // If no queue became available after 10 seconds, mark the customer as unserved
        customer.setServed(false);
        totalLeftUnserved.incrementAndGet();
    }


    // public void processQueues() {
    //     for (int i = 0; i < queues.length; i++) {
    //         queueLocks[i].lock();
    //         try {
    //             Customer customer = queues[i].poll();
    //             if (customer != null) {
    //                 totalServiceTime.addAndGet(customer.getServiceTime());
    //                 totalServed.incrementAndGet();
    //                 try {
    //                     Thread.sleep(customer.getServiceTime() * 1000L);
    //                 } catch (InterruptedException e) {
    //                     Thread.currentThread().interrupt();
    //                 }
    //             }
    //         } finally {
    //             queueLocks[i].unlock();
    //         }
    //     }
    // }

     public void processQueues() {
        for (int i = 0; i < queues.length; i++) {
            int finalI = i;
            cashierPool.submit(() -> {  // Each cashier works in a separate thread
                try {
                    Customer customer;
                    while ((customer = queues[finalI].poll()) != null) {
                        totalServiceTime.addAndGet(customer.getServiceTime());
                        totalServed.incrementAndGet();
                        Thread.sleep(customer.getServiceTime() * 1000L);  // Simulate service time
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    public void shutdown() {
        cashierPool.shutdown();
        try {
            if (!cashierPool.awaitTermination(60, TimeUnit.SECONDS)) {
                cashierPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            cashierPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getTotalServed() {
        return totalServed.get();
    }

    public int getTotalLeftUnserved() {
        return totalLeftUnserved.get();
    }

    public int getTotalCustomers() {
        return totalCustomers.get();
    }

    public int getTotalServiceTime() {
        return totalServiceTime.get();
    }
}
