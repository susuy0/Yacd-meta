import os, pathlib, subprocess, sys, tempfile, zipfile
apk=pathlib.Path(sys.argv[1]);sdk=pathlib.Path(os.environ['ANDROID_HOME'])
aapt=sdk/'build-tools/36.0.0/aapt'
manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
for component in ['org.telegram.messenger.voip.VoIPService','org.telegram.messenger.voip.TelegramConnectionService','org.telegram.messenger.ContactsSyncAdapterService','org.telegram.messenger.AuthenticatorService','org.telegram.ui.VoIPPermissionActivity','org.telegram.ui.VoIPFeedbackActivity','org.telegram.messenger.LocationSharingService']:
    assert component not in manifest,component
for screen in ['com.futoonpay.admin.MainActivity','com.futoonpay.admin.NativeModuleActivity','com.futoonpay.admin.DomainAuditActivity']:
    assert screen not in manifest, 'Standalone admin screen must not be packaged: '+screen
assert '"androidx.activity.ComponentActivity"' not in manifest, 'Compose test activity must not be exported by the APK'
permissions=subprocess.check_output([str(aapt),'dump','permissions',str(apk)],text=True)
for name in ['RECORD_AUDIO','READ_CONTACTS','WRITE_CONTACTS','CAMERA','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION','ACCESS_MEDIA_LOCATION','READ_MEDIA_VIDEO','FOREGROUND_SERVICE_CAMERA','FOREGROUND_SERVICE_LOCATION','FOREGROUND_SERVICE_MICROPHONE']:
    assert 'android.permission.'+name not in permissions,name
nm=sdk/'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
with zipfile.ZipFile(apk) as z,tempfile.TemporaryDirectory() as work:
    assert not any(name.startswith('assets/futoon/') for name in z.namelist()), 'Obsolete business WebView assets must not be packaged'
    for entry in z.namelist():
        if entry.startswith('lib/') and entry.endswith('/libtmessages.49.so'):
            lib=pathlib.Path(work)/'core.so';lib.write_bytes(z.read(entry))
            symbols=subprocess.check_output([str(nm),'-D','--defined-only',str(lib)],text=True)
            for removed in ['Java_org_telegram_messenger_voip_','Java_org_webrtc_','MediaController_startRecord','MediaController_writeFrame','MediaController_stopRecord']:
                assert removed not in symbols,removed
            for required in ['MediaController_isOpusFile','MediaController_getWaveform','JNI_OnLoad']:
                assert required in symbols,required
            print(entry,lib.stat().st_size,'bytes; call/record engine exports absent; voice decoder exports present')
print('PASS: removed Android components, permissions and native engines audited')

