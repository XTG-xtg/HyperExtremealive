"""Build the OpenAlive-derived photo frame graph from pinned Rust source."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import struct
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ndk', required=True)
    parser.add_argument('--cargo', default=os.environ.get('CARGO', 'cargo'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    host = {'Windows': 'windows-x86_64', 'Linux': 'linux-x86_64', 'Darwin': 'darwin-x86_64'}[platform.system()]
    toolchain = Path(args.ndk) / 'toolchains/llvm/prebuilt' / host
    suffix = '.exe' if os.name == 'nt' else ''
    clang = toolchain / 'bin' / ('clang' + suffix)
    target = root / 'app/build/alive-photo-target'
    destination = root / 'app/build/generated/aliveJni/arm64-v8a'
    env = os.environ.copy()
    env['CARGO_TARGET_DIR'] = str(target)
    env['CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER'] = str(clang)
    env.pop('RUSTFLAGS', None)
    env['CARGO_ENCODED_RUSTFLAGS'] = '\x1f'.join([
        '-C', 'link-arg=--target=aarch64-linux-android33',
        '-C', 'link-arg=-Wl,-z,max-page-size=16384',
        '--remap-path-prefix=' + str(root) + '=/hyperextreme',
    ])
    subprocess.run([args.cargo, 'build', '--locked', '--release', '--target', 'aarch64-linux-android'],
                   cwd=root / 'alive-photo', env=env, check=True)
    library = target / 'aarch64-linux-android/release/libhyperextreme_photo.so'
    data = library.read_bytes()
    if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
        raise RuntimeError('Expected little-endian AArch64 ELF64')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    for i in range(count):
        header = offset + i * size
        if struct.unpack_from('<I', data, header)[0] == 1:
            if struct.unpack_from('<Q', data, header + 48)[0] < 16384:
                raise RuntimeError('Photo library is not 16KB page aligned')
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(library, destination / library.name)
    print('Photo native library built from source; AArch64 / 16KB verified')


if __name__ == '__main__':
    main()
