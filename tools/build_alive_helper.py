"""Build the allowlisted app_process helper from source; never consume a prebuilt core JAR."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def build(sdk, java_home, platform, build_tools, output):
    sdk, java_home, output = Path(sdk), Path(java_home), Path(output)
    android = sdk / f"platforms/android-{platform}/android.jar"
    if not android.is_file():
        android = sdk / f"platforms/android-{platform}.0/android.jar"
    tools = sdk / "build-tools" / build_tools
    exe = ".exe" if os.name == "nt" else ""
    for required in [android, tools / "lib/d8.jar", java_home / f"bin/javac{exe}"]:
        if not required.is_file():
            raise RuntimeError(f"Missing build prerequisite: {required}")
    scratch = ROOT / "build/alive-helper"
    scratch.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=scratch) as directory:
        temp = Path(directory)
        assert temp.resolve().is_relative_to(scratch.resolve())
        classes, dex = temp / "classes", temp / "dex"
        classes.mkdir(); dex.mkdir()
        sources = sorted((ROOT / "alive-root/src").rglob("*.java"))
        sources.append(ROOT / "app/src/main/java/com/frederic22/hyperextreme/alive/core/ModuleIdentity.java")
        options = ["-encoding", "UTF-8", "-source", "8", "-target", "8", "-bootclasspath",
                   str(android), "-d", str(classes), *map(str, sources)]
        args = temp / "javac.args"
        args.write_text("\n".join(json.dumps(v.replace('\\', '/')) for v in options), encoding="utf-8")
        subprocess.run([str(java_home / f"bin/javac{exe}"), "-J-Dfile.encoding=UTF-8",
                        "-J-Dstdout.encoding=UTF-8", "-J-Dstderr.encoding=UTF-8", "@" + str(args)], check=True)
        jar = temp / "helper.jar"
        with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as out:
            for file in classes.rglob("*.class"):
                out.write(file, file.relative_to(classes).as_posix())
        subprocess.run([str(java_home / f"bin/java{exe}"), "-cp", str(tools / "lib/d8.jar"),
                        "com.android.tools.r8.D8", "--min-api", "28", "--lib", str(android),
                        "--output", str(dex), str(jar)], check=True)
        data = (dex / "classes.dex").read_bytes()
        if data[:8] != b"dex\n039\x00":
            raise RuntimeError("Root helper must use DEX 039")
        (output / "alive_root.dex").write_bytes(data)
    print("Alive root helper built from source:", output / "alive_root.dex")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"), required=not os.environ.get("JAVA_HOME"))
    parser.add_argument("--compile-sdk", default="37")
    parser.add_argument("--build-tools", default="37.0.0")
    parser.add_argument("--output", default=str(ROOT / "app/build/generated/aliveAssets"))
    args = parser.parse_args()
    build(args.sdk, args.java_home, args.compile_sdk, args.build_tools, args.output)
