#!/usr/bin/env python3
"""Execute the production send-state conditions; audit the native chat-tool entry points."""
from pathlib import Path
import subprocess, sys, tempfile

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
assert 'draft.replace(' in callback and 'sendMessage(' not in callback
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
