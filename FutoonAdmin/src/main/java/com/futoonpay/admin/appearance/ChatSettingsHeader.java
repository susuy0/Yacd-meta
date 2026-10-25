package com.futoonpay.admin.appearance;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Repositions the host's own avatar and text; its listeners and account state stay with the host. */
public final class ChatSettingsHeader extends FrameLayout {
    private View avatar, action;
    private final android.graphics.drawable.GradientDrawable card=new android.graphics.drawable.GradientDrawable();
    private boolean rtl;
    public void setRtl(boolean value) { rtl=value; if(title!=null){ title.setGravity((rtl?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL); subtitle.setGravity((rtl?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL); } requestLayout(); }
    private TextView title, subtitle;
    public ChatSettingsHeader(Context context) { super(context); setClipChildren(false); card.setColor(ChatLook.surface(false));card.setCornerRadius(dp(10));setBackground(new android.graphics.drawable.InsetDrawable(card,dp(16),dp(4),dp(16),dp(4))); }
    public void setDark(boolean dark) {card.setColor(ChatLook.surface(dark));invalidate();}
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    public static int heightDp(Context context) {
        return Math.max(104, 32 + Math.max(64, Math.round(44 * context.getResources().getConfiguration().fontScale + 4)));
    }
    public void setParts(View avatar, TextView title, TextView subtitle, View action) {
        this.action=action; this.avatar=avatar; this.title=title; this.subtitle=subtitle;
        for(View child:new View[]{avatar,title,subtitle,action}) {
            if(child.getParent()!=this) { if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);addView(child); }
        }
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP,22); subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        title.setIncludeFontPadding(false);subtitle.setIncludeFontPadding(false);
        setRtl(rtl);
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec), height=resolveSize(dp(heightDp(getContext())),heightSpec);
        if(avatar!=null) {
            avatar.measure(MeasureSpec.makeMeasureSpec(dp(56),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(56),MeasureSpec.EXACTLY));
            action.measure(MeasureSpec.makeMeasureSpec(dp(56),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(64),MeasureSpec.EXACTLY));
            int available=Math.max(0,width-dp(180));
            title.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            subtitle.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        }
        setMeasuredDimension(width,height);
    }
    private void place(View child,int x,int y) {
        if(rtl)x=getMeasuredWidth()-x-child.getMeasuredWidth();
        child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        if(avatar==null)return;
        place(avatar,dp(32),(getHeight()-avatar.getMeasuredHeight())/2);
        place(action,getWidth()-dp(72),(getHeight()-action.getMeasuredHeight())/2);
        int block=title.getMeasuredHeight()+dp(4)+subtitle.getMeasuredHeight(), y=(getHeight()-block)/2;
        place(title,dp(100),y);place(subtitle,dp(100),y+title.getMeasuredHeight()+dp(4));
    }
}
