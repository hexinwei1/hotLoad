[TOC]
* [hotLoad]()
* [1. 演示 hotLoad 说明]()
* [2. 目录说明]()
* [3. 热加载原理]()
 * [3.1 字节码增强]()
    * [3.1.1 ASM]()
       * [1. API]()
       * [2. 使用：ASM实现AOP]()
    * [3.1.2 Javassist]()
       * [API]()
    * [3.1.3 重新编译类 热加载用]()
 * [3.2 修改的字节码动态载入JVM]()
    * [3.2.1 Instrumentation]()
    * [1. ClassFileTransformer 类文件转换器]()
    * [2. Instrumentation API]()
    * [3.2.2 Instrumentation   Java Agent  只能在启动时操作]()
    * [3.2.3 Instrumentation   Attach  运行时操作，热加载用]()
 * [3.3 参考]()


# hotLoad

reload hotload 热加载/热部署



## 1. 演示 hotLoad 说明

1. clone 项目，IDE打开

2. 运行 `reload.run.RunTest` ，控制台输出 

   ```
   11111111111111111111
   22222222222222222222
   11111111111111111111
   22222222222222222222
   ```

3. 运行 `reload.run.Attach`，控制台输出

   ```
   AAAAAAAAAAAAAAAAAAAA
   BBBBBBBBBBBBBBBBBBBB
   AAAAAAAAAAAAAAAAAAAA
   BBBBBBBBBBBBBBBBBBBB
   ```

   

## 2. 目录说明

- `newCode.jar`：需要加载到JVM中的新的字节码

- `reload.jar`：热加载用到的工具`Instrumentation`。

- `src`: 代码
  - `base` 一些热加载基础知识学习Demo
  - `reload`：热加载源码



## 3. 热加载原理

### 3.1 字节码增强

字节码增强技术就是一类对现有字节码进行修改或者动态生成全新字节码文件的技术。

#### 3.1.1 ASM
ASM在指令层次上手动操纵字节码，可以直接生产 .class 字节码文件，也可以在类被加载入JVM之前动态修改类字节码。

##### 1. API

- ClassReader：用于读取已经编译好的.class文件。
- ClassWriter：用于重新构建编译后的类，如修改类名、属性以及方法，也可以生成新的类的字节码文件。
- 各种Visitor类：如上所述，CoreAPI根据字节码从上到下依次处理，对于字节码文件中不同的区域有不同的Visitor，比如用于访问方法的MethodVisitor、用于访问类变量的FieldVisitor、用于访问注解的AnnotationVisitor等。为了实现AOP，重点要使用的是MethodVisitor。

##### 2. 使用：ASM实现AOP

如下代码，Base类的process()方法，输出一行“process”。
使用ASM增强后，期望：process()方法执行前输出“start”，之后输出”end”。

```java
public class Base {
    public void process(){
        System.out.println("process");
    }
}
```


```java
/**
 * 生成字节码
 * ClassReader读取字节码
 * MyClassVisitor访问并修改字节码
 * ClassWriter写字节码，将旧的字节码替换掉
 */
public class Generator {
    public static void main(String[] args) throws Exception {
        //读取
        ClassReader classReader = new ClassReader("asm.Base");
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        //处理
        ClassVisitor classVisitor = new MyClassVisitor(classWriter);
        classReader.accept(classVisitor, ClassReader.SKIP_DEBUG);
        byte[] data = classWriter.toByteArray();
        //输出
        File f = new File("out/production/project1/asm/Base.class");
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(data);
        fout.close();
        System.out.println("now generator cc success!!!!!");

        new Base().process();
    }
}
```

