#!/usr/bin/env python3
"""Keep a runnable Android fixture made from the real native tab methods.

Only Telegram's icon/animation plumbing is stubbed. The layout, font sizing and
measurement methods below are copied verbatim and checked against the applied host.
"""
from pathlib import Path
import argparse

p = argparse.ArgumentParser()
p.add_argument('host', type=Path)
p.add_argument('--write', action='store_true')
a = p.parse_args()
repo = Path(__file__).resolve().parents[1]
source = (a.host / 'TMessagesProj/src/main/java/org/telegram/ui/Components/glass/GlassTabView.java').read_text()

def method(signature):
    start = source.index(signature)
    opening = source.index('{', start)
    end = opening + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(method(s) for s in [
    'public void setChatAppearance(String glyph)',
    'protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)',
    'public float measureTextWidth(float textSizeDp)',
    'public void setTextSizeDp(float textSizeDp)',
])
fixture = '''// Generated from native GlassTabView by ci/prepare_native_ui_tests.py.
package com.futoonpay.admin.nativehost;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
public class NativeTabFixture extends FrameLayout {
 public final TextView textView;
 public final ChatImage imageView;
 private View backupImageView;
 private String chatAppearanceGlyph;
 private Object tabAnimation;
 private final TextPaint defaultTextPaint;
 private TextPaint scaledTextPaint;
 private boolean selfMeasure;
 private int additionalWidth;
 public NativeTabFixture(Context c, String label) {
  super(c); imageView=new ChatImage(c);textView=new TextView(c);
  textView.setSingleLine(true);textView.setLines(1);textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
  textView.setText(label);textView.setGravity(Gravity.CENTER);
  textView.setTextColor(com.futoonpay.admin.appearance.ChatLook.text(false));
  addView(imageView,new LayoutParams(44,44));addView(textView,new LayoutParams(-1,-2));
  defaultTextPaint=new TextPaint(textView.getPaint());setChatAppearance("chat");
 }
 private int dp(float v){return com.futoonpay.admin.appearance.ChatChrome.dp(getContext(),v);}
 private float measureAttachTabWidth(){return 100;}
 public boolean isTabSelected(){return false;}
 public static class ChatImage extends ImageView {ChatImage(Context c){super(c);}void clearAnimationDrawable(){}}
 static class AndroidUtilities {static android.graphics.Typeface bold(){return android.graphics.Typeface.DEFAULT_BOLD;}}
 static class LayoutHelper {
  static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;
  static LayoutParams createFrame(int w,float h,int gravity){return createFrame(w,h,gravity,0,0,0,0);}
  static LayoutParams createFrame(int w,float h,int gravity,int l,int t,int r,int b){
   float d=android.content.res.Resources.getSystem().getDisplayMetrics().density;
   LayoutParams p=new LayoutParams(w<0?w:Math.round(w*d),h<0?(int)h:Math.round(h*d),gravity);
   p.setMargins(Math.round(l*d),Math.round(t*d),Math.round(r*d),Math.round(b*d));return p;
  }
 }
 METHODS
}
'''.replace('METHODS', methods)
target = repo / 'FutoonAdmin/src/test/java/com/futoonpay/admin/nativehost/NativeTabFixture.java'
if a.write:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(fixture)
else:
    assert target.read_text() == fixture, 'Native tab fixture drifted from the compiled host methods'
print('PASS: actual native tab font/layout/measurement methods match Android fixture')
