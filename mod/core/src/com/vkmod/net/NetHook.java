package com.vkmod.net;

import com.vkmod.core.Guard;
import com.vkmod.core.ModLog;
import com.vkmod.feat.MediaLog;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OkHttp integration. VK keeps {@code okhttp3.Interceptor} unobfuscated, so one proxy implementing it is
 * prepended to the client's application-interceptor list (see the smali hook in {@link com.vkmod.Hooks}).
 * Request/Response/ResponseBody are obfuscated and are resolved here by shape, not by name.
 */
public final class NetHook {
    private NetHook() {}

    private static volatile Object interceptor;
    private static volatile boolean modelReady, modelFailed;
    private static Class<?> ifaceCls, chainCls, reqCls, respCls, bodyCls, builderCls;
    private static Method reqM, proceedM;
    private static Field urlField, respBodyField;
    private static Method urlToString, ctM, stringM, bytesM, createStrM, createBytesM, buildM;
    private static boolean bytesFirst;
    private static Field bReq, bProto, bMsg, bCode, bBody;
    private static Object http11;

    /** Called from the OkHttpClient constructor hook with the builder's interceptor list. */
    public static List<Object> wrap(List<Object> original) {
        try {
            if (original == null) return null;
            if (!resolveInterface(original)) return original;
            for (Object o : original) if (o != null && Proxy.isProxyClass(o.getClass()) && o == interceptor) return original;
            ArrayList<Object> merged = new ArrayList<>(original.size() + 1);
            merged.add(interceptor);
            merged.addAll(original);
            return merged;
        } catch (Throwable t) {
            ModLog.e("wrap interceptors failed", t);
            return original;
        }
    }

