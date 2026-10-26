package okhttp3;
/** Response (with the Builder as nested class "a", fields named like VK's). */
public final class u {
    public final p a; public final Protocol b; public final String c; public final int d; public v g;
    public u(p a, Protocol b, String c, int d, v g) { this.a = a; this.b = b; this.c = c; this.d = d; this.g = g; }
    public static class a {
        public p a; public Protocol b; public String d; public v g; public int c = -1;
        public final u c() { return new u(a, b, d, c, g); }
        public final void b(v x) { g = x; }
    }
}
