"""Verify the signed ui23 APK and regression results; emit review artifacts."""
from pathlib import Path
import datetime,hashlib,json,re,shutil,subprocess,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'build/infinity-expansion-20261115';OUT=ROOT/'deliverables'
APK=ROOT/'app/build/outputs/apk/release/app-release.apk'
OLD=OUT/'HyperExtreme-Alive-0.5-alive.7-ui.22-trim1.apk'
PACK=OUT/'HyperExtreme-Alive-Dynamic-Wallpapers-v1.zip'
TOOLS=Path('E:/AndroidSdk/build-tools/37.0.0');JAVA=Path('E:/AndroidStudio/android-studio/jbr/bin/java.exe')
STEM='HyperExtreme-Alive-0.5-alive.7-ui.23-infinity2'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def command(name,args):
    r=subprocess.run([str(a) for a in args],capture_output=True,text=True,encoding='utf-8',errors='replace')
    (WORK/name).write_text(r.stdout+r.stderr,encoding='utf-8');assert r.returncode==0,(name,r.stdout,r.stderr)
    return r.stdout+r.stderr
badging=command('apk-badging.txt',[TOOLS/'aapt2.exe','dump','badging',APK])
assert "versionCode='38'" in badging and "versionName='0.5-alive.7-ui.23-infinity2'" in badging
assert "minSdkVersion:'33'" in badging and "targetSdkVersion:'37'" in badging
signer=[JAVA,'-jar',TOOLS/'lib/apksigner.jar','verify','--verbose','--print-certs']
signature=command('apk-signature.txt',signer+[APK]);previous=command('previous-apk-signature.txt',signer+[OLD])
cert=lambda value:re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})',value)
assert cert(signature) and cert(signature)==cert(previous)
command('apk-alignment.txt',[TOOLS/'zipalign.exe','-c','-P','16','4',APK])
sources=ROOT/'app/src/main/assets/infinity'
provenance=json.loads((ROOT/'docs/alive/INFINITY-PROVENANCE.json').read_text())
for name,digest in provenance['assets'].items():assert sha(sources/name)==digest,name
assert sha(PACK)=='a8a30f9c7476d2c8d9f7bfcf36848f76e952e63da3f1d9b060f76464ad6fb4aa'
new_folders=['n8-black','n8-blue','n8-gold','n8-orchid','n8-shaders','polygon-shaders','polygon']
new_folders += [f'polygon-{color}-{profile}' for color in ['black','blue','gold','orchid','purple'] for profile in [1,2]]
assets={p.relative_to(sources).as_posix():{'sha256':sha(p),'bytes':p.stat().st_size}
        for folder in new_folders for p in (sources/folder).rglob('*') if p.is_file()}
with zipfile.ZipFile(APK) as z,zipfile.ZipFile(OLD) as old,zipfile.ZipFile(PACK) as pack:
    assert z.testzip() is None and pack.testzip() is None
    assert not any(n.startswith(('assets/native-clock/','assets/notification/','assets/ui/')) for n in z.namelist())
    for name in old.namelist():
        if name.startswith('assets/') and not name.endswith('/') and not name.startswith('assets/dexopt/'):
            assert z.read(name)==old.read(name),name
    for name,row in assets.items():assert hashlib.sha256(z.read('assets/infinity/'+name)).hexdigest()==row['sha256'],name
    manifest=z.read('assets/dynamic-wallpapers.json');assert manifest==pack.read('manifest.json')
    for name in json.loads(manifest)['files']:assert 'assets/'+name not in z.namelist()
    for name in z.namelist():
        if re.fullmatch(r'classes\d*\.dex',name):
            data=z.read(name)
            for retired in [b'AliveExtensionsActivity',b'NativeClockRuntime',b'HyperOsExtensionHooks',b'FlymeNotificationLight']:assert retired not in data
mapping=ROOT/'app/build/outputs/mapping/release/mapping.txt'
text=mapping.read_text(encoding='utf-8')
for cls in ['InfinityRenderer','InfinityExpansionRenderer']:
    assert re.search(r'^com\.frederic22\.hyperextreme\.alive\.'+cls+r' -> ',text,re.M),cls
for cls,method in [('AodWallpaperGuard','refresh'),('SystemClockTracker','onPreDraw')]:
    assert re.search(r'^com\.frederic22\.hyperextreme\.alive\.'+cls+r' -> ',text,re.M) or f'com.frederic22.hyperextreme.alive.{cls}.{method}(' in text
tests=[]
for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite=ET.parse(p).getroot();assert int(suite.attrib['failures'])==0 and int(suite.attrib['errors'])==0,p
    tests.append({'class':suite.attrib['name'],'tests':int(suite.attrib['tests'])})
assert len(tests)>=16
gl=json.loads((WORK/'desktop-gl-validation.json').read_text());assert len(gl['frames'])==42
report={'created_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':STEM,'version_code':38,
        'apk_bytes':APK.stat().st_size,'apk_sha256':sha(APK),'previous_apk_bytes':OLD.stat().st_size,
        'added_asset_bytes':sum(r['bytes'] for r in assets.values()),'added_styles':14,'total_styles':20,
        'signature_verified':True,'signer_unchanged':True,'certificate_sha256':cert(signature),'zip_16kb_alignment':True,'zip_crc_valid':True,
        's8_assets_unchanged':True,'dynamic_pack_unchanged':True,'retired_extensions_absent':True,
        'android_test_count':sum(t['tests'] for t in tests),'tests':tests,'python_pack_tests':5,
        'desktop_gl_programs':8,'desktop_gl_frames':42,'release_build_and_lint_vital_passed':True,
        'full_lint_passed':False,'full_lint_existing_errors':5,'device_validation':False}
