package org.telegram.messenger.futoon;
import com.sun.net.httpserver.*;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FutoonExtendedTest {
 static int checks;
 static void check(boolean test,String name){checks++;if(!test)throw new AssertionError(name);}
 interface Throwing{void run()throws Exception;}
 static void fails(Throwing f,String name)throws Exception{boolean failed=false;try{f.run();}catch(Exception e){failed=true;}check(failed,name);}
 static Map<String,String> parse(String text)throws Exception{
  Map<String,String> out=new LinkedHashMap<>();if(text!=null)for(String pair:text.split("&")){String[] a=pair.split("=",2);out.put(URLDecoder.decode(a[0],"UTF-8"),a.length>1?URLDecoder.decode(a[1],"UTF-8"):"");}return out;
 }
 public static void main(String[] args)throws Exception{
  FutoonIdentity identity=FutoonIdentity.topic("工单12627|游客|诺诺永久在𝕏|5824870757");
  check(identity!=null&&identity.telegramId.equals("5824870757"),"64-bit topic TGID retained");
  check(identity.ticket.equals("12627")&&!identity.ticket.equals(identity.telegramId),"ticket is not PID or TGID");
  check(identity.role.equals("游客"),"visitor label does not suppress lookup");
  check(identity.name.equals("诺诺永久在𝕏"),"Unicode display name preserved");
  check(FutoonIdentity.topic(" 工单12627 ｜ 游客 ｜名字|含分隔符｜ 5824870757 ").telegramId.equals("5824870757"),"rightmost delimiter and fullwidth delimiter");
  check(FutoonIdentity.topic("工单1|游客|名字|0")==null,"zero TGID rejected");
  check(FutoonIdentity.topic("工单1|游客|名字|-123")==null,"negative TGID rejected");
  check(FutoonIdentity.topic("工单1|游客|名字|99999999999999999999")==null,"overflow TGID rejected");
  check(FutoonIdentity.topic("工单12627|游客|名字|not-an-id")==null,"bad title never guesses ticket");
  check(FutoonIdentity.topic("客服群5824870757")==null,"ordinary group name is not an identity");
  check(FutoonIdentity.privatePeer(5824870757L,123,false,false,"用户").telegramId.equals("5824870757"),"private chat peer ID");
  check(FutoonIdentity.privatePeer(123,123,false,false,"自己")==null,"self chat rejected");
  check(FutoonIdentity.privatePeer(123,1,true,false,"bot")==null,"bot auto identity rejected");
  check(FutoonIdentity.privatePeer(123,1,false,true,"deleted")==null,"deleted identity rejected");
  check(FutoonQuery.parse("订单号: ABC001",FutoonQuery.Kind.AUTO).kind==FutoonQuery.Kind.AUTO,"generic order label stays ambiguous");
  check(FutoonQuery.parse("通道订单号: CH001",FutoonQuery.Kind.AUTO).kind==FutoonQuery.Kind.CHANNEL_ORDER,"channel order type");
  List<FutoonQuery> candidates=FutoonQuery.extract("PID：12345，这笔200元还没到账。商户订单号：ABC20261017001；系统订单号：SYS20261017002");
  check(candidates.size()==3,"natural message extracts three distinct fields");
  check(candidates.get(0).kind==FutoonQuery.Kind.PID&&candidates.get(1).kind==FutoonQuery.Kind.MERCHANT_ORDER,"labels preserved");
  check(FutoonQuery.extract("TGID: 5824870757 工单12627 金额: 200.00").isEmpty(),"TGID ticket and amount not misclassified");
  check(FutoonQuery.extract("系统订单号 SYS20261017001\n系统订单号 SYS20261017001").size()==1,"deduplicate repeated labels");
  check(FutoonQuery.extract("系统订单号 SYS20261017******123456").isEmpty(),"masked order must not become a partial exact query");
  check(FutoonQuery.extract("请查 123456******789012").isEmpty(),"masked bare fragments rejected");
  fails(()->FutoonQuery.parse("PID 0",FutoonQuery.Kind.AUTO),"zero PID rejected");
  check(FutoonBusinessApi.amount("100.01").toPlainString().equals("100.01"),"money precision");
  for(String x:new String[]{"0","-1","+1","1.001","1e3","NaN"," 1"})fails(()->FutoonBusinessApi.amount(x),"invalid amount "+x);
  check(FutoonBusinessApi.mutates("merchant.balance")&&FutoonBusinessApi.mutates("batch.notify"),"mutations require explicit confirmation");
  check(!FutoonBusinessApi.mutates("merchant.lookup")&&!FutoonBusinessApi.mutates("orders.search"),"lookups remain read only");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  List<Map<String,String>> requests=new ArrayList<>();boolean[] duplicate={false},ack={true},unsupported={false};
  server.createContext("/admin/",exchange->{
   try{
    Map<String,String> f=parse(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));f.putAll(parse(exchange.getRequestURI().getRawQuery()));f.put("_method",exchange.getRequestMethod());requests.add(f);
    String act=f.get("act"),body;
    if(exchange.getRequestURI().getPath().endsWith("uset.php"))body="<form><input type='hidden' name='uid' value='20418'><input name='username' value='old'><input readonly name='account' value='locked'><input type='password' name='key' value='keep-secret'></form>";
    else if("userList".equals(act)){
     JSONArray rows=new JSONArray();String value=f.get("value");
     if("5824870757".equals(value)||"20418".equals(value)){
      rows.put(new JSONObject().put("uid","20418").put("telegram_id","5824870757").put("username","商户").put("money","1000.00"));
      rows.put(new JSONObject().put("uid",duplicate[0]?"20419":"99999").put("telegram_id",duplicate[0]?"5824870757":"58248707570"));
     }
     body=new JSONObject().put("rows",rows).put("total",rows.length()).toString();
    }else if("orderList".equals(act)){
     body=unsupported[0]&&"api_trade_no".equals(f.get("column"))?"{\"code\":1,\"msg\":\"unsupported column\"}":"{\"rows\":[],\"total\":0}";
    }else if("userPayStat".equals(act)){
     body="{\"rows\":[{\"uid\":\"20418\",\"total\":\"100\"},{\"uid\":\"999\",\"total\":\"900\"}]}";
    }else body=ack[0]?"{\"code\":0,\"msg\":\"ok\"}":"{\"msg\":\"unknown\"}";
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}
   }catch(Exception ex){exchange.sendResponseHeaders(500,-1);exchange.close();}
  });server.start();
  try{
   FutoonApi.SessionPersistence memory=new FutoonApi.SessionPersistence(){String data="";public String load(){return data;}public void save(String s){data=s;}public void clear(){data="";}};
   FutoonBusinessApi b=new FutoonBusinessApi(new FutoonApi("http://127.0.0.1:"+server.getAddress().getPort()+"/admin/",memory,true));
   JSONObject hit=b.merchant("telegram_id","5824870757");
   check(hit.getJSONArray("rows").length()==1&&hit.getJSONArray("rows").getJSONObject(0).getString("uid").equals("20418"),"fuzzy TGID extra row excluded");
   check(requests.get(requests.size()-1).get("column").equals("telegram_id"),"actual TGID field used");
   check(b.merchant("telegram_id","555").getJSONArray("rows").length()==0,"unbound identity empty");
   duplicate[0]=true;fails(()->b.merchant("telegram_id","5824870757"),"duplicate mapping blocks automatic merchant selection");duplicate[0]=false;
   b.call("merchant.balance",new JSONObject().put("uid","20418").put("direction","add").put("amount","100").put("currency","CNY"));
   Map<String,String> req=requests.get(requests.size()-1);
   check(req.get("actdo").equals("0")&&req.get("rmb").equals("100.00"),"increase uses actdo zero and positive amount");
   b.call("merchant.balance",new JSONObject().put("uid","20418").put("direction","deduct").put("amount","100").put("currency","CNY"));
   req=requests.get(requests.size()-1);check(req.get("actdo").equals("1")&&req.get("rmb").equals("100.00"),"deduct uses actdo one and positive amount");
   fails(()->b.call("merchant.balance",new JSONObject().put("uid","20418").put("direction","add").put("amount","100").put("currency","USDT")),"unverified currency cannot hit CNY balance API");
   JSONObject preview=b.call("balance.preview",new JSONObject().put("before","1000.00").put("amount","0.01").put("direction","add"));
   check(preview.getString("after").equals("1000.01"),"decimal preview is exact");
   fails(()->b.call("balance.preview",new JSONObject().put("before","10").put("amount","11").put("direction","deduct")),"negative resulting balance rejected");
   b.call("merchant.group",new JSONObject().put("uid","20418").put("gid","0"));
   check(requests.get(requests.size()-1).get("gid").equals("0"),"default group zero accepted");
   JSONObject form=b.call("merchant.profile",new JSONObject().put("uid","20418"));
   check(!form.toString().contains("keep-secret"),"existing secret not exposed to UI");
   b.call("form.save",new JSONObject().put("ticket",form.getString("ticket")).put("values",new JSONObject().put("username","new").put("uid","999").put("account","changed").put("key","")));
   req=requests.get(requests.size()-1);
   check(req.get("uid").equals("20418")&&req.get("username").equals("new"),"profile keeps original UID");
   check(req.get("account").equals("locked")&&req.get("key").equals("keep-secret"),"read only and blank secret preserved");
   fails(()->b.call("form.save",new JSONObject().put("ticket",form.getString("ticket")).put("values",new JSONObject())),"successful form ticket cannot replay");
   ack[0]=false;fails(()->b.call("merchant.resetKey",new JSONObject().put("uid","20418")),"missing mutation acknowledgment rejected");ack[0]=true;
   fails(()->b.call("http",new JSONObject().put("url","https://example.org")),"arbitrary HTTP bridge unavailable");
   fails(()->b.call("merchant.status",new JSONObject().put("uid","20418").put("type","key").put("enabled",true)),"unknown merchant flag rejected");
   b.call("order.action",new JSONObject().put("id","SYS001").put("action","queryChannelOrder"));check(requests.get(requests.size()-1).get("tradeNo").equals("SYS001"),"channel query parameter");
   b.call("order.action",new JSONObject().put("id","SYS001").put("action","refund").put("money","2.50").put("remark","测试"));check(requests.get(requests.size()-1).get("money").equals("2.50"),"refund decimal contract");
   JSONObject batch=b.call("batch.notify",new JSONObject().put("orders",new JSONArray().put("SYS001").put("SYS001").put("SYS002")));
   check(batch.getInt("requested")==2&&batch.getJSONArray("results").length()==2,"batch deduplicates and reports each outcome");
   requests.clear();
   b.call("orders.search",new JSONObject().put("text","ABC001").put("status","1").put("uid","20418").put("channel","88").put("starttime","2026-10-01").put("endtime","2026-10-17"));
   check(requests.size()==3,"filters preserve automatic search across all three order fields");
   check(requests.stream().allMatch(x->"20418".equals(x.get("uid"))&&"88".equals(x.get("channel"))&&"1".equals(x.get("dstatus"))&&"2026-10-01".equals(x.get("starttime"))),"all query types retain merchant channel status and dates");
   b.call("orders.list",new JSONObject().put("column","money").put("value","200.00"));
   check(requests.get(requests.size()-1).get("column").equals("money"),"backend amount search field");
   fails(()->b.call("orders.list",new JSONObject().put("starttime","2026-10-18").put("endtime","2026-10-17")),"reversed date range rejected");
   fails(()->b.call("orders.list",new JSONObject().put("status","999")),"invalid order status rejected before request");
   unsupported[0]=true;
   JSONObject fallback=b.call("orders.search",new JSONObject().put("text","20418"));
   check(fallback.getString("kind").equals("PID")&&!fallback.getString("warning").isEmpty(),"unsupported channel column retains PID fallback and visible warning");
   fails(()->b.call("orders.search",new JSONObject().put("text","CH001").put("kind","CHANNEL_ORDER")),"explicit channel failure remains an error");
   unsupported[0]=false;
   JSONObject stat=b.call("merchant.stats",new JSONObject().put("uid","20418").put("date","2026-10-17"));
   check(stat.getJSONArray("rows").length()==1&&stat.getJSONArray("rows").getJSONObject(0).getString("uid").equals("20418"),"merchant statistics exclude other merchants");
   check(requests.get(requests.size()-1).get("day").equals("2026-10-17")&&requests.get(requests.size()-1).get("method").equals("type"),"merchant statistics use verified day and method fields");
  }finally{server.stop(0);}
  System.out.println("PASS: "+checks+" extended identity, recognition and business contract checks");
 }
}
