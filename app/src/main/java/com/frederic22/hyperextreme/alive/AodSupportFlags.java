package com.frederic22.hyperextreme.alive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Prepares only the two ROM capability fields before the deoptimized manager constructor. */
final class AodSupportFlags {
    static void prepare(Class<?> configs) throws ReflectiveOperationException {
        Field[] fields = new Field[2];
        String[] names = {"SUPPORT_FULL_AOD", "INNER_SCREEN_SUPPORT_FULL_AOD"};
        boolean[] previous = new boolean[2];
        for (int i = 0; i < fields.length; i++) {
            Field field = configs.getDeclaredField(names[i]);
            if (field.getType() != boolean.class || !Modifier.isStatic(field.getModifiers()))
                throw new NoSuchFieldException(names[i] + " is not a static boolean");
            field.setAccessible(true);
            fields[i] = field;
            previous[i] = field.getBoolean(null); // Also completes class initialization.
        }
        if (previous[0] && previous[1]) return;

        // Android 17 ART aborts the process on JNI SetStaticBooleanField for final fields.
        // Resolve the runtime's field accessors, never assume an ART field layout or offset.
        Class<?> type = Class.forName("jdk.internal.misc.Unsafe");
        Object unsafe = type.getMethod("getUnsafe").invoke(null);
        Method base = type.getMethod("staticFieldBase", Field.class);
        Method offset = type.getMethod("staticFieldOffset", Field.class);
        Method write = type.getMethod("putBooleanVolatile", Object.class, long.class, boolean.class);
        Object[] bases = new Object[2];
        long[] offsets = new long[2];
        for (int i = 0; i < fields.length; i++) {
            bases[i] = base.invoke(unsafe, fields[i]);
            offsets[i] = (Long) offset.invoke(unsafe, fields[i]);
            if (bases[i] == null || offsets[i] < 0)
                throw new IllegalStateException("Invalid AOD capability field storage");
        }
        try {
            for (int i = 0; i < fields.length; i++)
                if (!previous[i]) write.invoke(unsafe, bases[i], offsets[i], true);
            for (Field field : fields)
                if (!field.getBoolean(null)) throw new IllegalStateException("AOD capability readback failed");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            for (int i = 0; i < fields.length; i++) {
                try { write.invoke(unsafe, bases[i], offsets[i], previous[i]); }
                catch (ReflectiveOperationException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    private AodSupportFlags() {}
}
