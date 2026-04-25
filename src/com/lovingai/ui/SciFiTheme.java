package com.lovingai.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Point2D;
import java.util.Enumeration;

/**
 * 赛博朋克工业 HUD：深空海军蓝底、电光青蓝、悬浮全息波形与 KPI 环；在 {@link LifeformApp#main} 最早调用 {@link #install()}。
 */
public final class SciFiTheme {

    public static final boolean REDUCE_MOTION =
            Boolean.parseBoolean(System.getProperty("lovingai.ui.reduceMotion", "true"));

    /** 最深背景（void） */
    public static final Color BG_VOID = new Color(0x010408);
    public static final Color BG_DEEP = new Color(0x030a12);
    public static final Color BG_PANEL = new Color(0x071420);
    /** 终端 / 文本框底色 */
    public static final Color CONSOLE_BG = new Color(0x050c14);
    /** 核心青高光 */
    public static final Color ACCENT = new Color(0x3ee8ff);
    /** 电光蓝（数据高亮） */
    public static final Color ELECTRIC_BLUE = new Color(0x00b4ff);
    public static final Color ACCENT_DIM = new Color(0x1a4a5c);
    public static final Color TEXT = new Color(0xe8f4ff);
    public static final Color TEXT_MUTED = new Color(0x7a9ab8);
    /** HUD 外框、扫描线 */
    public static final Color HUD_EDGE = new Color(0x4af0ff);
    public static final Color GRID_MAJOR = new Color(0x1e4a62);
    public static final Color GRID_MINOR = new Color(0x143040);
    public static final Color STATUS_OK = new Color(0x3dff9a);
    public static final Color HUD_WARN = new Color(0xffaa44);

    private static volatile FontUIResource UI_FONT_RESOURCE;
    private static volatile Font UI_FONT = new Font("Dialog", Font.PLAIN, 13);
    private static volatile Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private SciFiTheme() {}

    /**
     * 深空渐变 + 工业网格 + 体积光柱感 + 轻扫描线（无外部贴图）。
     */
    public static final class HudBackdropPanel extends JPanel {
        private float phase = 0f;

        public HudBackdropPanel() {
            super(new BorderLayout());
            setOpaque(true);
            if (!REDUCE_MOTION) {
                Timer t =
                        new Timer(
                                48,
                                e -> {
                                    phase += 0.022f;
                                    if (phase > 1000f) {
                                        phase = 0f;
                                    }
                                    repaint();
                                });
                t.setRepeats(true);
                t.start();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Point2D c = new Point2D.Float(w * 0.42f, h * 0.08f);
            float r = Math.max(w, h) * 1.05f;
            RadialGradientPaint rgp =
                    new RadialGradientPaint(
                            c,
                            r,
                            new float[] {0f, 0.35f, 0.72f, 1f},
                            new Color[] {
                                new Color(0x0a1a32),
                                new Color(0x051018),
                                new Color(0x020810),
                                BG_VOID
                            },
                            MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g2.setPaint(rgp);
            g2.fillRect(0, 0, w, h);

            g2.setComposite(AlphaComposite.SrcOver.derive(0.34f));
            g2.setPaint(
                    new GradientPaint(
                            0, 0, new Color(0, 80, 140, 40), w * 0.7f, h, new Color(0, 20, 40, 0)));
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver);

            g2.setColor(new Color(0, 180, 255, 12));
            for (int y = 0; y < h; y += 20) {
                g2.drawLine(0, y, w, y);
            }
            g2.setColor(new Color(40, 120, 180, 9));
            for (int x = 0; x < w; x += 28) {
                g2.drawLine(x, 0, x, h);
            }
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 212, 255, 14));
            for (int x = 0; x < w; x += 140) {
                g2.drawLine(x, 0, x + h / 3, h);
            }