```java
/**
 * 对字节码的visit以及修改
 *
 * 1. visitMethod方法，获取到process()方法
 * 2. MyMethodVisitor中的visitCode方法，它会在ASM开始访问某一个方法的Code区时被调用，将AOP中的前置逻辑(print start)就放在这里。
 * 3. MyMethodVisitor中的visitInsn方法，每当ASM访问到无参数指令时被调用。我们判断了当前指令是否为无参数的“return”指令，如果是就在它的前面添加一些指令，也就是将AOP的后置逻辑放在该方法中。
 */
public class MyClassVisitor extends ClassVisitor implements Opcodes {

    public MyClassVisitor(ClassVisitor cv) {
        super(ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        cv.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * 获取到process()方法
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        // Base类中有两个方法：无参构造以及process方法，这里不增强构造方法
        if (!name.equals("<init>") && mv != null) {
            mv = new MyMethodVisitor(mv);
        }
        return mv;
    }

    class MyMethodVisitor extends MethodVisitor implements Opcodes {

        public MyMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        /**
         * ASM开始访问某一个方法的Code区时被调用
         * print start 在这里处理
         */
        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("start");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }

        /**
         * 每当ASM访问到无参数指令时被调用
         * print end 在这里处理
         */
        @Override
        public void visitInsn(int opcode) {
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)
                    || opcode == Opcodes.ATHROW) {
                //方法在返回之前，打印"end"
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("end");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            }
            mv.visitInsn(opcode);
        }
    }
}
```

- 首先通过MyClassVisitor类中的visitMethod方法，判断当前字节码读到哪一个方法了。跳过构造方法 <init> 后，将需要被增强的方法交给内部类MyMethodVisitor来进行处理。
- 接下来，进入内部类MyMethodVisitor中的visitCode方法，它会在ASM开始访问某一个方法的Code区时被调用，重写visitCode方法，将AOP中的前置逻辑就放在这里。
- MyMethodVisitor继续读取字节码指令，每当ASM访问到无参数指令时，都会调用MyMethodVisitor中的visitInsn方法。我们判断了当前指令是否为无参数的“return”指令，如果是就在它的前面添加一些指令，也就是将AOP的后置逻辑放在该方法中。
- 综上，重写MyMethodVisitor中的两个方法，就可以实现AOP了，而重写方法时就需要用ASM的写法，手动写入或者修改字节码。通过调用methodVisitor的visitXXXXInsn()方法就可以实现字节码的插入，XXXX对应相应的操作码助记符类型，比如mv.visitLdcInsn(“end”)对应的操作码就是ldc “end”，即将字符串“end”压入栈。


查看Base.class，如下：
```java
public class Base {
    public Base() {
    }

    public void process() {
        System.out.println("start");
        System.out.println("process");
        System.out.println("end");
    }
}
```

执行结果如下：
```java
start
process
end
```


#### 3.1.2 Javassist

ASM是在指令层次上操作字节码的，比较晦涩难懂。 而Javassist是在Java源代码层次操作字节码的，简单易用。

利用Javassist实现字节码增强时，可以无须关注字节码刻板的结构，其优点就在于编程简单。直接使用java编码的形式，而不需要了解虚拟机指令，就能动态改变类的结构或者动态生成类。

##### API
- CtClass（compile-time class）：编译时类信息，它是一个class文件在代码中的抽象表现形式，可以通过一个类的全限定名来获取一个CtClass对象，用来表示这个类文件。
- ClassPool：从开发视角来看，ClassPool是一张保存CtClass信息的HashTable，key为类名，value为类名对应的CtClass对象。当我们需要对某个类进行修改时，就是通过pool.getCtClass(“className”)方法从pool中获取到相应的CtClass。
- CtMethod、CtField：这两个比较好理解，对应的是类中的方法和属性。

我们依然是对Base中的process()方法做增强，在方法调用前后分别输出”start”和”end。需要做的就是从pool中获取到相应的CtClass对象和其中的方法，然后执行method.insertBefore和insertAfter方法分别插入的Java代码即可。实现代码如下：

```java
public class JavassistTest {
    public static <ClassPoll> void main(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get("javassist.Base");
        CtMethod m = cc.getDeclaredMethod("process");
        m.insertBefore("{ System.out.println(\"start\"); }");
        m.insertAfter("{ System.out.println(\"end\"); }");
        Class c = cc.toClass();
        Base h = (Base)c.newInstance();
        h.process();
    }
}
```

#### 3.1.3 重新编译类 热加载用
这个不用说了，改完Java代码，重新编译下字节码就变了，通常是用IDE导出成jar包。

### 3.2 修改的字节码动态载入JVM
以上只是修改字节码，只能在类加载前进行强化，并不涉及到JVM运行时对类的重新加载。

而我们期望的效果是：在一个持续运行并已经加载了所有类的JVM中，还能利用字节码增强技术对其中的类行为做替换并重新加载。

#### 3.2.1 Instrumentation 

