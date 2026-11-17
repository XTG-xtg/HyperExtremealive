package com.frederic22.hyperextreme.alive;
import org.junit.Test;
import static org.junit.Assert.*;
public class InfinityLauncherOffsetTest {
    @Test public void automaticAndExplicitStepsDriveTheSameTwentyThemes(){
        for(int id=801;id<=820;id++){
            InfinityMotion auto=InfinityMotion.create(id,2),normal=InfinityMotion.create(id,2);
            auto.follow(InfinityLauncherOffset.centered(.8f,-1),0,1_000_000_000L);
            normal.follow(InfinityLauncherOffset.centered(.8f,.25f),0,1_000_000_000L);
            auto.advance(2_000_000_000L,1);normal.advance(2_000_000_000L,1);
            assertEquals(normal.desktopX(),auto.desktopX(),0);assertTrue(auto.desktopX()!=0);
        }
    }
    @Test public void invalidAndSinglePageOffsetsCannotJump(){
        assertEquals(0,InfinityLauncherOffset.centered(0,0),0);
        assertEquals(0,InfinityLauncherOffset.centered(Float.NaN,-1),0);
        assertEquals(0,InfinityLauncherOffset.centered(.5f,Float.NaN),0);
        assertEquals(0,InfinityLauncherOffset.centered(.8f,-2),0);
        assertEquals(.5f,InfinityLauncherOffset.centered(2,-1),0);
    }
}
