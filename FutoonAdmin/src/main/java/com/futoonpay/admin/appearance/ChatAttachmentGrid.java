package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Native attachment launcher. Choices execute only after an explicit tap. */
public final class ChatAttachmentGrid extends ScrollView {
    public static final class Entry {
        public final String key; public final CharSequence label; public final Drawable icon; public final Runnable action, longAction;
        public Entry(String key,CharSequence label,Drawable icon,Runnable action){this(key,label,icon,action,null);}
        public Entry(String key,CharSequence label,Drawable icon,Runnable action,Runnable longAction){this.key=key;this.label=label;this.icon=icon;this.action=action;this.longAction=longAction;}
    }
    private final Tiles tiles; private boolean chosen, rtl;
    public void setRtl(boolean value){rtl=value;tiles.requestLayout();}
    public ChatAttachmentGrid(Context context,boolean dark,List<Entry> entries) {
        super(context);setFillViewport(false);setClipToPadding(false);setVerticalScrollBarEnabled(false);
        GradientDrawable background=new GradientDrawable();background.setColor(ChatLook.wallpaper(dark));background.setCornerRadius(dp(16));setBackground(background);
        tiles=new Tiles(context);tiles.setPadding(dp(16),dp(16),dp(16),dp(16));addView(tiles,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        for(Entry entry:entries){
            LinearLayout cell=new LinearLayout(context);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(2),dp(8),dp(2),dp(8));
            cell.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(android.widget.Button.class.getName());}});
            cell.setTag(entry.key);cell.setFocusable(true);cell.setClickable(true);cell.setContentDescription(entry.label);
            GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(12));cell.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x33ffffff:0x16000000),null,mask));
            FrameLayout iconBox=new FrameLayout(context);GradientDrawable iconBg=new GradientDrawable();iconBg.setColor(ChatLook.incoming(dark));iconBg.setCornerRadius(dp(30));iconBox.setBackground(iconBg);
            ImageView icon=new ImageView(context);icon.setImageDrawable(entry.icon);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER);iconBox.addView(icon,ip);cell.addView(iconBox,new LinearLayout.LayoutParams(dp(60),dp(60)));
            TextView label=new TextView(context);label.setText(entry.label);label.setTextColor(ChatLook.text(dark));label.setTextSize(14);label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);lp.topMargin=dp(8);cell.addView(label,lp);
            cell.setOnClickListener(v->{if(chosen)return;chosen=true;entry.action.run();});if(entry.longAction!=null)cell.setOnLongClickListener(v->{if(chosen)return true;chosen=true;entry.longAction.run();return true;});tiles.addView(cell);
        }
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    public int columns(){return tiles.columns;}
    /** Glyph colours sampled from the reference attach sheet. */
    public static int tint(String glyph,boolean dark){
        switch(glyph){case "gallery":return 0xff1d7cf5;case "file":return 0xff0a95e8;case "poll":return 0xfff9b233;case "todo":return 0xffe5073a;case "audio":return dark?0xfff7f8fa:0xff333333;case "money":return 0xff1bc995;default:return dark?0xffa8b0b4:0xff6b6b6e;}
    }
    private final class Tiles extends ViewGroup {
        int columns=1,rowHeight;
        Tiles(Context c){super(c);}
        @Override protected void onMeasure(int ws,int hs){
            int width=MeasureSpec.getSize(ws),available=Math.max(1,width-getPaddingLeft()-getPaddingRight());
            columns=Math.max(1,Math.min(Math.min(4,Math.max(1,getChildCount())),available/dp(84)));int cellWidth=available/columns;rowHeight=dp(108);
            for(int i=0;i<getChildCount();i++){View c=getChildAt(i);c.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));rowHeight=Math.max(rowHeight,c.getMeasuredHeight());}
            int rows=(getChildCount()+columns-1)/columns;setMeasuredDimension(width,getPaddingTop()+getPaddingBottom()+rows*rowHeight);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int available=getWidth()-getPaddingLeft()-getPaddingRight(),cell=available/columns;
            for(int i=0;i<getChildCount();i++){int column=i%columns;if(rtl)column=columns-1-column;View c=getChildAt(i);int x=getPaddingLeft()+column*cell,y=getPaddingTop()+(i/columns)*rowHeight;c.layout(x,y,x+cell,y+rowHeight);}
        }
    }
}
