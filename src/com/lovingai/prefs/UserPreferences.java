package com.lovingai.prefs;

/** 持久化用户偏好（节律、哲学调度、声部合并、失败降级）。 */
public record UserPreferences(
        boolean proactiveEnabled,
        boolean philosophySchedulerEnabled,
        String voiceMergeMode,
        boolean failureAutoDemote) {

    public static UserPreferences defaults() {
        return new UserPreferences(true, true, "structure_first", true);
    }
}
