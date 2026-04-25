package com.lovingai.ui;

import com.lovingai.LivingAI;
import com.lovingai.ai.LocalAiPrefs;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 选择本机推理基址与模型（OpenAI 兼容，如 LM Studio）：拉取模型列表、保存到 data/localai.properties。
 */
final class LocalModelDialog extends JDialog {

    private static final Pattern NAME_IN_MODELS =
            Pattern.compile("\\{\"name\"\\s*:\\s*\"([^\"]+)\"\\}");

    private final JTextField baseField = new JTextField(LocalAiPrefs.DEFAULT_BASE_URL, 32);
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JComboBox<String> visionModelCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");

    private LocalModelDialog(Frame owner) {
        super(owner, "本地大模型（LM Studio 等）", true);
        setLayout(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 6));
        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        row1.add(new JLabel("基址 (仅 http 本机)"), BorderLayout.WEST);
        baseField.setToolTipText("LM Studio Local Server 一般为 " + LocalAiPrefs.DEFAULT_BASE_URL + "（OpenAI 兼容）");
        row1.add(baseField, BorderLayout.CENTER);
        JPanel row2 = new JPanel(new BorderLayout(6, 0));
        row2.add(new JLabel("文本模型"), BorderLayout.WEST);
        modelCombo.setEditable(true);
        row2.add(modelCombo, BorderLayout.CENTER);
        JPanel row3 = new JPanel(new BorderLayout(6, 0));
        row3.add(new JLabel("视觉模型"), BorderLayout.WEST);
        visionModelCombo.setEditable(true);
        row3.add(visionModelCombo, BorderLayout.CENTER);
        form.add(row1);
        form.add(row2);
        form.add(row3);
        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton refresh = new JButton("刷新模型列表");
        refresh.addActionListener(e -> refreshModels());
        JButton save = new JButton("保存");
        save.addActionListener(e -> savePrefs());
        JButton test = new JButton("试调用");
        test.addActionListener(e -> testGenerate());
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        buttons.add(refresh);
        buttons.add(save);
        buttons.add(test);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
        add(status, BorderLayout.NORTH);

        getContentPane().setBackground(SciFiTheme.BG_PANEL);
        form.setBackground(SciFiTheme.BG_PANEL);
        buttons.setBackground(SciFiTheme.BG_DEEP);
        status.setForeground(SciFiTheme.TEXT_MUTED);

