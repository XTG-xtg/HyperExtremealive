package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class ExtensionIntegrationTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences(ExtensionOptions.PREFS,0).edit().clear().commit();}
    @Test public void newFeaturesAreOptInAndDraftDoesNotTouchWallpaper() {
        ExtensionOptions defaults=ExtensionOptions.load(context);assertFalse(defaults.clock);assertEquals(0,defaults.light);
        context.getSharedPreferences("alive",0).edit().putInt("variant",605).putLong("revision",123).commit();
        ExtensionOptions draft=new ExtensionOptions(true,"flyme-2",2,15,0xffaabbcc,1.2f);
        Bundle bundle=new Bundle();draft.write(bundle);ExtensionOptions restored=ExtensionOptions.from(bundle);
        assertEquals(draft.clockKey(),restored.clockKey());assertFalse(ExtensionOptions.load(context).clock);
        draft.save(context);assertTrue(ExtensionOptions.load(context).clock);
        assertEquals(605,com.frederic22.hyperextreme.alive.Config.variant(context));
        assertEquals(123,context.getSharedPreferences("alive",0).getLong("revision",0));
    }
    @Test public void corruptedPreferencesFailClosedWithoutBreakingConfigRead() {
        context.getSharedPreferences(ExtensionOptions.PREFS,0).edit().putString("clock","oops").commit();
        assertFalse(ExtensionOptions.load(context).clock);
        assertFalse(com.frederic22.hyperextreme.alive.Config.read(context).getBoolean("ext_clock"));
    }
    @Test public void optionsRejectUnknownStylesAndNonFiniteValues() {
        ExtensionOptions invalid=new ExtensionOptions(true,"../../bad.dex",9,-1,0x123456,Float.NaN);
        assertEquals(NativeHyperOsStyles.ALL[0].id,invalid.style);assertEquals(0,invalid.light);
        assertEquals(10,invalid.seconds);assertEquals(1,invalid.scale,0);assertEquals(0xff123456,invalid.color);
        assertEquals(1,new ExtensionOptions(false,null,0,10,0,Float.NEGATIVE_INFINITY).scale,0);
        assertEquals(.65f,new ExtensionOptions(false,null,0,10,0,-5).scale,0);
        assertEquals(1.35f,new ExtensionOptions(false,null,0,10,0,5).scale,0);
    }
    @Test public void alphaRollbackUsesLastNativeWriteAndNeverTouchesOtherViews() {
        View stock=new View(context),other=new View(context);stock.setAlpha(.7f);other.setAlpha(.9f);
        OwnedClockMask mask=new OwnedClockMask();mask.bind(stock);assertEquals(0,stock.getAlpha(),0);
        assertFalse(mask.intercept(other,.1f));assertEquals(.9f,other.getAlpha(),.001f);
        assertTrue(mask.intercept(stock,.35f));mask.clear();assertEquals(.35f,stock.getAlpha(),.001f);
        mask.clear();assertEquals(.35f,stock.getAlpha(),.001f);
    }
    @Test public void switchingTargetsRestoresOldAlphaAndHonorsUnhookedNativeChanges() {
        View first=new View(context),second=new View(context);first.setAlpha(.8f);second.setAlpha(.6f);
        OwnedClockMask mask=new OwnedClockMask();mask.bind(first);mask.bind(second);
        assertEquals(.8f,first.getAlpha(),.001f);
        second.setAlpha(.2f);mask.clear();assertEquals(.2f,second.getAlpha(),.001f);
    }
    @Test public void originalMeasureFailureIsContained() {
        AtomicInteger failures=new AtomicInteger();
        GuardedClockFrame guard=new GuardedClockFrame(context,e->failures.incrementAndGet(),()->fail("broken clock ready"));
        guard.addView(new View(context){@Override protected void onMeasure(int w,int h){throw new IllegalStateException("unsupported ROM");}});
        guard.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));
        assertEquals(0,guard.getMeasuredHeight());
        // Detached View.post waits for attachment; no exception escaped traversal.
        assertEquals(0,failures.get());
    }
    @Test public void clockStopDuringInitialTickDoesNotScheduleRecurringWork() {
        AtomicInteger ticks=new AtomicInteger();ClockUpdates[] holder=new ClockUpdates[1];
        holder[0]=new ClockUpdates(context,true,t->{ticks.incrementAndGet();holder[0].stop();});holder[0].start();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3));assertEquals(1,ticks.get());
    }
    @Test public void catalogHasOnlyValidatedUniquePresetsAndRealThumbnailFiles()throws Exception {
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(String[] style:OriginalClockController.catalog(context)){assertTrue(ExtensionOptions.validStyle(style[0]));assertTrue(ids.add(style[0]));}
        assertEquals(66,ids.size());
        for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)try(java.io.InputStream in=context.getAssets().open("native-clock/previews/"+style.preview)){assertTrue(in.read()>=0);}
        for(NativeVivoClockStyles.Style style:NativeVivoClockStyles.all(context))try(java.io.InputStream in=context.getAssets().open("native-clock/vivo/previews/"+style.key+".png")){assertTrue(in.read()>=0);}
    }
    @Test public void absentRomContractsDisableAdapter() {
        assertFalse(HyperOsExtensionHooks.requiredContracts(context.getClassLoader()));
    }
    @Test public void wallpaperGeometryCanFollowTheOwnedClockInsteadOfHiddenStock() {
        FrameLayout root=new FrameLayout(context);
        GuardedClockFrame clock=new GuardedClockFrame(context,e->fail(),()->{});
        clock.addView(new View(context),new FrameLayout.LayoutParams(300,160));root.addView(clock);
        root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,400,800);assertSame(clock,SystemClockTracker.find(root));
        clock.setAlpha(0);assertNull(SystemClockTracker.find(root));
    }
    @Test public void fingerprintAndDetachRestoreOnlyOwnedMasksAndCancelTimers() {
        FrameLayout root=new FrameLayout(context);View stock=new View(context),notification=new View(context),biometric=new View(context);
        root.addView(stock);root.addView(notification);root.addView(biometric);
        stock.setAlpha(.6f);notification.setAlpha(.7f);biometric.setAlpha(.8f);
        HyperOsExtensionHost host=new HyperOsExtensionHost(root,null,Bundle::new);
        host.mask.bind(stock);host.lightMask.bind(notification);host.fingerprint(true);
        assertEquals(.6f,stock.getAlpha(),.001f);assertEquals(.7f,notification.getAlpha(),.001f);assertEquals(.8f,biometric.getAlpha(),.001f);
        host.close();host.fingerprint(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(.8f,biometric.getAlpha(),.001f);assertEquals(3,root.getChildCount());
    }
}
