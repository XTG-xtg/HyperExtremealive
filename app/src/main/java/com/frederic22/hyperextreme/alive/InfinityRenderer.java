package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;

/** S8 Infinity layered textures and point cloud, hosted by Alive's EGL/state lifecycle. */
final class InfinityRenderer implements WallpaperRenderer {
    static WallpaperRenderer create(AssetManager assets,int variant,boolean dark,int width,int height,int scene,InfinityMotion shared)throws IOException {
        return variant>=807?new InfinityExpansionRenderer(assets,variant,dark,width,height,scene,shared)
            :new InfinityRenderer(assets,variant,dark,width,height,scene,shared);
    }
    private final InfinityTheme theme;
    private final InfinityMotion motion;
    private final int width,height;
    private final boolean dark;
    private final ArrayList<AssetGl> programs=new ArrayList<>();
    private final ArrayList<Mesh> meshes=new ArrayList<>();
    private final int[] textures=new int[2];
    private AssetGl color,texture,effect,particle;
    private Mesh background,home,aod,layers,stars;
    private final float[] projection=new float[16],model=new float[16],mvp=new float[16];

    InfinityRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene,InfinityMotion shared)throws IOException {
        this.width=width;this.height=height;this.dark=dark;
        theme=new InfinityTheme(assets,variant);motion=shared!=null?shared:new InfinityMotion(variant,scene);
        try {
            color=program(assets,"color_vertex_shader.glsl","color_fragment_shader.glsl");
            texture=program(assets,"texture_vertext_shader.glsl","texture_fragment_shader.glsl");
            effect=program(assets,"simple_texture_vertex_shader.glsl","simple_texture_fragment_shader.glsl");
            particle=program(assets,"particle_vertex_shader.glsl","particle_fragment_shader.glsl");
            background=mesh(new float[]{-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0},4,texture,
                new String[]{"a_Position","a_TextureCoordinates"},new int[]{2,2});
            home=gradient(theme.homePositions,theme.homeColors);
            aod=gradient(theme.aodPositions,theme.aodColors);
            float[] geometry=new float[theme.vertices.length*24];
            for(int i=0;i<theme.vertices.length;i++)System.arraycopy(theme.vertices[i],0,geometry,i*24,24);
            layers=mesh(geometry,4,effect,new String[]{"a_Position","a_MaskTextureCoordinates"},new int[]{2,2});
            stars=mesh(particles(),9,particle,new String[]{"a_Position","a_Color","a_Size","a_HideOnHome"},new int[]{3,4,1,1});
            loadTexture(assets,theme.directory+"lock-background.png",0);
            loadTexture(assets,theme.directory+"lock-effect.png",1);
            Matrix.perspectiveM(projection,0,45,width/(float)height,1,10);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
        }catch(IOException|RuntimeException failure){close();throw failure;}
    }
    private AssetGl program(AssetManager assets,String vert,String frag)throws IOException {
        AssetGl result=new AssetGl(assets,"infinity/shaders/"+vert,"infinity/shaders/"+frag);programs.add(result);return result;
    }
    private Mesh mesh(float[] data,int stride,AssetGl shader,String[] names,int[] sizes)throws IOException {
        Mesh result=new Mesh();meshes.add(result);
        GLES30.glGenVertexArrays(1,result.vao,0);GLES30.glBindVertexArray(result.vao[0]);
        GLES30.glGenBuffers(1,result.vbo,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,result.vbo[0]);
        FloatBuffer buffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(data).flip();
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,data.length*4,buffer,GLES30.GL_STATIC_DRAW);
        int offset=0;
        for(int i=0;i<names.length;i++){
            int at=GLES30.glGetAttribLocation(shader.id,names[i]);
            if(at<0)throw new IOException("Missing Infinity attribute "+names[i]);
            GLES30.glEnableVertexAttribArray(at);GLES30.glVertexAttribPointer(at,sizes[i],GLES30.GL_FLOAT,false,stride*4,offset*4);offset+=sizes[i];
        }
        result.count=data.length/stride;return result;
    }
    private Mesh gradient(float[] positions,float[][] colors)throws IOException {
        float[] data=new float[(positions.length-1)*6*5];int at=0;
        int[] ends={0,1,1,0,1,0};float[] xs={-1,-1,1,-1,1,1};
        for(int row=0;row<positions.length-1;row++)for(int k=0;k<6;k++){
            int p=row+ends[k];data[at++]=xs[k];data[at++]=1-2*positions[p]/2960;
            for(int c=0;c<3;c++)data[at++]=colors[p][c]/255;
        }
        return mesh(data,5,color,new String[]{"a_Position","a_Color"},new int[]{2,3});
    }
    private void loadTexture(AssetManager assets,String path,int slot)throws IOException {
        Bitmap bitmap;
        try(InputStream input=assets.open(path)){bitmap=BitmapFactory.decodeStream(input);}
        if(bitmap==null)throw new IOException("Invalid Infinity texture: "+path);
        try {
            GLES30.glGenTextures(1,textures,slot);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[slot]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
        }finally{bitmap.recycle();}
    }
    static float[] particles(){
        Random random=new Random(0x5838494e46494eL);
        int[] counts={4000,4000,3000,3000,4};
        float[] radius={1.3f,1.3f,1.2f,1.4f,.8f},size={.9f,.6f,.5f,.6f,.6f},spread={.2f,.4f,.4f,.6f,3};
        float[] data=new float[14004*9];int at=0;
        for(int group=0;group<counts.length;group++)for(int i=0;i<counts[group];i++){
            double angle=i/(double)counts[group]*7;float noise=spread[group];
            data[at++]=(float)Math.cos(angle)*radius[group]+jitter(random,group==4?noise*2:noise);
            data[at++]=jitter(random,group==4?noise*2:noise);
            data[at++]=(float)Math.sin(angle)*radius[group]+jitter(random,noise)*(group==4?.6f:1);
            data[at++]=1;data[at++]=1;data[at++]=1;data[at++]=.01f+random.nextFloat()*.8f;
            data[at++]=size[group];data[at++]=(group==4?i%3==0:i%10==0||i%10==3||i%10==6)?1:3;
        }
        return data;
    }
    private static float jitter(Random random,float spread){return (random.nextFloat()-.5f)*spread;}
    public WallpaperMotion motion(){return motion;}
    public void render(){
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        float aspect=width/(float)height;
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,0,0,-2.3f);Matrix.scaleM(model,0,aspect*1.05f,1.05f,1);Matrix.multiplyMM(mvp,0,projection,0,model,0);
        InfinityParallax.background(mvp,motion);
        // Full-coverage compositing preserves a solid background during lock/home crossfade.
        float lock=motion.values[InfinityMotion.LOCK],homeAlpha=motion.values[InfinityMotion.HOME],sum=lock+homeAlpha;
        if(sum>0){
            drawGradient(home,sum);
            if(lock>0){texture.use();texture.matrix("u_Matrix",mvp);texture.f("u_Opacity",lock);
                bindTexture(texture,"u_TextureUnit",textures[0]);background.draw(GLES30.GL_TRIANGLE_STRIP);}
        }
        // Weighted backgrounds: source-over would fade HOME twice as AOD fades in.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE);
        if(motion.values[InfinityMotion.AOD]>0)drawGradient(aod,motion.values[InfinityMotion.AOD]);
        drawEffects();drawStars(aspect);
        if(dark){ // Match the existing dark toggle without replacing a theme's palette.
            GLES30.glBlendFunc(GLES30.GL_ZERO,GLES30.GL_CONSTANT_COLOR);GLES30.glBlendColor(.75f,.75f,.75f,1);
            Matrix.setIdentityM(mvp,0);color.use();color.matrix("u_Matrix",mvp);color.f("u_Opacity",1);home.draw(GLES30.GL_TRIANGLES);
        }
        GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);GLES30.glDisable(GLES30.GL_BLEND);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }
    private void drawGradient(Mesh mesh,float alpha){color.use();color.matrix("u_Matrix",mvp);color.f("u_Opacity",alpha);mesh.draw(GLES30.GL_TRIANGLES);}
    private void drawEffects(){
        float exit=motion.values[InfinityMotion.EXIT];if(exit>=.9999f)return;
        GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);effect.use();bindTexture(effect,"u_MaskUnit",textures[1]);
        GLES30.glBindVertexArray(layers.vao[0]);
        for(int i=0;i<theme.vertices.length;i++){
            float show=motion.reveal[i],center=(theme.vertices[i][4]+theme.vertices[i][8])*.5f;
            float turn=theme.index<=1?(theme.index==1&&i>=theme.vertices.length/2?60:40):theme.index>=4?60:0;
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,0,0,-6);Matrix.scaleM(model,0,2.4359999f,2.4359999f,1);
            Matrix.translateM(model,0,center,0,0);Matrix.rotateM(model,0,(1-show)*turn,0,1,0);Matrix.translateM(model,0,-center,0,0);
            Matrix.multiplyMM(mvp,0,projection,0,model,0);Matrix.scaleM(mvp,0,1+exit,1+exit,1);Matrix.translateM(mvp,0,.03f,0,0);
            effect.matrix("u_Matrix",mvp);effect.f("u_OverallAlpha",show*(1-exit));
            effect.f("a_MovePosition",motion.layerShift(i)+exit*exitX(i));effect.f("a_MoveYPosition",exit*exitY(i));
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN,i*6,6);
        }
    }
    private static final float[][] BLUE={{.48f,.4f},{.08f,.76f},{.4f,.28f},{.32f,.28f},{.18f,.14f},{-.24f,-.2f},{-.12f,-.56f},{-.36f,-.36f}};
    private static final float[][] PINK={{-1.9f,-.05f},{-1.1f,.3f},{-.77f,.03f},{-.46f,.75f},{.33f,-.16f},{.5f,.6f},{.8f,.01f},{.88f,.16f},{1.04f,0},{1.24f,.08f}};
    private static final float[][] SILVER={{1.89f,-.2f},{1.09f,.32f},{.76f,.02f},{.46f,.74f},{-.32f,-.16f},{-.49f,.6f},{-.8f,.09f},{.88f,.01f},{1.04f,0},{1.24f,.08f}};
    private float exitX(int i){return switch(theme.index){case 0->i<8?-.4f:.4f;case 1->i<8?-.5f:.5f;case 2->BLUE[i][0];case 4->PINK[i][0];case 5->SILVER[i][0];default->0;};}
    private float exitY(int i){return switch(theme.index){case 1->i<8?.5f:-.5f;case 2->BLUE[i][1];case 4->PINK[i][1];case 5->SILVER[i][1];default->0;};}
    private void drawStars(float aspect){
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        float stretch=((aspect/.48648f)-1)*.6f+1,side=theme.index==3||theme.index==5?-1:1;
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,side*.87f*stretch,.36f,-5);Matrix.scaleM(model,0,stretch,stretch,2);
        Matrix.rotateM(model,0,36,1,0,0);Matrix.rotateM(model,0,4,0,0,1);Matrix.rotateM(model,0,motion.rotation(),0,1,0);Matrix.multiplyMM(mvp,0,projection,0,model,0);
        InfinityParallax.foreground(mvp,motion);
        particle.use();particle.matrix("u_Matrix",mvp);particle.f("u_ParticleAlpha",motion.starAlpha());
        particle.f("u_PlusAlpha",motion.values[InfinityMotion.PLUS]);particle.f("u_ParticleDistance",.5f*width/1440f);stars.draw(GLES30.GL_POINTS);
    }
    private static void bindTexture(AssetGl shader,String uniform,int texture){GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);GLES30.glUniform1i(shader.at(uniform),0);}
    public void close(){for(Mesh mesh:meshes)mesh.close();meshes.clear();for(AssetGl shader:programs)shader.close();programs.clear();GLES30.glDeleteTextures(2,textures,0);textures[0]=textures[1]=0;}
    private static final class Mesh {
        final int[] vao=new int[1],vbo=new int[1];int count;
        void draw(int mode){GLES30.glBindVertexArray(vao[0]);GLES30.glDrawArrays(mode,0,count);}
        void close(){GLES30.glDeleteBuffers(1,vbo,0);GLES30.glDeleteVertexArrays(1,vao,0);}
    }
}
