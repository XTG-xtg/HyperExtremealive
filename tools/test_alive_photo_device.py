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
    parser.add_argument('--perf-only', action='store_true', help='Only run native-resolution consumed-window benchmarks')
    args = parser.parse_args()
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
    for f in [dex / 'classes.dex', repo / 'app/build/generated/aliveJni/arm64-v8a/libhyperextreme_photo.so']:
        subprocess.run(adb + ['push', str(f), remote + '/' + f.name], check=True)
    cases = [(f'photo-{v}-{land}', 'PhotoPipelineProbe', f'{v} {land}')
             for v in [601, 602] for land in ['false', 'true']]
    cases += [(case, 'LiveFrameProbe', case) for case in ['photo601', 'photo602', 'dual']]
    if args.state_only:
        cases = []
    cases += [('interrupted-sleep', 'PhotoInterruptionProbe', '')]
    if args.perf_only:
        cases = [(f'perf-{v}', 'PhotoPerfProbe', str(v)) for v in [601, 602]]
    for name, test, parameters in cases:
        command = (f"su -M -c 'CLASSPATH={remote}/classes.dex app_process -Djava.library.path={remote} "
                   f"/system/bin com.frederic22.hyperextreme.alive.{test} {parameters}'")
        result = subprocess.run(adb + ['shell', command], capture_output=True, timeout=180)
        (out / (name + '.log')).write_bytes(result.stdout + result.stderr)
        print(name, result.returncode, (result.stdout + result.stderr).decode('utf8', errors='replace'), flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)
    subprocess.run(adb + ['pull', remote, str(out / 'frames')], check=True)


if __name__ == '__main__':
    main()
