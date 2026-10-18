#!/usr/bin/env python3
"""Execute the real composer insertion method without an Android or Telegram server."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1])
source = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java").read_text()

def method(declaration):
    start = source.index(declaration)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]

methods = method("private boolean isFutoonComposer()") + "\n" + method("public boolean insertFutoonDraft(")
fixture = r'''
import java.util.*;
public class OpeningDraftFixture {
    static final int VISIBLE=0;
    static class ChatActivity {static final int MODE_QUICK_REPLIES=8;long topic=5;int mode;long getTopicId(){return topic;}int getChatMode(){return mode;}}
    static class UserConfig {static long owner=100;static UserConfig getInstance(int account){return new UserConfig();}long getClientUserId(){return owner;}}
    static class Text implements android.text.Editable {StringBuilder value=new StringBuilder("prefix|suffix");int start=6,end=6;
      public int length(){return value.length();} public android.text.Editable replace(int s,int e,String text){value.replace(s,e,text);return this;}
      public String toString(){return value.toString();}android.text.Editable getText(){return this;}int getSelectionStart(){return start;}int getSelectionEnd(){return end;}
      void setSelection(int s){start=end=s;}void requestFocus(){}
    }
    ChatActivity parentFragment=new ChatActivity();Text messageEditText=new Text();
    int currentAccount,visibility=VISIBLE,keyboard,sends;long dialog_id=42;boolean isStories,isLiveComment,scheduled,detached;
    Object editingMessageObject;List<Runnable> callbacks=new ArrayList<>();
    Object getWindowToken(){return detached?null:this;}int getVisibility(){return visibility;}boolean isInScheduleMode(){return scheduled;}
    void postDelayed(Runnable callback,long ms){callbacks.add(callback);}void openKeyboard(){keyboard++;}void checkSendButton(boolean animated){}
    void sendMessage(){sends++;}void flush(){new ArrayList<>(callbacks).forEach(Runnable::run);callbacks.clear();}
    METHODS
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static OpeningDraftFixture fresh(){UserConfig.owner=100;return new OpeningDraftFixture();}
    public static void main(String[] args){
      OpeningDraftFixture f=fresh();check(f.insertFutoonDraft("OPEN",42,5),"insert");check(f.messageEditText.toString().equals("prefixOPEN|suffix"),"preserve draft");check(f.sends==0,"must not send");f.flush();check(f.keyboard==1,"editable composer focus");
      f=fresh();f.messageEditText.end=7;check(f.insertFutoonDraft("OPEN",42,5),"selection");check(f.messageEditText.toString().equals("prefixOPENsuffix"),"replace only selection");
      f=fresh();check(!f.insertFutoonDraft("OPEN",43,5),"wrong dialog");check(f.messageEditText.toString().equals("prefix|suffix"),"stale draft unchanged");
      check(!fresh().insertFutoonDraft("OPEN",42,6),"wrong topic");
      f=fresh();f.editingMessageObject=new Object();check(!f.insertFutoonDraft("OPEN",42,5),"message edit");
      f=fresh();f.scheduled=true;check(!f.insertFutoonDraft("OPEN",42,5),"scheduled composer");
      f=fresh();f.parentFragment.mode=ChatActivity.MODE_QUICK_REPLIES;check(!f.insertFutoonDraft("OPEN",42,5),"business shortcuts");
      f=fresh();f.detached=true;check(!f.insertFutoonDraft("OPEN",42,5),"detached");
      f=fresh();f.visibility=8;check(!f.insertFutoonDraft("OPEN",42,5),"hidden");
      check(!fresh().insertFutoonDraft("x".repeat(4096),42,5),"oversized draft");check(!fresh().insertFutoonDraft("",42,5),"empty");
      f=fresh();check(f.insertFutoonDraft("OPEN",42,5),"delayed focus");UserConfig.owner=101;f.flush();check(f.keyboard==0,"account changed before keyboard");
      System.out.println("Opening draft composer: 12 behavioral checks passed");
    }
}
'''.replace("METHODS", methods)

with tempfile.TemporaryDirectory() as folder:
    target = Path(folder)
    (target / "org/telegram/messenger").mkdir(parents=True)
    (target / "android/text").mkdir(parents=True)
    (target / "org/telegram/messenger/BuildVars.java").write_text("package org.telegram.messenger; public class BuildVars {public static boolean FUTOON_LITE=true;}")
    (target / "android/text/Editable.java").write_text("package android.text; public interface Editable {int length();Editable replace(int start,int end,String text);}")
    program = target / "OpeningDraftFixture.java"
    for negative in (False, True):
        program.write_text(fixture.replace("dialog_id != targetDialog", "false") if negative else fixture)
        subprocess.run(["javac", "OpeningDraftFixture.java", "android/text/Editable.java", "org/telegram/messenger/BuildVars.java"], cwd=target, check=True)
        result = subprocess.run(["java", "OpeningDraftFixture"], cwd=target, capture_output=True, text=True)
        if negative:
            assert result.returncode != 0 and "wrong dialog" in result.stderr, "Regression fixture failed to reject removal of the conversation guard"
            print("Negative control rejects a missing conversation guard")
        else:
            assert result.returncode == 0, result.stderr
            print(result.stdout.strip())
