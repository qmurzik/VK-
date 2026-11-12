package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * QVK 3.0 update manifest (pure Java, unit-tested).
 *
 * Wire format (any static HTTPS host or mirror can serve it):
 * <pre>
 * { "payload": "&lt;JSON string&gt;", "key_id": "main", "signature": "&lt;base64 DER ECDSA-P256-SHA256 over payload bytes&gt;" }
 * payload = { "schema": 3, "package": "ru.qmods.qvk", "channel": "stable",
 *             "releases": [ { "version_code", "version_name", "vk_base": "8.100", "min_sdk", "apk_size",
 *                             "sha256", "signer_sha256", "urls": [..mirrors..], "notes", "published" } ] }
 * </pre>
 * Optional "kill": [ { "feature": "send_retry", "min_code": 0, "max_code": 23840, "reason": "…" } ] — features the team
 * switches off remotely for the listed builds when they misbehave (see {@link Qvk3Kill}); honoured only when signed.
 * The signature covers the exact payload bytes, so no JSON canonicalisation is needed. Releases for another
 * package or another VK base are rejected — QVK stays on VK 8.100.
 */
public final class Qvk3UpdateManifest {
    public static final String PACKAGE = "ru.qmods.qvk";
    public static final String VK_BASE = "8.100";

    public static final class Release {
        public final long versionCode;
        public final String versionName;
        public final String vkBase;
        public final int minSdk;
        public final long size;
        public final String sha256;
        public final String signerSha256;
        public final List<String> urls;
        public final String notes;
        public final String published;
        /** "alpha" (every build, the default) or "stable" (builds the team marked as stable). */
        public final String channel;

        Release(JSONObject o) {
            versionCode = o.optLong("version_code");
            versionName = o.optString("version_name");
            vkBase = o.optString("vk_base", VK_BASE);
            minSdk = o.optInt("min_sdk", 28);
            size = o.optLong("apk_size");
            sha256 = o.optString("sha256").toLowerCase(java.util.Locale.ROOT);
            signerSha256 = o.optString("signer_sha256").toLowerCase(java.util.Locale.ROOT);
            List<String> u = new ArrayList<>();
            JSONArray arr = o.optJSONArray("urls");
            if (arr != null) for (int i = 0; i < arr.length(); i++) u.add(arr.optString(i));
            if (u.isEmpty() && !o.optString("apk_url").isEmpty()) u.add(o.optString("apk_url"));
            urls = u;
            notes = o.optString("notes", o.optString("text"));
            published = o.optString("published");
            channel = "stable".equals(o.optString("channel")) ? "stable" : "alpha";
        }

        void validate() {
            if (versionCode <= 0 || versionCode > Integer.MAX_VALUE) throw new IllegalArgumentException("Неверный номер версии");
            if (versionName.isEmpty() || versionName.length() > 120) throw new IllegalArgumentException("Неверное имя версии");
            if (size <= 0 || size > 512L << 20) throw new IllegalArgumentException("Неверный размер APK");
            if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Нет SHA-256 APK");
            if (!signerSha256.isEmpty() && !signerSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Неверный отпечаток подписи");
            if (urls.isEmpty() || urls.size() > 16) throw new IllegalArgumentException("Нет адресов загрузки");
            for (String u : urls) httpsUrl(u);
        }
    }

    /** A feature switched off remotely for builds min..max (inclusive; max 0 = no upper bound). */
    public static final class Kill {
        public final String feature;
        public final long minCode, maxCode;
        public final String reason;

        Kill(JSONObject o) {
            feature = o.optString("feature");
            minCode = o.optLong("min_code", 0);
            maxCode = o.optLong("max_code", 0);
            String r = o.optString("reason").trim();
            reason = r.length() > 300 ? r.substring(0, 300) : r;
        }

        boolean valid() {
            return feature.matches("[a-z0-9_.]{1,48}") && minCode >= 0 && maxCode >= 0 && (maxCode == 0 || maxCode >= minCode);
        }

        public boolean applies(long code) {
            return code >= minCode && (maxCode == 0 || code <= maxCode);
        }
    }

    public final String channel;
    public final List<Release> releases;
    /** Remote switch-offs listed in the payload (all builds); use {@link #killsFor}. */
    public final List<Kill> kills;
    /** True when the payload signature was verified with a trusted key. */
    public final boolean signed;
    public final String keyId;

    private Qvk3UpdateManifest(String channel, List<Release> releases, boolean signed, String keyId) {
        this(channel, releases, signed, keyId, new ArrayList<>());
    }

    private Qvk3UpdateManifest(String channel, List<Release> releases, boolean signed, String keyId, List<Kill> kills) {
        this.channel = channel;
        this.releases = releases;
        this.kills = kills;
        this.signed = signed;
        this.keyId = keyId;
    }

