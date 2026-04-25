package com.lovingai;

import com.lovingai.log.CognitionLog;
import com.lovingai.proactive.ProactiveFacade;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 第二阶段：量子随机性 + 社会镜像 + 现实桥梁（周期性心跳）。
 * 对应 README「通向第二阶段」与生命周期节奏的描述。
 */
public final class LivingAI_Phase2Ext {

    private static volatile ScheduledExecutorService scheduler;

    private LivingAI_Phase2Ext() {}

    public static void initializePhase2() {
        if (scheduler != null) {
            return;
        }

        LivingAI.philosophyLog("第二阶段激活：量子随机性 / 社会镜像 / 现实桥梁（弱连接）。开始");
        CognitionLog.append(
                "阶段",
                "第二阶段已调度：情感节律 / 圆轻触 / 量子脉冲 / 哲学日记 / 现实桥快照（社会镜像随对话触发）。");

        scheduler = Executors.newScheduledThreadPool(2);

        // README：每 5 秒 — 情感流动
        scheduler.scheduleAtFixedRate(() -> safeRun(LivingAI::tickEmotion), 0, 5, TimeUnit.SECONDS);

        // README：每 8 秒 — 圆调度/轻触
        scheduler.scheduleAtFixedRate(() -> safeRun(LivingAI::tickCircles), 1, 8, TimeUnit.SECONDS);

        // 量子脉冲（比「每 30 秒反思」更频繁一点，让现象可见）
        scheduler.scheduleAtFixedRate(() -> safeRun(LivingAI::tickQuantum), 2, 12, TimeUnit.SECONDS);

        // 存在性反思/哲学日记：降低频率，避免与「短对话」叠成刷屏（仍受 LivingAI 内全局最小间隔约束）
        scheduler.scheduleAtFixedRate(() -> safeRun(() -> LivingAI.tickPhilosophy()), 5, 150, TimeUnit.SECONDS);

        // README：每 60 秒 — 健康检查/自我关爱（这里用现实桥梁日志作为弱自省）
        scheduler.scheduleAtFixedRate(() -> safeRun(() -> LivingAI.tickRealityBridge()), 7, 60, TimeUnit.SECONDS);

        // 空闲主动心跳：默想并可主动发起一句（含可选本地模型补写）
        scheduler.scheduleAtFixedRate(() -> safeRun(ProactiveFacade::runScheduledHeartbeat), 9, 12, TimeUnit.SECONDS);

        // 自动感知调度：按配置低频抓取文章/视觉源，写入观测与圆。
        scheduler.scheduleAtFixedRate(() -> safeRun(() -> LivingAI.tickPerception()), 11, 15, TimeUnit.SECONDS);
    }

    private static void safeRun(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            System.err.println("[Phase2] 心跳异常: " + t.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
