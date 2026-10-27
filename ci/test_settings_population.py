#!/usr/bin/env python3
"""Execute the native settings fill prefix at the adapter's real eager boundary."""
from pathlib import Path
import subprocess, sys, tempfile

host = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
settings = (host / 'SettingsActivity.java').read_text()
adapter = (host / 'Components/UniversalAdapter.java').read_text()
constructor = adapter[adapter.index('    public UniversalAdapter(\n', adapter.index('    public UniversalAdapter(\n') + 1):adapter.index('    public void setApplyBackground')]
assert 'update(false);' in constructor, 'Native constructor boundary changed; refresh the regression'
start = settings.index('    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter)')
prefix = settings[start:settings.index('        accountNumbers.clear();', start)] + '\n    }'
prefix = prefix.replace('com.futoonpay.admin.appearance.', '')
create = settings[settings.index('    public View createView(Context context)'):settings.index('    public void onFragmentDestroy()')]
assert create.index('settingsViewsReady = false;') < create.index('new UniversalRecyclerView(')
assert create.index('settingsViewsReady = true;') > create.index('topView = new ')
assert create.index('settingsViewsReady = true;') < create.index('listView.adapter.update(false);', create.index('settingsViewsReady = true;'))
assert 'if (settingsViewsReady && listView != null) listView.adapter.update(true);' in create
clear = settings[settings.index('    public void clearViews()'):settings.index('    public View createView')]
assert clear.index('if (ignoreClearViews) return;') < clear.index('settingsViewsReady = false;')
assert 'iosSettingsIntro = null;' in clear and 'iosSettingsIntro = null;' in create

program = '''
import java.util.ArrayList;
public class SettingsPopulationTest {
 static class R {static class string {static int Settings;}}
 static class ActionBar {static int getCurrentActionBarHeight(){return 44;}}
 static class FutoonChatAppearance {static boolean dark(){return false;}}
 static class ChatSettingsIntro {ChatSettingsIntro(Object c,String l,String h,Runnable r){}void setDark(boolean d){}static int heightDp(Object c){return 100;}}
 static class ChatSettingsHeader {void setDark(boolean d){}static int heightDp(Object c){return 100;}}
 static class UItem {Object view;UItem(Object v){view=v;}static UItem asCustom(Object v){return new UItem(v);}static UItem asCustomShadow(Object v,int h){return new UItem(v);}static UItem asSpace(int h){return new UItem(null);}}
 static class SearchItem {boolean showing;boolean isSearchFieldVisible2(){return showing;}void toggleSearch(boolean b){showing=b;}}
 static class Search {void fillItems(ArrayList<UItem> i){i.add(new UItem("search"));}}
 class UniversalAdapter {
  final ArrayList<UItem> items=new ArrayList<>();
  UniversalAdapter(){update(false);}
  void update(boolean animated){items.clear();fillItems(items,this);}
 }
 boolean settingsViewsReady;
 ChatSettingsIntro iosSettingsIntro;
 ChatSettingsHeader topView;
 SearchItem searchItem=new SearchItem();Search search=new Search();
 Object getContext(){return this;}String getString(int i){return "设置";}int dp(int i){return i;}
 PREFIX
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){SettingsPopulationTest t=new SettingsPopulationTest();
  UniversalAdapter a=t.new UniversalAdapter();check(a.items.isEmpty(),"Constructor must defer uncreated views");
  t.topView=new ChatSettingsHeader();t.settingsViewsReady=true;a.update(false);check(a.items.size()==2 && a.items.get(1).view==t.topView,"Ready adapter must contain its real card");
  ChatSettingsIntro old=t.iosSettingsIntro;t.settingsViewsReady=false;t.iosSettingsIntro=null;t.topView=null;a.update(false);check(a.items.isEmpty(),"Refresh during clear must be safe");
  UniversalAdapter recreated=t.new UniversalAdapter();t.topView=new ChatSettingsHeader();t.settingsViewsReady=true;recreated.update(false);check(recreated.items.size()==2 && t.iosSettingsIntro!=old,"Recreate must build a fresh intro");
  t.searchItem.showing=true;recreated.update(false);check(recreated.items.size()==2 && recreated.items.get(1).view.equals("search"),"Ready native search must still populate");
  t.settingsViewsReady=false;recreated.update(false);check(recreated.items.isEmpty(),"Destroyed settings must ignore late search refresh");
  System.out.println("PASS: native eager fill, ready card, clear/recreate, search and destroyed refresh");
 }
}
'''.replace('PREFIX', prefix)
with tempfile.TemporaryDirectory() as tmp:
    file = Path(tmp) / 'SettingsPopulationTest.java'
    def run(code):
        file.write_text(code)
        subprocess.run(['javac', str(file)], check=True)
        return subprocess.run(['java', '-cp', tmp, 'SettingsPopulationTest'], capture_output=True, text=True)
    result = run(program)
    assert result.returncode == 0, result.stderr
    print(result.stdout, end='')
    old = run(program.replace('if (!settingsViewsReady) return;', ''))
    assert old.returncode != 0 and 'NullPointerException' in old.stderr, old.stderr
    print('PASS: v0.11.0 constructor null-card crash reproduced by the same test without the guard')
