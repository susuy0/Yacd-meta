// Generated from native GlassTabView by ci/prepare_native_ui_tests.py.
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
 public void setChatAppearance(String glyph) {
        chatAppearanceGlyph = glyph;
        tabAnimation = null;
        imageView.clearAnimationDrawable();
        imageView.setVisibility(VISIBLE);
        if (backupImageView != null) backupImageView.setVisibility(GONE);
        imageView.setImageDrawable(org.telegram.ui.FutoonChatAppearance.icon(glyph + (isTabSelected() ? "_selected" : "")));
        imageView.setLayoutParams(LayoutHelper.createFrame(28, 28, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
        textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        textView.setIncludeFontPadding(false);
        textView.setHorizontallyScrolling(false);
        textView.scrollTo(0, 0);
        textView.setTypeface(AndroidUtilities.bold());
        invalidate();
    }
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (chatAppearanceGlyph != null) {
            com.futoonpay.admin.appearance.ChatTabGeometry.prepare(imageView, textView, MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        }
        if (selfMeasure) {
            final int width = (int) (measureAttachTabWidth()) + additionalWidth;
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
public float measureTextWidth(float textSizeDp) {
        if (scaledTextPaint == null) {
            scaledTextPaint = new TextPaint(defaultTextPaint);
        }
        scaledTextPaint.setTextSize(chatAppearanceGlyph == null ? dp(textSizeDp) : TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, com.futoonpay.admin.appearance.ChatTabGeometry.LABEL_SP, getResources().getDisplayMetrics()));
        return scaledTextPaint.measureText(textView.getText().toString());
    }
public void setTextSizeDp(float textSizeDp) {
        final float px = chatAppearanceGlyph == null ? dp(textSizeDp) : TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, com.futoonpay.admin.appearance.ChatTabGeometry.LABEL_SP, getResources().getDisplayMetrics());
        if (textView.getTextSize() != px) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
            defaultTextPaint.setTextSize(px);
        }
    }
}
