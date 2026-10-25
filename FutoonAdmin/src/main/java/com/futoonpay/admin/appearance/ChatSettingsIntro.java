package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Actual native large-title/search entry, with search owned by the host activity. */
public final class ChatSettingsIntro extends LinearLayout {
    private final TextView title,search;
    public ChatSettingsIntro(Context context,String label,String hint,Runnable action){
        super(context);setOrientation(VERTICAL);setPadding(dp(16),dp(8),dp(16),dp(16));
        title=new TextView(context);title.setText(label);title.setTextSize(34);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setIncludeFontPadding(false);addView(title,new LayoutParams(-1,-2));
        search=new TextView(context);search.setText(hint);search.setTextSize(17);search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(12),0,dp(12),0);search.setCompoundDrawablesWithIntrinsicBounds(ChatLook.icon("search",ChatLook.secondary(false),getResources().getDisplayMetrics().density),null,null,null);search.setCompoundDrawablePadding(dp(8));search.setFocusable(true);search.setContentDescription(hint);search.setOnClickListener(v->action.run());
        LayoutParams lp=new LayoutParams(-1,dp(Math.max(36,Math.round(24*getResources().getConfiguration().fontScale+12))));lp.topMargin=dp(12);addView(search,lp);setDark(false);
    }
    private int dp(float v){return ChatChrome.dp(getContext(),v);}
    public void setDark(boolean dark){title.setTextColor(ChatLook.text(dark));search.setTextColor(ChatLook.secondary(dark));GradientDrawable field=new GradientDrawable();field.setColor(ChatLook.field(dark));field.setCornerRadius(dp(10));search.setBackground(field);search.setCompoundDrawablesWithIntrinsicBounds(ChatLook.icon("search",ChatLook.secondary(dark),getResources().getDisplayMetrics().density),null,null,null);}
}
