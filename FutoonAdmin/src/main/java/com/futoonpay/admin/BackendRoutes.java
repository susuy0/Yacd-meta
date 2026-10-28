package com.futoonpay.admin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The official backend moved to {@code https://host/svc_<hash>/} and serves every page and AJAX endpoint without the
 * {@code .php} suffix ({@code order}, {@code ulist}, {@code ajax_user?act=userList}, ...). Old directories keep the
 * suffix. All request paths in the app are still written with {@code .php}; this is the single place that maps them.
 */
public final class BackendRoutes {
    private static final Pattern SVC_DIRECTORY = Pattern.compile("^(/svc_[0-9a-fA-F]+)(?:/.*)?$");
    private BackendRoutes() {}

    /** True when {@code basePath} is a new style {@code /svc_<hash>/} directory. */
    public static boolean usesPlainRoutes(String basePath) {
        return basePath != null && SVC_DIRECTORY.matcher(basePath).matches();
    }

    /** Reduces a pasted page address such as {@code /svc_x/home} to the directory {@code /svc_x/}; others pass through. */
    public static String directory(String path) {
        if (path == null) return null;
        Matcher matcher = SVC_DIRECTORY.matcher(path);
        return matcher.matches() ? matcher.group(1) + "/" : path;
    }

    /** Drops {@code .php} from the path part (never from the query string) when the backend uses plain routes. */
    public static String route(String basePath, String path) {
        if (path == null || !usesPlainRoutes(basePath)) return path;
        int query = path.indexOf('?');
        String head = query < 0 ? path : path.substring(0, query);
        String tail = query < 0 ? "" : path.substring(query);
        return (head.endsWith(".php") ? head.substring(0, head.length() - 4) : head) + tail;
    }
}
