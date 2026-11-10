"""Offline byte/dex-contract checks; never use ADB and never claim runtime rendering validation."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
checks = 0


def check(value, label):
    global checks
    if not value:
        raise AssertionError(label)
    checks += 1


def classes(path):
    from androguard.core.dex import DEX
    if path.suffix == '.dex':
        return {c.get_name(): c for c in DEX(path.read_bytes()).get_classes()}
    result = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                result.update({c.get_name(): c for c in DEX(archive.read(name)).get_classes()})
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--rom', type=Path, help='Optional archived MIUIAod APK; requires androguard')
    parser.add_argument('--dex', action='store_true', help='Inspect isolated runtime classes with androguard')
    args = parser.parse_args()
    provenance = json.loads((ROOT / 'docs/alive/IMPORTED-EXTENSIONS.json').read_text(encoding='utf-8'))
    for item in provenance['files']:
        path = ROOT / item['destination']
        check(path.is_file(), 'Missing import: ' + item['destination'])
        if '/assets/' in item['destination']:
            check(hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], 'Changed official asset: ' + path.name)
    for data, digest in [('native-clock/runtime/hyperos/runtime.apk', 'native-clock/runtime/hyperos/runtime.sha256'),
                         ('native-clock/runtime/flyme/code.dex', 'native-clock/runtime/flyme/code.sha256'),
                         ('native-clock/vivo/layouts.apk', 'native-clock/vivo/layouts.sha256'),
                         ('ui/editor-ui.apk', 'ui/editor-ui.sha256')]:
        check(hashlib.sha256((ASSETS/data).read_bytes()).hexdigest() == (ASSETS/digest).read_text().strip(), data)
    bridge = ROOT/'app/build/generated/aliveClockAssets/native-clock/bridge.dex'
    check(hashlib.sha256(bridge.read_bytes()).hexdigest() == bridge.with_suffix('.sha256').read_text().strip(), 'Bridge SHA256')
    hooks = (ROOT/'app/src/main/java/com/frederic22/hyperextreme/alive/HyperOsExtensionHooks.java').read_text(encoding='utf-8')
    check('"requestPulse"' not in hooks, 'No speculative notification pulse hook')
    check('"startAnimation"' in hooks and '"stopAnimation"' in hooks, 'ROM notification timeline ownership')
    if args.dex or args.rom:
        from loguru import logger
        logger.remove()
        expected_bridge = {'Lmiui/util/font/MultiLangHelper;', 'Lmiui/util/FeatureParser;',
                           'Lmiui/os/Build;', 'Lcom/miui/aod/utils/LooperUtils;'}
        check(set(classes(bridge)) == expected_bridge, 'Only four compatibility classes in bridge DEX')
        flyme = classes(ASSETS/'native-clock/runtime/flyme/code.dex')
        for name in ['T4/f', 'P4/q', 'P4/d', 'com/opensource/svgaplayer/SVGAImageView']:
            check('L'+name+';' in flyme, 'SVGA contract ' + name)
        hyperos = classes(ASSETS/'native-clock/runtime/hyperos/runtime.apk')
        for name in ['com/miui/clock/MiuiClockView', 'com/miui/clock/module/ClockViewType', 'com/miui/clock/utils/FontUtils']:
            check('L'+name+';' in hyperos, 'Clock runtime contract ' + name)
        if args.rom:
            rom = classes(args.rom)
            contracts = {
                'com/miui/aod/AODView': [('onAttachedToWindow', '()V'), ('onDetachedFromWindow', '()V'), ('makeNormalPanel', '()V')],
                'com/miui/aod/DozeHost': [('isDozing', '()Z'), ('isAodVisible', '()Z'), ('isFingerprintPressed', '()Z'),
                    ('fireFingerprintPressed', '(Z Z)V'), ('onFingerprintPressed', '(Z Z)V'),
                    ('onStopDoze', '()V'), ('screenTurnOn', '()V'), ('removeAODView', '()V'), ('disableShowWallpaper', '()V'),
                    ('showSecurityIdentityViewAt', '(Landroid/view/ViewGroup;)Lcom/miui/aod/AODView;'),
                    ('dozeTimeTick', '(Z)V'), ('setKeyguardOccluded', '(Z)V'), ('setAodVisibility', '(Z)V')],
                'com/miui/aod/widget/NotificationAnimationView': [('startAnimation', '()V'), ('stopAnimation', '()V')],
                'com/miui/clock/MiuiClockView': [('getTopMargin', '()F')],
                'com/miui/aod/Utils': [('isUsingSuperWallpaperStyle', '(Landroid/content/Context;)Z'), ('isAodEnable', '(Landroid/content/Context;)Z')],
                'com/miui/aod/widget/AODSettings': [('isSupportAodWallpaper', '(Landroid/content/Context;)Z')],
                'com/miui/aod/util/WallPaperUtils': [('isWallpaperSupportsAmbientMode', '()Z')],
            }
            for name, methods in contracts.items():
                cls = rom.get('L'+name+';'); check(cls is not None, name)
                signatures = {(m.get_name(), m.get_descriptor()) for m in cls.get_methods()}
                for method in methods:
                    check(method in signatures, name + str(method))
            for name, field in [('com/miui/aod/AODView', 'mHost'), ('com/miui/aod/DozeHost', 'mKeyguardOccluded'),
                                ('com/miui/aod/DozeHost', 'mNeedShowWallpaper'), ('com/miui/aod/DozeHost', 'mAODView'),
                                ('com/miui/aod/DozeHost', 'mFingerprintPressed'), ('com/miui/aod/DozeHost', 'lp'),
                                ('com/miui/aod/widget/NotificationAnimationView', 'mAnimationRunning')]:
                check(field in {f.get_name() for f in rom['L'+name+';'].get_fields()}, name + '.' + field)
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            for item in provenance['files']:
                if '/assets/' in item['destination']:
                    name = 'assets/' + item['destination'].split('/assets/', 1)[1]
                    check(hashlib.sha256(apk.read(name)).hexdigest() == item['sha256'], 'Packaged asset ' + name)
            check(apk.read('assets/native-clock/bridge.dex') == bridge.read_bytes(), 'Packaged bridge')
        if args.dex:
            check(not expected_bridge.intersection(classes(args.apk)), 'Original MIUI compatibility classes not merged into app DEX')
    result = {'checks': checks, 'deviceUsed': False, 'rendererInflationTested': False,
              'archivedRomSha256': hashlib.sha256(args.rom.read_bytes()).hexdigest() if args.rom else None,
              'apkSha256': hashlib.sha256(args.apk.read_bytes()).hexdigest() if args.apk else None}
    output = ROOT/'build/reports/alive-extensions-offline.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
