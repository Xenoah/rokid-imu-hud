package dev.xenoah.hud.core;

/** Short primitive-only copy: no Canvas, I/O, allocation, or filtering while holding this lock. */
public final class Exchange {
    private final Snapshot state=new Snapshot();
    public synchronized void publish(Snapshot s) { state.copyFrom(s); }
    public synchronized void read(Snapshot out) { out.copyFrom(state); }
}
