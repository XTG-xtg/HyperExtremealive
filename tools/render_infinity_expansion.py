"""Desktop GL verification using packaged expansion data; not phone screenshots."""
from pathlib import Path
import json,sys,math
ROOT=Path(__file__).resolve().parents[1]
reference=ROOT/'build/infinity-integration-20261114/render_reference.py'
# Reuse the already validated matrix/texture/GL helpers, without its rendering loop.
namespace={'__file__':str(reference)}
exec(compile(reference.read_text().split('stats=[]')[0],str(reference),'exec'),namespace)
globals().update({k:v for k,v in namespace.items() if not k.startswith('__')})
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/infinity-expansion-20261115/previews';OUT.mkdir(exist_ok=True)
ASSETS=ROOT/'app/src/main/assets/infinity'

def use_matrix(shader,value,name='u_Matrix'):
    shader[name].write(np.asarray(value.T,dtype='f4').tobytes())

def optional(shader,name,value):
    if name in shader:shader[name].value=value

def programs(path,v,f):
    return ctx.program(vertex_shader=shader(ASSETS/path/v,True),fragment_shader=shader(ASSETS/path/f,False))

plain=programs('n8-shaders','alpha_texture_vertex_shader.glsl','alpha_texture_fragment_shader.glsl')
masked=programs('n8-shaders','single_draw_vertex_shader.glsl','single_draw_fragment_shader.glsl')
np_program=programs('n8-shaders','particle_move_vertex_shader.glsl','particle_fragment_shader.glsl')
dot=programs('polygon-shaders','dot_vert_shader.glsl','dot_frag_shader.glsl')
line=programs('polygon-shaders','line_vert_shader.glsl','line_frag_shader.glsl')
face=programs('polygon-shaders','triangle_vert_shader.glsl','triangle_frag_shader.glsl')

def n8_stars():
    r=np.random.default_rng(803);data=[]
    for i in range(3000):
        bottom=.8 if i>2100 else .5 if i>1500 else .3 if i>900 else 0
        size=((r.random()+.1)*3 if i>1000 else 7 if i%200==0 else (r.random()+.1)*5)*W/1440
        data.extend([1000+r.random()*2.4,1.28*(bottom+r.random()*(1-bottom)),1000+r.random()*6,size,.8+r.random()*.2,i%2==0,1])
    return mesh(np_program,data,'3f 1f 1f 1f 1f',['a_Position','a_PointSize','a_PointAlpha','a_PlusAlpha','a_LayerIndex'])
