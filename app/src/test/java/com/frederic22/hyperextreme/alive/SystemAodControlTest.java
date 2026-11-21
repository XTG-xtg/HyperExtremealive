package com.frederic22.hyperextreme.alive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SystemAodControlTest {
    static class MemoryStore implements SystemAodControl.Store {
        final Map<String,String> values=new HashMap<>();
        int writes;
        int failAt=-1;
        boolean ignoreEnable;
        public String read(String key) { return values.get(key); }
        public void write(String key,String value) throws Exception {
            if(++writes==failAt) throw new IOException("denied");
            if(ignoreEnable && "1".equals(value) && SystemAodControl.ENABLED.equals(key)) return;
            if(value==null) values.remove(key); else values.put(key,value);
        }
    }
    @Test public void canEnableAfterWallpaperAlreadyMountedAndDisableStaysOff() throws Exception {
        MemoryStore store=new MemoryStore();
        store.values.put(SystemAodControl.ENABLED,"0");store.values.put(SystemAodControl.USER_SET,"0");
        SystemAodControl.set(store,true);
        assertEquals("1",store.read(SystemAodControl.ENABLED));assertEquals("1",store.read(SystemAodControl.USER_SET));
        SystemAodControl.set(store,false);
        assertEquals("0",store.read(SystemAodControl.ENABLED));assertEquals("0",store.read(SystemAodControl.USER_SET));
        int writes=store.writes;
        assertEquals("0",store.read(SystemAodControl.ENABLED));assertEquals(writes,store.writes);
    }
    @Test public void partialFailureRestoresBothOriginalValues() throws Exception {
        MemoryStore store=new MemoryStore();store.failAt=2;
        store.values.put(SystemAodControl.ENABLED,"0");store.values.put(SystemAodControl.USER_SET,"1");
        assertThrows(IOException.class,()->SystemAodControl.set(store,true));
        assertEquals("0",store.read(SystemAodControl.ENABLED));assertEquals("1",store.read(SystemAodControl.USER_SET));
    }
    @Test public void unconfirmedWriteCannotReportSuccessAndRestoresAbsentKeys() {
        MemoryStore store=new MemoryStore();store.ignoreEnable=true;
        assertThrows(IOException.class,()->SystemAodControl.set(store,true));
        assertTrue(store.values.isEmpty());
    }
    @Test public void readFailureDoesNotWriteAnything() {
        MemoryStore store=new MemoryStore() { @Override public String read(String key) { throw new SecurityException("unreadable"); } };
        assertThrows(SecurityException.class,()->SystemAodControl.set(store,true));assertEquals(0,store.writes);
    }
    @Test public void rollbackFailureIsReportedAndDoesNotSkipRestoringUserChoice() {
        MemoryStore store=new MemoryStore() {
            @Override public void write(String key,String value) throws Exception {
                if(SystemAodControl.ENABLED.equals(key)) throw new IOException("unwritable");
                super.write(key,value);
            }
        };
        IOException failure=assertThrows(IOException.class,()->SystemAodControl.set(store,true));
        assertTrue(failure.getMessage().contains("恢复原状态失败"));assertNull(store.read(SystemAodControl.USER_SET));
    }
    @Test public void forceEnablesBothModesButDoesNotPreventTurningAodOff() throws Exception {
        MemoryStore store=new MemoryStore();SystemAodControl.setForced(store,true);
        assertEquals("1",store.read(SystemAodControl.FORCE));assertEquals("1",store.read(SystemAodControl.FULL));
        assertEquals("1",store.read(SystemAodControl.ENABLED));
        SystemAodControl.set(store,false);
        assertEquals("0",store.read(SystemAodControl.ENABLED));assertEquals("1",store.read(SystemAodControl.FORCE));
        SystemAodControl.setForced(store,false);
        assertEquals("0",store.read(SystemAodControl.ENABLED));assertEquals("0",store.read(SystemAodControl.FORCE));
    }
    @Test public void failedForceOperationRestoresAllTouchedSystemSettings() {
        MemoryStore store=new MemoryStore();store.failAt=4;
        store.values.put(SystemAodControl.FULL,"0");store.values.put(SystemAodControl.ENABLED,"0");
        assertThrows(IOException.class,()->SystemAodControl.setForced(store,true));
        assertEquals("0",store.read(SystemAodControl.FULL));assertEquals("0",store.read(SystemAodControl.ENABLED));
        assertNull(store.read(SystemAodControl.FORCE));assertNull(store.read(SystemAodControl.USER_SET));
    }
}
