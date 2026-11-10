import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static linker check for the rebuilt APK.
 *
 * For every class in the checked set (QVK 3.0 classes + every class touched by a smali patch) it resolves
 * each referenced app class, method and field against the dex files that will actually ship. References
 * into the Android framework / Java runtime are left to javac (our Java code is compiled against
 * android.jar), so this catches the errors javac cannot see: wrong signatures written by hand in smali
 * patches, classes that went missing during the rebuild, and duplicate class definitions across dex files
 * (which make ART pick one copy silently).
 *
 * Usage: java -cp apktool.jar:. LinkCheck <apk> <class-prefix-or-descriptor>...
 */
public final class LinkCheck {
    private static final Map<String, ClassDef> classes = new HashMap<>();

    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(apk, Opcodes.forApi(28));
        Map<String, String> owner = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (String entry : container.getDexEntryNames()) {
            DexBackedDexFile dex = container.getEntry(entry).getDexFile();
            for (ClassDef c : dex.getClasses()) {
                String prev = owner.put(c.getType(), entry);
                if (prev != null) duplicates.add(c.getType() + " in " + prev + " and " + entry);
                classes.put(c.getType(), c);
            }
        }
        Set<String> problems = new TreeSet<>();
        for (String d : duplicates) problems.add("DUPLICATE CLASS " + d);

