package com.lovingai.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 小型 UTF-8 落盘辅助：统一「目录创建 + 追加一行」形态，便于观测链与错误归类。
 */
public final class Utf8Files {

    private Utf8Files() {}

    /** 向文本文件追加一行（含换行符），不存在则创建。 */
    public static void appendLine(Path path, String line) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
