#!/usr/bin/env python3
"""Run the same Android checks locally, with optional verified native-library reuse."""
from pathlib import Path
import argparse,fcntl,json,os,shlex,shutil,subprocess,urllib.parse
p=argparse.ArgumentParser();p.add_argument('action',choices=['sdk','tests','client','apk']);p.add_argument('--host',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--gradle',type=Path,required=True);p.add_argument('--native-dir',type=Path);p.add_argument('--state',type=Path,required=True);p.add_argument('--accept-sdk-licenses',action='store_true');p.add_argument('--test-filter');a=p.parse_args();repo=Path(__file__).resolve().parents[1];a.state.mkdir(parents=True,exist_ok=True)
env=dict(os.environ,JAVA_HOME=str(a.jdk.resolve()),ANDROID_HOME=str(a.sdk.resolve()),GRADLE_USER_HOME=str(a.state/'gradle-cache'));env['PATH']=str(a.jdk.resolve()/'bin')+os.pathsep+str(a.gradle.resolve()/'bin')+os.pathsep+env['PATH'];opts=[];proxy=urllib.parse.urlparse(env.get('HTTPS_PROXY',''))
if proxy.hostname:
 for scheme in ['http','https']:opts+=['-D'+scheme+'.proxyHost='+proxy.hostname,'-D'+scheme+'.proxyPort='+str(proxy.port or 80)]
 if Path('/etc/ssl/certs/java/cacerts').exists():opts+=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
 env['GRADLE_OPTS']=' '.join(shlex.quote(v)for v in shlex.split(env.get('GRADLE_OPTS',''))if not any(k in v for k in ['http.proxyHost','http.proxyPort','https.proxyHost','https.proxyPort']))+' '+' '.join(opts)
 env['JAVA_TOOL_OPTIONS']=(env.get('JAVA_TOOL_OPTIONS','')+' '+' '.join(opts)).strip()
build_lock=(a.state/'local-gradle.lock').open('a');fcntl.flock(build_lock,fcntl.LOCK_EX)
commands=[];result=a.state/(a.action+'-result.json');result.unlink(missing_ok=True)
with(a.state/(a.action+'.log')).open('w')as log:
 def run(cmd,**kw):
  r=subprocess.run([str(x)for x in cmd],env=env,stdout=log,stderr=subprocess.STDOUT,**kw);commands.append({'program':Path(cmd[0]).name,'exit_code':r.returncode})
  if r.returncode:raise SystemExit(r.returncode)
 if a.action=='sdk':
  sdkmanager=a.sdk/'cmdline-tools/latest/bin/sdkmanager'
  if a.accept_sdk_licenses:run([sdkmanager,'--licenses'],input='y\n'*150,text=True)
  run([sdkmanager,'platforms;android-36','build-tools;36.0.0','platform-tools'])
 elif a.action=='tests':
  tests=a.state/'native-tests'
  if not(tests/'settings.gradle.kts').exists():run(['bash',repo/'ci/test_native_admin.sh',tests])
  else:
   shutil.copytree(repo/'FutoonAdmin',tests/'FutoonAdmin',dirs_exist_ok=True,ignore=shutil.ignore_patterns('build','.gradle'))
   run(['gradle','-p',tests,':FutoonAdmin:testDebugUnitTest',*(['--tests',a.test_filter]if a.test_filter else [':FutoonAdmin:lintDebug']),'--no-daemon','--max-workers=2','-PfutoonUiTesting=true','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g','--console=plain','--stacktrace'])
 else:
  shutil.copytree(repo/'FutoonAdmin',a.host/'FutoonAdmin',dirs_exist_ok=True,ignore=shutil.ignore_patterns('build','.gradle'))
  (a.host/'local.properties').write_text('sdk.dir='+str(a.sdk.resolve())+'\n');env['REQUIRE_PUSH']='false';run(['python3',repo/'ci/configure.py','--root',a.host]);cmd=['gradle','-p',a.host,':TMessagesProj:compileDebugJavaWithJavac'if a.action=='client'else':TMessagesProj_App:assembleAfatDebug','-PvineFutoonDev=true','-PvineAbis=arm64-v8a','-PvineSkipGoogleServices=true','--no-daemon','--max-workers=2','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1g','--console=plain','--stacktrace']
  if a.native_dir:cmd+=['-PvineNativeDir='+str(a.native_dir.resolve())]
  run(cmd)
result.write_text(json.dumps({'action':a.action,'completed':True,'commands':commands},indent=2));print(a.action+' passed',flush=True)