        List<String> scopes = new ArrayList<>();
        for (int i = 1; i < args.length; i++) scopes.add(args[i]);
        int checkedClasses = 0, checkedRefs = 0;
        for (ClassDef c : classes.values()) {
            if (!inScope(c.getType(), scopes)) continue;
            checkedClasses++;
            if (c.getSuperclass() != null && appType(c.getSuperclass()) && !classes.containsKey(c.getSuperclass()))
                problems.add(c.getType() + ": missing superclass " + c.getSuperclass());
            for (Method m : c.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                for (Instruction insn : impl.getInstructions()) {
                    if (!(insn instanceof ReferenceInstruction)) continue;
                    Reference ref = ((ReferenceInstruction) insn).getReference();
                    String where = c.getType() + "->" + m.getName();
                    checkedRefs++;
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (mr.getDefiningClass().startsWith("[")) continue; // array methods (clone) live in the runtime
                        if (!appType(mr.getDefiningClass())) continue;
                        if (!classes.containsKey(mr.getDefiningClass())) {
                            problems.add(where + ": missing class " + mr.getDefiningClass());
                        } else {
                            Method target = findMethod(mr.getDefiningClass(), mr, new HashSet<>());
                            if (target == null && !resolveMethod(mr.getDefiningClass(), mr, new HashSet<>())) {
                                problems.add(where + ": missing method " + mr.getDefiningClass() + "->" + mr.getName()
                                        + "(" + String.join("", mr.getParameterTypes()) + ")" + mr.getReturnType());
                            } else if (target != null && !accessible(c.getType(), target.getDefiningClass(), target.getAccessFlags())) {
                                problems.add(where + ": illegal access to " + target.getDefiningClass() + "->" + target.getName()
                                        + " (" + ((target.getAccessFlags() & 0x2) != 0 ? "private" : "package-private") + ")");
                            }
                        }
                    } else if (ref instanceof FieldReference) {
                        FieldReference fr = (FieldReference) ref;
                        if (!appType(fr.getDefiningClass())) continue;
                        if (!classes.containsKey(fr.getDefiningClass())) {
                            problems.add(where + ": missing class " + fr.getDefiningClass());
                        } else if (!resolveField(fr.getDefiningClass(), fr, new HashSet<>())) {
                            problems.add(where + ": missing field " + fr.getDefiningClass() + "->" + fr.getName() + ":" + fr.getType());
                        }
                    } else if (ref instanceof TypeReference) {
                        String t = ((TypeReference) ref).getType();
                        while (t.startsWith("[")) t = t.substring(1);
                        if (t.startsWith("L") && appType(t) && !classes.containsKey(t))
                            problems.add(where + ": missing class " + t);
                    }
                }
            }
        }
        System.out.println("linkcheck: " + classes.size() + " classes in APK, checked " + checkedClasses
                + " classes / " + checkedRefs + " references");
        for (String p : problems) System.out.println("  " + p);
        if (!problems.isEmpty()) {
            System.out.println("linkcheck: FAILED (" + problems.size() + " problems)");
            System.exit(1);
        }
        System.out.println("linkcheck: OK");
    }

    private static boolean inScope(String type, List<String> scopes) {
        for (String s : scopes) {
            if (s.endsWith(";") ? type.equals(s) : type.startsWith(s)) return true;
        }
        return false;
    }

    /** Framework and runtime classes are not in the APK; they are verified by javac against android.jar. */
    private static boolean appType(String t) {
        return !(t.startsWith("Ljava/") || t.startsWith("Ljavax/") || t.startsWith("Landroid/")
                || t.startsWith("Ldalvik/") || t.startsWith("Lorg/json/") || t.startsWith("Lorg/xmlpull/")
                || t.startsWith("Lorg/w3c/") || t.startsWith("Lorg/xml/") || t.startsWith("Lsun/")
                || t.startsWith("Llibcore/") || t.startsWith("Lorg/apache/http/"))
                || t.startsWith("Landroidx/") || t.startsWith("Landroid/support/");
    }

    private static java.net.URLClassLoader framework;

    /** Loads framework classes from android.jar (java.* from the running JDK) to check inherited members. */
    private static Class<?> frameworkClass(String type) {
        String name = type.substring(1, type.length() - 1).replace('/', '.');
        try {
            if (framework == null) {
                String jar = System.getProperty("android.jar");
                framework = new java.net.URLClassLoader(new java.net.URL[]{new File(jar).toURI().toURL()}, ClassLoader.getPlatformClassLoader());
            }
            return Class.forName(name, false, framework);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean frameworkHasMethod(String type, MethodReference ref) {
        Class<?> c = frameworkClass(type);
        if (c == null) return System.getProperty("android.jar") == null; // cannot check without android.jar
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            if (declares(k, ref)) return true;
            for (Class<?> i : k.getInterfaces()) if (declares(i, ref)) return true;
        }
        return false;
    }

    private static boolean declares(Class<?> k, MethodReference ref) {
        try {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(ref.getName()) || m.getParameterCount() != ref.getParameterTypes().size()) continue;
                if (!desc(m.getReturnType()).equals(ref.getReturnType())) continue;
                boolean ok = true;
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) if (!desc(ps[i]).equals(ref.getParameterTypes().get(i).toString())) ok = false;
                if (ok) return true;
            }
            if (ref.getName().equals("<init>")) {
                for (java.lang.reflect.Constructor<?> m : k.getDeclaredConstructors()) {
                    if (m.getParameterCount() != ref.getParameterTypes().size()) continue;
                    boolean ok = true;
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i = 0; i < ps.length; i++) if (!desc(ps[i]).equals(ref.getParameterTypes().get(i).toString())) ok = false;
                    if (ok) return true;
                }
            }
        } catch (Throwable t) {
            return true; // unreadable class: do not report
        }
        return false;
    }

    private static String desc(Class<?> c) {
        if (c.isArray()) return "[" + desc(c.getComponentType());
        if (c == void.class) return "V";
        if (c == int.class) return "I";
        if (c == boolean.class) return "Z";
        if (c == long.class) return "J";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static boolean resolveMethod(String type, MethodReference ref, Set<String> seen) {
        if (!seen.add(type)) return false;
        ClassDef c = classes.get(type);
        if (c == null) return !appType(type) && frameworkHasMethod(type, ref); // left the APK: check the framework class
        for (Method m : c.getMethods()) {
            if (m.getName().equals(ref.getName()) && m.getReturnType().equals(ref.getReturnType())
                    && same(m.getParameterTypes(), ref.getParameterTypes())) return true;
        }
        if (c.getSuperclass() != null && resolveMethod(c.getSuperclass(), ref, seen)) return true;
        for (String i : c.getInterfaces()) if (resolveMethod(i, ref, seen)) return true;
        return false;
    }

    /** Method definition inside the APK (null when it lives in the framework or is missing). */
    private static Method findMethod(String type, MethodReference ref, Set<String> seen) {
        if (!seen.add(type)) return null;
        ClassDef c = classes.get(type);
        if (c == null) return null;
        for (Method m : c.getMethods()) {
            if (m.getName().equals(ref.getName()) && m.getReturnType().equals(ref.getReturnType())
                    && same(m.getParameterTypes(), ref.getParameterTypes())) return m;
        }
        if (c.getSuperclass() != null) {
            Method m = findMethod(c.getSuperclass(), ref, seen);
            if (m != null) return m;
        }
        for (String i : c.getInterfaces()) {
            Method m = findMethod(i, ref, seen);
            if (m != null) return m;
        }
        return null;
    }

    private static String pkg(String type) {
        int i = type.lastIndexOf('/');
        return i < 0 ? "" : type.substring(0, i);
    }

    /** private: only the same class (or its nest via synthetic accessors); package-private: same package. */
    private static boolean accessible(String caller, String owner, int flags) {
        if ((flags & 0x1) != 0 || (flags & 0x4) != 0) return true; // public / protected (protected checked by javac)
        if ((flags & 0x2) != 0) return caller.equals(owner); // d8 (min-api 28) uses synthetic accessors, never nestmates
        return pkg(caller).equals(pkg(owner));
    }

    private static boolean resolveField(String type, FieldReference ref, Set<String> seen) {
        if (!seen.add(type)) return false;
        ClassDef c = classes.get(type);
        if (c == null) return !appType(type); // framework fields: javac-checked for Java code
        for (Field f : c.getFields()) {
            if (f.getName().equals(ref.getName()) && f.getType().equals(ref.getType())) return true;
        }
        if (c.getSuperclass() != null && resolveField(c.getSuperclass(), ref, seen)) return true;
        for (String i : c.getInterfaces()) if (resolveField(i, ref, seen)) return true;
        return false;
    }

    private static boolean same(List<? extends CharSequence> a, List<? extends CharSequence> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).toString().equals(b.get(i).toString())) return false;
        return true;
    }
}