    private static synchronized boolean resolveInterface(List<Object> original) {
        if (interceptor != null) return true;
        if (modelFailed) return false;
        try {
            Class<?> iface = null;
            try { iface = Class.forName("okhttp3.Interceptor"); } catch (Throwable ignored) { }
            if (iface == null) {
                for (Object o : original) {
                    if (o == null) continue;
                    for (Class<?> c : o.getClass().getInterfaces()) {
                        for (Method m : c.getMethods()) if (m.getName().equals("intercept") && m.getParameterTypes().length == 1) { iface = c; break; }
                        if (iface != null) break;
                    }
                    if (iface != null) break;
                }
            }
            if (iface == null) { modelFailed = true; ModLog.w("Interceptor interface not found"); return false; }
            ifaceCls = iface;
            Method intercept = null;
            for (Method m : iface.getMethods()) if (m.getName().equals("intercept") && m.getParameterTypes().length == 1) intercept = m;
            if (intercept == null) { modelFailed = true; return false; }
            chainCls = intercept.getParameterTypes()[0];
            respCls = intercept.getReturnType();
            // proceed(Request): the one-arg method of Chain whose return type is the response type
            for (Method m : chainCls.getMethods()) {
                if (m.getParameterTypes().length == 1 && m.getReturnType() == respCls && !m.getParameterTypes()[0].isPrimitive()) { proceedM = m; break; }
            }
            if (proceedM == null) { modelFailed = true; ModLog.w("Chain.proceed not found"); return false; }
            reqCls = proceedM.getParameterTypes()[0];
            for (Method m : chainCls.getMethods()) if (m.getParameterTypes().length == 0 && m.getReturnType() == reqCls) { reqM = m; break; }
            if (reqM == null) { modelFailed = true; ModLog.w("Chain.request not found"); return false; }
            interceptor = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("intercept") && args != null && args.length == 1) return handle(args[0]);
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "VKModInterceptor";
                    return null;
                }
            });
            ModLog.i("OkHttp interceptor ready: iface=" + iface.getName() + " req=" + reqCls.getName() + " resp=" + respCls.getName());
            return true;
        } catch (Throwable t) {
            modelFailed = true;
            ModLog.e("resolve OkHttp model failed", t);
            return false;
        }
    }

    private static Object handle(Object chain) throws Throwable {
        Object request = reqM.invoke(chain);
        String url = null;
        NetRules rules = null;
        try {
            url = urlOf(request);
            rules = NetConfig.rules();
        } catch (Throwable t) {
            ModLog.w("inspect request failed", t);
        }
        if (url != null && rules != null) {
            String reason = rules.blockReason(url);
            if (reason != null) {
                if (NetRules.wantsFakeSuccess(reason)) {
                    Object fake = null;
                    try { fake = fakeResponse(request); } catch (Throwable t) { ModLog.w("fake response failed", t); }
                    if (fake != null) { ModLog.i("blocked[" + reason + "] " + shorten(url)); return fake; }
                }
                ModLog.i("dropped[" + reason + "] " + shorten(url));
                throw new IOException("VKMod blocked request (" + reason + ")");
            }
        }
        Object response;
        try {
            response = proceedM.invoke(chain, request);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
        if (url == null || response == null) return response;
        try {
            Object rewritten = ResponseRewriter.rewrite(url, response);
            return rewritten != null ? rewritten : response;
        } catch (Throwable t) {
            ModLog.w("rewrite failed, original response passed", t);
            return response;
        }
    }

    private static String shorten(String url) {
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) : url;
    }

    private static String urlOf(Object request) throws Exception {
        if (request == null) return null;
        if (urlField == null && urlToString == null) {
            for (Field f : reqCls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class) continue;
                if (!f.getType().getName().startsWith("okhttp3.")) continue;
                f.setAccessible(true);
                Object v = f.get(request);
                if (v == null) continue;
                String s = String.valueOf(v);
                if (s.startsWith("http://") || s.startsWith("https://")) { urlField = f; break; }
            }
            if (urlField == null) { urlToString = Object.class.getMethod("toString"); }
        }
        if (urlField != null) { Object v = urlField.get(request); return v == null ? null : v.toString(); }
        String s = String.valueOf(request);
        int i = s.indexOf("url=");
        if (i < 0) return null;
        int e = s.indexOf(", ", i);
        if (e < 0) e = s.lastIndexOf('}');
        return s.substring(i + 4, e < 0 ? s.length() : e);
    }

    // ---- body model (resolved lazily from the first real response) ----

    static synchronized boolean resolveBody(Object response) {
        if (modelReady) return true;
        if (modelFailed) return false;
        try {
            for (Field f : respCls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t.isPrimitive() || t == String.class || !t.getName().startsWith("okhttp3.")) continue;
                if (!Modifier.isAbstract(t.getModifiers())) continue;
                boolean looksLikeBody = false;
                for (Method m : t.getMethods()) if (m.getName().equals("contentType") && m.getParameterTypes().length == 0) looksLikeBody = true;
                if (looksLikeBody) { respBodyField = f; bodyCls = t; break; }
            }
            if (respBodyField == null) { modelFailed = true; ModLog.w("ResponseBody field not found"); return false; }
            respBodyField.setAccessible(true);
            for (Method m : bodyCls.getMethods()) {
                if (m.getParameterTypes().length == 0) {
                    if (m.getName().equals("contentType")) ctM = m;
                    else if (m.getName().equals("string")) stringM = m;
                    else if (m.getName().equals("bytes")) bytesM = m;
                } else if (m.getParameterTypes().length == 2 && Modifier.isStatic(m.getModifiers()) && m.getName().equals("create")) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] == String.class) createStrM = m;
                    if (p[0] == byte[].class) { createBytesM = m; bytesFirst = true; }
                    else if (p[1] == byte[].class && createBytesM == null) { createBytesM = m; bytesFirst = false; }
                }
            }
            if (ctM == null || stringM == null || createStrM == null) { modelFailed = true; ModLog.w("ResponseBody methods not found"); return false; }
            resolveBuilder();
            modelReady = true;
            return true;
        } catch (Throwable t) {
            modelFailed = true;
            ModLog.e("resolve body model failed", t);
            return false;
        }
    }

    private static void resolveBuilder() {
        try {
            for (Class<?> c : respCls.getDeclaredClasses()) {
                if (Modifier.isInterface(c.getModifiers())) continue;
                Method build = null;
                for (Method m : c.getDeclaredMethods()) if (m.getReturnType() == respCls && m.getParameterTypes().length == 0 && !Modifier.isStatic(m.getModifiers())) build = m;
                if (build == null) continue;
                Field req = null, proto = null, msg = null, code = null, body = null;
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> t = f.getType();
                    if (t == reqCls) req = f;
                    else if (t.getName().equals("okhttp3.Protocol")) proto = f;
                    else if (t == String.class) msg = f;
                    else if (t == int.class) code = f;
                    else if (t == bodyCls) body = f;
                }
                if (req == null || proto == null || msg == null || code == null || body == null) continue;
                req.setAccessible(true); proto.setAccessible(true); msg.setAccessible(true); code.setAccessible(true); body.setAccessible(true);
                build.setAccessible(true);
                builderCls = c; buildM = build; bReq = req; bProto = proto; bMsg = msg; bCode = code; bBody = body;
                Class<?> protoCls = proto.getType();
                for (Object e : protoCls.getEnumConstants()) if (e.toString().equals("http/1.1")) http11 = e;
                return;
            }
        } catch (Throwable t) {
            ModLog.w("resolve response builder failed", t);
        }
    }

    /** A successful {"response":1} reply for API calls the mod swallows. */
    private static Object fakeResponse(Object request) throws Exception {
        if (!modelReady) resolveBody(null);
        if (builderCls == null || http11 == null || !modelReady) return null;
        java.lang.reflect.Constructor<?> ctor = builderCls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object b = ctor.newInstance();
        bReq.set(b, request);
        bProto.set(b, http11);
        bMsg.set(b, "OK");
        bCode.setInt(b, 200);
        bBody.set(b, createStrM.invoke(null, "{\"response\":1}", null));
        return buildM.invoke(b);
    }

    // ---- response rewriting ----

    static final class ResponseRewriter {
        private ResponseRewriter() {}

        static Object rewrite(String url, Object response) throws Exception {
            String method = ApiFilter.methodFromUrl(url);
            if (method == null) return null;
            FilterRules fr = NetConfig.filter();
            boolean media = MediaLog.enabled();
            if (fr.isEmpty() && !media) return null;
            if (!resolveBody(response)) return null;
            Object body = respBodyField.get(response);
            if (body == null) return null;
            Object ct = ctM.invoke(body);
            String cts = ct == null ? "" : ct.toString().toLowerCase(Locale.ROOT);
            if (cts.contains("msgpack")) return rewriteMsgpack(method, fr, media, response, body, ct);
            // Content-Type is required and must look like text: an absent/unknown type (binary, streamed, a file
            // download served through the same /method/ path) must NEVER be buffered into a String and rebuilt,
            // or non-UTF-8 bytes get silently corrupted by the String round-trip.
            if (cts.isEmpty() || (!cts.contains("json") && !cts.contains("javascript") && !cts.contains("text/"))) return null;
            String original = (String) stringM.invoke(body);
            if (original == null) return null;
            String filtered = null;
            try { filtered = ApiFilter.filter(method, original, fr); } catch (Throwable t) { ModLog.w("filter " + method + " failed", t); }
            if (media) { try { MediaLog.scan(method, filtered != null ? filtered : original); } catch (Throwable t) { ModLog.w("media scan failed", t); } }
            if (filtered != null) ModLog.i("filtered " + method + ": " + original.length() + " -> " + filtered.length());
            Object nb = createStrM.invoke(null, filtered != null ? filtered : original, ct);
            respBodyField.set(response, nb);
            return response;
        }

        private static Object rewriteMsgpack(String method, FilterRules fr, boolean media, Object response, Object body, Object ct) throws Exception {
            if (createBytesM == null || bytesM == null) return null;
            byte[] raw = (byte[]) bytesM.invoke(body);
            if (raw == null) return null;
            String json = Msgpack.toJson(raw);
            byte[] out = raw;
            if (json != null) {
                String filtered = null;
                try { filtered = ApiFilter.filter(method, json, fr); } catch (Throwable t) { ModLog.w("filter " + method + " failed", t); }
                if (media) { try { MediaLog.scan(method, filtered != null ? filtered : json); } catch (Throwable t) { ModLog.w("media scan failed", t); } }
                if (filtered != null) {
                    byte[] re = Msgpack.fromJson(filtered);
                    if (re != null) { out = re; ModLog.i("filtered " + method + " (msgpack): " + raw.length + " -> " + re.length); }
                }
            }
            Object nb = bytesFirst ? createBytesM.invoke(null, out, ct) : createBytesM.invoke(null, ct, out);
            respBodyField.set(response, nb);
            return response;
        }
    }
}
