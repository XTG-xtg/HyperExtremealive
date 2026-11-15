"""Export user-supplied Infinity/N8 assets and numeric data from JADX output.

Run after decompiling and extracting res/raw and res/drawable into --work.
Original assets remain in the review directory. Packaged WebP is lossless.
"""
from pathlib import Path
import argparse, hashlib, json, re, shutil, struct
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('--work', type=Path, required=True)
parser.add_argument('--assets', type=Path, required=True)
args = parser.parse_args()
work, assets = args.work, args.assets
assets.mkdir(parents=True, exist_ok=True)

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

def array(text):
    text = re.sub(r'new (?:float|int|String|boolean)(?:\[\])+', '', text)
    text = re.sub(r'new (?:float|int)\[0\]', '[]', text)
    text = re.sub(r'(?<=\d)f\b', '', text)
    return json.loads(text.replace('{', '[').replace('}', ']'))

def field(text, name):
    return array(re.search(r'(?:this\.|\b)' + name + r' = ([^;]+);', text).group(1))

def webp(source, target):
    original = Image.open(source).convert('RGBA')
    if target.exists() and Image.open(target).convert('RGBA').tobytes() == original.tobytes():
        return
    original.save(target, 'WEBP', lossless=True, quality=100, method=6, exact=True)
    restored = Image.open(target).convert('RGBA')
    assert original.tobytes() == restored.tobytes(), target

def uv(frame, aw, ah):
    """Center, BL, BR, TR, TL, BL; exact TexturePacker orientation."""
    f=frame['frame']; x,y,w,h=(f[k] for k in ('x','y','w','h'))
    if frame['rotated']:
        return [(x+h/2,y+w/2),(x,y),(x,y+w),(x+h,y+w),(x+h,y),(x,y)]
    return [(x+w/2,y+h/2),(x,y+h),(x+w,y+h),(x+w,y),(x,y),(x,y+h)]

def geometry(size, frame, atlas_size, mask=None):
    w,h=size;aw,ah=atlas_size
    xy=[(0,0),(-w/1440,-h/1440),(w/1440,-h/1440),
        (w/1440,h/1440),(-w/1440,h/1440),(-w/1440,-h/1440)]
    tex=uv(frame,aw,ah)
    if mask:
        f=mask['frame'];mx,my,mw,mh=(f[k] for k in ('x','y','w','h'))
        # Corresponds to c.c.a.i.c.l.e's logical 2960 x 2960 color mask.
        px=(2960-w)/2/(2960/mh);py=(2960-h)/2/(2960/mw)
        left,right=mx+px,mx+mh-px;top,bottom=my+py,my+mw-py
        if mask['rotated']:
            mask_uv=[(mx+mh/2,my+mw/2),(left,top),(left,bottom),(right,bottom),(right,top),(left,top)]
        else:
            mask_uv=[(mx+mh/2,my+mw/2),(left,bottom),(right,bottom),(right,top),(left,top),(left,bottom)]
    else: mask_uv=tex
    return [v for p,t,m in zip(xy,tex,mask_uv) for v in (*p,t[0]/aw,t[1]/ah,m[0]/aw,m[1]/ah)]

catalog=[]
for index,(slug,package,label) in enumerate([('black','c','黑色'),('blue','d','蓝色'),('gold','e','金色'),('orchid','f','兰紫')]):
    folder=assets/('n8-'+slug);folder.mkdir(exist_ok=True)
    source=work/'n8-jadx/sources/c/c/a/i/c/n'/package
    bg=(source/'a.java').read_text();aod=(source/'b.java').read_text();fx=(source/'c.java').read_text()
    values={name:field(fx,name) for name in 'opqrstuvwxyzAB'}
    atlas=work/'n8-raw/res/raw'/f'infinity_lockscreen_{slug}_map.png'
    mapping=json.loads(atlas.with_name(atlas.stem+'_data.json').read_text())
    frames={f['filename']:f for f in mapping['frames']};size=Image.open(atlas).size
    assert tuple(mapping['meta']['size'][k] for k in ('w','h'))==size
    webp(atlas,folder/'atlas.webp')
    layer_data=[]
    for i in range(len(values['s'])):
        base=frames[values['v'][i]];mask=frames[values['x'][i]] if values['x'][i] else None
        components=[{'role':0,'masked':bool(mask),'vertices':geometry(values['u'][i],base,size,mask),
                     'mask_move':values['z'][i] if mask else [0,0]}]
        if values['o'][i]:components.append({'role':1,'masked':False,'vertices':geometry(values['u'][i],base,size),'mask_move':[0,0]})
        for role,name in ((2,values['w'][i]),(3,values['y'][i])):
            if name:components.append({'role':role,'masked':False,'vertices':geometry(values['u'][i],frames[name],size),'mask_move':[0,0]})
        layer_data.append({'opacity':values['r'][i],'position':[(values['t'][i][0]-1480)/720,(1480-values['t'][i][1])/720],
                           'entrance':values['q'][i],'exit':values['p'][i], 'components':components})
    background_name=re.search(r'return "([^"]+)";',bg).group(1)
    background=geometry([1440,1440],frames[background_name],size)
    for i in range(0,len(background),6):background[i:i+2]=[background[i]*1,background[i+1]*1]
    data={'variant':807+index,'kind':'n8','source_package':f'c.c.a.i.c.n.{package}',
          'atlas':'infinity/n8-'+slug+'/atlas.webp','atlas_size':list(size),
          'home_positions':field(bg,'n'),'home_colors':field(bg,'m'),
          'aod_positions':field(aod,'j')[0],'aod_colors':field(aod,'i')[0],
          'background':background,'layers':layer_data,
          'tilt_gain':float(re.search(r'this.F = ([\d.]+)f;',fx).group(1))}
    write_json(folder/'theme.json',data)
    catalog.append({'variant':807+index,'slug':'n8-'+slug,'name':'N8 · '+label,'layers':len(layer_data)})

