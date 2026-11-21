package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Validated numeric data; no Android view/service classes from the source APKs. */
final class InfinityExpansionTheme {
    final int variant;
    final boolean polygon;
    final String directory,atlas;
    final float[] background,homePositions,aodPositions,points,faces,lines;
    final float[][] homeColors,aodColors;
    final Layer[] layers;
    final float tiltGain;
    final int lockMode,homeMode;
    InfinityExpansionTheme(AssetManager assets,int variant)throws IOException {
        if(variant<807||!InfinityTheme.supports(variant))throw new IOException("Unknown Infinity expansion");
        this.variant=variant;directory="infinity/"+InfinityTheme.SLUGS[variant-801]+"/";
        try {
            JSONObject json=new JSONObject(AssetGl.text(assets,directory+"theme.json"));
            if(json.getInt("variant")!=variant)throw new JSONException("Infinity identity mismatch");
            polygon=variant>=811;
            if(!json.getString("kind").equals(polygon?"polygon":"n8"))throw new JSONException("Infinity renderer mismatch");
            if(polygon){
                String path=json.getString("directory");
                if(!path.equals("infinity/polygon/"+new String[]{"black","blue","gold","orchid","purple"}[(variant-811)/2]+"/"))throw new JSONException("Mesh path");
                atlas=path;points=binary(assets,path+"points.bin");faces=binary(assets,path+"faces.bin");lines=binary(assets,path+"lines.bin");
                if(points.length!=json.getInt("vertex_count")*10||faces.length!=json.getInt("face_count")*30||lines.length%20!=0)throw new JSONException("Mesh count");
                lockMode=json.getInt("lock_mode");homeMode=json.getInt("home_mode");
                if(lockMode!=1+(variant-811)%2||homeMode!=lockMode+1)throw new JSONException("Scene profile");
                for(int i=0;i<points.length;i+=10)if(points[i+7]<0||points[i+7]>4||points[i+8]<0||points[i+9]>1)throw new JSONException("Particle group");
                layers=new Layer[0];tiltGain=0;background=homePositions=aodPositions=new float[0];homeColors=aodColors=new float[0][];
            }else{
                atlas=json.getString("atlas");if(!atlas.equals(directory+"atlas.webp"))throw new JSONException("Atlas path");
                background=vector(json.getJSONArray("background"),36);
                homePositions=vector(json.getJSONArray("home_positions"),-1);homeColors=matrix(json.getJSONArray("home_colors"),3);
                aodPositions=vector(json.getJSONArray("aod_positions"),-1);aodColors=matrix(json.getJSONArray("aod_colors"),3);
                gradient(homePositions,homeColors);gradient(aodPositions,aodColors);
                JSONArray source=json.getJSONArray("layers");layers=new Layer[source.length()];
                if(layers.length!=InfinityTheme.LAYERS[variant-801])throw new JSONException("Tangram count");
                for(int i=0;i<layers.length;i++)layers[i]=new Layer(source.getJSONObject(i));
                tiltGain=(float)json.getDouble("tilt_gain");if(!Float.isFinite(tiltGain)||tiltGain<0||tiltGain>1)throw new JSONException("Tilt gain");
                lockMode=homeMode=0;points=faces=lines=new float[0];
            }
        }catch(JSONException failure){throw new IOException("Invalid Infinity expansion data",failure);}
    }
    private static float[] binary(AssetManager assets,String path)throws IOException {
        byte[] data=AssetGl.bytes(assets,path);if(data.length%40!=0||data.length==0||data.length>2_000_000)throw new IOException("Invalid mesh length");
        float[] result=new float[data.length/4];ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(result);
        for(float f:result)if(!Float.isFinite(f))throw new IOException("Non-finite mesh coordinate");return result;
    }
    static float[] vector(JSONArray array,int length)throws JSONException {
        if(length>=0&&array.length()!=length)throw new JSONException("Infinity vector length");
        float[] result=new float[array.length()];for(int i=0;i<result.length;i++){result[i]=(float)array.getDouble(i);if(!Float.isFinite(result[i]))throw new JSONException("Non-finite coordinate");}return result;
    }
    private static float[][] matrix(JSONArray array,int length)throws JSONException {
        float[][] result=new float[array.length()][];for(int i=0;i<result.length;i++)result[i]=vector(array.getJSONArray(i),length);return result;
    }
    private static void gradient(float[] positions,float[][] colors)throws JSONException {
        if(positions.length<2||positions.length!=colors.length||positions[0]!=0||positions[positions.length-1]!=2960)throw new JSONException("Gradient extent");
        for(int i=1;i<positions.length;i++)if(positions[i]<=positions[i-1])throw new JSONException("Gradient order");
        for(float[] color:colors)for(float c:color)if(c<0||c>255)throw new JSONException("Gradient color");
    }
    static final class Layer {
        final float opacity;
        final float[] position,entrance,exit;
        final Component[] components;
        Layer(JSONObject source)throws JSONException {
            opacity=(float)source.getDouble("opacity");if(!Float.isFinite(opacity)||opacity<0||opacity>1)throw new JSONException("Opacity");
            position=vector(source.getJSONArray("position"),2);entrance=vector(source.getJSONArray("entrance"),2);exit=vector(source.getJSONArray("exit"),2);
            JSONArray data=source.getJSONArray("components");if(data.length()<1||data.length()>4)throw new JSONException("Component count");
            components=new Component[data.length()];for(int i=0;i<components.length;i++)components[i]=new Component(data.getJSONObject(i));
        }
    }
    static final class Component {
        final int role;final boolean masked;final float[] vertices,maskMove;
        Component(JSONObject data)throws JSONException {
            role=data.getInt("role");masked=data.getBoolean("masked");vertices=vector(data.getJSONArray("vertices"),36);maskMove=vector(data.getJSONArray("mask_move"),2);
            if(role<0||role>3)throw new JSONException("Component role");
            for(int i=0;i<vertices.length;i++)if(i%6>=2&&(vertices[i]<0||vertices[i]>1))throw new JSONException("Atlas UV");
        }
    }
}
