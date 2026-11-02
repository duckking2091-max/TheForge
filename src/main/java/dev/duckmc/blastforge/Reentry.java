package dev.duckmc.blastforge;

/**
 * "This thread is doing it right now" marker, so a listener can ignore the event its own action caused.
 * Kept per thread: on Folia several regions run at once, and one region's action must not hide another's events.
 */
public final class Reentry {
    private final ThreadLocal<int[]> depth=ThreadLocal.withInitial(()->new int[1]);
    public void enter(){depth.get()[0]++;}
    public void exit(){depth.get()[0]--;}
    public boolean active(){return depth.get()[0]>0;}
}
