#!/usr/bin/env python3
"""Verify a completed local build, retain its R8 mapping, and prepare APK delivery."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,xml.etree.ElementTree as ET,zipfile
p=argparse.ArgumentParser();p.add_argument('--host',type=Path,required=True);p.add_argument('--state',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);p.add_argument('--version',required=True);a=p.parse_args();repo=Path(__file__).resolve().parents[1];host=a.host.resolve();state=a.state.resolve();out=a.output_dir.resolve();out.mkdir(parents=True,exist_ok=True)
def sha(path):
 with path.open('rb')as f:return hashlib.file_digest(f,'sha256').hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
for action in ['tests','client','apk']:
 result=json.loads((state/(action+'-result.json')).read_text());assert result['completed']and all(x['exit_code']==0 for x in result['commands']),action
results=state/'native-tests/FutoonAdmin/build/test-results/testDebugUnitTest';suites=[ET.parse(f).getroot()for f in results.glob('TEST-*.xml')];counts={k:sum(int(s.get(k,0))for s in suites)for k in ['tests','failures','errors','skipped']};assert counts['tests']>=340 and all(counts[k]==0 for k in ['failures','errors','skipped']),counts
lint=state/'native-tests/FutoonAdmin/build/reports/lint-results-debug.xml';issues=ET.parse(lint).getroot().findall('issue');lint_counts={level:sum(i.get('severity')==level for i in issues)for level in ['Fatal','Error','Warning','Information']};assert not lint_counts['Fatal']and not lint_counts['Error'],lint_counts
# Compare the complete host against a fresh application of the source patch.
with tempfile.TemporaryDirectory(prefix='futoon-source-proof-')as folder:
 env=dict(os.environ,GIT_INDEX_FILE=str(Path(folder)/'index'))
 for cmd in [['git','read-tree','HEAD'],['git','apply','--cached','--whitespace=nowarn',str(repo/'integration.patch')],['git','diff','--exit-code','--ignore-submodules=all','--','.',':!FutoonAdmin']]:subprocess.run(cmd,cwd=host,env=env,check=True,stdout=subprocess.DEVNULL)
module_files=git('ls-files','FutoonAdmin').splitlines()
for file in module_files:assert (repo/file).read_bytes()==(host/file).read_bytes(),file
apk=out/f'SMS-Futoon-v{a.version}-arm64.apk';shutil.copyfile(host/'TMessagesProj_App/build/outputs/apk/afat/debug/app.apk',apk)
verified=json.loads(subprocess.check_output(['python3',str(repo/'tools/verify_release.py'),str(apk)],text=True));verified['filename']=apk.name
with zipfile.ZipFile(apk)as z:native_sha=hashlib.sha256(z.read('lib/arm64-v8a/libtmessages.49.so')).hexdigest()
assert native_sha=='b9371dc7147f557eee08b7058d1fb6f141ca45a01715fc6248598668bea1798c',native_sha
mapping=host/'TMessagesProj_App/build/outputs/mapping/afatDebug/mapping.txt';mapping_record={'bytes':mapping.stat().st_size,'sha256':sha(mapping)}
images=sorted((state/'native-tests/FutoonAdmin/build/ui-screenshots').glob('*.png'))
record={'version':a.version,'source_commit':git('rev-parse','HEAD'),'source_tree':git('rev-parse','HEAD^{tree}'),'patch_sha256':sha(repo/'integration.patch'),'apk':verified,'tests':dict(counts,suites=len(suites)),'lint':lint_counts,'synthetic_screenshots':len(images),'module_files_compared':len(module_files),'full_host_matches_patch':True,'native_library_sha256':native_sha,'mapping':mapping_record,'build':'Local Gradle8.13 / JDK17 / SDK36; verified unchanged native library reused','limits':['Robolectric SDK35 synthetic fixtures, not an Android17 device test','No authenticated WhatsApp runtime capture or complete pixel parity','Live backend mutations, recipient delivery, gallery/keyboard device behavior and FCM delivery were not exercised']}
(state/'release-verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n')
archive=out/f'SMS-Futoon-v{a.version}-build-records.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6)as z:
 z.write(mapping,'mapping.txt');z.write(state/'release-verification.json','release-verification.json');z.write(lint,'lint-results-debug.xml')
 for f in results.glob('TEST-*.xml'):z.write(f,'test-results/'+f.name)
 for f in images:z.write(f,'synthetic-ui/'+f.name)
 for name in ['tests-result.json','client-result.json','apk-result.json','core-checks.json','tests.log','client.log','apk.log']:
  if(state/name).exists():z.write(state/name,'checks/'+name)
record['build_records']={'filename':archive.name,'bytes':archive.stat().st_size,'sha256':sha(archive)}
(out/'release-summary.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n');print(json.dumps(record,ensure_ascii=False,indent=2))
