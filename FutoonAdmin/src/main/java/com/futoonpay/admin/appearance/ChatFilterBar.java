package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Real filter buttons shared by the chat list and native layout regressions. */
public final class ChatFilterBar extends HorizontalScrollView {
    private final LinearLayout row;private boolean dark;private int selected;
    public ChatFilterBar(Context context,String[] labels,boolean dark,IntConsumer callback) {
        super(context);this.dark=dark;setHorizontalScrollBarEnabled(false);setFillViewport(false);
        row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);addView(row,new LayoutParams(-2,-2));
        for(int i=0;i<labels.length;i++){final int value=i;TextView button=new TextView(context){@Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");info.setSelected(isSelected());}};
            button.setText(labels[i]);button.setTextSize(15);{Typeface face=ChatFonts.semibold();button.setTypeface(face!=null?face:Typeface.create("sans-serif-medium",Typeface.NORMAL));}button.setGravity(Gravity.CENTER);button.setPadding(dp(12),0,dp(12),0);button.setFocusable(true);button.setOnClickListener(v->{select(value);callback.accept(value);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(Math.max(48,28+20*Math.max(1,getResources().getConfiguration().fontScale))));lp.setMarginEnd(dp(9));row.addView(button,lp);
        }select(0);
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    public void select(int value){selected=value;for(int i=0;i<row.getChildCount();i++){TextView b=(TextView)row.getChildAt(i);boolean active=i==value;b.setSelected(active);b.setTextColor(active?ChatLook.accent(dark):ChatLook.secondary(dark));GradientDrawable bg=new GradientDrawable();bg.setColor(active?ChatLook.selected(dark):ChatLook.field(dark));bg.setCornerRadius(dp(24));int inset=Math.max(0,(b.getLayoutParams().height-dp(34))/2);b.setBackground(new InsetDrawable(bg,0,inset,0,inset));b.setPadding(dp(12),0,dp(12),0);}}
    public void setDark(boolean value){dark=value;select(selected);}
    public int selectedFilter(){return selected;}
}
