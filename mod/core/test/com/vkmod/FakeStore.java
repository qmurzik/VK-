package com.vkmod;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** In-memory SharedPreferences for JVM tests. */
public final class FakeStore implements SharedPreferences {
    private final Map<String, Object> m = new HashMap<>();
    @Override public Map<String, ?> getAll() { return new HashMap<>(m); }
    @Override public String getString(String k, String d) { Object v = m.get(k); return v instanceof String ? (String) v : d; }
    @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
    @Override public int getInt(String k, int d) { Object v = m.get(k); if (v == null) return d; if (v instanceof Integer) return (Integer) v; throw new ClassCastException(); }
    @Override public long getLong(String k, long d) { Object v = m.get(k); return v instanceof Long ? (Long) v : d; }
    @Override public float getFloat(String k, float d) { Object v = m.get(k); return v instanceof Float ? (Float) v : d; }
    @Override public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
    @Override public boolean contains(String k) { return m.containsKey(k); }
    @Override public Editor edit() {
        return new Editor() {
            final Map<String, Object> pending = new HashMap<>(); boolean clear;
            public Editor putString(String k, String v) { pending.put(k, v); return this; }
            public Editor putStringSet(String k, Set<String> v) { return this; }
            public Editor putInt(String k, int v) { pending.put(k, v); return this; }
            public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            public Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            public Editor remove(String k) { pending.put(k, null); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() { apply(); return true; }
            public void apply() { if (clear) m.clear(); for (Map.Entry<String, Object> e : pending.entrySet()) { if (e.getValue() == null) m.remove(e.getKey()); else m.put(e.getKey(), e.getValue()); } }
        };
    }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
}
