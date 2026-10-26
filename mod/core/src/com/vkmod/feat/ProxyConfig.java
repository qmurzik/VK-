package com.vkmod.feat;

import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/** Process-wide proxy from a "socks5://user:pass@host:port" style string (applies to new connections). */
public final class ProxyConfig {
    private ProxyConfig() {}

    public static final class Parsed {
        public Proxy.Type type; public String host, user, pass; public int port;
    }

    public static Parsed parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            Parsed p = new Parsed();
            String rest = s;
            p.type = Proxy.Type.HTTP;
            int i = s.indexOf("://");
            if (i > 0) {
                String sch = s.substring(0, i).toLowerCase();
                if (sch.startsWith("socks")) p.type = Proxy.Type.SOCKS;
                rest = s.substring(i + 3);
            }
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                String cred = rest.substring(0, at);
                rest = rest.substring(at + 1);
                int c = cred.indexOf(':');
                p.user = c >= 0 ? cred.substring(0, c) : cred;
                p.pass = c >= 0 ? cred.substring(c + 1) : "";
            }
            int colon = rest.lastIndexOf(':');
            if (colon <= 0) return null;
            p.host = rest.substring(0, colon);
            p.port = Integer.parseInt(rest.substring(colon + 1).replaceAll("/.*$", ""));
            return p.port > 0 && p.port < 65536 ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void apply() {
        if (!Registry.isOn("proxy")) return;
        final Parsed p = parse(Registry.str("proxy"));
        if (p == null) { ModLog.w("proxy value is not valid, ignored"); return; }
        final Proxy proxy = new Proxy(p.type, InetSocketAddress.createUnresolved(p.host, p.port));
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                String host = uri.getHost();
                if (host == null || host.equals("localhost") || host.startsWith("127.")) return Collections.singletonList(Proxy.NO_PROXY);
                return Collections.singletonList(proxy);
            }
            @Override public void connectFailed(URI uri, SocketAddress sa, IOException ioe) { ModLog.w("proxy connect failed: " + ioe.getMessage()); }
        });
        if (p.user != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(p.user, p.pass == null ? new char[0] : p.pass.toCharArray());
                }
            });
        }
        System.setProperty("java.net.useSystemProxies", "false");
        ModLog.i("proxy set: " + p.type + " " + p.host + ":" + p.port);
    }
}
