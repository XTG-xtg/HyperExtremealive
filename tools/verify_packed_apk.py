"""Verify a release APK has no duplicate assets and losslessly restores the previous resources."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import zipfile
from pack_alive_assets import PACKED, ROOT


def verify(apk, baseline):
    with zipfile.ZipFile(apk) as current, zipfile.ZipFile(baseline) as previous:
        assert current.testzip() is None
        report = json.loads(current.read('assets/packed-alive-resources.json'))
        assert set(report['assets']) == set(PACKED)
        assert len(current.namelist()) == len(set(current.namelist())), 'Duplicate ZIP entries'
        checked = 0
        for item in previous.infolist():
            if not item.filename.startswith(('assets/', 'lib/')):
                continue
            # Generated ART profiles necessarily change when app bytecode changes.
            if item.filename in {'assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'}:
                assert item.filename in current.namelist()
                continue
            name = item.filename.removeprefix('assets/')
            if name in PACKED:
                assert item.filename not in current.namelist(), 'Original still packaged: ' + name
                packed = current.read(item.filename + '.agz')
                assert current.getinfo(item.filename + '.agz').compress_type == zipfile.ZIP_STORED
                restored = gzip.decompress(packed)
                assert len(restored) == report['assets'][name]['bytes']
                assert hashlib.sha256(restored).hexdigest() == report['assets'][name]['sha256']
            else:
                restored = current.read(item.filename)
            assert restored == previous.read(item.filename), 'Resource changed: ' + item.filename
            if item.filename.endswith('/libhome_bridge.so'):
                entry = current.getinfo(item.filename)
                assert entry.compress_type == zipfile.ZIP_STORED, 'LSPosed bridge must be mmap-ready'
                with apk.open('rb') as raw:
                    raw.seek(entry.header_offset + 26)
                    import struct
                    name_len, extra_len = struct.unpack('<HH', raw.read(4))
                assert (entry.header_offset + 30 + name_len + extra_len) % 16384 == 0, 'Bridge must be 16KB aligned'
            elif item.filename.startswith('lib/'):
                assert current.getinfo(item.filename).compress_type == zipfile.ZIP_DEFLATED
            if item.filename.endswith('.mp4'):
                assert current.getinfo(item.filename).compress_type == zipfile.ZIP_STORED
            checked += 1
        assert current.getinfo('classes.dex').compress_type == zipfile.ZIP_DEFLATED
    old, new = baseline.stat().st_size, apk.stat().st_size
    return {'apk': str(apk), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'baseline_bytes': old, 'apk_bytes': new, 'saved_bytes': old - new,
            'saved_percent': round((old - new) / old * 100, 2),
            'unchanged_resource_entries': checked, 'packed_resources': report['assets']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/release/app-release.apk')
    parser.add_argument('--baseline', type=Path, default=ROOT / 'deliverables/HyperExtreme-Alive-0.5-alive.7-ui.16.apk')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/packed-apk-verification.json')
    args = parser.parse_args()
    result = verify(args.apk, args.baseline)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
