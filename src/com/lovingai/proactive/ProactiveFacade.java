package com.lovingai.proactive;

import com.lovingai.LivingAI;

/**
 * 主动节律入口门面：调度器与 UI 只依赖此包，便于与 {@link com.lovingai.LivingAI} 巨石解耦演进。
 */
public final class ProactiveFacade {

    private ProactiveFacade() {}

    /** 调度器调用的空闲主动心跳（原 {@link LivingAI#tickProactiveHeartbeat()}）。 */
    public static void runScheduledHeartbeat() {
        LivingAI.tickProactiveHeartbeat();
    }
}
