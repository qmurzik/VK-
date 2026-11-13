package re.sova.qmods;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * No-op stand-in for VK's autoplay manager ({@code com.vk.libvideo.autoplay.a}) — BUG-0015.
 *
 * <p>VK's {@code AbstractAutoPlayDelegate.j()} returns the manager field, which becomes {@code null} once the
 * manager is released. RecyclerView keeps calling into the delegate after that (recycling the view of a just-sent
 * GIF/TG-sticker, focus changes, autoplay listener callbacks), and every one of the ~90 call sites dereferences the
 * result without a null check — each crash we fixed (alpha26/37/39) was just the next call site. The patched
 * {@code j()} now returns this inert proxy instead of {@code null}: calls on a released manager do nothing and
 * report "not playing", exactly what a detached player should do.</p>
 */
public final class Qvk3NullAutoplay {
    private static final String IFACE = "com.vk.libvideo.autoplay.a";
    private static volatile Object instance;

    private Qvk3NullAutoplay() {
    }

    /** The inert manager; never null unless the interface itself cannot be loaded. */
    public static Object get() {
        Object o = instance;
        if (o != null) return o;
        synchronized (Qvk3NullAutoplay.class) {
            if (instance == null) {
                try {
                    Class<?> c = Class.forName(IFACE, false, Qvk3NullAutoplay.class.getClassLoader());
                    instance = stub(c);
                } catch (Throwable t) {
                    return null;
                }
            }
            return instance;
        }
    }

    private static Object stub(Class<?> iface) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, HANDLER);
    }

    private static final InvocationHandler HANDLER = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            String n = m.getName();
            if (m.getDeclaringClass() == Object.class) {
                if ("equals".equals(n)) return args != null && args.length == 1 && proxy == args[0];
                if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                if ("toString".equals(n)) return "Qvk3NullAutoplay";
            }
            return empty(m.getReturnType());
        }
    };

    static Object empty(Class<?> r) {
        if (r == void.class) return null;
        if (r == boolean.class) return Boolean.FALSE;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == char.class) return (char) 0;
        if (r == String.class) return "";
        if (r.isInterface()) {
            try {
                return stub(r);   // nested listeners/handles stay inert instead of null
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
