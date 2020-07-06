package base.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

public class JavassistTest {
    public static <ClassPoll> void main(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get("javaAgent.Test");
        CtMethod m = cc.getDeclaredMethod("print");
        m.insertBefore("{ System.out.println(\"start\"); }");
        m.insertAfter("{ System.out.println(\"end\"); }");
        Class c = cc.toClass();
    }
}
