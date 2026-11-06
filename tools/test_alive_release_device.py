"""Opt-in R8 runtime smoke test. Never installs, starts the app, writes its data or binds wallpapers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--sdk', default='E:/AndroidSdk')
    parser.add_argument('--java-home', default='E:/AndroidStudio/android-studio/jbr')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    apk = root / 'app/build/outputs/apk/release/app-release.apk'
    mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
    required = {'PhotoRenderer': [], 'PhotoOptions': ['defaults'], 'VivoRenderer': [],
                'VivoOptions': ['load'], 'WallpaperRenderer': ['render', 'frameReady', 'create']}
    names, current = {}, None
    for line in mapping.read_text(encoding='utf-8').splitlines():
        cls = re.fullmatch(r'(\S+) -> ([^:]+):', line)
        if cls:
            current = cls[1].removeprefix('com.frederic22.hyperextreme.alive.')
            if current in required:
                names[current] = cls[2]
        elif current in required:
            method = re.fullmatch(r'\s+(?:\d+:\d+:)?\S+ (\w+)\([^)]*\)(?::\d+(?::\d+)?)? -> (\w+)', line)
            if method and method[1] in required[current]:
                key = current + '.' + method[1]
                if key in names and names[key] != method[2]:
                    raise ValueError('Ambiguous R8 method: ' + key)
                names[key] = method[2]
    for cls, methods in required.items():
        for key in [cls] + [cls + '.' + m for m in methods]:
            if key not in names:
                raise ValueError('R8 entry no longer available; update probe without changing release keeps: ' + key)
    out = root / 'build/alive-release-device'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'names.json').write_text(json.dumps(names, indent=2), encoding='utf-8')
    sdk, java = Path(args.sdk), Path(args.java_home) / 'bin'
    ext = '.exe' if os.name == 'nt' else ''
    tools = sdk / 'build-tools/37.0.0'
    android = sdk / 'platforms/android-37.0/android.jar'
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    source = root / 'tests/alive/device/ReleaseRuntimeProbe.java'
    # Deliberately compile only this reflection driver, NEVER the production Java sources.
    subprocess.run([str(java / ('javac' + ext)), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                    '-cp', str(android), '-d', str(classes), str(source)], check=True)
    jar = out / 'probe.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for file in classes.rglob('*.class'):
            archive.write(file, file.relative_to(classes).as_posix())
    dex = out / 'dex'
    dex.mkdir(exist_ok=True)
    subprocess.run([str(java / ('java' + ext)), '-cp', str(tools / 'lib/d8.jar'), 'com.android.tools.r8.D8',
                    '--min-api', '33', '--lib', str(android), '--output', str(dex), str(jar)], check=True)
    adb = [str(sdk / 'platform-tools' / ('adb' + ext)), '-s', args.serial]
    remote = '/data/local/tmp/hyperextreme-release-test'
    subprocess.run(adb + ['shell', 'mkdir', '-p', remote], check=True)
    files = [(dex / 'classes.dex', 'probe.dex'), (apk, 'candidate.apk'), (out / 'names.json', 'names.json')]
    with zipfile.ZipFile(apk) as archive:
        for path in archive.namelist():
            if path.startswith('lib/arm64-v8a/') and path.endswith('.so'):
                file = out / Path(path).name
                file.write_bytes(archive.read(path))
                files.append((file, file.name))
    for file, name in files:
        subprocess.run(adb + ['push', str(file), remote + '/' + name], check=True)
    command = (f"su -M -c 'CLASSPATH={remote}/probe.dex:{remote}/candidate.apk "
               f"app_process -Djava.library.path={remote} /system/bin releaseprobe.ReleaseRuntimeProbe'")
    result = subprocess.run(adb + ['shell', command], capture_output=True, timeout=240)
    (out / 'runtime.log').write_bytes(result.stdout + result.stderr)
    print((result.stdout + result.stderr).decode('utf-8', errors='replace'))
    result.check_returncode()
    if b'RELEASE_RUNTIME_PASS' not in result.stdout:
        raise RuntimeError('Probe did not report completion')
    (out / 'result.json').write_text(json.dumps({'passed': True, 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'serial': args.serial, 'optimized_apk_only': True, 'frame_cases': 42,
        'limitations': 'Offscreen representative renderer smoke; not UI startup, live hooks, power transitions or video.'}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
