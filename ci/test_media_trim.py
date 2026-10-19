#!/usr/bin/env python3
"""Run production permission dispatch with Activity fakes and audit removed capture routes."""
from pathlib import Path
import os,re,subprocess,sys,tempfile,xml.etree.ElementTree as ET
root=Path(sys.argv[1]); src=root/'TMessagesProj/src/main/java'
production=['org/telegram/messenger/futoon/FutoonMediaPolicy.java','org/telegram/ui/Components/FutoonPermissionRequests.java']
stubs={
'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=35; } }',
'android/app/Activity.java': '''package android.app; public class Activity {
 public int requests,callbacks,code; public String[] requested,returned; public int[] grants;
 public void requestPermissions(String[] p,int c){requests++;requested=p;code=c;}
 public void onRequestPermissionsResult(int c,String[] p,int[] g){callbacks++;code=c;returned=p;grants=g;}
}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; public class AndroidUtilities {
 private static java.util.ArrayList<Runnable> queue=new java.util.ArrayList<>();
 public static void runOnUIThread(Runnable action){queue.add(action);}
 public static void flush(){while(!queue.isEmpty()){java.util.ArrayList<Runnable> copy=new java.util.ArrayList<>(queue);queue.clear();for(Runnable r:copy)r.run();}}
}''',
'TrimTest.java': '''import android.app.Activity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.futoon.FutoonMediaPolicy;
import org.telegram.ui.Components.FutoonPermissionRequests;
import java.util.Arrays;
public class TrimTest {
 static int checks;
 static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 static void equal(int[] a,int[] b){check(Arrays.equals(a,b),Arrays.toString(a)+" != "+Arrays.toString(b));}
 public static void main(String[] args) {
  String p="android.permission.";
  for(String permission:new String[]{"CAMERA","RECORD_AUDIO","ACCESS_FINE_LOCATION","ACCESS_COARSE_LOCATION","ACCESS_BACKGROUND_LOCATION","ACCESS_MEDIA_LOCATION","READ_MEDIA_VIDEO","FOREGROUND_SERVICE_CAMERA","FOREGROUND_SERVICE_MICROPHONE","FOREGROUND_SERVICE_LOCATION"}) {
   Activity activity=new Activity();
   FutoonPermissionRequests.request(activity,new String[]{p+permission},18);
   check(activity.requests==0,"No OS dialog for "+permission);
   check(activity.callbacks==0,"Completion must be queued");
   AndroidUtilities.flush();
   check(activity.callbacks==1,"Removed request must complete exactly once");
   FutoonPermissionRequests.Result result=FutoonPermissionRequests.restore(activity,18,activity.returned,activity.grants);
   equal(result.grants,new int[]{-1});
   equal(FutoonMediaPolicy.restore(new String[]{p+permission},new String[]{p+permission},new int[]{0}),new int[]{-1});
  }
  String image=p+"READ_MEDIA_IMAGES", audio=p+"READ_MEDIA_AUDIO", notify=p+"POST_NOTIFICATIONS", storage=p+"READ_EXTERNAL_STORAGE", camera=p+"CAMERA", video=p+"READ_MEDIA_VIDEO";
  for(String permission:new String[]{image,audio,notify,storage,p+"WRITE_EXTERNAL_STORAGE"}) {
   Activity activity=new Activity();String[] requested={permission};
   FutoonPermissionRequests.request(activity,requested,4);
   check(activity.requests==1&&Arrays.equals(activity.requested,requested),"Preserve allowed OS request: "+permission);
   check(activity.callbacks==0,"Do not fabricate allowed grants");
  }
  Activity a=new Activity(),b=new Activity();String[] mixed={camera,image,video,audio};
  FutoonPermissionRequests.request(a,mixed,4);mixed[0]="mutated";
  FutoonPermissionRequests.request(b,new String[]{notify,camera},4);
  check(Arrays.equals(a.requested,new String[]{image,audio}),"Filter only removed capabilities");
  check(Arrays.equals(b.requested,new String[]{notify}),"Isolate request state by Activity");
  FutoonPermissionRequests.Result ar=FutoonPermissionRequests.restore(a,4,new String[]{audio,image},new int[]{-1,0});
  equal(ar.grants,new int[]{-1,0,-1,-1});
  check(ar.permissions[0].equals(camera),"Copy caller's permission array");
  equal(FutoonPermissionRequests.restore(b,4,new String[]{notify},new int[]{0}).grants,new int[]{0,-1});
  FutoonPermissionRequests.request(a,new String[]{image,camera},5);
  equal(FutoonPermissionRequests.restore(a,5,new String[0],new int[0]).grants,new int[]{-1,-1});
  equal(FutoonMediaPolicy.restore(new String[]{image,camera},null,null),new int[]{-1,-1});
  equal(FutoonMediaPolicy.restore(new String[]{image,audio},new String[]{image,audio},new int[]{0}),new int[]{0,-1});
  FutoonPermissionRequests.request(a,new String[0],6);AndroidUtilities.flush();
  check(a.requests==2,"Never issue an empty OS request");
  check(!FutoonMediaPolicy.CAMERA_CAPTURE&&!FutoonMediaPolicy.LOCATION_SHARING&&!FutoonMediaPolicy.VIDEO_EDITING,"Product capabilities");
  System.out.println("PASS: "+checks+" production permission dispatch checks");
 }
}'''}
with tempfile.TemporaryDirectory() as temporary:
 work=Path(temporary);files=[]
 for relative in production:
  target=work/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((src/relative).read_bytes());files.append(str(target))
 for relative,contents in stubs.items():
  target=work/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(contents);files.append(str(target))
 compiler=['java','-jar',os.environ['ECJ_JAR'],'-17'] if os.environ.get('ECJ_JAR') else ['javac','-encoding','UTF-8']
 subprocess.run(compiler+['-d',str(work)]+files,check=True)
 subprocess.run(['java','-cp',str(work),'TrimTest'],check=True)
