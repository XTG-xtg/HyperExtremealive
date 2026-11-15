"""Opt-in ART regression in a temporary process; never installs or changes AOD settings."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--sdk', default='E:/AndroidSdk')
    parser.add_argument('--java-home', default='E:/AndroidStudio/android-studio/jbr')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    out = root / 'build/aod-support-device'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(exist_ok=True)
    production = root / 'app/src/main/java/com/frederic22/hyperextreme/alive/AodSupportFlags.java'
    probe = root / 'tests/alive/device/AodSupportFlagsProbe.java'
    manifest = {'passed': False, 'serial': args.serial,
                'source_sha256': hashlib.sha256(production.read_bytes()).hexdigest(),
                'scope': 'Production source in isolated ART; not optimized APK or live SystemUI'}
    (out / 'result.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    java, sdk = Path(args.java_home) / 'bin', Path(args.sdk)
    subprocess.run([str(java / 'javac.exe'), '-source', '8', '-target', '8', '-encoding', 'UTF-8',
                    '-d', str(classes), str(production), str(probe)], check=True)
    jar = out / 'probe.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for file in classes.rglob('*.class'):
            archive.write(file, file.relative_to(classes).as_posix())
    subprocess.run([str(java / 'java.exe'), '-cp', str(sdk / 'build-tools/37.0.0/lib/d8.jar'),
                    'com.android.tools.r8.D8', '--min-api', '33', '--output', str(dex), str(jar)], check=True)
    adb = [str(sdk / 'platform-tools/adb.exe'), '-s', args.serial]
    remote = '/data/local/tmp/hx-aod-support-probe.dex'
    subprocess.run(adb + ['push', str(dex / 'classes.dex'), remote], check=True)
    command = (f"su -c 'CLASSPATH={remote} app_process /system/bin "
               "com.frederic22.hyperextreme.alive.AodSupportFlagsProbe'")
    result = subprocess.run(adb + ['shell', command], capture_output=True, timeout=30)
    (out / 'runtime.txt').write_bytes(result.stdout + result.stderr)
    print((result.stdout + result.stderr).decode('utf8', errors='replace'))
    result.check_returncode()
    if b'AOD_SUPPORT_FLAGS_PASS' not in result.stdout:
        raise RuntimeError('ART probe did not complete')
    manifest['passed'] = True
    (out / 'result.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')


if __name__ == '__main__':
    main()
