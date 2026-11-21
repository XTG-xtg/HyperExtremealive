package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class AodSupportFlagsTest {
    public static final class Unsupported {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final boolean INNER_SCREEN_SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final boolean UNRELATED=Boolean.parseBoolean("false");
    }
    public static final class Supported {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("true");
        public static final boolean INNER_SCREEN_SUPPORT_FULL_AOD=Boolean.parseBoolean("true");
    }
    public static final class Mixed {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("true");
        public static final boolean INNER_SCREEN_SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
    }
    public static final class WrongType {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final int INNER_SCREEN_SUPPORT_FULL_AOD=0;
    }
    public static final class InstanceField {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public final boolean INNER_SCREEN_SUPPORT_FULL_AOD=false;
    }
    public static final class MissingField {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
    }
    private static boolean value(Class<?> type,String name) throws Exception {
        return type.getField(name).getBoolean(null);
    }
    @Test public void finalFieldsBecomeTrueAndRepeatedPreparationIsSafe() throws Exception {
        assertFalse(value(Unsupported.class,"SUPPORT_FULL_AOD"));
        AodSupportFlags.prepare(Unsupported.class);
        AodSupportFlags.prepare(Unsupported.class);
        assertTrue(value(Unsupported.class,"SUPPORT_FULL_AOD"));
        assertTrue(value(Unsupported.class,"INNER_SCREEN_SUPPORT_FULL_AOD"));
        assertFalse(value(Unsupported.class,"UNRELATED"));
    }
    @Test public void supportedDeviceIsUnchanged() throws Exception {
        AodSupportFlags.prepare(Supported.class);
        assertTrue(value(Supported.class,"SUPPORT_FULL_AOD"));
        assertTrue(value(Supported.class,"INNER_SCREEN_SUPPORT_FULL_AOD"));
    }
    @Test public void oneUnsupportedFlagIsPrepared() throws Exception {
        AodSupportFlags.prepare(Mixed.class);
        assertTrue(value(Mixed.class,"SUPPORT_FULL_AOD"));
        assertTrue(value(Mixed.class,"INNER_SCREEN_SUPPORT_FULL_AOD"));
    }
    private static void rejectsWithoutPartialWrite(Class<?> type) throws Exception {
        assertThrows(NoSuchFieldException.class,()->AodSupportFlags.prepare(type));
        assertFalse(value(type,"SUPPORT_FULL_AOD"));
    }
    @Test public void wrongTypeDoesNotPartiallyWrite() throws Exception { rejectsWithoutPartialWrite(WrongType.class); }
    @Test public void instanceFieldDoesNotPartiallyWrite() throws Exception { rejectsWithoutPartialWrite(InstanceField.class); }
    @Test public void missingFieldDoesNotPartiallyWrite() throws Exception { rejectsWithoutPartialWrite(MissingField.class); }
}
