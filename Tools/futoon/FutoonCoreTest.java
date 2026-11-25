package org.telegram.messenger.futoon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Run with a JDK, org.json and OkHttp. All requests hit a disposable local fixture. */
public final class FutoonCoreTest {
    private static int checks;
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    private interface Throwing { void run() throws Exception; }
    private static void fails(Throwing action, String description) throws Exception {
        boolean thrown = false;
        try { action.run(); } catch (Exception exception) { thrown = true; }
        check(thrown, description);
    }
    private static void uncertain(Throwing action, String description) throws Exception {
        boolean thrown = false;
        try { action.run(); } catch (FutoonApi.MutationOutcomeUnknown exception) {
            thrown = exception.getMessage().contains("核对");
        }
        check(thrown, description);
    }
    private static final class Memory implements FutoonApi.SessionPersistence {
        String value = "";
        public String load() { return value; }
        public void save(String value) { this.value = value; }
        public void clear() { value = ""; }
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final ArrayList<Map<String, String>> requests = new ArrayList<>();
        boolean loginPage, forbidden, arrayRows, mismatch, redirectOutside, missingAck;
        boolean disconnectMutation;
        int mutationStatus, loginRedirect;
        String redirectTarget = "landing.php", mutationBody = "{\"code\":0,\"msg\":\"fixture ok\"}";
        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/admin/", this::handle);
            server.start();
        }
        String address() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/admin/"; }
        void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> fields = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                fields.putAll(parse(exchange.getRequestURI().getRawQuery()));
                fields.put("method", exchange.getRequestMethod());
                fields.put("cookie", String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")));
                fields.put("path", exchange.getRequestURI().getPath());
                requests.add(fields);
                String path = exchange.getRequestURI().getPath();
                if (forbidden) { send(exchange, 403, "denied"); return; }
                if (path.endsWith("login.php")) {
                    if (fields.containsKey("logout")) { send(exchange, 200, "ok"); return; }
                    if (exchange.getRequestMethod().equals("POST")) {
                        if (!"user".equals(fields.get("user")) || !"correct".equals(fields.get("pass"))) {
                            send(exchange, 200, "<script>alert('账号密码错误')</script>"); return;
                        }
                        exchange.getResponseHeaders().add("Set-Cookie", "admin_token=fixture; Path=/admin/; Max-Age=3600; HttpOnly");
                        if (loginRedirect != 0) {
                            exchange.getResponseHeaders().add("Location", "landing.php");
                            send(exchange, loginRedirect, ""); return;
                        }
                        send(exchange, 200, "登陆管理中心成功"); return;
                    }
                    exchange.getResponseHeaders().add("Set-Cookie", "PHPSESSID=captcha; Path=/admin/");
                    if (redirectOutside) {
                        exchange.getResponseHeaders().add("Location", "https://example.org/steal");
                        send(exchange, 302, ""); return;
                    }
                    send(exchange, 200, "<img src='code.php?r=1'><input name='user'><input name='pass'>"); return;
                }
                if (path.endsWith("code.php")) { send(exchange, 200, "fake captcha bytes"); return; }
                if (path.endsWith("read_redirect.php")) {
                    exchange.getResponseHeaders().add("Location", redirectTarget);
                    send(exchange, 302, ""); return;
                }
                if (path.endsWith("landing.php")) { send(exchange, 200, "{\"code\":0,\"msg\":\"landing\"}"); return; }
                if (loginPage) { send(exchange, 200, "<form action='login.php'><input name='user'><input name='pass'></form>"); return; }
                String act = fields.get("act");
                if ("fixtureWrite".equals(act) || "resetUser".equals(act)) {
                    if (disconnectMutation) { exchange.close(); return; }
                    if (mutationStatus != 0) {
                        exchange.getResponseHeaders().add("Location", redirectTarget);
                        exchange.getResponseHeaders().add("Retry-After", "0");
                        send(exchange, mutationStatus, ""); return;
                    }
                    send(exchange, 200, mutationBody); return;
                }
                if ("orderList".equals(act)) {
                    JSONArray rows = new JSONArray();
                    if ("1234".equals(fields.get("uid"))) {
                        rows.put(order("SYS001", "CUSTOM001", "1234")).put(order("SYS002", "CUSTOM002", "1234"));
                    } else if ("SYS001".equals(fields.get("value")) && "trade_no".equals(fields.get("column"))) {
                        // A backend fuzzy match must not become a wrong exact order result.
                        rows.put(order("SYS001", "CUSTOM001", "1234")).put(order("SYS0012", "CUSTOMX", "9999"));
                    } else if ("CUSTOM001".equals(fields.get("value")) && "out_trade_no".equals(fields.get("column"))) {
                        rows.put(order("SYS001", "CUSTOM001", "1234"));
                    } else if ("25".equals(fields.get("value")) && "out_trade_no".equals(fields.get("column"))) {
                        rows.put(order("SYS25", "25", "1234"));
                    }
                    send(exchange, 200, arrayRows ? rows.toString() : new JSONObject().put("rows", rows).put("total", rows.length()).toString());
                } else if ("order".equals(act)) {
                    send(exchange, 200, new JSONObject().put("code", 0).put("data", order(mismatch ? "WRONG" : fields.get("trade_no"), "CUSTOM001", "1234").put("channelname", "测试通道")).toString());
                } else if ("notify".equals(act) || "queryChannelOrder".equals(act)) {
                    send(exchange, 200, missingAck ? "{\"msg\":\"unknown\"}" : "{\"code\":0,\"msg\":\"fixture ok\"}");
                } else send(exchange, 200, "{\"code\":-1,\"msg\":\"unknown\"}");
            } catch (Exception exception) { send(exchange, 500, exception.toString()); }
        }
        public void close() { server.stop(0); }
    }
    private static JSONObject order(String system, String merchant, String pid) throws Exception {
        return new JSONObject().put("trade_no", system).put("out_trade_no", merchant).put("uid", pid)
                .put("money", "25.60").put("currency", "USD").put("status", 1).put("channel", "88");
    }
    private static Map<String, String> parse(String raw) throws Exception {
        HashMap<String, String> result = new HashMap<>();
        if (raw != null && !raw.isEmpty()) for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], "UTF-8"), parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "");
        }
        return result;
    }
    private static void send(HttpExchange exchange, int status, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    public static void main(String[] args) throws Exception {
        FutoonQuery.Kind auto = FutoonQuery.Kind.AUTO;
        check(FutoonQuery.parse(" PID：１２３４ ", auto).kind == FutoonQuery.Kind.PID, "fullwidth PID prefix");
        check(FutoonQuery.parse("商户订单号: CUSTOM001", auto).kind == FutoonQuery.Kind.MERCHANT_ORDER, "merchant prefix");
        check(FutoonQuery.parse("out_trade_no=CUSTOM001", auto).value.equals("CUSTOM001"), "API field prefix");
        check(FutoonQuery.parse("系统订单号: SYS001", auto).kind == FutoonQuery.Kind.SYSTEM_ORDER, "system prefix");
        check(FutoonQuery.parse("1234", auto).searchKinds().size() == 4, "short number is ambiguous across three order fields and PID");
        check(FutoonQuery.parse("00123456789012345678", auto).value.equals("00123456789012345678"), "leading zero preserved");
        check(FutoonQuery.parse("SYS\u200b001", auto).value.equals("SYS001"), "invisible copy character removed");
        check(FutoonQuery.parse("SYS001", auto).searchKinds().size() == 3, "alphanumeric order searches all three fields");
        check(FutoonQuery.parse("商户订单号: SHOP.2026/001", auto).value.equals("SHOP.2026/001"), "merchant reference punctuation preserved");
        fails(() -> FutoonQuery.parse("", auto), "blank selection rejected");
        fails(() -> FutoonQuery.parse("1234 5678", auto), "multiple IDs rejected");
        fails(() -> FutoonQuery.parse("PID: abc", auto), "non-numeric PID rejected");
        fails(() -> FutoonQuery.parse("https://other.example/", auto), "URL is not an ID");
        check(FutoonApi.normalizeAddress("https://pay.example/admin").equals("https://pay.example/admin/"), "backend directory normalized");
        fails(() -> FutoonApi.normalizeAddress("http://pay.example/admin"), "production requires HTTPS");
        fails(() -> FutoonApi.normalizeAddress("https://user:password@pay.example/admin"), "URL credentials rejected");
        fails(() -> FutoonApi.normalizeAddress("https://pay.example/admin/../other/"), "traversal rejected");
        fails(() -> FutoonApi.normalizeAddress("https://pay.example/admin?token=secret"), "query URL rejected");
        try (Fixture fixture = new Fixture()) {
            Memory memory = new Memory();
            FutoonApi api = new FutoonApi(fixture.address(), memory, true);
            check(!api.hasSession(), "initially unauthenticated");
            check(new String(api.captcha(), StandardCharsets.UTF_8).equals("fake captcha bytes"), "captcha uses extracted URL");
            fails(() -> api.login("user", "wrong", "1234"), "failed login does not create session");
            api.login("user", "correct", "1234");
            check(api.hasSession(), "successful login retained");
            check(memory.value.contains("admin_token"), "cookie passed to persistence");
            long originalExpiry = 0;
            JSONArray savedCookies = new JSONArray(memory.value);
            for (int i = 0; i < savedCookies.length(); i++) if (savedCookies.getJSONObject(i).getString("name").equals("admin_token")) originalExpiry = savedCookies.getJSONObject(i).getLong("expiry");
            FutoonApi.SearchResult pid = api.search(FutoonQuery.parse("PID:1234", auto), 1);
            check(pid.orders.size() == 2 && pid.kind == FutoonQuery.Kind.PID, "PID returns an order list");
            check(fixture.requests.get(fixture.requests.size() - 1).get("uid").equals("1234"), "PID uses uid filter");
            check(fixture.requests.get(fixture.requests.size() - 1).get("cookie").contains("admin_token=fixture"), "session sent to own backend");
            savedCookies = new JSONArray(memory.value);
            for (int i = 0; i < savedCookies.length(); i++) if (savedCookies.getJSONObject(i).getString("name").equals("admin_token")) check(savedCookies.getJSONObject(i).getLong("expiry") == originalExpiry, "requests must not extend cookie expiry");
            FutoonApi.SearchResult system = api.search(FutoonQuery.parse("SYS001", auto), 1);
            check(system.orders.size() == 1 && system.orders.get(0).id().equals("SYS001"), "fuzzy extra result excluded");
            FutoonApi.SearchResult merchant = api.search(FutoonQuery.parse("CUSTOM001", auto), 1);
            check(merchant.orders.size() == 1 && merchant.orders.get(0).id().equals("SYS001"), "merchant number lookup");
            FutoonApi.SearchResult shortOrder = api.search(FutoonQuery.parse("25", auto), 1);
            check(shortOrder.orders.size() == 1 && shortOrder.orders.get(0).id().equals("SYS25"), "short order prioritized over PID");
            check(api.search(FutoonQuery.parse("MISSING", auto), 1).orders.isEmpty(), "missing order remains empty");
            fixture.arrayRows = true;
            check(api.search(FutoonQuery.parse("PID:1234", auto), 1).orders.size() == 2, "top-level array backend supported");
            fixture.arrayRows = false;
            FutoonApi.Order detail = api.detail(system.orders.get(0));
            check(detail.get("channelname").equals("测试通道"), "nested detail parsed");
            check(detail.caption().contains("USD 25.60") && detail.caption().contains("SYS001"), "caption uses correct order currency");
            fixture.mismatch = true;
            fails(() -> api.detail(system.orders.get(0)), "mismatched detail cannot be operated on");
            fixture.mismatch = false;
            api.action("queryChannelOrder", "SYS001");
            check(fixture.requests.get(fixture.requests.size() - 1).get("tradeNo").equals("SYS001"), "channel query uses tradeNo");
            api.action("notify", "SYS001");
            check(fixture.requests.get(fixture.requests.size() - 1).get("trade_no").equals("SYS001"), "notify uses trade_no");
            fixture.missingAck = true;
            fails(() -> api.action("notify", "SYS001"), "missing action acknowledgement is an error");
            fixture.missingAck = false;
            fails(() -> api.action("refund", "SYS001"), "unsupported mutation blocked");
            FutoonApi restored = new FutoonApi(fixture.address(), memory, true);
            check(restored.hasSession(), "session can be restored");
            fixture.loginPage = true;
            fails(() -> restored.search(FutoonQuery.parse("SYS001", auto), 1), "HTML login page detected");
            check(!restored.hasSession() && memory.value.isEmpty(), "expired auth cleared");
            fixture.loginPage = false;
            fixture.forbidden = true;
            fails(() -> api.search(FutoonQuery.parse("SYS001", auto), 1), "permission error surfaced");
            fixture.forbidden = false;
            fixture.redirectOutside = true;
            fails(api::captcha, "cross-origin redirect blocked before following");
            fixture.redirectOutside = false;
            api.logout();
            check(!api.hasSession(), "logout clears integration auth");
        }
        reliabilityChecks();
        sessionRefreshChecks();
        System.out.println("PASS: " + checks + " Futoon query and HTTP integration checks");
    }

    private static void reliabilityChecks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Memory memory = new Memory();
            FutoonApi api = new FutoonApi(fixture.address(), memory, true);
            fixture.loginRedirect = 302;
            api.login("user", "correct", "1234");
            check(fixture.requests.size() == 2, "login follows one bounded redirect");
            Map<String, String> landing = fixture.requests.get(1);
            check("GET".equals(landing.get("method")) && !landing.containsKey("pass"), "login redirect discards password body");
            check(landing.get("cookie").contains("admin_token=fixture"), "redirect preserves browser session cookies");
            check(new FutoonApi(fixture.address(), memory, true).hasSession(), "redirect session uses the existing persistence format");
            int before = fixture.requests.size();
            check(api.json("read_redirect.php", null, "index.php").optString("msg").equals("landing"), "read follows an in-directory redirect");
            check(fixture.requests.size() - before == 2, "read uses only its explicit redirect hops");
            for (String target : new String[]{"/outside.php", "/admin-lookalike/landing.php", "landing.php#fragment", "%252e%252e/outside.php"}) {
                fixture.redirectTarget = target;
                before = fixture.requests.size();
                fails(() -> api.document("read_redirect.php"), "unsafe read redirect is blocked: " + target);
                check(fixture.requests.size() - before == 1, "unsafe redirect destination receives no request");
            }
            fixture.redirectTarget = "landing.php";
            for (String method : new String[]{"GET", "POST"}) {
                Map<String, String> fields = method.equals("POST") ? Map.of("value", "支付 & #1") : null;
                for (int status : new int[]{301, 302, 303, 307, 308, 503}) {
                    fixture.mutationStatus = status;
                    before = fixture.requests.size();
                    uncertain(() -> api.json("ajax.php?act=fixtureWrite&id=42", fields, "edit.php", true), "unconfirmed " + method + " / " + status);
                    check(fixture.requests.size() - before == 1, "mutation never replays " + method + " / " + status);
                }
                fixture.mutationStatus = 0;
                fixture.disconnectMutation = true;
                before = fixture.requests.size();
                uncertain(() -> api.json("ajax.php?act=fixtureWrite&id=42", fields, "edit.php", true), "disconnect retains uncertain mutation semantics");
                check(fixture.requests.size() - before == 1, "disconnect mutation sent once: " + method);
                fixture.disconnectMutation = false;
            }
            before = fixture.requests.size();
            fixture.mutationStatus = 302;
            FutoonBusinessApi business = new FutoonBusinessApi(api);
            uncertain(() -> business.call("merchant.resetKey", new JSONObject().put("uid", "42")), "business GET actions opt into mutation policy");
            check(fixture.requests.size() - before == 1, "business mutation redirect is not followed");
            fixture.mutationStatus = 0;
            for (String body : new String[]{"{}", "{\"code\":null}", "{\"code\":true}", "{\"code\":0.5}", "upstream disconnected"}) {
                fixture.mutationBody = body;
                uncertain(() -> api.json("ajax.php?act=fixtureWrite", null, "edit.php", true), "malformed confirmation remains uncertain");
            }
            fixture.mutationBody = "{\"code\":\"0\"}";
            check(api.json("ajax.php?act=fixtureWrite", null, "edit.php", true).optInt("code", -1) == 0, "string status remains compatible");
            fixture.mutationBody = "{\"code\":-1,\"msg\":\"explicit rejection\"}";
            boolean rejected = false;
            try { api.json("ajax.php?act=fixtureWrite", null, "edit.php", true); }
            catch (FutoonApi.MutationOutcomeUnknown unknown) { throw new AssertionError("explicit rejection misclassified", unknown); }
            catch (IOException expected) { rejected = expected.getMessage().contains("explicit rejection"); }
            check(rejected, "explicit server rejection differs from an uncertain result");
        }
    }

    private static void sessionRefreshChecks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Memory shared = new Memory();
            FutoonApi first = new FutoonApi(fixture.address(), shared, true);
            FutoonApi second = new FutoonApi(fixture.address(), shared, true);
            first.login("user", "correct", "1234");
            check(!second.hasSession(), "an existing Java client retains its snapshot until refreshed");
            second.refreshSession();
            check(second.hasSession(), "a second client refreshes a shared login without reconstruction");
            second.json("landing.php", null, "index.php");
            check(fixture.requests.get(fixture.requests.size() - 1).get("cookie").contains("admin_token=fixture"), "refreshed shared cookie reaches the next request");

            long expiry = System.currentTimeMillis() + 120000;
            shared.value = new JSONArray().put(new JSONObject().put("name", "admin_token").put("value", "kotlin-updated")
                    .put("domain", "127.0.0.1").put("path", "/admin/").put("secure", false).put("httpOnly", true).put("expiry", expiry)).toString();
            second.refreshSession();
            second.json("landing.php", null, "index.php");
            String header = fixture.requests.get(fixture.requests.size() - 1).get("cookie");
            check(header.contains("admin_token=kotlin-updated") && !header.contains("admin_token=fixture"), "PanelCookieJar-style update replaces the old Java token");
            check(new JSONArray(shared.value).getJSONObject(0).getLong("expiry") == expiry, "refresh and read preserve the cookie's absolute expiry");

            second.refreshSession();
            fails(() -> second.login("user", "wrong", "1234"), "refresh does not resurrect a removed token inside failed login");
            check(!second.hasSession(), "failed relogin leaves no stale admin token");
            check(!fixture.requests.get(fixture.requests.size() - 1).get("cookie").contains("admin_token"), "login request omits the previous token after explicit refresh");

            first.refreshSession();
            check(!first.hasSession(), "the original client refreshes token removal from the other client");
            first.login("user", "correct", "1234");
            second.refreshSession();
            shared.clear();
            second.refreshSession();
            check(!second.hasSession(), "clearing shared persistence clears a long-lived Java cookie snapshot");
            second.json("landing.php", null, "index.php");
            check(!fixture.requests.get(fixture.requests.size() - 1).get("cookie").contains("admin_token"), "a cleared shared token is not sent on later reads");

            shared.value = new JSONArray().put(new JSONObject().put("name", "admin_token").put("value", "expired")
                    .put("domain", "127.0.0.1").put("path", "/admin/").put("expiry", System.currentTimeMillis() - 1000)).toString();
            second.refreshSession();
            check(!second.hasSession(), "refresh discards expired Kotlin-style cookies");
        }
    }
}
