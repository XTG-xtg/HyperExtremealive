"""Opt-in offscreen layout and synthetic image tests. Does not install APKs or alter wallpapers/settings."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--sdk', default='E:/AndroidSdk')
    parser.add_argument('--java-home', default='E:/AndroidStudio/android-studio/jbr')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    out = repo / 'build/alive-layout-review'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(exist_ok=True)
    sdk, java = Path(args.sdk), Path(args.java_home) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    android = sdk / 'platforms/android-37.0/android.jar'
    tools = sdk / 'build-tools/37.0.0'
    api = repo / 'app/libs/libxposed-api-102.jar'
    main_code = repo / 'app/src/main/java/com/frederic22/hyperextreme'
    sources = sorted((main_code / 'alive').rglob('*.java')) + sorted((main_code / 'data').glob('*.java'))
    sources += [repo / 'tests/alive/device/LayoutPreviewProbe.java', repo / 'tests/alive/device/ImageImportProbe.java']
    options = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath',
               str(android) + os.pathsep + str(tools / 'core-lambda-stubs.jar'),
               '-cp', str(api), '-d', str(classes), *map(str, sources)]
    argument_file = out / 'javac.args'
    argument_file.write_text('\n'.join(json.dumps(value.replace('\\', '/')) for value in options), encoding='utf8')
    subprocess.run([str(java / ('javac' + suffix)), '@' + str(argument_file)], check=True)
    jar = out / 'probe.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for file in classes.rglob('*.class'):
            archive.write(file, file.relative_to(classes).as_posix())
    subprocess.run([str(java / ('java' + suffix)), '-cp', str(tools / 'lib/d8.jar'), 'com.android.tools.r8.D8',
                    '--min-api', '33', '--lib', str(android), '--lib', str(api), '--output', str(dex), str(jar)], check=True)
    adb = [str(sdk / 'platform-tools' / ('adb' + suffix)), '-s', args.serial]
    remote = '/data/local/tmp/hyperextreme-layout-review'
    subprocess.run(adb + ['shell', 'mkdir -p ' + remote], check=True)
    for file in [dex / 'classes.dex', repo / 'app/build/generated/aliveJni/arm64-v8a/libhyperextreme_photo.so']:
        subprocess.run(adb + ['push', str(file), remote + '/' + file.name], check=True)
    for name, main_class, parameters in [('image-import', 'ImageImportProbe', '')] + [
        (f'layout-{variant}', 'LayoutPreviewProbe', str(variant)) for variant in (1, 301, 601, 602)
    ]:
        command = (f"su -M -c 'CLASSPATH={remote}/classes.dex app_process -Djava.library.path={remote} "
                   f"/system/bin com.frederic22.hyperextreme.alive.{main_class} {parameters}'")
        result = subprocess.run(adb + ['shell', command], capture_output=True, timeout=90)
        (out / (name + '.log')).write_bytes(result.stdout + result.stderr)
        print(name, result.returncode, (result.stdout + result.stderr).decode('utf8', errors='replace'), flush=True)
        result.check_returncode()
    # Only pull generated test pictures, not any application or user files.
    for variant in (1, 301, 601, 602):
        for mode in range(3):
            name = f'layout-{variant}-{mode}.png'
            subprocess.run(adb + ['pull', f'{remote}/{name}', str(out / name)], check=True)


if __name__ == '__main__':
    main()
