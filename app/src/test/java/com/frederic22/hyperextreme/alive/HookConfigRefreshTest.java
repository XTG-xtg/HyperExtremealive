package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class HookConfigRefreshTest {
    public static class Provider extends ContentProvider {
        int reads;boolean ready,fail;
        public boolean onCreate(){return true;}
        public Bundle call(String method,String arg,Bundle extras){
            reads++;if(fail)throw new IllegalStateException("provider restarting");
            Bundle config=new Bundle();config.putBoolean("configured",true);config.putBoolean("enabled",ready);return config;
        }
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
        public String getType(Uri u){return null;}
        public Uri insert(Uri u,ContentValues v){return null;}
        public int delete(Uri u,String s,String[] a){return 0;}
        public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    }
    private Provider provider;
    @Before public void setup() {
        provider=new Provider();provider.attachInfo(RuntimeEnvironment.getApplication(),null);
        ShadowContentResolver.registerProviderInternal(Protocol.CONFIG.getAuthority(),provider);
        ReflectionHelpers.setStaticField(AliveHooks.class,"context",RuntimeEnvironment.getApplication());
        ReflectionHelpers.setStaticField(AliveHooks.class,"ipc",new Handler(Looper.getMainLooper()));
        ReflectionHelpers.setStaticField(AliveHooks.class,"readAt",-750L);
        ReflectionHelpers.setStaticField(AliveHooks.class,"refreshFailures",0);
        ReflectionHelpers.setStaticField(AliveHooks.class,"observing",false);
        ReflectionHelpers.setStaticField(AliveHooks.class,"cached",Bundle.EMPTY);
    }
    private Bundle refresh(){return ReflectionHelpers.callStaticMethod(AliveHooks.class,"refresh");}
    private Bundle cached(){return ReflectionHelpers.getStaticField(AliveHooks.class,"cached");}
    @Test public void coldReadBeforeEngineBindingRetriesAndPublishesReadyConfig() {
        assertFalse(refresh().getBoolean("enabled"));provider.ready=true;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        assertEquals(2,provider.reads);assertTrue(cached().getBoolean("enabled"));
    }
    @Test public void providerRestartIsRetriedWithoutBlockingTheHookThread() {
        provider.fail=true;assertFalse(refresh().getBoolean("enabled"));
        provider.fail=false;provider.ready=true;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        assertTrue(cached().getBoolean("enabled"));
    }
    @Test public void retriesAreBoundedWhenProviderRemainsUnavailable() {
        provider.fail=true;refresh();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(4,provider.reads);assertFalse(cached().getBoolean("enabled"));
    }
}