n8_cloud=n8_stars()
stats=[]
styles=json.loads((OUT.parent/'extracted-catalog.json').read_text(encoding='utf-8'))
for style in styles:
    folder=ASSETS/style['slug'];d=json.loads((folder/'theme.json').read_text())
    if d['kind']=='n8':
        atlas_tex=tex(ROOT/'app/src/main/assets'/d['atlas'],True)
        bg=mesh(texture,d['background'],'2f 2f 8x',['a_Position','a_TextureCoordinates'])
        hg=gradient(d['home_positions'],d['home_colors']);ag=gradient(d['aod_positions'],d['aod_colors'])
        layers=[]
        for layer_data in d['layers']:
            meshes=[]
            for part in layer_data['components']:
                p=masked if part['masked'] else plain
                fmt='2f 2f 2f' if part['masked'] else '2f 2f 8x'
                names=['a_Position','a_TextureCoordinates']+(['a_TextureCoordinatesMask'] if part['masked'] else [])
                meshes.append(mesh(p,part['vertices'],fmt,names))
            layers.append(meshes)
    else:
        shared=ROOT/'app/src/main/assets'/d['directory']
        lock_tex=tex(shared/'lock.webp');home_tex=tex(shared/'home.webp')
        pts=np.fromfile(shared/'points.bin',dtype='<f4').reshape(-1,10).copy()
        fcs=np.fromfile(shared/'faces.bin',dtype='<f4').reshape(-1,10).copy()
        lns=np.fromfile(shared/'lines.bin',dtype='<f4').reshape(-1,10).copy()
        for data in (pts,fcs,lns):data[:,:3]*=.64*W/1080
        dots=mesh(dot,pts,'3f 4f 1f 1f 1f',['aPosition','aColor','aGroup','aStartTime','aEndTime'])
        facets=mesh(face,fcs,'3f 4f 12x',['aPosition','aColor'])
        edges=mesh(line,lns,'3f 4f 12x',['aPosition','aColor'])
        near=max(W,H)/2
        proj=np.array([[4*near/W,0,0,0],[0,4*near/H,0,0],[0,0,-2,-3*near],[0,0,-1,0]])@translate(0,0,-near*2)
        r=np.random.default_rng(103);star_data=[]
        for i in range(100):
            rgba=pts[r.integers(len(pts)),3:7]
            start=r.random()*.4;star_data.extend([(r.random()-.5)*W,(r.random()-.5)*H,near*(.25+r.random()*.5),*rgba,r.integers(5),start,start+.6])
        dust=mesh(dot,star_data,'3f 4f 1f 1f 1f',['aPosition','aColor','aGroup','aStartTime','aEndTime'])
    for scene in range(3):
        fbo.clear(0,0,0,1);ctx.blend_func=(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA)
        if d['kind']=='n8':
            PP=P.copy();PP[2,2]=-9/7;PP[2,3]=-16/7
            M=PP@translate(0,0,-2.3)@scale(aspect*1.05,1.05,1)
            use_matrix(color,M);color['u_Opacity']=1;(ag if scene==0 else hg).render(gl.TRIANGLES)
            if scene==1:
                atlas_tex.use(0);use_matrix(texture,M);texture['u_Opacity']=1;texture['u_TextureUnit']=0;bg.render(gl.TRIANGLE_FAN)
                ctx.blend_func=(gl.ONE,gl.ONE_MINUS_SRC_ALPHA)
                for i in reversed(range(len(layers))):
                    ld=d['layers'][i];M=PP@translate(*ld['position'],-4.95)
                    for j,part in enumerate(ld['components']):
                        if part['role']>=2:continue
                        p=masked if part['masked'] else plain;use_matrix(p,M);p['u_Alpha']=ld['opacity'];p['u_TextureUnit']=0
                        optional(p,'u_MaskMoveX',ld['position'][0]*part['mask_move'][0]);optional(p,'u_MaskMoveY',-ld['position'][1]*part['mask_move'][1]);layers[i][j].render(gl.TRIANGLE_FAN)
            ctx.blend_func=(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA)
            use_matrix(np_program,PP@translate(-1.2,.15,-9)@scale(1,1,1.5)@rotate(18,'x'))
            np_program['u_Alpha']=.5 if scene==2 else .8;np_program['u_PlusAlpha']=1 if scene==2 else 0
            np_program['u_Move']=(0,0,0);np_program['u_Max']=(2.4,1.28,6,1);n8_cloud.render(gl.POINTS)
        else:
            mode=1 if scene==0 else d['lock_mode'] if scene==1 else d['home_mode']
            if mode>=2:
                (lock_tex if mode==2 else home_tex).use(0);use_matrix(texture,np.eye(4));texture['u_Opacity']=1;texture['u_TextureUnit']=0;quad.render(gl.TRIANGLE_STRIP)
            s=1 if mode==1 else 1.1 if mode==2 else 1.5;M=proj@scale(s,s,s)@rotate(35 if mode==1 else 0 if mode==2 else -20,'y')
            def draw_poly(p,obj,draw_mode,alpha,bright):
                use_matrix(p,M,'uWorldMatrix');p['uGlobalAlpha']=alpha
                optional(p,'uOverBrightness',bright);optional(p,'uNear',near);optional(p,'uPointSizeRatio',.64*W/1080*3);optional(p,'uGroupAlpha',(1,1,1,1,1));obj.render(draw_mode)
            if mode==1:
                draw_poly(dot,dust,gl.POINTS,1,.4);draw_poly(dot,dots,gl.POINTS,1,.4);draw_poly(line,edges,gl.LINES,.8,.1)
            elif mode==2:draw_poly(face,facets,gl.TRIANGLES,.5,0)
        raw=fbo.read(components=3);im=Image.frombytes('RGB',(W,H),raw).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        out=OUT/(style['slug']+f'-{scene}.png');im.save(out)
        if d['kind']=='n8' and scene==1:
            preview=im.copy();preview.thumbnail((360,740));preview.save(folder/'preview.jpg',quality=92)
        pixels=np.asarray(im);stats.append({'variant':style['variant'],'scene':scene,'mean':float(pixels.mean()),'lit_pixels':int(np.count_nonzero(pixels.max(axis=2)>8)),'image':str(out.relative_to(ROOT))})
        assert np.count_nonzero(pixels.max(axis=2)>8)>150,style
    if d['kind']=='n8':
        for group in layers:
            for m in group:m.release()
        bg.release();hg.release();ag.release();atlas_tex.release()
    else:
        for m in (dots,facets,edges,dust):m.release()
        lock_tex.release();home_tex.release()

font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
sheet=Image.new('RGB',(4*W,4*(H+48)),(20,22,26));draw=ImageDraw.Draw(sheet)
for i,style in enumerate(styles):
    im=Image.open(OUT/(style['slug']+'-1.png'));x=(i%4)*W;y=(i//4)*(H+48);sheet.paste(im,(x,y));draw.text((x+12,y+H+12),style['name'],font=font,fill='white')
sheet.thumbnail((1296,2808));sheet.save(OUT/'expansion-contact-sheet.jpg',quality=93)
(OUT.parent/'desktop-gl-validation.json').write_text(json.dumps({'scope':'Desktop GL, not Xiaomi device validation','programs_compiled':8,'frames':stats},indent=2))
print(json.dumps({'programs_compiled':8,'rendered_frames':len(stats),'contact_sheet':str(OUT/'expansion-contact-sheet.jpg')}))
