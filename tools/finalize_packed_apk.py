"""Align the stored LSPosed bridge after AGP legacy-JNI packaging; preserve variant signing identity."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def finalize(apk, sdk, build_tools, java_home, keystore, alias, min_sdk):
    apk = apk.resolve()
    if not apk.is_file() or not apk.is_relative_to((ROOT / 'app/build').resolve()):
        raise ValueError('Only a generated app/build APK may be finalized')
    ext = '.exe' if os.name == 'nt' else ''
    align = str(sdk / 'build-tools' / build_tools / ('zipalign' + ext))
    signer = [str(java_home / 'bin' / ('java' + ext)), '-jar',
              str(sdk / 'build-tools' / build_tools / 'lib/apksigner.jar')]

    def certificates(path):
        result = subprocess.run(signer + ['verify', '--print-certs', str(path)],
                                check=True, capture_output=True, text=True)
        digests = sorted(set(re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', result.stdout)))
        if not digests:
            raise ValueError('No verified signing certificate')
        return digests

    original_signers = certificates(apk)
    with tempfile.NamedTemporaryFile(prefix='aligned-', suffix='.apk', dir=apk.parent, delete=False) as temp:
        aligned = Path(temp.name).resolve()
    assert aligned.parent == apk.parent and aligned != apk
    try:
        subprocess.run([align, '-f', '-P', '16', '4', str(apk), str(aligned)], check=True)
        subprocess.run(signer + ['sign', '--ks', str(keystore), '--ks-key-alias', alias,
            '--ks-pass', 'env:ALIVE_STORE_PASSWORD', '--key-pass', 'env:ALIVE_KEY_PASSWORD',
            '--min-sdk-version', str(min_sdk), '--v2-signing-enabled', 'true',
            '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false', str(aligned)], check=True)
        if certificates(aligned) != original_signers:
            raise ValueError('Signing certificate changed; refusing to publish APK')
        subprocess.run([align, '-c', '-P', '16', '4', str(aligned)], check=True)
        os.replace(aligned, apk)
        # Any detached v4 signature produced for the pre-alignment APK is now stale.
        apk.with_name(apk.name + '.idsig').unlink(missing_ok=True)
        print('Finalized 16KB-aligned APK: ' + str(apk))
        print('SHA256: ' + hashlib.sha256(apk.read_bytes()).hexdigest())
    finally:
        aligned.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--build-tools', required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--alias', required=True)
    parser.add_argument('--min-sdk', type=int, required=True)
    args = parser.parse_args()
    finalize(args.apk, args.sdk, args.build_tools, args.java_home, args.keystore, args.alias, args.min_sdk)
