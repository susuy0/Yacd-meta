#!/usr/bin/env python3
"""Execute the production send-state conditions; audit the native chat-tool entry points."""
from pathlib import Path
import re, subprocess, sys, tempfile

root = Path(sys.argv[1])
base = root / 'TMessagesProj/src/main/java/org/telegram'
composer = (base / 'ui/Components/ChatActivityEnterView.java').read_text()
def method(signature):
    start = composer.index(signature); opening = composer.index('{', start); end = opening + 1; depth = 1
    while depth:
        depth += (composer[end] == '{') - (composer[end] == '}'); end += 1
    return composer[start:end]
visible_line = next(line.strip() for line in composer.splitlines() if line.strip().startswith('} else if (message.length() > 0'))
visible = visible_line[len('} else if ('):-3]
start = composer.index(visible_line)
transition_line = next(line.strip() for line in composer[start:].splitlines() if line.strip().startswith('if (audioVideoButtonContainer.getVisibility() == VISIBLE || slowModeButton'))
transition = transition_line[len('if ('):-3]
test = '''
public class ChatSendStateTest {
 static final int VISIBLE=0, GONE=8, INVISIBLE=4;
 static class View { int visibility=GONE; float alpha=0; int getVisibility(){return visibility;} float getAlpha(){return alpha;} }
 static class Flag { boolean value; boolean getValue(){return value;} }
 static class Composer {
  Object parentFragment=new Object(), audioToSend, videoToSendMessageObject;
  boolean isStories,isLiveComment,forceShowSendButton,richDraftActive,showBotButton,showSendButton;
  int slowModeTimer;
  CharSequence message=""; String caption=null;
  View audioVideoButtonContainer=new View(),slowModeButton=new View(),send=new View();
  Flag animatorIsBlockedByStreaming=new Flag();
  boolean isSlowModeIgnored(){return false;} long getStarsPrice(){return 0;}
  View getSendButtonInternal(){return send;}
  COMPOSER
  boolean wantsSend(){return VISIBLE_CONDITION;}
  boolean needsTransition(){return TRANSITION_CONDITION;}
 }
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  Composer c=new Composer();
  c.send.visibility=INVISIBLE;
  check(c.wantsSend()&&c.needsTransition(),"Empty Lite composer must expose send/quick-reply entry after recording control removal");
  c.message="订单 123";
  check(c.wantsSend()&&c.needsTransition(),"Typing must restore a previously invisible send button");
  c.send.visibility=VISIBLE;c.send.alpha=0;
  check(c.needsTransition(),"Zero-alpha send control must recover");
  c.send.alpha=1;
  check(!c.needsTransition(),"Visible stable button must not restart animations every update");
  org.telegram.messenger.BuildVars.FUTOON_LITE=false;c.message="";
  check(!c.wantsSend(),"Non-Lite empty composer retains original behavior");
  c.forceShowSendButton=true;
  check(c.wantsSend(),"Forwarding/forced-send state must remain supported");
  org.telegram.messenger.BuildVars.FUTOON_LITE=true;c.forceShowSendButton=false;c.parentFragment=null;
  check(!c.wantsSend(),"Story/media composers must not gain chat-only quick replies");
  System.out.println("PASS: production empty/typed/hidden/zero-alpha send states, stable animation and original composer behavior");
 }
}
'''.replace('COMPOSER',method('private boolean isFutoonComposer()')).replace('VISIBLE_CONDITION',visible).replace('TRANSITION_CONDITION',transition)
with tempfile.TemporaryDirectory(prefix='futoon-chat-tools-') as folder:
    p=Path(folder); (p/'org/telegram/messenger').mkdir(parents=True)
    (p/'org/telegram/messenger/BuildVars.java').write_text('package org.telegram.messenger; public class BuildVars { public static boolean FUTOON_LITE=true; }')
    (p/'ChatSendStateTest.java').write_text(test)
    subprocess.run(['javac','ChatSendStateTest.java','org/telegram/messenger/BuildVars.java'],cwd=p,check=True)
    subprocess.run(['java','ChatSendStateTest'],cwd=p,check=True)
    guard=' || isFutoonComposer() && caption == null && (getSendButtonInternal().getVisibility() != VISIBLE || getSendButtonInternal().getAlpha() < 1f)'
    assert guard in transition
    (p/'ChatSendStateTest.java').write_text(test.replace(guard,''))
    subprocess.run(['javac','ChatSendStateTest.java'],cwd=p,check=True)
    failed=subprocess.run(['java','ChatSendStateTest'],cwd=p,capture_output=True,text=True)
    assert failed.returncode != 0 and 'Empty Lite composer' in failed.stderr
    print('PASS: removing the fix reproduces the missing send button')

