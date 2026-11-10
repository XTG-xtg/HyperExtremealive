package com.frederic22.hyperextreme.alive.core;

/** An effect lifetime, NOT ownership of the native AOD display or an unlock. */
public final class ExtensionPulse {
    private long started=-1,deadline,serial;
    public long begin(long now,int seconds,boolean allowed){
        if(!allowed){cancel();return -1;}
        if(started<0||now>=deadline)started=now;
        int bounded=seconds==5||seconds==15?seconds:10;
        deadline=Math.min(started+30000,now+bounded*1000L);return ++serial;
    }
    public boolean active(long now){return started>=0&&now<deadline;}
    public long remaining(long now){return active(now)?deadline-now:0;}
    public boolean expire(long token,long now){if(token!=serial||active(now))return false;cancel();return true;}
    public void cancel(){started=-1;deadline=0;serial++;}
}
