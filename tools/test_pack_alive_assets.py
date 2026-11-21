"""Small, offline regression tests for deterministic packing and generated-only cleanup."""
import gzip
from pathlib import Path
import tempfile
import unittest
import json
import zipfile
from dynamic_resource_pack import MANIFEST, PACK_NAME
from pack_alive_assets import PACKED, ROOT, build


class PackingTest(unittest.TestCase):
    def setUp(self):
        workspace = (ROOT / 'build/packing-tests').resolve()
        workspace.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=workspace)
        self.folder = Path(self.temp.name).resolve()
        assert self.folder.is_relative_to(workspace)
        self.addCleanup(self.temp.cleanup)
        self.source, self.output = self.folder / 'source', self.folder / 'output'
        self.source.mkdir()
        for name in PACKED:
            file = self.source / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes((name.encode() + b'\0') * 300)
        (self.source / 'sample.mp4').write_bytes(b'video-passthrough')

    def test_lossless_reproducible_and_sources_untouched(self):
        originals = {p.relative_to(self.source): p.read_bytes() for p in self.source.rglob('*') if p.is_file()}
        build(self.source, self.output)
        generated = {p.relative_to(self.output): p.read_bytes() for p in self.output.rglob('*') if p.is_file()}
        build(self.source, self.output)
        self.assertEqual(generated, {p.relative_to(self.output): p.read_bytes() for p in self.output.rglob('*') if p.is_file()})
        self.assertEqual(originals, {p.relative_to(self.source): p.read_bytes() for p in self.source.rglob('*') if p.is_file()})
        for name in PACKED:
            self.assertFalse((self.output / name).exists())
            self.assertEqual(originals[Path(name)], gzip.decompress((self.output / (name + '.agz')).read_bytes()))
        self.assertEqual(originals[Path('sample.mp4')], (self.output / 'sample.mp4').read_bytes())

    def test_stale_generated_files_do_not_survive_rebuild(self):
        build(self.source, self.output)
        for name in ['obsolete.bin', PACKED[0], PACKED[0] + '.gz']:
            (self.output / name).write_bytes(b'stale')
        build(self.source, self.output)
        for name in ['obsolete.bin', PACKED[0], PACKED[0] + '.gz']:
            self.assertFalse((self.output / name).exists())
        self.assertTrue((self.source / PACKED[0]).exists())

    def test_missing_selected_asset_fails_instead_of_silent_omission(self):
        (self.source / PACKED[0]).unlink()
        with self.assertRaisesRegex(ValueError, 'Missing packed source'):
            build(self.source, self.output)

    def test_overlapping_source_and_output_rejected(self):
        for output in [self.source, self.source / 'child', self.folder]:
            with self.assertRaisesRegex(ValueError, 'must be separate'):
                build(self.source, output)

    def test_dynamic_media_moves_to_verified_pack_without_touching_other_families(self):
        files={'cosmic/sphere.bin':b'mesh','flyme-video/demo.mp4':b'video','stereo/main.webp':b'image',
               'cosmic/catalog.json':b'[]','infinity/blue/preview.jpg':b'infinity','shader/photo/test.png':b'photo'}
        for name,data in files.items():
            p=self.source/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
        build(self.source,self.output)
        pack=self.output.parent/'dynamic-wallpapers'/PACK_NAME
        first=pack.read_bytes()
        build(self.source,self.output)
        self.assertEqual(first,pack.read_bytes())
        with zipfile.ZipFile(pack) as z:
            manifest=json.loads(z.read('manifest.json'))
            self.assertEqual(z.read('manifest.json'),(self.output/MANIFEST).read_bytes())
            self.assertEqual(set(manifest['files']),{'cosmic/sphere.bin','flyme-video/demo.mp4','stereo/main.webp'})
            for name,data in files.items():
                self.assertEqual((self.source/name).read_bytes(),data)
                if name in manifest['files']:
                    self.assertFalse((self.output/name).exists())
                    self.assertEqual(z.read('assets/'+name),data)
                else:self.assertEqual((self.output/name).read_bytes(),data)


if __name__ == '__main__':
    unittest.main()
