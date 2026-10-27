#!/usr/bin/env python3
"""Run the native height selector with differing Activity and View font contexts."""
from pathlib import Path
import subprocess,sys,tempfile
s=(Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text()
start=s.index('private int getChatSearchHeight()');end=s.index('\n',start)
method=s[start:end].replace('com.futoonpay.admin.appearance.ChatInboxHeader','Header').replace('folderId != 0 && org.telegram.messenger.BuildVars.FUTOON_LITE','false')
code='''public class HeaderScaleTest {
 static class Configuration {float fontScale;Configuration(float f){fontScale=f;}}
 static class Resources {Configuration c;Resources(float f){c=new Configuration(f);}Configuration getConfiguration(){return c;}}
 static class Context {Resources r;Context(float f){r=new Resources(f);}Resources getResources(){return r;}}
 static class Header {static int homeHeight(float f){return Math.round(160*f);}}
 static final int SEARCH_FIELD_HEIGHT=48;
 Context activity=new Context(2),fragmentSearchField=new Context(1);boolean home=true;
 Context getContext(){return activity;}boolean hasChatQuickFilters(){return home;}
 METHOD
 static void check(boolean b){if(!b)throw new AssertionError("Height must use the actual rendered search context");}
 public static void main(String[] args){HeaderScaleTest t=new HeaderScaleTest();
 check(t.getChatSearchHeight()==160);t.fragmentSearchField=new Context(1.5f);check(t.getChatSearchHeight()==240);
 t.fragmentSearchField=null;check(t.getChatSearchHeight()==320);t.activity=null;check(t.getChatSearchHeight()==160);
 t.home=false;check(t.getChatSearchHeight()==48);System.out.println("PASS: View/Activity font mismatch, recreation and compact search");}
}'''.replace('METHOD',method)
with tempfile.TemporaryDirectory()as d:
 p=Path(d)/'HeaderScaleTest.java'
 def run(text):
  p.write_text(text);subprocess.run(['javac',str(p)],check=True);return subprocess.run(['java','-cp',d,'HeaderScaleTest'],capture_output=True,text=True)
 r=run(code);assert r.returncode==0,r.stderr;print(r.stdout,end='')
 old=code.replace('fragmentSearchField != null ? fragmentSearchField.getResources().getConfiguration().fontScale : ','')
 r=run(old);assert r.returncode!=0 and 'AssertionError' in r.stderr
 print('PASS: old Activity-only height rejected by the same behavior check')
