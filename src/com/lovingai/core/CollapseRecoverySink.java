package com.lovingai.core;

/** 系统彻底崩溃时由 {@link SystemCollapseGovernor} 回调，由 {@link com.lovingai.LivingAI} 实现断裂吸收与续行。 */
@FunctionalInterface
public interface CollapseRecoverySink {
    void onTotalCollapse(String collapseRecord);
}
