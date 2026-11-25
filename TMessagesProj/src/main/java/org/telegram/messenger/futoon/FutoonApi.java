package org.telegram.messenger.futoon;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Backend contract adapted from FutoonPayAdmin 1.3.4. No Telegram credentials enter this client. */
public final class FutoonApi {
    public interface SessionPersistence {
        String load() throws Exception;
        void save(String value) throws Exception;
        void clear() throws Exception;
    }
    public static final class SessionExpired extends IOException {
        public SessionExpired() { super("富通登录已过期，请重新登录"); }
    }
    public static final class PermissionDenied extends IOException {
        public PermissionDenied() { super("没有查看或操作权限，请检查富通账号权限"); }
    }
    /** The server may already have applied the operation. Read back its state before retrying. */
    public static final class MutationOutcomeUnknown extends IOException {
        public MutationOutcomeUnknown() { this(null); }
        public MutationOutcomeUnknown(Throwable cause) { super("操作结果待核对，请重新读取后台状态，勿重复提交", cause); }
    }
    public static final int PAGE_SIZE = 25;
    private final URI base;
    private final com.futoonpay.admin.data.AdminRequestPolicy requestPolicy;
    private final SessionPersistence persistence;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final Map<String, Long> cookieExpiry = new LinkedHashMap<>();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).cache(null).build();

    public FutoonApi(String address, SessionPersistence persistence) throws Exception {
        this(address, persistence, false);
    }

    FutoonApi(String address, SessionPersistence persistence, boolean loopbackTest) throws Exception {
        base = normalizeAddress(address, loopbackTest);
        // HTTP is available only through the package-private loopback fixture constructor.
        HttpUrl baseUrl = HttpUrl.get(base.toString());
        requestPolicy = new com.futoonpay.admin.data.AdminRequestPolicy(
                baseUrl.newBuilder().scheme("https").port(baseUrl.port()).build().toString());
        this.persistence = persistence;
        refreshSession();
    }

    /** Refresh before a panel business dispatch; do not reload inside login's request sequence. */
    public synchronized void refreshSession() throws Exception {
        String saved = persistence.load();
        List<HttpCookie> restored = new ArrayList<>();
        Map<String, Long> restoredExpiry = new LinkedHashMap<>();
        if (saved != null && !saved.isEmpty()) {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                long expiry = row.optLong("expiry", -1);
                if (expiry >= 0 && expiry <= System.currentTimeMillis()) continue;
                HttpCookie cookie = new HttpCookie(row.getString("name"), row.getString("value"));
                cookie.setVersion(0);
                cookie.setDomain(row.getString("domain"));
                cookie.setPath(row.optString("path", "/"));
                cookie.setSecure(row.optBoolean("secure"));
                setHttpOnlyCompat(cookie, row.optBoolean("httpOnly"));
                if (expiry >= 0) cookie.setMaxAge(Math.max(0, (expiry - System.currentTimeMillis()) / 1000));
                restored.add(cookie);
                restoredExpiry.put(cookieKey(cookie), expiry);
            }
        }
        cookies.getCookieStore().removeAll();
        cookieExpiry.clear();
        for (HttpCookie cookie : restored) cookies.getCookieStore().add(base, cookie);
        cookieExpiry.putAll(restoredExpiry);
    }

    public static String normalizeAddress(String address) {
        return normalizeAddress(address, false).toString();
    }

    private static URI normalizeAddress(String address, boolean loopbackTest) {
        try {
            URI uri = new URI(address.trim());
            boolean local = loopbackTest && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !(local && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = "/";
            if (path.contains("\\") || path.contains("//")) throw new IllegalArgumentException();
            for (String part : path.split("/")) {
                if (part.equals(".") || part.equals("..")) throw new IllegalArgumentException();
            }
            if (!path.endsWith("/")) path += "/";
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null, uri.getHost(), uri.getPort(), path, null, null);
        } catch (Exception exception) {
            throw new IllegalArgumentException("请填写完整 HTTPS 后台目录，例如 https://pay.example.com/admin/");
        }
    }

    public synchronized boolean hasSession() {
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if ("admin_token".equals(cookie.getName()) && !cookie.hasExpired()) return true;
        }
        return false;
    }

    public synchronized void logout() throws Exception {
        // Logging out this integration does not sign out any Telegram account.
        try { request("login.php?logout", null, "login.php", true); }
        finally { clearSession(); }
    }

    private void clearSession() throws Exception {
        cookies.getCookieStore().removeAll();
        cookieExpiry.clear();
        persistence.clear();
    }

    public synchronized byte[] captcha() throws Exception {
        String html = text(request("login.php", null, "login.php"));
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                "<img[^>]+src=[\\\"']([^\\\"']*code\\.php[^\\\"']*)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        String path = match.find() ? match.group(1).replace("&amp;", "&") : "code.php?r=" + System.currentTimeMillis();
        return request(path, null, "login.php");
    }

    public synchronized String login(String username, String password, String captcha) throws Exception {
        for (HttpCookie cookie : new ArrayList<>(cookies.getCookieStore().getCookies())) {
            if ("admin_token".equals(cookie.getName())) cookies.getCookieStore().remove(base, cookie);
        }
        String raw = text(request("login.php", map("user", username, "pass", password, "code", captcha), "login.php"));
        if (!hasSession()) {
            java.util.regex.Matcher alert = java.util.regex.Pattern.compile("alert\\(['\\\"]([^'\\\"]+)").matcher(raw);
            throw new IOException(alert.find() ? alert.group(1) : "登录失败，请检查账号、密码和验证码");
        }
        return "登录成功";
    }

    public static final class Order {
        public final JSONObject data;
        public Order(JSONObject data) { this.data = data; }
        public String get(String key) {
            Object value = data.opt(key);
            return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        }
        public String id() { return get("trade_no"); }
        public String currency() { return first(get("currency"), first(get("currency_code"), "CNY")); }
        public String status() {
            switch (data.optInt("status", -1)) {
                case 0: return "待支付";
                case 1: return "已支付";
                case 2: return "已退款";
                case 3: return "已冻结";
                default: return "未知状态";
            }
        }
        public String caption() {
            String value = "请协助核查此订单\n系统订单号：" + id() + "\n商户订单号：" + get("out_trade_no");
            if (!get("api_trade_no").isEmpty()) value += "\n通道订单号：" + get("api_trade_no");
            if (!get("uid").isEmpty()) value += "\n商户 PID：" + get("uid");
            return value + "\n金额：" + currency() + " " + get("money") + "\n状态：" + status();
        }
        public static String first(String a, String b) { return a.isEmpty() ? b : a; }
    }

    public static final class SearchResult {
        public final List<Order> orders;
        public final int total;
        public final FutoonQuery.Kind kind;
        public final String warning;
        SearchResult(List<Order> orders, int total, FutoonQuery.Kind kind) {
            this(orders, total, kind, "");
        }
        SearchResult(List<Order> orders, int total, FutoonQuery.Kind kind, String warning) {
            this.orders = orders; this.total = total; this.kind = kind; this.warning = warning;
        }
    }

    public synchronized SearchResult search(FutoonQuery query, int page) throws Exception {
        return search(query, page, Collections.emptyMap());
    }

    public synchronized SearchResult search(FutoonQuery query, int page, Map<String, String> filters) throws Exception {
        if (page < 1) throw new IllegalArgumentException("页码无效");
        LinkedHashMap<String, Order> exact = new LinkedHashMap<>();
        int total = 0;
        String warning = "";
        for (FutoonQuery.Kind kind : query.searchKinds()) {
            // For an ambiguous short number, exact order matches take priority over a PID list.
            if (kind == FutoonQuery.Kind.PID && !exact.isEmpty()) break;
            Map<String, String> fields = map("pageNumber", String.valueOf(page), "pageSize", "25",
                    "offset", String.valueOf((page - 1) * PAGE_SIZE), "limit", "25", "type", "0", "dstatus", "-1");
            fields.putAll(filters);
            if (kind == FutoonQuery.Kind.PID && fields.containsKey("uid") && !query.value.equals(fields.get("uid"))) continue;
            if (kind == FutoonQuery.Kind.PID) fields.put("uid", query.value);
            else { fields.put("column", kind == FutoonQuery.Kind.SYSTEM_ORDER ? "trade_no" : kind == FutoonQuery.Kind.CHANNEL_ORDER ? "api_trade_no" : "out_trade_no"); fields.put("value", query.value); }
            JSONObject json;
            try { json = json("ajax_order.php?act=orderList", fields, "order.php"); }
            catch (SessionExpired expired) { throw expired; }
            catch (Exception unsupportedChannelColumn) {
                if (kind == FutoonQuery.Kind.CHANNEL_ORDER && query.kind == FutoonQuery.Kind.AUTO) {
                    warning = "通道订单号查询未完成：" + unsupportedChannelColumn.getMessage();
                    continue;
                }
                throw unsupportedChannelColumn;
            }
            JSONArray rows = json.optJSONArray("rows");
            if (rows == null) throw new IOException("后台未返回订单列表，请核对后台版本和账号权限");
            ArrayList<Order> found = new ArrayList<>();
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                Order order = new Order(rows.getJSONObject(i));
                String candidate = order.get(kind == FutoonQuery.Kind.PID ? "uid" : kind == FutoonQuery.Kind.SYSTEM_ORDER ? "trade_no" : kind == FutoonQuery.Kind.CHANNEL_ORDER ? "api_trade_no" : "out_trade_no");
                if (!order.id().isEmpty() && query.value.equals(candidate)
                        && (!filters.containsKey("uid") || filters.get("uid").equals(order.get("uid")))
                        && (!filters.containsKey("channel") || filters.get("channel").equals(order.get("channel")))
                        && (!filters.containsKey("dstatus") || filters.get("dstatus").equals("-1") || filters.get("dstatus").equals(order.get("status")))) found.add(order);
            }
            if (kind == FutoonQuery.Kind.PID) return new SearchResult(found, json.optInt("total", found.size()), kind, warning);
            for (Order order : found) exact.put(order.id(), order);
            total = Math.max(total, json.optInt("total", found.size()));
            if (query.kind != FutoonQuery.Kind.AUTO) return new SearchResult(found, total, kind);
        }
        return new SearchResult(new ArrayList<>(exact.values()), exact.size(), query.kind, warning);
    }

    public synchronized Order detail(Order summary) throws Exception {
        JSONObject json = json("ajax_order.php?act=order&trade_no=" + encode(summary.id()), null, "order.php");
        JSONObject row = json.optJSONObject("data");
        if (row == null) row = json;
        JSONObject merged = new JSONObject(summary.data.toString());
        java.util.Iterator<String> keys = row.keys();
        while (keys.hasNext()) { String key = keys.next(); merged.put(key, row.get(key)); }
        if (!summary.id().equals(merged.optString("trade_no"))) throw new IOException("订单详情与查询订单不匹配，请重新查询");
        return new Order(merged);
    }

    public synchronized String action(String action, String orderId) throws Exception {
        if (!action.equals("notify") && !action.equals("queryChannelOrder")) throw new IllegalArgumentException("不支持此操作");
        if (orderId == null || orderId.isEmpty()) throw new IllegalArgumentException("缺少订单号");
        JSONObject json = json("ajax_order.php?act=" + action,
                map(action.equals("queryChannelOrder") ? "tradeNo" : "trade_no", orderId), "order.php", true);
        if (!json.has("code")) throw new IOException("后台未确认操作结果，请在后台核对后重试");
        return json.optString("msg", "操作已完成") + (json.has("data") ? "\n" + json.get("data") : "");
    }

    synchronized JSONObject json(String endpoint, Map<String, String> fields, String referer) throws Exception {
        return json(endpoint, fields, referer, false);
    }

    synchronized JSONObject json(String endpoint, Map<String, String> fields, String referer, boolean once) throws Exception {
        String raw = text(request(endpoint, fields, referer, once)).trim();
        if (looksLikeLogin(raw)) { clearSession(); throw new SessionExpired(); }
        JSONObject result;
        try {
            result = raw.startsWith("[") ? new JSONObject().put("rows", new JSONArray(raw)).put("total", new JSONArray(raw).length()) : new JSONObject(raw);
        } catch (Exception exception) {
            if (once) throw new MutationOutcomeUnknown(exception);
            throw new IOException("后台返回了无法解析的数据，请检查后台目录和权限");
        }
        if (once) {
            try { Integer.parseInt(String.valueOf(result.opt("code"))); }
            catch (NumberFormatException unconfirmedStatus) { throw new MutationOutcomeUnknown(unconfirmedStatus); }
        }
        int code = result.optInt("code", 0);
        if (code == 403 || code == 401) { clearSession(); throw new SessionExpired(); }
        if (code != 0 && code != 200) throw new IOException(result.optString("msg", "富通查询失败"));
        return result;
    }

    synchronized String document(String path) throws Exception {
        String html = text(request(path, null, path));
        if (looksLikeLogin(html)) { clearSession(); throw new SessionExpired(); }
        return html;
    }

    private byte[] request(String path, Map<String, String> fields, String referer) throws Exception {
        return request(path, fields, referer, false);
    }

    private byte[] request(String path, Map<String, String> fields, String referer, boolean once) throws Exception {
        URI target = resolve(path);
        String safeReferer = resolve(referer).toString();
        String method = fields == null ? "GET" : "POST";
        byte[] body = fields == null ? null : encodeFields(fields).getBytes(StandardCharsets.UTF_8);
        try {
            for (int redirect = 0; redirect < 5; redirect++) {
                Request.Builder request = new Request.Builder().url(target.toString())
                        .header("User-Agent", "VineChat-Futoon/0.1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json,text/html,image/*;q=0.9,*/*;q=0.8")
                        .header("Referer", safeReferer);
                for (Map.Entry<String, List<String>> header : cookies.get(target, Collections.emptyMap()).entrySet()) {
                    if (!header.getValue().isEmpty()) request.header(header.getKey(), String.join("; ", header.getValue()));
                }
                request.method(method, body == null ? null : RequestBody.create(
                        body, MediaType.get("application/x-www-form-urlencoded; charset=UTF-8")));
                AtomicBoolean exchanged = new AtomicBoolean(false);
                OkHttpClient exchangeClient = client.newBuilder().addNetworkInterceptor(chain -> {
                    resolve(chain.request().url().toString());
                    // OkHttp can perform HTTP follow-ups (503/421) even with connection retries off.
                    if (!exchanged.compareAndSet(false, true)) throw new IOException("后台响应要求再次发送请求，已停止自动重发");
                    return chain.proceed(chain.request());
                }).build();
                try (Response response = exchangeClient.newCall(request.build()).execute()) {
                    int status = response.code();
                    receiveCookies(target, response.headers().toMultimap());
                    if (status == 401) { clearSession(); throw new SessionExpired(); }
                    if (status == 403) throw new PermissionDenied();
                    if (status >= 300 && status <= 399) {
                        if (once) throw new MutationOutcomeUnknown();
                        String location = response.header("Location");
                        if (location == null) throw new IOException("后台跳转缺少目标地址");
                        if ((status == 307 || status == 308) && method.equals("POST")) {
                            throw new IOException("后台登录要求重发密码，已停止跳转");
                        }
                        HttpUrl next = response.request().url().resolve(location);
                        if (next == null) throw new IOException("后台跳转地址无效");
                        target = resolve(next.toString());
                        if (status == 303 || ((status == 301 || status == 302) && method.equals("POST"))) { method = "GET"; body = null; }
                        continue;
                    }
                    if (status < 200 || status >= 300) throw new IOException("富通请求失败（HTTP " + status + "）");
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) throw new IOException("后台返回内容为空");
                    try (InputStream input = responseBody.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (output.size() + count > 8 * 1024 * 1024) throw new IOException("后台返回内容过大");
                            output.write(buffer, 0, count);
                        }
                        return output.toByteArray();
                    }
                }
            }
            throw new IOException("后台跳转次数过多，请检查后台地址");
        } catch (Exception error) {
            if (once && !(error instanceof SessionExpired) && !(error instanceof PermissionDenied) && !(error instanceof MutationOutcomeUnknown)) {
                throw new MutationOutcomeUnknown(error);
            }
            throw error;
        }
    }

    private void receiveCookies(URI target, Map<String, List<String>> headers) throws Exception {
        cookies.put(target, headers);
        // Keep the existing browser-style Cookie header and encrypted persistence schema.
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) cookie.setVersion(0);
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey() == null || !header.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (String rawCookie : header.getValue()) for (HttpCookie cookie : HttpCookie.parse(rawCookie)) {
                if (cookie.getDomain() == null) cookie.setDomain(target.getHost());
                if (cookie.getPath() == null) {
                    String requestPath = target.getPath();
                    cookie.setPath(requestPath.substring(0, requestPath.lastIndexOf('/') + 1));
                }
                cookieExpiry.put(cookieKey(cookie), cookie.getMaxAge() < 0 ? -1 : System.currentTimeMillis() + cookie.getMaxAge() * 1000);
            }
        }
        saveCookies();
    }

    private URI resolve(String path) throws IOException {
        try {
            if ("https".equals(base.getScheme())) return URI.create(requestPolicy.resolveUrl(path));
            HttpUrl url = HttpUrl.get(base.toString()).resolve(path);
            if (url == null || !"http".equals(url.scheme())) throw new IOException("后台接口地址无效");
            // The HTTP-only fixture uses the production boundary rules with a scheme projection.
            // Preserve its effective port so an omitted :80 still compares correctly.
            requestPolicy.requireAllowedUrl(url.newBuilder().scheme("https").port(url.port()).build().toString());
            return url.uri();
        } catch (IllegalArgumentException exception) { throw new IOException("后台接口地址无效"); }
    }

    private void saveCookies() throws Exception {
        JSONArray array = new JSONArray();
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if (cookie.hasExpired()) continue;
            array.put(new JSONObject().put("name", cookie.getName()).put("value", cookie.getValue())
                    .put("domain", cookie.getDomain() == null ? base.getHost() : cookie.getDomain())
                    .put("path", cookie.getPath() == null ? "/" : cookie.getPath()).put("secure", cookie.getSecure())
                    .put("httpOnly", isHttpOnlyCompat(cookie)).put("expiry", cookieExpiry.containsKey(cookieKey(cookie)) ? cookieExpiry.get(cookieKey(cookie)) : -1));
        }
        persistence.save(array.toString());
    }

    private static boolean looksLikeLogin(String text) {
        return text.contains("<") && text.matches("(?is).*name\\s*=\\s*['\\\"]?user['\\\"\\s>].*")
                && text.matches("(?is).*name\\s*=\\s*['\\\"]?pass['\\\"\\s>].*");
    }
    private static String cookieKey(HttpCookie cookie) { return cookie.getName() + "|" + cookie.getDomain() + "|" + cookie.getPath(); }
    // These accessors were added to Android in API 24; the host app also supports API 23.
    private static void setHttpOnlyCompat(HttpCookie cookie, boolean value) {
        try { HttpCookie.class.getMethod("setHttpOnly", boolean.class).invoke(cookie, value); }
        catch (ReflectiveOperationException unavailableOnAndroid6) { /* No scripting engine uses this private cookie jar. */ }
    }
    private static boolean isHttpOnlyCompat(HttpCookie cookie) {
        try { return (Boolean) HttpCookie.class.getMethod("isHttpOnly").invoke(cookie); }
        catch (ReflectiveOperationException unavailableOnAndroid6) { return false; }
    }
    private static String text(byte[] data) { return new String(data, StandardCharsets.UTF_8); }
    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private static String encodeFields(Map<String, String> fields) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (builder.length() > 0) builder.append('&');
            builder.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
        }
        return builder.toString();
    }
    private static Map<String, String> map(String... values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }
}
