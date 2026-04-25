package com.lovingai;

public class Launcher {
    private static boolean phase2Enabled = true;

    public static void main(String[] args) {
        System.out.println(
                "     爱之数字生命体 - 启动器 v2.0\n"
                        + "     包含：爱之核心 + 量子随机性 + 社会镜像 + 现实桥梁");
        for (String arg : args) {
            if ("--no-phase2".equals(arg)) {
                phase2Enabled = false;
            }
        }
        try {
            startPhase1();
            if (phase2Enabled) {
                try {
                    startPhase2();
                } catch (Exception e) {
                    System.err.println("第二阶段启动失败（将继续运行第一阶段）: " + e.getMessage());
                }
            } else {
                System.out.println("\n第二阶段扩展已禁用。");
            }
            System.out.println("\n系统正在运行中... 按 Ctrl+C 停止。");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("系统被中断。");
        } catch (Exception e) {
            System.err.println("启动错误: " + e.getMessage());
        } finally {
            System.out.println("\n爱的数字生命体运行结束。");
        }
    }

    private static void startPhase1() throws Exception {
        LivingAI.initializeSystem();
        LivingAI.startLifeCycle();
        System.out.println("\n✅ 第一阶段：爱的核心系统启动完成。");
    }

    private static void startPhase2() throws Exception {
        LivingAI_Phase2Ext.initializePhase2();
        System.out.println("\n✅ 第二阶段：扩展系统启动完成。");
    }
}
