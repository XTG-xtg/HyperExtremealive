"""Opt-in pixel verification on an explicit device; no install, app data or wallpaper changes."""
from pathlib import Path
import argparse
import json
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--sdk", default="E:/AndroidSdk")
parser.add_argument("--java-home", default="E:/AndroidStudio/android-studio/jbr")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / "build/unified-brightness-device"
out.mkdir(exist_ok=True)
classes = out / "classes"
classes.mkdir(exist_ok=True)
sdk, java = Path(args.sdk), Path(args.java_home) / "bin"
android = sdk / "platforms/android-37.0/android.jar"
compiled = root / "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
source = root / "tests/alive/device/UnifiedBrightnessProbe.java"
subprocess.run([str(java / "javac.exe"), "-encoding", "UTF-8", "-source", "17", "-target", "17",
                "-cp", f"{android};{compiled}", "-d", str(classes), str(source)], check=True)
with zipfile.ZipFile(out / "probe.jar", "w") as archive:
    for path in classes.rglob("*.class"):
        archive.write(path, path.relative_to(classes).as_posix())
dex = out / "dex"
dex.mkdir(exist_ok=True)
subprocess.run([str(java / "java.exe"), "-cp", str(sdk / "build-tools/37.0.0/lib/d8.jar"),
                "com.android.tools.r8.D8", "--min-api", "33", "--lib", str(android),
                "--output", str(dex), str(out / "probe.jar")], check=True)
adb = [str(sdk / "platform-tools/adb.exe"), "-s", args.serial]
remote = "/data/local/tmp/hyperextreme-brightness-test"
subprocess.run(adb + ["shell", "mkdir", "-p", remote], check=True)
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
for path, name in [(dex / "classes.dex", "probe.dex"), (apk, "candidate.apk")]:
    subprocess.run(adb + ["push", str(path), remote + "/" + name], check=True)
run = subprocess.run(adb + ["shell", f"CLASSPATH={remote}/probe.dex:{remote}/candidate.apk "
    f"app_process /system/bin com.frederic22.hyperextreme.alive.UnifiedBrightnessProbe {remote}/candidate.apk"],
    capture_output=True, timeout=90)
(out / "runtime.log").write_bytes(run.stdout + run.stderr)
print((run.stdout + run.stderr).decode(errors="replace"))
run.check_returncode()
assert b"UNIFIED_BRIGHTNESS_PASS cases=250" in run.stdout
(out / "result.json").write_text(json.dumps({
    "passed": True, "pixel_cases": 250, "agsl_compiled": ["Spatial", "Raster"],
    "scope": "Candidate debug APK kernels; not installed wallpaper or SystemUI lifecycle",
}, indent=2), encoding="utf8")
