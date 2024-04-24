package haslab.eo;

import java.util.concurrent.Semaphore;

public class AdjustableSemaphore extends Semaphore {
    public AdjustableSemaphore(int permits) {
        super(permits);
    }

    public void reducePermits(int reduction){
        super.reducePermits(reduction);
    }

    /**
     * Adjusts the number of permits. A positive adjustment results in
     * the increase of the number of permits, while a negative adjustment
     * results in a reduction of the number of permits.
     * @param adjustment
     */
    public void adjustPermits(int adjustment){
        if(adjustment < 0)
            super.reducePermits(-adjustment);
        else
            super.release(adjustment);
    }
}
