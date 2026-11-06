package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Roll back local content/settings if root binding or either verification step fails. */
final class AliveApplyBackup implements AutoCloseable {
    private final Context context;
    private final JSONObject snapshot;
    private boolean accepted;
    AliveApplyBackup(Context context)throws IOException{
        this.context=context;
        recover(context);
        File active=active(context),backup=backup(context);boolean existed=active.isFile();
        if(!backup.getParentFile().isDirectory()&&!backup.getParentFile().mkdirs())throw new IOException("Cannot create apply journal");
        if(existed){
            Files.copy(active.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
            try(FileOutputStream out=new FileOutputStream(backup,true)){out.getFD().sync();}
        }
        try {
            snapshot=new JSONObject().put("version",1).put("existed",existed).put("accepted",false)
                .put("config",encode(Config.prefs(context).getAll()))
                .put("photo",encode(context.getSharedPreferences("alive_photo_applied",0).getAll()));
            write(journal(context),snapshot);
        }catch(JSONException e){throw new IOException("Cannot write apply journal",e);}
    }
    void accept()throws IOException{
        try{snapshot.put("accepted",true);write(journal(context),snapshot);accepted=true;}
        catch(JSONException e){throw new IOException(e);}
    }
    public void close()throws IOException{
        recover(context);
        if(!accepted)Config.changed(context);
    }
    private static File active(Context c){return new File(c.getFilesDir(),"alive/photo.png");}
    private static File backup(Context c){return new File(c.getFilesDir(),"alive/apply-photo.backup");}
    private static AtomicFile journal(Context c){return new AtomicFile(new File(c.getFilesDir(),"alive/apply-journal.json"));}
    static void recover(Context c)throws IOException {
        AtomicFile journal=journal(c);
        if(!journal.getBaseFile().isFile()&&!new File(journal.getBaseFile().getPath()+".bak").isFile())return;
        try {
            JSONObject state=new JSONObject(new String(journal.readFully(),StandardCharsets.UTF_8));
            if(state.getInt("version")!=1)throw new IOException("Unsupported apply journal version");
            if(!state.getBoolean("accepted")){
                if(state.getBoolean("existed")){
                    File temp=new File(c.getFilesDir(),"alive/photo.recover.png");
                    Files.copy(backup(c).toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp.toPath(),active(c).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                }else Files.deleteIfExists(active(c).toPath());
                restore(Config.prefs(c),decode(state.getJSONObject("config")));
                restore(c.getSharedPreferences("alive_photo_applied",0),decode(state.getJSONObject("photo")));
            }
            // The journal is retired first. An interrupted cleanup cannot trigger a rollback.
            journal.delete();Files.deleteIfExists(backup(c).toPath());
        }catch(JSONException e){throw new IOException("Invalid apply journal; retained for recovery",e);}
    }
    private static void write(AtomicFile file,JSONObject state)throws IOException {
        FileOutputStream out=file.startWrite();
        try{out.write(state.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException|RuntimeException e){file.failWrite(out);throw e;}
    }
    private static JSONObject encode(Map<String,?> values)throws JSONException,IOException {
        JSONObject result=new JSONObject();
        for(Map.Entry<String,?> row:values.entrySet()){
            Object value=row.getValue();String type;
            if(value instanceof String)type="s";
            else if(value instanceof Integer)type="i";
            else if(value instanceof Long)type="l";
            else if(value instanceof Float)type="f";
            else if(value instanceof Boolean)type="b";
            else if(value instanceof Set){type="set";value=new JSONArray((Set<?>)value);}
            else throw new IOException("Unsupported preference type");
            result.put(row.getKey(),new JSONObject().put("type",type).put("value",value));
        }
        return result;
    }
    private static Map<String,?> decode(JSONObject data)throws JSONException,IOException {
        Map<String,Object> result=new HashMap<>();java.util.Iterator<String> keys=data.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject row=data.getJSONObject(key);Object value;
            switch(row.getString("type")){
                case "s":value=row.getString("value");break;
                case "i":value=row.getInt("value");break;
                case "l":value=row.getLong("value");break;
                case "f":value=(float)row.getDouble("value");break;
                case "b":value=row.getBoolean("value");break;
                case "set":Set<String> set=new HashSet<>();JSONArray a=row.getJSONArray("value");for(int i=0;i<a.length();i++)set.add(a.getString(i));value=set;break;
                default:throw new IOException("Unsupported journal preference type");
            }
            result.put(key,value);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    static void restore(SharedPreferences preferences,Map<String,?> values)throws IOException{
        SharedPreferences.Editor editor=preferences.edit().clear();
        for(Map.Entry<String,?> row:values.entrySet()){
            String key=row.getKey();Object value=row.getValue();
            if(value instanceof String)editor.putString(key,(String)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);
            else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);
            else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));
            else throw new IOException("Unknown preference type: "+key);
        }
        if(!editor.commit())throw new IOException("Cannot restore Alive preferences");
    }
}
