"""Compile only the small MIUI compatibility bridge; never merge it into the application loader."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--sdk', required=True)
    p.add_argument('--java-home', required=True)
    a = p.parse_args()
    sdk, java = Path(a.sdk), Path(a.java_home) / 'bin'
    ext = '.exe' if (java / 'javac.exe').is_file() else ''
    out = ROOT / 'build/clock-bridge'
    out.mkdir(parents=True, exist_ok=True)
    scratch = tempfile.TemporaryDirectory(prefix='compile-', dir=out)
    classes, dex = Path(scratch.name) / 'classes', Path(scratch.name) / 'dex'
    classes.mkdir()
    dex.mkdir(exist_ok=True)
    android = next((f for f in [sdk/'platforms/android-37/android.jar', sdk/'platforms/android-37.0/android.jar'] if f.is_file()))
    args = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', str(android), '-d', str(classes)]
    args += [str(f) for f in sorted((ROOT/'alive-clock/bridge').rglob('*.java'))]
    argfile = out/'javac.args'
    argfile.write_text('\n'.join(json.dumps(v.replace('\\','/')) for v in args), encoding='utf-8')
    subprocess.run([str(java/('javac'+ext)), '@'+str(argfile)], check=True)
    jar = out/'bridge.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in sorted(classes.rglob('*.class')):
            z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix(), (2026,1,1,0,0,0)), f.read_bytes())
    subprocess.run([str(java/('java'+ext)), '-cp', str(sdk/'build-tools/37.0.0/lib/d8.jar'), 'com.android.tools.r8.D8',
                    '--min-api', '33', '--lib', str(android), '--output', str(dex), str(jar)], check=True)
    assets=ROOT/'app/build/generated/aliveClockAssets/native-clock'
    assets.mkdir(parents=True,exist_ok=True)
    data=(dex/'classes.dex').read_bytes()
    (assets/'bridge.dex').write_bytes(data)
    (assets/'bridge.sha256').write_text(hashlib.sha256(data).hexdigest(),encoding='ascii')
    assert Path(scratch.name).resolve().is_relative_to(out.resolve())
    scratch.cleanup()
