package org.telegram.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.futoonpay.admin.NativePanelBridge;
import com.futoonpay.admin.NativePanelView;
import com.futoonpay.admin.NativeChannelBarView;
import com.futoonpay.admin.ChannelSheetView;
import com.futoonpay.admin.ChannelSheetHost;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.ChatActivityEnterView;
import com.futoonpay.admin.NativePanelOptionsBridge;
import com.futoonpay.admin.ExtensionOptions;
import com.futoonpay.admin.PanelOptions;
import com.futoonpay.admin.AppearanceOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.futoon.*;
import org.telegram.tgnet.TLRPC;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Telegram host for the fully native FUTOON Compose panel. No WebView or HTML is used. */
public final class FutoonPanel implements NotificationCenter.NotificationCenterDelegate {
    private static final int PICK_IMAGES=8042;
    private static final int PICK_OCR=8043,EXPORT_FILE=8044;
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private final ChatActivity host;
    private final int account;
    private final long owner;
    private final FrameLayout root;
    private final View scrim;
    private final FrameLayout ball;
    private final TextView ballText;
    private final ImageView ballImage;
    private Bitmap entryBitmap;
    private String entryPath = "";
    private long entryModified, settingsRevision = -1;
    private final FutoonStore store;
    private final FutoonDeliveryTracker deliveries;
    private final ArrayList<File> photos=new ArrayList<>();
    private static final class PendingImage { final MessageObject message; final int generation; final String order; PendingImage(MessageObject message,int generation,String order){this.message=message;this.generation=generation;this.order=order;} }
    private final Map<String,PendingImage> downloading=new HashMap<>();
    private NativePanelView panel;
    private NativeChannelBarView channelBar;
    private BottomSheet channelSheet;
    private ChannelSheetView channelSheetView;
    private FutoonApi api;
    private FutoonBusinessApi business;
    private JSONObject context=new JSONObject();
    private String initialText="",activeOrder="";
    private volatile String backendAddress;
    private String exportText="";
    private int pickerGeneration,exportGeneration=-1;
    private boolean fullscreen,expanded,keyboard,ballPositioned,choosing;
    private volatile boolean destroyed,paused;
    private int lastBottomInset=-1;
    private volatile int generation;
    private float downX,downY,startX,startY;
    private boolean dragged;
    private long dialogId,topicId,sourceMessage;

    public static boolean canUsePhoto(int account,MessageObject message){
        return message!=null&&message.currentAccount==account&&message.getId()>0&&message.messageOwner!=null&&!message.messageOwner.noforwards
                &&!DialogObject.isEncryptedDialog(message.getDialogId())&&!MessagesController.getInstance(account).isPeerNoForwards(message.getDialogId())
                &&message.messageOwner.media!=null&&(message.messageOwner.media.photo instanceof TLRPC.TL_photo
                ||message.getDocument()!=null&&message.getDocument().mime_type!=null&&message.getDocument().mime_type.startsWith("image/")&&!message.isSticker()&&!message.isAnimatedSticker())
                &&message.messageOwner.media.ttl_seconds==0&&!message.isSecretMedia();
    }

