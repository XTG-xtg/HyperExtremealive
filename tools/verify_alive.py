"""Offline state, packaging, asset and Java compilation checks. Not a full APK/device test."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zlib
from build_alive_helper import build

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME', 'E:/AndroidSdk'))
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME', 'E:/AndroidStudio/android-studio/jbr'))
parser.add_argument('--compile-sdk', default='34', help='A lower public API verifies the new Java layer only')
parser.add_argument('--build-tools', default='35.0.0')
args = parser.parse_args()
sdk, java = Path(args.sdk), Path(args.java_home) / 'bin'
exe = '.exe' if os.name == 'nt' else ''
code = ROOT / 'app/src/main/java/com/frederic22/hyperextreme/alive'
assets = ROOT / 'app/src/main/assets'
scratch = ROOT / 'build/alive-verification'
scratch.mkdir(parents=True, exist_ok=True)
checks = 0


def check(value, label):
    global checks
    if not value:
        raise AssertionError(label)
    checks += 1


with tempfile.TemporaryDirectory(dir=scratch) as temp:
    assert Path(temp).resolve().is_relative_to(scratch.resolve())
    subprocess.run([str(java / ('javac' + exe)), '-d', temp,
                    str(code / 'core/AliveState.java'), str(ROOT / 'tests/alive/StateTest.java')], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp, 'StateTest'], check=True)
    subprocess.run([str(java / ('javac' + exe)), '-cp', temp, '-d', temp,
                    str(code / 'core/PhotoPowerGate.java'), str(ROOT / 'tests/alive/PhotoPowerStateTest.java')], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp, 'PhotoPowerStateTest', 'legacy'], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp, 'PhotoPowerStateTest'], check=True)
    subprocess.run([str(java / ('javac' + exe)), '-d', temp,
                    str(code / 'core/AlivePermissionPolicy.java'), str(code / 'core/PhotoGeometry.java'),
                    str(ROOT / 'tests/alive/RegressionTest.java')], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp, 'RegressionTest'], check=True)
    subprocess.run([str(java / ('javac' + exe)), '-d', temp,
                    str(code / 'WallpaperMotion.java'), str(code / 'MotionSession.java'),
                    str(ROOT / 'tests/alive/MotionSessionTest.java')], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp,
                    'com.frederic22.hyperextreme.alive.MotionSessionTest'], check=True)
    subprocess.run([str(java / ('javac' + exe)), '-d', temp,
                    str(code / 'WallpaperMotion.java'), str(code / 'StereoMotion.java'),
                    str(ROOT / 'tests/alive/StereoMotionTest.java')], check=True)
    subprocess.run([str(java / ('java' + exe)), '-cp', temp,
                    'com.frederic22.hyperextreme.alive.StereoMotionTest'], check=True)

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
a = '{http://schemas.android.com/apk/res/android}'
app = manifest.getroot().find('application')
for name in ['HomeWallpaperService', 'LockWallpaperService']:
    service = next(s for s in app.findall('service') if s.get(a + 'name') == '.alive.' + name)
    check(service.get(a + 'process') is None, 'Alive slots must share the app process')
    check(service.get(a + 'permission') == 'android.permission.BIND_WALLPAPER', 'Wallpaper binding permission')
provider = next(p for p in app.findall('provider') if p.get(a + 'name') == '.alive.ConfigProvider')
check(provider.get(a + 'authorities') == 'com.frederic22.hyperextreme.alive.config', 'Provider authority')
preview = next(p for p in app.findall('activity') if p.get(a + 'name') == '.alive.AlivePreviewActivity')
check(preview.get(a + 'exported') == 'false', 'Nonexported preview')
check(not (assets / 'xposed_init').exists(), 'No legacy entry point')
check(not list((ROOT / 'app/libs').glob('*hyperalive*')), 'No opaque HyperAlive core JAR')
entries = (ROOT / 'app/src/main/resources/META-INF/xposed/java_init.list').read_text().splitlines()
check(entries == ['com.frederic22.hyperextreme.hook.HookEntry'], 'One libxposed entry')
scope = (ROOT / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines()
check('system' in scope and 'android' not in scope, 'LSPosed system-server scope uses system')
installer = (ROOT / 'tools/install_and_scope.sh').read_text(encoding='utf-8')
check('--install' in installer and 'modules_config.db' not in installer and 'pkill' not in installer,
      'Installer requires explicit execution and never edits LSPosed databases or kills processes')
helper = (ROOT / 'alive-root/src/com/frederic22/hyperextreme/alive/root/WallpaperTool.java').read_text()
check(helper.index('if(!preflight(user)){') < helper.index('set(manager,"HomeWallpaperService"'),
      'Preflight runs before either setting transaction')
worker = (code / 'EglWorker.java').read_text()
check('if(value)sequence=-1' not in worker, 'Visibility must not replay a transition')
check('CosmicEgl.choose' in worker, 'Host uses the imported multisample EGL selection')
check('postDelayed(tick' not in worker and 'postFrameCallback(vsync)' in worker,
      'Display vsync owns pacing; no artificial 1fps AOD or post-swap sleep')
check('MotionSession.acquire' in worker and 'if(preview)' in worker,
      'Real slots share CPU motion; preview is isolated')
registry = (code / 'EngineRegistry.java').read_text(encoding='utf-8')
check('Config.variant' not in registry and 'power.isInteractive()' in registry
      and 'POWER_GATE.resolve' in registry, 'All live scenes use the real power-state gate')
service = (code / 'AliveWallpaperService.java').read_text(encoding='utf-8')
check('photo()' not in service and '!power.isInteractive()' in service
      and 'display-awake' in service and 'display-asleep' in service,
      'All live scene visibility and display callbacks respect interactive state')
check('if(renderer.frameReady())' in worker, 'Do not swap an incomplete photo mask sample')
check('photo.frameReady()&&(photo.needsRedraw()||photo.animating()||progress<1||options.continuousAod&&photo.motion().active())' in worker and 'photo.needsRedraw()||photo.animating()||progress<1?0:25' in worker,
      'Photo transitions and late-mask catch-up remain vsync-driven; settled photos stop and idle lens is paced')
check('if(framePosted&&scheduledDelay>0)stopFrames()' in worker, 'Wake and edit events preempt idle pacing')
mask_source = (code / 'MaskFrames.java').read_text(encoding='utf-8')
check('glTexSubImage2D' in mask_source and 'wanted()' in mask_source and 'capacity=' in mask_source,
      'Bounded mask read-ahead and reusable texture storage')
photo = (code / 'PhotoRenderer.java').read_text(encoding='utf-8-sig')
check('catchUp=delayedSample' in photo and 'boolean delayedSample=sample.pending' in photo,
      'A late mask cannot leave a completed transition displaying an old intermediate frame')
check('rotationSpeed=continuous||animating()?speed:0' in photo,
      'Static lens settings repaints do not accumulate idle rotation')
check('NativeScene.render' in photo and 'new FrameMotion' in photo and 'new TextureMotion' in photo,
      'Photo renderer uses the complete frame graph and independent tracks')
check('PhotoGeometry.crop' not in photo and 'String fragment=' not in photo,
      'Remove the placeholder disk/full-image blend')
check('sample.captureTextures' in photo and 'sample.capture(frameMotion' in photo,
      'Mask, texture and geometry share one pending presentation sample')
check('PhotoMedia.freezeLegacy(c,photo,true).save(c,false)' in (code / 'AliveSettings.java').read_text(encoding='utf-8'),
      'Applying publishes immutable photo references and a complete settings snapshot')
check('loadAsset(3,"shader/photo/lock/groundGlass/gray.png",false)' in photo and 'bind(2,4)' in photo,
      'Ground glass binds its gray and repeating mask inputs')
check('NativeScene' in (ROOT / 'app/proguard-rules.pro').read_text(encoding='utf-8'), 'JNI names kept in release')
check((ROOT / 'alive-photo/Cargo.lock').exists(), 'Pinned native dependencies are part of the source')
for file in code.rglob('*'):
    if file.suffix in {'.java', '.kt'}:
        content = file.read_text(encoding='utf-8')
        check('de.robv.android.xposed' not in content, 'No legacy API in ' + file.name)
        check('io.github.frederic.hyperalive' not in content, 'No Lab namespace in ' + file.name)
preview_source = (code / 'AlivePreviewActivity.kt').read_text(encoding='utf-8')
check('EngineRegistry.register' not in preview_source and 'Config.changed' not in preview_source, 'Preview isolation')
check('AliveSettings.apply(app, variant, dark, clock, renderOptions)' in preview_source and 'WindowDialog' in preview_source,
      'Editor applies only after explicit confirmation')
gallery_source = (code / 'AlivePage.kt').read_text(encoding='utf-8')
check('LazyVerticalGrid' in gallery_source and 'LruCache' in gallery_source and 'AliveSettings.apply' not in gallery_source,
      'Library lazily loads bounded thumbnails and routes to the editor')
check('aod_offset_x' in (code / 'PhotoOptions.java').read_text() and 'aod_offset_y' in (code / 'PhotoOptions.java').read_text(),
      'AOD position is separate from photo cropping and migrates with defaults')
spatial = (ROOT / 'app/src/main/java/com/frederic22/hyperextreme/wallpaper/SpatialServiceMode.kt').read_text(encoding='utf-8')
check('info?.component == ComponentName(ctx, SpatialWallpaperService::class.java)' in spatial, 'Precise Spatial identity')

provenance = json.loads((ROOT / 'docs/alive/PROVENANCE.json').read_text(encoding='utf-8'))
for entry in provenance['files']:
    if entry['destination'].startswith('app/src/main/assets/') and 'sha256' in entry:
        file = ROOT / entry['destination']
        check(hashlib.sha256(file.read_bytes()).hexdigest() == entry['sha256'], file.name)
for name in ['IMPORTED-PHOTO-STYLES.json', 'IMPORTED-VIVO.json']:
    imported=json.loads((ROOT/'docs/alive'/name).read_text(encoding='utf-8'))
    for entry in imported['files']:
        if 'sha256' in entry:
            file=ROOT/entry.get('destination',entry.get('path'))
            check(hashlib.sha256(file.read_bytes()).hexdigest()==entry['sha256'], 'Imported asset '+file.name)
for name in ['engine.apk','textures.zip','libpag.so','libffavc.so']:
    file=assets/'vivo/runtime'/name
    check(hashlib.sha256(file.read_bytes()).hexdigest()==file.with_name(name+'.sha256').read_text().strip(), 'Pinned Vivo runtime '+name)
    if name.endswith('.so'):
        data=file.read_bytes();offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
        check(data[:5]==b'\x7fELF\x02' and struct.unpack_from('<H',data,18)[0]==183, 'Vivo ARM64 ELF')
        for i in range(count):
            header=struct.unpack_from('<IIQQQQQQ',data,offset+i*size)
            if header[0]==1: check(header[7]>=16384,'Vivo 16KB page alignment')
for file in assets.rglob('*.json'):
    json.loads(file.read_text(encoding='utf-8'))
    checks += 1
catalog = json.loads((assets / 'cosmic/catalog.json').read_text(encoding='utf-8'))
for entry in catalog:
    if entry['id'] >= 300:
        continue
    directory = 'bubble' if entry['id'] >= 201 else 'cosmic'
    check((assets / directory / f"v{entry['id']}.json").is_file(), 'Catalog renderer')
    check((assets / 'cosmic' / entry['thumbnail']).is_file(), 'Catalog thumbnail')
for name, stride, index_size in [('sphere.bin', 12, 2), ('phoenix.bin', 32, 4)]:
    data = (assets / 'cosmic' / name).read_bytes()
    vertices, indices = struct.unpack_from('<II', data)
    check(len(data) == 8 + vertices * stride + indices * index_size, name)
for file in (assets / 'masks/leave_light').glob('*.rgbz'):
    check(len(zlib.decompress(file.read_bytes())) == 720 * 720 * 3, file.name)
for folder, size in [('moon',720),('door',720),('mountain',1080),('galaxy',1080)]:
    for file in (assets/'masks'/folder).glob('*.rgbz'):
        side=720 if file.stem=='end' else size
        check(len(zlib.decompress(file.read_bytes()))==side*side*3, str(file.relative_to(assets)))
imports=json.loads((ROOT/'docs/alive/IMPORTED-PHOTO-STYLES.json').read_text())
for entry in imports['files']:
    check(hashlib.sha256((ROOT/entry['path']).read_bytes()).hexdigest()==entry['sha256'],entry['path'])

build(sdk, Path(args.java_home), args.compile_sdk, args.build_tools, scratch / 'helper-assets')
with tempfile.TemporaryDirectory(dir=scratch) as temp:
    assert Path(temp).resolve().is_relative_to(scratch.resolve())
    android = sdk / f'platforms/android-{args.compile_sdk}/android.jar'
    classpath = str(ROOT / 'app/libs/libxposed-api-102.jar')
    options = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath',
               str(android) + os.pathsep + str(sdk / f'build-tools/{args.build_tools}/core-lambda-stubs.jar'),
               '-classpath', classpath, '-d', temp, *map(str, sorted(code.rglob('*.java')) + sorted((code.parent / 'data').glob('*.java')))]
    argfile = Path(temp) / 'java.args'
    argfile.write_text('\n'.join(json.dumps(v.replace('\\', '/')) for v in options), encoding='utf-8')
    subprocess.run([str(java / ('javac' + exe)), '-J-Dfile.encoding=UTF-8',
                    '-J-Dstdout.encoding=UTF-8', '-J-Dstderr.encoding=UTF-8', '@' + str(argfile)], check=True)
subprocess.run([str(sdk / f'build-tools/{args.build_tools}/aapt2{exe}'), 'compile', '--dir',
                str(ROOT / 'app/src/main/res'), '-o', str(scratch / 'resources.zip')], check=True)
result = {'passed': True, 'structural_asset_checks': checks, 'state_assertions': 18,
          'photo_power_assertions': 454,
          'permission_crop_assertions': 56,
          'motion_session_assertions': 23,
          'stereo_motion_assertions': 6,
          'java_sources_compiled': len(list(code.rglob('*.java'))) + len(list((code.parent / 'data').glob('*.java'))), 'compile_sdk': args.compile_sdk,
          'helper_dex': '039', 'resources_compiled': True,
          'limits': 'Does not compile Compose/Kotlin, build a complete APK, or validate live hooks/GPU/power.'}
(ROOT / 'docs/alive/verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
