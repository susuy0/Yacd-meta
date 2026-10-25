package com.futoonpay.admin.appearance;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Hosts the existing native labels and icon, preserving their listeners, spans and accessibility. */
public class ChatPreferenceRow extends FrameLayout {
    private TextView title,detail;
    private View icon;
    private boolean rtl;
    private final android.text.TextWatcher contentWatcher=new android.text.TextWatcher(){
        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        @Override public void onTextChanged(CharSequence s,int start,int before,int count){requestLayout();}
        @Override public void afterTextChanged(android.text.Editable text){}
    };
    public ChatPreferenceRow(Context c){super(c);}
    public final void bindChatRow(TextView title,TextView detail,View icon,boolean rtl){
        if(this.title!=null)this.title.removeTextChangedListener(contentWatcher);
        if(this.detail!=null)this.detail.removeTextChangedListener(contentWatcher);
        this.title=title;this.detail=detail;this.icon=icon;this.rtl=rtl;
        title.addTextChangedListener(contentWatcher);detail.addTextChangedListener(contentWatcher);
        title.getLayoutParams().height=LayoutParams.WRAP_CONTENT;
        detail.getLayoutParams().height=LayoutParams.WRAP_CONTENT;
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);detail.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
        title.setIncludeFontPadding(false);detail.setIncludeFontPadding(false);
        title.setGravity((rtl?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL);
        detail.setGravity((rtl?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL);
        requestLayout();
    }
    private int dp(float value){return ChatChrome.dp(getContext(),value);}
    private boolean hasDetail(){return detail!=null&&detail.getVisibility()!=GONE&&!TextUtils.isEmpty(detail.getText());}
    private boolean hasIcon(){return icon!=null&&icon.getVisibility()==VISIBLE;}
    @Override protected void onMeasure(int ws,int hs){
        if(title==null){super.onMeasure(ws,hs);return;}
        int width=MeasureSpec.getSize(ws),start=dp(hasIcon()?72:24),end=dp(24),available=Math.max(0,width-start-end);
        // The original cell used text padding as an icon gutter. The gutter is now in layout.
        title.setPadding(0,0,0,0);detail.setPadding(0,0,0,0);
        title.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        detail.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        if(icon!=null)icon.measure(MeasureSpec.makeMeasureSpec(dp(24),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(24),MeasureSpec.EXACTLY));
        int textHeight=title.getMeasuredHeight()+(hasDetail()?dp(4)+detail.getMeasuredHeight():0);
        setMeasuredDimension(width,resolveSize(Math.max(dp(hasDetail()?72:56),textHeight+dp(32)),hs));
    }
    private void place(View v,int x,int y){if(rtl)x=getMeasuredWidth()-x-v.getMeasuredWidth();v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        if(title==null){super.onLayout(changed,l,t,r,b);return;}
        int h=title.getMeasuredHeight()+(hasDetail()?dp(4)+detail.getMeasuredHeight():0),y=(getHeight()-h)/2,start=dp(hasIcon()?72:24);
        if(hasIcon())place(icon,dp(24),(getHeight()-icon.getMeasuredHeight())/2);
        place(title,start,y);if(hasDetail())place(detail,start,y+title.getMeasuredHeight()+dp(4));else detail.layout(0,0,0,0);
    }
}
