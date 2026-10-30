package com.futoonpay.admin;

import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import java.util.*;

/** Verified order actions survive Telegram's entity/layout rebuilding without changing message text. */
public final class ChatOrderHintMarks {
    private static final class Mark {
        final String text; final int start,end; final ClickableSpan span; URLSpan phone;
        Mark(String text,int start,int end,ClickableSpan span){this.text=text;this.start=start;this.end=end;this.span=span;}
    }
    private static final Map<Object,List<Mark>> marks=new WeakHashMap<>();
    public static synchronized void put(Object owner,String text,int start,int end,ClickableSpan span){
        if(start<0||end<=start||end>text.length())return;
        List<Mark> rows=marks.computeIfAbsent(owner,k->new ArrayList<>());
        rows.removeIf(m->m.start==start&&m.end==end);rows.add(new Mark(text,start,end,span));
    }
    public static synchronized void clear(Object owner,CharSequence... values){
        List<Mark> rows=marks.remove(owner);
        if(rows!=null)for(CharSequence value:values)if(value instanceof Spannable){
            Spannable text=(Spannable)value;
            for(Mark mark:rows){text.removeSpan(mark.span);
                if(mark.phone!=null&&mark.text.contentEquals(value)&&text.getSpans(mark.start,mark.end,ClickableSpan.class).length==0)text.setSpan(mark.phone,mark.start,mark.end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    public static synchronized boolean restore(Object owner,CharSequence value){
        if(!(value instanceof Spannable))return false;
        List<Mark> rows=marks.get(owner);if(rows==null)return false;
        Spannable text=(Spannable)value;boolean applied=false;
        for(Mark mark:rows){
            text.removeSpan(mark.span);
            if(!mark.text.contentEquals(value))continue;
            boolean conflict=false;List<URLSpan> phones=new ArrayList<>();
            for(ClickableSpan link:text.getSpans(mark.start,mark.end,ClickableSpan.class)){
                // A verified exact order number may replace an automatically detected phone number.
                if(link instanceof URLSpan&&((URLSpan)link).getURL()!=null&&((URLSpan)link).getURL().startsWith("tel:")&&text.getSpanStart(link)==mark.start&&text.getSpanEnd(link)==mark.end)phones.add((URLSpan)link);
                else {conflict=true;break;}
            }
            if(!conflict){for(URLSpan phone:phones){if(mark.phone==null)mark.phone=phone;text.removeSpan(phone);}text.setSpan(mark.span,mark.start,mark.end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);applied=true;}
        }
        return applied;
    }
    private ChatOrderHintMarks(){}
}