callback=method('private boolean onSendLongClick(View view)')
assert 'insertFutoonDraft(text, targetDialog, targetTopic)' in callback and 'sendMessage(' not in callback
assert 'draft.replace(' in method('public boolean insertFutoonDraft(')
assert 'targetDialog' in callback and 'targetTopic' in callback and 'getClientUserId() != owner' in callback
profile=(base/'ui/ProfileActivity.java').read_text()
assert 'detailCell.setTextAndValue(Long.toString(userId), "Telegram ID", false)' in profile
assert 'if (userId > 0) futoonTelegramIdRow = rowCount++;' in profile
assert 'put(++pointer, futoonTelegramIdRow, sparseIntArray)' in profile
for name in ['ui/Cells/DialogCell.java','ui/Components/ChatAvatarContainer.java']:
    assert 'FutoonChatPreferences.decorate(' in (base/name).read_text()
assert '"Futoon chat"' in (base/'ui/DialogsActivity.java').read_text()
assert '"FutoonPay 设置"' in (base/'ui/SettingsActivity.java').read_text()
assert 'android:label="短信"' in (root/'TMessagesProj/config/debug/AndroidManifest_SDK23.xml').read_text()
print('PASS: read-only profile TGID, both tag surfaces, draft-only insertion, settings and requested branding')