            float vx = (float) (Math.sin(phase * 0.7) * 0.5 + 0.5);
            int glowX = (int) (w * (0.2 + 0.55 * vx));
            RadialGradientPaint vol =
                    new RadialGradientPaint(
                            new Point2D.Float(glowX, 0),
                            h * 0.55f,
                            new float[] {0f, 1f},
                            new Color[] {new Color(0, 140, 220, 20), new Color(0, 0, 0, 0)},
                            MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g2.setPaint(vol);
            g2.fillRect(0, 0, w, (int) (h * 0.42));

            if (!REDUCE_MOTION) {
                g2.setComposite(AlphaComposite.SrcOver.derive(0.04f));
                for (int y = 0; y < h; y += 5) {
                    int o = (int) (Math.sin(phase * 1.3 + y * 0.02) * 6);
                    g2.setColor(new Color(200, 240, 255, 36));
                    g2.drawLine(0, y + o, w, y + o);
                }
            }
            g2.setComposite(AlphaComposite.SrcOver);

            drawHudBracket(g2, 12, 12, 44, 44, true);
            drawHudBracket(g2, w - 56, 12, 44, 44, false);

            float cx = w * 0.52f;
            float cy = h * 0.48f;
            float baseR = Math.min(w, h) * 0.2f;
            g2.setStroke(new BasicStroke(1.1f));
            for (int i = 0; i < 12; i++) {
                float p = phase + i * 0.51f;
                double a = Math.sin(p) * 0.5 + 0.5;
                int alpha = Math.max(8, Math.min(48, (int) (a * 52)));
                g2.setColor(new Color(100, 220, 255, alpha));
                int rx = (int) (baseR * (0.5 + 0.75 * Math.abs(Math.sin(p * 0.8))));
                int ry = (int) (baseR * (0.42 + 0.8 * Math.abs(Math.cos(p * 0.65))));
                int ox = (int) (Math.cos(p * 0.9) * baseR * 0.28);
                int oy = (int) (Math.sin(p * 0.85) * baseR * 0.16);
                g2.drawOval((int) cx - rx + ox, (int) cy - ry + oy, rx * 2, ry * 2);
            }
            g2.dispose();
        }

