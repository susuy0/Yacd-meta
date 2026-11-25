package org.telegram.messenger.futoon;

import org.json.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import java.util.*;
import java.util.concurrent.*;

/** Tracks actual outgoing Telegram messages independently of the chat panel lifetime. */
public final class FutoonDeliveryTracker implements NotificationCenter.NotificationCenterDelegate {
    public interface Listener { void changed(JSONObject row); }
    private static final Map<Integer,FutoonDeliveryTracker> INSTANCES=new HashMap<>();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final int account;
    private final long owner;
    private final FutoonStore store;
    private final Set<Listener> listeners=new HashSet<>();
    private final LinkedHashMap<Integer,Tag> outgoing=new LinkedHashMap<>();
    private final Map<String,JSONObject> backends=new HashMap<>();
    private final String session=UUID.randomUUID().toString();
    private static final int[] EVENTS={NotificationCenter.didReceiveNewMessages,NotificationCenter.messageReceivedByServer,NotificationCenter.messageSendError};
    private static final class Tag { String backend,draft,key;long dialog; }
    public static synchronized FutoonDeliveryTracker get(int account) {
        FutoonDeliveryTracker tracker=INSTANCES.get(account);
        long user=UserConfig.getInstance(account).getClientUserId();
        if(tracker==null||tracker.owner!=user) {
            if(tracker!=null)for(int e:EVENTS)NotificationCenter.getInstance(account).removeObserver(tracker,e);
            tracker=new FutoonDeliveryTracker(account);INSTANCES.put(account,tracker);
        }
        return tracker;
    }
    private FutoonDeliveryTracker(int account) {
        this.account=account;owner=UserConfig.getInstance(account).getClientUserId();store=new FutoonStore(account);
        for(int e:EVENTS)NotificationCenter.getInstance(account).addObserver(this,e);
    }
    public void addListener(Listener listener){listeners.add(listener);}
    public void removeListener(Listener listener){listeners.remove(listener);}
    private boolean valid(){return owner==UserConfig.getInstance(account).getClientUserId()&&UserConfig.getInstance(account).isClientActivated();}
    private synchronized JSONObject data(String backend)throws Exception {
        JSONObject value=backends.get(backend);
        if(value==null){String raw=store.readLocal("deliveries:"+backend);value=raw.isEmpty()?new JSONObject():new JSONObject(raw);backends.put(backend,value);}
        return value;
    }
    public synchronized JSONObject read(String backend)throws Exception {
        JSONObject out=new JSONObject(),data=data(backend);
        for(Iterator<String> keys=data.keys();keys.hasNext();) {String id=keys.next();JSONObject row=new JSONObject(data.getJSONObject(id).toString());row.remove("_items");out.put(id,row);}
        return out;
    }
    private Tag tag(TLRPC.Message message,long dialog) {
        if(message==null||message.params==null)return null;
        String draft=message.params.get("futoon_draft"),backend=message.params.get("futoon_backend");
        if(draft==null||draft.isEmpty()||backend==null||backend.isEmpty())return null;
        Tag t=new Tag();t.draft=draft;t.backend=backend;t.dialog=dialog;
        t.key=message.random_id!=0?"r"+message.random_id:session+":"+message.id;return t;
    }
    private void record(Tag tag,String state,int message) {
        IO.execute(()->{
            if(!valid())return;
            try {
                JSONObject visible;
                synchronized(this) {
                    JSONObject all=data(tag.backend),row=all.optJSONObject(tag.draft);if(row==null)row=new JSONObject();
                    JSONObject items=row.optJSONObject("_items");if(items==null)items=new JSONObject();
                    // A late local 'sending' event must not overwrite an acknowledged message.
                    if(!state.equals("pending")||!items.has(tag.key))items.put(tag.key,state);
                    while(items.length()>100)items.remove(items.keys().next());
                    int sent=0,failed=0,pending=0;
                    for(Iterator<String> keys=items.keys();keys.hasNext();) {String s=items.optString(keys.next());if(s.equals("sent"))sent++;else if(s.equals("failed"))failed++;else pending++;}
                    row.put("draft",tag.draft).put("dialog",""+tag.dialog).put("message",message).put("sent",sent).put("failed",failed).put("pending",pending)
                            .put("updated",System.currentTimeMillis()).put("_items",items);
                    all.put(tag.draft,row);
                    while(all.length()>100){String oldest=null;long time=Long.MAX_VALUE;for(Iterator<String> keys=all.keys();keys.hasNext();){String id=keys.next();long at=all.getJSONObject(id).optLong("updated");if(at<time){time=at;oldest=id;}}all.remove(oldest);}
                    store.writeLocal("deliveries:"+tag.backend,all.toString());visible=new JSONObject(row.toString());visible.remove("_items");
                }
                JSONObject result=visible;
                AndroidUtilities.runOnUIThread(()->{if(valid()&&store.address().equals(tag.backend))for(Listener listener:new ArrayList<>(listeners))listener.changed(result);});
            }catch(Exception error){FileLog.e(error);}
        });
    }
    @Override public void didReceivedNotification(int event,int eventAccount,Object... args) {
        if(eventAccount!=account||!valid())return;
        if(event==NotificationCenter.didReceiveNewMessages&&args.length>1&&args[1] instanceof Collection) {
            for(Object value:(Collection<?>)args[1])if(value instanceof MessageObject) {
                MessageObject object=(MessageObject)value;if(!object.isOut()||object.getId()>=0)continue;
                Tag tag=tag(object.messageOwner,((Number)args[0]).longValue());
                if(tag!=null){outgoing.put(object.getId(),tag);record(tag,"pending",object.getId());}
            }
            while(outgoing.size()>300)outgoing.remove(outgoing.keySet().iterator().next());
        }else if(event==NotificationCenter.messageReceivedByServer&&args.length>3) {
            int old=((Number)args[0]).intValue();Tag tag=outgoing.get(old);
            if(tag==null&&args[2] instanceof TLRPC.Message)tag=tag((TLRPC.Message)args[2],((Number)args[3]).longValue());
            if(tag!=null){outgoing.put(old,tag);record(tag,"sent",((Number)args[1]).intValue());}
        }else if(event==NotificationCenter.messageSendError&&args.length>0) {
            int id=((Number)args[0]).intValue();Tag tag=outgoing.get(id);if(tag!=null)record(tag,"failed",id);
        }
    }
}
