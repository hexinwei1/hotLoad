package base;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ParseJarFile {


    public static void main(String[] args) throws Exception {
        JarFile jarFile = new JarFile("D:\\work\\project\\project1\\out\\artifacts\\base\\base.jar");
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                Class classObject = getClassObject(jarEntry);
                byte[] classCode = getClassByte(jarFile, jarEntry);
            }
        }
    }

    private static byte[] getClassByte(JarFile jarFile, JarEntry jarEntry) {
        try {
            InputStream in = jarFile.getInputStream(jarEntry);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int size = 0;
            while((size = in.read()) != -1){
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
        return className.substring(0, className.length() - 7);
    }

}