        private static void drawHudBracket(Graphics2D g2, int x, int y, int w, int h, boolean left) {
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(ELECTRIC_BLUE.getRed(), ELECTRIC_BLUE.getGreen(), ELECTRIC_BLUE.getBlue(), 160));
            if (left) {
                g2.drawLine(x, y, x + w, y);
                g2.drawLine(x, y, x, y + h);
            } else {
                g2.drawLine(x, y, x + w, y);
                g2.drawLine(x + w, y, x + w, y + h);
            }
        }
    }

    /**
     * 对话区底板：巨型全息能量主波、侧向柱状/折线趋势、角部 KPI 环（不替代功能控件，仅视觉层）。
     */
    public static final class DialogueStagePanel extends JPanel {
        private float phase = 0f;

        public DialogueStagePanel() {
            super(new BorderLayout());
            setOpaque(true);
            setBackground(new Color(0x020810));
            if (!REDUCE_MOTION) {
                Timer t =
                        new Timer(
                                56,
                                e -> {
                                    phase += 0.022f;
                                    if (phase > 10_000f) {
                                        phase = 0f;
                                    }
                                    repaint();
                                });
                t.setRepeats(true);
                t.start();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g2.setPaint(
                    new GradientPaint(
                            0,
                            0,
                            new Color(4, 14, 26, 235),
                            w,
                            h,
                            new Color(2, 6, 12, 248)));
            g2.fillRect(0, 0, w, h);

            g2.setComposite(AlphaComposite.SrcOver.derive(0.26f));
            g2.setColor(GRID_MINOR);
            for (int gx = 0; gx < w; gx += 32) {
                g2.drawLine(gx, 0, gx, h);
            }
            for (int gy = 0; gy < h; gy += 32) {
                g2.drawLine(0, gy, w, gy);
            }
            g2.setComposite(AlphaComposite.SrcOver);

            int mid = (int) (h * 0.62);
            g2.setStroke(new BasicStroke(2.2f));
            for (int k = 0; k < 3; k++) {
                Color c =
                        k == 0
                                ? new Color(60, 240, 255, 140)
                                : k == 1
                                        ? new Color(0, 180, 255, 110)
                                        : k == 2
                                                ? new Color(255, 140, 80, 90)
                                                : new Color(120, 200, 255, 70);
                g2.setColor(c);
                int prevX = 0;
                int prevY = mid;
                for (int x = 1; x < w; x += 5) {
                    float p = x / (float) w;
                    double yv =
                            Math.sin((p * 11.0) + phase * 1.2 + k * 0.6) * (h * (0.1 + 0.015 * k))
                                    + Math.sin((p * 23.0) - phase * 0.9 + k) * (h * 0.035)
                                    + Math.sin((p * 5.5) + phase * 0.4) * (h * 0.02);
                    int y = (int) (mid + yv);
                    g2.drawLine(prevX, prevY, x, y);
                    prevX = x;
                    prevY = y;
                }
            }

            int barW = Math.min(160, w / 8);
            int bx0 = 10;
            int bBase = h - 28;
            int bHmax = (int) (h * 0.22);
            g2.setStroke(new BasicStroke(1.2f));
            for (int b = 0; b < 9; b++) {
                double amp = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(phase * 1.1 + b * 0.7));
                int bh = (int) (bHmax * amp);
                int x = bx0 + b * (barW / 9 + 2);
                g2.setColor(new Color(0, 200, 255, 110));
                g2.fillRect(x, bBase - bh, Math.max(3, barW / 10), bh);
                g2.setColor(new Color(100, 240, 255, 180));
                g2.drawRect(x, bBase - bh, Math.max(3, barW / 10), bh);
            }

            int lx0 = w - Math.min(200, w / 5);
            int lm = (int) (h * 0.38);
            g2.setStroke(new BasicStroke(1.4f));
            for (int series = 0; series < 3; series++) {
                g2.setColor(
                        new Color(
                                series == 0 ? 80 : 0,
                                series == 1 ? 220 : 180,
                                255,
                                140 - series * 25));
                int px = lx0;
                int py = lm;
                for (int x = lx0 + 2; x < w - 8; x += 6) {
                    float p = (x - lx0) / (float) (w - lx0);
                    int y =
                            lm
                                    + (int)
                                            (Math.sin(p * 8 + phase + series * 1.2)
                                                            * (h * 0.06)
                                                    + Math.sin(p * 14 - phase * 0.8) * (h * 0.025));
                    g2.drawLine(px, py, x, y);
                    px = x;
                    py = y;
                }
            }

            drawKpiArc(g2, 18, 14, 46, 0.78 + 0.04 * Math.sin(phase), ACCENT, "能耗");
            drawKpiArc(g2, w - 120, 14, 46, 0.55 + 0.08 * Math.sin(phase * 0.9), ELECTRIC_BLUE, "产能");
            drawKpiArc(g2, w / 2 - 23, 10, 46, 0.995, STATUS_OK, "在线");

            g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
            g2.setPaint(
                    new GradientPaint(0, h / 2, new Color(0, 0, 0, 0), 0, h, new Color(0, 4, 10, 220)));
            g2.fillRect(0, h / 2, w, h / 2);
            g2.setComposite(AlphaComposite.SrcOver);

            g2.dispose();
        }

        private static void drawKpiArc(
                Graphics2D g2, int x, int y, int d, double pct, Color c, String label) {
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(40, 70, 90, 180));
            g2.drawOval(x, y, d, d);
            int sweep = (int) (360 * Math.max(0, Math.min(1, pct)));
            g2.setColor(c);
            g2.drawArc(x, y, d, d, 90, -sweep);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
            g2.setColor(TEXT_MUTED);
            g2.drawString(label, x, y + d + 12);
            g2.setColor(ACCENT);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            g2.drawString(String.format("%.0f%%", pct * 100), x + 8, y + d / 2 + 4);
        }
    }

    /**
     * 总览顶栏：工业 KPI 带 — 能耗/产能/在线环 + 柱状吞吐 + 微型折线（装饰性实时趋势，与底层 HTTP 刷新独立）。
     */
    public static final class HudKpiRibbon extends JPanel {
        private float phase = 0f;

        public HudKpiRibbon() {
            setOpaque(true);
            setPreferredSize(new java.awt.Dimension(10, 92));
            if (!REDUCE_MOTION) {
                Timer t =
                        new Timer(
                                64,
                                e -> {
                                    phase += 0.02f;
                                    if (phase > 10_000f) {
                                        phase = 0f;
                                    }
                                    repaint();
                                });
                t.setRepeats(true);
                t.start();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setPaint(
                    new GradientPaint(0, 0, new Color(6, 18, 32, 250), w, h, new Color(2, 8, 14, 250)));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(ELECTRIC_BLUE.getRed(), ELECTRIC_BLUE.getGreen(), ELECTRIC_BLUE.getBlue(), 90));
            g2.drawLine(0, h - 1, w, h - 1);

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            g2.setColor(ELECTRIC_BLUE);
            g2.drawString("◆ SYS.OBSERVATION // LIVE METRICS", 12, 16);
            g2.setColor(TEXT_MUTED);
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
            g2.drawString("NODE · LOCAL · 127.0.0.1", 12, 28);

            int cy = 22;
            int d = 52;
            drawRibbonGauge(g2, w - 280, cy, d, 0.72 + 0.05 * Math.sin(phase), "能耗", HUD_WARN);
            drawRibbonGauge(g2, w - 190, cy, d, 0.58 + 0.07 * Math.sin(phase * 1.1), "产能", ELECTRIC_BLUE);
            drawRibbonGauge(g2, w - 100, cy, d, 0.998, "在线", STATUS_OK);

            int midX = w / 2 - 120;
            int baseY = h - 14;
            for (int i = 0; i < 16; i++) {
                double amp = 0.25 + 0.75 * (0.5 + 0.5 * Math.sin(phase * 1.2 + i * 0.4));
                int bh = (int) ((h - 36) * amp * 0.45);
                int x = midX + i * 14;
                g2.setColor(new Color(0, 180, 240, 100));
                g2.fillRect(x, baseY - bh, 10, bh);
                g2.setColor(new Color(120, 230, 255, 160));
                g2.drawRect(x, baseY - bh, 10, bh);
            }
            g2.setColor(TEXT_MUTED);
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
            g2.drawString("产能输出", midX, h - 2);

            int lx = 200;
            int lm = h / 2 + 6;
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(255, 160, 90, 160));
            int px = lx;
            int py = lm;
            for (int x = lx + 3; x < midX - 20; x += 5) {
                float p = (x - lx) / (float) (midX - 20 - lx);
                int y = lm + (int) (Math.sin(p * 10 + phase) * 12 + Math.sin(p * 19 - phase) * 5);
                g2.drawLine(px, py, x, y);
                px = x;
                py = y;
            }
            g2.setColor(TEXT_MUTED);
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
            g2.drawString("能耗趋势", lx, lm - 16);

            g2.dispose();
        }

        private static void drawRibbonGauge(
                Graphics2D g2, int x, int y, int d, double pct, String label, Color c) {
            g2.setColor(new Color(30, 55, 75, 200));
            g2.fillOval(x, y, d, d);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(20, 40, 55, 255));
            g2.drawOval(x, y, d, d);
            int sweep = (int) (360 * Math.max(0, Math.min(1, pct)));
            g2.setColor(c);
            g2.drawArc(x, y, d, d, 90, -sweep);
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
            g2.setColor(TEXT_MUTED);
            g2.drawString(label, x, y + d + 12);
            g2.setColor(ACCENT);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            g2.drawString(String.format("%02.0f%%", pct * 100), x + 12, y + d / 2 + 5);
        }
    }

    /** 顶栏组件：供外部每秒刷新时钟。 */
    public static final class CommandHeaderBar {
        public final JPanel panel;
        public final JLabel clockLabel;

        CommandHeaderBar(JPanel panel, JLabel clockLabel) {
            this.panel = panel;
            this.clockLabel = clockLabel;
        }
    }

    /** 指挥台顶栏：HUD 抬头条 + 链路状态。 */
    public static CommandHeaderBar buildCommandHeader() {
        JPanel shell = new JPanel(new BorderLayout(0, 0));
        shell.setOpaque(true);
        shell.setBackground(new Color(0x030810));
        shell.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x00c8ff)),
                        BorderFactory.createEmptyBorder(10, 18, 10, 18)));

        JPanel west = new JPanel(new BorderLayout(4, 2));
        west.setOpaque(false);
        west.add(
                new JLabel(
                        "<html><span style='color:#3ee8ff;font-weight:bold;font-size:17px;letter-spacing:3px;'>LovingAI</span><br/>"
                                + "<span style='color:#5a7a90;font-size:10px;font-family:monospace;'>◢ ACMI · NEURAL CONSOLE · HUD v2</span></html>"),
                BorderLayout.NORTH);

        JLabel center =
                new JLabel(
                        "<html><div style='text-align:center;'>"
                                + "<span style='color:#e8f4ff;font-size:17px;font-weight:600;letter-spacing:4px;'>"
                                + "战术神经界面 · 生命体征 / 认知链 / 观测阵列"
                                + "</span><br/>"
                                + "<span style='color:#6a8aa8;font-size:10px;font-family:monospace;'>"
                                + "LOOPBACK 127.0.0.1 · SANDBOX–REALITY · PERCEPTION · OBSERVABILITY"
                                + "</span></div></html>",
                        JLabel.CENTER);

        JLabel clock = new JLabel("00:00:00", JLabel.RIGHT);
        clock.setForeground(ELECTRIC_BLUE);
        Font mono = new Font(Font.MONOSPACED, Font.BOLD, 20);
        clock.setFont(mono);

        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(clock, BorderLayout.CENTER);

        shell.add(west, BorderLayout.WEST);
        shell.add(center, BorderLayout.CENTER);
        shell.add(east, BorderLayout.EAST);

        JPanel wrap = new JPanel(new BorderLayout(0, 0));
        wrap.setOpaque(false);
        wrap.add(shell, BorderLayout.NORTH);
        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(true);
        status.setBackground(new Color(0x02060c));
        status.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, GRID_MAJOR),
                        BorderFactory.createEmptyBorder(5, 18, 7, 18)));
        JLabel st =
                new JLabel(
                        "<html>"
                                + "<span style='color:"
                                + String.format("#%06x", STATUS_OK.getRGB() & 0xffffff)
                                + ";font-size:12px;'>●</span> "
                                + "<span style='color:#7a9ab8;font-size:10px;font-family:monospace;'>LINK OK</span> &nbsp; "
                                + "<span style='color:#3dff9a;font-size:12px;'>●</span> "
                                + "<span style='color:#7a9ab8;font-size:10px;font-family:monospace;'>HTTP 127.0.0.1:8080</span> &nbsp; "
                                + "<span style='color:#5a7a90;font-size:10px;font-family:monospace;'>塑圆 · 导入 · 沙盒 · 观测</span>"
                                + "</html>");
        status.add(st, BorderLayout.WEST);
        wrap.add(status, BorderLayout.SOUTH);
        return new CommandHeaderBar(wrap, clock);
    }

    /** 须在构造任何 Swing 窗口之前调用一次。 */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            boolean set = false;
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    set = true;
                    break;
                }
            }
            if (!set) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (Exception ignored2) {
            }
        }

        FontUIResource uiFont = deriveUiFont();
        UI_FONT_RESOURCE = uiFont;
        UI_FONT = uiFont;
        MONO_FONT = deriveMonoFont();
        for (Enumeration<Object> e = UIManager.getDefaults().keys(); e.hasMoreElements(); ) {
            Object k = e.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof FontUIResource) {
                UIManager.put(k, uiFont);
            }
        }
        UIManager.put("Panel.background", BG_PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TabbedPane.background", BG_DEEP);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.unselectedBackground", BG_DEEP);
        UIManager.put("TabbedPane.contentAreaColor", BG_PANEL);
        UIManager.put("TextField.background", CONSOLE_BG);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextArea.background", CONSOLE_BG);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", new Color(0x114a5e));
        UIManager.put("TextArea.selectionForeground", TEXT);
        UIManager.put("TextField.selectionBackground", new Color(0x114a5e));
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("Table.background", CONSOLE_BG);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", ACCENT_DIM);
        UIManager.put("Table.selectionBackground", new Color(0x0f3a52));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", new Color(0x071a26));
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("TableHeader.font", uiFont.deriveFont(Font.BOLD, uiFont.getSize2D()));
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.background", BG_PANEL);
        Color btnBg = new Color(0x0c2434);
        UIManager.put("Button.background", btnBg);
        UIManager.put("Button.foreground", ACCENT);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("MenuBar.background", BG_DEEP);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("MenuItem.background", BG_PANEL);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", new Color(0x0d2b3d));
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("ScrollPane.background", BG_DEEP);
        UIManager.put("Viewport.background", BG_DEEP);
        UIManager.put("SplitPane.background", BG_DEEP);
        UIManager.put("SplitPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("SplitPane.dividerSize", 7);
        UIManager.put("SplitPaneDivider.border", new BorderUIResource(BorderFactory.createMatteBorder(0, 0, 0, 0, new Color(0, 0, 0, 0))));
        UIManager.put("ScrollBarUI", "com.lovingai.ui.NeonScrollBarUI");
        UIManager.put("ScrollBar.width", 9);
        UIManager.put("ScrollBar.thumb", new Color(0x2bbad6));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(0x0b2a3a));
        UIManager.put("ScrollBar.thumbHighlight", new Color(0x80f0ff));
        UIManager.put("ScrollBar.thumbShadow", new Color(0x0b2a3a));
        UIManager.put("ScrollBar.track", new Color(0x031018));
        UIManager.put("ScrollBar.trackHighlight", new Color(0x031018));
        UIManager.put("ComboBox.background", CONSOLE_BG);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", new Color(0x114a5e));
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ToolTip.background", new Color(0x071420));
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createLineBorder(new Color(0x007fa0), 1)));
        UIManager.put(
                "ScrollPane.border",
                new BorderUIResource(BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0x007fa0))));
        UIManager.put(
                "TextField.border",
                new BorderUIResource(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(0x007fa0), 1),
                                BorderFactory.createEmptyBorder(6, 10, 6, 10))));
        UIManager.put(
                "TextArea.border",
                new BorderUIResource(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(0x007fa0), 1),
                                BorderFactory.createEmptyBorder(8, 10, 8, 10))));
        UIManager.put(
                "Button.border",
                new BorderUIResource(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(ELECTRIC_BLUE, 1),
                                BorderFactory.createEmptyBorder(5, 14, 5, 14))));
        UIManager.put(
                "TabbedPane.tabInsets",
                new javax.swing.plaf.InsetsUIResource(8, 12, 8, 12));
        UIManager.put(
                "TabbedPane.selectedTabPadInsets",
                new javax.swing.plaf.InsetsUIResource(3, 3, 3, 3));
        UIManager.put("TabbedPane.selected", BG_PANEL);
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.highlight", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.light", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.shadow", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.darkShadow", new Color(0, 0, 0, 0));

        UIManager.put("control", BG_PANEL);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusBase", new Color(0x071c28));
        UIManager.put("nimbusBlueGrey", new Color(0x123043));
        UIManager.put("nimbusLightBackground", BG_PANEL);
        UIManager.put("info", new Color(0x071420));
    }

    private static FontUIResource deriveUiFont() {
        String[] preferred = new String[] {"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "PingFang SC", "Noto Sans CJK SC", "Dialog"};
        String chosen = "Dialog";
        try {
            String[] fam = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String p : preferred) {
                for (String f : fam) {
                    if (p.equalsIgnoreCase(f)) {
                        chosen = f;
                        break;
                    }
                }
                if (!"Dialog".equals(chosen)) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        int size = parseUiInt(System.getProperty("lovingai.ui.font.size"), 14);
        size = clampUiInt(size, 11, 24);
        return new FontUIResource(new Font(chosen, Font.PLAIN, size));
    }

    private static Font deriveMonoFont() {
        String[] preferred = new String[] {"Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas", "Menlo", "Monaco", Font.MONOSPACED};
        String chosen = Font.MONOSPACED;
        try {
            String[] fam = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            outer:
            for (String p : preferred) {
                for (String f : fam) {
                    if (p.equalsIgnoreCase(f)) {
                        chosen = f;
                        break outer;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        int size = parseUiInt(System.getProperty("lovingai.ui.mono.size"), 13);
        size = clampUiInt(size, 11, 26);
        return new Font(chosen, Font.PLAIN, size);
    }

    private static int parseUiInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clampUiInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    public static Font uiFont(int style, int size) {
        Font base = UI_FONT;
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, 13);
        }
        return base.deriveFont(style, (float) size);
    }

    public static Font monoFont(int style, int size) {
        Font base = MONO_FONT;
        if (base == null) {
            base = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        }
        return base.deriveFont(style, (float) size);
    }

    public static Border panelBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(0, 127, 160, 160), 1),
                        title,
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        uiFont(Font.BOLD, 12),
                        new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 200)),
                BorderFactory.createEmptyBorder(6, 8, 8, 8));
    }

    public static JPanel createTopBanner() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x030a12));
        p.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, ELECTRIC_BLUE),
                        BorderFactory.createEmptyBorder(8, 16, 10, 16)));
        JLabel l =
                new JLabel(
                        "<html><span style='color:#3ee8ff;font-weight:bold;font-size:15px;font-family:monospace;'>LovingAI</span>"
                                + "<span style='color:#a8c4dc;'> · TACTICAL UI</span>"
                                + "<span style='color:#5a7a88;font-size:10px;'> // LOOPBACK · SANDBOX</span></html>");
        p.add(l, BorderLayout.WEST);
        return p;
    }

    public static JPanel createBreadcrumbBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x02080e));
        p.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT_DIM),
                        BorderFactory.createEmptyBorder(6, 16, 8, 16)));
        JLabel l =
                new JLabel(
                        "<html><span style='color:#5a7a90;font-family:monospace;font-size:10px;'>TRACE</span> "
                                + "<span style='color:#3ee8ff;'>LOCAL</span>"
                                + "<span style='color:#3a5060;'> → </span>"
                                + "<span style='color:#9eb8d0;'>SANDBOX⇄REALITY</span>"
                                + "<span style='color:#3a5060;'> → </span>"
                                + "<span style='color:#9eb8d0;'>CORPUS</span>"
                                + "<span style='color:#3a5060;'> · </span>"
                                + "<span style='color:#6a8aa8;font-size:10px;'>DREAM · COLLAPSE · BRIDGE · CHAIN</span></html>");
        p.add(l, BorderLayout.WEST);
        return p;
    }

    public static JPanel wrapTabbedPane(JTabbedPane tabs) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);
        shell.setBackground(new Color(4, 10, 16, 120));
        shell.setBorder(BorderFactory.createEmptyBorder());
        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(true);
        inner.setBackground(new Color(5, 12, 20, 230));
        inner.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0x00c8ff)),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        inner.add(tabs, BorderLayout.CENTER);
        shell.add(inner, BorderLayout.CENTER);
        return shell;
    }

    public static void styleTerminalScroll(JScrollPane sp) {
        if (sp == null) {
            return;
        }
        sp.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 127, 160, 150), 1),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        sp.getViewport().setBackground(CONSOLE_BG);
    }

    public static void styleTabbedPane(JTabbedPane tabs) {
        tabs.setTabPlacement(JTabbedPane.LEFT);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setBackground(BG_DEEP);
        tabs.setForeground(TEXT);
        tabs.setOpaque(true);
        tabs.setFont(uiFont(Font.PLAIN, 12));
    }
}
