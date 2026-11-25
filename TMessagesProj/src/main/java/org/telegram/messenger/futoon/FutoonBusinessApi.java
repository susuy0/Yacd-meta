package org.telegram.messenger.futoon;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Typed operations adapted from FutoonPayAdmin 1.3.4, never an arbitrary URL bridge. */
public final class FutoonBusinessApi {
    public interface Engine { boolean supports(String op); JSONObject call(String op,JSONObject params) throws Exception; }
    private final FutoonApi api;
    private final Engine engine;
    private final Map<String, Form> forms = new LinkedHashMap<>();
    private final Map<String, String> modulePaths = new HashMap<>();
    private static final class Form {
        String endpoint, referer;
        boolean route;
        final Set<String> channels = new HashSet<>(), multiselect = new HashSet<>();
        final Map<String, String> defaults = new LinkedHashMap<>();
        final Set<String> editable = new HashSet<>();
        long expires = System.currentTimeMillis() + 600000;
    }
    public FutoonBusinessApi(FutoonApi api) { this(api,null); }
    public FutoonBusinessApi(FutoonApi api,Engine engine) { this.api=api;this.engine=engine; }
    public static boolean mutates(String op) {
        return Arrays.asList("order.action", "merchant.balance", "merchant.status", "merchant.group",
                "merchant.resetKey", "form.save", "domain.action", "blacklist.add", "blacklist.remove",
                "settlement.status", "settlement.info", "module.action", "channel.test", "batch.notify", "batch.run").contains(op);
    }
    public static BigDecimal amount(String text) {
        if (text == null || !text.matches("[0-9]{1,14}(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("金额必须是大于 0 的正数，最多两位小数");
        BigDecimal v = new BigDecimal(text);
        if (v.signum() <= 0) throw new IllegalArgumentException("金额必须大于 0");
        return v.setScale(2);
    }
    private static String id(String s) {
        if (!FutoonIdentity.validId(s)) throw new IllegalArgumentException("记录 ID 无效");
        return FutoonIdentity.canonicalId(s);
    }
    private static String orderId(String s) {
        return FutoonQuery.parse(s, FutoonQuery.Kind.SYSTEM_ORDER).value;
    }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static Map<String, String> map(String... values) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) m.put(values[i], values[i + 1]);
        return m;
    }
    private static Map<String, String> orderFilters(JSONObject p) {
        Map<String, String> fields = new LinkedHashMap<>();
        String status=p.optString("status","-1");
        if(!status.matches("-1|[0-3]"))throw new IllegalArgumentException("订单状态无效");
        fields.put("dstatus",status);
        for(String key:new String[]{"uid","channel"})if(!p.optString(key).isEmpty())fields.put(key,id(p.optString(key)));
        for(String key:new String[]{"starttime","endtime"})if(!p.optString(key).isEmpty()) {
            String day=p.optString(key);LocalDate.parse(day);fields.put(key,day);
        }
        if(fields.containsKey("starttime")&&fields.containsKey("endtime")&&fields.get("starttime").compareTo(fields.get("endtime"))>0)
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        return fields;
    }
    private JSONObject action(String endpoint, Map<String, String> fields, String referer) throws Exception {
        JSONObject result = api.json(endpoint, fields, referer, true);
        if (!result.has("code")) throw new IOException("后台没有明确确认结果，请刷新核对，勿重复提交");
        return result;
    }
    private JSONObject page(String endpoint, String referer, int page, Map<String, String> filters) throws Exception {
        if (page < 1 || page > 100000) throw new IllegalArgumentException("页码无效");
        Map<String, String> f = map("pageNumber", "" + page, "pageSize", "25", "offset", "" + ((page - 1) * 25), "limit", "25");
        f.putAll(filters);
        JSONObject result = api.json(endpoint, f, referer);
        if (result.optJSONArray("rows") == null) throw new IOException("后台未提供列表，请检查接口权限");
        return result;
    }
    public JSONObject merchant(String key, String value) throws Exception {
        if (!key.equals("uid") && !key.equals("telegram_id")) throw new IllegalArgumentException("只能按 PID 或 TGID 查询");
        value = id(value);
        JSONArray hits = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int n = 1; n <= 100; n++) {
            JSONObject p = page("ajax_user.php?act=userList", "ulist.php", n, map("column", key, "value", value, "dstatus", "0"));
            JSONArray rows = p.getJSONArray("rows");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (value.equals(row.optString(key)) && seen.add(row.optString("uid"))) hits.put(row);
            }
            if (hits.length() > 1) throw new IOException("数据冲突：一个 TGID 返回了多个商户，请到后台核对绑定，未自动采用任何记录");
            if (rows.length() < 25 || n * 25 >= p.optInt("total", Integer.MAX_VALUE))
                return new JSONObject().put("rows", hits).put("total", hits.length()).put("exact", true);
        }
        throw new IOException("商户查询结果过多，无法确认唯一匹配，请核对后台精确搜索");
    }
    public JSONObject call(String op, JSONObject p) throws Exception {
        if(engine!=null && engine.supports(op))return engine.call(op,p);
        String uid = p.optString("uid"), record = p.optString("id");
        switch (op) {
            case "batch.notify": {
                JSONArray orders=p.getJSONArray("orders"),results=new JSONArray();
                if(orders.length()<1||orders.length()>100)throw new IllegalArgumentException("每批应为 1 至 100 笔订单");
                Set<String> ids=new LinkedHashSet<>();
                for(int i=0;i<orders.length();i++)ids.add(orderId(orders.getString(i)));
                for(String trade:ids) {
                    try {JSONObject r=call("order.action",new JSONObject().put("id",trade).put("action","notify"));results.put(new JSONObject().put("id",trade).put("ok",true).put("message",r.optString("msg")));}
                    catch(FutoonApi.SessionExpired expired){results.put(new JSONObject().put("id",trade).put("ok",false).put("message",expired.getMessage()));break;}
                    catch(Exception e){results.put(new JSONObject().put("id",trade).put("ok",false).put("message",e.getMessage()));}
                }
                return new JSONObject().put("results",results).put("requested",ids.size());
            }
            case "merchant.lookup": return merchant(p.optString("column", "telegram_id"), p.getString("value"));
            case "orders.list": {
                Map<String,String> f = orderFilters(p);f.put("type","0");
                String column = p.optString("column");
                if (!column.isEmpty()) {
                    if (!Arrays.asList("trade_no","out_trade_no","api_trade_no","uid","name","money","realmoney","getmoney","domain","buyer","ip").contains(column)) throw new IllegalArgumentException("订单搜索字段无效");
                    f.put("column",column); f.put("value",p.optString("value"));
                }
                return page("ajax_order.php?act=orderList","order.php",p.optInt("page",1),f);
            }
            case "orders.search": {
                FutoonQuery q = FutoonQuery.parse(p.getString("text"), FutoonQuery.Kind.valueOf(p.optString("kind","AUTO")));
                FutoonApi.SearchResult r = api.search(q,p.optInt("page",1),orderFilters(p));
                JSONArray rows = new JSONArray(); for (FutoonApi.Order o:r.orders) rows.put(o.data);
                return new JSONObject().put("rows",rows).put("total",r.total).put("kind",r.kind.name()).put("value",q.value).put("warning",r.warning);
            }
            case "order.detail": return api.detail(new FutoonApi.Order(new JSONObject().put("trade_no",orderId(record)))).data;
            case "order.action": {
                String trade = orderId(record), kind = p.getString("action");
                if (kind.equals("setStatus")) {
                    int status = p.getInt("status");
                    if (status < 0 || status > 3) throw new IllegalArgumentException("订单状态无效");
                    return action("ajax_order.php?act=setStatus&trade_no="+enc(trade)+"&status="+status,null,"order.php");
                }
                if (!Arrays.asList("notify","queryChannelOrder","freeze","unfreeze","refund","apirefund").contains(kind)) throw new IllegalArgumentException("订单操作无效");
                Map<String,String> fields = map(kind.equals("queryChannelOrder")?"tradeNo":"trade_no",trade);
                if (kind.equals("refund") || kind.equals("apirefund")) {
                    fields.put("money",amount(p.getString("money")).toPlainString());
                    fields.put("remark",p.optString("remark"));
                    if (kind.equals("apirefund")) fields.put("paypwd",p.getString("paypwd"));
                }
                return action("ajax_order.php?act="+kind,fields,"order.php");
            }
            case "merchant.flow": return page("ajax_user.php?act=recordList","record.php",p.optInt("page",1),map("uid",id(uid)));
            case "merchant.stats": {
                String date = p.optString("date",LocalDate.now().toString());
                LocalDate.parse(date);String merchant=id(uid);
                JSONObject result=api.json("ajax_user.php?act=userPayStat",map("day",date,"method","type"),"ustat.php");
                JSONArray rows=result.optJSONArray("rows"),matched=new JSONArray();
                if(rows==null)throw new IOException("后台未提供商户统计列表");
                for(int i=0;i<rows.length();i++)if(merchant.equals(rows.getJSONObject(i).optString("uid")))matched.put(rows.getJSONObject(i));
                return new JSONObject().put("uid",merchant).put("day",date).put("rows",matched);
            }
            case "merchant.status": {
                String type = p.getString("type");
                if (!Arrays.asList("status","pay","settle").contains(type)) throw new IllegalArgumentException("商户开关类型无效");
                return action("ajax_user.php?act=setUser",map("uid",id(uid),"type",type,"status",p.getBoolean("enabled")?"1":"0"),"ulist.php");
            }
            case "merchant.balance": {
                String currency=p.optString("currency","CNY");
                if (!currency.equals("CNY")) throw new IOException("现有后台充值接口仅验证了 CNY，其他币种需核验专用接口后操作");
                String direction=p.getString("direction");
                if (!direction.equals("add") && !direction.equals("deduct")) throw new IllegalArgumentException("请选择增加或扣除");
                return action("ajax_user.php?act=recharge",map("uid",id(uid),"actdo",direction.equals("add")?"0":"1","rmb",amount(p.getString("amount")).toPlainString()),"ulist.php");
            }
            case "balance.preview": {
                BigDecimal before=new BigDecimal(p.getString("before")), delta=amount(p.getString("amount"));
                boolean add=p.getString("direction").equals("add");
                BigDecimal after=add?before.add(delta):before.subtract(delta);
                if (after.signum()<0) throw new IllegalArgumentException("扣除金额超过可用余额");
                return new JSONObject().put("before",before.toPlainString()).put("amount",delta.toPlainString()).put("after",after.toPlainString());
            }
            case "merchant.group": {
                String gid=p.getString("gid");if(!gid.matches("[0-9]{1,10}"))throw new IllegalArgumentException("用户组 GID 无效");
                return action("ajax_user.php?act=setUserGroup",map("uid",id(uid),"gid",gid,"endtime",p.optString("endtime")),"ulist.php");
            }
            case "merchant.resetKey": return action("ajax_user.php?act=resetUser&uid="+id(uid),null,"ulist.php");
            case "merchant.profile": return profile(id(uid));
            case "form.save": return saveForm(p.getString("ticket"),p.getJSONObject("values"));
            case "settlements.list": return page("ajax_settle.php?act=settleList","slist.php",p.optInt("page",1),map("uid",uid.isEmpty()?"":id(uid),"type","0","dstatus",p.optString("status","-1")));
            case "settlement.status": {
                int status=p.getInt("status");
                if(status<0||status>3) throw new IllegalArgumentException("结算状态无效");
                if(status==3 && p.optString("reason").trim().isEmpty())throw new IllegalArgumentException("请填写失败原因");
                JSONObject result=action("ajax_settle.php?act=setSettleStatus&id="+id(record)+"&uid="+id(uid)+"&status="+status+"&usdt_rate="+enc(p.optString("usdt_rate"))+"&usdt="+enc(p.optString("usdt_count")),null,"slist.php");
                if(status==3) {
                    try {action("ajax_settle.php?act=settle_setresult",map("id",id(record),"result",p.getString("reason")),"slist.php");}
                    catch(Exception ex){return new JSONObject().put("code",0).put("partial",true).put("msg","状态已更新，但失败原因保存失败，请刷新核对："+ex.getMessage());}
                }
                return result;
            }
            case "settlement.info": return action("ajax_settle.php?act=settle_save",map("id",id(record),"pay_type",p.getString("pay_type"),"pay_account",p.getString("pay_account"),"pay_name",p.getString("pay_name")),"slist.php");
            case "domains.list": return page("ajax_user.php?act=domainList","domain.php",p.optInt("page",1),map("uid",uid,"dstatus",p.optString("status","-1")));
            case "domain.action": {
                String a=p.getString("action");
                if(a.equals("add"))return action("ajax_user.php?act=addDomain",map("uid",id(uid),"domain",p.getString("domain")),"domain.php");
                if(a.equals("delete"))return action("ajax_user.php?act=delDomain",map("id",id(record)),"domain.php");
                int status=p.getInt("status");if(status<0||status>2)throw new IllegalArgumentException("审核状态无效");
                return action("ajax_user.php?act=setDomainStatus",map("id",id(record),"status",""+status,"remark",p.optString("remark")),"domain.php");
            }
            case "blacklist.list": return page("ajax_user.php?act=blackList","blacklist.php",p.optInt("page",1),map("content",p.optString("content")));
            case "blacklist.add": {
                if(p.optString("content").trim().isEmpty())throw new IllegalArgumentException("请输入拦截内容");
                return action("ajax_user.php?act=addBlack",map("type",p.getString("type"),"content",p.getString("content"),"black_reason",p.optString("reason"),"days",p.optString("days","0"),"remark",p.optString("remark")),"blacklist.php");
            }
            case "blacklist.remove": return action("ajax_user.php?act=delBlack",map("id",id(record)),"blacklist.php");
            case "stats": {
                String date=LocalDate.now().toString();
                JSONObject all=page("ajax_order.php?act=orderList","order.php",1,map("starttime",date,"endtime",date,"type","0","dstatus","-1"));
                JSONObject paid=page("ajax_order.php?act=orderList","order.php",1,map("starttime",date,"endtime",date,"type","0","dstatus","1"));
                JSONObject out=new JSONObject().put("count",all.optInt("total")).put("paid",paid.optInt("total")).put("rows",all.getJSONArray("rows"));
                for(String k:new String[]{"money","total_money","paid_money","success_rate"})if(all.has(k))out.put(k,all.get(k));
                return out;
            }
            case "module.read": return module(p.getString("module"),p.optInt("page",1),p.optString("query"));
            case "module.plugins": return new JSONObject().put("options",plugins(p.getString("type")));
            case "module.editor": return editor(p.getString("module"),record,p.optString("editor","detail"));
            case "module.action": {
                String module=p.getString("module"), a=p.getString("action");
                String target=module.equals("channels")?"Channel":module.equals("payment_types")?"PayType":module.equals("rolls")?"Roll":"";
                if(target.isEmpty()||!Arrays.asList("enable","disable","delete").contains(a))throw new IllegalArgumentException("此模块操作尚未有已验证的接口");
                String endpoint="ajax_pay.php?act="+(a.equals("delete")?"del":"set")+target+"&id="+id(record);
                if(!a.equals("delete"))endpoint+="&status="+(a.equals("enable")?"1":"0");
                return action(endpoint,null,path(module));
            }
            case "channel.stats": return api.json("ajax_pay.php?act=channelMonitor",map("channel_id",id(record),"type","0","pageNumber","1","pageSize","25","offset","0","limit","25"),"pay_channel_monitor.php");
            case "channel.test": return action("ajax_pay.php?act=testpay",map("channel",id(record),"name",p.optString("name","测试订单"),"money",amount(p.getString("amount")).toPlainString()),path("channels"));
            default: throw new IllegalArgumentException("未知业务操作");
        }
    }
    private JSONObject profile(String uid) throws Exception {
        String source="uset.php?my=edit&uid="+uid;
        Document doc=Jsoup.parse(api.document(source));
        return form(doc,"ajax_user.php?act=editUser&uid="+uid,source,null);
    }
    private JSONObject form(Element doc,String endpoint,String referer,JSONObject values) throws Exception {
        Form f=new Form();f.endpoint=endpoint;f.referer=referer;
        JSONArray fields=new JSONArray();Map<String,List<Element>> grouped=new LinkedHashMap<>();
        for(Element el:doc.select("input[name],select[name],textarea[name]"))grouped.computeIfAbsent(el.attr("name"),key->new ArrayList<>()).add(el);
        for(Map.Entry<String,List<Element>> group:grouped.entrySet()) {
            String name=group.getKey();if(name.isEmpty())continue;
            Element el=group.getValue().get(0);
            for(Element candidate:group.getValue())if(!candidate.attr("type").equals("hidden")){el=candidate;break;}
            String type=el.tagName().equals("select")?el.hasAttr("multiple")?"multiselect":"select":el.tagName().equals("textarea")?"textarea":el.attr("type");
            if(Arrays.asList("submit","button","file").contains(type))continue;
            String value=el.tagName().equals("textarea")?el.text():el.val();JSONArray options=new JSONArray();
            if(type.equals("radio")) {
                type="select";value="";
                for(Element radio:group.getValue()){options.put(new JSONObject().put("value",radio.val()).put("label",radio.parent().text()));if(radio.hasAttr("checked"))value=radio.val();}
            }else if(type.equals("select")||type.equals("multiselect")) {
                List<String> selected=new ArrayList<>();
                for(Element o:el.select("option")){String v=o.hasAttr("value")?o.attr("value"):o.text();options.put(new JSONObject().put("value",v).put("label",o.text()));if(o.hasAttr("selected"))selected.add(v);}
                if(type.equals("multiselect")){value=String.join(",",selected);f.multiselect.add(name);}
            }else if(type.equals("checkbox"))value=el.hasAttr("checked")?"1":"0";
            String key=name.endsWith("[]")?name.substring(0,name.length()-2):name;
            if(values!=null&&values.has(key)) {
                Object raw=values.opt(key);if(raw instanceof JSONArray){List<String> parts=new ArrayList<>();JSONArray arr=(JSONArray)raw;for(int i=0;i<arr.length();i++)parts.add(arr.optString(i));value=String.join(",",parts);}else value=values.optString(key);
            }
            f.defaults.put(name,value);
            if(type.equals("hidden"))continue;
            if(!el.hasAttr("readonly")&&!el.hasAttr("disabled"))f.editable.add(name);
            String label=el.attr("placeholder");Element container=el.closest(".form-group,.input-group,td,label");
            if(container!=null){Element l=container.selectFirst("label");if(l!=null&&!l.text().isEmpty())label=l.text();}
            if(label.isEmpty())label=name;
            boolean secret=isSecret(name)||type.equals("password");
            JSONObject field=field(name,label,secret?"":value,secret?"password":type).put("sensitive",secret).put("readOnly",!f.editable.contains(name));
            if(options.length()>0||type.equals("select")||type.equals("multiselect"))field.put("options",options);
            fields.put(field);
        }
        if(values!=null){if(values.has("id"))f.defaults.put("id",values.getString("id"));if(f.defaults.containsKey("action"))f.defaults.put("action","edit");}
        if(fields.length()==0)throw new IOException("后台未返回可编辑表单，请检查权限或后台版本");
        return register(f,fields);
    }
    private static boolean isSecret(String name){return name.toLowerCase(Locale.ROOT).matches(".*(?:password|passwd|secret|token|private|key).*");}
    private static JSONObject field(String name,String label,String value,String type)throws Exception{return new JSONObject().put("name",name).put("label",label).put("value",value).put("type",type);}
    private JSONObject register(Form f,JSONArray fields)throws Exception {
        if(forms.size()>25)forms.clear();String ticket=UUID.randomUUID().toString();forms.put(ticket,f);
        return new JSONObject().put("ticket",ticket).put("fields",fields).put("kind",f.route?"route":"form");
    }
    private JSONObject saveForm(String ticket,JSONObject values) throws Exception {
        Form f=forms.get(ticket);if(f==null||f.expires<System.currentTimeMillis())throw new IOException("编辑表单已过期，请重新加载");
        Map<String,String> fields=new LinkedHashMap<>(f.defaults);
        if(f.route) {
            Set<String> members=new HashSet<>();boolean positive=false;
            for(int i=0;i<100;i++) {
                String c="list["+i+"][channel]",w="list["+i+"][weight]",channel=values.optString(c,fields.getOrDefault(c,""));
                fields.remove(c);fields.remove(w);if(channel.isEmpty())continue;
                if(!f.channels.contains(channel))throw new IOException("路由通道已失效，请重新加载后选择");
                if(!members.add(channel))throw new IOException("同一通道不能重复添加");
                String weight=values.optString(w,f.defaults.getOrDefault(w,"1"));
                if(!weight.matches("[0-9]{1,9}"))throw new IOException("权重必须为非负整数");
                positive|=Long.parseLong(weight)>0;fields.put(c,channel);fields.put(w,weight);
            }
            if(members.isEmpty())throw new IOException("轮询路由至少需要一个通道");
            if(!positive)throw new IOException("至少一个通道的权重必须大于 0");
        }else for(String key:f.editable)if(values.has(key)) {
            String value=values.optString(key);if(!isSecret(key)||!value.isEmpty())fields.put(key,value);
        }
        if(f.endpoint.startsWith("ajax_pay.php?act=saveChannel&")&&f.defaults.containsKey("type")&&(!Objects.equals(fields.get("type"),f.defaults.get("type"))||!Objects.equals(fields.get("plugin"),f.defaults.get("plugin")))) {
            JSONArray allowed=plugins(fields.getOrDefault("type",""));boolean matched=false;
            for(int i=0;i<allowed.length();i++)if(allowed.getJSONObject(i).getString("value").equals(fields.get("plugin")))matched=true;
            if(!matched)throw new IOException("支付插件与所选支付方式不匹配，请重新加载后选择");
        }
        for(String key:f.multiselect) {
            String value=fields.remove(key),base=key.endsWith("[]")?key.substring(0,key.length()-2):key;
            int n=0;Set<String> seen=new HashSet<>();for(String selected:value.split(","))if(!selected.isEmpty()&&seen.add(selected))fields.put(base+"["+(n++)+"]",id(selected));
        }
        JSONObject result=action(f.endpoint,fields,f.referer);forms.remove(ticket);return result;
    }
    private String path(String module) throws Exception {
        if(modulePaths.containsKey(module))return modulePaths.get(module);
        String fallback,label;
        switch(module){
            case "channels":fallback="pay_channel.php?tab=all";label="支付通道";break;
            case "payment_types":fallback="pay_type.php";label="支付方式";break;
            case "rolls":fallback="pay_roll.php?tab=all";label="通道轮询";break;
            case "proxies":fallback="proxy.php";label="中转代理";break;
            case "channel_monitor":fallback="pay_channel_monitor.php";label="通道监测";break;
            default:throw new IllegalArgumentException("模块无效");
        }
        // Discover actual PHP routes instead of inventing list APIs.
        try {
            Document nav=Jsoup.parse(api.document("index.php"));
            for(Element a:nav.select("a[href]"))if(a.text().contains(label)||(module.equals("channel_monitor")&&(a.text().contains("通道监控")||a.text().contains("通道检测")))||(module.equals("rolls")&&a.text().contains("通道轮训"))){
                String href=a.attr("href");
                if(href.matches("(?:\\./)?[A-Za-z0-9_]+\\.php(?:\\?[^#]*)?")) {fallback=href.replaceFirst("^\\./","");break;}
            }
        }catch(FutoonApi.SessionExpired expired){throw expired;}catch(IOException unavailableNavigation){/* Use the contract route. */}
        if((module.equals("channels")||module.equals("rolls"))&&!fallback.contains("tab="))fallback+=(fallback.contains("?")?"&":"?")+"tab=all";
        modulePaths.put(module,fallback);return fallback;
    }
    private JSONObject module(String module,int page,String query) throws Exception {
        if(page<1||page>100000)throw new IllegalArgumentException("页码无效");
        String route=path(module);Document doc=Jsoup.parse(api.document(route+(route.contains("?")?"&":"?")+"page="+page));
        if(module.equals("channel_monitor"))return monitor(doc,route,page,query);
        JSONArray tables=new JSONArray();
        for(Element table:doc.select("table")) {
            JSONArray headers=new JSONArray(),rows=new JSONArray();
            for(Element th:table.select("thead th"))headers.put(th.text());
            if(headers.length()==0)for(Element th:table.select("tr").first()!=null?table.select("tr").first().select("th"):new org.jsoup.select.Elements())headers.put(th.text());
            for(Element tr:table.select("tr")) {
                org.jsoup.select.Elements cells=tr.select("td");
                if(cells.isEmpty()||(!query.isEmpty()&&!tr.text().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))))continue;
                JSONArray cellsOut=new JSONArray();for(Element td:cells)cellsOut.put(td.text());
                String rid="";Element checkbox=tr.selectFirst("input[value]");
                if(checkbox!=null)rid=checkbox.attr("value");
                if(!FutoonIdentity.validId(rid)) {
                    for(Element control:tr.select("[onclick],a[href]")) {
                        Matcher m=Pattern.compile("[?&]id=([0-9]+)|\\(\\s*['\"]?([0-9]+)['\"]?\\s*[,)]").matcher(control.attr("onclick")+" "+control.attr("href"));
                        if(m.find()){rid=m.group(1)!=null?m.group(1):m.group(2);break;}
                    }
                    if(!FutoonIdentity.validId(rid)&&FutoonIdentity.validId(cells.first().text()))rid=cells.first().text();
                }
                rows.put(new JSONObject().put("id",rid).put("cells",cellsOut));
            }
            if(headers.length()>0||rows.length()>0)tables.put(new JSONObject().put("headers",headers).put("rows",rows));
        }
        if(tables.length()==0)throw new IOException("后台未返回业务表格，请检查权限或页面结构");
        return new JSONObject().put("tables",tables).put("module",module).put("page",page);
    }
    private JSONObject editor(String module,String record,String mode) throws Exception {
        String suffix=module.equals("channels")?"Channel":module.equals("payment_types")?"PayType":module.equals("rolls")?"Roll":"";
        if(suffix.isEmpty()||!Arrays.asList("detail","account","route").contains(mode)||mode.equals("account")&&!module.equals("channels")||mode.equals("route")&&!module.equals("rolls"))throw new IOException("此模块不支持该编辑操作");
        String route=path(module),rid=id(record),read=mode.equals("account")?"channelInfo":mode.equals("route")?"rollInfo":"get"+suffix;
        JSONObject result=api.json("ajax_pay.php?act="+read+"&id="+rid,null,route);
        if(mode.equals("route"))return routeForm(result,rid,route);
        String save="ajax_pay.php?act="+(mode.equals("account")?"saveChannelInfo":"save"+suffix)+"&id="+rid;
        Object raw=result.opt("data");
        if(raw instanceof String&&((String)raw).trim().startsWith("<"))return form(selectForm(Jsoup.parse((String)raw),module,true),save,route,new JSONObject().put("id",rid));
        JSONObject data=raw instanceof JSONObject?new JSONObject(raw.toString()):new JSONObject(result.toString());data.put("id",rid);
        Element template=selectForm(Jsoup.parse(api.document(route)),module,false);
        if(module.equals("payment_types")){for(Element name:template.select("[name=name]"))name.attr("readonly","");}
        if(module.equals("channels")) {
            Element plugin=template.selectFirst("select[name=plugin]");
            if(plugin!=null) {
                try {JSONArray options=plugins(data.optString("type"));for(int i=0;i<options.length();i++){JSONObject option=options.getJSONObject(i);plugin.appendElement("option").attr("value",option.getString("value")).text(option.getString("label"));}}
                catch(FutoonApi.SessionExpired expired){throw expired;}catch(Exception unavailable){/* Existing plugin remains editable with the page's options. */}
                boolean found=false;String current=data.optString("plugin");for(Element option:plugin.select("option"))if(option.val().equals(current))found=true;
                if(!current.isEmpty()&&!found)plugin.appendElement("option").attr("value",current).text(current);
            }
        }
        return form(template,save,route,data);
    }
    private JSONArray plugins(String type)throws Exception {
        JSONArray out=new JSONArray();if(type.isEmpty()||type.equals("0"))return out;
        JSONObject payload=api.json("ajax_pay.php?act=getPlugins&typeid="+id(type),null,path("channels"));JSONArray data=rows(payload,0);
        if(data==null)throw new IOException("后台未返回支付插件列表");Set<String> seen=new HashSet<>();
        for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row==null)continue;String value=row.optString("name",row.optString("value",row.optString("plugin","")));if(!value.isEmpty()&&seen.add(value))out.put(new JSONObject().put("value",value).put("label",row.optString("showname",row.optString("label",value))));}
        return out;
    }
    private Element selectForm(Document doc,String module,boolean fragment)throws Exception {
        Element best=null;int score=-1;
        for(Element candidate:doc.select("form")) {
            boolean matches=fragment||module.equals("channels")&&candidate.selectFirst("[name=plugin]")!=null&&candidate.selectFirst("[name=rate]")!=null||module.equals("payment_types")&&candidate.selectFirst("[name=device]")!=null&&candidate.selectFirst("[name=showname]")!=null||module.equals("rolls")&&candidate.selectFirst("[name=kind]")!=null&&candidate.selectFirst("[name=type]")!=null;
            int count=candidate.select("input[name],select[name],textarea[name]").size();if(matches&&count>score){best=candidate;score=count;}
        }
        if(best==null&&fragment&&doc.selectFirst("input[name],select[name],textarea[name]")!=null)return doc;
        if(best==null)throw new IOException("当前后台未提供此记录的编辑表单，请核对权限或后台版本");return best;
    }
    private JSONObject routeForm(JSONObject payload,String rid,String referer)throws Exception {
        JSONObject data=payload.optJSONObject("data");if(data==null)data=payload;
        JSONArray channels=data.optJSONArray("channels"),info=data.optJSONArray("info");
        if(channels==null)channels=payload.optJSONArray("channels");if(info==null)info=payload.optJSONArray("info");
        if(channels==null)throw new IOException("后台未返回可选路由通道，请检查轮询组支付方式及权限");
        Form f=new Form();f.route=true;f.endpoint="ajax_pay.php?act=saveRollInfo&id="+rid;f.referer=referer;
        JSONArray options=new JSONArray().put(new JSONObject().put("value","").put("label","不使用")),fields=new JSONArray();
        for(int i=0;i<channels.length();i++){JSONObject c=channels.optJSONObject(i);if(c==null)continue;String id=c.optString("id");if(!FutoonIdentity.validId(id))continue;f.channels.add(id);options.put(new JSONObject().put("value",id).put("label",c.optString("name","通道 "+id)+" (#"+id+")"));}
        int count=Math.max(1,info==null?0:info.length());if(count>100)throw new IOException("此轮询组路由超过 100 条，请使用后台编辑");
        for(int i=0;i<count;i++){
            JSONObject row=info==null?null:info.optJSONObject(i);String channel=row==null?"":row.optString("channel"),weight=row==null?"1":row.optString("weight","1");
            String c="list["+i+"][channel]",w="list["+i+"][weight]";f.defaults.put(c,channel);f.defaults.put(w,weight);
            JSONArray opts=new JSONArray(options.toString());if(!channel.isEmpty()&&!f.channels.contains(channel))opts.put(new JSONObject().put("value",channel).put("label","原通道已失效 #"+channel+"（请移除或更换）"));
            fields.put(field(c,"路由 "+(i+1)+" · 通道",channel,"select").put("options",opts));fields.put(field(w,"权重",weight,"number"));
        }
        return register(f,fields).put("routeOptions",options).put("recordId",rid);
    }
    private JSONObject monitor(Document doc,String referer,int page,String query)throws Exception {
        Map<String,String> filters=map("channel_id",query,"type","0","pageNumber",""+page,"pageSize","25","offset",""+((page-1)*25),"limit","25");
        Exception failure=null;
        for(boolean post:new boolean[]{true,false})try{
            String endpoint="ajax_pay.php?act=channelMonitor";
            if(!post)for(Map.Entry<String,String> entry:filters.entrySet())endpoint+="&"+enc(entry.getKey())+"="+enc(entry.getValue());
            JSONObject payload=api.json(endpoint,post?filters:null,referer);JSONArray source=rows(payload,0);
            if(source==null)throw new IOException("通道监测返回的数据没有列表，无法读取监测结果");
            List<String> columns=new ArrayList<>();for(Element th:doc.select("th[data-field]"))columns.add(th.attr("data-field"));
            if(columns.isEmpty()){Matcher m=Pattern.compile("field\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(doc.html());while(m.find())if(!columns.contains(m.group(1)))columns.add(m.group(1));}
            JSONArray out=new JSONArray();
            for(int i=0;i<source.length();i++) {
                Object item=source.opt(i);if(item instanceof String){String text=((String)item).trim();if(text.startsWith("{"))item=new JSONObject(text);else if(text.startsWith("["))item=new JSONArray(text);}
                JSONObject row;if(item instanceof JSONObject)row=new JSONObject(item.toString());else if(item instanceof JSONArray){row=new JSONObject();JSONArray arr=(JSONArray)item;for(int n=0;n<arr.length();n++)row.put(n<columns.size()?columns.get(n):"column_"+n,arr.opt(n));}else continue;
                for(Iterator<String> keys=row.keys();keys.hasNext();){String key=keys.next();Object v=row.opt(key);if(v instanceof String&&((String)v).contains("<"))row.put(key,Jsoup.parse((String)v).text());}
                String id=row.optString("id",row.optString("channel_id",row.optString("channel","")));if(FutoonIdentity.validId(id))row.put("id",FutoonIdentity.canonicalId(id));
                if(!row.has("name"))row.put("name",row.optString("channel_name",id));out.put(row);
            }
            return new JSONObject().put("rows",out).put("total",total(payload,out.length(),0)).put("page",page).put("module","channel_monitor");
        }catch(FutoonApi.SessionExpired expired){throw expired;}catch(Exception error){failure=error;}
        throw new IOException("通道监测加载失败："+(failure==null?"未知响应":failure.getMessage()),failure);
    }
    private static JSONArray rows(Object value,int depth)throws Exception {
        if(value==null||depth>8)return null;
        if(value instanceof JSONArray)return (JSONArray)value;
        if(value instanceof String){String text=((String)value).trim();if(text.startsWith("["))return new JSONArray(text);if(text.startsWith("{"))return rows(new JSONObject(text),depth+1);return null;}
        if(!(value instanceof JSONObject))return null;JSONObject object=(JSONObject)value;JSONArray empty=null;
        for(String key:new String[]{"rows","data","list","items","result","records","channels"})if(object.has(key)){JSONArray found=rows(object.opt(key),depth+1);if(found!=null){if(found.length()>0)return found;empty=found;}}
        if(empty!=null)return empty;
        JSONArray mapped=new JSONArray();boolean map=false;
        for(Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();if(Arrays.asList("code","msg","message","total","count").contains(key))continue;Object row=object.opt(key);if(!(row instanceof JSONObject)&&!(row instanceof JSONArray))return null;mapped.put(row);map=true;}
        return map?mapped:null;
    }
    private static int total(JSONObject payload,int fallback,int depth){
        if(depth>8)return fallback;for(String key:new String[]{"total","count"})if(payload.has(key))return payload.optInt(key,fallback);
        for(String key:new String[]{"data","result"})if(payload.optJSONObject(key)!=null)return total(payload.optJSONObject(key),fallback,depth+1);return fallback;
    }
}