# Execute the actual Telegram bridge with the same listener replacement contract
# as BaseFragment.showDialog. No Android device or message send is involved.
stubs = {
 'com/futoonpay/admin/ChannelChatToolsHostBridge.java': 'package com.futoonpay.admin; public interface ChannelChatToolsHostBridge extends ChatToolsHostBridge {String channelAccountScope();String channelBackendAddress();}',
 'org/telegram/messenger/futoon/FutoonStore.java': 'package org.telegram.messenger.futoon; public class FutoonStore {public FutoonStore(int a){}public String address(){return "https://fixture.example/admin/";}}',
 'android/app/Dialog.java': '''package android.app; public class Dialog { public interface OnDismissListener { void onDismiss(Dialog dialog); } public OnDismissListener listener; public int dismissCalls; public void setOnDismissListener(OnDismissListener listener){this.listener=listener;} public void dismiss(){dismissCalls++;if(listener!=null)listener.onDismiss(this);} }''',
 'android/view/View.java': '''package android.view; public class View {public static final int GONE=8; public Object getWindowToken(){return this;} }''',
 'android/content/Context.java': '''package android.content; public class Context {}''',
 'android/content/Intent.java': '''package android.content; public class Intent {}''',
 'com/futoonpay/admin/ChatToolsHostBridge.java': '''package com.futoonpay.admin; public interface ChatToolsHostBridge extends ChatToolsNavigationBridge {void settingsTitle(String title);void pickEntryImage();void pickAutomationGroup();String selectedGroup();String automationLog();void resetEntryPosition();}''',
 'org/telegram/ui/FutoonSettingsHostActions.java': '''package org.telegram.ui; public class FutoonSettingsHostActions {public FutoonSettingsHostActions(Object parent,int account,Runnable refresh){}public void chooseEntryImage(){}public void chooseGroup(){}public String selectedGroup(){return "";}public String automationLog(){return "";}public void resetEntryPosition(){}public void activityResult(int requestCode,int resultCode,android.content.Intent data){}}''',
 'com/futoonpay/admin/ChatToolsBridge.java': '''package com.futoonpay.admin; public interface ChatToolsBridge { String load(); void save(String json) throws Exception; void selectReply(String text); void manage(); void originalSendOptions(); void close(); }''',
 'com/futoonpay/admin/ChatToolsNavigationBridge.java': '''package com.futoonpay.admin; public interface ChatToolsNavigationBridge extends ChatToolsBridge {String accountLabel();String selectedTab();String selectedCategory();void rememberSelection(String tab,String category);void manageAt(String tab,String category);}''',
 'org/telegram/ui/FutoonThemeBridge.java': '''package org.telegram.ui; public class FutoonThemeBridge {public static void bind(android.view.View view,java.util.function.Consumer<String> receiver){receiver.accept("{}");}public static void bind(android.view.View view,java.util.function.Consumer<String> receiver,int account){receiver.accept("{}");}public static void refresh(android.view.View view){}}''',
 'com/futoonpay/admin/ChatToolsView.java': '''package com.futoonpay.admin; public class ChatToolsView extends android.view.View { public static ChatToolsView last; public final ChatToolsBridge bridge; public final String initialPage; public boolean closed,shown; public int height; public ChatToolsView(Object context,ChatToolsBridge bridge,boolean picker){this(context,bridge,picker,"tags");} public ChatToolsView(Object context,ChatToolsBridge bridge,boolean picker,String initialPage){this.bridge=bridge;this.initialPage=initialPage;last=this;} public void reloadSettings(){} public void setThemeColors(String json){} public void setPanelHeight(int height){this.height=height;} public void onShown(){shown=true;} public void onHidden(){shown=false;} public boolean handleBack(){bridge.close();return true;} public void close(){closed=true;} }''',
 'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; import java.util.*; public class AndroidUtilities { public static class Point {public int y=1000;} public static Point displaySize=new Point(); static List<Runnable> queue=new ArrayList<>(); public static int dp(int value){return value;} public static int hiddenKeyboards; public static void hideKeyboard(android.view.View view){hiddenKeyboards++;} public static void runOnUIThread(Runnable action,long delay){queue.add(action);}public static void runOnUIThread(Runnable action){queue.add(action);} public static void flush(){List<Runnable> copy=new ArrayList<>(queue);queue.clear();for(Runnable r:copy)r.run();} }''',
 'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig {public static long owner=101;public static UserConfig getInstance(int account){return new UserConfig();}public long getClientUserId(){return owner;}}''',
 'org/telegram/messenger/futoon/FutoonChatPreferences.java': '''package org.telegram.messenger.futoon; public class FutoonChatPreferences {public static FutoonChatPreferences get(int account){return new FutoonChatPreferences();}public String raw(){return "";}public void save(int account,String json){} }''',
 'org/telegram/ui/ActionBar/BottomSheet.java': '''package org.telegram.ui.ActionBar; import android.app.Dialog; public class BottomSheet extends Dialog {public static BottomSheet last;public BottomSheet(){last=this;}public static class Builder {public Builder(Object context){}public Builder setCustomView(Object view){return this;}public BottomSheet create(){return new BottomSheet();}} }''',
 'org/telegram/messenger/R.java': '''package org.telegram.messenger; public class R {public static class drawable {public static final int ic_ab_back=1;}}''',
 'org/telegram/ui/ActionBar/ActionBar.java': '''package org.telegram.ui.ActionBar; public class ActionBar {public static ActionBar last;public boolean added=true;public int visibility;public String title;public ActionBar(){last=this;}public void setBackButtonImage(int id){}public void setTitle(String title){this.title=title;}public void setTitleSize(int size){}public void setAllowOverlayTitle(boolean allow){}public void setAddToContainer(boolean add){added=add;}public void setVisibility(int visible){visibility=visible;}public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener){}public static class ActionBarMenuOnItemClick {public void onItemClick(int id){}}}''',
 'org/telegram/ui/ActionBar/BaseFragment.java': '''package org.telegram.ui.ActionBar; import android.content.Context;import android.view.View;public class BaseFragment {protected int currentAccount;protected View fragmentView;protected ActionBar actionBar=new ActionBar();public int finished;public View createView(Context context){return null;}public void onActivityResultFragment(int requestCode,int resultCode,android.content.Intent data){}public void onResume(){}public void onPause(){}public void onFragmentDestroy(){}public boolean onBackPressed(boolean invoked){return true;}public void finishFragment(){finished++;}}''',
 'org/telegram/ui/ChatActivity.java': '''package org.telegram.ui; import android.app.Dialog;import android.view.View; public class ChatActivity {public boolean alive=true,reject,paused;public long dialog=100,topic=2;public long getDialogId(){return dialog;}public long getTopicId(){return topic;}public boolean isPaused(){return paused;}public boolean isLastFragment(){return true;}public FutoonChatSettingsActivity opened;public Object getParentActivity(){return alive?this:null;}public View getFragmentView(){return alive?new View():null;}public boolean presentFragment(FutoonChatSettingsActivity fragment){opened=fragment;fragment.createView(new android.content.Context());return true;}public Dialog showDialog(Dialog dialog,Dialog.OnDismissListener listener){if(reject)return null;dialog.setOnDismissListener(d->{if(listener!=null)listener.onDismiss(d);});return dialog;} }''',
 'org/telegram/ui/QuickReplyBridgeTest.java': '''package org.telegram.ui;
import com.futoonpay.admin.ChatToolsView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
public class QuickReplyBridgeTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  ChatActivity host=new ChatActivity();StringBuilder draft=new StringBuilder("已有草稿：");int[] options={0};
  check(FutoonQuickReplies.show(host,0,draft::append,()->options[0]++),"Picker should open");
  check(ChatToolsView.last.shown&&ChatToolsView.last.height>0,"View needs lifecycle and bounded height");
  ChatToolsView.last.bridge.selectReply("请提供凭证");
  check(draft.toString().equals("已有草稿："),"Draft insertion waits for native sheet dismissal");
  AndroidUtilities.flush();check(draft.toString().equals("已有草稿：请提供凭证"),"Selected text must reach editable draft");
  check(ChatToolsView.last.closed&&options[0]==0,"Selecting a reply disposes its view and never invokes send options");
  BottomSheet.last.dismiss();AndroidUtilities.flush();check(draft.toString().equals("已有草稿：请提供凭证"),"Repeated dismissal must not insert twice");
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.selectReply("旧账号");UserConfig.owner=202;AndroidUtilities.flush();
  check(draft.toString().equals("已有草稿：请提供凭证"),"Account switch must invalidate deferred insertion");UserConfig.owner=101;
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.selectReply("旧页面");host.alive=false;AndroidUtilities.flush();
  check(draft.toString().equals("已有草稿：请提供凭证"),"Detached chat must reject deferred insertion");host.alive=true;
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);((com.futoonpay.admin.ChatToolsNavigationBridge)ChatToolsView.last.bridge).manageAt("tab-A","cat-B");AndroidUtilities.flush();check(host.opened!=null&&ChatToolsView.last.initialPage.equals("replies"),"Manage opens the quick replies settings tab");
  check(host.opened.selectedTab().equals("tab-A")&&host.opened.selectedCategory().equals("cat-B"),"Manage retains the selected category");
  host.opened.onFragmentDestroy();AndroidUtilities.flush();check(((com.futoonpay.admin.ChatToolsNavigationBridge)ChatToolsView.last.bridge).selectedCategory().equals("cat-B"),"Returning from management reopens the selected category");
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.selectReply("wrong dialog");host.dialog++;AndroidUtilities.flush();check(draft.toString().equals("已有草稿：请提供凭证"),"Dialog changes reject insertion");
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.selectReply("wrong topic");host.topic++;AndroidUtilities.flush();check(draft.toString().equals("已有草稿：请提供凭证"),"Topic changes reject insertion");
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.selectReply("x".repeat(4097));AndroidUtilities.flush();check(draft.toString().equals("已有草稿：请提供凭证"),"Overlong stored replies are never inserted or truncated");
  FutoonQuickReplies.show(host,0,draft::append,()->options[0]++);ChatToolsView.last.bridge.originalSendOptions();AndroidUtilities.flush();check(options[0]==1,"Original send options remain reachable");
  host.reject=true;check(!FutoonQuickReplies.show(host,0,draft::append,()->options[0]++),"Failed presentation is reported");check(ChatToolsView.last.closed,"Failed presentation must dispose the view");check(BottomSheet.last.dismissCalls>0,"Failed presentation must also dismiss a partially shown window");check(AndroidUtilities.hiddenKeyboards>0,"Picker must hide the keyboard before opening");
  FutoonChatSettingsActivity settings=new FutoonChatSettingsActivity(0);settings.createView(new android.content.Context());ChatToolsView view=ChatToolsView.last;
  check(org.telegram.ui.ActionBar.ActionBar.last.added&&org.telegram.ui.ActionBar.ActionBar.last.visibility!=8,"Settings must keep the real Telegram ActionBar visible and attached");
  ((com.futoonpay.admin.ChatToolsHostBridge)view.bridge).settingsTitle("外观");check(org.telegram.ui.ActionBar.ActionBar.last.title.equals("外观"),"Child settings titles must update the native header");
  settings.onResume();check(view.shown,"Settings resumes its native view");
  check(!settings.onBackPressed(false)&&settings.finished==0,"Predictive back must not close settings during the query phase");
  check(!settings.onBackPressed(true)&&settings.finished==1,"Invoked back must route through the native view");
  settings.onPause();check(!view.shown,"Settings pauses its native view");settings.onFragmentDestroy();check(view.closed,"Destroying settings must dispose the view");
  System.out.println("PASS: actual quick-reply bridge, native dismiss contract, editable draft delivery, single insertion, account/lifecycle guards, settings and original options");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='futoon-quick-reply-bridge-') as folder:
    p=Path(folder)
    for name,content in stubs.items():
        target=p/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content)
    for name in ['FutoonQuickReplies.java','FutoonChatSettingsActivity.java']:
        target=p/'org/telegram/ui'/name;target.write_text((base/'ui'/name).read_text())
    subprocess.run(['javac',*[str(f.relative_to(p)) for f in p.rglob('*.java')]],cwd=p,check=True)
    subprocess.run(['java','org.telegram.ui.QuickReplyBridgeTest'],cwd=p,check=True)

# Execute the real host wiring and caption/attachment routing predicates together.
# A visible send button must never leave the conversation without a media entry.
host = (base/'ui/ChatActivity.java').read_text()
hook = host.index('chatActivityEnterView.setSideButtonsForAttach(sideControlsButtonsLayout);')
gate_line = host[:hook].rstrip().splitlines()[-1].strip()
gate = re.fullmatch(r'if \((.*)\) \{', gate_line).group(1)
caption_line = next(line.strip() for line in composer.splitlines() if line.strip().startswith('boolean captionNearAttach ='))
caption = caption_line.split('=', 1)[1].strip().removesuffix(';')
media_test = '''
import org.telegram.messenger.BuildVars;
public class MediaEntryTest {
 static final int MODE_SCHEDULED=1, DEFAULT_HEIGHT=44;
 static class TextUtils {static boolean isEmpty(String value){return value==null||value.isEmpty();}}
 static class LocaleController {static boolean isRTL;}
 static class Editor {String getCaption(){return null;}boolean isNearRightCaption(int width){return false;}}
 static class Composer {
  Object parentFragment=new Object();boolean isStories,isLiveComment;
  CharSequence message="";Editor messageEditText=new Editor();
  int dp(int value){return value;}
  ACTUAL_COMPOSER
  boolean mediaOutside(){return ACTUAL_CAPTION;}
 }
 static boolean wired(int chatMode,boolean isComments){return ACTUAL_GATE;}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Composer c=new Composer();BuildVars.FUTOON_LITE=true;
  check(wired(0,false),"Lite media button must be wired in normal private/group/topic chats");
  check(c.mediaOutside(),"Empty composer must expose the media entry alongside send");
  c.message="订单 123";
  check(c.mediaOutside(),"Media entry must remain visible while typing");
  c.message="付款凭证\\n第二行";LocaleController.isRTL=true;
  check(c.mediaOutside(),"Multiline/RTL input must retain the media entry");
  BuildVars.FUTOON_LITE=false;LocaleController.isRTL=false;
  check(!wired(0,false)&&wired(MODE_SCHEDULED,false)&&wired(0,true),"Original non-Lite routing must remain unchanged");
  check(!c.mediaOutside(),"Normal Telegram caption routing must remain unchanged");
  BuildVars.FUTOON_LITE=true;c.isStories=true;
  check(!c.mediaOutside(),"Stories must retain their original composer behavior");
  System.out.println("PASS: media entry wired in normal chats, retained for empty/typed/multiline/RTL input, native routes preserved");
 }
}
'''.replace('ACTUAL_COMPOSER', method('private boolean isFutoonComposer()')).replace('ACTUAL_CAPTION', caption).replace('ACTUAL_GATE', gate)
with tempfile.TemporaryDirectory(prefix='futoon-media-entry-') as folder:
    p=Path(folder);(p/'org/telegram/messenger').mkdir(parents=True)
    (p/'org/telegram/messenger/BuildVars.java').write_text('package org.telegram.messenger; public class BuildVars {public static boolean FUTOON_LITE=true;}')
    target=p/'MediaEntryTest.java'
    def run_media(source):
        target.write_text(source)
        subprocess.run(['javac','MediaEntryTest.java','org/telegram/messenger/BuildVars.java'],cwd=p,check=True)
        return subprocess.run(['java','MediaEntryTest'],cwd=p,capture_output=True,text=True)
    current=run_media(media_test);assert current.returncode==0,current.stderr
    print(current.stdout.strip())
    missing_wire=run_media(media_test.replace(gate, gate.replace('BuildVars.FUTOON_LITE || ', '')))
    assert missing_wire.returncode!=0 and 'Lite media button must be wired' in missing_wire.stderr
    old_caption=caption.replace('isFutoonComposer() ||', 'isFutoonComposer() && message.length() == 0 ||', 1)
    missing_typed=run_media(media_test.replace(caption, old_caption))
    assert missing_typed.returncode!=0 and 'Media entry must remain visible while typing' in missing_typed.stderr
    print('PASS: old missing-media wiring and typed-input routing both reproduce the reported regression')
