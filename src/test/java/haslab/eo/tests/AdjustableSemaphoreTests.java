package haslab.eo.tests;

import pt.uminho.di.a3m_discarded_2.flow_control.AdjustableSemaphore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class AdjustableSemaphoreTests {
    @Test
    public void testNegativeAdjustment() throws InterruptedException {
        int P = 10;
        AdjustableSemaphore semaphore = new AdjustableSemaphore(P);
        semaphore.acquire(6);
        int newP = 4;
        semaphore.adjustPermits(newP - P);
        assert semaphore.availablePermits() == -2;
    }

    @Test
    public void testPositiveAdjustment(){
        int P = 10;
        AdjustableSemaphore semaphore = new AdjustableSemaphore(P);
        semaphore.adjustPermits(10);
        assert semaphore.availablePermits() == 20;
    }

    @Test
    public void testAcquireAfterAdjustmentToNegativeValue() throws InterruptedException {
        int P = 4;
        AdjustableSemaphore semaphore = new AdjustableSemaphore(P);
        semaphore.acquire(P); // acquire 4 permits

        // assuming that the semaphore should now have only a total of 2 permits,
        // since 4 permits have been acquired, the current available permits must become -2
        int newP = 2;
        semaphore.adjustPermits(newP - P);

        // 2 releases must occur before the semaphore reaches 0 permits available,
        // and 3 to have 1 permit available
        assert !semaphore.tryAcquire(0, TimeUnit.MILLISECONDS);
        semaphore.release();
        assert !semaphore.tryAcquire(0, TimeUnit.MILLISECONDS);
        semaphore.release();
        assert !semaphore.tryAcquire(0, TimeUnit.MILLISECONDS);
        semaphore.release();
        assert semaphore.tryAcquire(0, TimeUnit.MILLISECONDS);
    }
}
