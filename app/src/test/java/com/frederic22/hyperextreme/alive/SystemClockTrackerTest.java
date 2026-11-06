package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35,application=Application.class)
public class SystemClockTrackerTest {
    private Context context(){
        Context app=RuntimeEnvironment.getApplication();Resources original=app.getResources();
        Resources named=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public String getResourceEntryName(int id){return id==1?"clock":id==2?"clock_container":id==3?"aod_time":"unrelated";}
        };
        return new ContextWrapper(app){@Override public Resources getResources(){return named;}};
    }
    private View child(FrameLayout root,int id,int height){View view=new View(root.getContext());view.setId(id);root.addView(view);view.layout(0,0,300,height);return view;}
    @Test public void boundedClockGroupWinsOverEarlierStatusBarClock(){
        FrameLayout root=new FrameLayout(context());root.layout(0,0,1080,2400);
        child(root,1,50);View clock=child(root,2,400);
        assertSame(clock,SystemClockTracker.find(root));
        clock.setVisibility(View.GONE);assertNull(SystemClockTracker.find(root));
    }
    @Test public void fullScreenHostsAndHiddenClocksAreNotAnchors(){
        FrameLayout root=new FrameLayout(context());root.layout(0,0,1080,2400);
        child(root,2,2400);View time=child(root,3,200);
        assertSame(time,SystemClockTracker.find(root));
        time.setAlpha(0);assertNull(SystemClockTracker.find(root));
    }
    @Test public void nestedClockContentExcludesPaddingAndBlankSpacers(){
        FrameLayout root=new FrameLayout(context());root.layout(0,0,1080,2400);
        FrameLayout host=new FrameLayout(context());host.setId(2);root.addView(host);host.layout(0,100,1080,1100);
        View spacer=new View(context());host.addView(spacer);spacer.layout(0,0,1080,1000);
        android.widget.TextView clock=new android.widget.TextView(context());clock.setText("12:34");clock.setTextSize(50);
        host.addView(clock);clock.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY));
        clock.layout(100,100,900,800);
        android.graphics.Rect rect=SystemClockTracker.contentBounds(host);
        assertFalse(rect.isEmpty());assertTrue(rect.height()<300);assertTrue(rect.width()<800);
        assertTrue(rect.bottom<600);
        clock.setText("");assertTrue(SystemClockTracker.contentBounds(host).isEmpty());
    }
}