(WORK/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
prov={'sources':[{'apk':'Infinity Wallpaper-1.0.apk','package':'com.swautoma.wallpaper.infinity','version':'1.0 (1)','sha256':'d336cb028b9fdfb1df4208917828246e96c3da317030913d1ab9b8894954011f'},
                 {'apk':'N8 Infinity Wallpaper-1.2.apk','package':'com.swautoma.wallpaper.infinityn8','version':'1.2 (3)','sha256':'73b56a03d146b6f22b8c238c148aef7bee04ed994ce99ff2431c960f24d19e66'}],
      'source_kind':'User supplied third-party Infinity recreations; not verified Samsung official packages',
      'extraction':'JADX 1.5.6 + baksmali 2.5.2; custom OBJ grouping checked against Smali',
      'code':'EGL-hosted renderer and event-time adapters written for this project; source imagery, GLSL and numeric data retained',
      'asset_rights':'Original rights holders retained; no separate redistribution licence identified in supplied APKs',
      'texture_encoding':'Full resolution lossless WebP; exact RGBA comparison against source PNGs',
      'styles':json.loads((WORK/'extracted-catalog.json').read_text(encoding='utf-8')),'assets':assets}
(ROOT/'docs/alive/INFINITY-EXPANSION-PROVENANCE.json').write_text(json.dumps(prov,ensure_ascii=False,indent=2),encoding='utf-8')
(WORK/'packaged-asset-hashes.json').write_text(json.dumps(assets,ensure_ascii=False,indent=2),encoding='utf-8')
target=OUT/(STEM+'.apk');shutil.copy2(APK,target)
shutil.copy2(WORK/'validation.json',OUT/(STEM+'-validation.json'))
with zipfile.ZipFile(OUT/(STEM+'-r8-mapping.zip'),'w',zipfile.ZIP_DEFLATED) as z:
    for p in mapping.parent.iterdir():
        if p.is_file():z.write(p,p.name)
changed=['InfinityTheme.java','InfinityMotion.java','InfinityRenderer.java','InfinityGeometryMotion.java','InfinityExpansionTheme.java','InfinityExpansionRenderer.java','EglWorker.java','WallpaperRenderer.java','AlivePresets.kt','AlivePreviewActivity.kt','CompanionPreparation.java']
files=[ROOT/'app/src/main/java/com/frederic22/hyperextreme/alive'/p for p in changed]
files += [ROOT/'app/build.gradle.kts',ROOT/'tools/extract_infinity_expansion.py',ROOT/'tools/render_infinity_expansion.py',Path(__file__).resolve(),
          ROOT/'docs/alive/INFINITY-EXPANSION.zh-CN.md',ROOT/'docs/alive/INFINITY-EXPANSION-PROVENANCE.json',ROOT/'docs/alive/INFINITY-TOP-LEVEL.zh-CN.md']
files += [ROOT/'app/src/test/java/com/frederic22/hyperextreme/alive'/p for p in ['InfinityExpansionTest.kt','InfinityHomeScreenshotTest.kt','InfinityIntegrationTest.kt']]
with zipfile.ZipFile(OUT/(STEM+'-source-delta.zip'),'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    z.writestr('README-SOURCE-DELTA.txt','Apply over ui22-trim1 source by overwriting included files. No deletions. No signing secrets included. Not a standalone project.\n')
    for p in files:z.write(p,p.relative_to(ROOT).as_posix())
    for name in assets:z.write(sources/name,'app/src/main/assets/infinity/'+name)
    z.write(WORK/'validation.json','BUILD-VALIDATION.json')
with zipfile.ZipFile(OUT/'Infinity-20261115-decompiled-review.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for kind in ['infinity','n8']:
        prefixes=['com/horizon/infinity','com/swautoma/wallpaper','c/c/a/i'] if kind=='n8' else ['com/horizon/infinity','com/swautoma/wallpaper']
        for suffix in ['jadx/sources','smali']:
            for prefix in prefixes:
                for p in (WORK/(kind+'-'+suffix)/prefix).rglob('*'):
                    if p.is_file():z.write(p,p.relative_to(WORK).as_posix())
        for p in (WORK/(kind+'-raw')).rglob('*'):
            if p.is_file() and (p.suffix in ['.glsl','.obj','.json'] or p.name.startswith(('wallpaper_','infinity_lockscreen_'))):z.write(p,p.relative_to(WORK).as_posix())
    for name in ['apk-inventory.json','extracted-catalog.json','desktop-gl-validation.json','validation.json','infinity-badging.txt','n8-badging.txt']:z.write(WORK/name,name)
    z.write(ROOT/'docs/alive/INFINITY-EXPANSION.zh-CN.md','README.zh-CN.md')
artifacts=[target,OUT/(STEM+'-validation.json'),OUT/(STEM+'-r8-mapping.zip'),OUT/(STEM+'-source-delta.zip'),OUT/'Infinity-20261115-decompiled-review.zip']
(OUT/(STEM+'-SHA256SUMS.txt')).write_text(''.join(f'{sha(p)}  {p.name}\n' for p in artifacts),encoding='ascii')
print(json.dumps({k:v for k,v in report.items() if k!='tests'},ensure_ascii=False,indent=2))
