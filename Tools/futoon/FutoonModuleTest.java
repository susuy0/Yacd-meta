package org.telegram.messenger.futoon;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Regression fixtures for the Android admin 1.3.4 monitor/editor contracts. */
public final class FutoonModuleTest {
 static int checks;
 static void check(boolean v,String text){checks++;if(!v)throw new AssertionError(text);}
 static void rejects(FutoonExtendedTest.Throwing f,String text)throws Exception{boolean failed=false;try{f.run();}catch(Exception expected){failed=true;}check(failed,text);}
 static JSONObject params(String module,String editor){return new JSONObject().put("module",module).put("id","88").put("editor",editor);}
 static JSONObject field(JSONObject form,String name){JSONArray fields=form.getJSONArray("fields");for(int i=0;i<fields.length();i++)if(fields.getJSONObject(i).getString("name").equals(name))return fields.getJSONObject(i);throw new AssertionError("missing field "+name);}
 public static void main(String[] args)throws Exception{
  List<Map<String,String>> requests=new ArrayList<>();int[] variant={0};boolean[] postRejected={false},failure={false};
  String roll="<form id='search'><input name='q'></form><form id='editor'><input type='hidden' name='id'><input type='hidden' name='action' value='add'><input name='name'><select name='kind'><option value='0'>固定</option><option value='1'>随机</option></select><select name='type'><option value='2'>微信</option></select></form>";
  String channel="<table><thead><tr><th>ID</th><th>名称</th></tr></thead><tbody><tr><td>主通道</td><td><a onclick=\"editChannel('88')\">编辑</a></td></tr></tbody></table><form id='search'><input name='name' value='not-editor'></form><form id='edit'><input type='hidden' name='id'><input type='hidden' name='action' value='add'><input name='name'><select name='plugin'><option value='wx'>微信</option></select><input name='rate'><select name='type'><option value='2'>微信</option></select><input type='password' name='key' value='old-secret'></form>";
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/admin/",e->{try{
   Map<String,String> fields=FutoonExtendedTest.parse(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));fields.putAll(FutoonExtendedTest.parse(e.getRequestURI().getRawQuery()));fields.put("_method",e.getRequestMethod());requests.add(fields);
   String path=e.getRequestURI().getPath(),act=fields.getOrDefault("act",""),body="";
   if(path.endsWith("index.php"))body="<a href='pay_channel.php'>支付通道</a><a href='pay_roll.php'>通道轮询</a><a href='pay_channel_monitor.php'>通道监测</a>";
   else if(path.endsWith("pay_channel_monitor.php"))body="<table><thead><th data-field='id'>ID</th><th data-field='name'>通道</th><th data-field='success_rate_5min'>5 分钟</th></thead></table>";
   else if(path.endsWith("pay_roll.php"))body=roll;
   else if(path.endsWith("pay_channel.php"))body=channel;
   else if(act.equals("channelMonitor")) {
    JSONArray rows=new JSONArray().put(new JSONObject().put("id","88").put("name","主通道").put("success_rate_5min","92.5%"));
    if(failure[0])body="{\"code\":1,\"msg\":\"permission denied\"}";
    else if(postRejected[0]&&e.getRequestMethod().equals("POST"))body="{\"code\":1,\"msg\":\"GET required\"}";
    else if(variant[0]==0)body=new JSONObject().put("code",0).put("data",new JSONObject().put("records",rows).put("total",51)).toString();
    else if(variant[0]==1)body=new JSONObject().put("rows",rows.toString()).put("total",1).toString();
    else if(variant[0]==2)body=new JSONObject().put("data",new JSONObject().put("88",rows.getJSONObject(0))).toString();
    else if(variant[0]==3)body=new JSONObject().put("rows",new JSONArray().put(new JSONArray().put("88").put("主通道").put("92.5%"))).toString();
    else body="{\"rows\":[],\"total\":0}";
   }else if(act.equals("rollInfo"))body="{\"code\":0,\"data\":{\"channels\":[{\"id\":\"11\",\"name\":\"微信A\"},{\"id\":\"22\",\"name\":\"微信B\"}],\"info\":[{\"channel\":\"11\",\"weight\":\"7\"}]}}";
   else if(act.equals("getRoll"))body="{\"code\":0,\"data\":{\"id\":\"88\",\"name\":\"默认路由\",\"kind\":\"1\",\"type\":\"2\"}}";
   else if(act.equals("getChannel"))body="{\"code\":0,\"data\":{\"id\":\"88\",\"name\":\"主通道\",\"plugin\":\"wx\",\"type\":\"2\",\"rate\":\"97.5\",\"key\":\"server-secret\"}}";
   else if(act.equals("getPlugins"))body="{\"code\":0,\"data\":[{\"name\":\"wx\",\"showname\":\"微信\"}]}";
   else if(act.equals("channelInfo"))body=new JSONObject().put("code",0).put("data","<div class='form-group'><label>商户号</label><input name='appid' value='123'></div><input type='password' name='appkey' value='keep-existing'>").toString();
   else body="{\"code\":0,\"msg\":\"saved\"}";
   byte[] data=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);try(java.io.OutputStream out=e.getResponseBody()){out.write(data);}
  }catch(Exception error){e.sendResponseHeaders(500,-1);e.close();}});server.start();
  try {
   FutoonApi.SessionPersistence memory=new FutoonApi.SessionPersistence(){public String load(){return "";}public void save(String s){}public void clear(){}};
   FutoonBusinessApi b=new FutoonBusinessApi(new FutoonApi("http://127.0.0.1:"+server.getAddress().getPort()+"/admin/",memory,true));
   JSONObject r=b.call("module.read",params("channel_monitor","detail").put("page",2));
   check(r.getJSONArray("rows").length()==1&&r.getInt("total")==51,"nested monitor records and authoritative total");
   Map<String,String> req=requests.get(requests.size()-1);check(req.get("offset").equals("25")&&req.get("type").equals("0"),"monitor paging and filter contract");
   for(int i=1;i<=3;i++){variant[0]=i;r=b.call("module.read",params("channel_monitor","detail"));JSONObject row=r.getJSONArray("rows").getJSONObject(0);check(row.getString("id").equals("88")&&row.getString("success_rate_5min").equals("92.5%"),"monitor alternate list encoding "+i);}
   postRejected[0]=true;variant[0]=0;b.call("module.read",params("channel_monitor","detail").put("query","88"));req=requests.get(requests.size()-1);check(req.get("_method").equals("GET")&&req.get("channel_id").equals("88"),"read-only GET fallback retains filters");postRejected[0]=false;
   variant[0]=4;r=b.call("module.read",params("channel_monitor","detail"));check(r.getJSONArray("rows").isEmpty(),"legitimate empty monitor result");
   failure[0]=true;rejects(()->b.call("module.read",params("channel_monitor","detail")),"backend monitor error is not empty success");failure[0]=false;
   r=b.call("module.read",params("channels","detail"));check(r.getJSONArray("tables").getJSONObject(0).getJSONArray("rows").getJSONObject(0).getString("id").equals("88"),"HTML onclick row ID is retained");
   JSONObject form=b.call("module.editor",params("channels","detail"));check(field(form,"name").getString("value").equals("主通道"),"correct edit form selected instead of search form");check(!form.toString().contains("server-secret"),"secret not exposed");
   JSONObject plugins=b.call("module.plugins",new JSONObject().put("type","2"));check(plugins.getJSONArray("options").getJSONObject(0).getString("value").equals("wx"),"payment plugins use typeid and official names");
   final String channelTicket=form.getString("ticket");rejects(()->b.call("form.save",new JSONObject().put("ticket",channelTicket).put("values",new JSONObject().put("plugin","missing"))),"invalid payment plugin rejected before saving");
   b.call("form.save",new JSONObject().put("ticket",form.getString("ticket")).put("values",new JSONObject().put("id","999").put("action","add").put("name","修改后的通道").put("key","")));
   req=requests.get(requests.size()-1);check(req.get("id").equals("88")&&req.get("action").equals("edit")&&req.get("rate").equals("97.5"),"edit preserves record identity and untouched fields");check(req.get("key").equals("server-secret"),"blank secret retains existing configuration");
   form=b.call("module.editor",params("channels","account"));check(field(form,"appid").getString("value").equals("123"),"HTML fragment account editor");
   b.call("form.save",new JSONObject().put("ticket",form.getString("ticket")).put("values",new JSONObject().put("appid","456")));req=requests.get(requests.size()-1);check(req.get("act").equals("saveChannelInfo")&&req.get("id").equals("88"),"account editor save endpoint");
   form=b.call("module.editor",params("rolls","detail"));check(field(form,"kind").getString("value").equals("1"),"roll configuration template populated");
   JSONObject route=b.call("module.editor",params("rolls","route"));check(route.getString("kind").equals("route")&&field(route,"list[0][channel]").getString("value").equals("11"),"structured route info imported");
   String ticket=route.getString("ticket");
   JSONObject values=new JSONObject().put("list[0][channel]","11").put("list[0][weight]","3").put("list[1][channel]","22").put("list[1][weight]","1").put("list[2][channel]","").put("list[2][weight]","bad");
   rejects(()->b.call("form.save",new JSONObject().put("ticket",ticket).put("values",new JSONObject(values.toString()).put("list[1][channel]","11"))),"duplicate routes rejected before write");
   rejects(()->b.call("form.save",new JSONObject().put("ticket",ticket).put("values",new JSONObject(values.toString()).put("list[1][channel]","999"))),"unknown route channel rejected");
   rejects(()->b.call("form.save",new JSONObject().put("ticket",ticket).put("values",new JSONObject(values.toString()).put("list[0][weight]","0").put("list[1][weight]","0"))),"all zero route weights rejected");
   b.call("form.save",new JSONObject().put("ticket",ticket).put("values",values));req=requests.get(requests.size()-1);check(req.get("act").equals("saveRollInfo")&&req.get("id").equals("88"),"route save uses original record target");check(req.get("list[1][channel]").equals("22")&&!req.containsKey("list[2][weight]"),"new routes saved and blank rows excluded");
   rejects(()->b.call("form.save",new JSONObject().put("ticket",ticket).put("values",values)),"successful route ticket cannot be replayed");
   System.out.println("PASS: "+checks+" monitor and configuration editor regressions");
  }finally{server.stop(0);}
 }
}
