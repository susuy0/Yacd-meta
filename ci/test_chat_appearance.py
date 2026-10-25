#!/usr/bin/env python3
"""Audit real host integration; Android rendering/touch regressions run in native-admin."""
from pathlib import Path
import re,sys
repo=Path(__file__).resolve().parents[1];host=Path(sys.argv[1]);src=host/'TMessagesProj/src/main/java/org/telegram/ui'
policy=(repo/'FutoonAdmin/src/main/java/com/futoonpay/admin/appearance/ChatLook.java').read_text();theme=(src/'ActionBar/Theme.java').read_text();adapter=(src/'FutoonChatAppearance.java').read_text()
keys=set(re.findall(r'public static (?:final )?int (key_\w+)\s*=',theme));mapped=dict(re.findall(r'put\("(key_\w+)", Theme\.(key_\w+)\)',adapter))
assert len(mapped)>=100
assert all(k==v and k in keys for k,v in mapped.items())
assert set(re.findall(r'case "(key_\w+)"',policy))<=keys
assert 'java.lang.reflect' not in adapter
assert 'if (currentTheme != null)' in theme and 'FutoonChatAppearance.color(key, currentTheme.isDark())' in theme
composer=(src/'Components/ChatActivityEnterView.java').read_text();a=composer.index('    public void drawBackground(');b=composer.index('\n    public float getVisualHeight()',a)
assert 'setBackground(' not in composer[a:b], 'Draw must not allocate or relayout the composer'
assert 'FutoonQuickReplies' in composer and 'insertFutoon' in composer
main=(src/'MainTabsActivity.java').read_text();assert 'setOnLongClickListener(this::openAccountSelector)' in main and 'setOnLongClickListener(this::openFoldersSelector)' in main
assert 'POSITION_CONTACTS = -1' in main and 'TABS_COUNT = 3' in main
assert 'setChatAppearance("chat")' in main
view=(src/'DialogsActivity.java').read_text();assert 'hasChatQuickFilters()' in view and '!onlySelect && folderId == 0 && communityId == 0' in view
assert 'dialog.unread_count > 0 || dialog.unread_mark' in view and 'chat.megagroup' in view
assert 'getChatSearchHeight()' in view and 'R.string.FilterNameUnread' in view
assert 'new ArrayList<>()' in view and 'return filtered;' in view
assert 'FutoonPanel' in (src/'ChatActivity.java').read_text()
assert 'ChatLook.bubblePath' in (src/'ActionBar/MessageDrawable.java').read_text()
assert 'getImageReceiver' in (src/'ProfileActivity.java').read_text() and 'Telegram ID' in (src/'ProfileActivity.java').read_text()
print(f'PASS: {len(mapped)} native theme keys, cached palette, native filters, composer allocation guard, existing callbacks, business and permission boundaries')

components=(repo/'FutoonAdmin/src/main/java/com/futoonpay/admin/ui/ModernComponents.kt').read_text()
panel=(repo/'FutoonAdmin/src/main/java/com/futoonpay/admin/NativePanelView.kt').read_text()
assert 'ChatNavigationBar(tabs.sortedBy { options.panel.tabOrder.indexOf(it.id) }, navigator.tab, navigator::select)' in panel
assert 'Role.Tab' in components and 'selectableGroup()' in components
assert 'MainTab.entries.find { it.name == id }?.let(onSelect)' in components
assert 'ChatFilterTabs(options, selected, onSelect)' in components
assert 'ChatFieldColors()' in components
print('PASS: shared native tab routing, configured order, accessibility selection, scrollable filters and field styles')

# Execute the production send-circle geometry. A static color audit alone cannot
# catch the old 38dp circle or a circle clipped by a 48dp composer control.
import subprocess, tempfile
def method(signature):
    start = composer.index(signature)
    opening = composer.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (composer[end] == '{') - (composer[end] == '}')
        end += 1
    return composer[start:end]

geometry = method('private void checkBackgroundRect()')
native = '''
public class SendCircleGeometryTest {
 static final int DEFAULT_HEIGHT=48;
 static class Rect { float l,t,r,b; void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;} }
 static class Price { float width; float getCurrentWidth(){return width;} }
 final Rect backgroundRect=new Rect(); final Price priceText=new Price();
 float density=1, sameWidthFactor=0; int measuredWidth=100, measuredHeight=48;
 float dpf2(float value){return value*density;}
 float lerp(float a,float b,float f){return a+(b-a)*f;}
 int getMeasuredWidth(){return measuredWidth;} int getMeasuredHeight(){return measuredHeight;}
 GEOMETRY
 static void check(boolean value){if(!value)throw new AssertionError("Send circle clipped or wrong size");}
 public static void main(String[] args){
  for(float density:new float[]{1,2,3}) for(int controlHeight:new int[]{48,96}) {
   SendCircleGeometryTest v=new SendCircleGeometryTest();v.density=density;
   v.measuredWidth=(int)(100*density);v.measuredHeight=(int)(controlHeight*density);
   for(float price:new float[]{0,60}) for(float factor:new float[]{0,.5f,1}) {
    v.priceText.width=price*density;v.sameWidthFactor=factor;v.checkBackgroundRect();
    Rect r=v.backgroundRect;check(r.l>=0&&r.t>=0&&r.r<=v.measuredWidth&&r.b<=v.measuredHeight);
    check(Math.abs(r.b-r.t-48*density)<.01f);check(r.r-r.l>=48*density);
    if(price==0||factor==1)check(Math.abs(r.r-r.l-48*density)<.01f);
   }
  }
  System.out.println("PASS: production send-circle geometry at 1x/2x/3x, multiline and paid-message expansion");
 }
}
'''.replace('GEOMETRY', geometry)
with tempfile.TemporaryDirectory() as directory:
    java = Path(directory)/'SendCircleGeometryTest.java'
    java.write_text(native)
    subprocess.run(['javac', '-d', directory, str(java)], check=True)
    subprocess.run(['java', '-cp', directory, 'SendCircleGeometryTest'], check=True)
