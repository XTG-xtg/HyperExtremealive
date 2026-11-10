package com.frederic22.hyperextreme.alive;

import android.view.Display;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionPreparationTest {
    @Test public void hiddenAwakeShaderFamiliesCanPrepare() {
        for(int variant=601;variant<=606;variant++)
            assertTrue(CompanionPreparation.allowed(false,variant,false,2,Display.STATE_ON,true));
        for(int variant:new int[]{1,3,4,19,101,105,201,205,301,303,501,503})
            assertTrue(CompanionPreparation.allowed(false,variant,false,2,Display.STATE_ON,true));
        for(int variant:new int[]{0,2,5,20,106,300,401,403,504,701,702,703})
            assertFalse(CompanionPreparation.allowed(false,variant,false,2,Display.STATE_ON,true));
    }
    @Test public void previewAndAlreadyVisibleEnginesCannotPrepare() {
        assertFalse(CompanionPreparation.allowed(true,601,false,2,Display.STATE_ON,true));
        assertFalse(CompanionPreparation.allowed(false,601,true,2,Display.STATE_ON,true));
    }
    @Test public void sleepIntentRevokesPreparationEvenBeforeDisplayPowerChanges() {
        assertFalse(CompanionPreparation.allowed(false,601,false,0,Display.STATE_ON,true));
        assertFalse(CompanionPreparation.allowed(false,601,false,1,Display.STATE_ON,true));
    }
    @Test public void offDozeUnknownAndNoninteractiveNeverPrepare() {
        for(int display:new int[]{Display.STATE_OFF,Display.STATE_DOZE,Display.STATE_DOZE_SUSPEND,Display.STATE_UNKNOWN})
            assertFalse(CompanionPreparation.allowed(false,601,false,2,display,true));
        assertFalse(CompanionPreparation.allowed(false,601,false,2,Display.STATE_ON,false));
    }
}
