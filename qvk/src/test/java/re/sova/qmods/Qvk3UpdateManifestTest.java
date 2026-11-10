package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class Qvk3UpdateManifestTest {
    static String sha = "ab".repeat(32);

    static JSONObject release(long code, String base, int minSdk) throws Exception {
        return new JSONObject().put("version_code", code).put("version_name", "8.100-qvk-" + code).put("vk_base", base)
                .put("min_sdk", minSdk).put("apk_size", 1000).put("sha256", sha)
                .put("urls", new JSONArray().put("https://a.example/qvk.apk").put("https://b.example/qvk.apk"));
    }

    static String payload(JSONObject... rs) throws Exception {
        JSONArray a = new JSONArray();
        for (JSONObject r : rs) a.put(r);
        return new JSONObject().put("schema", 3).put("package", "ru.qmods.qvk").put("releases", a).toString();
    }

    static KeyPair keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    static String sign(String payload, KeyPair k) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(k.getPrivate());
        s.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    static String wrap(String payload, String sig) throws Exception {
        return new JSONObject().put("payload", payload).put("key_id", "main").put("signature", sig).toString();
    }

    @Test public void signedManifestVerifies() throws Exception {
        KeyPair k = keys();
        String p = payload(release(23800, "8.100", 28));
        List<String> trusted = Collections.singletonList(Base64.getEncoder().encodeToString(k.getPublic().getEncoded()));
        Qvk3UpdateManifest m = Qvk3UpdateManifest.parse(wrap(p, sign(p, k)), trusted);
        assertTrue(m.signed);
        assertEquals(23800, m.best(23776, 34).versionCode);
        assertEquals(2, m.best(23776, 34).urls.size());
    }

    @Test(expected = SecurityException.class)
    public void tamperedPayloadIsRejected() throws Exception {
        KeyPair k = keys();
        String p = payload(release(23800, "8.100", 28));
        String sig = sign(p, k);
        String evil = p.replace("a.example", "evil.example");
        Qvk3UpdateManifest.parse(wrap(evil, sig), Collections.singletonList(Base64.getEncoder().encodeToString(k.getPublic().getEncoded())));
    }

    @Test(expected = SecurityException.class)
    public void unsignedRejectedWhenKeysConfigured() throws Exception {
        KeyPair k = keys();
        Qvk3UpdateManifest.parse(payload(release(23800, "8.100", 28)), Collections.singletonList(Base64.getEncoder().encodeToString(k.getPublic().getEncoded())));
    }

    @Test public void unsignedAcceptedWithoutKeysButMarked() throws Exception {
        Qvk3UpdateManifest m = Qvk3UpdateManifest.parse(payload(release(23800, "8.100", 28)), Collections.emptyList());
        assertFalse(m.signed);
    }

    @Test public void onlyVk8100AndCompatibleSdkOffered() throws Exception {
        Qvk3UpdateManifest m = Qvk3UpdateManifest.parse(payload(release(23900, "8.135", 28), release(23850, "8.100", 35), release(23810, "8.100", 28)), null);
        assertEquals(23810, m.best(23776, 34).versionCode);
        assertNull(m.best(23810, 34));
        assertEquals("Установлена последняя версия", m.whyNothing(23900, 34));
        Qvk3UpdateManifest only8135 = Qvk3UpdateManifest.parse(payload(release(23900, "8.135", 28)), null);
        assertNull(only8135.best(23776, 34));
        assertTrue(only8135.whyNothing(23776, 34).contains("8.100"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void otherPackageRejected() throws Exception {
        Qvk3UpdateManifest.parse(new JSONObject(payload(release(1, "8.100", 28))).put("package", "com.vkontakte.android").toString(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void httpMirrorRejected() throws Exception {
        JSONObject r = release(23800, "8.100", 28).put("urls", new JSONArray().put("http://plain.example/qvk.apk"));
        Qvk3UpdateManifest.parse(payload(r), null);
    }

    @Test public void legacyCommunityReleaseConverts() throws Exception {
        JSONObject legacy = new JSONObject().put("version_code", 23790).put("version_name", "8.100-qvk-2.6.19").put("apk_size", 5)
                .put("sha256", sha).put("apk_url", "https://qmods.ru/x.apk");
        Qvk3UpdateManifest m = Qvk3UpdateManifest.fromLegacy(legacy);
        assertEquals(23790, m.best(23776, 30).versionCode);
    }
}
