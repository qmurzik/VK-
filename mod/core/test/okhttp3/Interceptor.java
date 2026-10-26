package okhttp3;
import java.io.IOException;
/** Mirrors the shape of VK's (R8-processed) OkHttp: Chain is the nested interface "a". */
public interface Interceptor {
    interface a { u a(p pVar) throws IOException; p request(); d call(); int d(); a c(int i); }
    u intercept(a aVar) throws IOException;
}
