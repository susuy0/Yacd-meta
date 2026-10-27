package com.futoonpay.admin.appearance;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Shared Android chat design tokens and vector artwork, independent of business state. */
public final class ChatLook {
    private ChatLook() {}
    public static final int ROW_HEIGHT = 76, NAV_HEIGHT = 56, BUBBLE_RADIUS = 12, INPUT_RADIUS = 18;
    public static int surface(boolean dark) { return dark ? 0xff12181c : 0xffffffff; }
    public static int background(boolean dark) { return dark ? 0xff0a1014 : 0xfff4f4f4; }
    public static int field(boolean dark) { return dark ? 0xff20272b : 0xfff4f4f4; }
    public static int wallpaper(boolean dark) { return dark ? 0xff0a1014 : 0xfff7f5ef; }
    public static int text(boolean dark) { return dark ? 0xfff7f8fa : 0xff0a0a0a; }
    public static int secondary(boolean dark) { return dark ? 0xff8d9599 : 0xff767779; }
    public static int accent(boolean dark) { return dark ? 0xff21c063 : 0xff1dab61; }
    public static int accentText(boolean dark) { return dark ? 0xff21c063 : 0xff1b8755; }
    public static int selectedText(boolean dark) { return dark ? 0xffd9fdd3 : 0xff15603e; }
    public static int outgoing(boolean dark) { return dark ? 0xff144d37 : 0xffd8fcd8; }
    public static int incoming(boolean dark) { return dark ? 0xff20272b : 0xffffffff; }
    public static int selected(boolean dark) { return dark ? 0xff103529 : 0xffd8fcd8; }
    /** Bottom tab bar fill and its top hairline, sampled from the iOS reference (#F8F8F8 / #D6D6D6). */
    public static int tabBar(boolean dark) { return dark ? 0xff12181c : 0xfff8f8f8; }
    public static int tabBorder(boolean dark) { return dark ? 0xff20272b : 0xffd6d6d6; }
    public static Integer color(String key, boolean dark) {
        switch (key) {
            case "key_checkboxSquareUnchecked": case "key_radioBackground": return secondary(dark);
            case "key_chat_emojiPanelBackground": case "key_player_background": return surface(dark);
            case "key_chat_emojiSearchBackground": case "key_chat_emojiPanelShadowLine": case "key_player_progressBackground": return field(dark);
            case "key_chat_emojiSearchIcon": case "key_chat_emojiPanelEmptyText": case "key_chat_emojiPanelIcon": case "key_chat_emojiBottomPanelIcon": case "key_chat_emojiPanelBackspace": case "key_chat_emojiPanelStickerSetName": case "key_chat_emojiPanelStickerSetNameIcon": case "key_chat_emojiPanelTrendingDescription": case "key_player_actionBarSubtitle": case "key_player_time": case "key_player_button": case "key_windowBackgroundWhiteInputField": return secondary(dark);
            case "key_chat_emojiPanelTrendingTitle": case "key_player_actionBarTitle": case "key_player_actionBarItems": return text(dark);
            case "key_chat_emojiPanelIconSelected": case "key_chat_emojiPanelStickerPackSelectorLine": case "key_chat_emojiPanelNewTrending": case "key_player_progress": case "key_player_buttonActive": case "key_checkboxSquareBackground": case "key_radioBackgroundChecked": case "key_windowBackgroundWhiteInputFieldActivated": return accent(dark);
            case "key_chat_emojiPanelStickerPackSelector": case "key_player_actionBarSelector": return selected(dark);
            case "key_chat_emojiPanelStickerSetNameHighlight": return accentText(dark);
            case "key_checkboxSquareCheck": return 0xffffffff;
            case "key_graySection": return surface(dark);
            case "key_graySectionText": return secondary(dark);
            case "key_actionBarActionModeDefault": case "key_actionBarActionModeDefaultTop": case "key_dialogTopBackground": return surface(dark);
            case "key_dialogBackgroundGray": case "key_dialogSearchBackground": case "key_chat_botKeyboardButtonBackground": case "key_chat_inFileBackground": case "key_sharedMedia_linkPlaceholder": case "key_sharedMedia_photoPlaceholder": case "key_chat_inAudioSeekbar": case "key_chat_outAudioSeekbar": case "key_dialogLineProgressBackground": case "key_dialog_inlineProgressBackground": return field(dark);
            case "key_chat_outFileBackground": return outgoing(dark);
            case "key_dialogButtonSelector": case "key_actionBarActionModeDefaultSelector": case "key_profile_tabSelector": case "key_chat_botKeyboardButtonBackgroundPressed": case "key_chat_inFileBackgroundSelected": case "key_chat_outFileBackgroundSelected": case "key_dialogLinkSelection": return selected(dark);
            case "key_actionBarActionModeDefaultIcon": case "key_dialogSearchText": case "key_chat_botKeyboardButtonText": case "key_sharedMedia_linkPlaceholderText": return text(dark);
            case "key_dialogTextGray": case "key_dialogTextGray2": case "key_dialogTextGray3": case "key_dialogTextGray4": case "key_dialogTextHint": case "key_dialogIcon": case "key_dialogSearchHint": case "key_dialogSearchIcon": case "key_dialogEmptyText": case "key_windowBackgroundWhiteValueText": case "key_profile_tabText": case "key_chat_inAudioPerformerSelectedText": case "key_chat_outAudioPerformerSelectedText": case "key_chat_inAudioDurationSelectedText": case "key_chat_outAudioDurationSelectedText": return secondary(dark);
            case "key_dialogTextLink": case "key_profile_tabSelectedText": case "key_chat_inPreviewInstantText": case "key_chat_outPreviewInstantText": return accentText(dark);
            case "key_dialogInputFieldActivated": case "key_dialogCheckboxSquareBackground": case "key_dialogRoundCheckBox": case "key_dialogRadioBackgroundChecked": case "key_dialogLineProgress": case "key_dialog_inlineProgress": case "key_profile_tabSelectedLine": case "key_chat_inFileProgress": case "key_chat_outFileProgress": case "key_chat_inFileProgressSelected": case "key_chat_outFileProgressSelected": case "key_chat_inAudioProgress": case "key_chat_outAudioProgress": case "key_chat_inAudioSelectedProgress": case "key_chat_outAudioSelectedProgress": case "key_chat_inAudioSeekbarFill": case "key_chat_outAudioSeekbarFill": case "key_chat_inPreviewLine": case "key_chat_outPreviewLine": case "key_sharedMedia_startStopLoadIcon": case "key_switchTrackBlueChecked": case "key_switchTrackBlueThumbChecked": return accent(dark);
            case "key_dialogCheckboxSquareCheck": case "key_dialogRoundCheckBoxCheck": return 0xffffffff;
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
            case "key_glass_tabUnselected": return secondary(dark);
            case "key_glass_tabSelectedText": return text(dark);
            case "key_glass_tabSelected": return text(dark);
            case "key_dialogButton": return accentText(dark);
            case "key_chat_messagePanelSend": return accent(dark);
            case "key_chats_unreadCounter": case "key_featuredStickers_addButton":
            case "key_switch2TrackChecked": case "key_switchTrackChecked": case "key_checkbox": case "key_chats_actionBackground": case "key_profile_actionBackground": case "key_profile_verifiedBackground":
            case "key_telegram_color_dialogsLogo":
            case "key_chat_outReplyLine": case "key_chat_inReplyLine": return accent(dark);
            case "key_chat_outReplyNameText": case "key_chat_inReplyNameText": return selectedText(dark);
            case "key_chat_topPanelTitle": return accentText(dark);
            case "key_chat_topPanelLine": return accent(dark);
            case "key_chat_topPanelMessage": case "key_chat_topPanelClose": return secondary(dark);
            case "key_chats_unreadCounterText": case "key_featuredStickers_buttonText": case "key_chat_messagePanelVoicePressed":
            case "key_checkboxCheck": case "key_chats_actionIcon": case "key_profile_actionIcon": case "key_profile_verifiedCheck": return 0xffffffff;
            case "key_chat_outSentCheck": case "key_chat_outSentCheckSelected": return secondary(dark);
            case "key_chat_outSentCheckRead": case "key_chat_outSentCheckReadSelected": return 0xff53bdeb;
            case "key_chat_inReplyMessageText": case "key_chat_outReplyMessageText": return secondary(dark);
            case "key_chat_inReplyMediaMessageText": case "key_chat_outReplyMediaMessageText": return selectedText(dark);
            case "key_dialogInputField": case "key_chat_messagePanelVoiceBackground": return field(dark);
            case "key_divider": case "key_actionBarDefaultSubmenuSeparator": return dark ? 0xff20272b : 0xffe4e4e4;
            case "key_listSelector": case "key_actionBarDefaultSelector": return dark ? 0x1fe9edef : 0x12111b21;
            case "key_chat_selectedBackground": return dark ? 0x664a8f7c : 0x5580d8a0;
            // Chat-list accents: WhatsApp uses green for unread time/typing/draft, gray for media labels, blue only for read ticks.
            case "key_telegram_color_text": case "key_chats_actionMessage": case "key_chats_draft": return accentText(dark);
            case "key_chats_attachMessage": case "key_chats_sentCheck": case "key_chats_sentClock": case "key_chats_pinnedIcon": case "key_chats_muteIcon": return secondary(dark);
            case "key_chats_sentReadCheck": return 0xff53bdeb;
            case "key_chats_verifiedBackground": return accent(dark);
            case "key_chats_verifiedCheck": return 0xffffffff;
            case "key_chat_serviceBackground": return dark ? 0xff182229 : 0xffffffff;
            case "key_chat_serviceText": case "key_chat_serviceLink": case "key_chat_serviceIcon": return text(dark);
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
        if(!joinedBottom){float tail=radius*.65f;if(outgoing){path.moveTo(body.right-radius,body.bottom);path.lineTo(body.right+tail,body.bottom);path.quadTo(body.right,body.bottom-tail*.4f,body.right,body.bottom-radius);path.close();}else{path.moveTo(body.left+radius,body.bottom);path.lineTo(body.left-tail,body.bottom);path.quadTo(body.left,body.bottom-tail*.4f,body.left,body.bottom-radius);path.close();}}
    }
    public static Drawable icon(String glyph, int color, float density) { return new Glyph(glyph, color, density); }
    public static Drawable wallpaperDrawable(boolean dark, float density) { return new Paper(dark, density); }
    private static Bitmap doodleBitmap;
    private static int doodleDensity;
    public static synchronized Drawable wallpaperDrawable(android.content.res.Resources resources, boolean dark) {
        int density=resources.getDisplayMetrics().densityDpi;
        if(doodleBitmap==null || doodleDensity!=density) {
            doodleBitmap=BitmapFactory.decodeResource(resources, com.futoonpay.admin.R.drawable.futoon_chat_doodle);
            doodleDensity=density;
        }
        return doodleBitmap == null ? wallpaperDrawable(dark, resources.getDisplayMetrics().density) : new DoodlePaper(doodleBitmap, dark);
    }
    private static final class DoodlePaper extends Drawable {
        private final boolean dark; private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG); private int alpha=255;
        DoodlePaper(Bitmap bitmap, boolean dark) {
            this.dark=dark; ink.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            // The reference PNG is an opaque grayscale mask, not an RGBA image.
            // Map its luminance to coverage; SRC_IN alone would tint the whole rectangle.
            int tint=dark?0x1affffff:0xffe9e4d9;
            float coverage=Color.alpha(tint)/255f;
            ink.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0,0,0,0,Color.red(tint), 0,0,0,0,Color.green(tint), 0,0,0,0,Color.blue(tint),
                .2126f*coverage,.7152f*coverage,.0722f*coverage,0,0
            })));
        }
        @Override public void draw(@NonNull Canvas canvas) {
            if(alpha==0)return; Rect bounds=getBounds(); int save=alpha<255?canvas.saveLayerAlpha(new RectF(bounds),alpha):canvas.save();
            canvas.clipRect(bounds); canvas.drawColor(wallpaper(dark)); ink.setAlpha(255); canvas.drawRect(bounds,ink); canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
        @Override public void setColorFilter(@Nullable ColorFilter filter){ink.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return alpha==255?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
    }

    private static final class Glyph extends Drawable {
        private final String glyph; private final int color, size; private final Paint p = new Paint(3); private final Path path = new Path(); private int alpha=255;
        Glyph(String glyph,int color,float density) { this.glyph=glyph;this.color=color;size=Math.round(24*density);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND); }
        @Override public void draw(@NonNull Canvas c) {
            int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setAlpha(alpha);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);path.rewind();
            switch(glyph) {
                case "copy": c.drawRoundRect(8,7,21,21,2,2,p);path.moveTo(16,3);path.lineTo(5,3);path.quadTo(3,3,3,5);path.lineTo(3,16);c.drawPath(path,p);break;
                case "forward": path.moveTo(14,4);path.lineTo(22,11);path.lineTo(14,18);path.lineTo(14,13);path.cubicTo(7,13,4,16,2,20);path.cubicTo(2,10,6,8,14,8);path.close();c.drawPath(path,p);break;
                case "delete": c.drawLine(3,6,21,6,p);path.moveTo(8,6);path.lineTo(8,3);path.lineTo(16,3);path.lineTo(16,6);path.moveTo(5,6);path.lineTo(6,21);path.lineTo(18,21);path.lineTo(19,6);c.drawPath(path,p);c.drawLine(10,10,10,17,p);c.drawLine(14,10,14,17,p);break;
                case "download": c.drawLine(12,3,12,16,p);c.drawLine(7,11,12,16,p);c.drawLine(12,16,17,11,p);path.moveTo(4,17);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,17);c.drawPath(path,p);break;
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
                case "edit": path.moveTo(4,16);path.lineTo(4,21);path.lineTo(9,20);path.lineTo(21,8);path.lineTo(16,3);path.close();c.drawPath(path,p);c.drawLine(13,6,18,11,p);break;
                case "gallery": c.drawRoundRect(3,3,21,21,3,3,p);c.drawCircle(8,8,2,p);path.moveTo(3,18);path.lineTo(9,12);path.lineTo(13,16);path.lineTo(17,11);path.lineTo(21,15);c.drawPath(path,p);break;
                case "file": path.moveTo(5,2);path.lineTo(14,2);path.lineTo(20,8);path.lineTo(20,22);path.lineTo(5,22);path.close();c.drawPath(path,p);c.drawLine(14,2,14,8,p);c.drawLine(14,8,20,8,p);c.drawLine(9,13,16,13,p);c.drawLine(9,17,16,17,p);break;
                case "audio": path.moveTo(9,18);path.lineTo(9,5);path.lineTo(20,3);path.lineTo(20,16);c.drawPath(path,p);c.drawOval(3,16,9,21,p);c.drawOval(14,14,20,19,p);break;
                case "poll": c.drawLine(4,21,4,12,p);c.drawLine(12,21,12,4,p);c.drawLine(20,21,20,8,p);break;
                case "todo": c.drawRoundRect(3,3,21,21,3,3,p);path.moveTo(7,12);path.lineTo(10,15);path.lineTo(17,8);c.drawPath(path,p);break;
                case "reply": path.moveTo(10,5);path.lineTo(3,11);path.lineTo(10,17);path.moveTo(3,11);path.cubicTo(17,8,21,12,21,20);c.drawPath(path,p);break;
                case "app": for(int y=4;y<20;y+=10)for(int x=4;x<20;x+=10)c.drawRoundRect(x,y,x+6,y+6,1,1,p);break;
                case "money": c.drawRoundRect(2,5,22,19,2,2,p);c.drawCircle(12,12,4,p);c.drawLine(5,12,6,12,p);c.drawLine(18,12,19,12,p);break;
                case "plus": c.drawLine(12,3,12,21,p);c.drawLine(3,12,21,12,p);break;
                case "more": p.setStyle(Paint.Style.FILL);c.drawCircle(5,12,1.5f,p);c.drawCircle(12,12,1.5f,p);c.drawCircle(19,12,1.5f,p);break;
                case "back": c.drawLine(8,12,15,5,p);c.drawLine(8,12,15,19,p);break;
                case "archive": c.drawRoundRect(3,4,21,9,1.5f,1.5f,p);path.moveTo(4.5f,9);path.lineTo(4.5f,19);path.quadTo(4.5f,20.5f,6,20.5f);path.lineTo(18,20.5f);path.quadTo(19.5f,20.5f,19.5f,19);path.lineTo(19.5f,9);c.drawPath(path,p);c.drawLine(10,13,14,13,p);break;
                case "key": c.drawCircle(8.5f,15.5f,4,p);c.drawLine(11.5f,12.5f,20,4,p);c.drawLine(16.5f,7.5f,19,10,p);c.drawLine(13.5f,10.5f,15.5f,12.5f,p);break;
                case "lock": c.drawRoundRect(5,10.5f,19,21,2.5f,2.5f,p);path.moveTo(8,10.5f);path.lineTo(8,7.5f);path.cubicTo(8,1.5f,16,1.5f,16,7.5f);path.lineTo(16,10.5f);c.drawPath(path,p);break;
                case "bubble": c.drawOval(3,3.5f,21,18.5f,p);path.moveTo(7.5f,17);path.lineTo(5.5f,21.5f);path.lineTo(11,18.4f);c.drawPath(path,p);break;
                case "notify": c.drawRoundRect(3,7,18,21,3.5f,3.5f,p);p.setStyle(Paint.Style.FILL);c.drawCircle(18,6,3.2f,p);break;
                case "storage": c.drawLine(8,20,8,4,p);c.drawLine(4,8,8,4,p);c.drawLine(12,8,8,4,p);c.drawLine(16,4,16,20,p);c.drawLine(12,16,16,20,p);c.drawLine(20,16,16,20,p);break;
                case "laptop": c.drawRoundRect(5,5,19,15.5f,1.8f,1.8f,p);c.drawLine(2,19.5f,22,19.5f,p);break;
                case "list": c.drawRoundRect(5,4,19,21,2.5f,2.5f,p);c.drawCircle(12,11,2.4f,p);path.moveTo(7.8f,18);path.quadTo(12,13.2f,16.2f,18);c.drawPath(path,p);c.drawLine(9,1.8f,15,1.8f,p);break;
                case "globe": c.drawCircle(12,12,9,p);c.drawOval(8,3,16,21,p);c.drawLine(3,12,21,12,p);break;
                case "battery": c.drawRoundRect(2,7,19,17,3,3,p);c.drawLine(21.5f,10,21.5f,14,p);p.setStyle(Paint.Style.FILL);c.drawRoundRect(4.5f,9.5f,11,14.5f,1,1,p);break;
                case "info": c.drawCircle(12,12,9,p);c.drawLine(12,11,12,17,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,7.5f,1.2f,p);break;
                case "star": path.moveTo(12,3);path.lineTo(14.6f,8.8f);path.lineTo(21,9.4f);path.lineTo(16.2f,13.6f);path.lineTo(17.6f,20);path.lineTo(12,16.8f);path.lineTo(6.4f,20);path.lineTo(7.8f,13.6f);path.lineTo(3,9.4f);path.lineTo(9.4f,8.8f);path.close();c.drawPath(path,p);break;
                case "bell": path.moveTo(6,17);path.lineTo(6,11);path.cubicTo(6,4,18,4,18,11);path.lineTo(18,17);path.lineTo(20,18.5f);path.lineTo(4,18.5f);path.close();c.drawPath(path,p);c.drawLine(10.5f,21.5f,13.5f,21.5f,p);break;
                case "palette": c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8,10,1.3f,p);c.drawCircle(12,7.5f,1.3f,p);c.drawCircle(16,10,1.3f,p);c.drawCircle(8.5f,15,1.3f,p);break;
                case "timer": c.drawCircle(12,13,8,p);c.drawLine(12,13,12,8.5f,p);c.drawLine(9.5f,2.5f,14.5f,2.5f,p);break;
                case "block": c.drawCircle(12,12,9,p);c.drawLine(5.7f,5.7f,18.3f,18.3f,p);break;
                case "phone": path.moveTo(5,3.5f);path.lineTo(9,3.5f);path.lineTo(10.5f,8);path.lineTo(8.5f,9.8f);path.cubicTo(9.5f,12.5f,11.5f,14.5f,14.2f,15.5f);path.lineTo(16,13.5f);path.lineTo(20.5f,15);path.lineTo(20.5f,19);path.quadTo(20.5f,20.5f,19,20.5f);path.cubicTo(10,20.5f,3.5f,14,3.5f,5);path.quadTo(3.5f,3.5f,5,3.5f);path.close();c.drawPath(path,p);break;
                case "video": c.drawRoundRect(2.5f,6.5f,16,17.5f,3,3,p);path.moveTo(16,10.5f);path.lineTo(21.5f,7.5f);path.lineTo(21.5f,16.5f);path.lineTo(16,13.5f);c.drawPath(path,p);break;
                case "sticker": path.moveTo(6,3);path.lineTo(18,3);path.quadTo(21,3,21,6);path.lineTo(21,14);path.lineTo(14,21);path.lineTo(6,21);path.quadTo(3,21,3,18);path.lineTo(3,6);path.quadTo(3,3,6,3);path.close();c.drawPath(path,p);path.rewind();path.moveTo(21,14);path.lineTo(17,14);path.quadTo(14,14,14,17);path.lineTo(14,21);c.drawPath(path,p);break;
                case "camera": c.drawRoundRect(2.5f,7,21.5f,20,3,3,p);c.drawCircle(12,13.5f,3.8f,p);c.drawLine(8,7,9.5f,4,p);c.drawLine(9.5f,4,14.5f,4,p);c.drawLine(14.5f,4,16,7,p);break;
                case "mic": c.drawRoundRect(9,2.5f,15,14,3,3,p);path.moveTo(5.5f,11);path.cubicTo(5.5f,19,18.5f,19,18.5f,11);c.drawPath(path,p);c.drawLine(12,18.5f,12,22,p);break;
                case "location": path.moveTo(12,21.5f);path.cubicTo(6,15,4.5f,12.5f,4.5f,9.5f);path.cubicTo(4.5f,5,8,2.5f,12,2.5f);path.cubicTo(16,2.5f,19.5f,5,19.5f,9.5f);path.cubicTo(19.5f,12.5f,18,15,12,21.5f);path.close();c.drawPath(path,p);c.drawCircle(12,9.5f,2.6f,p);break;
                case "contact": c.drawCircle(12,12,9.5f,p);c.drawCircle(12,9.5f,3.2f,p);path.moveTo(5.8f,18.5f);path.cubicTo(8,13.5f,16,13.5f,18.2f,18.5f);c.drawPath(path,p);break;
                case "event": c.drawRoundRect(3,4.5f,21,21,2.5f,2.5f,p);c.drawLine(3,9.5f,21,9.5f,p);c.drawLine(8,2.5f,8,6.5f,p);c.drawLine(16,2.5f,16,6.5f,p);break;
                case "camera_plain": c.drawCircle(12,12,8,p);break;
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
