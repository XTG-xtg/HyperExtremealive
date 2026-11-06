"""Opt-in isolated GPU tests on an explicitly selected rooted device; never install or set wallpaper."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--compile-sdk', default='37.0')
    parser.add_argument('--build-tools', default='37.0.0')
    parser.add_argument('--state-only', action='store_true', help='Only run real-Looper photo interruption tests')
    parser.add_argument('--gpu-perf', action='store_true', help='Measure synchronous GPU frame work without vsync')
    parser.add_argument('--native-library', type=Path, help='Explicit ABI-compatible native baseline for --gpu-perf only')
    parser.add_argument('--cache-only', action='store_true', help='Compare cached and fresh texture passes')
    parser.add_argument('--perf-only', action='store_true', help='Only run native-resolution consumed-window benchmarks')
    parser.add_argument('--placement-only', action='store_true', help='Only compare initial, configured and rebound AOD position')
    parser.add_argument('--assert-placement', action='store_true')
    parser.add_argument('--variants',default='601,602',help='Comma-separated photo IDs (601..606)')
    parser.add_argument('--power-only',action='store_true',help='Only run hidden live-worker orb regressions')
    parser.add_argument('--assets-only',action='store_true',help='Only run independent photo source pixel assertions')
    parser.add_argument('--editor-fixes',action='store_true',help='Real AOD thumbnails and settled-worker brightness regression')
    parser.add_argument('--preview-switch',action='store_true',help='Switch all renderer families on one preview surface')
    parser.add_argument('--vivo',default='',help='Only test Vivo family:style pairs, e.g. 1:1,2:1,3:1')
    parser.add_argument('--vivo-mask',action='store_true',help='Only run offline ONNX subject-mask tests')
    parser.add_argument('--vivo-worker',action='store_true',help='Only run Vivo real-window worker lifecycle tests')
    parser.add_argument('--vivo-fixture',choices=['factory','custom','video'],default='factory')
    parser.add_argument('--shell-uid',action='store_true',help='Run Vivo video diagnostics as shell instead of root (isolated temp storage)')
    args = parser.parse_args()
    if args.native_library and not args.gpu_perf: parser.error('--native-library requires --gpu-perf')
    if args.shell_uid and not args.vivo: parser.error('--shell-uid requires --vivo')
    repo = Path(__file__).resolve().parents[1]
    out = repo / 'build/alive-photo-device'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(parents=True, exist_ok=True); dex.mkdir(exist_ok=True)
    sdk, java = Path(args.sdk), Path(args.java_home) / 'bin'
    exe = '.exe' if os.name == 'nt' else ''
    android = sdk / f'platforms/android-{args.compile_sdk}/android.jar'
    tools = sdk / 'build-tools' / args.build_tools
    api = repo / 'app/libs/libxposed-api-102.jar'
    files = sorted((repo / 'app/src/main/java/com/frederic22/hyperextreme/alive').rglob('*.java'))
    files += sorted((repo / 'app/src/main/java/com/frederic22/hyperextreme/data').glob('*.java'))
    files += [repo / 'tests/alive/device/PhotoPipelineProbe.java', repo / 'tests/alive/device/LiveFrameProbe.java', repo / 'tests/alive/device/PhotoInterruptionProbe.java', repo / 'tests/alive/device/PhotoPerfProbe.java']
    files += [repo/'tests/alive/device/ProbeContext.java',repo/'tests/alive/device/PresentationProbe.java',repo/'tests/alive/device/LivePowerProbe.java']
    files += [repo/'tests/alive/device/PhotoAssetsProbe.java']
    files += [repo/'tests/alive/device/VivoPipelineProbe.java']
    files += [repo/'tests/alive/device/VivoMaskProbe.java']
    files += [repo/'tests/alive/device/VivoWorkerProbe.java']
    files += [repo/'tests/alive/device/PhotoThumbnailProbe.java',repo/'tests/alive/device/BrightnessWorkerProbe.java']
    files += [repo/'tests/alive/device/PreviewSwitchProbe.java']
    files += [repo/'tests/alive/device/PlacementWorkerProbe.java',repo/'tests/alive/device/PhotoCacheProbe.java',repo/'tests/alive/device/PhotoGpuPerfProbe.java']
    options = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath',
               str(android) + os.pathsep + str(tools / 'core-lambda-stubs.jar'),
               '-cp', str(api), '-d', str(classes), *map(str, files)]
    argfile = out / 'javac.args'
    argfile.write_text('\n'.join(json.dumps(v.replace('\\', '/')) for v in options), encoding='utf8')
    subprocess.run([str(java / ('javac' + exe)), '@' + str(argfile)], check=True)
    jar = out / 'probe.jar'
    with zipfile.ZipFile(jar, 'w') as z:
        for f in classes.rglob('*.class'):
            z.write(f, f.relative_to(classes).as_posix())
    subprocess.run([str(java / ('java' + exe)), '-cp', str(tools / 'lib/d8.jar'),
                    'com.android.tools.r8.D8', '--min-api', '33', '--lib', str(android),
                    '--lib', str(api), '--output', str(dex), str(jar)], check=True)
    adb = [str(sdk / 'platform-tools' / ('adb' + exe)), '-s', args.serial]
    remote = '/data/local/tmp/hyperextreme-photo-test'
    subprocess.run(adb + ['shell', 'mkdir -p ' + remote], check=True)
    for f in [dex / 'classes.dex', args.native_library or repo / 'app/build/generated/aliveJni/arm64-v8a/libhyperextreme_photo.so']:
        subprocess.run(adb + ['push', str(f), remote + '/' + f.name], check=True)
    subprocess.run(adb+['push',str(repo/'app/build/outputs/apk/debug/app-debug.apk'),remote+'/candidate.apk'],check=True)
    if args.vivo_mask:
        with zipfile.ZipFile(repo/'app/build/outputs/apk/debug/app-debug.apk') as apk:
            for name in ['libonnxruntime.so','libonnxruntime4j_jni.so']:
                library=out/name;library.write_bytes(apk.read('lib/arm64-v8a/'+name))
                subprocess.run(adb+['push',str(library),remote+'/'+name],check=True)
    variants=[int(v) for v in args.variants.split(',')]
    if not variants or (not args.placement_only and any(v not in range(601,607) for v in variants)): raise ValueError('Invalid photo IDs')
    cases = [(f'photo-{v}-{land}', 'PhotoPipelineProbe', f'{v} {land}')
             for v in variants for land in ['false', 'true']]
    cases += [('presentation','PresentationProbe','')]
    cases += [('photo-assets','PhotoAssetsProbe','')]
    cases += [(f'power-{v}','LivePowerProbe',str(v)) for v in [1,101,201]]
    cases += [(case, 'LiveFrameProbe', case) for case in ['photo601', 'photo602', 'dual']]
    if args.state_only:
        cases = []
    cases += [('interrupted-sleep', 'PhotoInterruptionProbe', '')]
    if args.perf_only:
        cases = [(f'perf-{v}', 'PhotoPerfProbe', str(v)) for v in [601, 602]]
    if args.power_only:
        cases=[(f'power-{v}','LivePowerProbe',str(v)) for v in [1,101,201]]
    if args.assets_only:
        cases=[('photo-assets','PhotoAssetsProbe','')]
    if args.vivo:
        pairs=[tuple(map(int,p.split(':'))) for p in args.vivo.split(',')]
        if any(len(p)!=2 or p[0] not in range(1,4) or p[1] not in range(1,6 if p[0]==3 else 5) for p in pairs): raise ValueError('Invalid Vivo family/style')
        cases=[(f'vivo-{family}-{style}-{args.vivo_fixture}','VivoPipelineProbe',f'{family} {style} {args.vivo_fixture}') for family,style in pairs]
        if args.vivo_fixture=='video':
            if any(family!=2 for family,style in pairs): raise ValueError('Video fixtures require raster scenes')
            clip=out/'vivo-test-clip.mp4'
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=320x640:rate=30','-t','2','-c:v','libx264','-pix_fmt','yuv420p',str(clip)],check=True)
            subprocess.run(adb+['shell','mkdir','-p','/data/local/tmp/hyperextreme-vivo-test/files'],check=True)
            subprocess.run(adb+['push',str(clip),remote+'/vivo-test-clip.mp4'],check=True)
            subprocess.run(adb+['shell',"su -M -c 'cp /data/local/tmp/hyperextreme-photo-test/vivo-test-clip.mp4 /data/local/tmp/hyperextreme-vivo-test/files/vivo-test-clip.mp4'"],check=True)
            if args.shell_uid:
                subprocess.run(adb+['shell','mkdir -p /data/local/tmp/hyperextreme-vivo-shell-test/files; cp /data/local/tmp/hyperextreme-photo-test/vivo-test-clip.mp4 /data/local/tmp/hyperextreme-vivo-shell-test/files/vivo-test-clip.mp4'],check=True)
    if args.vivo_mask:
        cases=[('vivo-mask','VivoMaskProbe','')]
    if args.vivo_worker:
        cases=[(f'vivo-worker-{v}','VivoWorkerProbe',str(v)) for v in [701,702,703]]
    if args.editor_fixes:
        cases=[('presentation','PresentationProbe',''),('photo-thumbnails','PhotoThumbnailProbe','')]
        cases += [(f'brightness-{v}','BrightnessWorkerProbe',str(v)) for v in [1,601,602,603,604,605,606]]
    if args.preview_switch:
        cases=[('preview-switch','PreviewSwitchProbe','')]
    if args.placement_only:
        cases=[(f'placement-{v}','PlacementWorkerProbe',str(v)) for v in variants]
    if args.gpu_perf:
        cases=[('photo-gpu-perf','PhotoGpuPerfProbe','')]
    if args.cache_only:
        cases=[('photo-cache','PhotoCacheProbe','')]
    for name, test, parameters in cases:
        command = (f"su -M -c 'CLASSPATH={remote}/classes.dex:{remote}/candidate.apk app_process -Dalive.test.placement.assert={str(args.assert_placement).lower()} -Dalive.test.apk={remote}/candidate.apk -Djava.library.path={remote} "
                   f"/system/bin com.frederic22.hyperextreme.alive.{test} {parameters}'")
        if args.shell_uid:
            command=(f"CLASSPATH={remote}/classes.dex:{remote}/candidate.apk app_process -Dalive.test.out=/data/local/tmp/hyperextreme-vivo-shell-test -Dalive.test.apk={remote}/candidate.apk -Djava.library.path={remote} /system/bin com.frederic22.hyperextreme.alive.{test} {parameters}")
        result = subprocess.run(adb + ['shell', command], capture_output=True, timeout=180)
        (out / (name + '.log')).write_bytes(result.stdout + result.stderr)
        print(name, result.returncode, (result.stdout + result.stderr).decode('utf8', errors='replace'), flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)
    if args.vivo or args.vivo_mask:
        pictures=out/'vivo';pictures.mkdir(exist_ok=True)
        listing=subprocess.run(adb+['shell','find','/data/local/tmp/hyperextreme-vivo-test','-maxdepth','1','-name','*.png'],capture_output=True,text=True,check=True)
        for path in listing.stdout.splitlines():
            if path.startswith('/data/local/tmp/hyperextreme-vivo-test/') and '/' not in path.rsplit('/',1)[-1]:
                name=Path(path).name
                if args.vivo_mask and not name.startswith('subject-'): continue
                if args.vivo and not any(name.startswith(f'{family}-{style}-{args.vivo_fixture}-') for family,style in pairs): continue
                subprocess.run(adb+['pull',path,str(pictures/Path(path).name)],check=True)
    elif not (args.power_only or args.state_only or args.perf_only or args.assets_only or args.vivo_worker or args.preview_switch or args.placement_only or args.cache_only or args.gpu_perf):
        pictures=out/'frames';pictures.mkdir(exist_ok=True)
        listing=subprocess.run(adb+['shell','find',remote,'-maxdepth','1','-name','*.png'],capture_output=True,text=True,check=True)
        for path in listing.stdout.splitlines():
            if path.startswith(remote+'/') and '/' not in path[len(remote)+1:]:
                if args.editor_fixes and not Path(path).name.startswith('tile-'): continue
                subprocess.run(adb+['pull',path,str(pictures/Path(path).name)],check=True)


if __name__ == '__main__':
    main()
