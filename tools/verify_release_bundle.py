"""Verify that an optimized APK and its dynamic wallpaper ZIP match the source assets."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

from dynamic_resource_pack import external_asset, MANIFEST, PACK_NAME
from pack_alive_assets import PACKED, ROOT


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(apk, pack, aapt):
    metadata = json.loads((apk.parent / 'output-metadata.json').read_text(encoding='utf-8'))
    assert metadata['variantName'] == 'release', 'Expected the release build variant'
    assert metadata['applicationId'] == 'com.xtg.hyperextremealive'
    assert len(metadata['elements']) == 1
    element = metadata['elements'][0]
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True, encoding='utf-8')
    assert 'application-debuggable' not in badging, 'APK is debuggable'
    assert "name='com.xtg.hyperextremealive'" in badging
    assert f"versionCode='{element['versionCode']}'" in badging
    assert f"versionName='{element['versionName']}'" in badging
    assert re.search(r"native-code:\s*'arm64-v8a'\s*$", badging, re.MULTILINE)
    mapping = ROOT / 'app/build/outputs/mapping/release/mapping.txt'
    assert mapping.is_file() and mapping.stat().st_size > 0, 'Missing R8 mapping'
    checked = 0
    with zipfile.ZipFile(apk) as app, zipfile.ZipFile(pack) as resources:
        assert app.testzip() is None and resources.testzip() is None
        assert len(app.namelist()) == len(set(app.namelist()))
        assert len(resources.namelist()) == len(set(resources.namelist()))
        manifest_bytes = app.read('assets/' + MANIFEST)
        assert manifest_bytes == resources.read('manifest.json'), 'APK and resource pack versions differ'
        manifest = json.loads(manifest_bytes)
        assert manifest['format'] == 1 and manifest['id'] == 'alive-dynamic-v1'
        assert set(resources.namelist()) == {'manifest.json'} | {'assets/' + name for name in manifest['files']}
        for name, entry in manifest['files'].items():
            assert external_asset(name) and '..' not in Path(name).parts
            data = resources.read('assets/' + name)
            assert len(data) == entry['bytes'] and digest(data) == entry['sha256'], name
            assert digest(data) == digest((ROOT / 'app/src/main/assets' / name).read_bytes()), name
            assert 'assets/' + name not in app.namelist(), 'External asset also bundled: ' + name
        for source in sorted((ROOT / 'app/src/main/assets').rglob('*')):
            if not source.is_file():
                continue
            name = source.relative_to(ROOT / 'app/src/main/assets').as_posix()
            if external_asset(name):
                assert name in manifest['files'], 'Missing external asset: ' + name
                continue
            packed = name in PACKED
            key = 'assets/' + name + ('.agz' if packed else '')
            data = app.read(key)
            if packed:
                assert 'assets/' + name not in app.namelist()
                assert app.getinfo(key).compress_type == zipfile.ZIP_STORED
                data = gzip.decompress(data)
            assert digest(data) == digest(source.read_bytes()), 'Packaged source asset mismatch: ' + name
            if name.endswith('.mp4'):
                assert app.getinfo(key).compress_type == zipfile.ZIP_STORED
            checked += 1
        bridge = app.getinfo('lib/arm64-v8a/libhome_bridge.so')
        assert bridge.compress_type == zipfile.ZIP_STORED
        with apk.open('rb') as raw:
            raw.seek(bridge.header_offset + 26)
            name_length, extra_length = struct.unpack('<HH', raw.read(4))
        assert (bridge.header_offset + 30 + name_length + extra_length) % 16384 == 0
        assert app.getinfo('classes.dex').compress_type == zipfile.ZIP_DEFLATED
    return {'versionCode': element['versionCode'], 'versionName': element['versionName'],
            'apk': {'file': str(apk), 'bytes': apk.stat().st_size, 'sha256': digest(apk.read_bytes())},
            'resource_pack': {'file': str(pack), 'bytes': pack.stat().st_size, 'sha256': digest(pack.read_bytes()),
                              'files': len(manifest['files']), 'manifest_sha256': digest(manifest_bytes)},
            'bundled_source_assets_verified': checked, 'debuggable': False, 'r8_mapping': str(mapping),
            'note': 'Offline package verification; no device UI or sleep/wake testing.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/release/app-release.apk')
    parser.add_argument('--pack', type=Path, default=ROOT / 'build/dynamic-wallpapers' / PACK_NAME)
    parser.add_argument('--aapt', type=Path, required=True, help='Path to Android SDK aapt2 executable')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/release-bundle-verification.json')
    args = parser.parse_args()
    report = verify(args.apk, args.pack, args.aapt)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
