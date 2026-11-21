"""Deterministic data-only Alive dynamic wallpaper pack; sources remain untouched."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = 'dynamic-wallpapers.json'
PACK_NAME = 'HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip'

def external_asset(name):
    return (name.split('/')[0] in {'cosmic', 'flyme-video', 'stereo'}
            and Path(name).suffix.lower() in {'.png', '.jpg', '.jpeg', '.webp', '.mp4', '.bin'}) or name == 'shader/phoenix/Noise6.png'

def build(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    if output == source or source in output.parents or output in source.parents:
        raise ValueError('Pack output and source must be separate')
    entries = {}
    for path in sorted(source.rglob('*')):
        if path.is_symlink():
            raise ValueError('Asset symlinks are not supported')
        if path.is_file():
            name = path.relative_to(source).as_posix()
            if external_asset(name):
                data = path.read_bytes()
                entries[name] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    manifest = {'format': 1, 'id': 'alive-dynamic-v1', 'files': entries}
    raw = (json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(',', ':'))+'\n').encode('utf-8')
    output.mkdir(parents=True, exist_ok=True)
    (output/MANIFEST).write_bytes(raw)
    with zipfile.ZipFile(output/PACK_NAME, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in [('manifest.json', raw)] + [('assets/'+n, (source/n).read_bytes()) for n in entries]:
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compresslevel=9)
    return manifest

if __name__ == '__main__':
    report = build(ROOT/'app/src/main/assets', ROOT/'build/dynamic-wallpapers')
    print(json.dumps({'files': len(report['files']), 'bytes': sum(f['bytes'] for f in report['files'].values()), 'pack': PACK_NAME}))
