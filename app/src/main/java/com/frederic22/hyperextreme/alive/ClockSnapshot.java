package com.frederic22.hyperextreme.alive;

import android.os.Bundle;
import android.os.SystemClock;
import com.frederic22.hyperextreme.alive.core.AodPlacement;

/** Only geometry crosses the process boundary: no screenshots, notifications or user text. */
final class ClockSnapshot {
    final AodPlacement bounds;
    final long at;
    final boolean aod;
    ClockSnapshot(Bundle b) {
        bounds=new AodPlacement(b.getInt("width"),b.getInt("height"),b.getInt("left"),b.getInt("top"),b.getInt("right"),b.getInt("bottom"));
        if(!bounds.isClockContent())throw new IllegalArgumentException("Clock host is not content geometry");
        at=b.getLong("at");aod=b.getBoolean("aod");
        long now=SystemClock.uptimeMillis();
        if(at<0||at>now||now-at>5000)throw new IllegalArgumentException("Stale clock geometry");
    }
    boolean usable(int width,int height){
        long age=SystemClock.uptimeMillis()-at;
        return aod&&age>=0&&age<120000&&bounds.isClockContent()&&bounds.fits(width,height);
    }
    Bundle bundle(){
        Bundle b=new Bundle();b.putInt("width",bounds.width);b.putInt("height",bounds.height);
        b.putInt("left",bounds.left);b.putInt("top",bounds.top);b.putInt("right",bounds.right);b.putInt("bottom",bounds.bottom);
        b.putLong("at",at);b.putBoolean("aod",aod);return b;
    }
}
