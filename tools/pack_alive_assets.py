"""Lossless build-only asset packing; keep imported source bytes and digests unchanged."""
import gzip
import hashlib
import json
from pathlib import Path
import shutil
from dynamic_resource_pack import build as build_dynamic_pack, external_asset, MANIFEST

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets'
OUTPUT = ROOT / 'app/build/generated/packedAliveAssets'
# These assets are already extracted to digest-pinned private files before loading.
# Keep textures, mask frames and openFd videos out of the decompression path.
PACKED = (
    'vivo/runtime/libpag.so',
    'vivo/runtime/libffavc.so',
)


def build(source=SOURCE, output=OUTPUT, dynamic_output=None):
    source, output = source.resolve(), output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError('Source and generated assets must be separate')
    output.mkdir(parents=True, exist_ok=True)
    dynamic_output = Path(dynamic_output) if dynamic_output is not None else output.parent / 'dynamic-wallpapers'
    build_dynamic_pack(source, dynamic_output)
    wanted = set()
    entries = {}
    for path in sorted(source.rglob('*')):
        if path.is_symlink():
            raise ValueError('Asset symlinks are not supported: ' + str(path))
        if not path.is_file():
            continue
        name = path.relative_to(source).as_posix()
        if external_asset(name):
            continue
        # AGP transparently gunzips .gz assets while merging; use an opaque extension.
        destination = output / (name + '.agz' if name in PACKED else name)
        if not destination.resolve().is_relative_to(output):
            raise ValueError('Generated asset escapes output')
        destination.parent.mkdir(parents=True, exist_ok=True)
        if name in PACKED:
            data = path.read_bytes()
            packed = gzip.compress(data, compresslevel=9, mtime=0)
            if len(packed) >= len(data) or gzip.decompress(packed) != data:
                raise ValueError('Invalid or ineffective compression: ' + name)
            destination.write_bytes(packed)
            entries[name] = {'bytes': len(data), 'gzip_bytes': len(packed),
                             'sha256': hashlib.sha256(data).hexdigest()}
        else:
            shutil.copyfile(path, destination)
        wanted.add(destination)
    if set(entries) != set(PACKED):
        raise ValueError('Missing packed source assets')
    report = output / 'packed-alive-resources.json'
    report.write_text(json.dumps({'format': 1, 'assets': entries}, indent=2) + '\n', encoding='ascii')
    wanted.add(report)
    index = output / MANIFEST
    shutil.copyfile(dynamic_output / MANIFEST, index)
    wanted.add(index)
    # Remove stale individual generated files only, never source files or directory trees.
    for path in output.rglob('*'):
        if path.is_symlink() or not path.resolve().is_relative_to(output):
            raise ValueError('Generated output contains an unsafe path')
        if path.is_file() and path not in wanted:
            path.unlink()
    return entries


if __name__ == '__main__':
    print(json.dumps(build(dynamic_output=ROOT / 'build/dynamic-wallpapers'), indent=2))
