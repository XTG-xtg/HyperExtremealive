package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.content.res.Resources;
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

/** N8 masked tangrams and Infinity 1.0 OBJ facets using the existing EGL host. */
final class InfinityExpansionRenderer implements WallpaperRenderer {
    private final InfinityExpansionTheme theme;
    private final InfinityMotion motion;
    private final int width,height;
    private final boolean dark;
    private final ArrayList<AssetGl> shaders=new ArrayList<>();
    private final ArrayList<Mesh> meshes=new ArrayList<>();
    private final ArrayList<Integer> textures=new ArrayList<>();
    private final float[] projection=new float[16],model=new float[16],mvp=new float[16],view=new float[16],combined=new float[16];
    private AssetGl texture,color,masked,plain,particle,dot,line,face;
    private Mesh background,home,aod,stars,points,faces,lines;
    private Mesh[][] components;
    private int atlas,lockTexture,homeTexture;

    InfinityExpansionRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene,InfinityMotion shared)throws IOException {
        this.width=width;this.height=height;this.dark=dark;
        theme=new InfinityExpansionTheme(assets,variant);motion=shared!=null?shared:InfinityMotion.create(variant,scene);
        try {
            texture=program(assets,"infinity/shaders/","texture_vertext_shader.glsl","texture_fragment_shader.glsl");
            if(theme.polygon)initPolygon(assets);else initN8(assets);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
        }catch(IOException|RuntimeException failure){close();throw failure;}
    }
    private void initN8(AssetManager assets)throws IOException {
        String path="infinity/n8-shaders/";
        color=program(assets,"infinity/shaders/","color_vertex_shader.glsl","color_fragment_shader.glsl");
        masked=program(assets,path,"single_draw_vertex_shader.glsl","single_draw_fragment_shader.glsl");
        plain=program(assets,path,"alpha_texture_vertex_shader.glsl","alpha_texture_fragment_shader.glsl");
        particle=program(assets,path,"particle_move_vertex_shader.glsl","particle_fragment_shader.glsl");
        atlas=loadTexture(assets,theme.atlas);
        background=mesh(theme.background,6,texture,new String[]{"a_Position","a_TextureCoordinates"},new int[]{2,2});
        home=gradient(theme.homePositions,theme.homeColors);aod=gradient(theme.aodPositions,theme.aodColors);
        components=new Mesh[theme.layers.length][];
        for(int i=0;i<components.length;i++){
            InfinityExpansionTheme.Component[] data=theme.layers[i].components;components[i]=new Mesh[data.length];
            for(int j=0;j<data.length;j++)components[i][j]=mesh(data[j].vertices,6,data[j].masked?masked:plain,
                new String[]{"a_Position","a_TextureCoordinates","a_TextureCoordinatesMask"},new int[]{2,2,2});
        }
        stars=mesh(n8Particles(width),7,particle,new String[]{"a_Position","a_PointSize","a_PointAlpha","a_PlusAlpha","a_LayerIndex"},new int[]{3,1,1,1,1});
        Matrix.perspectiveM(projection,0,45,width/(float)height,1,8);
    }
    private void initPolygon(AssetManager assets)throws IOException {
        if(!(motion instanceof InfinityGeometryMotion))throw new IOException("Wrong Infinity motion profile");
        String path="infinity/polygon-shaders/";
        dot=program(assets,path,"dot_vert_shader.glsl","dot_frag_shader.glsl");
        line=program(assets,path,"line_vert_shader.glsl","line_frag_shader.glsl");
        face=program(assets,path,"triangle_vert_shader.glsl","triangle_frag_shader.glsl");
        lockTexture=loadTexture(assets,theme.atlas+"lock.webp");homeTexture=loadTexture(assets,theme.atlas+"home.webp");
        background=mesh(new float[]{-1,-1,0,1,1,-1,1,1,-1,1,0,0,1,1,1,0},4,texture,
            new String[]{"a_Position","a_TextureCoordinates"},new int[]{2,2});
        String[] attributes={"aPosition","aColor","aGroup","aStartTime","aEndTime"};int[] sizes={3,4,1,1,1};
        float[] scaledPoints=scaled(theme.points),scaledFaces=scaled(theme.faces),scaledLines=scaled(theme.lines);
        points=mesh(scaledPoints,10,dot,attributes,sizes);faces=mesh(scaledFaces,10,face,attributes,sizes);lines=mesh(scaledLines,10,line,attributes,sizes);
        stars=mesh(polygonStars(scaledPoints),10,dot,attributes,sizes);
        float near=Math.max(width,height)*.5f;
        Matrix.frustumM(projection,0,-width*.25f,width*.25f,-height*.25f,height*.25f,near,near*3);
        Matrix.setLookAtM(view,0,0,0,near*2,0,0,-1,0,1,0);
        Matrix.multiplyMM(combined,0,projection,0,view,0);System.arraycopy(combined,0,projection,0,16);
    }
    private float[] scaled(float[] original){
        float[] result=original.clone();float scale=.64f*width/1080;
        for(int i=0;i<result.length;i+=10)for(int j=0;j<3;j++)result[i+j]*=scale;return result;
    }
    static float[] n8Particles(int width){
        Random r=new Random(0x4e38494e46494eL);float[] result=new float[3000*7];
        for(int i=0;i<3000;i++){
            int at=i*7;result[at]=1000+r.nextFloat()*2.4f;
            float bottom=i>2100?.8f:i>1500?.5f:i>900?.3f:0;
            result[at+1]=1.28f*(bottom+r.nextFloat()*(1-bottom));result[at+2]=1000+r.nextFloat()*6;
            result[at+3]=(i>1000?(r.nextFloat()+.1f)*3:i%200==0?7:(r.nextFloat()+.1f)*5)*width/1440f;
            result[at+4]=.8f+r.nextFloat()*.2f;result[at+5]=i%2==0?1:0;result[at+6]=1;
        }return result;
    }
    private float[] polygonStars(float[] data){
        Random r=new Random(0x504f4c59494e46L);float[] result=new float[100*10];
        for(int i=0;i<100;i++){
            int at=i*10,source=r.nextInt(data.length/10)*10;
            result[at]=(r.nextFloat()-.5f)*width;result[at+1]=(r.nextFloat()-.5f)*height;
            result[at+2]=Math.max(width,height)*(.125f+r.nextFloat()*.25f);
            System.arraycopy(data,source+3,result,at+3,4);result[at+7]=r.nextInt(5);result[at+8]=r.nextFloat()*.4f;result[at+9]=result[at+8]+.6f;
        }return result;
    }
    public WallpaperMotion motion(){return motion;}
    public void render(){
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        if(theme.polygon)renderPolygon();else renderN8();
        GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);GLES30.glDisable(GLES30.GL_BLEND);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }
    private float dim(){return dark?.75f:1;}
    private void renderN8(){
        float aspect=width/(float)height;
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,0,0,-2.3f);Matrix.scaleM(model,0,aspect*1.05f,1.05f,1);Matrix.multiplyMM(mvp,0,projection,0,model,0);
        InfinityParallax.background(mvp,motion);
        drawGradient(home,motion.values[InfinityMotion.LOCK]+motion.values[InfinityMotion.HOME]);
        if(motion.values[InfinityMotion.LOCK]>0){texture.use();texture.matrix("u_Matrix",mvp);texture.f("u_Opacity",motion.values[InfinityMotion.LOCK]*dim());bind(texture,"u_TextureUnit",atlas);background.draw(GLES30.GL_TRIANGLE_FAN);}
        // Add weighted AOD color without attenuating the fading desktop a second time.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE);
        drawGradient(aod,motion.values[InfinityMotion.AOD]);
        GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        float exit=motion.values[InfinityMotion.EXIT],density=Resources.getSystem().getDisplayMetrics().density;
        for(int i=components.length-1;i>=0;i--){
            InfinityExpansionTheme.Layer data=theme.layers[i];float shift=motion.shift[i]*6*theme.tiltGain,reveal=motion.reveal[i];
            float x=data.position[0]+data.entrance[0]*density/1440*(1-reveal)+data.exit[0]*density/1440*exit+shift;
            float y=data.position[1]-data.entrance[1]*density/1440*(1-reveal)-data.exit[1]*density/1440*exit+(theme.variant==810?shift:-shift);
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,-4.95f);Matrix.scaleM(model,0,1+exit,1+exit,1);Matrix.multiplyMM(mvp,0,projection,0,model,0);
            for(int j=0;j<components[i].length;j++){
                InfinityExpansionTheme.Component component=data.components[j];
                float opacity=component.role<=1?data.opacity:component.role==2?Math.max(0,-shift)*4:Math.max(0,shift)*4;
                float alpha=Math.max(0,1-2*exit)*reveal*opacity*dim();if(alpha<=0)continue;
                AssetGl shader=component.masked?masked:plain;shader.use();shader.matrix("u_Matrix",mvp);shader.f("u_Alpha",alpha);
                shader.f("u_MaskMoveX",x*component.maskMove[0]);shader.f("u_MaskMoveY",-y*component.maskMove[1]);bind(shader,"u_TextureUnit",atlas);components[i][j].draw(GLES30.GL_TRIANGLE_FAN);
            }
        }
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,-1.2f,.15f,-9);Matrix.scaleM(model,0,1,1,1.5f);Matrix.rotateM(model,0,18,1,0,0);Matrix.multiplyMM(mvp,0,projection,0,model,0);
        InfinityParallax.foreground(mvp,motion);
        particle.use();particle.matrix("u_Matrix",mvp);particle.f("u_Alpha",motion.starAlpha()*dim());particle.f("u_PlusAlpha",1-motion.values[InfinityMotion.PLUS]);
        GLES30.glUniform3f(particle.at("u_Move"),motion.rotation()*.03f,0,-motion.rotation()*.024f);GLES30.glUniform4f(particle.at("u_Max"),2.4f,1.28f,6,1);stars.draw(GLES30.GL_POINTS);
    }
    private void renderPolygon(){
        InfinityGeometryMotion state=(InfinityGeometryMotion)motion;float[] g=state.geometry;
        Matrix.setIdentityM(mvp,0);InfinityParallax.background(mvp,motion);texture.use();texture.matrix("u_Matrix",mvp);
        // Composite the old image first so opaque source backgrounds never uncover black.
        float total=Math.min(1,g[InfinityGeometryMotion.LOCK_BG]+g[InfinityGeometryMotion.HOME_BG]);
        if(total>0){bind(texture,"u_TextureUnit",lockTexture);texture.f("u_Opacity",total*dim());background.draw(GLES30.GL_TRIANGLE_STRIP);}
        if(g[InfinityGeometryMotion.HOME_BG]>0){bind(texture,"u_TextureUnit",homeTexture);texture.f("u_Opacity",g[InfinityGeometryMotion.HOME_BG]*dim());background.draw(GLES30.GL_TRIANGLE_STRIP);}
        Matrix.setIdentityM(model,0);float scale=g[InfinityGeometryMotion.SCALE];Matrix.scaleM(model,0,scale,scale,scale);
        Matrix.rotateM(model,0,g[InfinityGeometryMotion.ROTATE]+state.rotationY(),0,1,0);
        Matrix.rotateM(model,0,-motion.desktopY()*8,1,0,0);Matrix.multiplyMM(mvp,0,projection,0,model,0);
        InfinityParallax.foreground(mvp,motion);
        float starGain=motion.aodStarGain()+.55f*motion.starEmphasis();
        drawPolygon(dot,stars,GLES30.GL_POINTS,g[InfinityGeometryMotion.STARS]*starGain,g[InfinityGeometryMotion.BRIGHT],state.groups);
        drawPolygon(dot,points,GLES30.GL_POINTS,Math.max(g[InfinityGeometryMotion.DOTS],state.pulseDots())*starGain,g[InfinityGeometryMotion.BRIGHT],state.groups);
        drawPolygon(line,lines,GLES30.GL_LINES,Math.max(g[InfinityGeometryMotion.LINES],state.pulseLines()),.1f,state.groups);
        drawPolygon(face,faces,GLES30.GL_TRIANGLES,g[InfinityGeometryMotion.FACES],0,state.groups);
    }
    private void drawPolygon(AssetGl shader,Mesh mesh,int mode,float alpha,float brightness,float[] groups){
        if(alpha<=0)return;shader.use();shader.matrix("uWorldMatrix",mvp);shader.f("uGlobalAlpha",alpha*dim());shader.f("uOverBrightness",brightness);
        shader.f("uPointSizeRatio",.64f*width/1080*3);shader.f("uNear",Math.max(width,height)*.5f);
        GLES30.glUniform1fv(shader.at("uGroupAlpha"),5,groups,0);mesh.draw(mode);
    }
    private void drawGradient(Mesh mesh,float alpha){if(alpha<=0)return;color.use();color.matrix("u_Matrix",mvp);color.f("u_Opacity",alpha*dim());mesh.draw(GLES30.GL_TRIANGLES);}
    private Mesh gradient(float[] positions,float[][] colors)throws IOException {
        float[] data=new float[(positions.length-1)*30];int at=0;int[] ends={0,1,1,0,1,0};float[] xs={-1,-1,1,-1,1,1};
        for(int row=0;row<positions.length-1;row++)for(int k=0;k<6;k++){
            int p=row+ends[k];data[at++]=xs[k];data[at++]=1-2*positions[p]/2960;for(int c=0;c<3;c++)data[at++]=colors[p][c]/255;
        }return mesh(data,5,color,new String[]{"a_Position","a_Color"},new int[]{2,3});
    }
    private AssetGl program(AssetManager assets,String path,String vertex,String fragment)throws IOException {
        AssetGl shader=new AssetGl(assets,path+vertex,path+fragment);shaders.add(shader);return shader;
    }
    private Mesh mesh(float[] data,int stride,AssetGl shader,String[] names,int[] sizes)throws IOException {
        if(data.length==0||data.length%stride!=0)throw new IOException("Invalid Infinity mesh");
        Mesh mesh=new Mesh();meshes.add(mesh);mesh.count=data.length/stride;
        GLES30.glGenVertexArrays(1,mesh.vao,0);GLES30.glBindVertexArray(mesh.vao[0]);GLES30.glGenBuffers(1,mesh.vbo,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,mesh.vbo[0]);
        FloatBuffer buffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(data).flip();GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,data.length*4,buffer,GLES30.GL_STATIC_DRAW);
        int offset=0;for(int i=0;i<names.length;i++){
            int location=GLES30.glGetAttribLocation(shader.id,names[i]);
            if(location>=0){GLES30.glEnableVertexAttribArray(location);GLES30.glVertexAttribPointer(location,sizes[i],GLES30.GL_FLOAT,false,stride*4,offset*4);}
            else if(i==0)throw new IOException("Missing Infinity position attribute");offset+=sizes[i];
        }return mesh;
    }
    private int loadTexture(AssetManager assets,String path)throws IOException {
        Bitmap bitmap;try(InputStream input=assets.open(path)){bitmap=BitmapFactory.decodeStream(input);}
        if(bitmap==null)throw new IOException("Invalid Infinity texture: "+path);
        try {
            int[] max=new int[1];GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE,max,0);if(bitmap.getWidth()>max[0]||bitmap.getHeight()>max[0])throw new IOException("Infinity texture exceeds GPU limit");
            int[] texture=new int[1];GLES30.glGenTextures(1,texture,0);textures.add(texture[0]);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture[0]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);return texture[0];
        }finally{bitmap.recycle();}
    }
    private static void bind(AssetGl shader,String name,int texture){GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);GLES30.glUniform1i(shader.at(name),0);}
    public void close(){
        for(Mesh mesh:meshes){GLES30.glDeleteBuffers(1,mesh.vbo,0);GLES30.glDeleteVertexArrays(1,mesh.vao,0);}meshes.clear();
        for(AssetGl shader:shaders)shader.close();shaders.clear();for(int texture:textures)GLES30.glDeleteTextures(1,new int[]{texture},0);textures.clear();
    }
    private static final class Mesh {
        final int[] vao=new int[1],vbo=new int[1];int count;
        void draw(int mode){GLES30.glBindVertexArray(vao[0]);GLES30.glDrawArrays(mode,0,count);}
    }
}
