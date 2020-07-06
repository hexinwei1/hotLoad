package reload.insjar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class InstrumentationTest {

    /**
     * 当当前代码的jar包 被attach到一个JVM上时，执行此方法
     * jar包需要设置参数：Agentmain-class: javaAgent.InstrumentationTest
     * @param agentArgs 传来的参数 newCode.jar包路径
     * @param instrumentation
     */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
        System.out.println("agentmain:agentArgs=" + agentArgs);

        List<ClassPackage> classPackages = getClassPackages(agentArgs);
        for (ClassPackage classPackage : classPackages) {
            // add类转换器
            instrumentation.addTransformer(new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                        ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                    return classPackage.classCode;
                }
            }, true);
            // 指定类转换器要转换的类
            instrumentation.retransformClasses(classPackage.classObject);
        }

    }

    private static List<ClassPackage> getClassPackages(String path) throws IOException {
        List classPackages = new ArrayList();

        JarFile jarFile = new JarFile(path);
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                Class classObject = getClassObject(jarEntry);
                byte[] classCode = getClassByte(jarFile, jarEntry);
                classPackages.add(new ClassPackage(classObject, classCode));
            }
        }

        return classPackages;
    }

    private static byte[] getClassByte(JarFile jarFile, JarEntry jarEntry) {
        try {
            InputStream in = jarFile.getInputStream(jarEntry);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int size = 0;
            while((size = in.read()) != -1) {
                out.write(size);
            }
            in.close();
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Class getClassObject(JarEntry jarEntry) {
        try {
            return Class.forName(getClassName(jarEntry));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getClassName(JarEntry jarEntry) {
        StringBuffer className = new StringBuffer();
        String[] split = jarEntry.getName().split("/");
        for (String s : split) {
            className.append(s).append(".");
        }
        // 切除最后的 ".class."
        return className.substring(0, className.length() - ".class.".length());
    }

    static class ClassPackage {
        /** 类对象 */
        final private Class classObject;
        /** 字节码 */
        final private byte[] classCode;

        ClassPackage(Class classObject, byte[] classCode) {
            this.classObject = classObject;
            this.classCode = classCode;
        }
    }

}
