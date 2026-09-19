package dev.xenoah.hud.core;

final class Rate {
    private long start; private int count;
    double hz;
    void add(long t) {
        if(start==0){start=t;count=0;return;}
        count++;
        if(t-start>=1_000_000_000L){hz=count*1e9/(t-start);start=t;count=0;}
    }
}
