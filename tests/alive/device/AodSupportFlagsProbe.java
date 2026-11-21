package com.frederic22.hyperextreme.alive;

/** Runs the production accessor in an isolated ART process, never in SystemUI. */
public final class AodSupportFlagsProbe {
    public static final class Unsupported {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final boolean INNER_SCREEN_SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final boolean UNRELATED=Boolean.parseBoolean("false");
    }
    public static final class Malformed {
        public static final boolean SUPPORT_FULL_AOD=Boolean.parseBoolean("false");
        public static final int INNER_SCREEN_SUPPORT_FULL_AOD=0;
    }
    public static void main(String[] args) {
        try {
            if (Unsupported.class.getField("SUPPORT_FULL_AOD").getBoolean(null))
                throw new AssertionError("Unsupported fixture must start false");
            AodSupportFlags.prepare(Unsupported.class);
            AodSupportFlags.prepare(Unsupported.class);
            for (String key:new String[]{"SUPPORT_FULL_AOD","INNER_SCREEN_SUPPORT_FULL_AOD"}) {
                if (!Unsupported.class.getField(key).getBoolean(null)) throw new AssertionError(key);
                System.out.println(key+"=true");
            }
            if (Unsupported.class.getField("UNRELATED").getBoolean(null)) throw new AssertionError("Unrelated flag");
            try { AodSupportFlags.prepare(Malformed.class); throw new AssertionError("Malformed contract accepted"); }
            catch (NoSuchFieldException expected) {}
            if (Malformed.class.getField("SUPPORT_FULL_AOD").getBoolean(null)) throw new AssertionError("Partial write");
            System.out.println("AOD_SUPPORT_FLAGS_PASS");
        } catch(Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
