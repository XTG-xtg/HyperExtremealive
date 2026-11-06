// Derived from OpenAlive; MIT; see docs/alive/OPENALIVE_LICENSE and IMPORTED-VIVO.json.
package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.hardware.*;

/** Scales only incoming gyro velocity. Vivo retains angle integration, damping and limits. */
final class VivoSensorGain implements SensorEventListener,AutoCloseable {
    private final SensorManager manager;
    private final SensorEventListener original;
    private final Sensor gyro;
    private final int sampling;
    private float gain;
    private boolean registered,active;
    VivoSensorGain(Context context,VivoCalls api,Object sensor,int relative)throws Exception{
        manager=context.getSystemService(SensorManager.class);original=(SensorEventListener)sensor;
        gyro=(Sensor)api.get(sensor,"d");sampling=(Integer)api.get(sensor,"j");gain=multiplier(relative);
    }
    static float multiplier(int relative){
        int value=Math.max(-50,Math.min(50,relative));
        // Keep 0 byte-for-byte on the native listener. The negative half offers
        // a wider reduction without changing Vivo's colour/intensity algorithm.
        return (float)Math.pow(value<0?10:2,value/50.0);
    }
    void start(){
        active=true;
        if(gyro==null)return;
        // Re-registering an existing listener is idempotent. Do not trust our
        // cached state after display suspension or a native controller stop.
        // Keep Vivo's own listener at default gain, including its calibration.
        if(gain==1){
            if(registered){manager.unregisterListener(this);registered=false;}
            manager.registerListener(original,gyro,sampling);
            return;
        }
        manager.unregisterListener(original,gyro);
        if(registered){
            // Android can return false for an already-enabled sensor. That is
            // not a reason to add a second, unscaled native subscription.
            manager.registerListener(this,gyro,sampling);
            return;
        }
        registered=manager.registerListener(this,gyro,sampling);
        if(!registered)manager.registerListener(original,gyro,sampling);
    }
    // Called on the main thread, like the original sensor callback. Updating this
    // factor must not reset Vivo's integrated angle or recreate the GL scene.
    void relative(int value){
        gain=multiplier(value);
        if(!active||gyro==null)return;
        if(gain==1&&registered){
            manager.unregisterListener(this);registered=false;
            manager.registerListener(original,gyro,sampling);
        }else if(gain!=1)start();
    }
    @Override public void onSensorChanged(SensorEvent event){
        float x=event.values[0],y=event.values[1],z=event.values[2];
        try{event.values[0]=x*gain;event.values[1]=y*gain;event.values[2]=z*gain;original.onSensorChanged(event);}
        finally{event.values[0]=x;event.values[1]=y;event.values[2]=z;}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){original.onAccuracyChanged(sensor,accuracy);}
    @Override public void close(){active=false;if(registered){manager.unregisterListener(this);registered=false;}manager.unregisterListener(original);}
}