namespace={'android':'http://schemas.android.com/apk/res/android','tools':'http://schemas.android.com/tools'}
blocked={'CAMERA','RECORD_AUDIO','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION','ACCESS_MEDIA_LOCATION','READ_MEDIA_VIDEO','FOREGROUND_SERVICE_CAMERA','FOREGROUND_SERVICE_MICROPHONE','FOREGROUND_SERVICE_LOCATION'}
for manifest in [root/'TMessagesProj/src/main/AndroidManifest.xml',*(root/'TMessagesProj/config').rglob('AndroidManifest*.xml')]:
 for node in ET.parse(manifest).getroot().findall('uses-permission'):
  name=node.get('{'+namespace['android']+'}name','').removeprefix('android.permission.')
  assert name not in blocked or node.get('{'+namespace['tools']+'}node')=='remove',(manifest,name)
for path in src.rglob('*.java'):
 text=path.read_text()
 assert 'MediaStore.ACTION_IMAGE_CAPTURE' not in text,path
 assert 'MediaStore.ACTION_VIDEO_CAPTURE' not in text,path
 if path.name not in {'FutoonPermissionRequests.java','BotLocation.java'}:
  assert not re.search(r'\b(?:activity|parentActivity|getParentActivity\(\))\.requestPermissions\(',text),path
camera=(src/'org/telegram/messenger/camera/CameraController.java').read_text()
assert 'Camera.open(' not in camera and 'new MediaRecorder(' not in camera
location=(src/'org/telegram/messenger/LocationController.java').read_text()
assert 'requestLocationUpdates(' not in location and 'startService(' not in location
viewer=(src/'org/telegram/ui/PhotoViewer.java').read_text()
assert 'if (isCurrentVideo && mode != EDIT_MODE_NONE) return;' in viewer
assert 'if (isCurrentVideo) return null;' in viewer
assert 'photoCropView.setBitmap' in viewer and 'EDIT_MODE_PAINT' in viewer
assert 'videoPlayer' in viewer
panel=(src/'org/telegram/ui/FutoonPanel.java').read_text()
assert 'sendEvidence' in panel
assert 'ACTION_OPEN_DOCUMENT' in panel or 'ACTION_GET_CONTENT' in panel
print('PASS: all manifest variants, external capture removal, hardware stubs, image editor and evidence selection boundaries')

web=(src/'org/telegram/ui/web/BotWebViewContainer.java').read_text()
assert 'callback.invoke(origin, false, false);' in web
assert 'PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)' in web
assert 'PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)' in web
provider=(src/'org/telegram/messenger/GoogleLocationProvider.java').read_text()
assert 'com.google.android.gms.location' not in provider
assert 'play-services-location:' not in (root/'TMessagesProj/build.gradle').read_text()
scan=(src/'org/telegram/ui/CameraScanActivity.java').read_text()
assert 'Uri uri = Uri.parse(text);' not in scan, 'Gallery QR must accept plain text without dereferencing a null URI path'
assert 'currentType == TYPE_QR_LOGIN && !text.startsWith("tg://login?token=")' in scan
print('PASS: no Mini App capture prompt, no fused-location SDK, gallery QR login validation retained')
