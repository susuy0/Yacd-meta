package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Production inbox header: measured large title, real search controls and one filter row. */
public class ChatInboxHeader extends FrameLayout {
    private TextView largeTitle;
    private View filters,editor,search,close,extras,tokens;
    public ChatInboxHeader(Context context){super(context);}
    public static int titleBand(float scale){return Math.max(52,Math.round(41*Math.max(1,scale)+11));}
    public static int searchBand(float scale){return Math.max(52,Math.round(24*Math.max(1,scale)+28));}
    public static int filterBand(float scale){return Math.max(56,Math.round(20*Math.max(1,scale)+36));}
    public static int homeHeight(float scale){return titleBand(scale)+searchBand(scale)+filterBand(scale);}
    private int dp(float value){return ChatChrome.dp(getContext(),value);}
    public void bindHome(String title,View filters,View editor,View search,View close,View extras,View tokens){
        this.filters=filters;this.editor=editor;this.search=search;this.close=close;this.extras=extras;this.tokens=tokens;
        if(largeTitle==null){largeTitle=new TextView(getContext());largeTitle.setTextSize(34);largeTitle.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));largeTitle.setIncludeFontPadding(false);largeTitle.setSingleLine(true);largeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);largeTitle.setGravity(Gravity.CENTER_VERTICAL);largeTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);addView(largeTitle,new LayoutParams(-1,-2));}
        largeTitle.setText(title);requestLayout();
    }
    public void setHeaderDark(boolean dark){if(largeTitle!=null)largeTitle.setTextColor(ChatLook.text(dark));}
    public int searchTop(){return largeTitle==null?getPaddingTop():dp(titleBand(getResources().getConfiguration().fontScale)+8);}
    public int searchBottom(){return largeTitle==null?getHeight()-getPaddingBottom():dp(titleBand(getResources().getConfiguration().fontScale)+searchBand(getResources().getConfiguration().fontScale)-8);}
    private void position(View view,int top,int height,int gravity,int left,int right){if(view==null)return;LayoutParams p=(LayoutParams)view.getLayoutParams();p.gravity=Gravity.TOP|gravity;p.topMargin=dp(top);p.bottomMargin=0;p.height=dp(height);p.leftMargin=dp(left);p.rightMargin=dp(right);}
    @Override protected void onMeasure(int ws,int hs){
        if(largeTitle!=null){float scale=getResources().getConfiguration().fontScale;int title=titleBand(scale),field=searchBand(scale),bar=filterBand(scale),input=field-16;
            position(largeTitle,0,title,Gravity.FILL_HORIZONTAL,5,5);
            position(editor,title+4,input,Gravity.FILL_HORIZONTAL,5,5);
            position(search,title+4+(input-24)/2,24,Gravity.LEFT,13,13);
            position(close,title+4+(input-24)/2,24,Gravity.RIGHT,13,13);
            position(extras,title+4,input,Gravity.RIGHT,32,32);
            position(tokens,title+4+(input-32)/2,32,Gravity.LEFT,5,5);
            position(filters,title+field,bar-8,Gravity.FILL_HORIZONTAL,5,5);
        }
        super.onMeasure(ws,hs);
    }
}
