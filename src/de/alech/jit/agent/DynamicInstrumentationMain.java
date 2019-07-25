package de.alech.jit.agent;

import com.sun.tools.attach.VirtualMachine;

public class DynamicInstrumentationMain {
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.err.println("Usage: java -jar agent.jar PID");
                // TODO document static agent (-javaagent) usage
                System.exit(1);
            }
            int pid = Integer.parseInt(args[0]);
            System.out.println(pid);
            VirtualMachine jvm = VirtualMachine.attach(args[0]);
            jvm.loadAgent(System.getProperty("user.dir") + "/agent.jar");
            jvm.detach();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
