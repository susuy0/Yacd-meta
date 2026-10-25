package com.futoonpay.admin.appearance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;

/** Common native chrome; no conversation, permission or business state is read here. */
public final class ChatChrome {
    private ChatChrome() {}
    public static final int TOOLBAR=56, ROW=56, DETAIL_ROW=72, ICON=24, MEDIA_RADIUS=10, MENU_RADIUS=12, DIALOG_RADIUS=24, SHEET_RADIUS=28;
    public static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    public static int galleryColumns(int widthDp){return widthDp>=900?6:widthDp>=600?4:widthDp<300?2:3;}
    public static int galleryTile(int width,int columns,int padding,int gap){return Math.max(1,(width-padding*2-gap*(columns-1))/Math.max(1,columns));}
    public static int rowHeight(Context c,boolean detail){float scale=Math.max(1,c.getResources().getConfiguration().fontScale);return dp(c,Math.max(detail?DETAIL_ROW:ROW,32+(detail?40:24)*scale));}
    public static GradientDrawable surface(Context c,boolean dark,int radius){GradientDrawable d=new GradientDrawable();d.setColor(ChatLook.surface(dark));d.setCornerRadius(dp(c,radius));return d;}
    public static RippleDrawable action(Context c,boolean dark,int radius){GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(c,radius));return new RippleDrawable(ColorStateList.valueOf(dark?0x1fffffff:0x16000000),surface(c,dark,radius),mask);}
    public interface NightMode { boolean dark(); }
    public static android.graphics.drawable.Drawable field(Context c,NightMode mode) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint=new android.graphics.Paint(3);
            private final android.graphics.RectF rect=new android.graphics.RectF();
            private int alpha=255;
            @Override public void draw(android.graphics.Canvas canvas){rect.set(getBounds());paint.setColor(ChatLook.field(mode.dark()));paint.setAlpha(alpha);canvas.drawRoundRect(rect,dp(c,24),dp(c,24),paint);}
            @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    public static int preferenceHeight(Context c,android.widget.TextView title,android.widget.TextView detail){
        boolean hasDetail=detail.getVisibility()==android.view.View.VISIBLE&&!android.text.TextUtils.isEmpty(detail.getText());
        if(!hasDetail)return rowHeight(c,false);
        return Math.max(rowHeight(c,true),title.getMeasuredHeight()+detail.getMeasuredHeight()+dp(c,36));
    }
    public static void stackLabels(android.view.View parent,android.widget.TextView title,android.widget.TextView detail){
        if(detail.getVisibility()!=android.view.View.VISIBLE||android.text.TextUtils.isEmpty(detail.getText()))return;
        int gap=dp(parent.getContext(),4),top=Math.max(0,(parent.getHeight()-title.getMeasuredHeight()-detail.getMeasuredHeight()-gap)/2);
        title.layout(title.getLeft(),top,title.getRight(),top+title.getMeasuredHeight());
        int next=top+title.getMeasuredHeight()+gap;detail.layout(detail.getLeft(),next,detail.getRight(),next+detail.getMeasuredHeight());
    }
    public static boolean animationsEnabled(Context c){return Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}
}