instrument是JVM提供的一个可以修改已加载类的类库，支持在运行时对类定义的修改。

#### 1. ClassFileTransformer 类文件转换器
ClassFileTransformer 类文件转换器。ClassFileTransforme接口中的transform()方法，返回新类的字节码。
```java
	/**
	 * 类转换器的转化方法
	 * @param loader 原类加载器
	 * @param className 原类全类名
	 * @param classBeingRedefined 原类对象
	 * @param protectionDomain 原类保护域
	 * @param classfileBuffer 原类二进制数据，只读
	 * @return 新的类的二进制数据
	 */
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {}
```

#### 2. Instrumentation API 

```java
   /**
     * Instrumentation 添加一个类转化器
	 * @Param transformer 类转化器
	 * @Param canRetransform 是否允许转换，一般设置为true
     */
	void addTransformer(ClassFileTransformer transformer, boolean canRetransform);

   /**
     * Instrumentation 移除一个类转化器
	 * @Param transformer 类转化器
     */
	boolean removeTransformer(ClassFileTransformer transformer);

   /**
     * 转换类
	 * @Param classes 需要转换的类
     */
    void retransformClasses(Class<?>... classes) throws UnmodifiableClassException;
```

#### 3.2.2 Instrumentation + Java Agent  只能在启动时操作
1. 测试类
```java
public class Test {

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            print();
            Thread.sleep(5000);
        }
    }

    public static void print() {
        System.out.println("1111111111111111111");
    }
}
```

2. 类转换器，print()前后分别输出 start end
```java
/**
 * 类转换器
 */
public class MyClassTansformer implements ClassFileTransformer {

    private String args;

    public MyClassTansformer(String args) {
        super();
        this.args = args;
    }

    /**
     * 类转换器的转化方法
     * @param loader 原类加载器
     * @param className 原类全类名
     * @param classBeingRedefined 原类对象
     * @param protectionDomain 原类保护域
     * @param classfileBuffer 原类二进制数据，只读
     * @return 新的类的二进制数据
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        System.out.println("load class=" + className + ", args=" + args);

        // 修改并重新加载Test.class字节码，在print方法前打印start,后打印end
        try {
            if (className.equals("javaAgent/Test")) {
                ClassPool cp = ClassPool.getDefault();
                CtClass cc = cp.get("javaAgent.Test");
                CtMethod m = cc.getDeclaredMethod("print");
                m.insertBefore("{ System.out.println(\"start\"); }");
                m.insertAfter("{ System.out.println(\"end\"); }");
                return cc.toBytecode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }
}
```

3. 实现premain()方法，premain中新增类转换器，所有加载的类都会被转换。
    ```java
    public class InstrumentationTest {
        /**
         * main方法之前调用
         * Premain-class:javaAgent.InstrumentationTest
         * @param agentArgs 传来的参数
         * @param instrumentation
         */
        public static void premain(String agentArgs, Instrumentation instrumentation) {
            System.out.println("premain:agentArgs=" + agentArgs);
            // 新增一个类转换器，所有加载的类都会被转换
            instrumentation.addTransformer(new MyClassTansformer(agentArgs), true);
        }

    }
    ```


- premain()在main方法之前运行
- premain()所在jar包，MANIFEST.MF需要如下设置：
```
Premain-Class: javaAgent.InstrumentationTest
Can-Redefine-Classes: true
Can-Retransform-Classes: true
```
- JVM启动时需要设置参数：`-javaagent:D:\premain.jar`

4. `InstrumentationTest`  `MyClassTansformer`  打成jar包，设置MANIFEST.MF：
```
Premain-Class: javaAgent.InstrumentationTest
Can-Redefine-Classes: true
Can-Retransform-Classes: true
```

5. 设置JVM参数 `-javaagent:D:\premain.jar`，执行Test.main()

输出结果如下：

1. 加载的所有类都被 `MyClassTansformer` 转换了，因为都输出了`load class=...`
2. premain()在main方法之前运行
3. Test.print()方法字节码被改变了，加了start end

```java
premain:agentArgs=null
load class=sun/nio/cs/ThreadLocalCoders, args=null
load class=sun/nio/cs/ThreadLocalCoders$1, args=null
...省略很多行...
load class=javaAgent/Test, args=null
load class=sun/launcher/LauncherHelper$FXHelper, args=null
load class=java/lang/Class$MethodArray, args=null
load class=java/lang/Void, args=null
start
1111111111111111111
end
start
1111111111111111111
end
...
```