# Original custom OBJ: v=position, c=ARGB255, g=per-corner color indices, f=1-based triangle indices.
source=work/'infinity-jadx/sources/com/horizon/infinity/inf'
r=(source.parent/'R.java').read_text()
constants={name:str(int(value,0)) for name,value in re.findall(r'public static final int (\w+) = (0x[\da-f]+|\d+);',r)}
constants.update({'MASK':'127','SOURCE_KEYBOARD':'257','DEFAULT_DRAG_ANIMATION_DURATION':'200','DEFAULT_SWIPE_ANIMATION_DURATION':'250'})
for color_index,(slug,label) in enumerate([('black','黑色'),('blue','蓝色'),('gold','金色'),('orchid','兰紫'),('purple','紫色')]):
    resource=(source/f'InfinityWallpaper{slug.title()}Resource.java').read_text()
    obj_name=re.search(r'return .*?R.raw.(.*?);',resource).group(1)
    text=re.search(r'groupData = ([^;]+);',resource).group(1)
    text=re.sub(r'(?:R.styleable|WorkQueueKt|InputDeviceCompat|ItemTouchHelper.Callback)\.(\w+)',lambda m:constants[m[1]],text)
    groups=array(text)
    obj=work/'infinity-raw/res/raw'/(obj_name+'.obj')
    vertices=[];faces=[];colors=[];corner_colors=[]
    for line in obj.read_text().splitlines():
        row=line.split()
        if not row:continue
        if row[0]=='v':vertices.append([float(x) for x in row[1:4]])
        elif row[0]=='f':faces.append([int(x)-1 for x in row[1:4]])
        elif row[0]=='c':
            a,red,green,blue=[float(x)/255 for x in row[1:5]];colors.append([red,green,blue,a])
        elif row[0]=='g':corner_colors.append([int(x)-1 for x in row[1:4]])
    assert len(faces)==len(corner_colors)
    # Source parseData sets groups before populating faceIndexData: all indices
    # still reference vertex 0. Its group table even contains stale face IDs.
    # Preserve the displayed source grouping; keep the unused table in metadata.
    vertex_groups=[0]*len(vertices)
    vertex_groups[0]=len(groups)-1
    point_colors=[[0,0,0,0] for _ in vertices]
    face_data=[];edges=set()
    for face,cs in zip(faces,corner_colors):
        for vid,ci in zip(face,cs):
            rgba=colors[ci]
            if point_colors[vid][3]==0:point_colors[vid]=rgba
            face_data.extend([*vertices[vid],*rgba,0,0,1])
        for i in range(3):edges.add(tuple(sorted((face[i],face[(i+1)%3]))))
    # Deterministic stagger, retaining the source's 0-.4 / .6-1 time range.
    points=[]
    for i,(position,rgba,group) in enumerate(zip(vertices,point_colors,vertex_groups)):
        start=((i*0.61803398875)%1)*.4
        points.extend([*position,*rgba,group,start,start+.6])
    lines=[v for edge in sorted(edges) for vid in edge for v in points[vid*10:vid*10+10]]
    shared=assets/'polygon'/slug;shared.mkdir(parents=True,exist_ok=True)
    mesh={'points':points,'faces':face_data,'lines':lines,'vertex_count':len(vertices),'face_count':len(faces),'groups':groups,'source_obj':obj_name}
    for name,values in [('points',points),('faces',face_data),('lines',lines)]:
        (shared/(name+'.bin')).write_bytes(struct.pack('<'+'f'*len(values),*values))
    write_json(shared/'mesh.json',{k:v for k,v in mesh.items() if k not in ('points','faces','lines')})
    for state in ('lock','home'):
        webp(work/'infinity-raw/res/drawable'/f'wallpaper_{slug}_{state}.png',shared/(state+'.webp'))
    for profile in range(2):
        variant=811+color_index*2+profile;style=f'polygon-{slug}-{profile+1}'
        folder=assets/style;folder.mkdir(exist_ok=True)
        data={'variant':variant,'kind':'polygon','directory':'infinity/polygon/'+slug+'/',
              'lock_mode':1+profile,'home_mode':2+profile,'vertex_count':len(vertices),'face_count':len(faces)}
        write_json(folder/'theme.json',data)
        preview=work/'infinity-raw/res/drawable'/f'wallpaper_{slug}_{"lock" if profile==0 else "home"}.png'
        im=Image.open(preview);im.thumbnail((360,740));im.save(folder/'preview.jpg',quality=92)
        catalog.append({'variant':variant,'slug':style,'name':'几何 · '+label+('星空' if profile==0 else '渐变'),'layers':0})

for kind in ('n8','infinity'):
    target=assets/('n8-shaders' if kind=='n8' else 'polygon-shaders');target.mkdir(exist_ok=True)
    for p in (work/(kind+'-raw/res/raw')).glob('*shader*'):
        if p.suffix!='.obj':shutil.copy2(p,target/p.name)
write_json(work/'extracted-catalog.json',catalog)
write_json(work/'packaged-asset-hashes.json',{
    p.relative_to(assets).as_posix():{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
    for p in assets.rglob('*') if p.is_file() and (p.parts[-2].startswith(('n8-','polygon-')) or '/polygon/' in p.as_posix())})
print(json.dumps(catalog,ensure_ascii=False))