        setPreferredSize(new Dimension(520, 240));
        pack();
        setLocationRelativeTo(owner);
    }

    static void show(Frame owner) {
        LocalModelDialog d = new LocalModelDialog(owner);
        d.loadPrefsThenModels();
        d.setVisible(true);
    }

    private void loadPrefsThenModels() {
        status.setText("加载偏好…");
        SwingWorker<String, Void> w =
                new SwingWorker<>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        return httpGet(
                                "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/prefs");
                    }

                    @Override
                    protected void done() {
                        try {
                            String json = get();
                            String base = firstJsonString(json, "baseUrl");
                            String model = firstJsonString(json, "model");
                            String vision = firstJsonString(json, "visionModel");
                            if (base != null) baseField.setText(base);
                            if (model != null) modelCombo.setSelectedItem(model);
                            if (vision != null && !vision.isBlank()) {
                                visionModelCombo.setSelectedItem(vision);
                                if (visionModelCombo.getSelectedItem() == null) {
                                    visionModelCombo.getEditor().setItem(vision);
                                }
                            }
                            status.setText("已加载；可点「刷新模型列表」");
                            refreshModels();
                        } catch (Exception ex) {
                            status.setText("加载失败: " + ex.getMessage());
                        }
                    }
                };
        w.execute();
    }

    private void refreshModels() {
        status.setText("拉取模型列表…");
        String base = baseField.getText().trim();
        if (base.isEmpty()) base = LocalAiPrefs.DEFAULT_BASE_URL;
        String url =
                "http://127.0.0.1:"
                        + LivingAI.HTTP_PORT
                        + "/api/localai/models?baseUrl="
                        + URLEncoder.encode(base, StandardCharsets.UTF_8);
        String fBase = base;
        SwingWorker<String, Void> w =
                new SwingWorker<>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        return httpGet(url);
                    }

                    @Override
                    protected void done() {
                        try {
                            String json = get();
                            if (json != null && json.contains("\"ok\":false")) {
                                String err = firstJsonString(json, "error");
                                String kind = firstJsonString(json, "kind");
                                String hint = firstJsonString(json, "hintText");
                                String eb = firstJsonString(json, "effectiveBaseUrl");
                                if (eb != null && !eb.isBlank()) {
                                    baseField.setText(eb);
                                }
                                StringBuilder line = new StringBuilder();
                                line.append("未连接");
                                if (kind != null && !kind.isBlank()) {
                                    line.append(" [").append(kind).append("]");
                                }
                                if (err != null && !err.isBlank()) {
                                    line.append(": ").append(err);
                                }
                                status.setText(
                                        "<html><body style='width:460px'>"
                                                + escapeHtmlForSwing(line.toString())
                                                + (hint != null && !hint.isBlank()
                                                        ? "<br/><br/><span style='color:#8899aa'>"
                                                                + escapeHtmlForSwing(hint)
                                                                + "</span>"
                                                        : "")
                                                + "</body></html>");
                                return;
                            }
                            String eb = firstJsonString(json, "effectiveBaseUrl");
                            List<String> names = parseModelNames(json);
                            String keep = "";
                            if (modelCombo.getSelectedItem() != null) {
                                keep = modelCombo.getSelectedItem().toString().trim();
                            }
                            if (keep.isEmpty() && modelCombo.getEditor().getItem() != null) {
                                keep = modelCombo.getEditor().getItem().toString().trim();
                            }
                            modelCombo.removeAllItems();
                            for (String n : names) {
                                modelCombo.addItem(n);
                            }
                            if (!keep.isEmpty()) {
                                modelCombo.setSelectedItem(keep);
                                if (modelCombo.getSelectedItem() == null
                                        || !keep.equals(String.valueOf(modelCombo.getSelectedItem()))) {
                                    modelCombo.getEditor().setItem(keep);
                                }
                            } else if (!names.isEmpty()) {
                                modelCombo.setSelectedIndex(0);
                            }
                            String vKeep = "";
                            if (visionModelCombo.getSelectedItem() != null) {
                                vKeep = visionModelCombo.getSelectedItem().toString().trim();
                            }
                            if (vKeep.isEmpty() && visionModelCombo.getEditor().getItem() != null) {
                                vKeep = visionModelCombo.getEditor().getItem().toString().trim();
                            }
                            visionModelCombo.removeAllItems();
                            for (String n : names) {
                                visionModelCombo.addItem(n);
                            }
                            if (!vKeep.isEmpty()) {
                                visionModelCombo.setSelectedItem(vKeep);
                                if (visionModelCombo.getSelectedItem() == null
                                        || !vKeep.equals(String.valueOf(visionModelCombo.getSelectedItem()))) {
                                    visionModelCombo.getEditor().setItem(vKeep);
                                }
                            } else if (!names.isEmpty()) {
                                visionModelCombo.setSelectedIndex(0);
                            }
                            baseField.setText(
                                    eb != null && !eb.isBlank() ? eb : fBase);
                            status.setText("共 " + names.size() + " 个模型");
                        } catch (Exception ex) {
                            status.setText("刷新失败: " + ex.getMessage());
                        }
                    }
                };
        w.execute();
    }

    private static List<String> parseModelNames(String json) {
        List<String> out = new ArrayList<>();
        Matcher m = NAME_IN_MODELS.matcher(json);
        while (m.find()) {
            String n = m.group(1);
            if (!out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    private void savePrefs() {
        status.setText("保存中…");
        SwingWorker<String, Void> w =
                new SwingWorker<>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        String base = baseField.getText().trim();
                        String model = String.valueOf(modelCombo.getEditor().getItem()).trim();
                        if (model.isEmpty() && modelCombo.getSelectedItem() != null) {
                            model = String.valueOf(modelCombo.getSelectedItem());
                        }
                        String vision = String.valueOf(visionModelCombo.getEditor().getItem()).trim();
                        if (vision.isEmpty() && visionModelCombo.getSelectedItem() != null) {
                            vision = String.valueOf(visionModelCombo.getSelectedItem());
                        }
                        String form =
                                "baseUrl="
                                        + URLEncoder.encode(base, StandardCharsets.UTF_8)
                                        + "&model="
                                        + URLEncoder.encode(model, StandardCharsets.UTF_8)
                                        + "&visionModel="
                                        + URLEncoder.encode(vision, StandardCharsets.UTF_8)
                                        + "&inferenceBackend="
                                        + URLEncoder.encode("openai", StandardCharsets.UTF_8);
                        return httpPostForm(
                                "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/prefs", form);
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            status.setText("已保存");
                        } catch (Exception ex) {
                            status.setText("保存失败: " + ex.getMessage());
                            JOptionPane.showMessageDialog(LocalModelDialog.this, ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
        w.execute();
    }

    private void testGenerate() {
        status.setText("试调用中…");
        SwingWorker<String, Void> w =
                new SwingWorker<>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        String base = baseField.getText().trim();
                        if (base.isEmpty()) {
                            base = LocalAiPrefs.DEFAULT_BASE_URL;
                        }
                        String model = String.valueOf(modelCombo.getEditor().getItem()).trim();
                        if (model.isEmpty() && modelCombo.getSelectedItem() != null) {
                            model = String.valueOf(modelCombo.getSelectedItem());
                        }
                        String form =
                                "baseUrl="
                                        + URLEncoder.encode(base, StandardCharsets.UTF_8)
                                        + "&model="
                                        + URLEncoder.encode(model, StandardCharsets.UTF_8)
                                        + "&prompt="
                                        + URLEncoder.encode("用一句话说：你好。", StandardCharsets.UTF_8)
                                        + "&timeoutSec=240";
                        return httpPostForm(
                                "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/generate", form);
                    }

                    @Override
                    protected void done() {
                        try {
                            String json = get();
                            String text = firstJsonString(json, "text");
                            JOptionPane.showMessageDialog(
                                    LocalModelDialog.this,
                                    text != null ? text : json,
                                    "试调用结果",
                                    JOptionPane.INFORMATION_MESSAGE);
                            status.setText("试调用完成");
                        } catch (Exception ex) {
                            status.setText("试调用失败");
                            JOptionPane.showMessageDialog(LocalModelDialog.this, ex.getMessage(), "试调用失败", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
        w.execute();
    }

    private static String escapeHtmlForSwing(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
    }

    private static String firstJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    private static String unescapeJson(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case '\\' -> b.append('\\');
                    case '"' -> b.append('"');
                    default -> {
                        b.append('\\');
                        b.append(n);
                    }
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String httpGet(String url) throws Exception {
        URI uri = URI.create(url);
        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(120_000);
        c.setRequestMethod("GET");
        try {
            int code = c.getResponseCode();
            InputStream in =
                    (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null) {
                throw new IOException(
                        "HTTP " + code + (code == 500 ? "（可检查基址是否为 LM Studio 的 :1234，而非旧版 :11434）" : ""));
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String body = sb.toString().trim();
            if (code < 200 || code >= 300) {
                String err = firstJsonString(body, "error");
                throw new IOException(
                        (err != null ? err : body) + " (HTTP " + code + ")");
            }
            return body;
        } finally {
            c.disconnect();
        }
    }

    private static String httpPostForm(String url, String form) throws Exception {
        URI uri = URI.create(url);
        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(120_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        byte[] raw = form.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(raw.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(raw);
        }
        try {
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null) {
                in = (code >= 200 && code < 300) ? c.getErrorStream() : c.getInputStream();
            }
            if (in == null) {
                throw new IOException("HTTP " + code + "（无响应体）");
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String body = sb.toString().trim();
            if (code < 200 || code >= 300) {
                String err = firstJsonString(body, "error");
                throw new IOException(
                        (err != null ? err : body) + " (HTTP " + code + ")");
            }
            return body;
        } finally {
            c.disconnect();
        }
    }
}
