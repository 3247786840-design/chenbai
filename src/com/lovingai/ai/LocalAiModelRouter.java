package com.lovingai.ai;

/**
 * 按「本轮需要什么」选择本机模型名；未单独配置时回退到 {@link LocalAiPrefs#getModel()}。
 *
 * <p>可在 {@code data/localai.properties} 中可选配置（空则自动回退）：
 *
 * <ul>
 *   <li>{@code auxModel} — 塑圆并联辅助链（首轮 / 进化 / 第三连环）
 *   <li>{@code compressModel} — 圆上下文浓缩、高优先回复后处理压缩
 *   <li>{@code fastModel} — 短输出：沙盒探针校验、主动追问自解等（未配 compress 时亦可作浓缩回退）
 *   <li>{@code distillModel} — 导入资料「文学蒸馏」
 * </ul>
 */
public final class LocalAiModelRouter {

    private LocalAiModelRouter() {}

    /** 与对话管线中各步对应的模型角色。 */
    public enum Role {
        /** 并联辅助主链 */
        AUX_MAIN,
        /** 圆上下文浓缩（进入主位前） */
        CONTEXT_COMPRESS,
        /** 高优先度长回复的本地再压缩 */
        OUTPUT_COMPRESS,
        /** 沙盒→现实探针可执行性校验（极短输出） */
        PROBE,
        /** 主动消息 / 自解等短轮次 */
        PROACTIVE,
        /** 导入长文本蒸馏 */
        DISTILL,
        /** 通用试跑（如 /api/localai/generate 未显式指定 model） */
        GENERIC
    }

    public static String model(Role role) {
        String main = LocalAiPrefs.getModel();
        switch (role) {
            case AUX_MAIN:
                return firstNonBlank(LocalAiPrefs.getAuxModel(), main);
            case CONTEXT_COMPRESS:
            case OUTPUT_COMPRESS:
                return firstNonBlank(
                        LocalAiPrefs.getCompressModel(),
                        firstNonBlank(LocalAiPrefs.getFastModel(), main));
            case PROBE:
            case PROACTIVE:
                return firstNonBlank(LocalAiPrefs.getFastModel(), main);
            case DISTILL:
                return firstNonBlank(LocalAiPrefs.getDistillModel(), main);
            case GENERIC:
            default:
                return main;
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback;
    }
}
