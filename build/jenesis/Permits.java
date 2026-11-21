package build.jenesis;

import module java.base;

public interface Permits {

    void acquire() throws InterruptedException;

    void release();

    static Permits of(int count) {
        return new Counted(new Semaphore(count));
    }

    record Counted(Semaphore semaphore) implements Permits {

        @Override
        public void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        @Override
        public void release() {
            semaphore.release();
        }
    }
}
