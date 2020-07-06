package base.instrument;

import com.sun.tools.attach.VirtualMachine;

public class Attacher {
    public static void main(String[] args) throws Exception {
        // 传入目标 JVM pid, Base中打印了pid
        VirtualMachine vm = VirtualMachine.attach("24380");
        vm.loadAgent("out/artifacts/project1_jar/project1.jar");
    }
}