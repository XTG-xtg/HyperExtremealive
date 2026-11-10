package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Looper;
import com.frederic22.hyperextreme.alive.core.AliveState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class EngineStartupTest {
    @Before public void resetRegistry() {
        AliveState state=ReflectionHelpers.getStaticField(EngineRegistry.class,"STATE");
        state.previous=state.current=AliveState.LOCK;state.startedMs=state.sequence=0;
        state.lastEventMs=-1;state.homeConfirmedMs=-10000;
        ((java.util.ArrayDeque<?>)ReflectionHelpers.getStaticField(EngineRegistry.class,"HISTORY")).clear();
        ReflectionHelpers.setStaticField(EngineRegistry.class,"powerInitialized",false);
    }
    @Test public void engineStartupPowerStateIsAvailableBeforeSurfaceAttachment() {
        Application context=RuntimeEnvironment.getApplication();
        shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
        EngineRegistry.MAIN.post(()->{
            EngineRegistry.signal(context,1,SystemClock.uptimeMillis(),"engine-created");
            assertEquals(0,EngineRegistry.snapshot().mode);
        });
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void longIdleHistorySeedsSettledStateNotOldTransition() {
        Application context=RuntimeEnvironment.getApplication();
        shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
        EngineRegistry.signal(context,0,SystemClock.uptimeMillis(),"screen-off");
        shadowOf(Looper.getMainLooper()).idle();
        long sequence=EngineRegistry.snapshot().sequence;
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30));
        EngineRegistry.Snapshot[] history=EngineRegistry.history();
        assertEquals(1,history.length);assertEquals(0,history[0].from);assertEquals(0,history[0].mode);
        assertEquals(sequence,history[0].sequence);assertEquals(0,history[0].startMs);
    }
}
