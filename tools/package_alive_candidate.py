"""Package explicitly selected source roots; never ship references, credentials or caches."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'deliverables'
NAME = 'HyperExtreme-Alive-0.5-alive.7-ui.4-candidate'
ROOT_FILES = ['.gitignore', 'ALIVE-POWER-FIX.md', 'ALIVE-UI-NOTES.md', 'README.md',
              'LICENSE', 'MIT_License.md', 'build.gradle.kts', 'settings.gradle.kts',
              'gradle.properties', 'gradlew', 'gradlew.bat', 'keystore.properties.example']
SOURCE_DIRS = ['app/src', 'app/libs', 'gradle', 'docs', 'tools', 'tests', 'alive-root/src', 'alive-photo/native']
EXTRA_FILES = ['app/build.gradle.kts', 'app/proguard-rules.pro', 'alive-photo/Cargo.toml',
               'alive-photo/Cargo.lock', 'alive-photo/rust-toolchain.toml', 'alive-photo/LICENSE']
FORBIDDEN = {'.reference', 'build', '.gradle', '.kotlin', '__pycache__', '.cxx', 'keystore', 'deliverables'}


def main():
    OUT.mkdir(exist_ok=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.is_file():
        raise SystemExit('Build and verify the candidate APK before packaging.')
    files = {ROOT / p for p in ROOT_FILES + EXTRA_FILES}
    for directory in SOURCE_DIRS:
        files.update(p for p in (ROOT / directory).rglob('*') if p.is_file()
                     and not FORBIDDEN.intersection(p.relative_to(ROOT).parts)
                     and p.suffix not in {'.pyc', '.jks', '.keystore', '.db'})
    entries = []
    for path in sorted(files):
        relative = path.relative_to(ROOT).as_posix()
        if not path.resolve().is_relative_to(ROOT.resolve()) or path.is_symlink():
            raise ValueError('Source escapes workspace: ' + relative)
        if path.name in {'keystore.properties', 'local.properties'}:
            raise ValueError('Private configuration: ' + relative)
        entries.append((path, relative, hashlib.sha256(path.read_bytes()).hexdigest()))
    with zipfile.ZipFile(OUT / (NAME + '-source.zip'), 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path, relative, digest in entries:
            archive.write(path, NAME + '-source/' + relative)
        archive.writestr(NAME + '-source/SOURCE-MANIFEST.json', json.dumps(
            {'files': {relative: digest for _, relative, digest in entries}}, indent=2))
    shutil.copyfile(apk, OUT / (NAME + '.apk'))
    output = [OUT / (NAME + '.apk'), OUT / (NAME + '-source.zip')]
    checksums = '\n'.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name for p in output) + '\n'
    (OUT / 'SHA256SUMS.txt').write_text(checksums, encoding='ascii')
    with zipfile.ZipFile(output[1]) as archive:
        if archive.testzip() is not None:
            raise ValueError('Source ZIP integrity check failed')
        for _, relative, digest in entries:
            if hashlib.sha256(archive.read(NAME + '-source/' + relative)).hexdigest() != digest:
                raise ValueError('Source checksum mismatch: ' + relative)
    print(json.dumps({'source_files': len(entries), 'files': [str(p) for p in output]}, indent=2))
    print(checksums)


if __name__ == '__main__':
    main()
