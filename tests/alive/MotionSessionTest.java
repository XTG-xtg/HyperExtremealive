package com.frederic22.hyperextreme.alive;

public final class MotionSessionTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("check "+checks);checks++;}
    private static final class Motion implements WallpaperMotion {
        long last=-1,elapsed;int mode=1,changes,pauses,advances;
        public void change(int value,boolean animate){if(mode!=value){mode=value;changes++;}}
        public void advance(long time,float speed){check(time>=last);if(last>=0)elapsed+=time-last;last=time;advances++;}
        public void follow(float x,float y){}
        public void pause(){last=-1;pauses++;}
        public boolean active(){return false;}
    }
    public static void main(String[] args)throws Exception{
        Motion motion=new Motion();Object home=new Object(),lock=new Object();
        MotionSession a=MotionSession.acquire("fixture",()->motion);
        MotionSession b=MotionSession.acquire("fixture",()->{throw new AssertionError("Duplicate CPU timeline");});
        check(a==b);a.visible(home,true);b.visible(lock,true);
        long now=3_600_000_000_000L;
        a.frame(0,1,0,now,()->{});
        check(motion.elapsed==0); // Process uptime must not become gradient color time.
        int advances=motion.advances;
        b.frame(0,1,0,now,()->{});check(motion.advances==advances);
        a.frame(1,2,now+10,now+20,()->{});
        check(motion.mode==2&&motion.elapsed==20&&motion.changes==1);
        b.frame(0,1,0,now+30,()->{});
        check(motion.mode==2&&motion.elapsed==30); // Stale slot must not reverse new state.
        a.visible(home,false);check(motion.pauses==0);
        b.frame(1,2,now+10,now+40,()->{});check(motion.elapsed==40);
        b.visible(lock,false);check(motion.pauses==1);
        a.visible(home,true);a.frame(1,2,now+10,now+1_000_000,()->{});
        check(motion.elapsed==40); // Hidden time does not move a paused animation.
        a.frame(2,0,now+100,now+1_000_100,()->{});
        check(motion.mode==0&&motion.elapsed==140); // Late event time is clamped forward.
        b.frame(2,0,now+100,now+1_000_090,()->{});
        check(motion.elapsed==140); // A delayed vsync from another slot cannot rewind.
        MotionSession.release("fixture",a,home);MotionSession.release("fixture",b,lock);
        MotionSession c=MotionSession.acquire("fixture",Motion::new);check(c!=a);
        MotionSession.release("fixture",c,new Object());
        System.out.println("MotionSession assertions: "+checks);
    }
}
