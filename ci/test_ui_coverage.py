#!/usr/bin/env python3
"""Gate every restyled UI family and preserve the original business click contracts."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,tempfile
repo=Path(__file__).resolve().parents[1];host=Path(sys.argv[1]);ui=host/'TMessagesProj/src/main/java/org/telegram/ui'
def balanced(source,start):
    i=source.index('{',start);level=0;state='code';j=i
    while j<len(source):
        c=source[j];n=source[j+1:j+2]
        if state=='line':
            if c=='\n':state='code'
        elif state=='block':
            if c=='*'and n=='/':state='code';j+=1
        elif state in ['string','char']:
            if c=='\\':j+=1
            elif c==('"'if state=='string'else"'"):state='code'
        elif c=='/'and n=='/':state='line';j+=1
        elif c=='/'and n=='*':state='block';j+=1
        elif c=='"':state='string'
        elif c=="'":state='char'
        elif c=='{':level+=1
        elif c=='}':
            level-=1
            if level==0:return source[i:j+1]
        j+=1
    raise ValueError('Unbalanced block')
def callbacks(source):
    values=[]
    for m in re.finditer(r'\bonClick\s*=\s*',source):
        tail=source[m.end():]
        value=balanced(source,m.end())if tail.startswith('{')else re.match(r'[\w.:?]+',tail).group()
        values.append(hashlib.sha256(value.encode()).hexdigest())
    return values
manifest=json.loads((repo/'reference/ui-callbacks-backend-2026-10-30.json').read_text())
for name,expected in manifest.items():assert callbacks((repo/name).read_text())==expected,'Business callback changed: '+name
rules={
'gallery':{'Components/ChatAttachAlertPhotoLayout.java':['ChatChrome.galleryColumns','ChatChrome.galleryTile','int paddingTop = insetsTop + listAdditionalH + dp(8)'],'PhotoPickerActivity.java':['ChatChrome.galleryColumns','ChatChrome.galleryTile'],'Cells/PhotoPickerAlbumsCell.java':['imageView.setRoundRadius(AndroidUtilities.dp(12))']},
'profile and secondary settings':{'Cells/TextDetailSettingsCell.java':['extends com.futoonpay.admin.appearance.ChatPreferenceRow','bindChatRow(textView, valueTextView, imageView, LocaleController.isRTL)'],'Cells/TextDetailCell.java':['ChatChrome.rowHeight(getContext(), true)'],'Cells/TextCheckCell.java':['ChatChrome.rowHeight','ChatChrome.stackLabels'],'Cells/TextRadioCell.java':['ChatChrome.rowHeight','ChatChrome.stackLabels'],'Components/SharedMediaLayout.java':['setBlurredBackground(null)'],'Components/DialogsActivityTopPanelLayout.java':['backgroundDrawable = null;']},
'media and bot messages':{'Cells/ChatMessageCell.java':['ChatChrome.MEDIA_RADIUS','replyImageReceiver.setRoundRadius(dp(6))','botButton.height = dp(48)'],'Cells/BotButton.java':['Arrays.fill(radii, dp(12))'],'bots/BotKeyboardView.java':['final int br = dp(12)','!isFullSize ? 48']},
'menus and selection':{'ActionBar/ActionBarMenuSubItem.java':['FutoonChatAppearance.menuIcon','ChatChrome.rowHeight'],'ActionBar/AlertDialog.java':['ChatChrome.DIALOG_RADIUS','blurredBackground = false'],'ActionBar/BottomSheet.java':['ChatChrome.SHEET_RADIUS','public int openDuration = 220'],'FutoonChatAppearance.java':['key_actionBarActionModeDefaultIcon']},
'login/search/group/topics':{'LoginActivity.java':['countryOutlineView.setChatUnderlineStyle(true)','phoneOutlineView.setChatUnderlineStyle(true)'],'ActionBar/ActionBarMenuItem.java':['ChatChrome.field(getContext()','COMPLEX_UNIT_SP, 16'],'Cells/ProfileSearchCell.java':['ChatChrome.rowHeight(getContext(), true)'],'Cells/ManageChatUserCell.java':['ChatChrome.rowHeight(getContext(), true)'],'TopicCreateFragment.java':['COMPLEX_UNIT_SP'],'CodeFieldContainer.java':['canvas.drawLine(AndroidUtilities.rectTmp.left'],'FutoonChatAppearance.java':['key_chat_emojiPanelBackground','key_player_background']},
'motion':{'ActionBar/ActionBarLayout.java':['ChatChrome.animationsEnabled(getContext()) ? 220 : 0'],'ActionBar/ActionBarPopupWindow.java':['ChatChrome.animationsEnabled(content.getContext()) ? 150 : 0'],'Components/ChatAttachAlert.java':['R.style.ChatMenuAnimation']}
}
for family,files in rules.items():
    for name,needles in files.items():
        text=(ui/name).read_text()
        for needle in needles:assert needle in text,(family,name,needle)
    print('PASS:',family)
actions=(repo/'FutoonAdmin/src/main/java/com/futoonpay/admin/ui/ChatActions.kt').read_text();assert actions.count('modifier.heightIn(min=48.dp)')==3
assert len(manifest)>=15
# Execute the production null-safe blur detach method. This was a real startup crash boundary.
source=(ui/'Components/ScrollSlidingTextTabStrip.java').read_text();signature='public void setBlurredBackground(BlurredBackgroundDrawable background)';method=signature+' '+balanced(source,source.index(signature))
program='''public class NativeBlurDetachTest {
 static class BlurredBackgroundDrawable { Object callback; void setCallback(Object c){callback=c;} }
 BlurredBackgroundDrawable backgroundDrawable; void invalidate(){}
 METHOD
 public static void main(String[] args){NativeBlurDetachTest v=new NativeBlurDetachTest();v.setBlurredBackground(null);BlurredBackgroundDrawable first=new BlurredBackgroundDrawable();v.setBlurredBackground(first);if(first.callback!=v)throw new AssertionError();v.setBlurredBackground(null);if(first.callback!=null)throw new AssertionError();v.setBlurredBackground(first);}
}'''.replace('METHOD',method)
with tempfile.TemporaryDirectory()as folder:
    p=Path(folder);java=p/'NativeBlurDetachTest.java';java.write_text(program);subprocess.run(['javac',str(java)],check=True);subprocess.run(['java','-cp',folder,'NativeBlurDetachTest'],check=True)
    broken=program.replace('if (backgroundDrawable != null) backgroundDrawable.setCallback(this);','backgroundDrawable.setCallback(this);');java.write_text(broken);subprocess.run(['javac',str(java)],check=True);negative=subprocess.run(['java','-cp',folder,'NativeBlurDetachTest'],capture_output=True,text=True);assert negative.returncode!=0 and 'NullPointerException'in negative.stderr
print('PASS: actual native null-background transition, callback detach, and crashing negative control')
print('PASS:',sum(len(x)for x in manifest.values()),'reviewed business click expressions across',len(manifest),'files; common native and Compose action contracts')
# Execute native inline-keyboard placement formulas, not a second implementation of the layout.
message=(ui/'Cells/ChatMessageCell.java').read_text()
height=re.search(r'substractBackgroundHeight = keyboardHeight = (.*);',message).group(1)
pos=re.search(r'botButton.y = (.*);',message).group(1)
button=re.search(r'botButton.height = (.*);',message).group(1)
program='''public class InlineGeometry {
 static float density;static int dp(float value){return (int)Math.ceil(value*density);}
 public static void main(String[] args){for(float d:new float[]{1,2,3})for(int rows=1;rows<=20;rows++)for(boolean grouped:new boolean[]{false,true}){
  density=d;int separatorHeight=dp(8),separators=grouped?rows-1:0;int keyboardHeight=HEIGHT;float previous=0;
  for(int row=0;row<rows;row++){int separatorsHeight=grouped?row*separatorHeight:0;float top=POSITION;int buttonHeight=BUTTON;
   if(buttonHeight<48*d||top<previous||top+buttonHeight>keyboardHeight)throw new AssertionError("Inline keyboard overlaps/clips");previous=top+buttonHeight;}
 }}
}'''.replace('HEIGHT',height).replace('POSITION',pos).replace('BUTTON',button)
with tempfile.TemporaryDirectory()as folder:
    file=Path(folder)/'InlineGeometry.java';file.write_text(program);subprocess.run(['javac',str(file)],check=True);subprocess.run(['java','-cp',folder,'InlineGeometry'],check=True)
print('PASS: production inline-bot keyboard placement at 1x/2x/3x with 1..20 rows and separators')

keyboard=(ui/'bots/BotKeyboardView.java').read_text()
formula=re.search(r'buttonHeight = (.*);',keyboard).group(1)
assert 'private final ScrollView scrollView;' in keyboard
program="""import java.util.*; public class ReplyKeyboardGeometry {
 static final int BORDER_MARGIN=8,MIDDLE_MARGIN=4;static int panelHeight;static boolean isFullSize;
 static class AndroidUtilities {static float density;}
 static class Keyboard {ArrayList<Object> rows=new ArrayList<>();}static Keyboard botButtons;
 static int dp(float value){return (int)Math.ceil(value*AndroidUtilities.density);}
 public static void main(String[] args){for(float density:new float[]{1,1.5f,2,3})for(int rows=1;rows<=30;rows++)for(int height:new int[]{160,320,640})for(boolean full:new boolean[]{false,true}){
  AndroidUtilities.density=density;panelHeight=dp(height);isFullSize=full;botButtons=new Keyboard();for(int i=0;i<rows;i++)botButtons.rows.add(new Object());int actual=FORMULA;
  if(actual<48||!full&&actual!=48)throw new AssertionError("Reply keyboard touch target collapsed");
 }}
}""".replace('FORMULA',formula)
with tempfile.TemporaryDirectory()as folder:
    p=Path(folder)/'ReplyKeyboardGeometry.java';p.write_text(program);subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',folder,'ReplyKeyboardGeometry'],check=True)
print('PASS: production reply-keyboard row calculation preserves 48dp targets at four densities and 1..30 rows; overflow uses its existing ScrollView')
