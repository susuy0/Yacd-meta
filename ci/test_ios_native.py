#!/usr/bin/env python3
"""Exercise the actual native collapsing header and check PDF integration boundaries."""
from pathlib import Path
import re,sys,subprocess,tempfile
repo=Path(__file__).resolve().parents[1];host=Path(sys.argv[1]);src=host/'TMessagesProj/src/main/java/org/telegram/ui'
settings=(src/'SettingsActivity.java').read_text();signature='private void updateActionBarVisible(boolean force, boolean animated)';a=settings.index(signature);start=settings.index('{',a);end=start+1;depth=1
while depth:
 depth+=(settings[end]=='{')-(settings[end]=='}');end+=1
method=settings[a:end]
program='''
public class IosNativeHeaderTest {
 static class View {float alpha=1,y;int bottom=80; void setAlpha(float f){alpha=f;} float getY(){return y;} int getBottom(){return bottom;} }
 static class RecyclerView {static class ViewHolder {View itemView=new View();}}
 static class ListView {RecyclerView.ViewHolder first;int children; RecyclerView.ViewHolder findViewHolderForAdapterPosition(int p){return first;}int getChildCount(){return children;}}
 static class Bar extends View {View titles=new View();View getTitlesContainer(){return titles;}}
 static class Search {boolean showing;boolean isSearchFieldVisible2(){return showing;}}
 static class Animator {void cancel(){}}
 Bar actionBar=new Bar();ListView listView=new ListView();Search searchItem=new Search();View actionBarBackground=new View();boolean actionBarVisible;Animator actionBarVisibleAnimator;
 int dp(int v){return v;}
 METHOD
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){IosNativeHeaderTest t=new IosNativeHeaderTest();
 t.updateActionBarVisible(false,false);check(!t.actionBarVisible,"Initial empty layout must not duplicate large title");
 t.listView.first=new RecyclerView.ViewHolder();t.listView.children=1;t.listView.first.itemView.y=100;t.updateActionBarVisible(false,false);check(!t.actionBarVisible&&t.actionBar.titles.alpha==0,"Visible large title must own the title");
 t.listView.first.itemView.y=20;t.updateActionBarVisible(false,false);check(t.actionBarVisible&&t.actionBar.titles.alpha==1,"Scrolled title must become a compact header");
 t.listView.first.itemView.y=100;t.searchItem.showing=true;t.updateActionBarVisible(false,false);check(t.actionBarVisible,"Search must have its compact bar");
 t.searchItem.showing=false;t.listView.first=null;t.updateActionBarVisible(false,false);check(t.actionBarVisible,"Recycled large header must not remove the scrolled title");
 t.actionBar=null;t.updateActionBarVisible(false,false);t.actionBar=new Bar();t.actionBar.titles=null;t.updateActionBarVisible(false,false);t.actionBar.titles=new View();t.listView=null;t.updateActionBarVisible(false,false);
 System.out.println("PASS: actual native large/compact header, search, recycle, empty and pre-create lifecycle states");}
}
'''.replace('METHOD',method)
with tempfile.TemporaryDirectory()as tmp:
 p=Path(tmp)/'IosNativeHeaderTest.java'
 def run(code):
  p.write_text(code);subprocess.run(['javac',str(p)],check=True);return subprocess.run(['java','-cp',tmp,'IosNativeHeaderTest'],capture_output=True,text=True)
 current=run(program);assert current.returncode==0,current.stderr;print(current.stdout,end='')
 # Reproduce the previous forced-visible title even though its native inputs are present.
 bad=program.replace('actionBar.getTitlesContainer().setAlpha(actionBarVisible ? 1f : 0f);','actionBarVisible = true; actionBar.getTitlesContainer().setAlpha(1f);')
 failed=run(bad);assert failed.returncode!=0 and 'Initial empty layout' in failed.stderr
 print('PASS: forced-visible original header rejected by the same behavioral test')
bar=(src/'ActionBar/ActionBar.java').read_text();assert 'if (org.telegram.ui.FutoonChatAppearance.ios()) return dp(44);' in bar
chat=(src/'Components/ChatActivityEnterView.java').read_text();dialogs=(src/'DialogsActivity.java').read_text();tabs=(src/'Components/FilterTabsView.java').read_text();profile=(src/'ProfileActivity.java').read_text();bot=(src/'Cells/BotButton.java').read_text()
assert 'iosAttachButton.setOnClickListener' in chat and 'delegate.didPressAttachButton();' in chat
assert 'captionNearAttach && !isFutoonComposer()' in chat and 'isFutoonComposer() ? 44 : 0' in chat
assert 'public void selectPosition(int position)' in tabs and 'delegate.canPerformActions()) scrollToTab' in tabs
assert 'filterTabsView.selectPosition(which)' in dialogs and 'presentFragment(new FiltersSetupActivity())' in dialogs
assert 'dismissed -> fragmentSearchField.selectChatFilter' in dialogs
assert 'newChat.setOnClickListener(v -> floatingButton3.performClick())' in dialogs
assert 'listView.setSections(view ->' in profile and 'Telegram ID' in profile
assert 'bgColor == BotInlineKeyboard.BackgroundColor.NONE ? Theme.getColor(Theme.key_chat_botKeyboardButtonText' in bot
assert 'com.github.QWEA0' not in (repo/'FutoonAdmin/build.gradle.kts').read_text()
print('PASS: inline attachment uses native dispatch; folder locks/transitions and cancellation preserved; new-chat original handler; profile sections and TGID; readable bot label')
subprocess.run([sys.executable,str(repo/'ci/test_settings_population.py'),str(host)],check=True)
subprocess.run([sys.executable,str(repo/'ci/prepare_native_ui_tests.py'),str(host)],check=True)
native_tabs=(src/'Components/glass/GlassTabView.java').read_text()
main_tabs=(src/'MainTabsActivity.java').read_text()
assert 'tabsView.setPadding(dp(8), 0, dp(8), 0);' in main_tabs
assert 'ChatTabGeometry.prepare(imageView, textView' in native_tabs
assert native_tabs.count('ChatTabGeometry.LABEL_SP')==2
