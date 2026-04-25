package com.lovingai.core.columnkey;

/**
 * 圆-柱门控可调参数快照（D4/D6）；应可写入观测或账本以便回放。
 *
 * @param weightJitterScale 柱维权重整体缩放（1.0 为中性）
 * @param maxParallelCircles 单次并行比对圆数量上限
 * @param minSimilarityFloor 低耦合兜底阈值（加权相似度）
 * @param historyBlendRatio 历史先验混合比例 λ，建议 0.10～0.20
 * 见 ADR 0001。
 */
public record ColumnGateParams(
        double weightJitterScale,
        int maxParallelCircles,
        double minSimilarityFloor,
        double historyBlendRatio) {

    public ColumnGateParams {
        weightJitterScale = Math.max(0.5, Math.min(1.2, weightJitterScale));
        maxParallelCircles = Math.max(1, Math.min(16, maxParallelCircles));
        minSimilarityFloor = Math.max(0.0, Math.min(0.5, minSimilarityFloor));
        historyBlendRatio = Math.max(0.0, Math.min(0.35, historyBlendRatio));
    }
}