#### 3.2.3 Instrumentation + Attach  运行时操作，热加载用

Java Agent 只能在Java进程启动是改变字节码，Attach可以在JVM运行时动态的将字节码修改。

Attach API 的作用是提供JVM进程间通信的能力，比如说我们为了让另外一个JVM进程把线上服务的线程Dump出来，会运行jstack或jmap的进程，并传递pid的参数，告诉它要对哪个进程进行线程Dump，这就是Attach API做的事情。


期望：Test程序输出`1111111111111`，热加载之后Test程序输出`22222222222222`
```java
public class Test {

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            print();
            Thread.sleep(5000);
        }
    }

    public static void print() {
        System.out.println("1111111111111111");
    }
}
```

1. 创建`222222222222` 字节码文件，编译`Test2.java` -->`Test2.class`，保存
```java
public class Test2 {

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            print();
            Thread.sleep(5000);
        }
    }

    public static void print() {
        System.out.println("222222222222222222");
    }
}
```

2. 类转换器
```java
/**
 * 类转换器
 */
public class MyClassTansformer implements ClassFileTransformer {

    /** 新class文件路径名 */
    private String args;

    public MyClassTansformer(String args) {
        super();
        this.args = args;
    }

    /**
     * 类转换器的转化方法
     * @param loader 原类加载器
     * @param className 原类全类名
     * @param classBeingRedefined 原类对象
     * @param protectionDomain 原类保护域
     * @param classfileBuffer 原类二进制数据，只读
     * @return 新的类的二进制数据
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            return getNewClassByte();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }

    /**
     * 获取新类的二进制字节码
     */
    private byte[] getNewClassByte() throws Exception {
        File filename = new File(args);
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] temp = new byte[1024];
        int size = 0;
        while((size = in.read(temp)) != -1){
            out.write(temp, 0, size);
        }
        in.close();
        return out.toByteArray();
    }
}
```

3. 实现agentmain()方法
    1. agentmain.jar被attach到一个JVM上时，执行agentmain()方法
    2. agentmain()中添加类转换器，并指定要转换的类
    3. 不同于premain方法，premain会转换所有加载的类，agentmain只转换指定的类
```java
public class InstrumentationTest {
    /**
     * 当当前代码的jar 被attach到一个JVM上时，执行此方法
     * Agentmain-class:javaAgent.InstrumentationTest
     * @param agentArgs 传来的参数
     * @param instrumentation
     */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
        System.out.println("agentmain:agentArgs=" + agentArgs);
        // add类转换器
        instrumentation.addTransformer(new MyClassTansformer(agentArgs), true);
        // 使用类转换器转换Test.class
        instrumentation.retransformClasses(Test.class);
    }
}
```

4. Attach 代码
```java
public class Attach {
    public static void main(String[] args) throws Exception {
        // 遍历所有虚拟机描述VirtualMachineDescriptor，筛选出main方法类名为Test的JVM
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            if (!vmd.displayName().endsWith("Test")) {
                continue;
            }

            // 根据进程id获取虚拟机VirtualMachine
            VirtualMachine vm = VirtualMachine.attach(vmd.id());
            // 将agentmain.jar attach 到JVM中
            vm.loadAgent("D:/agentmain.jar", "D:/Test2.class");
        }
    }
}
```

5.  `InstrumentationTest`  `MyClassTansformer`  打成jar包，设置MANIFEST.MF：
 ```
Agent-Class: reload.InstrumentationTest
Can-Redefine-Classes: true
Can-Retransform-Classes: true
 ```

6. 启动Test，启动Attach，成功！！
执行结果：
```java
111111111111111111111
111111111111111111111
agentmain:agentArgs=D:/Test2.class
222222222222222222
222222222222222222
...
```




### 3.3 参考
1. [字节码增强技术探索](https://tech.meituan.com/2019/09/05/java-bytecode-enhancement.html)
2. 《实战Java虚拟机》11.3(P322)  11.4(399)
3. [Java黑科技之源：JVMTI完全解读](https://blog.csdn.net/duqi_2009/article/details/94518203)
4. [深入浅出Java](https://www.jianshu.com/nb/8550292)
