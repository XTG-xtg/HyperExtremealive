package com.frederic22.hyperextreme.alive;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** Small cache for the verified ROM contracts; unavailable fields throw to the caller. */
final class SystemFields {
    private static final Map<Class<?>,Map<String,Field>> FIELDS=new HashMap<>();
    static Object get(Object host,String name) throws ReflectiveOperationException {
        if(host==null)throw new NoSuchFieldException(name);
        Class<?> type=host.getClass();Field field;
        synchronized(FIELDS){
            Map<String,Field> map=FIELDS.computeIfAbsent(type,key->new HashMap<>());
            field=map.get(name);
            if(field==null){
                for(Class<?> scan=type;scan!=null;scan=scan.getSuperclass()){
                    try{field=scan.getDeclaredField(name);field.setAccessible(true);map.put(name,field);break;}
                    catch(NoSuchFieldException missing){/* Inspect the parent. */}
                }
            }
        }
        if(field==null)throw new NoSuchFieldException(name);
        return field.get(host);
    }
}
