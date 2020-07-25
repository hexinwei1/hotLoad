package reload.run;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class Attach {
    public static void main(String[] args) throws Exception {
        // 遍历所有虚拟机描述VirtualMachineDescriptor，筛选出main方法类名为Test的JVM
        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            if (!vmd.displayName().endsWith("RunTest")) {
                continue;
            }

            // 根据进程id获取虚拟机VirtualMachine
            VirtualMachine vm = VirtualMachine.attach(vmd.id());

            /**
             * 将reload.jar attach 到JVM中
             * D:/reload.jar - 热加载源码 InstrumentationTest agentmain()
             * D:/newCode.jar - 需要加载的新的字节码
             */
            vm.loadAgent("jar/reload.jar", "jar/newCode.jar");
        }
    }
}
