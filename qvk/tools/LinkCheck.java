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
                        if (!appType(mr.getDefiningClass())) continue;
                        if (!classes.containsKey(mr.getDefiningClass())) {
                            problems.add(where + ": missing class " + mr.getDefiningClass());
                        } else if (!resolveMethod(mr.getDefiningClass(), mr, new HashSet<>())) {
                            problems.add(where + ": missing method " + mr.getDefiningClass() + "->" + mr.getName()
                                    + "(" + String.join("", mr.getParameterTypes()) + ")" + mr.getReturnType());
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

    private static boolean resolveMethod(String type, MethodReference ref, Set<String> seen) {
        if (!seen.add(type)) return false;
        ClassDef c = classes.get(type);
        if (c == null) return !appType(type); // hierarchy left the APK: framework class, trust javac
        for (Method m : c.getMethods()) {
            if (m.getName().equals(ref.getName()) && m.getReturnType().equals(ref.getReturnType())
                    && same(m.getParameterTypes(), ref.getParameterTypes())) return true;
        }
        if (c.getSuperclass() != null && resolveMethod(c.getSuperclass(), ref, seen)) return true;
        for (String i : c.getInterfaces()) if (resolveMethod(i, ref, seen)) return true;
        return false;
    }

    private static boolean resolveField(String type, FieldReference ref, Set<String> seen) {
        if (!seen.add(type)) return false;
        ClassDef c = classes.get(type);
        if (c == null) return !appType(type);
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
