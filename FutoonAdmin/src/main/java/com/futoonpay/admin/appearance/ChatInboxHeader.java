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
    private ChatAttentionBand attention;
    public ChatInboxHeader(Context context){super(context);}
    // Geometry measured from the reference: 6 top inset, 34pt title row, 34pt search field and 34pt filter pills.
    public static final int TOP_INSET=6, TITLE_GAP=8, FILTER_GAP=9;
    private static int titleHeight(float scale){return Math.max(42,Math.round(41*Math.max(1,scale)+1));}
    private static int inputHeight(float scale){return Math.max(34,Math.round(24*Math.max(1,scale)+10));}
    private static int filterFrame(float scale){return Math.max(48,Math.round(28+20*Math.max(1,scale)));}
    public static int titleBand(float scale){return TOP_INSET+titleHeight(scale);}
    public static int searchBand(float scale){return TITLE_GAP+inputHeight(scale)+FILTER_GAP;}
    public static int filterBand(float scale){return filterFrame(scale)+1;}
    public static int homeHeight(float scale){return titleBand(scale)+searchBand(scale)+filterBand(scale);}
    /** Height with the 待我处理 band between the search field and the filters. */
    public static int homeHeight(float scale,ChatAttentionBand band){return homeHeight(scale)+(band==null||band.getVisibility()==GONE?0:band.heightDp());}
    public ChatAttentionBand attentionBand(){return attention;}
    /** Adds (or replaces) the band; the host supplies items later through the band itself. */
    public ChatAttentionBand ensureAttention(boolean dark){if(attention==null){attention=new ChatAttentionBand(getContext(),dark);addView(attention,new LayoutParams(-1,dp(ChatAttentionBand.height(1f,true))));}return attention;}
    private int dp(float value){return ChatChrome.dp(getContext(),value);}
    public void bindHome(String title,View filters,View editor,View search,View close,View extras,View tokens){
        // The compact native search field has its own inset. Our three bands already
        // include their spacing, so inheriting that padding clips the filter viewport.
        setPadding(getPaddingLeft(),0,getPaddingRight(),0);
        this.filters=filters;this.editor=editor;this.search=search;this.close=close;this.extras=extras;this.tokens=tokens;
        if(largeTitle==null){largeTitle=new TextView(getContext());largeTitle.setTextSize(34);{Typeface face=ChatFonts.bold();largeTitle.setTypeface(face!=null?face:Typeface.create("sans-serif",Typeface.BOLD));}largeTitle.setIncludeFontPadding(false);largeTitle.setSingleLine(true);largeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);largeTitle.setGravity(Gravity.CENTER_VERTICAL);largeTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);addView(largeTitle,new LayoutParams(-1,-2));}
        largeTitle.setText(title);requestLayout();
    }
    public void setLargeTitleAlpha(float alpha){if(largeTitle!=null)largeTitle.setAlpha(alpha);}
    public void setHeaderDark(boolean dark){if(largeTitle!=null)largeTitle.setTextColor(ChatLook.text(dark));}
    public int searchTop(){return largeTitle==null?getPaddingTop():editor.getTop();}
    public int searchBottom(){return largeTitle==null?getHeight()-getPaddingBottom():editor.getBottom();}
    private void position(View view,int top,int height,int gravity,int left,int right){if(view==null)return;LayoutParams p=(LayoutParams)view.getLayoutParams();p.gravity=Gravity.TOP|gravity;p.topMargin=dp(top);p.bottomMargin=0;p.height=dp(height);p.leftMargin=dp(left);p.rightMargin=dp(right);}
    @Override protected void onMeasure(int ws,int hs){
        if(largeTitle!=null){float scale=getResources().getConfiguration().fontScale;int title=titleBand(scale),field=searchBand(scale),bar=filterBand(scale),input=inputHeight(scale),top=title+TITLE_GAP;
            // Legacy compact-search LayoutParams must not clip the expanded header.
            int band=attention==null||attention.getVisibility()==GONE?0:attention.heightDp();
            hs=MeasureSpec.makeMeasureSpec(dp(title+field+band+bar)+getPaddingTop()+getPaddingBottom(),MeasureSpec.EXACTLY);
            if(attention!=null&&band>0){LayoutParams ap=(LayoutParams)attention.getLayoutParams();ap.gravity=Gravity.TOP|Gravity.FILL_HORIZONTAL;ap.topMargin=dp(title+field);ap.leftMargin=0;ap.rightMargin=0;ap.height=dp(band);}
            position(largeTitle,TOP_INSET,titleHeight(scale),Gravity.FILL_HORIZONTAL,5,5);
            position(editor,top,input,Gravity.FILL_HORIZONTAL,5,5);
            position(search,top+(input-24)/2,24,Gravity.LEFT,8,13);
            position(close,top+(input-24)/2,24,Gravity.RIGHT,13,13);
            position(extras,top,input,Gravity.RIGHT,32,32);
            position(tokens,top+(input-32)/2,32,Gravity.LEFT,5,5);
            position(filters,title+field+band,bar-1,Gravity.FILL_HORIZONTAL,5,5);
        }
        super.onMeasure(ws,hs);
    }
}
