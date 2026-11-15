package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Numeric data extracted from S8 Infinity 1.2; see docs/alive/INFINITY-PROVENANCE.json. */
final class InfinityTheme {
    static final String[] SLUGS={"black","gold","blue","orchid","pink","silver",
        "n8-black","n8-blue","n8-gold","n8-orchid",
        "polygon-black-1","polygon-black-2","polygon-blue-1","polygon-blue-2",
        "polygon-gold-1","polygon-gold-2","polygon-orchid-1","polygon-orchid-2","polygon-purple-1","polygon-purple-2"};
    static final String[] NAMES={"Infinity · 黑色","Infinity · 金色","Infinity · 蓝色","Infinity · 兰紫","Infinity · 粉色","Infinity · 银色",
        "N8 · 黑色","N8 · 蓝色","N8 · 金色","N8 · 兰紫",
        "几何 · 黑色星空","几何 · 黑色渐变","几何 · 蓝色星空","几何 · 蓝色渐变",
        "几何 · 金色星空","几何 · 金色渐变","几何 · 兰紫星空","几何 · 兰紫渐变","几何 · 紫色星空","几何 · 紫色渐变"};
    static final int[] LAYERS={16,16,8,9,10,10,11,9,9,5,0,0,0,0,0,0,0,0,0,0};
    final int index;
    final String directory;
    final float[] homePositions,aodPositions;
    final float[][] homeColors,aodColors,vertices;
    static boolean supports(int variant){return variant>=801&&variant<801+SLUGS.length;}

    InfinityTheme(AssetManager assets,int variant)throws IOException {
        if(variant<801||variant>806)throw new IOException("Not an S8 Infinity theme: "+variant);
        index=variant-801;directory="infinity/"+SLUGS[index]+"/";
        try {
            JSONObject source=new JSONObject(AssetGl.text(assets,directory+"theme.json"));
            if(source.getInt("source_theme_index")!=index)throw new JSONException("Theme index mismatch");
            homePositions=vector(source.getJSONArray("home_positions"));
            homeColors=matrix(source.getJSONArray("home_colors_rgb255"),3);
            aodPositions=vector(source.getJSONArray("aod_positions").getJSONArray(0));
            aodColors=matrix(source.getJSONArray("aod_colors_rgb255").getJSONArray(0),3);
            vertices=matrix(source.getJSONArray("effect_vertices_normalized"),24);
            if(vertices.length!=LAYERS[index])throw new JSONException("Effect layer count");
            validateGradient(homePositions,homeColors);validateGradient(aodPositions,aodColors);
        }catch(JSONException error){throw new IOException("Invalid Infinity data",error);}
    }
    private static void validateGradient(float[] positions,float[][] colors)throws JSONException {
        if(positions.length!=colors.length||positions.length<2||positions[0]!=0||positions[positions.length-1]!=2960)
            throw new JSONException("Gradient extent");
        for(int i=1;i<positions.length;i++)if(positions[i]<=positions[i-1])throw new JSONException("Gradient order");
    }
    private static float[] vector(JSONArray array)throws JSONException {
        float[] result=new float[array.length()];
        for(int i=0;i<result.length;i++){result[i]=(float)array.getDouble(i);if(!Float.isFinite(result[i]))throw new JSONException("Non-finite value");}
        return result;
    }
    private static float[][] matrix(JSONArray array,int columns)throws JSONException {
        float[][] result=new float[array.length()][];
        for(int i=0;i<result.length;i++){result[i]=vector(array.getJSONArray(i));if(result[i].length!=columns)throw new JSONException("Invalid row length");}
        return result;
    }
}