    public FutoonPanel(ChatActivity host,FrameLayout parent,int account,ChatActivityEnterView composer) {
        this.host=host;this.account=account;owner=UserConfig.getInstance(account).getClientUserId();store=new FutoonStore(account);backendAddress=store.address();deliveries=FutoonDeliveryTracker.get(account);
        Context c=parent.getContext();
        root=new FrameLayout(c);root.setClipChildren(true);root.setClipToPadding(true);parent.addView(root,new FrameLayout.LayoutParams(-1,-1));
        scrim=new View(c);scrim.setVisibility(View.GONE);scrim.setOnClickListener(v->collapse());root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        ball=new FrameLayout(c);ball.setContentDescription("打开富通原生悬浮面板");ball.setClipToOutline(true);
        ballText = new TextView(c); ballText.setText("富通"); ballText.setTextSize(14); ballText.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); ballText.setTextColor(Color.WHITE); ballText.setGravity(Gravity.CENTER);
        ballImage = new ImageView(c); ballImage.setScaleType(ImageView.ScaleType.CENTER_CROP); ball.addView(ballText,new FrameLayout.LayoutParams(-1,-1)); ball.addView(ballImage,new FrameLayout.LayoutParams(-1,-1));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff087a4b);bg.setCornerRadius(dp(24));ball.setBackground(bg);ball.setElevation(dp(3));
        root.addView(ball,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.LEFT));
        ball.setOnClickListener(v->open(""));
        ball.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ball.animate().cancel();downX=e.getRawX();downY=e.getRawY();startX=ball.getX();startY=ball.getY();dragged=false;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.hypot(dx,dy)>dp(6))dragged=true;if(dragged){root.requestDisallowInterceptTouchEvent(true);ball.setX(Math.max(dp(4),Math.min(root.getWidth()-ball.getWidth()-dp(4),startX+dx)));ball.setY(Math.max(dp(56),Math.min(root.getHeight()-ball.getHeight()-dp(80),startY+dy)));}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){root.requestDisallowInterceptTouchEvent(false);if(!dragged)ball.performClick();else snapBall();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){root.requestDisallowInterceptTouchEvent(false);if(dragged)snapBall();return true;}return true;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int height=b-t;keyboard=root.getRootView().getHeight()-height>dp(180);
            if(!ballPositioned&&r-l>0&&height>0){SharedPreferences prefs=preferences();ball.setX(prefs.getBoolean("right",true)?Math.max(dp(4),r-l-ball.getLayoutParams().width-dp(8)):dp(8));ball.setY(Math.max(dp(56),Math.min(height-ball.getLayoutParams().height-dp(80),height*prefs.getFloat("y",.45f))));ballPositioned=true;}
            if(expanded)resize();if(ball.getY()>height-dp(130))ball.setY(Math.max(dp(56),height-dp(130)));
        });
        NotificationCenter nc=NotificationCenter.getInstance(account);nc.addObserver(this,NotificationCenter.fileLoaded);nc.addObserver(this,NotificationCenter.fileLoadFailed);nc.addObserver(this,NotificationCenter.updateInterfaces);
        applyOptions();
        channelBar = new NativeChannelBarView(c, createBridge(), new ChannelSheetHost() {
            public void heightChanged(int pixels) { composer.setFutoonChannelBarHeight(pixels); }
            public void closeSheet() { if (channelSheet != null) channelSheet.dismiss(); }
            public void showSheet(ChannelSheetView view, String kind) {
                if (!valid() || paused || host.getParentActivity() == null || channelSheet != null) { view.forceClose(); channelBar.sheetDismissed(); return; }
                boolean hadKeyboard = composer.isKeyboardVisible();
                composer.hidePopup(false, false);
                channelSheetView = view;
                int height = (int)(AndroidUtilities.displaySize.y * (kind.equals("edit") ? .94f : kind.equals("orders") ? .64f : kind.equals("stats") ? .80f : .70f));
                view.setPanelHeight(height);
                FutoonThemeBridge.bind(view, view::setThemeColors, account);
                BottomSheet sheet = new BottomSheet(host.getParentActivity(), true) {
                    @Override public void dismiss() { if (view.requestClose()) super.dismiss(); }
                    @Override public void onBackPressed() { view.handleBack(); }
                };
                sheet.setCustomView(view); sheet.setCanDismissWithSwipe(!kind.equals("edit"));
                channelSheet = sheet;
                if (host.showDialog(sheet, ignored -> {
                    channelSheet = null; channelSheetView = null; channelBar.sheetDismissed();
                    if (hadKeyboard && valid() && !paused && host.isLastFragment()) AndroidUtilities.runOnUIThread(composer::openKeyboard, 120);
                }) == null) { view.forceClose(); sheet.dismiss(); channelBar.sheetDismissed(); channelSheet = null; channelSheetView = null; return; }
                view.onShown();
            }
        });
        composer.setFutoonChannelBar(channelBar);
        FutoonThemeBridge.bind(channelBar, channelBar::setThemeColors, account);
    }

    private int dp(float n){return AndroidUtilities.dp(n);}
    private SharedPreferences preferences(){return root.getContext().getSharedPreferences("futoon_panel_ui_"+owner,Context.MODE_PRIVATE);}
    private void snapBall(){boolean right=ball.getX()+ball.getWidth()/2f>=root.getWidth()/2f;ball.animate().x(right?Math.max(dp(4),root.getWidth()-ball.getWidth()-dp(8)):dp(8)).setDuration(140).start();preferences().edit().putBoolean("right",right).putFloat("y",root.getHeight()>0?ball.getY()/root.getHeight():.45f).apply();}
    private ExtensionOptions options() { return FutoonChatPreferences.get(account).options(); }
    private String stateKey() { return "navigation:" + backendAddress + ":" + dialogId + ":" + topicId; }
    private void savePanelState() { if (panel != null && options().getPanel().getRemember()) preferences().edit().putString(stateKey(),panel.navigationState()).putBoolean(stateKey()+":full",fullscreen).apply(); }
    private void releasePanel() { if (panel != null) { savePanelState(); panel.close(); root.removeView(panel); panel = null; } }
    private void applyOptions() {
        if(channelBar!=null){channelBar.reloadSettings();FutoonThemeBridge.refresh(channelBar);}
        PanelOptions p = options().getPanel(); AppearanceOptions a = options().getAppearance();
        settingsRevision = FutoonChatPreferences.get(account).revision();
        ViewGroup.LayoutParams bp = ball.getLayoutParams(); bp.width = bp.height = dp(p.getEntrySize()); ball.setLayoutParams(bp); ball.setAlpha(p.getEntryOpacity()/100f);
        GradientDrawable entry = new GradientDrawable(); entry.setColor(a.getAccent().isEmpty() ? 0xff087a4b : Color.parseColor(a.getAccent())); entry.setCornerRadius(dp(p.getEntryRadius())); ball.setBackground(entry);
        File expected = new File(root.getContext().getFilesDir(), "futoon-entry-"+owner+".png");
        String image = p.getImage().equals(expected.getAbsolutePath()) && expected.isFile() ? p.getImage() : "";
        long modified = image.isEmpty() ? 0 : expected.lastModified();
        if (!image.equals(entryPath) || modified != entryModified) {
            ballImage.setImageDrawable(null); if (entryBitmap != null) { entryBitmap.recycle(); entryBitmap = null; }
            entryPath = image; entryModified = modified;
            if (!image.isEmpty()) entryBitmap = ImageLoader.loadBitmap(image,null,512,512,true);
            if (entryBitmap != null) ballImage.setImageBitmap(entryBitmap);
        }
        ballImage.setVisibility(entryBitmap == null ? View.GONE : View.VISIBLE); ballText.setVisibility(entryBitmap == null ? View.VISIBLE : View.GONE);
        ballPositioned = false;
        scrim.setBackgroundColor((Math.round(a.getDim()*255f/100f)<<24)); scrim.setVisibility(expanded && a.getDim()>0 ? View.VISIBLE : View.GONE);
        if (panel != null) { panel.setOptions(FutoonChatPreferences.get(account).raw()); panel.setAlpha(a.getOpacity()/100f); GradientDrawable bg = new GradientDrawable(); bg.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite)); bg.setCornerRadius(dp(a.getRadius())); panel.setBackground(bg); FutoonThemeBridge.refresh(panel); }
        root.requestLayout(); if (expanded) resize();
    }
    private boolean valid(){return !destroyed&&owner==UserConfig.getInstance(account).getClientUserId()&&UserConfig.getInstance(account).isClientActivated();}
    private void toast(String s){Toast.makeText(root.getContext(),s,Toast.LENGTH_LONG).show();}

    /** Old backend state must never be re-keyed as a new backend's live panel memory. */
    private void discardBackendPanel(NativePanelView previous) {
        if (previous == null) return;
        previous.setOptions("{\"extensions\":{\"panel\":{\"remember\":false}}}");
        previous.close();
        root.removeView(previous);
        if (panel == previous) panel = null;
    }

    private void changeBackendScope(String next, boolean deferPanelReset) {
        if (backendAddress.equals(next)) return;
        savePanelState();
        final String previousBackend = backendAddress;
        final NativePanelView previous = panel;
        final boolean reopen = expanded;
        generation++;
        pickerGeneration = -1;
        exportGeneration = -1;
        photos.clear();
        downloading.clear();
        activeOrder = "";
        initialText = "";
        sourceMessage = 0;
        exportText = "";
        FutoonStore.Draft draft = FutoonStore.draft(account);
        if (draft != null && draft.address.equals(previousBackend)) FutoonStore.clearDraft(account);
        backendAddress = next;
        api = null;
        business = null;
        if (previous != null) { previous.onHidden(); previous.setVisibility(View.GONE); }
        Runnable reset = () -> {
            discardBackendPanel(previous);
            if (!valid() || !backendAddress.equals(store.address())) return;
            if (channelBar != null) channelBar.reloadSettings();
            if (reopen && expanded && !paused) {
                ensurePanel();
                panel.setVisibility(View.VISIBLE);
                panel.onShown();
                resize();
            }
        };
        // EmbeddedEnvironment finishes activating its new repository after configure() returns.
        // Dispose that old environment on the next UI turn, not from inside its configure call.
        if (deferPanelReset) AndroidUtilities.runOnUIThread(reset); else reset.run();
    }

    private NativePanelOptionsBridge createBridge() { return new NativePanelOptionsBridge(){
            public String settingsJson() { return FutoonChatPreferences.get(account).raw(); }
            public String address(){return store.address();}
            public String username(){return store.username();}
            public String accountScope(){return account+":"+owner;}
            public com.futoonpay.admin.PanelSession session(){return FutoonNativeBridge.session(account,store);}
            public void pickEvidenceFromChat(){AndroidUtilities.runOnUIThread(()->{collapse();toast("长按聊天图片，选择「作为富通凭证」");});}
            public String[] evidencePaths(){return photos.stream().map(File::getAbsolutePath).toArray(String[]::new);}
            public android.graphics.Bitmap evidencePreview(String path){if(!valid()||!path.startsWith(root.getContext().getCacheDir().getAbsolutePath()+File.separator))return null;return ImageLoader.loadBitmap(path,null,320,320,true);}
            public void removeEvidence(int index){final int gen=generation;final String backend=store.address();AndroidUtilities.runOnUIThread(()->{if(valid()&&gen==generation&&backend.equals(store.address())&&index>=0&&index<photos.size()){photos.remove(index);syncDraft();}});}
            public void editEvidence(int index){final int gen=generation;final String backend=store.address();AndroidUtilities.runOnUIThread(()->{if(!valid()||gen!=generation||!backend.equals(store.address())||index<0||index>=photos.size())return;File original=photos.get(index);new FutoonImageEditor(host.getParentActivity(),original,file->{if(valid()&&gen==generation&&backend.equals(store.address())&&index<photos.size()&&photos.get(index).equals(original)){photos.set(index,file);syncDraft();}},()->{}).show();});}
            public boolean hasSession(){return valid() && com.futoonpay.admin.ChannelSessionStatus.hasSession(FutoonNativeBridge.session(account,store));}
            public void configure(String address,String username)throws Exception{
                final String normalized=FutoonApi.normalizeAddress(address);
                final int gen=generation; final String backend=store.address();
                onUi(gen,backend,()->{
                    synchronized(FutoonPanel.this){
                        // Construct first, so an invalid endpoint leaves the current scope intact.
                        FutoonApi nextApi=new FutoonApi(normalized,store.session(normalized));
                        changeBackendScope(normalized,true);
                        store.address(normalized);store.username(username);
                        api=nextApi;business=new FutoonBusinessApi(api,FutoonNativeBridge.create(account,store,normalized));
                    }
                    if(channelBar!=null)channelBar.reloadSettings();
                    return "{}";
                });
            }
            public byte[] captcha()throws Exception{synchronized(FutoonPanel.this){ensureClient();return api.captcha();}}
            public String login(String username,String password,String captcha)throws Exception{synchronized(FutoonPanel.this){ensureClient();String message=api.login(username,password,captcha);business=null;store.username(username);AndroidUtilities.runOnUIThread(()->{if(channelBar!=null)channelBar.reloadSettings();});return new JSONObject().put("success",true).put("code",0).put("msg",message).toString();}}
            public void logout()throws Exception{synchronized(FutoonPanel.this){ensureClient();api.logout();business=null;AndroidUtilities.runOnUIThread(()->{if(channelBar!=null)channelBar.reloadSettings();});}}
            public String call(String operation,String params)throws Exception{return nativeCall(operation,new JSONObject(params));}
            public void collapse(){AndroidUtilities.runOnUIThread(FutoonPanel.this::collapse);}
            public void copy(String text){AndroidUtilities.runOnUIThread(()->{((ClipboardManager)root.getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("富通",text));toast("已复制");});}
            public void selectEvidence(String order,String caption){final int gen=generation;final String backend=store.address();AndroidUtilities.runOnUIThread(()->{if(!valid()||gen!=generation||!backend.equals(store.address()))return;try{prepareEvidence(order,caption);}catch(Exception error){toast("订单凭证初始化失败："+error.getMessage());}});}
            public void openGallery(){final int gen=generation;final String backend=store.address();AndroidUtilities.runOnUIThread(()->{if(valid()&&gen==generation&&backend.equals(store.address()))FutoonPanel.this.openGallery();});}
            public void sendEvidence(String order,String caption){final int gen=generation;final String backend=store.address();AndroidUtilities.runOnUIThread(()->{if(valid()&&gen==generation&&backend.equals(store.address()))FutoonPanel.this.sendEvidence(order,caption);});}
        }; }

    private void ensurePanel(){
        if (!backendAddress.equals(store.address())) changeBackendScope(store.address(),false);
        if(panel!=null)return;
        restoreDraftFiles();
        panel=new NativePanelView(root.getContext(),createBridge(), dialogId+":"+topicId);
        fullscreen = options().getPanel().getRemember() ? preferences().getBoolean(stateKey()+":full",options().getPanel().getFull()) : options().getPanel().getFull();
        if (options().getPanel().getRemember()) panel.restoreNavigationState(preferences().getString(stateKey(),""));
        panel.setFullscreenHandler(()->{fullscreen=!fullscreen;resize();});
        FutoonThemeBridge.bind(panel,panel::setThemeColors,account);
        panel.setVisibility(View.GONE);panel.setElevation(dp(12));
        GradientDrawable panelBg=new GradientDrawable();panelBg.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));panelBg.setCornerRadius(dp(18));panel.setBackground(panelBg);panel.setClipToOutline(true);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(600),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.setMargins(dp(8),0,dp(8),dp(8));root.addView(panel,lp);
        applyOptions();applyContext();restoreCurrentEvidence();panel.updateEvidenceCount(photos.size());if(!initialText.isEmpty()){panel.recognize(initialText);initialText="";}
    }
    private void restoreCurrentEvidence() {
        if (panel == null || panel.currentEvidence().isEmpty()) return;
        try {
            JSONObject order = new JSONObject(panel.currentEvidence()); String trade = order.optString("trade_no");
            if (trade.isEmpty()) return;
            JSONObject local; synchronized(store) { String raw = store.readLocal(store.address()); local = raw.isEmpty() ? new JSONObject() : new JSONObject(raw); }
            JSONArray files = local.optJSONObject("_nativeProofs") == null ? null : local.getJSONObject("_nativeProofs").optJSONArray(trade);
            prepareEvidence(order.toString(), order.optString("_caption")); photos.clear();
            if (files != null) for (int i=0; i<files.length() && photos.size()<10; i++) { File file = new File(files.getString(i)); if (file.isFile() && file.getCanonicalPath().startsWith(root.getContext().getCacheDir().getCanonicalPath()+File.separator)) photos.add(file); }
            syncDraft();
        } catch (Exception error) { toast("凭证页面已恢复，请重新选择失效的图片"); }
    }
    private void ensureClient()throws Exception{
        final String address=backendAddress;
        if(!valid()||!address.equals(store.address()))throw new IOException("账号或后台已变化，请重新打开面板");
        if(address.isEmpty())throw new FutoonApi.SessionExpired();
        if(api==null)api=new FutoonApi(address,store.session(address));
        if(business==null)business=new FutoonBusinessApi(api,FutoonNativeBridge.create(account,store,address));
    }
    private void applyContext(){if(panel!=null)panel.updateChatContext(context.optString("label"),context.optString("tgid"),context.optString("source"),account+":"+dialogId+":"+topicId+":"+context.optString("tgid"),dialogId,topicId,context.optString("ticket"),context.optString("role"));}

    public boolean isView(View view){return view==root;}
    public void onBottomInsetChanged(int inset){if(lastBottomInset==inset)return;lastBottomInset=inset;root.requestLayout();}
    public void bind(FutoonIdentity identity,String label,long dialog,long topic){if(dialogId==dialog&&topicId==topic&&context.optString("tgid").equals(identity==null?"":identity.telegramId)){if(channelBar!=null)channelBar.bindChat(label,dialog,topic,identity==null?"":identity.telegramId,identity==null?"":identity.source);applyContext();return;}boolean reopen=expanded;if(dialogId!=dialog||topicId!=topic)releasePanel();dialogId=dialog;topicId=topic;generation++;photos.clear();activeOrder="";try{context=new JSONObject().put("label",label).put("dialog",""+dialog).put("topic",""+topic).put("tgid",identity==null?"":identity.telegramId).put("source",identity==null?"未识别商户":identity.source).put("ticket",identity==null?"":identity.ticket).put("role",identity==null?"":identity.role);}catch(Exception ignored){}if(channelBar!=null)channelBar.bindChat(label,dialog,topic,identity==null?"":identity.telegramId,identity==null?"":identity.source);if(reopen){ensurePanel();panel.setVisibility(View.VISIBLE);panel.onShown();}applyContext();if(panel!=null)panel.updateEvidenceCount(photos.size());}
    public void open(String text){if(destroyed)return;host.bindFutoonContext();ensurePanel();expanded=true;paused=false;ball.setVisibility(View.GONE);applyOptions();panel.setVisibility(View.VISIBLE);panel.onShown();resize();root.bringToFront();if(text!=null&&!text.isEmpty())panel.recognizeText(text,dialogId,topicId,sourceMessage,false);}
    public void recognizeSelected(String text,long messageId){sourceMessage=Math.max(0,messageId);open(text);}
    public void recognizeMessage(MessageObject message){if(message==null||message.messageOwner==null)return;ArrayList<MessageObject> messages=new ArrayList<>();messages.add(message);if(message.replyMessageObject!=null)messages.add(message.replyMessageObject);recognizeMessages(messages);}
    public void recognizeMessages(Collection<MessageObject> messages){
        if(!valid())return;
        host.bindFutoonContext();StringBuilder text=new StringBuilder();JSONObject pointers=new JSONObject();Set<String> ambiguous=new HashSet<>();sourceMessage=0;
        for(MessageObject message:messages)if(message!=null&&message.messageOwner!=null&&message.messageOwner.message!=null){
            String raw=message.messageOwner.message;text.append(raw).append('\n');
            try{for(FutoonQuery item:FutoonQuery.extract(raw)){String key=item.kind.name()+":"+item.value;if(ambiguous.contains(key))continue;JSONObject previous=pointers.optJSONObject(key);if(previous!=null&&previous.optLong("message")!=message.getId()){pointers.remove(key);ambiguous.add(key);}else pointers.put(key,new JSONObject().put("dialog",dialogId).put("topic",topicId).put("message",message.getId()));}}catch(Exception ignored){}
        }
        open("");panel.recognizeMessageBatch(text.toString(),pointers.toString());
    }
    public void collapse(){savePanelState();if(panel!=null){AndroidUtilities.hideKeyboard(panel);panel.onHidden();panel.setVisibility(View.GONE);}expanded=false;scrim.setVisibility(View.GONE);ball.setVisibility(View.VISIBLE);if(!options().getPanel().getRemember())releasePanel();}
    private void resize(){if(panel==null)return;panel.setWindowState(fullscreen,keyboard);int available=root.getHeight();FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();int h=FutoonPanelLayout.sheetHeight(available,dp(8),dp(240),fullscreen,keyboard,options().getPanel().getHeight());int width=root.getWidth()>dp(720)?dp(680):FrameLayout.LayoutParams.MATCH_PARENT;if(p.height!=h||p.width!=width){p.height=h;p.width=width;panel.setLayoutParams(p);}}
    public boolean back(boolean invoked){if(!expanded)return false;if(invoked){if(keyboard)AndroidUtilities.hideKeyboard(panel);else if(panel==null||!panel.handleBack())collapse();}return true;}
    public void resume(){paused=false;if(!backendAddress.equals(store.address()))changeBackendScope(store.address(),false);if(channelBar!=null){host.bindFutoonContext();channelBar.onShown();}applyOptions();if(expanded&&panel!=null)panel.onShown();}
    public void pause(){paused=true;if(channelBar!=null)channelBar.onHidden();savePanelState();if(panel!=null)panel.onHidden();}
    public void destroy(){if(destroyed)return;destroyed=true;if(channelBar!=null){channelBar.close();channelBar=null;}generation++;NotificationCenter nc=NotificationCenter.getInstance(account);nc.removeObserver(this,NotificationCenter.fileLoaded);nc.removeObserver(this,NotificationCenter.fileLoadFailed);nc.removeObserver(this,NotificationCenter.updateInterfaces);releasePanel();ballImage.setImageDrawable(null);if(entryBitmap!=null){entryBitmap.recycle();entryBitmap=null;}if(root.getParent() instanceof ViewGroup)((ViewGroup)root.getParent()).removeView(root);}

    private void prepareEvidence(String raw,String caption)throws Exception{
        if(!valid()||backendAddress.isEmpty()||!backendAddress.equals(store.address()))throw new IOException("账号或后台已变化，请重新选择订单");
        JSONObject order=new JSONObject(raw);
        String next=order.optString("trade_no");
        if(next.isEmpty())throw new IOException("缺少订单号");
        FutoonApi.Order selected=new FutoonApi.Order(order);
        JSONObject source=order.optJSONObject("_source");
        if(!activeOrder.isEmpty()&&!activeOrder.equals(next)){generation++;photos.clear();downloading.clear();pickerGeneration=-1;}
        activeOrder=next;
        FutoonStore.setDraft(account,backendAddress,selected,caption,source==null?dialogId:source.optLong("dialog",dialogId));
        FutoonStore.Draft draft=FutoonStore.draft(account);
        draft.sourceMessage=source==null?sourceMessage:source.optLong("message");
        draft.sourceTopic=source==null?topicId:source.optLong("topic");
        syncDraft();
    }
    private void openGallery(){openImagePicker(false);}
    private void openImagePicker(boolean ocr){if(!valid()||!backendAddress.equals(store.address())){toast("账号或后台已变化，请重新打开面板");return;}if(choosing){toast("已有图片选择正在进行");return;}choosing=true;pickerGeneration=generation;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,!ocr);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{host.startActivityForResult(intent,ocr?PICK_OCR:PICK_IMAGES);}catch(Exception e){choosing=false;toast("无法打开图片选择器");}}
    private void sendEvidence(String raw,String caption){try{prepareEvidence(raw,caption);if(photos.isEmpty())throw new IOException("请先选择凭证图片");ArrayList<String> paths=new ArrayList<>();for(File f:photos){if(!f.isFile())throw new IOException("凭证文件已失效");paths.add(f.getAbsolutePath());}if(!(host.getParentActivity() instanceof LaunchActivity))throw new IOException("无法打开发送预览");((LaunchActivity)host.getParentActivity()).shareFutoonEvidence(account,paths,caption,activeOrder);collapse();}catch(Exception e){toast(e.getMessage());}}
    public void addChatPhoto(MessageObject message){restoreDraftFiles();if(!canUsePhoto(account,message)){toast("这张图片受保护或不可用");return;}if(photos.size()>=10){toast("每次最多选择 10 张凭证");return;}File f=message.messageOwner.attachPath==null?null:new File(message.messageOwner.attachPath);if(f==null||!f.isFile())f=FileLoader.getInstance(account).getPathToMessage(message.messageOwner);if(f!=null&&f.isFile()){appendPhoto(f);return;}String name;if(message.getDocument()!=null){name=FileLoader.getAttachFileName(message.getDocument());downloading.put(name,new PendingImage(message,generation,activeOrder));FileLoader.getInstance(account).loadFile(message.getDocument(),message,FileLoader.PRIORITY_HIGH,0);}else{TLRPC.PhotoSize size=FileLoader.getClosestPhotoSizeWithSize(message.messageOwner.media.photo.sizes,AndroidUtilities.getPhotoSize());if(size==null){toast("图片不可用");return;}name=FileLoader.getAttachFileName(size);downloading.put(name,new PendingImage(message,generation,activeOrder));FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(size,message.messageOwner.media.photo),message,"jpg",FileLoader.PRIORITY_HIGH,0);}toast("正在下载凭证图片");}
    private void appendPhoto(File source){if(!valid())return;if(photos.size()>=10){toast("每次最多选择 10 张凭证");return;}if(source.length()>25*1024*1024){toast("请选择 25 MB 以内的图片");return;}if(source.getAbsolutePath().startsWith(root.getContext().getCacheDir().getAbsolutePath()+File.separator)){if(!photos.contains(source))photos.add(source);syncDraft();open("");return;}final int gen=generation;WORK.execute(()->{try{File dir=new File(root.getContext().getCacheDir(),"futoon-evidence");if(!dir.exists())dir.mkdirs();File copy=File.createTempFile("proof-",".jpg",dir);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(copy)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}AndroidUtilities.runOnUIThread(()->{if(gen==generation)appendPhoto(copy);});}catch(Exception e){AndroidUtilities.runOnUIThread(()->toast("凭证缓存失败："+e.getMessage()));}});}
    private void restoreDraftFiles(){FutoonStore.Draft draft=FutoonStore.draft(account);if(draft==null||!draft.address.equals(store.address())||draft.sourceDialog!=dialogId||draft.sourceTopic!=topicId||!activeOrder.isEmpty())return;activeOrder=draft.order.id();sourceMessage=draft.sourceMessage;for(String path:draft.imagePaths){File file=new File(path);if(file.isFile()&&path.startsWith(root.getContext().getCacheDir().getAbsolutePath()+File.separator)&&!photos.contains(file))photos.add(file);}}
    private void syncDraft(){
        FutoonStore.Draft draft=FutoonStore.draft(account);
        if(draft!=null&&draft.address.equals(backendAddress)&&backendAddress.equals(store.address())&&draft.order.id().equals(activeOrder)){draft.imagePaths.clear();for(File f:photos)draft.imagePaths.add(f.getAbsolutePath());
            final String backend=draft.address,trade=draft.order.id();final JSONArray paths=new JSONArray(draft.imagePaths);
            WORK.execute(()->{try{synchronized(store){String saved=store.readLocal(backend);JSONObject local=saved.isEmpty()?new JSONObject():new JSONObject(saved);JSONObject proofs=local.optJSONObject("_nativeProofs");if(proofs==null)proofs=new JSONObject();proofs.put(trade,paths);local.put("_nativeProofs",proofs);store.writeLocal(backend,local.toString());}}catch(Exception e){AndroidUtilities.runOnUIThread(()->toast("凭证草稿保存失败："+e.getMessage()));}});
        }
        if(panel!=null)panel.updateEvidenceCount(photos.size());
    }
    private String nativeCall(String operation,JSONObject params)throws Exception{
        if(!valid()||!backendAddress.equals(store.address()))throw new IOException("Telegram 账号或后台已变化，请重新打开面板");
        final String backend=store.address();final int requestGeneration=generation;
        if(operation.equals("chat.tools.read"))return FutoonChatPreferences.get(account).raw();
        if(operation.equals("ui.channel.refresh")) {
            AndroidUtilities.runOnUIThread(()->{if(valid() && requestGeneration==generation && backend.equals(store.address()) && channelBar!=null)channelBar.reloadSettings();});
            return "{}";
        }
        if(operation.equals("ui.channel.order") || operation.equals("ui.channel.open")) {
            if(params.optLong("dialog")!=dialogId || params.optLong("topic")!=topicId) throw new IOException("会话已变化");
            AndroidUtilities.runOnUIThread(()->{
                if(!valid() || paused || requestGeneration!=generation || !backend.equals(store.address()) || params.optLong("dialog")!=dialogId || params.optLong("topic")!=topicId) return;
                if(operation.equals("ui.channel.order")) { if(channelBar!=null)channelBar.fromOrder(params.optString("channel")); }
                else { try { open(""); String kind=params.optString("kind"),order=params.optString("order"); if(kind.equals("evidence"))prepareEvidence(order,""); panel.openShortcutTarget(kind,params.optString("id"),order); } catch(Exception error) { toast("打开业务页面失败："+error.getMessage()); } }
            },operation.equals("ui.channel.open") ? 180 : 0);
            return "{}";
        }
        if(operation.equals("ui.draft"))return onUi(requestGeneration,backend,()->{
            String key=account+":"+dialogId+":"+topicId+":"+context.optString("tgid");
            if(paused||!key.equals(params.optString("context"))||dialogId!=params.optLong("dialog")||topicId!=params.optLong("topic"))throw new IOException("当前会话已变化，请返回原会话填入文案");
            String text=params.optString("text");
            if(text.isEmpty()||text.length()>4096||!host.futoonInsertDraft(text,dialogId,topicId))throw new IOException("当前输入框不可用或内容过长，请核对后重试");
            collapse();return new JSONObject().put("inserted",true).toString();
        });
        if(operation.equals("recognize")){
            JSONArray items=new JSONArray();for(FutoonQuery query:FutoonQuery.extract(params.optString("text")))items.put(new JSONObject().put("kind",query.kind.name()).put("value",query.value).put("label",FutoonQuery.label(query.kind)));return items.toString();
        }
        if(operation.equals("local.read")){synchronized(store){String saved=store.readLocal(backend);JSONObject local=saved.isEmpty()?new JSONObject():new JSONObject(saved);local.remove("_nativeProofs");return local.toString();}}
        if(operation.equals("delivery.read"))return deliveries.read(backend).toString();
        if(operation.equals("local.write")){synchronized(store){String previous=store.readLocal(backend);JSONObject old=previous.isEmpty()?new JSONObject():new JSONObject(previous);JSONObject proofs=old.optJSONObject("_nativeProofs");if(proofs!=null)params.put("_nativeProofs",proofs);store.writeLocal(backend,params.toString());return "{}";}}
        if(operation.equals("ocr.gallery"))return onUi(requestGeneration,backend,()->{openImagePicker(true);return "{}";});
        if(operation.equals("export"))return onUi(requestGeneration,backend,()->{exportGeneration=generation;exportText=params.optString("text");String name=params.optString("name","futoon.csv");if(!name.matches("[A-Za-z0-9_.-]{1,100}"))name="futoon.csv";host.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),EXPORT_FILE);return "{}";});
        if(operation.equals("ui.source"))return onUi(requestGeneration,backend,()->{long message=params.optLong("message"),dialog=params.optLong("dialog");if(message<=0||dialog==0)throw new IOException("这笔订单没有可定位的原聊天消息");collapse();host.futoonJumpToSource(dialog,params.optLong("topic"),message);return "{}";});
        if(operation.equals("evidence.draft"))return onUi(requestGeneration,backend,()->{JSONObject order=params.getJSONObject("order");prepareEvidence(order.toString(),params.optString("caption"));return "{}";});
        if(operation.equals("evidence.restore")){
            final JSONObject order=params.getJSONObject("order");final String trade=order.getString("trade_no"),caption=params.optString("caption");final int gen=generation;JSONArray paths;
            synchronized(store){String saved=store.readLocal(store.address());JSONObject local=saved.isEmpty()?new JSONObject():new JSONObject(saved);JSONObject proofs=local.optJSONObject("_nativeProofs");paths=proofs==null?null:proofs.optJSONArray(trade);}
            final ArrayList<File> restored=new ArrayList<>();String owned=root.getContext().getCacheDir().getCanonicalPath()+File.separator;
            if(paths!=null)for(int i=0;i<paths.length()&&restored.size()<10;i++){File file=new File(paths.getString(i));if(file.isFile()&&file.getCanonicalPath().startsWith(owned))restored.add(file);}
            return onUi(requestGeneration,backend,()->{if(gen!=generation||!valid())throw new IOException("会话已变化，请重新恢复草稿");photos.clear();photos.addAll(restored);activeOrder=trade;prepareEvidence(order.toString(),caption);ensurePanel();panel.adoptEvidence(order.toString(),caption);return new JSONObject().put("count",restored.size()).toString();});
        }
        synchronized(this){
            // A queued call may have captured the previous scope before configure acquired this lock.
            if(!valid()||requestGeneration!=generation||!backend.equals(backendAddress)||!backend.equals(store.address()))throw new IOException("账号、后台或会话已变化，请重新操作");
            ensureClient();api.refreshSession();return business.call(operation,params).toString();
        }
    }
    private String onUi(int gen,String backend,Callable<String> action)throws Exception{
        Callable<String> guarded=()->{if(!valid()||gen!=generation||!backend.equals(backendAddress)||!backend.equals(store.address()))throw new IOException("账号、后台或会话已变化，请重新操作");return action.call();};
        if(Looper.myLooper()==Looper.getMainLooper())return guarded.call();
        FutureTask<String> task=new FutureTask<>(guarded);AndroidUtilities.runOnUIThread(task);
        try{return task.get(15,TimeUnit.SECONDS);}catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}
    }
    private void readOcr(File file,int gen){
        try{
            com.google.mlkit.vision.text.TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            recognizer.process(InputImage.fromFilePath(root.getContext(),Uri.fromFile(file))).addOnSuccessListener(text->{
                String raw=text.getText();recognizer.close();
                WORK.execute(()->{String qr=decodeQr(file),result=raw+(qr.isEmpty()?"":"\n二维码内容："+qr);file.delete();AndroidUtilities.runOnUIThread(()->{if(valid()&&gen==generation&&panel!=null)panel.recognizeText(result,dialogId,topicId,0,true);});});
            }).addOnFailureListener(error->{recognizer.close();file.delete();if(valid()&&gen==generation&&panel!=null)panel.recognitionError("图片识别失败："+error.getMessage());});
        }catch(Exception error){file.delete();if(panel!=null)panel.recognitionError("图片识别失败："+error.getMessage());}
    }
    private String decodeQr(File file){
        Bitmap bitmap=null;try{bitmap=ImageLoader.loadBitmap(file.getPath(),null,1600,1600,true);if(bitmap==null)return "";int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels)))).getText();}catch(Exception ignored){return "";}finally{if(bitmap!=null)bitmap.recycle();}
    }
    public boolean activityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==EXPORT_FILE){
            if(exportGeneration!=generation||!valid()||!backendAddress.equals(store.address()))return true;
            exportGeneration=-1;
            if(resultCode==Activity.RESULT_OK&&data!=null&&data.getData()!=null){final Uri uri=data.getData();final String text=exportText;WORK.execute(()->{try(OutputStream out=root.getContext().getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法写入文件");out.write(("\uFEFF"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));AndroidUtilities.runOnUIThread(()->toast("导出完成"));}catch(Exception e){AndroidUtilities.runOnUIThread(()->toast("导出失败："+e.getMessage()));}});}return true;
        }
        if(requestCode!=PICK_IMAGES&&requestCode!=PICK_OCR)return false;
        choosing=false;if(resultCode!=Activity.RESULT_OK||data==null)return true;
        if(pickerGeneration!=generation||!valid()||!backendAddress.equals(store.address())){toast("会话已变化，请重新选择图片");return true;}
        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount()&&i<10;i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
        final int gen=generation;final boolean ocr=requestCode==PICK_OCR;
        WORK.execute(()->{ArrayList<File> files=new ArrayList<>();try{for(Uri uri:uris)files.add(copyImage(uri));AndroidUtilities.runOnUIThread(()->{if(gen!=generation||!valid())return;if(ocr){if(!files.isEmpty())readOcr(files.get(0),gen);}else{for(File file:files)if(photos.size()<10)photos.add(file);syncDraft();}});}catch(Exception e){AndroidUtilities.runOnUIThread(()->{toast("读取图片失败："+e.getMessage());if(ocr&&panel!=null)panel.recognitionError(e.getMessage());});}});return true;
    }
    private File copyImage(Uri uri)throws Exception{File dir=new File(root.getContext().getCacheDir(),"futoon-evidence");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建缓存");File f=File.createTempFile("proof-",".jpg",dir);boolean ok=false;try(InputStream in=root.getContext().getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("图片不可读");byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>25*1024*1024)throw new IOException("图片超过 25 MB");out.write(buf,0,n);}android.graphics.BitmapFactory.Options dimensions=new android.graphics.BitmapFactory.Options();dimensions.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),dimensions);if(dimensions.outWidth<=0||dimensions.outHeight<=0)throw new IOException("所选文件不是有效图片");ok=true;return f;}finally{if(!ok)f.delete();}}
    @Override public void didReceivedNotification(int event,int eventAccount,Object... args){if(!valid()||eventAccount!=account)return;if(event==NotificationCenter.updateInterfaces&&settingsRevision!=FutoonChatPreferences.get(account).revision()){applyOptions();return;}if((event==NotificationCenter.fileLoaded||event==NotificationCenter.fileLoadFailed)&&args.length>0){PendingImage pending=downloading.remove(String.valueOf(args[0]));if(pending!=null&&pending.generation==generation&&pending.order.equals(activeOrder)){if(event==NotificationCenter.fileLoaded&&!paused)addChatPhoto(pending.message);else toast(event==NotificationCenter.fileLoaded?"图片已下载，请重新点选添加凭证":"凭证图片下载失败");}}}
}
