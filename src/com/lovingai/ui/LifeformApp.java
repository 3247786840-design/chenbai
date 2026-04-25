package com.lovingai.ui;

import com.lovingai.LivingAI;
import com.lovingai.ai.LocalAiPrefs;
import com.lovingai.LivingAI_Phase2Ext;
import com.lovingai.core.BlockSelfPortrait;
import com.lovingai.sandbox.SandboxPhysicsCorpus;
import com.lovingai.util.HttpFileDownload;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.util.concurrent.Callable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Properties;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可视化：总览 · 对话塑圆 · 本地 AI · 导入与学习 · 网站抓取 · 认知日志。
 * {@code java -cp build com.lovingai.ui.LifeformApp}
 */
public final class LifeformApp {

    private LifeformApp() {}

    public static void main(String[] args) throws Exception {
        SciFiTheme.install();
        Thread http =
                new Thread(
                        () -> {
                            try {
                                LivingAI.initializeSystem();
                                LivingAI_Phase2Ext.initializePhase2();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        },
                        "lovingai-http");
        http.start();
        Thread.sleep(900);
        SwingUtilities.invokeLater(() -> new LifeformFrame().setVisible(true));
    }

    static final class LifeformFrame extends JFrame {
        private static final Pattern JSON_STR = Pattern.compile("\"(response|text)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

        /**
         * POST /api/chat 可能含多轮本地模型与长 JSON；读超时须大于服务端最坏等待，避免客户端先断开导致
         * {@code Unexpected end of file from server}（不删减任何对话能力，仅放宽 HTTP 等待）。
         */
        private static final int CHAT_HTTP_READ_TIMEOUT_MS = 20 * 60 * 1000;

        private final JLabel headline = new JLabel(" ");
        private final JTextArea rawStatus = new JTextArea(6, 72);
        /** 625 格（25×25）方块自画像（与 /api/life/block-shape 同源） */
        private final JLabel[][] blockShapeCells = new JLabel[BlockSelfPortrait.ROWS][BlockSelfPortrait.COLS];
        private final DefaultTableModel tableModel =
                new DefaultTableModel(
                        new Object[] {
                            "name", "type", "maturity", "chaos", "loveW", "self", "sRadius", "sEcc", "act#"
                        },
                        0);
        private final JTable table = new JTable(tableModel);
        private final JTextArea cognitionLog = new JTextArea(14, 80);
        private final JTextArea relationPulseView = new JTextArea(7, 72);
        private final JTextArea chatArea = new JTextArea(16, 72);
        private final JTextField chatInput = new JTextField(48);
        private final JCheckBox useImportedContext =
                new JCheckBox("本回合并入导入资料（默开；取消则本轮不检索）", true);
        private final JCheckBox useLocalLlm =
                new JCheckBox("本地辅助大模型：润色与连环追问（默开；无服务时自动旁路；LM Studio 见下方）", true);
        /** 默认开：生命体经本机模型以第一人称开口（内在肌理仍由圆与情感先行合成并写入提示）。关则恢复「结构主文+并联回声」。 */
        private final JCheckBox localMindPrimary =
                new JCheckBox("本机主声：生命体开口（默开；需上项与可用 baseUrl）", true);
        /** 朗读本条生命体回复（Windows：系统语音；见「设备与语音」页说明） */
        private final JCheckBox voiceAutoSpeak =
                new JCheckBox("自动朗读生命体本条回复（宿主侧语音）", false);
        private String chatConversationId = UUID.randomUUID().toString();

        /** 仪表盘轮询在后台执行，避免 EDT 上同步 httpGet 导致界面卡死。 */
        private volatile boolean dashboardPollBusy = false;

        private static final int DASHBOARD_POLL_INTERVAL_MS = 2500;
        private final JTextField aiBase = new JTextField(LocalAiPrefs.DEFAULT_BASE_URL, 36);
        private final JTextField aiModel = new JTextField(LocalAiPrefs.DEFAULT_MODEL, 20);
        private final JComboBox<String> aiLatencyProfile =
                new JComboBox<>(new String[] {"极速（fast）", "平衡（balanced）", "深思（deep）"});
        private final JTextArea aiPrompt = new JTextArea(8, 72);
        private final JTextArea aiOut = new JTextArea(8, 72);
        /** /v1/models 代理返回的原始 JSON（读取） */
        private final JTextArea auxModelsJson = new JTextArea(6, 36);
        /** GET /api/localai/prefs 原文（读取） */
        private final JTextArea auxPrefsJson = new JTextArea(6, 36);
        /** 向辅助大脑单独追问（不经由「与它交流」塑圆） */
        private final JTextField auxAskField = new JTextField(48);
        /** 辅助大脑调用记录（提示词生成 + 单独追问） */
        private final JTextArea auxBrainSessionLog = new JTextArea(10, 72);
        private final JTextArea importList = new JTextArea(14, 80);
        private final JLabel importStatus = new JLabel(" ");
        /** 对某条导入做文学蒸馏时填写的文档 id（见列表） */
        private final JTextField importDistillId = new JTextField(24);

        private final JTextField webHostField = new JTextField(28);
        private final JTextField webUrlField = new JTextField(40);
        private final JTextField webFileNameField = new JTextField(20);
        private final JTextArea webTeachArea = new JTextArea(6, 72);
        private final JTextArea webConfigArea = new JTextArea(8, 80);
        private final JLabel webPanelStatus = new JLabel(" ");
        private final JCheckBox perceptionEnabled = new JCheckBox("启用自动感知调度", false);
        private final JCheckBox perceptionNetworkUnlocked =
                new JCheckBox("联网保护锁（解锁后才允许网络读取）", false);
        private final JTextField perceptionUnlockWindowSecField = new JTextField("300", 5);
        private final JTextField perceptionIntervalSecField = new JTextField("180", 6);
        private final JTextField perceptionMaxItemsField = new JTextField("3", 6);
        private final JTextField perceptionMaxFramesField = new JTextField("6", 6);
        private final JCheckBox perceptionFilterAsciiHeavy =
                new JCheckBox("过滤英文占比过高行", true);
        private final JTextField perceptionMaxAsciiRatioField = new JTextField("0.22", 5);
        private final JCheckBox perceptionBlockSourcePrompt =
                new JCheckBox("拦截源码/提示词诱导语句", true);
        private final JComboBox<String> perceptionModeBox =
                new JComboBox<>(new String[] {"混合（文章+视觉）", "仅文章", "仅视觉"});
        private final JComboBox<String> perceptionSourceTypeBox =
                new JComboBox<>(new String[] {"文章", "视觉"});
        private final JTextField perceptionSourceUrlField = new JTextField(42);
        private final JTextField perceptionSourceLabelField = new JTextField(16);
        private final JTextField perceptionBatchLimitField = new JTextField("12", 5);
        private final JTextArea perceptionSourcesArea = new JTextArea(10, 80);
        private static final String DEFAULT_FANQIE_LIBRARY_URL = "https://fanqienovel.com/library?enter_from=menu";
        private static final String DEFAULT_FANQIE_READER_URL = "https://fanqienovel.com/reader/6982735801973113351";
        private static final String DEFAULT_QIDIAN_FREE_URL = "https://www.qidian.com/all/vip0/";
        private final DefaultTableModel perceptionNovelTableModel =
                new DefaultTableModel(
                        new Object[] {
                            "小说标识", "源数量", "章节数", "章节范围", "缺章数", "缺章预览", "最近读取"
                        },
                        0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
        private final JTable perceptionNovelTable = new JTable(perceptionNovelTableModel);
        private final TableRowSorter<DefaultTableModel> perceptionNovelSorter =
                new TableRowSorter<>(perceptionNovelTableModel);
        private final JCheckBox perceptionMissingOnly = new JCheckBox("仅显示缺章", false);
        private final JLabel perceptionStatus = new JLabel(" ");

        private final JCheckBox uiDeviceEnabled =
                new JCheckBox("启用手机/伴侣端投喂（需 pushToken；详见文档）", false);
        private final JTextField uiDeviceToken = new JTextField(44);
        private final JCheckBox uiDeviceListenLan =
                new JCheckBox("在局域网开放独立摄入口（改后需重启 LovingAI 进程）", false);
        private final JTextField uiDeviceListenPort = new JTextField("8787", 6);
        private final JTextField uiDeviceAllowedIds = new JTextField(
                "meizu6", 28);
        private final JTextArea uiDeviceStatus = new JTextArea(5, 80);
        private final JLabel uiDeviceLanHint = new JLabel(" ");

        /** USB 有线：adb 可执行文件（或 PATH 中的 adb） */
        private final JTextField uiUsbAdbExe = new JTextField("adb", 10);
        /** 伴侣 APK 写入的默认导出文件（与 android/usb-hook-notepad 包名一致） */
        private final JTextField uiUsbRemoteFile =
                new JTextField(
                        "/sdcard/Android/data/com.lovingai.usbhook/files/lovingai_usb_notes.txt", 72);
        /** 伴侣 APK 直链（https）；下载到 PC 的 companion-cache，手机仍只装轻量采集端 */
        private final JTextField uiUsbCompanionUrl = new JTextField(52);
        private final JTextArea uiUsbLog = new JTextArea(4, 80);

        private final JTextArea dreamModuleJson = new JTextArea(10, 80);
        private final JTextArea dreamLogFilter = new JTextArea(10, 80);
        private final JTextArea metabolismView = new JTextArea(10, 80);
        private final JTextField dreamAwakeIntervalMinField = new JTextField("120", 6);
        private final JTextField dreamDurationMinField = new JTextField("30", 6);
        private final JLabel dreamRhythmStatus = new JLabel(" ");
        private final JTextArea sandboxModuleJson = new JTextArea(10, 80);
        private final JTextArea sandboxTrajectoryView = new JTextArea(9, 80);
        private final JComboBox<String> sandboxTrajectoryProfile =
                new JComboBox<>(new String[] {"lite", "balanced", "deep"});
        private final JCheckBox sandboxTrajectoryDelayed = new JCheckBox("扛不住时使用延时演算", true);
        private final JTextField sandboxTrajectorySeed = new JTextField(48);
        private final JTextArea bridgeLogView = new JTextArea(14, 80);
        private final JTextArea projectSyncLog = new JTextArea(22, 80);
        /** 沙盒引用的现实物理常数/条文（写入 data/sandbox/physics-reference.txt） */
        private final JTextArea physicsRefEditor = new JTextArea(7, 72);

        private JTabbedPane tabs;
        private JFrame companionTalkWindow;
        private JDialog auxiliaryBrainDialog;

        LifeformFrame() {
            super("LovingAI · 神经界面");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            if (companionTalkWindow != null) {
                                companionTalkWindow.dispose();
                                companionTalkWindow = null;
                            }
                            if (auxiliaryBrainDialog != null) {
                                auxiliaryBrainDialog.dispose();
                                auxiliaryBrainDialog = null;
                            }
                        }
                    });
            headline.setFont(SciFiTheme.uiFont(Font.BOLD, 14));
            headline.setForeground(SciFiTheme.ACCENT);
            rawStatus.setEditable(false);
            rawStatus.setBackground(SciFiTheme.CONSOLE_BG);
            rawStatus.setForeground(SciFiTheme.TEXT);
            rawStatus.setLineWrap(true);
            rawStatus.setWrapStyleWord(true);
            cognitionLog.setEditable(false);
            cognitionLog.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));
            cognitionLog.setBackground(SciFiTheme.CONSOLE_BG);
            cognitionLog.setForeground(SciFiTheme.TEXT);
            cognitionLog.setLineWrap(true);
            cognitionLog.setWrapStyleWord(true);
            relationPulseView.setEditable(false);
            relationPulseView.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            relationPulseView.setBackground(SciFiTheme.CONSOLE_BG);
            relationPulseView.setForeground(new Color(0xa9d4f5));
            relationPulseView.setLineWrap(true);
            relationPulseView.setWrapStyleWord(true);
            chatArea.setEditable(false);
            chatArea.setBackground(SciFiTheme.CONSOLE_BG);
            chatArea.setForeground(SciFiTheme.TEXT);
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            aiPrompt.setLineWrap(true);
            aiPrompt.setWrapStyleWord(true);
            aiOut.setEditable(false);
            aiOut.setLineWrap(true);
            styleMono(dreamModuleJson);
            styleMono(dreamLogFilter);
            styleMono(metabolismView);
            styleMono(sandboxModuleJson);
            styleMono(sandboxTrajectoryView);
            styleMono(bridgeLogView);
            styleMono(projectSyncLog);
            dreamModuleJson.setLineWrap(true);
            sandboxModuleJson.setLineWrap(true);
            sandboxTrajectoryView.setLineWrap(true);

            JPanel overview = new JPanel(new BorderLayout(6, 6));
            overview.setOpaque(true);
            overview.setBackground(SciFiTheme.BG_PANEL);
            overview.setBorder(
                    BorderFactory.createCompoundBorder(
                            SciFiTheme.panelBorder("总览 · 方块自画像 / 圆表 / 认知日志"),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            JPanel north = new JPanel(new BorderLayout(4, 4));
            north.add(headline, BorderLayout.NORTH);
            JPanel blockWrap = new JPanel(new BorderLayout(4, 4));
            blockWrap.setOpaque(false);
            JLabel blockTitle =
                    new JLabel(
                            "<html><span style='color:#8aa0b8;'>方块自画像</span> "
                                    + "<span style='color:#4ee7f5;'>625 格 (25×25)</span>"
                                    + "<span style='color:#8aa0b8;'> · 情感核 / 圆 / 现实桥 · 时变扰动 · 视觉脉冲 · 非模型</span></html>");
            JPanel blockGrid = new JPanel(new java.awt.GridLayout(BlockSelfPortrait.ROWS, BlockSelfPortrait.COLS, 1, 1));
            blockGrid.setBackground(SciFiTheme.BG_DEEP);
            for (int j = 0; j < BlockSelfPortrait.ROWS; j++) {
                for (int i = 0; i < BlockSelfPortrait.COLS; i++) {
                    JLabel cell = new JLabel();
                    cell.setOpaque(true);
                    cell.setBackground(SciFiTheme.BG_DEEP);
                    cell.setBorder(BorderFactory.createLineBorder(new Color(26, 74, 92, 110), 1));
                    blockShapeCells[j][i] = cell;
                    blockGrid.add(cell);
                }
            }
            blockWrap.add(blockTitle, BorderLayout.NORTH);
            blockWrap.add(blockGrid, BorderLayout.CENTER);
            JPanel statusCol = new JPanel(new BorderLayout(6, 6));
            statusCol.setOpaque(false);
            statusCol.add(blockWrap, BorderLayout.NORTH);
            JScrollPane rawStatusScroll = new JScrollPane(rawStatus);
            SciFiTheme.styleTerminalScroll(rawStatusScroll);
            JPanel relationWrap = new JPanel(new BorderLayout(2, 2));
            relationWrap.setOpaque(false);
            relationWrap.add(
                    new JLabel("关系脉冲（温度/趋势/修订/冲突调解回放）"),
                    BorderLayout.NORTH);
            JScrollPane relationScroll = new JScrollPane(relationPulseView);
            SciFiTheme.styleTerminalScroll(relationScroll);
            relationWrap.add(relationScroll, BorderLayout.CENTER);
            JSplitPane statusRelationSplit =
                    new JSplitPane(JSplitPane.VERTICAL_SPLIT, rawStatusScroll, relationWrap);
            statusRelationSplit.setResizeWeight(0.62);
            statusRelationSplit.setOneTouchExpandable(true);
            statusCol.add(statusRelationSplit, BorderLayout.CENTER);
            north.add(statusCol, BorderLayout.CENTER);
            table.setPreferredScrollableViewportSize(new Dimension(700, 180));
            JScrollPane tableScroll = new JScrollPane(table);
            SciFiTheme.styleTerminalScroll(tableScroll);
            JSplitPane splitMid =
                    new JSplitPane(JSplitPane.VERTICAL_SPLIT, north, tableScroll);
            splitMid.setResizeWeight(0.4);
            JPanel overviewCenter = new JPanel(new BorderLayout(4, 4));
            overviewCenter.setOpaque(false);
            overviewCenter.add(new SciFiTheme.HudKpiRibbon(), BorderLayout.NORTH);
            overviewCenter.add(splitMid, BorderLayout.CENTER);
            overview.add(overviewCenter, BorderLayout.CENTER);
            JPanel logWrap = new JPanel(new BorderLayout(2, 2));
            logWrap.setOpaque(false);
            logWrap.add(new JLabel("认知 / 行为日志（环形缓冲，可观察「塑圆·梦境·哲学·本地AI」）"), BorderLayout.NORTH);
            JScrollPane cognitionScroll = new JScrollPane(cognitionLog);
            SciFiTheme.styleTerminalScroll(cognitionScroll);
            logWrap.add(cognitionScroll, BorderLayout.CENTER);
            overview.add(logWrap, BorderLayout.SOUTH);

            JPanel chatPanel = new JPanel(new BorderLayout(6, 6));
            chatPanel.setOpaque(true);
            chatPanel.setBackground(SciFiTheme.BG_PANEL);
            chatPanel.setBorder(SciFiTheme.panelBorder("与它交流 · 塑圆（/api/chat）"));
            JScrollPane chatScroll = new JScrollPane(chatArea);
            SciFiTheme.styleTerminalScroll(chatScroll);
            chatArea.setOpaque(false);
            chatArea.setForeground(new Color(0xeaf8ff));
            chatArea.setFont(SciFiTheme.uiFont(Font.PLAIN, 14));
            chatArea.setMargin(new Insets(16, 16, 16, 16));
            chatScroll.setOpaque(false);
            chatScroll.getViewport().setOpaque(false);
            chatScroll.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0, 127, 160, 120), 1),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            SciFiTheme.DialogueStagePanel dialogueStage = new SciFiTheme.DialogueStagePanel();
            dialogueStage.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            dialogueStage.add(chatScroll, BorderLayout.CENTER);
            chatPanel.add(dialogueStage, BorderLayout.CENTER);
            JPanel chatSouth = new JPanel(new BorderLayout(4, 4));
            JPanel chatHint = new JPanel(new java.awt.GridLayout(5, 1, 2, 2));
            chatHint.add(new JLabel("对话塑造圆（/api/chat；可反复检索导入资料以加速圆的成长）"));
            chatHint.add(useImportedContext);
            chatHint.add(useLocalLlm);
            chatHint.add(localMindPrimary);
            JPanel voiceRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            voiceRow.add(voiceAutoSpeak);
            JButton btnTestVoice = new JButton("试播语音");
            btnTestVoice.addActionListener(
                    e ->
                            VoiceOutput.speakAsync(
                                    "宿主，这是试播。若你听不见，请检查 Windows 是否已安装中文语音包。"));
            voiceRow.add(btnTestVoice);
            chatHint.add(voiceRow);
            chatSouth.add(chatHint, BorderLayout.NORTH);
            chatSouth.add(chatInput, BorderLayout.CENTER);
            JPanel chatEast = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
            JButton newChatSession = new JButton("新会话");
            newChatSession.addActionListener(
                    e -> {
                        chatConversationId = UUID.randomUUID().toString();
                        chatArea.append("\n【系统】已生成新会话 id，思考计数从零开始。\n");
                    });
            JButton sendChat = new JButton("发送");
            sendChat.addActionListener(e -> sendChat());
            chatEast.add(newChatSession);
            chatEast.add(sendChat);
            chatSouth.add(chatEast, BorderLayout.EAST);
            getRootPane().setDefaultButton(sendChat);
            chatPanel.add(chatSouth, BorderLayout.SOUTH);

