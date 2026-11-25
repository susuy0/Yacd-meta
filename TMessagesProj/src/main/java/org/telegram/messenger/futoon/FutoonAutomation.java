package org.telegram.messenger.futoon;

import android.content.Context;
import android.content.SharedPreferences;
import com.futoonpay.admin.GroupAutomationRule;
import java.util.*;
import org.json.JSONArray;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;

/** Per-account message automation. It never scans history or synthesizes screen touches. */
public final class FutoonAutomation implements NotificationCenter.NotificationCenterDelegate {
    private static final HashMap<Integer, FutoonAutomation> instances = new HashMap<>();
    private static final HashSet<Integer> starting = new HashSet<>();
    private final int account;
    private final long owner;
    private final SharedPreferences preferences;
    private final long started = System.currentTimeMillis()/1000;
    private final LinkedHashSet<String> completed = new LinkedHashSet<>();
    private final LinkedHashMap<String, MessageObject> latest = new LinkedHashMap<>();
    private final HashMap<String, Runnable> pending = new HashMap<>();
    private final HashMap<String, Long> ruleDue = new HashMap<>();
    private long nextDue;
    private boolean stopped;
    private List<GroupAutomationRule> rules = Collections.emptyList();
    public static synchronized FutoonAutomation get(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        FutoonAutomation result = instances.get(account);
        if (result != null && (result.owner != owner || result.stopped)) { result.stop(); instances.remove(account); result = null; }
        if (result == null) { result = new FutoonAutomation(account, owner); instances.put(account, result); result.reload(); }
        return result;
    }
    public static void ensureStarted(int account) {
        synchronized(FutoonAutomation.class) { FutoonAutomation current=instances.get(account); long owner=UserConfig.getInstance(account).getClientUserId(); if(owner<=0 || current!=null&&!current.stopped&&current.owner==owner || !starting.add(account))return; }
        AndroidUtilities.runOnUIThread(()->{ synchronized(FutoonAutomation.class){starting.remove(account);} if(UserConfig.getInstance(account).isClientActivated())get(account); });
    }
    public static void startAccounts() { for (int account=0; account<UserConfig.MAX_ACCOUNT_COUNT; account++) if (UserConfig.getInstance(account).isClientActivated()) get(account); }
    private FutoonAutomation(int account, long owner) {
        this.account=account; this.owner=owner;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("futoon_automation_"+owner, Context.MODE_PRIVATE);
        try { JSONArray data=new JSONArray(preferences.getString("completed", "[]")); for(int i=0;i<data.length();i++)completed.add(data.getString(i)); } catch(Exception ignored) {}
        NotificationCenter center=NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.didReceiveNewMessages);
        center.addObserver(this, NotificationCenter.replaceMessagesObjects);
        center.addObserver(this, NotificationCenter.appDidLogout);
    }
    private boolean active() { return !stopped && owner>0 && UserConfig.getInstance(account).isClientActivated() && UserConfig.getInstance(account).getClientUserId()==owner; }
    public void reload() {
        for(Runnable action:pending.values())AndroidUtilities.cancelRunOnUIThread(action);
        pending.clear(); ruleDue.clear(); nextDue=0;
        rules=active()?new ArrayList<>(FutoonChatPreferences.get(account).options().getRules()):Collections.emptyList();
    }
    private void stop() {
        if(stopped)return;stopped=true;
        for(Runnable action:pending.values())AndroidUtilities.cancelRunOnUIThread(action); pending.clear(); latest.clear(); rules=Collections.emptyList();
        NotificationCenter center=NotificationCenter.getInstance(account); center.removeObserver(this,NotificationCenter.didReceiveNewMessages);center.removeObserver(this,NotificationCenter.replaceMessagesObjects);center.removeObserver(this,NotificationCenter.appDidLogout);
    }
    @Override public void didReceivedNotification(int event,int eventAccount,Object... args) {
        if(eventAccount!=account)return;
        if(event==NotificationCenter.appDidLogout){stop();return;}
        if(event!=NotificationCenter.didReceiveNewMessages && event!=NotificationCenter.replaceMessagesObjects)return;
        if(!active()||rules.isEmpty()||args.length<2||!(args[0] instanceof Long)||!(args[1] instanceof ArrayList))return;
        if(event==NotificationCenter.didReceiveNewMessages && ((args.length>2&&Boolean.TRUE.equals(args[2])) || (args.length>3&&args[3] instanceof Integer&&((Integer)args[3])!=0)))return;
        long dialog=(Long)args[0];if(dialog>=0)return;
        boolean watched=false;for(GroupAutomationRule rule:rules)if(rule.getEnabled()&&rule.getDialog()==dialog){watched=true;break;}if(!watched)return;
        for(Object item:(ArrayList<?>)args[1])if(item instanceof MessageObject)receive((MessageObject)item);
    }
    private void receive(MessageObject message) {
        if(message==null||message.messageOwner==null||message.getId()<=0||message.isOutOwner()||message.messageOwner instanceof TLRPC.TL_messageService||message.messageOwner.date<started)return;
        String messageKey=message.getDialogId()+":"+message.getId(); latest.remove(messageKey);latest.put(messageKey,message);
        while(latest.size()>200)latest.remove(latest.keySet().iterator().next());
        long now=System.currentTimeMillis()/1000;
        long topic=MessageObject.getTopicId(account,message.messageOwner,true), sender=message.getFromChatId();
        String text=message.messageOwner.message==null?"":message.messageOwner.message;
        for(GroupAutomationRule rule:rules) {
            if(!rule.acceptsEvent(message.getDialogId(),topic,sender,text,message.messageOwner.date,now))continue;
            String key=messageKey+":"+(rule.getAction().equals("click")?"button:"+rule.getButton():"draft");
            if(completed.contains(key)||pending.containsKey(key)||pending.size()>=50)continue;
            if(rule.getAction().equals("click")&&button(message,rule)==null)continue;
            final GroupAutomationRule snapshot=rule;
            long due=Math.max(System.currentTimeMillis(),Math.max(nextDue,ruleDue.getOrDefault(rule.getId(),0L)));
            nextDue=due+500;ruleDue.put(rule.getId(),due+rule.getCooldown()*1000L);
            Runnable task=()->{pending.remove(key);if(!active()||!rules.contains(snapshot))return;MessageObject current=latest.get(messageKey);if(current!=null)try{execute(snapshot,current,key);}catch(Exception error){record(snapshot,current,"执行中止，未自动重试");}};
            pending.put(key,task);AndroidUtilities.runOnUIThread(task,Math.max(0,due-System.currentTimeMillis()));
        }
    }
    private TL_keyboard.KeyboardInlineButton button(MessageObject message,GroupAutomationRule rule) {
        TLRPC.User user=MessagesController.getInstance(account).getUser(rule.getSender());
        if(user==null||!user.bot||!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup))return null;
        TL_keyboard.KeyboardInlineButton found=null;
        for(TL_keyboard.KeyboardInlineButtonRow row:((TLRPC.TL_replyInlineMarkup)message.messageOwner.reply_markup).rows)for(TL_keyboard.KeyboardInlineButton candidate:row.buttons)
            if(rule.getButton().equals(candidate.getText()==null?"":candidate.getText().trim())) {
                if(found!=null || !(candidate.getType() instanceof TL_keyboard.TL_inlineButtonTypeCallback))return null;
                TL_keyboard.TL_inlineButtonTypeCallback type=(TL_keyboard.TL_inlineButtonTypeCallback)candidate.getType();
                if(type.requires_password||type.data==null)return null;found=candidate;
            }
        return found;
    }
    private boolean claim(String key) {
        if(completed.contains(key))return false;completed.add(key);while(completed.size()>1000)completed.remove(completed.iterator().next());
        if(preferences.edit().putString("completed",new JSONArray(completed).toString()).commit())return true;
        completed.remove(key);return false;
    }
    private void execute(GroupAutomationRule rule,MessageObject message,String key) {
        long now=System.currentTimeMillis()/1000;
        if(!rule.acceptsEvent(message.getDialogId(),MessageObject.getTopicId(account,message.messageOwner,true),message.getFromChatId(),message.messageOwner.message==null?"":message.messageOwner.message,message.messageOwner.date,now))return;
        if(rule.getAction().equals("draft")){draft(rule,message,key);return;}
        TL_keyboard.KeyboardInlineButton selected=button(message,rule);if(selected==null||!claim(key))return;
        TLRPC.TL_messages_getBotCallbackAnswer request=new TLRPC.TL_messages_getBotCallbackAnswer();
        request.peer=MessagesController.getInstance(account).getInputPeer(message.getDialogId());request.msg_id=message.getId();request.flags|=1;request.data=selected.getData();
        record(rule,message,"已提交按钮动作");
        ConnectionsManager.getInstance(account).sendRequest(request,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(!active())return;
            record(rule,message,error==null?"按钮回调完成":("结果未确认／已拒绝："+error.text+"；不自动重试"));
        }),ConnectionsManager.RequestFlagFailOnServerErrors);
    }
    private void draft(GroupAutomationRule rule,MessageObject message,String key) {
        String text=rule.getDraft().replace("{消息文字}",message.messageOwner.message==null?"":message.messageOwner.message).replace("{群名称}",rule.getGroupName()).replace("{发送者ID}",Long.toString(rule.getSender()));
        if(text.isEmpty()||text.length()>4096){record(rule,message,"草稿为空或超过 4096 字，已跳过");return;}
        long dialog=message.getDialogId(), topic=MessageObject.getTopicId(account,message.messageOwner,true);
        TLRPC.DraftMessage previous=MediaDataController.getInstance(account).getDraft(dialog,topic);
        if(previous!=null&&previous.message!=null&&!previous.message.isEmpty()){record(rule,message,"已有聊天草稿，已跳过");return;}
        BaseFragment fragment=LaunchActivity.getSafeLastFragment();
        if(fragment instanceof ChatActivity&&fragment.getCurrentAccount()==account&&((ChatActivity)fragment).getDialogId()==dialog&&((ChatActivity)fragment).getTopicId()==topic) {
            ChatActivity chat=(ChatActivity)fragment;
            if(!chat.futoonDraftEmpty()){record(rule,message,"输入框已有文字，已跳过");return;}
            if(claim(key))record(rule,message,chat.futoonInsertDraft(text,dialog,topic)?"已填入回复草稿":"输入框暂不可用，已跳过");
        } else if(claim(key)) {
            MediaDataController.getInstance(account).saveDraft(dialog,topic,text,null,null,null,null,0L,false,false);
            record(rule,message,"已生成聊天草稿，等待手动发送");
        }
    }
    private void record(GroupAutomationRule rule,MessageObject message,String outcome) {
        String line=android.text.format.DateFormat.format("MM-dd HH:mm:ss",System.currentTimeMillis())+" · "+rule.getTitle()+" · 群 "+message.getDialogId()+" / 消息 "+message.getId()+" · "+outcome;
        ArrayList<String> rows=new ArrayList<>();rows.add(line);
        try{JSONArray old=new JSONArray(preferences.getString("history","[]"));for(int i=0;i<old.length()&&rows.size()<100;i++)rows.add(old.getString(i));}catch(Exception ignored){}
        preferences.edit().putString("history",new JSONArray(rows).toString()).apply();
    }
    public String history() { StringBuilder text=new StringBuilder();try{JSONArray rows=new JSONArray(preferences.getString("history","[]"));for(int i=0;i<rows.length();i++)text.append(rows.getString(i)).append("\n\n");}catch(Exception ignored){}return text.length()==0?"暂无执行记录":text.toString(); }
}
