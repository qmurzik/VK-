package okhttp3;
public enum Protocol { HTTP_1_1("http/1.1"), HTTP_2("h2"); private final String s; Protocol(String s) { this.s = s; } @Override public String toString() { return s; } }