            styleMono(auxModelsJson);
            styleMono(auxPrefsJson);
            auxBrainSessionLog.setEditable(false);
            auxBrainSessionLog.setLineWrap(true);
            auxBrainSessionLog.setWrapStyleWord(true);
            auxBrainSessionLog.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));

            JPanel aiPanel = new JPanel(new BorderLayout(8, 8));
            aiPanel.setBorder(
                    SciFiTheme.panelBorder("辅助大脑 — 本机推理（OpenAI 兼容，如 LM Studio；经 LovingAI 代理，仅本机）"));

            JPanel aiNorth = new JPanel();
            aiNorth.setLayout(new javax.swing.BoxLayout(aiNorth, javax.swing.BoxLayout.Y_AXIS));
            JPanel aiRow1 = new JPanel(new BorderLayout(6, 4));
            aiRow1.add(new JLabel("推理服务基址"), BorderLayout.WEST);
            aiRow1.add(aiBase, BorderLayout.CENTER);
            JPanel aiRow2 = new JPanel(new BorderLayout(6, 4));
            aiRow2.add(new JLabel("模型"), BorderLayout.WEST);
            aiRow2.add(aiModel, BorderLayout.CENTER);
            JPanel aiRow3 = new JPanel(new BorderLayout(6, 4));
            aiRow3.add(new JLabel("推理档位"), BorderLayout.WEST);
            aiLatencyProfile.setToolTipText("极速=更快返回；平衡=默认；深思=更长展开。仅调整时延与参数，不改功能链路。");
            aiRow3.add(aiLatencyProfile, BorderLayout.CENTER);
            JPanel aiBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            JButton pickModel = new JButton("选择模型…");
            pickModel.addActionListener(e -> LocalModelDialog.show(LifeformFrame.this));
            JButton btnRefreshModels = new JButton("刷新模型列表");
            btnRefreshModels.addActionListener(e -> refreshAuxModelsJson());
            JButton btnReadPrefs = new JButton("读取配置");
            btnReadPrefs.addActionListener(e -> refreshAuxPrefsJson());
            JButton btnSavePrefs = new JButton("保存配置到服务端");
            btnSavePrefs.addActionListener(e -> saveAuxPrefsToServer());
            aiBtns.add(pickModel);
            aiBtns.add(btnRefreshModels);
            aiBtns.add(btnReadPrefs);
            aiBtns.add(btnSavePrefs);
            aiRow2.add(aiBtns, BorderLayout.EAST);
            aiNorth.add(aiRow1);
            aiNorth.add(aiRow2);
            aiNorth.add(aiRow3);

            JSplitPane readSplit =
                    new JSplitPane(
                            JSplitPane.HORIZONTAL_SPLIT,
                            new JScrollPane(auxModelsJson),
                            new JScrollPane(auxPrefsJson));
            readSplit.setResizeWeight(0.5);
            readSplit.setBorder(
                    BorderFactory.createTitledBorder(
                            "读取：模型列表（OpenAI 兼容 /v1/models）与当前偏好"));
            aiNorth.add(readSplit);

            aiPanel.add(aiNorth, BorderLayout.NORTH);

            JPanel aiMid = new JPanel(new BorderLayout(6, 6));
            aiMid.add(
                    new JLabel("长提示词（塑形前整理思路；执行生成时整段发给辅助大脑）"),
                    BorderLayout.NORTH);
            aiMid.add(new JScrollPane(aiPrompt), BorderLayout.CENTER);
            JPanel aiMidSouth = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
            JButton runAi = new JButton("执行提示词（生成）");
            runAi.addActionListener(e -> runLocalAi());
            aiMidSouth.add(runAi);
            aiMid.add(aiMidSouth, BorderLayout.SOUTH);
            aiPanel.add(aiMid, BorderLayout.CENTER);

            JPanel aiSouth = new JPanel(new BorderLayout(6, 6));
            JPanel askBlock = new JPanel(new BorderLayout(4, 4));
            askBlock.add(
                    new JLabel("单独追问（不经「与它交流」塑圆；生命体想弄清某件事时用）"),
                    BorderLayout.NORTH);
            JPanel askLine = new JPanel(new BorderLayout(4, 4));
            askLine.add(auxAskField, BorderLayout.CENTER);
            JButton askBrain = new JButton("向辅助大脑提问");
            askBrain.addActionListener(e -> askAuxiliaryBrainQuick());
            askLine.add(askBrain, BorderLayout.EAST);
            askBlock.add(askLine, BorderLayout.CENTER);
            aiSouth.add(askBlock, BorderLayout.NORTH);

            JPanel logBlock = new JPanel(new BorderLayout(4, 4));
            logBlock.add(new JLabel("调用记录（提示词与追问都会追加）"), BorderLayout.NORTH);
            logBlock.add(new JScrollPane(auxBrainSessionLog), BorderLayout.CENTER);
            aiSouth.add(logBlock, BorderLayout.CENTER);

            JPanel outBlock = new JPanel(new BorderLayout(4, 4));
            outBlock.add(new JLabel("最近一次响应原文"), BorderLayout.NORTH);
            outBlock.add(new JScrollPane(aiOut), BorderLayout.CENTER);
            aiSouth.add(outBlock, BorderLayout.SOUTH);
            aiPanel.add(aiSouth, BorderLayout.SOUTH);

            JPanel importPanel = new JPanel(new BorderLayout(6, 6));
            importPanel.setBorder(SciFiTheme.panelBorder("导入与外触 — 宿主世界的符号化接触"));
            importList.setEditable(false);
            importList.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));
            importList.setBackground(SciFiTheme.CONSOLE_BG);
            importList.setForeground(SciFiTheme.TEXT);
            importPanel.add(new JScrollPane(importList), BorderLayout.CENTER);
            JPanel impSouth = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
            impSouth.add(importStatus);
            JPanel impButtons = new JPanel();
            JButton impFile = new JButton("导入文本类文件…");
            impFile.addActionListener(e -> importFiles(false));
            JButton impZip = new JButton("导入 ZIP…");
            impZip.addActionListener(e -> importFiles(true));
            JButton impRefresh = new JButton("刷新列表");
            impRefresh.addActionListener(e -> refreshImportList());
            JButton impClear = new JButton("清空资料库");
            impClear.addActionListener(e -> clearImportLibrary());
            impButtons.add(impFile);
            impButtons.add(impZip);
            impButtons.add(impRefresh);
            impButtons.add(impClear);
            impSouth.add(impButtons);
            JPanel distillRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            distillRow.add(new JLabel("条目 id："));
            distillRow.add(importDistillId);
            JButton distHeur = new JButton("启发式蒸馏（无模型）");
            distHeur.addActionListener(e -> runLiteraryDistill("heuristic"));
            JButton distLlm = new JButton("本地模型蒸馏（LLM）");
            distLlm.addActionListener(e -> runLiteraryDistill("llm"));
            distillRow.add(distHeur);
            distillRow.add(distLlm);
            impSouth.add(distillRow);
            importPanel.add(impSouth, BorderLayout.SOUTH);
            importPanel.add(
                    new JLabel(
                            "<html><body style='width:820px;color:#e6f2ff;'>"
                                    + "<b style='color:#4ee7f5;'>外触动机</b> · 导入的文档是你给予的「外部接触」样本；系统以此为动力做检索、塑圆与自省，而不只是冷归档。<br/>"
                                    + "<span style='color:#8aa0b8;'>支持常见文本（.txt .md .json .html .csv 等）；ZIP 会解压并索引其中可读文本。"
                                    + "长篇小说可先导入，再填 <b style='color:#4ee7f5;'>条目 id</b> 做「蒸馏」：摘要层会优先进入对话，辅助思考世界与自我追问（见下方按钮）。</span>"
                                    + "</body></html>"),
                    BorderLayout.NORTH);

            JPanel webPanel = new JPanel(new BorderLayout(6, 6));
            webPanel.setBorder(SciFiTheme.panelBorder("网站抓取与站点说明 — 白名单 + 自然语言「怎么用」"));
            webConfigArea.setEditable(false);
            webConfigArea.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            webConfigArea.setBackground(SciFiTheme.CONSOLE_BG);
            webConfigArea.setForeground(SciFiTheme.TEXT);
            webTeachArea.setLineWrap(true);
            webTeachArea.setWrapStyleWord(true);
            webTeachArea.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));
            webTeachArea.setBackground(SciFiTheme.CONSOLE_BG);
            webTeachArea.setForeground(SciFiTheme.TEXT);
            JPanel webNorth = new JPanel(new BorderLayout(4, 4));
            webNorth.add(
                    new JLabel(
                            "<html><body style='width:860px;color:#e6f2ff;'>先在下方填写<strong style='color:#4ee7f5;'>主机名</strong>（如 example.com）加入<strong>白名单</strong>，再填 <strong>URL</strong> 抓取页面正文入库；「站点说明」用自然语言描述导航或章节规则，对话里你若提到该站的链接，会优先带上这段说明。<br/><span style='color:#8aa0b8;'>非无头浏览器：仅 HTTP GET 与 HTML 去标签；不执行脚本。请自行遵守 robots 与版权。</span></body></html>"),
                    BorderLayout.NORTH);
            webNorth.add(new JScrollPane(webConfigArea), BorderLayout.CENTER);
            JPanel webCenter = new JPanel(new java.awt.GridLayout(0, 1, 4, 6));
            JPanel rowAllow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowAllow.add(new JLabel("主机："));
            rowAllow.add(webHostField);
            JButton btnWebAllow = new JButton("加入白名单");
            btnWebAllow.addActionListener(e -> webPostAllow());
            rowAllow.add(btnWebAllow);
            JButton btnWebCfg = new JButton("刷新配置");
            btnWebCfg.addActionListener(e -> refreshWebConfig());
            rowAllow.add(btnWebCfg);
            webCenter.add(rowAllow);
            JPanel rowTeach = new JPanel(new BorderLayout(4, 4));
            rowTeach.add(new JLabel("自然语言站点说明（对上面主机生效）："), BorderLayout.NORTH);
            rowTeach.add(new JScrollPane(webTeachArea), BorderLayout.CENTER);
            JPanel rowTeachBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            JButton btnSavePb = new JButton("保存站点说明");
            btnSavePb.addActionListener(e -> webSavePlaybook());
            rowTeachBtns.add(btnSavePb);
            rowTeach.add(rowTeachBtns, BorderLayout.SOUTH);
            webCenter.add(rowTeach);
            JPanel rowFetch = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowFetch.add(new JLabel("页面 URL："));
            rowFetch.add(webUrlField);
            rowFetch.add(new JLabel("入库文件名（可选）："));
            rowFetch.add(webFileNameField);
            JButton btnFetch = new JButton("抓取并加入导入库");
            btnFetch.addActionListener(e -> webFetchUrl());
            rowFetch.add(btnFetch);
            webCenter.add(rowFetch);
            webPanel.add(webNorth, BorderLayout.NORTH);
            webPanel.add(webCenter, BorderLayout.CENTER);
            webPanel.add(webPanelStatus, BorderLayout.SOUTH);

            JPanel perceptionPanel = new JPanel(new BorderLayout(6, 6));
            perceptionPanel.setBorder(SciFiTheme.panelBorder("自动感知调度 · 视觉巡览 + 文章吸收"));
            perceptionSourcesArea.setEditable(false);
            perceptionSourcesArea.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));
            perceptionSourcesArea.setBackground(SciFiTheme.CONSOLE_BG);
            perceptionSourcesArea.setForeground(SciFiTheme.TEXT);
            perceptionNovelTable.setFillsViewportHeight(true);
            perceptionNovelTable.setRowSorter(perceptionNovelSorter);
            perceptionNovelTable.setDefaultRenderer(
                    Object.class,
                    new javax.swing.table.DefaultTableCellRenderer() {
                        @Override
                        public Component getTableCellRendererComponent(
                                JTable table,
                                Object value,
                                boolean isSelected,
                                boolean hasFocus,
                                int row,
                                int column) {
                            Component c =
                                    super.getTableCellRendererComponent(
                                            table, value, isSelected, hasFocus, row, column);
                            int modelRow = table.convertRowIndexToModel(row);
                            Object missingObj = perceptionNovelTableModel.getValueAt(modelRow, 4);
                            int missing;
                            try {
                                missing = Integer.parseInt(String.valueOf(missingObj));
                            } catch (Exception ex) {
                                missing = 0;
                            }
                            if (isSelected) return c;
                            if (missing > 0) {
                                c.setBackground(new Color(0x3A1A1A));
                                c.setForeground(new Color(0xFFD2D2));
                            } else {
                                c.setBackground(new Color(0x0B1220));
                                c.setForeground(SciFiTheme.TEXT);
                            }
                            return c;
                        }
                    });
            JPanel perceptionTop = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
            JPanel pCfg = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            pCfg.add(perceptionEnabled);
            pCfg.add(perceptionNetworkUnlocked);
            pCfg.add(new JLabel("解锁秒数"));
            pCfg.add(perceptionUnlockWindowSecField);
            pCfg.add(new JLabel("模式"));
            pCfg.add(perceptionModeBox);
            pCfg.add(new JLabel("间隔秒"));
            pCfg.add(perceptionIntervalSecField);
            pCfg.add(new JLabel("每轮上限"));
            pCfg.add(perceptionMaxItemsField);
            pCfg.add(new JLabel("视觉帧上限"));
            pCfg.add(perceptionMaxFramesField);
            pCfg.add(perceptionFilterAsciiHeavy);
            pCfg.add(new JLabel("英文比例阈值"));
            pCfg.add(perceptionMaxAsciiRatioField);
            pCfg.add(perceptionBlockSourcePrompt);
            JButton pLoad = new JButton("读取配置");
            pLoad.addActionListener(e -> refreshPerceptionConfig());
            JButton pSave = new JButton("保存配置");
            pSave.addActionListener(e -> savePerceptionConfig());
            JButton pRun = new JButton("立即执行一轮");
            pRun.addActionListener(e -> runPerceptionNow());
            pCfg.add(pLoad);
            pCfg.add(pSave);
            pCfg.add(pRun);
            perceptionTop.add(pCfg);
            JPanel pSource = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            pSource.add(new JLabel("类型"));
            pSource.add(perceptionSourceTypeBox);
            pSource.add(new JLabel("地址"));
            pSource.add(perceptionSourceUrlField);
            pSource.add(new JLabel("标签"));
            pSource.add(perceptionSourceLabelField);
            JButton pAdd = new JButton("新增源");
            pAdd.addActionListener(e -> addPerceptionSource());
            JButton pBatchAdd = new JButton("书页批量加章节");
            pBatchAdd.addActionListener(e -> batchAddPerceptionSourcesFromBookUrl());
            JButton pBatchPreview = new JButton("预览章节");
            pBatchPreview.addActionListener(e -> previewPerceptionSourcesFromBookUrl());
            JButton pRemove = new JButton("移除源(url)");
            pRemove.addActionListener(e -> removePerceptionSource());
            JButton pRefresh = new JButton("刷新源列表");
            pRefresh.addActionListener(e -> refreshPerceptionSources());
            JButton pNovel = new JButton("同书聚合视图");
            pNovel.addActionListener(e -> refreshPerceptionNovelSummary());
            JButton pSortMissingDesc = new JButton("缺章降序");
            pSortMissingDesc.addActionListener(e -> sortPerceptionNovelsByMissingDesc());
            JButton pRestoreDefaults = new JButton("恢复默认双站");
            pRestoreDefaults.addActionListener(e -> restoreDefaultPerceptionSources());
            perceptionMissingOnly.addActionListener(e -> applyPerceptionNovelFilter());
            pSource.add(pAdd);
            pSource.add(new JLabel("批量数"));
            pSource.add(perceptionBatchLimitField);
            pSource.add(pBatchPreview);
            pSource.add(pBatchAdd);
            pSource.add(pRemove);
            pSource.add(pRefresh);
            pSource.add(pNovel);
            pSource.add(pSortMissingDesc);
            pSource.add(pRestoreDefaults);
            pSource.add(perceptionMissingOnly);
            perceptionTop.add(pSource);
            perceptionPanel.add(perceptionTop, BorderLayout.NORTH);
            JSplitPane perceptionSplit =
                    new JSplitPane(
                            JSplitPane.VERTICAL_SPLIT,
                            new JScrollPane(perceptionNovelTable),
                            new JScrollPane(perceptionSourcesArea));
            perceptionSplit.setResizeWeight(0.55);
            perceptionPanel.add(perceptionSplit, BorderLayout.CENTER);
            perceptionPanel.add(perceptionStatus, BorderLayout.SOUTH);

            JPanel dreamPanel = new JPanel(new BorderLayout(6, 6));
            dreamPanel.setOpaque(true);
            dreamPanel.setBackground(SciFiTheme.BG_PANEL);
            dreamPanel.setBorder(
                    BorderFactory.createCompoundBorder(
                            SciFiTheme.panelBorder("梦境模块 · JSON 与认知抽行"),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            JPanel dreamTop = new JPanel(new BorderLayout(4, 4));
            dreamTop.add(new JLabel("梦境引擎（JSON）与认知日志中与梦相关的行"), BorderLayout.NORTH);
            JPanel dreamCfg = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            JButton dreamLoadCfg = new JButton("读取梦境节律");
            dreamLoadCfg.addActionListener(e -> refreshDreamRhythmConfig());
            JButton dreamSaveCfg = new JButton("保存梦境节律");
            dreamSaveCfg.addActionListener(e -> saveDreamRhythmConfig());
            dreamCfg.add(new JLabel("清醒分钟后入梦"));
            dreamCfg.add(dreamAwakeIntervalMinField);
            dreamCfg.add(new JLabel("梦境分钟"));
            dreamCfg.add(dreamDurationMinField);
            dreamCfg.add(dreamLoadCfg);
            dreamCfg.add(dreamSaveCfg);
            dreamCfg.add(dreamRhythmStatus);
            dreamTop.add(dreamCfg, BorderLayout.SOUTH);
            dreamPanel.add(dreamTop, BorderLayout.NORTH);
            JSplitPane dreamSplit =
                    new JSplitPane(
                            JSplitPane.VERTICAL_SPLIT,
                            new JScrollPane(dreamModuleJson),
                            new JScrollPane(dreamLogFilter));
            dreamSplit.setResizeWeight(0.45);
            dreamPanel.add(dreamSplit, BorderLayout.CENTER);

            JPanel metabolismPanel = new JPanel(new BorderLayout(6, 6));
            metabolismPanel.setOpaque(true);
            metabolismPanel.setBackground(SciFiTheme.BG_PANEL);
            metabolismPanel.setBorder(
                    BorderFactory.createCompoundBorder(
                            SciFiTheme.panelBorder("生命代谢仪表 · 增圆 / 破碎 / 回收"),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            JPanel metabolismTop = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            JButton metabolismRefresh = new JButton("刷新代谢");
            metabolismRefresh.addActionListener(
                    e -> {
                        try {
                            String json =
                                    httpGet(
                                            "http://127.0.0.1:"
                                                    + LivingAI.HTTP_PORT
                                                    + "/api/life/metabolism");
                            metabolismView.setText(formatMetabolismView(json));
                            metabolismView.setCaretPosition(0);
                        } catch (Exception ex) {
                            metabolismView.setText("代谢读取失败: " + ex.getMessage());
                        }
                    });
            metabolismTop.add(
                    new JLabel("代谢是冰冷的也是必须的；情感不会让代谢变少，只会让代谢加速。"));
            metabolismTop.add(metabolismRefresh);
            metabolismPanel.add(metabolismTop, BorderLayout.NORTH);
            metabolismPanel.add(new JScrollPane(metabolismView), BorderLayout.CENTER);

            JPanel sandboxPanel = new JPanel(new BorderLayout(6, 6));
            sandboxPanel.setBorder(SciFiTheme.panelBorder("沙盒 · 现实桥（混沌构图 → 崩溃求解 → 论证 → 拟现实 → 链世界）"));
            JPanel sbNorth = new JPanel(new BorderLayout(4, 4));
            sbNorth.add(
                    new JLabel(
                            "<html><body style='width:880px;'><span style='color:#8aa0b8;'>沙盒志业：以混沌为质料预演，在失稳与系统崩溃的张力中求解并完成论证，向可核对的「现实世界」侧写收敛；下方物理条文用于</span>"
                                    + "<span style='color:#4ee7f5;'>锚定已知自然律</span>"
                                    + "<span style='color:#8aa0b8;'>。每次对话触发的预演先读锚定再推演，经 bridge.log 成为链向宿主世界的接口。保存写入 </span><code>data/sandbox/physics-reference.txt</code></body></html>"),
                    BorderLayout.NORTH);
            physicsRefEditor.setLineWrap(true);
            physicsRefEditor.setWrapStyleWord(true);
            physicsRefEditor.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            physicsRefEditor.setBackground(SciFiTheme.CONSOLE_BG);
            physicsRefEditor.setForeground(SciFiTheme.TEXT);
            sbNorth.add(new JScrollPane(physicsRefEditor), BorderLayout.CENTER);
            JPanel sbPhysBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            JButton btnPhysLoad = new JButton("从服务端加载");
            btnPhysLoad.addActionListener(e -> loadPhysicsReferenceFromServer());
            JButton btnPhysSave = new JButton("保存到服务端");
            btnPhysSave.addActionListener(e -> savePhysicsReferenceToServer());
            JButton btnPhysSeed = new JButton("填入内置种子");
            btnPhysSeed.addActionListener(
                    e -> physicsRefEditor.setText(SandboxPhysicsCorpus.defaultSeed()));
            JButton btnTrajectory = new JButton("一键演绎人生轨迹");
            btnTrajectory.addActionListener(e -> runSandboxLifeTrajectory());
            sbPhysBtns.add(btnPhysLoad);
            sbPhysBtns.add(btnPhysSave);
            sbPhysBtns.add(btnPhysSeed);
            sbPhysBtns.add(new JLabel("复杂度"));
            sbPhysBtns.add(sandboxTrajectoryProfile);
            sbPhysBtns.add(sandboxTrajectoryDelayed);
            sbPhysBtns.add(btnTrajectory);
            sbNorth.add(sbPhysBtns, BorderLayout.SOUTH);
            JPanel trajectoryBox = new JPanel(new BorderLayout(4, 4));
            trajectoryBox.setBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(0x31506f)),
                            "人生轨迹演绎（沙盒多分支，含性能护栏）"));
            JPanel trajectorySeedRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            trajectorySeedRow.add(new JLabel("轨迹种子（可空）："));
            trajectorySeedRow.add(sandboxTrajectorySeed);
            trajectoryBox.add(trajectorySeedRow, BorderLayout.NORTH);
            trajectoryBox.add(new JScrollPane(sandboxTrajectoryView), BorderLayout.CENTER);
            JSplitPane sbSplit =
                    new JSplitPane(
                            JSplitPane.VERTICAL_SPLIT,
                            new JScrollPane(sandboxModuleJson),
                            new JScrollPane(bridgeLogView));
            sbSplit.setResizeWeight(0.4);
            JPanel sbCenter = new JPanel(new BorderLayout(4, 4));
            sbCenter.add(sbNorth, BorderLayout.NORTH);
            JSplitPane sbWithTrajectory =
                    new JSplitPane(JSplitPane.VERTICAL_SPLIT, trajectoryBox, sbSplit);
            sbWithTrajectory.setResizeWeight(0.36);
            sbCenter.add(sbWithTrajectory, BorderLayout.CENTER);
            sandboxPanel.add(sbCenter, BorderLayout.CENTER);

            JPanel syncPanel = new JPanel(new BorderLayout(6, 6));
            syncPanel.setOpaque(true);
            syncPanel.setBackground(SciFiTheme.BG_PANEL);
            syncPanel.setBorder(
                    BorderFactory.createCompoundBorder(
                            SciFiTheme.panelBorder("项目日志同步 · 与 /api/life/cognition-log 同源"),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            syncPanel.add(
                    new JLabel("项目环形日志全量同步（与 HTTP /api/life/cognition-log 同源）"),
                    BorderLayout.NORTH);
            JScrollPane projectSyncScroll = new JScrollPane(projectSyncLog);
            SciFiTheme.styleTerminalScroll(projectSyncScroll);
            syncPanel.add(projectSyncScroll, BorderLayout.CENTER);

            JPanel deviceVoicePanel = new JPanel(new BorderLayout(8, 8));
            deviceVoicePanel.setOpaque(true);
            deviceVoicePanel.setBackground(SciFiTheme.BG_PANEL);
            deviceVoicePanel.setBorder(
                    BorderFactory.createCompoundBorder(
                            SciFiTheme.panelBorder("旧手机接入 · 语音 · USB 有线"),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            deviceVoicePanel.add(
                    new JLabel(
                            "<html><body style='width:920px;color:#e6f2ff;'>"
                                    + "<b style='color:#4ee7f5;'>魅族 6 等旧机</b>：<strong>生命体与 LovingAI 本体在 PC</strong>；手机可仅作「记事/缓冲」。"
                                    + "两种方式：① WiFi + token 推送（见下区）；② <strong>USB 调试</strong>：在 PC 上安装伴侣 APK、用数据线拉取文本到本机 <code>data/perception/usb-inbox</code> 再导入。"
                                    + "<br/><span style='color:#8aa0b8;'>WiFi：主对话口 <code>127.0.0.1:8080</code>；伴侣说明 <code>docs/DEVICE_PHONE_INGEST.md</code> · USB 流程 <code>docs/USB_WIRED_COMPANION.md</code>。</span></body></html>"),
                    BorderLayout.NORTH);
            JPanel dvForm = new JPanel(new java.awt.GridLayout(0, 1, 6, 6));
            JPanel rowEn = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowEn.add(uiDeviceEnabled);
            dvForm.add(rowEn);
            JPanel rowTok = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowTok.add(new JLabel("pushToken："));
            uiDeviceToken.setToolTipText("与手机端请求头 X-Device-Push-Token 一致");
            rowTok.add(uiDeviceToken);
            JButton btnGenTok = new JButton("生成随机 token");
            btnGenTok.addActionListener(
                    e ->
                            uiDeviceToken.setText(
                                    UUID.randomUUID().toString().replace("-", "")
                                            + UUID.randomUUID().toString().replace("-", "")));
            rowTok.add(btnGenTok);
            dvForm.add(rowTok);
            JPanel rowLan = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowLan.add(uiDeviceListenLan);
            rowLan.add(new JLabel("端口"));
            rowLan.add(uiDeviceListenPort);
            dvForm.add(rowLan);
            JPanel rowIds = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            rowIds.add(new JLabel("允许的 deviceId（逗号分隔，空=不限制）："));
            rowIds.add(uiDeviceAllowedIds);
            dvForm.add(rowIds);
            dvForm.add(uiDeviceLanHint);
            JPanel rowBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            JButton btnDvSave = new JButton("保存到 data/perception/device-push.properties");
            btnDvSave.addActionListener(e -> saveDevicePushPropertiesFromUi());
            JButton btnDvLoad = new JButton("从文件重新加载");
            btnDvLoad.addActionListener(e -> loadDevicePushPropertiesIntoUi());
            JButton btnDvStatus = new JButton("刷新状态");
            btnDvStatus.addActionListener(e -> refreshDevicePushStatusFromServer());
            rowBtns.add(btnDvSave);
            rowBtns.add(btnDvLoad);
            rowBtns.add(btnDvStatus);
            dvForm.add(rowBtns);

            JPanel usbBlock = new JPanel(new java.awt.GridLayout(0, 1, 6, 6));
            usbBlock.setBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(0x31506f)),
                            "USB 有线 · adb 安装伴侣 / 拉取记录（软件本体在 PC）"));
            JPanel usbRow1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            usbRow1.add(new JLabel("adb："));
            usbRow1.add(uiUsbAdbExe);
            JButton btnAdbVer = new JButton("检测 adb");
            btnAdbVer.addActionListener(e -> runUsbAdbTask("adb version", () -> AdbUsbBridge.version(uiUsbAdbExe.getText().trim())));
            JButton btnAdbDev = new JButton("列出设备");
            btnAdbDev.addActionListener(
                    e -> runUsbAdbTask("adb devices", () -> AdbUsbBridge.devices(uiUsbAdbExe.getText().trim())));
            JButton btnAdbInstall = new JButton("选择 APK 并安装到手机");
            btnAdbInstall.addActionListener(e -> chooseApkAndInstallViaAdb());
            usbRow1.add(btnAdbVer);
            usbRow1.add(btnAdbDev);
            usbRow1.add(btnAdbInstall);
            usbBlock.add(usbRow1);

            uiUsbCompanionUrl.setToolTipText(
                    "HTTPS 直链到 .apk（可放在内网或发布页）。下载保存在 data/perception/companion-cache/，LovingAI 本体仍在 PC。");
            JPanel usbRowCompanion =
                    new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            usbRowCompanion.add(new JLabel("伴侣 APK 下载地址："));
            usbRowCompanion.add(uiUsbCompanionUrl);
            JButton btnUsbSaveUrl = new JButton("保存下载地址");
            btnUsbSaveUrl.addActionListener(e -> saveUsbCompanionPropertiesFromUi());
            JButton btnUsbDl = new JButton("一键下载伴侣 APK");
            btnUsbDl.addActionListener(e -> downloadCompanionApkToCache(false));
            JButton btnUsbDlInstall = new JButton("下载并安装到手机");
            btnUsbDlInstall.addActionListener(e -> downloadCompanionApkToCache(true));
            usbRowCompanion.add(btnUsbSaveUrl);
            usbRowCompanion.add(btnUsbDl);
            usbRowCompanion.add(btnUsbDlInstall);
            usbBlock.add(usbRowCompanion);

            JPanel usbRow2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            usbRow2.add(new JLabel("手机端导出文件路径："));
            uiUsbRemoteFile.setToolTipText("伴侣 APK 默认写入路径，可按实机修改");
            usbRow2.add(uiUsbRemoteFile);
            JButton btnPull = new JButton("adb pull 到 usb-inbox");
            btnPull.addActionListener(e -> pullUsbRemoteToInbox());
            JButton btnImpUsb = new JButton("将 usb-inbox 导入资料库");
            btnImpUsb.addActionListener(e -> importUsbInboxToCorpus());
            usbRow2.add(btnPull);
            usbRow2.add(btnImpUsb);
            usbBlock.add(usbRow2);
            uiUsbLog.setEditable(false);
            uiUsbLog.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            uiUsbLog.setBackground(SciFiTheme.CONSOLE_BG);
            uiUsbLog.setForeground(new Color(0xa8c8e8));
            usbBlock.add(new JScrollPane(uiUsbLog));

            JSplitPane dvUsbSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dvForm, usbBlock);
            dvUsbSplit.setResizeWeight(0.48);
            dvUsbSplit.setOneTouchExpandable(true);

            uiDeviceStatus.setEditable(false);
            uiDeviceStatus.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            uiDeviceStatus.setBackground(SciFiTheme.CONSOLE_BG);
            uiDeviceStatus.setForeground(SciFiTheme.TEXT);
            deviceVoicePanel.add(dvUsbSplit, BorderLayout.CENTER);
            deviceVoicePanel.add(new JScrollPane(uiDeviceStatus), BorderLayout.SOUTH);

            tabs = new JTabbedPane();
            SciFiTheme.styleTabbedPane(tabs);
            tabs.addTab("与它交流 · 塑圆", chatPanel);
            tabs.addTab("总览 + 认知日志", overview);
            tabs.addTab("辅助大脑 (本地 AI)", aiPanel);
            tabs.addTab("导入 / 外触", importPanel);
            tabs.addTab("网站抓取", webPanel);
            tabs.addTab("自动感知", perceptionPanel);
            tabs.addTab("设备与语音", deviceVoicePanel);
            tabs.addTab("梦境模块", dreamPanel);
            tabs.addTab("生命代谢", metabolismPanel);
            tabs.addTab("沙盒·现实桥", sandboxPanel);
            tabs.addTab("项目日志同步", syncPanel);

            SciFiTheme.HudBackdropPanel root = new SciFiTheme.HudBackdropPanel();
            SciFiTheme.CommandHeaderBar cmdHeader = SciFiTheme.buildCommandHeader();
            JPanel fusionTop = new JPanel(new BorderLayout(6, 4));
            fusionTop.setOpaque(false);
            fusionTop.add(cmdHeader.panel, BorderLayout.CENTER);
            JPanel quickRow = new JPanel(new BorderLayout(8, 2));
            quickRow.setOpaque(false);
            JLabel quickHint =
                    new JLabel(
                            "<html><span style='color:#6a90a8;font-family:monospace;font-size:10px;'>"
                                    + "FUSION VIEW · DYNAMIC HUD BACKDROP + FUNCTIONAL STACK · NO FEATURE TRIM"
                                    + "</span></html>");
            quickHint.setFont(quickHint.getFont().deriveFont(Font.PLAIN, 11f));
            quickRow.add(quickHint, BorderLayout.CENTER);
            JButton quickOpen = new JButton("打开 /talk");
            quickOpen.addActionListener(
                    e -> {
                        try {
                            java.awt.Desktop.getDesktop()
                                    .browse(URI.create("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/talk"));
                        } catch (Exception ex) {
                            rawStatus.append("\n" + ex.getMessage());
                        }
                    });
            quickRow.add(quickOpen, BorderLayout.EAST);
            fusionTop.add(quickRow, BorderLayout.SOUTH);
            root.add(fusionTop, BorderLayout.NORTH);
            root.add(SciFiTheme.wrapTabbedPane(tabs), BorderLayout.CENTER);
            setJMenuBar(buildMenuBar());
            tabs.setSelectedIndex(0);

            javax.swing.Timer clockTimer =
                    new javax.swing.Timer(
                            1000,
                            e -> {
                                LocalTime t = LocalTime.now();
                                cmdHeader.clockLabel.setText(
                                        String.format(
                                                "%02d:%02d:%02d",
                                                t.getHour(),
                                                t.getMinute(),
                                                t.getSecond()));
                            });
            clockTimer.setInitialDelay(0);
            clockTimer.start();

            aiLatencyProfile.setSelectedIndex(1);
            loadDevicePushPropertiesIntoUi();
            loadUsbCompanionPropertiesIntoUi();
            updateDeviceLanHintLabel();
            try {
                Files.createDirectories(
                        Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "usb-inbox"));
                Files.createDirectories(
                        Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "companion-cache"));
            } catch (Exception ignored) {
            }
            Timer deviceUiTimer =
                    new Timer(
                            5000,
                            e -> {
                                refreshDevicePushStatusFromServer();
                                updateDeviceLanHintLabel();
                            });
            deviceUiTimer.setInitialDelay(1200);
            deviceUiTimer.start();

            setContentPane(root);
            root.setBorder(BorderFactory.createEmptyBorder());
            getRootPane().setBorder(BorderFactory.createEmptyBorder());
            getContentPane().setBackground(SciFiTheme.BG_DEEP);
            setBackground(SciFiTheme.BG_DEEP);

            setPreferredSize(new Dimension(1180, 840));
            pack();
            setLocationRelativeTo(null);
            try {
                SwingUtilities.invokeLater(this::enqueueDashboardRefresh);
                refreshImportList();
                refreshWebConfig();
                refreshPerceptionConfig();
                refreshPerceptionSources();
                loadLocalAiPrefsIntoFields();
                loadPhysicsReferenceFromServer();
            } catch (Exception ex) {
                headline.setText("初始拉取: " + ex.getMessage());
            }

            Timer dashboardTimer =
                    new Timer(
                            DASHBOARD_POLL_INTERVAL_MS,
                            e -> enqueueDashboardRefresh());
            dashboardTimer.setInitialDelay(600);
            dashboardTimer.start();
        }

        /**
         * 合并原 1.5s 仪表盘刷新 + 110ms 方块动画请求：全部在后台拉取，避免阻塞 Swing EDT。若上一轮尚未结束则跳过
         * 本轮，防止请求堆积。
         */
        private void enqueueDashboardRefresh() {
            if (dashboardPollBusy) {
                return;
            }
            dashboardPollBusy = true;
            final String base = "http://127.0.0.1:" + LivingAI.HTTP_PORT;
            final String convId = chatConversationId;
            SwingWorker<Void, Void> w =
                    new SwingWorker<>() {
                        String st;
                        String tsv;
                        String log100;
                        String rpJson;
                        String rpFail;
                        String dreamJson;
                        String metabolismJson;
                        String sandboxJson;
                        String bridgeLog;
                        String fullLog280;
                        String blockJson;
                        String fatal;

                        @Override
                        protected Void doInBackground() {
                            try {
                                st = httpGet(base + "/api/status");
                                tsv = httpGet(base + "/api/life/circles.tsv");
                                log100 = httpGet(base + "/api/life/cognition-log.txt?n=100");
                                try {
                                    rpJson =
                                            httpGet(
                                                    base
                                                            + "/api/life/relation-pulse?conversationId="
                                                            + URLEncoder.encode(
                                                                    convId, StandardCharsets.UTF_8));
                                } catch (Exception ex) {
                                    rpFail = ex.getMessage();
                                }
                                dreamJson = httpGet(base + "/api/modules/dream");
                                metabolismJson = httpGet(base + "/api/life/metabolism");
                                sandboxJson = httpGet(base + "/api/modules/sandbox");
                                bridgeLog = httpGet(base + "/api/reality/bridge.log?n=120");
                                fullLog280 = httpGet(base + "/api/life/cognition-log.txt?n=280");
                                blockJson = httpGet(base + "/api/life/block-shape");
                            } catch (Exception ex) {
                                fatal = ex.getMessage();
                            }
                            return null;
                        }

                        @Override
                        protected void done() {
                            dashboardPollBusy = false;
                            try {
                                get();
                            } catch (Exception ignored) {
                            }
                            if (fatal != null) {
                                headline.setText("拉取失败: " + fatal);
                                return;
                            }
                            if (st != null) {
                                rawStatus.setText(st);
                            }
                            headline.setText(
                                    "<html><span style='color:#4ee7f5;'>链路 · 127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "</span> <span style='color:#8aa0b8;'>· 探索与外触资料 · 对话与辅助大脑 · 同源进程（回环校验）</span></html>");
                            if (tsv != null) {
                                tableModel.setRowCount(0);
                                String[] lines = tsv.split("\n");
                                for (int i = 1; i < lines.length; i++) {
                                    String line = lines[i].trim();
                                    if (line.isEmpty()) {
                                        continue;
                                    }
                                    String[] cols = line.split("\t");
                                    if (cols.length >= 9) {
                                        tableModel.addRow(
                                                new Object[] {
                                                    cols[0], cols[1], cols[2], cols[3], cols[4], cols[5],
                                                    cols[6], cols[7], cols[8]
                                                });
                                    } else if (cols.length >= 6) {
                                        tableModel.addRow(
                                                new Object[] {
                                                    cols[0], cols[1], cols[2], cols[3], cols[4], cols[5], "",
                                                    "", ""
                                                });
                                    }
                                }
                            }
                            if (log100 != null) {
                                cognitionLog.setText(log100);
                                cognitionLog.setCaretPosition(cognitionLog.getDocument().getLength());
                            }
                            if (rpFail != null) {
                                relationPulseView.setText("关系脉冲读取失败: " + rpFail);
                            } else if (rpJson != null) {
                                relationPulseView.setText(formatRelationPulseView(rpJson));
                                relationPulseView.setCaretPosition(0);
                            }
                            if (dreamJson != null) {
                                dreamModuleJson.setText(dreamJson);
                                syncDreamRhythmFromDreamModuleJson(dreamJson);
                            }
                            if (metabolismJson != null) {
                                metabolismView.setText(formatMetabolismView(metabolismJson));
                                metabolismView.setCaretPosition(0);
                            }
                            if (sandboxJson != null) {
                                sandboxModuleJson.setText(sandboxJson);
                            }
                            if (bridgeLog != null) {
                                bridgeLogView.setText(bridgeLog);
                            }
                            if (fullLog280 != null) {
                                projectSyncLog.setText(fullLog280);
                                projectSyncLog.setCaretPosition(0);
                                dreamLogFilter.setText(
                                        filterLogKinds(
                                                fullLog280, "梦境", "噩梦", "梦境·", "梦渊", "爱·梦", "碎片"));
                                dreamLogFilter.setCaretPosition(0);
                            }
                            if (blockJson != null) {
                                applyBlockShapeJson(blockJson);
                            }
                        }
                    };
            w.execute();
        }

        private static void styleMono(JTextArea a) {
            a.setEditable(false);
            a.setFont(SciFiTheme.monoFont(Font.PLAIN, 11));
            a.setBackground(SciFiTheme.CONSOLE_BG);
            a.setForeground(SciFiTheme.TEXT);
        }

        private static String filterLogKinds(String cognitionText, String... kinds) {
            if (cognitionText == null || cognitionText.isBlank()) {
                return "";
            }
            StringBuilder o = new StringBuilder();
            for (String line : cognitionText.split("\n")) {
                for (String k : kinds) {
                    if (line.contains(k)) {
                        o.append(line).append('\n');
                        break;
                    }
                }
            }
            return o.toString();
        }

        private static String formatRelationPulseView(String rpJson) {
            if (rpJson == null || rpJson.isBlank()) return "关系脉冲为空";
            String convId = firstJsonString(rpJson, "conversationId");
            String trend = firstJsonString(rpJson, "relationTrend");
            String stage = firstJsonString(rpJson, "relationStage");
            String lifeNarrative = firstJsonString(rpJson, "lifeNarrative");
            String reason = firstJsonString(rpJson, "relationReason");
            String noiseTag = firstJsonString(rpJson, "noiseTag");
            String noiseDistillation = firstJsonString(rpJson, "noiseDistillation");
            String treasure = firstJsonString(rpJson, "circleTreasureAnchor");
            String relationMemory = firstJsonString(rpJson, "relationMemory");
            String selfRevision = firstJsonString(rpJson, "lastSelfRevision");
            String conflict = firstJsonString(rpJson, "lastConflictMediation");
            double temp = firstJsonDouble(rpJson, "relationTemperature", 0.52);
            StringBuilder sb = new StringBuilder();
            sb.append("关系脉冲摘要").append('\n');
            sb.append("会话: ").append(convId == null ? "default" : convId).append('\n');
            sb.append("温度: ")
                    .append(String.format(java.util.Locale.US, "%.2f", temp))
                    .append(" | 趋势: ")
                    .append(trend == null ? "flat" : trend)
                    .append(" | 阶段: ")
                    .append(stage == null ? "探索" : stage)
                    .append('\n');
            if (lifeNarrative != null && !lifeNarrative.isBlank()) sb.append("生命叙事: ").append(lifeNarrative).append('\n');
            if (reason != null && !reason.isBlank()) sb.append("解释: ").append(reason).append('\n');
            if (noiseTag != null && !noiseTag.isBlank()) sb.append("噪声归类: ").append(noiseTag).append('\n');
            if (noiseDistillation != null && !noiseDistillation.isBlank()) sb.append("噪声蒸馏: ").append(noiseDistillation).append('\n');
            if (treasure != null && !treasure.isBlank()) sb.append("圆宝藏: ").append(treasure).append('\n');
            if (relationMemory != null && !relationMemory.isBlank()) sb.append("关系记忆: ").append(relationMemory).append('\n');
            if (selfRevision != null && !selfRevision.isBlank()) sb.append("自我修订: ").append(selfRevision).append('\n');
            if (conflict != null && !conflict.isBlank()) sb.append("冲突调解: ").append(conflict).append('\n');
            sb.append('\n').append("---- RAW JSON ----").append('\n').append(rpJson);
            return sb.toString();
        }

        private void applyBlockShapeJson(String json) {
            if (json == null || json.isBlank()) {
                return;
            }
            try {
                int key = json.indexOf("\"cells\":[");
                if (key < 0) {
                    return;
                }
                int from = key + 9;
                int to = json.indexOf(']', from);
                if (to <= from) {
                    return;
                }
                String inner = json.substring(from, to);
                String[] parts = inner.split(",");
                int n = Math.min(BlockSelfPortrait.CELL_COUNT, parts.length);
                for (int k = 0; k < n; k++) {
                    float t = Float.parseFloat(parts[k].trim());
                    int j = k / BlockSelfPortrait.COLS;
                    int i = k % BlockSelfPortrait.COLS;
                    applyBlockIntensity(blockShapeCells[j][i], t);
                }
            } catch (Exception ignored) {
            }
        }

        private static void applyBlockIntensity(JLabel cell, float t) {
            if (t < 0f) {
                t = 0f;
            } else if (t > 1f) {
                t = 1f;
            }
            int r =
                    Math.min(
                            255,
                            Math.max(
                                    0,
                                    (int)
                                            (SciFiTheme.BG_DEEP.getRed()
                                                    + (SciFiTheme.ACCENT.getRed() - SciFiTheme.BG_DEEP.getRed())
                                                            * t)));
            int g =
                    Math.min(
                            255,
                            Math.max(
                                    0,
                                    (int)
                                            (SciFiTheme.BG_DEEP.getGreen()
                                                    + (SciFiTheme.ACCENT.getGreen()
                                                                    - SciFiTheme.BG_DEEP.getGreen())
                                                            * t)));
            int b =
                    Math.min(
                            255,
                            Math.max(
                                    0,
                                    (int)
                                            (SciFiTheme.BG_DEEP.getBlue()
                                                    + (SciFiTheme.ACCENT.getBlue()
                                                                    - SciFiTheme.BG_DEEP.getBlue())
                                                            * t)));
            cell.setBackground(new Color(r, g, b));
        }

        private JMenuBar buildMenuBar() {
            JMenu talk = new JMenu("交流");
            JMenuItem openCompanion = new JMenuItem("独立交流窗口…");
            openCompanion.addActionListener(e -> showCompanionTalkWindow());
            JMenuItem focusTab = new JMenuItem("切换到「与它交流」标签");
            focusTab.addActionListener(e -> tabs.setSelectedIndex(0));
            talk.add(openCompanion);
            talk.add(focusTab);
            JMenu brain = new JMenu("辅助大脑");
            JMenuItem openAux = new JMenuItem("辅助大脑读取/调用窗口…");
            openAux.addActionListener(e -> showAuxiliaryBrainDialog());
            JMenuItem focusAuxTab = new JMenuItem("切换到「辅助大脑」标签");
            focusAuxTab.addActionListener(e -> tabs.setSelectedIndex(2));
            brain.add(openAux);
            brain.add(focusAuxTab);
            JMenuBar bar = new JMenuBar();
            bar.add(talk);
            bar.add(brain);
            return bar;
        }

        private void showCompanionTalkWindow() {
            if (companionTalkWindow != null) {
                companionTalkWindow.setVisible(true);
                companionTalkWindow.toFront();
                return;
            }
            JFrame f = new JFrame("与它交流 — LovingAI");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JTextArea area = new JTextArea(20, 56);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JTextField in = new JTextField(40);
            JCheckBox imp =
                    new JCheckBox("本回合并入导入资料（默开；取消则本轮不检索）", useImportedContext.isSelected());
            JCheckBox llm =
                    new JCheckBox("本地辅助大模型（默开；无服务时旁路）", useLocalLlm.isSelected());
            JCheckBox lmPri =
                    new JCheckBox("本机主声（生命体开口）", localMindPrimary.isSelected());
            JPanel south = new JPanel(new BorderLayout(4, 4));
            JCheckBox vShare =
                    new JCheckBox("朗读生命体回复", voiceAutoSpeak.isSelected());
            JPanel northCol = new JPanel(new java.awt.GridLayout(4, 1, 2, 2));
            northCol.add(imp);
            northCol.add(llm);
            northCol.add(lmPri);
            northCol.add(vShare);
            south.add(northCol, BorderLayout.NORTH);
            south.add(in, BorderLayout.CENTER);
            JButton send = new JButton("发送");
            send.addActionListener(e -> sendChatFrom(area, in, imp, llm, lmPri, vShare));
            south.add(send, BorderLayout.EAST);
            f.getRootPane().setDefaultButton(send);
            in.addActionListener(e -> sendChatFrom(area, in, imp, llm, lmPri, vShare));
            f.setLayout(new BorderLayout(8, 8));
            f.add(new JScrollPane(area), BorderLayout.CENTER);
            f.add(south, BorderLayout.SOUTH);
            f.pack();
            f.setMinimumSize(new Dimension(520, 420));
            f.setLocationRelativeTo(this);
            f.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            if (companionTalkWindow == f) {
                                companionTalkWindow = null;
                            }
                        }
                    });
            companionTalkWindow = f;
            f.setVisible(true);
        }

        private void sendChat() {
            sendChatFrom(
                    chatArea,
                    chatInput,
                    useImportedContext,
                    useLocalLlm,
                    localMindPrimary,
                    voiceAutoSpeak);
        }

        private void sendChatFrom(
                JTextArea targetArea,
                JTextField inputField,
                JCheckBox refImported,
                JCheckBox refLocalLlm,
                JCheckBox refLocalMindPrimary,
                JCheckBox speakReplyWhenChecked) {
            String msg = inputField.getText().trim();
            if (msg.isEmpty()) return;
            targetArea.append("\n【你】 " + msg + "\n");
            inputField.setText("");
            boolean refImp = refImported.isSelected();
            boolean llm = refLocalLlm.isSelected();
            boolean lmPrimary =
                    refLocalMindPrimary == null || refLocalMindPrimary.isSelected();
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "message="
                                            + URLEncoder.encode(msg, StandardCharsets.UTF_8)
                                            + "&useImported="
                                            + URLEncoder.encode(refImp ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&useLocalLlm="
                                            + URLEncoder.encode(llm ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&localMindPrimary="
                                            + URLEncoder.encode(lmPrimary ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&conversationId="
                                            + URLEncoder.encode(chatConversationId, StandardCharsets.UTF_8)
                                            + "&localaiBase="
                                            + URLEncoder.encode(
                                                    aiBase.getText().trim(), StandardCharsets.UTF_8)
                                            + "&localaiModel="
                                            + URLEncoder.encode(
                                                    aiModel.getText().trim(), StandardCharsets.UTF_8);
                            return httpPostForm(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/chat",
                                    form,
                                    CHAT_HTTP_READ_TIMEOUT_MS);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                String reply = firstJsonString(json, "response");
                                String circle = firstJsonString(json, "circleTouched");
                                String treasure = firstJsonString(json, "circleTreasureAnchor");
                                String srcActive = firstJsonString(json, "perceptionSourcesActive");
                                String toneProfile = firstJsonString(json, "toneProfile");
                                String closureTag = firstJsonString(json, "closureTag");
                                String relationStage = firstJsonString(json, "relationStage");
                                String noiseTag = firstJsonString(json, "noiseTag");
                                String noiseDistillation = firstJsonString(json, "noiseDistillation");
                                String replyText = reply != null ? reply : json;
                                targetArea.append("【生命体】 " + replyText + "\n");
                                if (circle != null) {
                                    targetArea.append("【触及圆】 " + circle + "\n");
                                }
                                if (treasure != null && !treasure.isBlank()) {
                                    targetArea.append("【圆宝藏】 " + treasure + "\n");
                                }
                                if (srcActive != null && !srcActive.isBlank()) {
                                    targetArea.append("【感知源】 " + srcActive + "\n");
                                }
                                if (toneProfile != null && !toneProfile.isBlank()) {
                                    targetArea.append("【语气轮廓】 " + toneProfile + "\n");
                                }
                                if (closureTag != null && !closureTag.isBlank()) {
                                    targetArea.append("【闭环标签】 " + closureTag + "\n");
                                }
                                if (relationStage != null && !relationStage.isBlank()) {
                                    targetArea.append("【关系阶段】 " + relationStage + "\n");
                                }
                                if (noiseTag != null && !noiseTag.isBlank()) {
                                    targetArea.append("【噪声归类】 " + noiseTag + "\n");
                                }
                                if (noiseDistillation != null && !noiseDistillation.isBlank()) {
                                    targetArea.append("【噪声蒸馏】 " + noiseDistillation + "\n");
                                }
                                if (speakReplyWhenChecked != null
                                        && speakReplyWhenChecked.isSelected()
                                        && reply != null
                                        && !reply.isBlank()) {
                                    VoiceOutput.speakAsync(reply);
                                }
                            } catch (Exception ex) {
                                String m = ex.getMessage();
                                if (m == null) {
                                    m = ex.getClass().getSimpleName();
                                }
                                if (m.contains("end of file") || m.contains("EOF")) {
                                    m +=
                                            "\n（常见：生命体进程退出、连接被重置、或等待仍不足；请确认 HTTP 已启动，"
                                                    + "或暂时取消「本地辅助大模型」以缩短单轮耗时后重试。）";
                                }
                                m += "\n（会话窗口不切换；保持同一 conversationId，下一轮可继续复盘本次中断。）";
                                targetArea.append("【错误】 " + m + "\n");
                            }
                        }
                    };
            w.execute();
        }

        private void loadLocalAiPrefsIntoFields() {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/prefs");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                String base = firstJsonString(json, "baseUrl");
                                String model = firstJsonString(json, "model");
                                String latencyProfile = firstJsonString(json, "latencyProfile");
                                if (base != null) aiBase.setText(base);
                                if (model != null) aiModel.setText(model);
                                if ("fast".equalsIgnoreCase(latencyProfile)) {
                                    aiLatencyProfile.setSelectedIndex(0);
                                } else if ("deep".equalsIgnoreCase(latencyProfile)) {
                                    aiLatencyProfile.setSelectedIndex(2);
                                } else {
                                    aiLatencyProfile.setSelectedIndex(1);
                                }
                                auxPrefsJson.setText(json);
                                auxPrefsJson.setCaretPosition(0);
                            } catch (Exception ignored) {
                            }
                        }
                    };
            w.execute();
        }

        private void loadPhysicsReferenceFromServer() {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/sandbox/physics");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                String t = firstJsonString(json, "text");
                                if (t != null) {
                                    physicsRefEditor.setText(t);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    };
            w.execute();
        }

        private void savePhysicsReferenceToServer() {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpPostPlain(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/sandbox/physics",
                                    physicsRefEditor.getText());
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        "已保存到 data/sandbox/physics-reference.txt",
                                        "沙盒物理参照",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void runSandboxLifeTrajectory() {
            sandboxTrajectoryView.setText("人生轨迹演绎中…");
            String profile = String.valueOf(sandboxTrajectoryProfile.getSelectedItem());
            String seed = sandboxTrajectorySeed.getText().trim();
            boolean delayed = sandboxTrajectoryDelayed.isSelected();
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String url =
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/sandbox/life-trajectory?conversationId="
                                            + URLEncoder.encode(chatConversationId, StandardCharsets.UTF_8)
                                            + "&profile="
                                            + URLEncoder.encode(profile == null ? "lite" : profile, StandardCharsets.UTF_8)
                                            + "&seed="
                                            + URLEncoder.encode(seed, StandardCharsets.UTF_8)
                                            + "&delayed="
                                            + URLEncoder.encode(delayed ? "true" : "false", StandardCharsets.UTF_8);
                            return httpGet(url);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                sandboxTrajectoryView.setText(formatTrajectoryView(json));
                                sandboxTrajectoryView.setCaretPosition(0);
                                if (firstJsonBool(json, "running", false)) {
                                    String jobId = firstJsonString(json, "jobId");
                                    if (jobId != null && !jobId.isBlank()) {
                                        pollTrajectoryJob(jobId);
                                    }
                                }
                                sandboxModuleJson.setText(httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/modules/sandbox"));
                                bridgeLogView.setText(httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/reality/bridge.log?n=120"));
                            } catch (Exception ex) {
                                sandboxTrajectoryView.setText("人生轨迹演绎失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void pollTrajectoryJob(String jobId) {
            javax.swing.Timer timer =
                    new javax.swing.Timer(
                            420,
                            null);
            timer.addActionListener(
                    e -> {
                        SwingWorker<String, Void> w =
                                new SwingWorker<>() {
                                    @Override
                                    protected String doInBackground() throws Exception {
                                        String url =
                                                "http://127.0.0.1:"
                                                        + LivingAI.HTTP_PORT
                                                        + "/api/sandbox/life-trajectory/tick?jobId="
                                                        + URLEncoder.encode(jobId, StandardCharsets.UTF_8);
                                        return httpGet(url);
                                    }

                                    @Override
                                    protected void done() {
                                        try {
                                            String json = get();
                                            sandboxTrajectoryView.setText(formatTrajectoryView(json));
                                            sandboxTrajectoryView.setCaretPosition(0);
                                            boolean running = firstJsonBool(json, "running", false);
                                            if (!running) {
                                                timer.stop();
                                            }
                                        } catch (Exception ex) {
                                            timer.stop();
                                            sandboxTrajectoryView.append("\n[轮询中断] " + ex.getMessage());
                                        }
                                    }
                                };
                        w.execute();
                    });
            timer.setRepeats(true);
            timer.start();
        }

        private static String formatTrajectoryView(String json) {
            if (json == null || json.isBlank()) return "无轨迹结果";
            String profile = firstJsonString(json, "profile");
            int runs = firstJsonInt(json, "runs", 0);
            int completed = firstJsonInt(json, "completedRuns", runs);
            boolean running = firstJsonBool(json, "running", false);
            boolean dreamThrottled = firstJsonBool(json, "dreamThrottled", false);
            int throttleWaitMs = firstJsonInt(json, "throttleWaitMs", 0);
            String lifeMode = firstJsonString(json, "lifeMode");
            double avgHarm = firstJsonDouble(json, "avgHarm", 0);
            double avgCare = firstJsonDouble(json, "avgCare", 0);
            double avgAffinity = firstJsonDouble(json, "avgAffinity", 0);
            double randomness = firstJsonDouble(json, "randomnessScore", 0);
            String suggestion = firstJsonString(json, "suggestion");
            String seed = firstJsonString(json, "seed");
            StringBuilder sb = new StringBuilder();
            sb.append("人生轨迹演绎结果").append('\n');
            sb.append("档位: ")
                    .append(profile == null ? "lite" : profile)
                    .append(" | 进度: ")
                    .append(completed)
                    .append("/")
                    .append(runs)
                    .append(running ? "（延时演算中）" : "（完成）")
                    .append('\n');
            if (lifeMode != null && !lifeMode.isBlank()) {
                sb.append("生命态: ").append(lifeMode);
                if (dreamThrottled) {
                    sb.append(" | 梦态节流中，下一步约 ")
                            .append(String.format(java.util.Locale.US, "%.1f", Math.max(0, throttleWaitMs) / 1000.0))
                            .append("s");
                } else if (running) {
                    sb.append(" | 清醒态，不限速");
                }
                sb.append('\n');
            }
            sb.append("平均伤害: ").append(String.format(java.util.Locale.US, "%.3f", avgHarm)).append('\n');
            sb.append("平均照护: ").append(String.format(java.util.Locale.US, "%.3f", avgCare)).append('\n');
            sb.append("现实亲和: ").append(String.format(java.util.Locale.US, "%.3f", avgAffinity)).append('\n');
            sb.append("随机性: ").append(String.format(java.util.Locale.US, "%.3f", randomness)).append('\n');
            if (seed != null && !seed.isBlank()) sb.append("种子: ").append(seed).append('\n');
            if (suggestion != null && !suggestion.isBlank()) sb.append("建议: ").append(suggestion).append('\n');
            sb.append('\n').append("---- RAW JSON ----").append('\n').append(json);
            return sb.toString();
        }

        private static String formatMetabolismView(String json) {
            if (json == null || json.isBlank()) return "无代谢数据";
            String phase = firstJsonString(json, "phase");
            String lifeMode = firstJsonString(json, "lifeMode");
            int circleCount = firstJsonInt(json, "circleCount", 0);
            long grown = firstJsonLong(json, "autoCircleGrownTotal", 0L);
            long pressure = firstJsonLong(json, "storagePressureEvents", 0L);
            long shattered = firstJsonLong(json, "circleShatterTotal", 0L);
            long released = firstJsonLong(json, "storageReleasedBytes", 0L);
            long used = firstJsonLong(json, "importedUsageBytes", 0L);
            long quota = firstJsonLong(json, "importedQuotaBytes", 0L);
            double ratio = firstJsonDouble(json, "importedUsageRatio", 0.0);
            StringBuilder sb = new StringBuilder();
            sb.append("生命代谢仪表").append('\n');
            sb.append("阶段: ").append(phase == null ? "seed_growth" : phase);
            if (lifeMode != null && !lifeMode.isBlank()) {
                sb.append(" | 生命态: ").append(lifeMode);
            }
            sb.append('\n');
            sb.append("圆数量: ").append(circleCount).append(" | 自动增圆累计: ").append(grown).append('\n');
            sb.append("存储压力事件: ").append(pressure).append(" | 破碎累计: ").append(shattered).append('\n');
            sb.append("累计释放: ").append(humanBytes(released)).append('\n');
            sb.append("导入占用: ")
                    .append(humanBytes(used))
                    .append(" / ")
                    .append(humanBytes(quota))
                    .append(" (")
                    .append(String.format(java.util.Locale.US, "%.2f%%", ratio * 100.0))
                    .append(")")
                    .append('\n');
            sb.append('\n').append("---- RAW JSON ----").append('\n').append(json);
            return sb.toString();
        }

        private void refreshAuxModelsJson() {
            refreshAuxModelsJsonInto(auxModelsJson);
        }

        private void refreshAuxModelsJsonInto(JTextArea target) {
            target.setText("拉取中…");
            String base = aiBase.getText().trim();
            if (base.isEmpty()) {
                base = LocalAiPrefs.DEFAULT_BASE_URL;
            }
            String fBase = base;
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/localai/models?baseUrl="
                                            + URLEncoder.encode(fBase, StandardCharsets.UTF_8));
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                if (json != null && json.contains("\"ok\":false")) {
                                    String err = firstJsonString(json, "error");
                                    String kind = firstJsonString(json, "kind");
                                    String hint = firstJsonString(json, "hintText");
                                    String req = firstJsonString(json, "requestedBaseUrl");
                                    String eb = firstJsonString(json, "effectiveBaseUrl");
                                    if (eb != null && !eb.isBlank()) {
                                        aiBase.setText(eb);
                                    }
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("本地推理不可用");
                                    if (kind != null && !kind.isBlank()) {
                                        sb.append(" [").append(kind).append("]");
                                    }
                                    sb.append("\n\n");
                                    if (req != null && !req.isBlank()) {
                                        sb.append("请求的基址: ").append(req).append("\n");
                                    }
                                    if (err != null && !err.isBlank()) {
                                        sb.append("\n").append(err).append("\n");
                                    }
                                    if (hint != null && !hint.isBlank()) {
                                        sb.append("\n").append(hint);
                                    }
                                    sb.append("\n\n---- JSON ----\n").append(json);
                                    target.setText(sb.toString());
                                    target.setCaretPosition(0);
                                    return;
                                }
                                String eb = firstJsonString(json, "effectiveBaseUrl");
                                if (eb != null && !eb.isBlank()) {
                                    aiBase.setText(eb);
                                }
                                target.setText(json);
                                target.setCaretPosition(0);
                            } catch (Exception ex) {
                                target.setText("错误: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void refreshAuxPrefsJson() {
            refreshAuxPrefsJsonInto(auxPrefsJson);
        }

        private void refreshAuxPrefsJsonInto(JTextArea target) {
            target.setText("拉取中…");
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
                                target.setText(json);
                                target.setCaretPosition(0);
                            } catch (Exception ex) {
                                target.setText("错误: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void saveAuxPrefsToServer() {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String base = aiBase.getText().trim();
                            String model = aiModel.getText().trim();
                            String latencyProfile =
                                    switch (aiLatencyProfile.getSelectedIndex()) {
                                        case 0 -> "fast";
                                        case 2 -> "deep";
                                        default -> "balanced";
                                    };
                            String form =
                                    "baseUrl="
                                            + URLEncoder.encode(base, StandardCharsets.UTF_8)
                                            + "&model="
                                            + URLEncoder.encode(model, StandardCharsets.UTF_8)
                                            + "&latencyProfile="
                                            + URLEncoder.encode(latencyProfile, StandardCharsets.UTF_8)
                                            + "&inferenceBackend="
                                            + URLEncoder.encode("openai", StandardCharsets.UTF_8);
                            return httpPostForm(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/prefs",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                auxPrefsJson.setText(json);
                                auxPrefsJson.setCaretPosition(0);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, "已保存到 data/localai.properties", "辅助大脑", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void askAuxiliaryBrainQuick() {
            String q = auxAskField.getText().trim();
            if (q.isEmpty()) return;
            String wrapped =
                    "你是 LovingAI 生命体的辅助推理模块（外接本机 OpenAI 兼容推理服务，如 LM Studio）。请用中文简洁回答；"
                            + "不要扮演「圆」或主对话里的角色。\n\n问题：\n"
                            + q;
            invokeAuxiliaryGenerate(wrapped, "【单独追问】\n" + q, auxBrainSessionLog, aiOut);
            auxAskField.setText("");
        }

        /**
         * 调用 /api/localai/generate，并写入会话记录与原文区。
         */
        private void invokeAuxiliaryGenerate(
                String fullPrompt,
                String logCaption,
                JTextArea sessionLog,
                JTextArea rawOut) {
            String base = aiBase.getText().trim();
            String modelRaw = aiModel.getText().trim();
            final String modelResolved = modelRaw.isEmpty() ? LocalAiPrefs.DEFAULT_MODEL : modelRaw;
            rawOut.setText("请求中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "baseUrl="
                                            + URLEncoder.encode(base, StandardCharsets.UTF_8)
                                            + "&model="
                                            + URLEncoder.encode(modelResolved, StandardCharsets.UTF_8)
                                            + "&prompt="
                                            + URLEncoder.encode(fullPrompt, StandardCharsets.UTF_8)
                                            + "&timeoutSec=240";
                            return httpPostForm(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/localai/generate",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                String text = firstJsonString(json, "text");
                                String err = firstJsonString(json, "error");
                                String show =
                                        text != null && !text.isBlank()
                                                ? text
                                                : (err != null && !err.isBlank() ? "错误: " + err : json);
                                rawOut.setText(show);
                                sessionLog.append("\n────────\n");
                                sessionLog.append(logCaption);
                                sessionLog.append("\n【辅助大脑】\n");
                                sessionLog.append(show);
                                sessionLog.append("\n");
                                sessionLog.setCaretPosition(sessionLog.getDocument().getLength());
                            } catch (Exception ex) {
                                rawOut.setText("错误: " + ex.getMessage());
                                sessionLog.append("\n【错误】 " + ex.getMessage() + "\n");
                            }
                        }
                    };
            w.execute();
        }

        private void showAuxiliaryBrainDialog() {
            if (auxiliaryBrainDialog != null) {
                auxiliaryBrainDialog.setVisible(true);
                auxiliaryBrainDialog.toFront();
                return;
            }
            JDialog d = new JDialog(this, "辅助大脑 — 读取与调用", false);
            JTextArea dlgModels = new JTextArea(10, 42);
            JTextArea dlgPrefs = new JTextArea(8, 42);
            JTextArea dlgLog = new JTextArea(12, 60);
            JTextArea dlgLast = new JTextArea(6, 60);
            JTextField dlgAsk = new JTextField(40);
            styleMono(dlgModels);
            styleMono(dlgPrefs);
            dlgLog.setEditable(false);
            dlgLog.setLineWrap(true);
            dlgLog.setWrapStyleWord(true);
            dlgLog.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));
            dlgLast.setEditable(false);
            dlgLast.setLineWrap(true);
            dlgLast.setWrapStyleWord(true);
            dlgLast.setFont(SciFiTheme.monoFont(Font.PLAIN, 12));

            JPanel north = new JPanel(new BorderLayout(6, 6));
            JLabel hint =
                    new JLabel(
                            "与主窗口「辅助大脑」标签共用基址/模型；此处可边读列表边追问（会话与主窗口分开）。");
            hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
            north.add(hint, BorderLayout.NORTH);
            JSplitPane sp =
                    new JSplitPane(
                            JSplitPane.HORIZONTAL_SPLIT,
                            new JScrollPane(dlgModels),
                            new JScrollPane(dlgPrefs));
            sp.setResizeWeight(0.5);
            north.add(sp, BorderLayout.CENTER);
            JPanel readBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
            JButton bModels = new JButton("刷新模型列表");
            bModels.addActionListener(e -> refreshAuxModelsJsonInto(dlgModels));
            JButton bPrefs = new JButton("读取配置");
            bPrefs.addActionListener(e -> refreshAuxPrefsJsonInto(dlgPrefs));
            readBtns.add(bModels);
            readBtns.add(bPrefs);
            north.add(readBtns, BorderLayout.SOUTH);

            JPanel center = new JPanel(new BorderLayout(4, 4));
            JPanel askRow = new JPanel(new BorderLayout(4, 4));
            askRow.add(new JLabel("向辅助大脑提问"), BorderLayout.NORTH);
            JPanel askLine = new JPanel(new BorderLayout(4, 4));
            askLine.add(dlgAsk, BorderLayout.CENTER);
            JButton askBtn = new JButton("发送");
            askBtn.addActionListener(
                    e -> {
                        String q = dlgAsk.getText().trim();
                        if (q.isEmpty()) return;
                        String wrapped =
                                "你是 LovingAI 生命体的辅助推理模块（外接本机 OpenAI 兼容推理服务，如 LM Studio）。请用中文简洁回答；"
                                        + "不要扮演「圆」或主对话里的角色。\n\n问题：\n"
                                        + q;
                        invokeAuxiliaryGenerate(
                                wrapped, "【单独追问·副窗】\n" + q, dlgLog, dlgLast);
                        dlgAsk.setText("");
                    });
            askLine.add(askBtn, BorderLayout.EAST);
            askRow.add(askLine, BorderLayout.CENTER);
            center.add(askRow, BorderLayout.NORTH);
            center.add(new JScrollPane(dlgLog), BorderLayout.CENTER);

            d.setLayout(new BorderLayout(8, 8));
            d.add(north, BorderLayout.NORTH);
            d.add(center, BorderLayout.CENTER);
            d.add(new JScrollPane(dlgLast), BorderLayout.SOUTH);
            d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            d.pack();
            d.setMinimumSize(new Dimension(640, 520));
            d.setLocationRelativeTo(this);
            d.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            if (auxiliaryBrainDialog == d) {
                                auxiliaryBrainDialog = null;
                            }
                        }
                    });
            auxiliaryBrainDialog = d;
            refreshAuxModelsJsonInto(dlgModels);
            refreshAuxPrefsJsonInto(dlgPrefs);
            d.setVisible(true);
        }

        private void refreshWebConfig() {
            webPanelStatus.setText("加载配置…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/web/config");
                        }

                        @Override
                        protected void done() {
                            try {
                                webConfigArea.setText(get());
                                webConfigArea.setCaretPosition(0);
                                webPanelStatus.setText("已刷新白名单与 playbook 列表");
                            } catch (Exception ex) {
                                webPanelStatus.setText("配置失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void webPostAllow() {
            String h = webHostField.getText().trim();
            if (h.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this, "请填写主机名。", "白名单", JOptionPane.WARNING_MESSAGE);
                return;
            }
            webPanelStatus.setText("提交中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "host=" + URLEncoder.encode(h, StandardCharsets.UTF_8);
                            return httpPostForm(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/web/allow",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                webPanelStatus.setText(j);
                                refreshWebConfig();
                            } catch (Exception ex) {
                                webPanelStatus.setText("失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage(),
                                        "白名单",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void webSavePlaybook() {
            String h = webHostField.getText().trim();
            String text = webTeachArea.getText();
            if (h.isEmpty() || text == null || text.isBlank()) {
                JOptionPane.showMessageDialog(
                        this,
                        "请填写主机名与说明正文。",
                        "站点说明",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            webPanelStatus.setText("保存中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "host="
                                            + URLEncoder.encode(h, StandardCharsets.UTF_8)
                                            + "&instruction="
                                            + URLEncoder.encode(text, StandardCharsets.UTF_8);
                            return httpPostForm(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/web/playbook",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                webPanelStatus.setText(get());
                                refreshWebConfig();
                            } catch (Exception ex) {
                                webPanelStatus.setText("失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage(),
                                        "站点说明",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void webFetchUrl() {
            String url = webUrlField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this, "请填写 URL。", "抓取", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String fn = webFileNameField.getText().trim();
            webPanelStatus.setText("抓取中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "url="
                                            + URLEncoder.encode(url, StandardCharsets.UTF_8);
                            if (!fn.isEmpty()) {
                                form +=
                                        "&fileName="
                                                + URLEncoder.encode(fn, StandardCharsets.UTF_8);
                            }
                            return httpPostForm(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/web/fetch",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                webPanelStatus.setText(j);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, j, "抓取", JOptionPane.INFORMATION_MESSAGE);
                                refreshImportList();
                            } catch (Exception ex) {
                                webPanelStatus.setText("失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage(),
                                        "抓取",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void refreshPerceptionConfig() {
            perceptionStatus.setText("读取配置中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/config");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                perceptionEnabled.setSelected(firstJsonBool(json, "enabled", false));
                                perceptionNetworkUnlocked.setSelected(firstJsonBool(json, "networkUnlocked", false));
                                perceptionUnlockWindowSecField.setText(Integer.toString(firstJsonInt(json, "unlockWindowSec", 300)));
                                perceptionIntervalSecField.setText(Integer.toString(firstJsonInt(json, "intervalSec", 180)));
                                perceptionMaxItemsField.setText(Integer.toString(firstJsonInt(json, "maxItems", 3)));
                                perceptionMaxFramesField.setText(Integer.toString(firstJsonInt(json, "maxVideoFrames", 6)));
                                perceptionFilterAsciiHeavy.setSelected(firstJsonBool(json, "filterAsciiHeavy", true));
                                perceptionMaxAsciiRatioField.setText(String.format(java.util.Locale.US, "%.2f", firstJsonDouble(json, "maxAsciiRatio", 0.22)));
                                perceptionBlockSourcePrompt.setSelected(firstJsonBool(json, "blockSourcecodePrompt", true));
                                String mode = firstJsonString(json, "mode");
                                applyPerceptionModeSelection(mode);
                                perceptionStatus.setText(
                                        "配置已加载 运行中="
                                                + firstJsonBool(json, "running", false)
                                                + " 源数量="
                                                + firstJsonInt(json, "sourceCount", 0)
                                                + " 解锁剩余秒="
                                                + firstJsonInt(json, "networkUnlockRemainSec", 0)
                                                + " 护栏连击="
                                                + firstJsonInt(json, "guardHitStreak", 0)
                                                + " 最近护栏="
                                                + (firstJsonString(json, "lastGuardHit") == null
                                                        ? ""
                                                        : firstJsonString(json, "lastGuardHit")));
                            } catch (Exception ex) {
                                perceptionStatus.setText("读取失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void savePerceptionConfig() {
            perceptionStatus.setText("保存中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "enabled="
                                            + URLEncoder.encode(perceptionEnabled.isSelected() ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&networkUnlocked="
                                            + URLEncoder.encode(perceptionNetworkUnlocked.isSelected() ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&unlockWindowSec="
                                            + URLEncoder.encode(perceptionUnlockWindowSecField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&mode="
                                            + URLEncoder.encode(canonicalPerceptionMode(String.valueOf(perceptionModeBox.getSelectedItem())), StandardCharsets.UTF_8)
                                            + "&intervalSec="
                                            + URLEncoder.encode(perceptionIntervalSecField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&maxItems="
                                            + URLEncoder.encode(perceptionMaxItemsField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&maxVideoFrames="
                                            + URLEncoder.encode(perceptionMaxFramesField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&filterAsciiHeavy="
                                            + URLEncoder.encode(perceptionFilterAsciiHeavy.isSelected() ? "true" : "false", StandardCharsets.UTF_8)
                                            + "&maxAsciiRatio="
                                            + URLEncoder.encode(perceptionMaxAsciiRatioField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&blockSourcecodePrompt="
                                            + URLEncoder.encode(perceptionBlockSourcePrompt.isSelected() ? "true" : "false", StandardCharsets.UTF_8);
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/config", form);
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                perceptionStatus.setText("配置已保存");
                                refreshPerceptionConfig();
                            } catch (Exception ex) {
                                perceptionStatus.setText("保存失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void refreshDreamRhythmConfig() {
            dreamRhythmStatus.setText("读取中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/life/dream-rhythm");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                int awakeMin = firstJsonInt(json, "awakeIntervalMin", 120);
                                int dreamMin = firstJsonInt(json, "dreamDurationMin", 30);
                                dreamAwakeIntervalMinField.setText(Integer.toString(awakeMin));
                                dreamDurationMinField.setText(Integer.toString(dreamMin));
                                boolean active = firstJsonBool(json, "forcedActive", false);
                                int nextSec = firstJsonInt(json, "nextDreamInSec", 0);
                                int remainSec = firstJsonInt(json, "dreamRemainSec", 0);
                                dreamRhythmStatus.setText(
                                        active
                                                ? ("梦中剩余≈" + (remainSec / 60) + " 分钟")
                                                : ("下次入梦≈" + (nextSec / 60) + " 分钟"));
                            } catch (Exception ex) {
                                dreamRhythmStatus.setText("读取失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void saveDreamRhythmConfig() {
            dreamRhythmStatus.setText("保存中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "awakeIntervalMin="
                                            + URLEncoder.encode(dreamAwakeIntervalMinField.getText().trim(), StandardCharsets.UTF_8)
                                            + "&dreamDurationMin="
                                            + URLEncoder.encode(dreamDurationMinField.getText().trim(), StandardCharsets.UTF_8);
                            return httpPostForm(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/life/dream-rhythm",
                                    form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                int awakeMin = firstJsonInt(json, "awakeIntervalMin", 120);
                                int dreamMin = firstJsonInt(json, "dreamDurationMin", 30);
                                dreamAwakeIntervalMinField.setText(Integer.toString(awakeMin));
                                dreamDurationMinField.setText(Integer.toString(dreamMin));
                                dreamRhythmStatus.setText("已保存：清醒 " + awakeMin + " 分钟，梦境 " + dreamMin + " 分钟");
                            } catch (Exception ex) {
                                dreamRhythmStatus.setText("保存失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void syncDreamRhythmFromDreamModuleJson(String json) {
            if (json == null || json.isBlank()) {
                return;
            }
            int awakeMin = firstJsonInt(json, "awakeIntervalMin", -1);
            int dreamMin = firstJsonInt(json, "dreamDurationMin", -1);
            if (awakeMin > 0) {
                dreamAwakeIntervalMinField.setText(Integer.toString(awakeMin));
            }
            if (dreamMin > 0) {
                dreamDurationMinField.setText(Integer.toString(dreamMin));
            }
            boolean active = firstJsonBool(json, "forcedActive", false);
            int nextSec = firstJsonInt(json, "nextDreamInSec", 0);
            int remainSec = firstJsonInt(json, "dreamRemainSec", 0);
            if (active) {
                dreamRhythmStatus.setText("梦中剩余≈" + (remainSec / 60) + " 分钟");
            } else if (nextSec > 0) {
                dreamRhythmStatus.setText("下次入梦≈" + (nextSec / 60) + " 分钟");
            }
        }

        private void refreshPerceptionSources() {
            perceptionStatus.setText("刷新源列表…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/sources");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                perceptionSourcesArea.setText(formatPerceptionSources(json));
                                perceptionSourcesArea.setCaretPosition(0);
                                perceptionStatus.setText("源列表已刷新");
                            } catch (Exception ex) {
                                perceptionStatus.setText("刷新失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void refreshPerceptionNovelSummary() {
            perceptionStatus.setText("加载同书聚合…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/novels");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                fillPerceptionNovelTable(json);
                                perceptionSourcesArea.setText(formatPerceptionNovels(json));
                                perceptionSourcesArea.setCaretPosition(0);
                                perceptionStatus.setText("同书聚合已刷新");
                            } catch (Exception ex) {
                                perceptionStatus.setText("聚合失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void fillPerceptionNovelTable(String json) {
            perceptionNovelTableModel.setRowCount(0);
            if (json == null || json.isBlank()) return;
            Pattern p =
                    Pattern.compile(
                            "\"novelKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"sourceCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterMin\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterMax\"\\s*:\\s*(\\d+)\\s*,\\s*\"missingChapterCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"missingChapterPreview\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"lastReadMs\"\\s*:\\s*(\\d+)",
                            Pattern.DOTALL);
            Matcher m = p.matcher(json);
            while (m.find()) {
                long lastReadMs;
                try {
                    lastReadMs = Long.parseLong(m.group(8));
                } catch (Exception ex) {
                    lastReadMs = 0L;
                }
                String chapterCount = m.group(3);
                String range = "0".equals(chapterCount) ? "-" : (m.group(4) + ".." + m.group(5));
                String lastRead =
                        lastReadMs <= 0
                                ? "-"
                                : DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                                        .format(
                                                Instant.ofEpochMilli(lastReadMs)
                                                        .atZone(ZoneId.systemDefault())
                                                        .toLocalDateTime());
                perceptionNovelTableModel.addRow(
                        new Object[] {
                            unescapeSimple(m.group(1)),
                            m.group(2),
                            chapterCount,
                            range,
                            m.group(6),
                            unescapeSimple(m.group(7)),
                            lastRead
                        });
            }
            applyPerceptionNovelFilter();
        }

        private void applyPerceptionNovelFilter() {
            if (!perceptionMissingOnly.isSelected()) {
                perceptionNovelSorter.setRowFilter(null);
                return;
            }
            perceptionNovelSorter.setRowFilter(
                    new RowFilter<>() {
                        @Override
                        public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                            Object v = entry.getValue(4);
                            try {
                                return Integer.parseInt(String.valueOf(v)) > 0;
                            } catch (Exception ex) {
                                return false;
                            }
                        }
                    });
        }

        private void sortPerceptionNovelsByMissingDesc() {
            List<javax.swing.RowSorter.SortKey> keys = new ArrayList<>();
            keys.add(new javax.swing.RowSorter.SortKey(4, javax.swing.SortOrder.DESCENDING));
            keys.add(new javax.swing.RowSorter.SortKey(2, javax.swing.SortOrder.DESCENDING));
            keys.add(new javax.swing.RowSorter.SortKey(0, javax.swing.SortOrder.ASCENDING));
            perceptionNovelSorter.setSortKeys(keys);
            perceptionNovelSorter.sort();
        }

        private void addPerceptionSource() {
            String type = canonicalPerceptionSourceType(String.valueOf(perceptionSourceTypeBox.getSelectedItem()));
            String url = perceptionSourceUrlField.getText().trim();
            String label = perceptionSourceLabelField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请填写源 URL。", "自动感知", JOptionPane.WARNING_MESSAGE);
                return;
            }
            perceptionStatus.setText("添加源中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "type="
                                            + URLEncoder.encode(type, StandardCharsets.UTF_8)
                                            + "&url="
                                            + URLEncoder.encode(url, StandardCharsets.UTF_8)
                                            + "&label="
                                            + URLEncoder.encode(label, StandardCharsets.UTF_8);
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/source/add", form);
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                perceptionStatus.setText("已新增源");
                                refreshPerceptionSources();
                            } catch (Exception ex) {
                                perceptionStatus.setText("新增失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void batchAddPerceptionSourcesFromBookUrl() {
            String bookUrl = perceptionSourceUrlField.getText().trim();
            String labelPrefix = perceptionSourceLabelField.getText().trim();
            String limitRaw = perceptionBatchLimitField.getText().trim();
            if (bookUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先在 URL 填写书页地址。", "自动感知", JOptionPane.WARNING_MESSAGE);
                return;
            }
            perceptionStatus.setText("批量提取章节中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "bookUrl="
                                            + URLEncoder.encode(bookUrl, StandardCharsets.UTF_8)
                                            + "&limit="
                                            + URLEncoder.encode(limitRaw.isEmpty() ? "12" : limitRaw, StandardCharsets.UTF_8)
                                            + "&labelPrefix="
                                            + URLEncoder.encode(labelPrefix, StandardCharsets.UTF_8);
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/source/batch-add", form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                perceptionStatus.setText("批量添加完成: " + j);
                                refreshPerceptionSources();
                            } catch (Exception ex) {
                                perceptionStatus.setText("批量添加失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void previewPerceptionSourcesFromBookUrl() {
            String bookUrl = perceptionSourceUrlField.getText().trim();
            String limitRaw = perceptionBatchLimitField.getText().trim();
            if (bookUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先在 URL 填写书页地址。", "自动感知", JOptionPane.WARNING_MESSAGE);
                return;
            }
            perceptionStatus.setText("预览章节提取中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form =
                                    "bookUrl="
                                            + URLEncoder.encode(bookUrl, StandardCharsets.UTF_8)
                                            + "&limit="
                                            + URLEncoder.encode(limitRaw.isEmpty() ? "12" : limitRaw, StandardCharsets.UTF_8);
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/source/batch-preview", form);
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                perceptionSourcesArea.setText(formatPerceptionBatchPreview(json));
                                perceptionSourcesArea.setCaretPosition(0);
                                perceptionStatus.setText("预览完成（未写入感知源）");
                            } catch (Exception ex) {
                                perceptionStatus.setText("预览失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void removePerceptionSource() {
            String url = perceptionSourceUrlField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请在 URL 输入框填写要移除的源。", "自动感知", JOptionPane.WARNING_MESSAGE);
                return;
            }
            perceptionStatus.setText("移除源中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String form = "url=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/source/remove", form);
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                perceptionStatus.setText("已移除（若存在）");
                                refreshPerceptionSources();
                            } catch (Exception ex) {
                                perceptionStatus.setText("移除失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void runPerceptionNow() {
            perceptionStatus.setText("触发执行中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/run", "");
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                perceptionStatus.setText("已触发: " + j);
                                refreshPerceptionConfig();
                            } catch (Exception ex) {
                                perceptionStatus.setText("触发失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void restoreDefaultPerceptionSources() {
            perceptionStatus.setText("恢复默认双站中…");
            SwingWorker<Void, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            postPerceptionSourceAdd("article", DEFAULT_FANQIE_LIBRARY_URL, "fanqie_library_default");
                            postPerceptionSourceAdd("article", DEFAULT_FANQIE_READER_URL, "fanqie_reader_default");
                            postPerceptionSourceAdd("article", DEFAULT_QIDIAN_FREE_URL, "qidian_free_library_default");
                            return null;
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                perceptionStatus.setText("已恢复默认源：番茄 + 起点");
                                refreshPerceptionSources();
                                refreshPerceptionNovelSummary();
                            } catch (Exception ex) {
                                perceptionStatus.setText("恢复失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private void postPerceptionSourceAdd(String type, String url, String label) throws Exception {
            String form =
                    "type="
                            + URLEncoder.encode(type, StandardCharsets.UTF_8)
                            + "&url="
                            + URLEncoder.encode(url, StandardCharsets.UTF_8)
                            + "&label="
                            + URLEncoder.encode(label == null ? "" : label, StandardCharsets.UTF_8);
            httpPostForm("http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/perception/source/add", form);
        }

        private String canonicalPerceptionMode(String uiMode) {
            String m = uiMode == null ? "" : uiMode.trim();
            if (m.contains("仅文章")) return "article";
            if (m.contains("仅视觉")) return "video";
            if ("article".equalsIgnoreCase(m) || "video".equalsIgnoreCase(m) || "mixed".equalsIgnoreCase(m)) {
                return m.toLowerCase();
            }
            return "mixed";
        }

        private void applyPerceptionModeSelection(String mode) {
            String m = canonicalPerceptionMode(mode);
            if ("article".equals(m)) {
                perceptionModeBox.setSelectedItem("仅文章");
            } else if ("video".equals(m)) {
                perceptionModeBox.setSelectedItem("仅视觉");
            } else {
                perceptionModeBox.setSelectedItem("混合（文章+视觉）");
            }
        }

        private String canonicalPerceptionSourceType(String uiType) {
            String t = uiType == null ? "" : uiType.trim();
            if (t.contains("视觉")) return "video";
            if ("video".equalsIgnoreCase(t) || "article".equalsIgnoreCase(t)) {
                return t.toLowerCase();
            }
            return "article";
        }

        private static String formatPerceptionSources(String json) {
            if (json == null || json.isBlank()) return "";
            Pattern p =
                    Pattern.compile(
                            "\"type\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"label\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"(?:\\s*,\\s*\"novelKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\")?(?:\\s*,\\s*\"chapterId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\")?",
                            Pattern.DOTALL);
            Matcher m = p.matcher(json);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find()) {
                n++;
                sb.append(n)
                        .append(". [")
                        .append(unescapeSimple(m.group(1)))
                        .append("] ")
                        .append(unescapeSimple(m.group(2)))
                        .append("  标签=")
                        .append(unescapeSimple(m.group(3)))
                        .append("  小说=")
                        .append(unescapeSimple(m.group(4) == null ? "" : m.group(4)))
                        .append("  章节=")
                        .append(unescapeSimple(m.group(5) == null ? "" : m.group(5)))
                        .append('\n');
            }
            if (n == 0) return json;
            return sb.toString();
        }

        private static String formatPerceptionNovels(String json) {
            if (json == null || json.isBlank()) return "";
            int novelCount = firstJsonInt(json, "novelCount", 0);
            StringBuilder sb = new StringBuilder();
            sb.append("同书聚合: ").append(novelCount).append(" 本\n");
            Pattern p =
                    Pattern.compile(
                            "\"novelKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"sourceCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterMin\"\\s*:\\s*(\\d+)\\s*,\\s*\"chapterMax\"\\s*:\\s*(\\d+)\\s*,\\s*\"missingChapterCount\"\\s*:\\s*(\\d+)\\s*,\\s*\"missingChapterPreview\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"lastReadMs\"\\s*:\\s*(\\d+)",
                            Pattern.DOTALL);
            Matcher m = p.matcher(json);
            int i = 0;
            while (m.find()) {
                i++;
                long lastReadMs;
                try {
                    lastReadMs = Long.parseLong(m.group(8));
                } catch (Exception ex) {
                    lastReadMs = 0L;
                }
                String lastRead =
                        lastReadMs <= 0
                                ? "-"
                                : DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                                        .format(
                                                Instant.ofEpochMilli(lastReadMs)
                                                        .atZone(ZoneId.systemDefault())
                                                        .toLocalDateTime());
                sb.append(i)
                        .append(". novel=")
                        .append(unescapeSimple(m.group(1)))
                        .append("  src=")
                        .append(m.group(2))
                        .append("  chapters=")
                        .append(m.group(3));
                if (!"0".equals(m.group(3))) {
                    sb.append("  range=").append(m.group(4)).append("..").append(m.group(5));
                }
                sb.append('\n')
                        .append("   missing=")
                        .append(m.group(6));
                String miss = unescapeSimple(m.group(7));
                if (!miss.isBlank()) sb.append(" [").append(miss).append(']');
                sb.append('\n').append("   lastRead=").append(lastRead).append('\n');
            }
            if (i == 0) {
                sb.append("（暂无可聚合的小说源）");
            }
            return sb.toString();
        }

        private static String formatPerceptionBatchPreview(String json) {
            if (json == null || json.isBlank()) return "";
            StringBuilder sb = new StringBuilder();
            String bookUrl = firstJsonString(json, "bookUrl");
            int count = firstJsonInt(json, "count", 0);
            sb.append("书页地址=").append(bookUrl == null ? "" : bookUrl).append('\n');
            sb.append("预览数量=").append(count).append('\n');
            Pattern p = Pattern.compile("\"items\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            if (!m.find()) return sb.toString();
            String arr = m.group(1);
            Matcher item = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(arr);
            int i = 0;
            while (item.find()) {
                i++;
                sb.append(i).append(". ").append(unescapeSimple(item.group(1))).append('\n');
            }
            return sb.toString();
        }

        private void refreshImportList() {
            importStatus.setText("刷新中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/import/list");
                        }

                        @Override
                        protected void done() {
                            try {
                                String json = get();
                                importList.setText(formatImportList(json));
                                importList.setCaretPosition(0);
                                importStatus.setText("已加载（原始 JSON 条目见上）");
                            } catch (Exception ex) {
                                importStatus.setText("列表失败: " + ex.getMessage());
                            }
                        }
                    };
            w.execute();
        }

        private static String formatImportList(String json) {
            if (json == null || json.isBlank()) return "";
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile(
                                    "\"id\"\\s*:\\s*\"([^\"]+)\".*?\"fileName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\".*?\"chars\"\\s*:\\s*([0-9]+)",
                                    java.util.regex.Pattern.DOTALL)
                            .matcher(json);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find()) {
                n++;
                sb.append(n)
                        .append(". id=")
                        .append(m.group(1))
                        .append("  ")
                        .append(unescapeSimple(m.group(2)))
                        .append("  (")
                        .append(m.group(3))
                        .append(" 字)\n");
            }
            if (n == 0) {
                return json;
            }
            return sb.toString();
        }

        private void runLiteraryDistill(String mode) {
            String id = importDistillId.getText().trim();
            if (id.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "请先在列表中复制某条的 id，填入「条目 id」。",
                        "蒸馏",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            importStatus.setText("蒸馏中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            String q =
                                    "id="
                                            + URLEncoder.encode(id, StandardCharsets.UTF_8)
                                            + "&mode="
                                            + URLEncoder.encode(mode, StandardCharsets.UTF_8);
                            return httpPostRaw(
                                    "http://127.0.0.1:"
                                            + LivingAI.HTTP_PORT
                                            + "/api/import/distill?"
                                            + q,
                                    new byte[0],
                                    null);
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                importStatus.setText(j);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        j,
                                        "文学蒸馏",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage(),
                                        "蒸馏失败",
                                        JOptionPane.ERROR_MESSAGE);
                                importStatus.setText("蒸馏失败");
                            }
                        }
                    };
            w.execute();
        }

        private static String unescapeSimple(String s) {
            return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private void importFiles(boolean zip) {
            JFileChooser ch = new JFileChooser();
            ch.setMultiSelectionEnabled(!zip);
            int r = zip ? ch.showOpenDialog(this) : ch.showOpenDialog(this);
            if (r != JFileChooser.APPROVE_OPTION) return;
            if (zip) {
                Path p = ch.getSelectedFile().toPath();
                importStatus.setText("上传 ZIP…");
                SwingWorker<String, Void> w =
                        new SwingWorker<>() {
                            @Override
                            protected String doInBackground() throws Exception {
                                byte[] raw = Files.readAllBytes(p);
                                return httpPostRaw(
                                        "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/import/zip",
                                        raw,
                                        null);
                            }

                            @Override
                            protected void done() {
                                try {
                                    String j = get();
                                    importStatus.setText(j);
                                    refreshImportList();
                                    JOptionPane.showMessageDialog(LifeformFrame.this, j, "导入 ZIP", JOptionPane.INFORMATION_MESSAGE);
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(LifeformFrame.this, ex.getMessage(), "导入失败", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        };
                w.execute();
                return;
            }
            File[] selected = ch.getSelectedFiles();
            final File[] files;
            if (selected.length == 0 && ch.getSelectedFile() != null) {
                files = new File[] {ch.getSelectedFile()};
            } else {
                files = selected;
            }
            if (files.length == 0) {
                return;
            }
            importStatus.setText("上传中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            StringBuilder acc = new StringBuilder();
                            for (File f : files) {
                                byte[] raw = Files.readAllBytes(f.toPath());
                                String resp =
                                        httpPostRaw(
                                                "http://127.0.0.1:"
                                                        + LivingAI.HTTP_PORT
                                                        + "/api/import/file",
                                                raw,
                                                f.getName());
                                acc.append(f.getName()).append(": ").append(resp).append("\n");
                            }
                            return acc.toString();
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                importStatus.setText(j);
                                refreshImportList();
                                JOptionPane.showMessageDialog(LifeformFrame.this, j, "导入完成", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(LifeformFrame.this, ex.getMessage(), "导入失败", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void clearImportLibrary() {
            int ok =
                    JOptionPane.showConfirmDialog(
                            this,
                            "确定清空所有导入的文本索引？（不可恢复）",
                            "确认",
                            JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            importStatus.setText("清空中…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpPostRaw(
                                    "http://127.0.0.1:" + LivingAI.HTTP_PORT + "/api/import/clear",
                                    new byte[0],
                                    null);
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                importStatus.setText("已清空");
                                refreshImportList();
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(LifeformFrame.this, ex.getMessage(), "清空失败", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private static String httpPostRaw(String url, byte[] body, String filename) throws Exception {
            URI uri = URI.create(url);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(300_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            if (filename != null) {
                c.setRequestProperty(
                        "X-Filename", URLEncoder.encode(filename, StandardCharsets.UTF_8));
            }
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString().trim();
            } finally {
                c.disconnect();
            }
        }

        private void runLocalAi() {
            String prompt = aiPrompt.getText();
            if (prompt.trim().isEmpty()) {
                aiOut.setText("提示词为空");
                return;
            }
            invokeAuxiliaryGenerate(
                    prompt, "【提示词生成】\n" + prompt, auxBrainSessionLog, aiOut);
        }

        private static String firstJsonString(String json, String key) {
            Matcher m = JSON_STR.matcher(json);
            while (m.find()) {
                if (key.equals(m.group(1))) {
                    return unescapeJson(m.group(2));
                }
            }
            return null;
        }

        private static int firstJsonInt(String json, String key, int fallback) {
            if (json == null || json.isBlank() || key == null || key.isBlank()) return fallback;
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
            Matcher m = p.matcher(json);
            if (!m.find()) return fallback;
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static long firstJsonLong(String json, String key, long fallback) {
            if (json == null || json.isBlank() || key == null || key.isBlank()) return fallback;
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
            Matcher m = p.matcher(json);
            if (!m.find()) return fallback;
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static double firstJsonDouble(String json, String key, double fallback) {
            if (json == null || json.isBlank() || key == null || key.isBlank()) return fallback;
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
            Matcher m = p.matcher(json);
            if (!m.find()) return fallback;
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static boolean firstJsonBool(String json, String key, boolean fallback) {
            if (json == null || json.isBlank() || key == null || key.isBlank()) return fallback;
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (!m.find()) return fallback;
            return "true".equalsIgnoreCase(m.group(1));
        }

        private static String humanBytes(long bytes) {
            if (bytes <= 0L) return "0B";
            String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
            double v = bytes;
            int i = 0;
            while (v >= 1024.0 && i < units.length - 1) {
                v /= 1024.0;
                i++;
            }
            return String.format(java.util.Locale.US, "%.2f%s", v, units[i]);
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

        private void loadDevicePushPropertiesIntoUi() {
            Path p = Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "device-push.properties");
            if (!Files.isRegularFile(p)) {
                uiDeviceEnabled.setSelected(false);
                uiDeviceToken.setText("");
                uiDeviceListenLan.setSelected(false);
                uiDeviceListenPort.setText("8787");
                uiDeviceAllowedIds.setText("meizu6");
                return;
            }
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(p)) {
                    props.load(in);
                }
                uiDeviceEnabled.setSelected(Boolean.parseBoolean(props.getProperty("enabled", "false")));
                uiDeviceToken.setText(props.getProperty("pushToken", ""));
                uiDeviceListenLan.setSelected(Boolean.parseBoolean(props.getProperty("listenLan", "false")));
                uiDeviceListenPort.setText(props.getProperty("listenPort", "8787"));
                uiDeviceAllowedIds.setText(props.getProperty("allowedDeviceIds", "meizu6"));
            } catch (Exception ex) {
                uiDeviceStatus.setText("读取配置失败: " + ex.getMessage());
            }
        }

        private void saveDevicePushPropertiesFromUi() {
            Path p = Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "device-push.properties");
            try {
                Files.createDirectories(p.getParent());
                Properties props = new Properties();
                props.setProperty("enabled", Boolean.toString(uiDeviceEnabled.isSelected()));
                props.setProperty("pushToken", uiDeviceToken.getText().trim());
                props.setProperty("listenLan", Boolean.toString(uiDeviceListenLan.isSelected()));
                String port = uiDeviceListenPort.getText().trim();
                props.setProperty("listenPort", port.isEmpty() ? "8787" : port);
                props.setProperty("allowedDeviceIds", uiDeviceAllowedIds.getText().trim());
                try (OutputStream os = Files.newOutputStream(p)) {
                    props.store(os, "LovingAI device push (GUI)");
                }
                JOptionPane.showMessageDialog(
                        this,
                        "已保存到 data/perception/device-push.properties。\n若更改了 listenLan 或端口，请重启 LovingAI 进程。",
                        "设备投喂",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }

        private Path usbCompanionPropertiesPath() {
            return Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "usb-companion.properties");
        }

        private Path usbCompanionCachedApkPath() {
            return Paths.get(
                    com.lovingai.LivingAI.BASE_DIR,
                    "perception",
                    "companion-cache",
                    "lovingai-usb-companion.apk");
        }

        private void loadUsbCompanionPropertiesIntoUi() {
            Path p = usbCompanionPropertiesPath();
            if (!Files.isRegularFile(p)) {
                uiUsbCompanionUrl.setText("");
                return;
            }
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(p)) {
                    props.load(in);
                }
                uiUsbCompanionUrl.setText(props.getProperty("companionApkUrl", ""));
            } catch (Exception ex) {
                appendUsbLog("读取 usb-companion.properties 失败: " + ex.getMessage());
            }
        }

        private void saveUsbCompanionPropertiesFromUi() {
            Path p = usbCompanionPropertiesPath();
            try {
                Files.createDirectories(p.getParent());
                Properties props = new Properties();
                props.setProperty("companionApkUrl", uiUsbCompanionUrl.getText().trim());
                try (OutputStream os = Files.newOutputStream(p)) {
                    props.store(os, "USB companion APK download URL (PC only; phone is thin client)");
                }
                JOptionPane.showMessageDialog(
                        this,
                        "已保存到 data/perception/usb-companion.properties",
                        "伴侣下载地址",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** 从直链下载伴侣 APK 到 PC；可选随后 adb install。手机端仍为轻量采集，对话与推理在 PC。 */
        private void downloadCompanionApkToCache(boolean installAfter) {
            String urlStr = uiUsbCompanionUrl.getText().trim();
            if (urlStr.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this, "请先填写「伴侣 APK 下载地址」", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            final URI uri;
            try {
                uri = URI.create(urlStr);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "URL 格式无效", "提示", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Path dest = usbCompanionCachedApkPath();
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            Files.createDirectories(dest.getParent());
                            long bytes =
                                    HttpFileDownload.downloadHttpToFile(uri, dest, 20_000, 600_000);
                            String head =
                                    "已下载 "
                                            + bytes
                                            + " 字节 → "
                                            + dest.toAbsolutePath()
                                            + "\n";
                            if (!installAfter) {
                                return head;
                            }
                            String adbOut =
                                    AdbUsbBridge.install(
                                            uiUsbAdbExe.getText().trim(), dest.toAbsolutePath().toString());
                            return head + "=== adb install ===\n" + adbOut;
                        }

                        @Override
                        protected void done() {
                            try {
                                String out = get();
                                appendUsbLog(
                                        "=== "
                                                + (installAfter ? "下载并安装伴侣 APK" : "一键下载伴侣 APK")
                                                + " ===\n"
                                                + out);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        out.length() > 800 ? out.substring(0, 800) + "…" : out,
                                        installAfter ? "下载并安装" : "下载完成",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                appendUsbLog(
                                        "=== 伴侣 APK 下载失败 ===\n"
                                                + (ex.getMessage() != null ? ex.getMessage() : ex));
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex),
                                        "失败",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void refreshDevicePushStatusFromServer() {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return httpGet(
                                    "http://127.0.0.1:"
                                            + com.lovingai.LivingAI.HTTP_PORT
                                            + "/api/perception/device/status");
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                uiDeviceStatus.setText(j);
                                uiDeviceStatus.setCaretPosition(0);
                            } catch (Exception ignored) {
                            }
                        }
                    };
            w.execute();
        }

        private void updateDeviceLanHintLabel() {
            String port = uiDeviceListenPort.getText().trim();
            if (port.isEmpty()) {
                port = "8787";
            }
            StringBuilder plain = new StringBuilder("手机 POST（需 token）：");
            try {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                int n = 0;
                while (en.hasMoreElements()) {
                    NetworkInterface ni = en.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) {
                        continue;
                    }
                    Enumeration<InetAddress> ad = ni.getInetAddresses();
                    while (ad.hasMoreElements()) {
                        InetAddress a = ad.nextElement();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                            plain.append(" http://")
                                    .append(a.getHostAddress())
                                    .append(":")
                                    .append(port)
                                    .append("/api/perception/device/push");
                            n++;
                            if (n >= 5) {
                                break;
                            }
                        }
                    }
                    if (n >= 5) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            uiDeviceLanHint.setText(
                    "<html><span style='color:#8aa0b8;'>" + plain + "</span></html>");
        }

        private void appendUsbLog(String s) {
            SwingUtilities.invokeLater(
                    () -> {
                        uiUsbLog.append(s);
                        uiUsbLog.append("\n");
                        uiUsbLog.setCaretPosition(uiUsbLog.getDocument().getLength());
                    });
        }

        private void runUsbAdbTask(String title, Callable<String> action) {
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return action.call();
                        }

                        @Override
                        protected void done() {
                            try {
                                appendUsbLog("=== " + title + " ===\n" + get());
                            } catch (Exception ex) {
                                appendUsbLog(
                                        "=== "
                                                + title
                                                + " 失败 ===\n"
                                                + (ex.getMessage() != null ? ex.getMessage() : ex));
                            }
                        }
                    };
            w.execute();
        }

        private void chooseApkAndInstallViaAdb() {
            JFileChooser ch = new JFileChooser();
            ch.setFileFilter(new FileNameExtensionFilter("Android APK (*.apk)", "apk"));
            if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File apk = ch.getSelectedFile();
            if (apk == null) {
                return;
            }
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return AdbUsbBridge.install(
                                    uiUsbAdbExe.getText().trim(), apk.getAbsolutePath());
                        }

                        @Override
                        protected void done() {
                            try {
                                String out = get();
                                appendUsbLog("=== adb install ===\n" + out);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, out, "adb install", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex),
                                        "安装失败",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void pullUsbRemoteToInbox() {
            String remote = uiUsbRemoteFile.getText().trim();
            if (remote.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this, "请填写手机端导出文件路径", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Path inboxDir = Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "usb-inbox");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            Files.createDirectories(inboxDir);
                            String name = remote;
                            int slash = name.lastIndexOf('/');
                            if (slash >= 0 && slash + 1 < name.length()) {
                                name = name.substring(slash + 1);
                            }
                            if (name.isBlank()) {
                                name = "phone-export.txt";
                            }
                            Path dest = inboxDir.resolve(name);
                            return AdbUsbBridge.pull(
                                    uiUsbAdbExe.getText().trim(),
                                    remote,
                                    dest.toAbsolutePath().toString());
                        }

                        @Override
                        protected void done() {
                            try {
                                String out = get();
                                appendUsbLog("=== adb pull ===\n" + out);
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, out, "adb pull", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex),
                                        "pull 失败",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private void importUsbInboxToCorpus() {
            Path dir = Paths.get(com.lovingai.LivingAI.BASE_DIR, "perception", "usb-inbox");
            if (!Files.isDirectory(dir)) {
                JOptionPane.showMessageDialog(this, "目录不存在: " + dir, "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File[] list =
                    dir.toFile()
                            .listFiles(
                                    (d, n) -> {
                                        String x = n.toLowerCase();
                                        return x.endsWith(".txt")
                                                || x.endsWith(".md")
                                                || x.endsWith(".ndjson");
                                    });
            if (list == null || list.length == 0) {
                JOptionPane.showMessageDialog(
                        this,
                        "usb-inbox 下暂无 .txt / .md / .ndjson",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            importStatus.setText("从 USB 收件箱导入…");
            SwingWorker<String, Void> w =
                    new SwingWorker<>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            StringBuilder acc = new StringBuilder();
                            for (File f : list) {
                                byte[] raw = Files.readAllBytes(f.toPath());
                                String safe =
                                        "usb-inbox-"
                                                + f.getName()
                                                        .replaceAll(
                                                                "[^a-zA-Z0-9._-]", "_");
                                String resp =
                                        httpPostRaw(
                                                "http://127.0.0.1:"
                                                        + com.lovingai.LivingAI.HTTP_PORT
                                                        + "/api/import/file",
                                                raw,
                                                safe);
                                acc.append(f.getName()).append(": ").append(resp).append("\n");
                            }
                            return acc.toString();
                        }

                        @Override
                        protected void done() {
                            try {
                                String j = get();
                                appendUsbLog("=== 导入资料库 ===\n" + j);
                                importStatus.setText("USB 导入完成");
                                refreshImportList();
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this, j, "导入完成", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(
                                        LifeformFrame.this,
                                        ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex),
                                        "导入失败",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    };
            w.execute();
        }

        private static String httpGet(String url) throws Exception {
            URI uri = URI.create(url);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(120_000);
            c.setRequestMethod("GET");
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

        private static String httpPostForm(String url, String form) throws Exception {
            return httpPostForm(url, form, 300_000);
        }

        private static String httpPostForm(String url, String form, int readTimeoutMs) throws Exception {
            URI uri = URI.create(url);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            byte[] raw = form.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(raw.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(raw);
            }
            try {
                return readUrlConnectionText(c);
            } finally {
                c.disconnect();
            }
        }

        /** 先取状态码，4xx/5xx 优先读 errorStream，减少误判与 EOF。 */
        private static String readUrlConnectionText(HttpURLConnection c) throws IOException {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) {
                in = code >= 400 ? c.getInputStream() : c.getErrorStream();
            }
            if (in == null) {
                return "";
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString().trim();
            }
        }

        private static String httpPostPlain(String url, String body) throws Exception {
            URI uri = URI.create(url);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(60_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(raw.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(raw);
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString().trim();
            } finally {
                c.disconnect();
            }
        }
    }
}