    public static void httpsUrl(String u) {
        try {
            URI uri = new URI(u);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || u.length() > 2048) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("Нужен HTTPS-адрес: " + u);
        }
    }

    /**
     * Parses and checks a manifest. {@code trustedKeys} are base64 X.509 EC public keys. When at least one
     * trusted key is configured, an unsigned or badly signed manifest is rejected; without keys the manifest
     * is accepted as "unsigned" (APK SHA-256 and signer certificate are still enforced by the installer).
     */
    public static Qvk3UpdateManifest parse(String body, List<String> trustedKeys) throws Exception {
        JSONObject outer = new JSONObject(body);
        String payload;
        boolean signed = false;
        String keyId = "";
        if (outer.has("payload")) {
            payload = outer.getString("payload");
            keyId = outer.optString("key_id");
            String sig = outer.optString("signature");
            if (!sig.isEmpty() && trustedKeys != null) {
                for (String k : trustedKeys) {
                    if (verify(payload, sig, k)) {
                        signed = true;
                        break;
                    }
                }
            }
        } else {
            payload = body;
        }
        if (trustedKeys != null && !trustedKeys.isEmpty() && !signed) {
            throw new SecurityException("Подпись манифеста не прошла проверку — обновление отклонено");
        }
        JSONObject p = new JSONObject(payload);
        if (p.optInt("schema") != 3) throw new IllegalArgumentException("Неподдерживаемая версия манифеста");
        if (!PACKAGE.equals(p.optString("package"))) throw new IllegalArgumentException("Манифест для другого приложения");
        JSONArray arr = p.optJSONArray("releases");
        if (arr == null || arr.length() == 0 || arr.length() > 200) throw new IllegalArgumentException("В манифесте нет выпусков");
        List<Release> rs = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Release r = new Release(arr.getJSONObject(i));
            r.validate();
            rs.add(r);
        }
        List<Kill> ks = new ArrayList<>();
        JSONArray ka = p.optJSONArray("kill");
        if (ka != null) for (int i = 0; i < ka.length() && i < 64; i++) {
            JSONObject o = ka.optJSONObject(i);
            if (o == null) continue;
            Kill k = new Kill(o);
            if (k.valid()) ks.add(k);
        }
        return new Qvk3UpdateManifest(p.optString("channel", "stable"), rs, signed, keyId, ks);
    }

    /** Switch-offs that apply to build {@code code}. Only a signed manifest may switch features off. */
    public List<Kill> killsFor(long code) {
        List<Kill> out = new ArrayList<>();
        if (!signed) return out;
        for (Kill k : kills) if (k.applies(code)) out.add(k);
        return out;
    }

    public static boolean verify(String payload, String signatureB64, String publicKeyB64) {
        try {
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64.trim())));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(key);
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(signatureB64.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    /** Newest release that is newer than {@code installed}, built on VK 8.100 and compatible with {@code sdk}. */
    public Release best(long installed, int sdk) {
        return best(installed, sdk, false);
    }

    /** stableOnly: only releases marked "stable" (the «Стабильная» update channel). */
    public Release best(long installed, int sdk, boolean stableOnly) {
        Release best = null;
        for (Release r : releases) {
            if (r.versionCode <= installed || r.minSdk > sdk || !VK_BASE.equals(r.vkBase)) continue;
            if (stableOnly && !"stable".equals(r.channel)) continue;
            if (best == null || r.versionCode > best.versionCode) best = r;
        }
        return best;
    }

    /** Human explanation why nothing is offered (for the updates screen). */
    public String whyNothing(long installed, int sdk) {
        boolean newer = false, otherBase = false, sdkTooLow = false;
        for (Release r : releases) {
            if (r.versionCode <= installed) continue;
            newer = true;
            if (!VK_BASE.equals(r.vkBase)) otherBase = true;
            if (r.minSdk > sdk) sdkTooLow = true;
        }
        if (!newer) return "Установлена последняя версия";
        if (otherBase) return "Новые выпуски собраны не на VK " + VK_BASE + " и не предлагаются";
        if (sdkTooLow) return "Новый выпуск требует более новую версию Android";
        return "Подходящих выпусков нет";
    }

    /** Converts a QVK 2.x community/GitHub release object into a single-release manifest (unsigned). */
    public static Qvk3UpdateManifest fromLegacy(JSONObject legacy) throws Exception {
        JSONObject r = new JSONObject()
                .put("version_code", legacy.getLong("version_code"))
                .put("version_name", legacy.getString("version_name"))
                .put("apk_size", legacy.getLong("apk_size"))
                .put("sha256", legacy.getString("sha256"))
                .put("urls", new JSONArray().put(legacy.getString("apk_url")))
                .put("notes", legacy.optString("text"));
        Release rel = new Release(r);
        rel.validate();
        List<Release> l = new ArrayList<>();
        l.add(rel);
        return new Qvk3UpdateManifest("community", l, false, "");
    }
}
