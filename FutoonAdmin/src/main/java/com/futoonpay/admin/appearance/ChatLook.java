package com.futoonpay.admin.appearance;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Shared Android chat design tokens and vector artwork, independent of business state. */
public final class ChatLook {
    private ChatLook() {}
    public static final int ROW_HEIGHT = 76, NAV_HEIGHT = 80, BUBBLE_RADIUS = 10, INPUT_RADIUS = 24;
    public static int surface(boolean dark) { return dark ? 0xff12181c : 0xffffffff; }
    public static int background(boolean dark) { return dark ? 0xff0a1014 : 0xffffffff; }
    public static int field(boolean dark) { return dark ? 0xff20272b : 0xfff1f2f4; }
    public static int wallpaper(boolean dark) { return dark ? 0xff0a1014 : 0xffefeae2; }
    public static int text(boolean dark) { return dark ? 0xfff7f8fa : 0xff0a1014; }
    public static int secondary(boolean dark) { return dark ? 0xff8d9599 : 0xff5b6368; }
    public static int accent(boolean dark) { return dark ? 0xff21c063 : 0xff1daa61; }
    public static int accentText(boolean dark) { return dark ? 0xff21c063 : 0xff1b8755; }
    public static int selectedText(boolean dark) { return dark ? 0xffd9fdd3 : 0xff15603e; }
    public static int outgoing(boolean dark) { return dark ? 0xff144d37 : 0xffd9fdd3; }
    public static int incoming(boolean dark) { return dark ? 0xff20272b : 0xffffffff; }
    public static int selected(boolean dark) { return dark ? 0xff103529 : 0xffd9fdd3; }
    public static Integer color(String key, boolean dark) {
        switch (key) {
            case "key_chat_wallpaper": return wallpaper(dark);
            case "key_chat_wallpaper_gradient_to1": case "key_chat_wallpaper_gradient_to2": case "key_chat_wallpaper_gradient_to3":
            case "key_chat_outBubbleGradient1": case "key_chat_outBubbleGradient2": case "key_chat_outBubbleGradient3": case "key_chat_outBubbleGradientAnimated": return 0;
            case "key_chat_outBubble": case "key_chat_outBubbleShadow": return outgoing(dark);
            case "key_chat_inBubble": case "key_chat_inBubbleShadow": return incoming(dark);
            case "key_chat_outBubbleSelected": return dark ? 0xff126b59 : 0xffc0e9b7;
            case "key_chat_inBubbleSelected": return dark ? 0xff344650 : 0xffe4e8e8;
            case "key_windowBackgroundWhite": case "key_actionBarDefault": case "key_actionBarDefaultArchived":
            case "key_actionBarDefaultSubmenuBackground": case "key_dialogBackground":
            case "key_chat_messagePanelBackground": case "key_chat_topPanelBackground": case "key_avatar_backgroundActionBarBlue": return surface(dark);
            case "key_windowBackgroundGray": case "key_windowBackgroundGrayShadow": return background(dark);
            case "key_chat_messagePanelText": case "key_chat_messageTextIn": case "key_chat_messageTextOut":
            case "key_actionBarDefaultTitle": case "key_actionBarDefaultIcon": case "key_actionBarDefaultSearch":
            case "key_actionBarDefaultSubmenuItem": case "key_actionBarDefaultSubmenuItemIcon": case "key_dialogTextBlack":
            case "key_windowBackgroundWhiteBlackText": case "key_chats_name": case "key_chats_nameMessage": case "key_profile_title": return text(dark);
            case "key_chat_inTimeText": case "key_chat_outTimeText": case "key_chat_messagePanelHint": case "key_chat_messagePanelIcons":
            case "key_actionBarDefaultSubtitle": case "key_actionBarDefaultSearchPlaceholder": case "key_chats_date":
            case "key_chats_message": case "key_chats_message_threeLines": case "key_windowBackgroundWhiteGrayText":
            case "key_windowBackgroundWhiteGrayText2": case "key_windowBackgroundWhiteGrayText3": case "key_windowBackgroundWhiteGrayText4":
            case "key_glass_defaultIcon": case "key_profile_status": case "key_avatar_subtitleInProfileBlue": case "key_windowBackgroundWhiteGrayIcon": return secondary(dark);
            case "key_chat_inFileNameText": case "key_chat_outFileNameText":
            case "key_chat_inAudioTitleText": case "key_chat_outAudioTitleText":
            case "key_chat_inContactNameText": case "key_chat_outContactNameText": return text(dark);
            case "key_chat_inFileInfoText": case "key_chat_outFileInfoText": case "key_chat_inFileInfoSelectedText": case "key_chat_outFileInfoSelectedText":
            case "key_chat_inContactPhoneText": case "key_chat_outContactPhoneText": case "key_chat_inContactPhoneSelectedText": case "key_chat_outContactPhoneSelectedText":
            case "key_chat_inAudioPerformerText": case "key_chat_outAudioPerformerText": case "key_chat_inAudioDurationText": case "key_chat_outAudioDurationText": return secondary(dark);
            case "key_chat_inForwardedNameText": case "key_chat_outForwardedNameText": return secondary(dark);
            case "key_actionBarTabActiveText": case "key_actionBarTabLine": case "key_telegram_color": return accentText(dark);
            case "key_actionBarTabUnactiveText": return secondary(dark);
            case "key_actionBarTabSelector": return selected(dark);
            case "key_glass_tabUnselected": case "key_glass_tabSelectedText": return text(dark);
            case "key_glass_tabSelected": return selectedText(dark);
            case "key_dialogButton": return accentText(dark);
            case "key_chat_messagePanelSend": return accent(dark);
            case "key_chats_unreadCounter": case "key_featuredStickers_addButton":
            case "key_switch2TrackChecked": case "key_switchTrackChecked": case "key_checkbox": case "key_chats_actionBackground": case "key_profile_actionBackground": case "key_profile_verifiedBackground":
            case "key_telegram_color_dialogsLogo":
            case "key_chat_outReplyLine": case "key_chat_inReplyLine": return accent(dark);
            case "key_chat_outReplyNameText": case "key_chat_inReplyNameText": return selectedText(dark);
            case "key_chats_unreadCounterText": case "key_featuredStickers_buttonText": case "key_chat_messagePanelVoicePressed":
            case "key_checkboxCheck": case "key_chats_actionIcon": case "key_profile_actionIcon": case "key_profile_verifiedCheck": return 0xffffffff;
            case "key_chat_outSentCheck": case "key_chat_outSentCheckSelected": return secondary(dark);
            case "key_chat_outSentCheckRead": case "key_chat_outSentCheckReadSelected": return 0xff53bdeb;
            case "key_chat_inReplyMessageText": case "key_chat_outReplyMessageText": return secondary(dark);
            case "key_chat_inReplyMediaMessageText": case "key_chat_outReplyMediaMessageText": return selectedText(dark);
            case "key_dialogInputField": case "key_chat_messagePanelVoiceBackground": return field(dark);
            case "key_divider": case "key_actionBarDefaultSubmenuSeparator": return dark ? 0xff20272b : 0xfff1f2f4;
            case "key_listSelector": case "key_actionBarDefaultSelector": return dark ? 0x1fe9edef : 0x12111b21;
            case "key_chat_selectedBackground": return dark ? 0x664a8f7c : 0x5580d8a0;
            case "key_chat_serviceBackground": return dark ? 0xff182229 : 0xfff7f4ef;
            case "key_chat_serviceText": case "key_chat_serviceLink": case "key_chat_serviceIcon": return secondary(dark);
        }
        // Preserve destructive/payment/status colors and special media overlays.
        if (key.startsWith("key_windowBackgroundWhiteBlue") || key.startsWith("key_windowBackgroundWhiteGreen") || key.startsWith("key_chat_inSiteName") || key.startsWith("key_chat_outSiteName") || key.startsWith("key_dialogTextBlue")) return accentText(dark);
        return null;
    }
    /** Text bubble body and its top-corner tail share the exact outline used by hit highlights. */
    public static void bubblePath(Path path, RectF body, float radius, boolean outgoing, boolean joinedTop, boolean joinedBottom) {
        path.rewind();
        if (body.width() <= 0 || body.height() <= 0) return;
        radius=Math.max(0, Math.min(radius, Math.min(body.width(), body.height()) / 2));
        float tight=radius*.4f;
        float[] corners={radius,radius,radius,radius,radius,radius,radius,radius};
        if(joinedTop){int at=outgoing?2:0;corners[at]=corners[at+1]=tight;}
        if(joinedBottom){int at=outgoing?4:6;corners[at]=corners[at+1]=tight;}
        path.addRoundRect(body,corners,Path.Direction.CW);
        if(!joinedTop){float tail=radius*.7f;if(outgoing){path.moveTo(body.right-radius,body.top);path.lineTo(body.right+tail,body.top);path.quadTo(body.right+tail,body.top+tail*.3f,body.right,body.top+radius);path.close();}else{path.moveTo(body.left+radius,body.top);path.lineTo(body.left-tail,body.top);path.quadTo(body.left-tail,body.top+tail*.3f,body.left,body.top+radius);path.close();}}
    }
    public static Drawable icon(String glyph, int color, float density) { return new Glyph(glyph, color, density); }
    public static Drawable wallpaperDrawable(boolean dark, float density) { return new Paper(dark, density); }
    private static final class Glyph extends Drawable {
        private final String glyph; private final int color, size; private final Paint p = new Paint(3); private final Path path = new Path(); private int alpha=255;
        Glyph(String glyph,int color,float density) { this.glyph=glyph;this.color=color;size=Math.round(24*density);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND); }
        @Override public void draw(@NonNull Canvas c) {
            int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setAlpha(alpha);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);path.rewind();
            switch(glyph) {
                case "search": c.drawCircle(10.5f,10.5f,6.5f,p);c.drawLine(15.5f,15.5f,21,21,p);break;
                case "menu": p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.6f,p);break;
                case "send": p.setStyle(Paint.Style.FILL);path.moveTo(4,3);path.lineTo(22,12);path.lineTo(4,21);path.lineTo(7,13.2f);path.lineTo(16,12);path.lineTo(7,10.8f);path.close();c.drawPath(path,p);break;
                case "compose":
                    p.setStyle(Paint.Style.FILL);path.moveTo(3,4);path.lineTo(18,4);path.quadTo(21,4,21,7);path.lineTo(21,17);path.quadTo(21,20,18,20);path.lineTo(7,20);path.quadTo(4,20,4,17);path.lineTo(4,8);path.lineTo(2,5);path.quadTo(1.5f,4,3,4);path.close();
                    path.setFillType(Path.FillType.EVEN_ODD);path.moveTo(11,8);path.lineTo(13,8);path.lineTo(13,11);path.lineTo(16,11);path.lineTo(16,13);path.lineTo(13,13);path.lineTo(13,16);path.lineTo(11,16);path.lineTo(11,13);path.lineTo(8,13);path.lineTo(8,11);path.lineTo(11,11);path.close();c.drawPath(path,p);path.setFillType(Path.FillType.WINDING);break;
                case "chat": case "chat_selected":
                    if(glyph.endsWith("_selected"))p.setStyle(Paint.Style.FILL);
                    path.moveTo(4,4);path.lineTo(17,4);path.quadTo(20,4,20,7);path.lineTo(20,15);path.quadTo(20,18,17,18);path.lineTo(8,18);path.lineTo(3,21);path.lineTo(3,7);path.quadTo(3,4,4,4);path.close();c.drawPath(path,p);
                    if(!glyph.endsWith("_selected")){c.drawLine(7,9,16,9,p);c.drawLine(7,13,13,13,p);}break;
                case "profile": case "profile_selected":
                    if(glyph.endsWith("_selected"))p.setStyle(Paint.Style.FILL);
                    c.drawCircle(12,7.5f,3.5f,p);path.moveTo(4,21);path.cubicTo(4,12,20,12,20,21);path.close();c.drawPath(path,p);break;
                case "settings": case "settings_selected":
                    if(glyph.endsWith("_selected")){p.setStyle(Paint.Style.FILL);path.addCircle(12,12,8,Path.Direction.CW);path.addCircle(12,12,3,Path.Direction.CCW);c.drawPath(path,p);}else{c.drawCircle(12,12,3,p);c.drawCircle(12,12,7,p);}
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(glyph.endsWith("_selected")?3:2);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}break;
                case "clip": path.moveTo(8,17);path.lineTo(16,9);path.cubicTo(19,6,22,10,19,13);path.lineTo(10,22);path.cubicTo(5,26,-1,19,4,14);path.lineTo(14,4);path.cubicTo(20,-2,27,6,21,12);c.save();c.scale(.8f,.8f,12,12);c.drawPath(path,p);c.restore();break;
                case "emoji": c.drawCircle(12,12,9,p);c.drawArc(7,8,17,18,20,140,false,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8.5f,9,1,p);c.drawCircle(15.5f,9,1,p);break;
                case "back": c.drawLine(19,12,5,12,p);c.drawLine(5,12,11,6,p);c.drawLine(5,12,11,18,p);break;
                default: c.drawCircle(12,12,8,p);
            }
            c.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth(){return size;} @Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();} @Override public void setColorFilter(@Nullable ColorFilter filter){p.setColorFilter(filter);invalidateSelf();} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Paper extends Drawable {
        private final boolean dark;private final float density;private final Paint p=new Paint(3);private int alpha=255;
        Paper(boolean dark,float density){this.dark=dark;this.density=density;}
        @Override public void draw(@NonNull Canvas c){Rect b=getBounds();if(alpha==0)return;int save=alpha<255?c.saveLayerAlpha(new RectF(b),alpha):c.save();c.clipRect(b);c.drawColor(wallpaper(dark));p.setColor(dark?0x123d5866:0x126f8171);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(.75f,density*.65f));p.setAlpha(18);
            float cell=112*density;for(float y=b.top;y<b.bottom+cell;y+=cell)for(float x=b.left;x<b.right+cell;x+=cell){float shift=(((int)((y-b.top)/cell)&1)==0?0:cell/2);float cx=x+shift+24*density,cy=y+28*density,r=8*density;c.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r/3,r/3,p);c.drawCircle(cx+44*density,cy+33*density,r,p);c.drawLine(cx+18*density,cy+65*density,cx+28*density,cy+55*density,p);c.drawLine(cx+23*density,cy+54*density,cx+23*density,cy+66*density,p);}c.restoreToCount(save);}
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}@Override public void setColorFilter(@Nullable ColorFilter f){p.setColorFilter(f);invalidateSelf();}@Override public int getOpacity(){return alpha==255?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
    }
}
