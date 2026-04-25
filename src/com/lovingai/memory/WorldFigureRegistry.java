package com.lovingai.memory;

import com.lovingai.log.CognitionLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 世界人物与关系草图：非破坏性累加，供对话主位与圆抽样理解「谁与谁」；启发式从用户话中抽取，可人工纠偏（见落盘文件）。
 */
public final class WorldFigureRegistry {

    /** 与 {@link com.lovingai.LivingAI#BASE_DIR} 一致，工作目录相对路径。 */
    private static final Path PATH = Paths.get(com.lovingai.LivingAI.BASE_DIR, "memory", "world-figures.tsv");
    private static final Path PENDING_PATH =
            Paths.get(com.lovingai.LivingAI.BASE_DIR, "memory", "world-figures-pending.tsv");
    private static final int MAX_NODES = 220;
    private static final int MAX_RELATIONS = 520;
    private static final String HOST_ID = "宿主";

    private final ConcurrentHashMap<String, FigureNode> nodes = new ConcurrentHashMap<>();
    private final List<FigureRelation> relations = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, PendingCandidate> pending = new ConcurrentHashMap<>();

    private record PendingCandidate(String displayName, String noteSeed, int count, long firstMs, long lastMs) {}

    public static final class FigureNode {
        public final String id;
        public volatile String displayName;
        /** 逗号分隔别名，用于命中用户话。 */
        public volatile String aliases;
        public volatile String roleHint;
        public volatile String note;
        public volatile long updatedMs;

        public FigureNode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = "";
            this.roleHint = "";
            this.note = "";
            this.updatedMs = System.currentTimeMillis();
        }
    }

    public static final class FigureRelation {
        public final String fromId;
        public final String toId;
        public volatile String label;
        public volatile double confidence;
        public volatile long updatedMs;

        public FigureRelation(String fromId, String toId, String label, double confidence) {
            this.fromId = fromId;
            this.toId = toId;
            this.label = label;
            this.confidence = confidence;
            this.updatedMs = System.currentTimeMillis();
        }
    }

    private static final Pattern P_IS_OF =
            Pattern.compile(
                    "(.{1,14}?)是(.{1,14}?)的(父亲|母亲|爸爸|妈妈|儿子|女儿|孩子|朋友|同事|老师|学生|丈夫|妻子|兄弟|姐妹|恋人|搭档|上司|下属)");
    private static final Pattern P_AND_ARE =
            Pattern.compile("(.{1,14}?)和(.{1,14}?)是(朋友|夫妻|兄弟|姐妹|同事|搭档|恋人)");
    private static final Pattern P_I_AM =
            Pattern.compile(
                    "(?:我(?:叫|是)|(?i:my\\s+name\\s+is|i\\s+am)\\s+)([^，。！？\\s\"\\n]{1,14})");
    private static final Pattern P_QUOTED = Pattern.compile("「([^」]{1,12})」");
    private static final Pattern P_PRONOUN_KIN =
            Pattern.compile(
                    "(我|你|他|她|我们|你们|他们|她们)的?(爸爸|妈妈|父亲|母亲|爷爷|奶奶|外公|外婆|姥爷|姥姥|哥哥|姐姐|弟弟|妹妹|儿子|女儿|丈夫|妻子|老公|老婆|叔叔|阿姨|舅舅|姑姑|伯伯|婶婶|侄子|侄女|外甥|外甥女)");

    public record ExtractionStats(
            int candidateSpansTotal,
            int droppedNoiseSpan,
            int droppedEmptySpan,
            int acceptedSpan,
            int pendingSpan,
            int promotedNodes,
            int upsertedNodesNew,
            int relationsNew,
            int relationsStrengthened,
            int inferredRelationsNew,
            int suspiciousAcceptedSpan) {}

    private static final int PENDING_PROMOTE_THRESHOLD = 3;

    private record UpsertDecision(
            UpsertNodeResult result, boolean pendingAdded, boolean promoted, boolean suspiciousCandidate) {}

    /**
     * 报道/转述语、机构场景碎片：不作为可累加人物节点（避免「听…说起」等误入草图）。
     */
    private static boolean isNoiseFigureSpan(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) {
            return true;
        }
        String s = cleaned.trim();
        if (s.length() > 14) {
            return true;
        }
        if (s.endsWith("说起")
                || s.endsWith("说道")
                || s.endsWith("说到")
                || s.endsWith("所说")
                || s.endsWith("还称")) {
            return true;
        }
        if (s.startsWith("听") && (s.contains("说") || s.contains("讲") || s.contains("谈"))) {
            return true;
        }
        if (s.contains("说") && (s.contains("有人") || s.contains("听") || s.contains("传"))) {
            return true;
        }
        if (s.startsWith("据")
                || s.startsWith("有消息称")
                || s.startsWith("据报道")
                || s.startsWith("据悉")) {
            return true;
        }
        if (s.startsWith("有人")
                || s.startsWith("某人")
                || s.startsWith("某位")
                || s.startsWith("某个")) {
            return true;
        }
        if (s.contains("文化馆")
                || s.contains("博物馆")
                || s.contains("宣传部")
                || s.contains("记者站")
                || s.contains("新闻网")) {
            return true;
        }
        if (s.contains("媒体") || s.contains("平台") || s.contains("官网") || s.contains("公众号")) {
            return true;
        }
        return false;
    }

    private static boolean isSuspiciousAcceptedSpan(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) return true;
        String s = cleaned.trim();
        if (isNoiseFigureSpan(s)) return true;
        if (s.startsWith("某") || s.startsWith("一个") || s.startsWith("一些")) return true;
        if (s.contains("网友") || s.contains("群众") || s.contains("路人")) return true;
        if (s.contains("记者") || s.contains("媒体") || s.contains("报道")) return true;
        if (s.endsWith("先生") || s.endsWith("女士") || s.endsWith("同学")) return true;
        return false;
    }

    private static boolean figureNameAllowed(String cleanedDisplay) {
        return cleanedDisplay != null
                && !cleanedDisplay.isBlank()
                && !isNoiseFigureSpan(cleanedDisplay);
    }

    public synchronized void load() {
        nodes.clear();
        relations.clear();
        pending.clear();
        ensureHost();
        try {
            if (Files.isRegularFile(PATH)) {
                List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split("\t", -1);
                    if (p.length < 2) {
                        continue;
                    }
                    String kind = p[0].trim();
                    if ("NODE".equalsIgnoreCase(kind) && p.length >= 4) {
                        String id = p[1].trim();
                        if (id.isBlank()) {
                            continue;
                        }
                        FigureNode n = new FigureNode(id, unesc(p[2]));
                        n.aliases = p.length > 3 ? unesc(p[3]) : "";
                        n.roleHint = p.length > 4 ? unesc(p[4]) : "";
                        n.note = p.length > 5 ? unesc(p[5]) : "";
                        try {
                            n.updatedMs = p.length > 6 ? Long.parseLong(p[6].trim()) : System.currentTimeMillis();
                        } catch (Exception ignored) {
                            n.updatedMs = System.currentTimeMillis();
                        }
                        nodes.put(id, n);
                    } else if ("REL".equalsIgnoreCase(kind) && p.length >= 5) {
                        String a = p[1].trim();
                        String b = p[2].trim();
                        String label = unesc(p[3]);
                        double conf = 0.62;
                        try {
                            conf = Double.parseDouble(p[4].trim());
                        } catch (Exception ignored) {
                        }
                        long ts = System.currentTimeMillis();
                        if (p.length > 5) {
                            try {
                                ts = Long.parseLong(p[5].trim());
                            } catch (Exception ignored) {
                            }
                        }
                        FigureRelation r = new FigureRelation(a, b, label, conf);
                        r.updatedMs = ts;
                        relations.add(r);
                    }
                }
            }
            ensureHost();
            loadPending();
            CognitionLog.append(
                    "人物草图",
                    "已载入 nodes=" + nodes.size() + " rel=" + relations.size() + " pending=" + pending.size());
        } catch (Exception ex) {
            CognitionLog.append("人物草图", "读取失败: " + ex.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("# kind\\tfields…  NODE: id\\tdisplay\\taliases\\troleHint\\tnote\\tts  REL: from\\tto\\tlabel\\tconf\\tts\n");
            List<FigureNode> ns = new ArrayList<>(nodes.values());
            ns.sort(Comparator.comparing(n -> n.id));
            for (FigureNode n : ns) {
                sb.append("NODE\t")
                        .append(esc(n.id))
                        .append('\t')
                        .append(esc(n.displayName))
                        .append('\t')
                        .append(esc(n.aliases))
                        .append('\t')
                        .append(esc(n.roleHint))
                        .append('\t')
                        .append(esc(n.note))
                        .append('\t')
                        .append(n.updatedMs)
                        .append('\n');
            }
            synchronized (relations) {
                for (FigureRelation r : relations) {
                    sb.append("REL\t")
                            .append(esc(r.fromId))
                            .append('\t')
                            .append(esc(r.toId))
                            .append('\t')
                            .append(esc(r.label))
                            .append('\t')
                            .append(String.format(Locale.US, "%.3f", r.confidence))
                            .append('\t')
                            .append(r.updatedMs)
                            .append('\n');
                }
            }
            Files.writeString(
                    PATH,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            savePending();
        } catch (IOException ex) {
            CognitionLog.append("人物草图", "落盘失败: " + ex.getMessage());
        }
    }

    private void loadPending() {
        if (!Files.isRegularFile(PENDING_PATH)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(PENDING_PATH, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                if (p.length < 4) continue;
                String name = unesc(p[0]);
                if (name.isBlank() || !figureNameAllowed(name)) continue;
                int count = 1;
                try {
                    count = Integer.parseInt(p[1].trim());
                } catch (Exception ignored) {
                }
                long first = 0L;
                long last = 0L;
                try {
                    first = Long.parseLong(p[2].trim());
                } catch (Exception ignored) {
                }
                try {
                    last = Long.parseLong(p[3].trim());
                } catch (Exception ignored) {
                }
                String note = p.length > 4 ? unesc(p[4]) : "";
                pending.put(normalizeId(name), new PendingCandidate(name, note, Math.max(1, count), first, last));
            }
        } catch (Exception ignored) {
        }
    }

    private void savePending() throws IOException {
        Files.createDirectories(PENDING_PATH.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# displayName\\tcount\\tfirstMs\\tlastMs\\tnote\n");
        List<PendingCandidate> all = new ArrayList<>(pending.values());
        all.sort(Comparator.comparing(o -> o.displayName));
        for (PendingCandidate c : all) {
            sb.append(esc(c.displayName))
                    .append('\t')
                    .append(Math.max(1, c.count))
                    .append('\t')
                    .append(c.firstMs)
                    .append('\t')
                    .append(c.lastMs)
                    .append('\t')
                    .append(esc(c.noteSeed))
                    .append('\n');
        }
        Files.writeString(
                PENDING_PATH,
                sb.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /** 从一轮用户话中启发式抽取人物与关系；非破坏性合并。 */
    public ExtractionStats absorbUtterance(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ExtractionStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        String t = rawText.replace('\r', '\n');
        int candidateSpansTotal = 0;
        int droppedNoiseSpan = 0;
        int droppedEmptySpan = 0;
        int acceptedSpan = 0;
        int pendingSpan = 0;
        int promotedNodes = 0;
        int upsertedNodesNew = 0;
        int relationsNew = 0;
        int relationsStrengthened = 0;
        int suspiciousAcceptedSpan = 0;
        Matcher m1 = P_IS_OF.matcher(t);
        while (m1.find()) {
            candidateSpansTotal++;
            String a = cleanName(m1.group(1));
            String b = cleanName(m1.group(2));
            String role = m1.group(3);
            if (a.isEmpty() || b.isEmpty()) {
                droppedEmptySpan++;
                continue;
            }
            if (!figureNameAllowed(a) || !figureNameAllowed(b)) {
                droppedNoiseSpan++;
                continue;
            }
            UpsertDecision ra = upsertFigureWithPending(a, "");
            UpsertDecision rb = upsertFigureWithPending(b, "");
            if (ra.pendingAdded()) pendingSpan++;
            if (rb.pendingAdded()) pendingSpan++;
            if (ra.promoted()) promotedNodes++;
            if (rb.promoted()) promotedNodes++;
            FigureNode na = ra.result().node();
            FigureNode nb = rb.result().node();
            if (na == null || nb == null) continue;
            if (ra.suspiciousCandidate()) suspiciousAcceptedSpan++;
            if (rb.suspiciousCandidate()) suspiciousAcceptedSpan++;
            acceptedSpan++;
            if (ra.result().created()) upsertedNodesNew++;
            if (rb.result().created()) upsertedNodesNew++;
            na.updatedMs = System.currentTimeMillis();
            nb.updatedMs = System.currentTimeMillis();
            String label = relationLabelFromTemplate(a, b, role);
            RelationUpsert rr = addOrStrengthenRelationResult(na.id, nb.id, label, 0.72);
            if (rr.created()) relationsNew++;
            if (rr.strengthened()) relationsStrengthened++;
        }
        Matcher m2 = P_AND_ARE.matcher(t);
        while (m2.find()) {
            candidateSpansTotal++;
            String a = cleanName(m2.group(1));
            String b = cleanName(m2.group(2));
            String rel = m2.group(3);
            if (a.isEmpty() || b.isEmpty()) {
                droppedEmptySpan++;
                continue;
            }
            if (!figureNameAllowed(a) || !figureNameAllowed(b)) {
                droppedNoiseSpan++;
                continue;
            }
            UpsertDecision ra = upsertFigureWithPending(a, "");
            UpsertDecision rb = upsertFigureWithPending(b, "");
            if (ra.pendingAdded()) pendingSpan++;
            if (rb.pendingAdded()) pendingSpan++;
            if (ra.promoted()) promotedNodes++;
            if (rb.promoted()) promotedNodes++;
            FigureNode na = ra.result().node();
            FigureNode nb = rb.result().node();
            if (na == null || nb == null) continue;
            if (ra.suspiciousCandidate()) suspiciousAcceptedSpan++;
            if (rb.suspiciousCandidate()) suspiciousAcceptedSpan++;
            acceptedSpan++;
            if (ra.result().created()) upsertedNodesNew++;
            if (rb.result().created()) upsertedNodesNew++;
            RelationUpsert rr = addOrStrengthenRelationResult(na.id, nb.id, "互为" + rel, 0.65);
            if (rr.created()) relationsNew++;
            if (rr.strengthened()) relationsStrengthened++;
        }
        Matcher m3 = P_I_AM.matcher(t);
        if (m3.find()) {
            candidateSpansTotal++;
            String me = cleanName(m3.group(1));
            if (!me.isEmpty() && figureNameAllowed(me)) {
                FigureNode host = nodes.get(HOST_ID);
                if (host != null) {
                    host.roleHint = "会话中自称：" + shortOne(me, 40);
                    host.updatedMs = System.currentTimeMillis();
                }
                UpsertDecision r = upsertFigureWithPending(me, "自称名");
                if (r.pendingAdded()) pendingSpan++;
                if (r.promoted()) promotedNodes++;
                FigureNode n = r.result().node();
                if (n != null) {
                    if (r.suspiciousCandidate()) suspiciousAcceptedSpan++;
                    mergeAlias(n, "我");
                    acceptedSpan++;
                    if (r.result().created()) upsertedNodesNew++;
                }
            } else {
                droppedNoiseSpan++;
            }
        }
        Matcher mq = P_QUOTED.matcher(t);
        while (mq.find()) {
            candidateSpansTotal++;
            String q = cleanName(mq.group(1));
            if (q.length() >= 2 && q.length() <= 10 && figureNameAllowed(q)) {
                UpsertDecision r = upsertFigureWithPending(q, "引号提及");
                if (r.pendingAdded()) pendingSpan++;
                if (r.promoted()) promotedNodes++;
                if (r.result().node() != null) {
                    if (r.suspiciousCandidate()) suspiciousAcceptedSpan++;
                    acceptedSpan++;
                    if (r.result().created()) upsertedNodesNew++;
                }
            } else {
                droppedNoiseSpan++;
            }
        }
        Matcher mk = P_PRONOUN_KIN.matcher(t);
        while (mk.find()) {
            candidateSpansTotal++;
            String pronoun = mk.group(1);
            String kin = mk.group(2);
            FigureNode owner = resolvePronounOwner(pronoun);
            String kinRole = normalizeKinshipLabel(kin);
            String kn = owner.displayName + "的" + kin;
            if (!figureNameAllowed(kn)) {
                droppedNoiseSpan++;
                continue;
            }
            acceptedSpan++;
            UpsertNodeResult r = upsertFigureResult(kn, "亲属提及");
            FigureNode kinNode = r.node();
            if (kinNode == null) {
                continue;
            }
            if (r.created()) upsertedNodesNew++;
            kinNode.roleHint = kinRole;
            RelationUpsert rr = addOrStrengthenRelationResult(owner.id, kinNode.id, "亲属:" + kinRole, 0.70);
            if (rr.created()) relationsNew++;
            if (rr.strengthened()) relationsStrengthened++;
        }
        int inferred = inferKinshipChains();
        int inferredRelationsNew = inferred;
        if (acceptedSpan > 0 || pendingSpan > 0 || promotedNodes > 0 || inferred > 0) {
            trimIfNeeded();
            save();
            CognitionLog.append(
                    "人物草图",
                    "本轮吸收 span≈"
                            + acceptedSpan
                            + "/"
                            + candidateSpansTotal
                            + " pending≈"
                            + pendingSpan
                            + " promoted≈"
                            + promotedNodes
                            + "（noiseDrop≈"
                            + droppedNoiseSpan
                            + "） nodesNew≈"
                            + upsertedNodesNew
                            + " relNew≈"
                            + relationsNew
                            + " relUp≈"
                            + relationsStrengthened
                            + "；链推断≈"
                            + inferred);
        }
        return new ExtractionStats(
                candidateSpansTotal,
                droppedNoiseSpan,
                droppedEmptySpan,
                acceptedSpan,
                pendingSpan,
                promotedNodes,
                upsertedNodesNew,
                relationsNew,
                relationsStrengthened,
                inferredRelationsNew,
                suspiciousAcceptedSpan);
    }

    private UpsertDecision upsertFigureWithPending(String displayName, String noteSeed) {
        if (!figureNameAllowed(displayName)) {
            return new UpsertDecision(new UpsertNodeResult(null, false), false, false, true);
        }
        boolean suspicious = isSuspiciousAcceptedSpan(displayName);
        boolean strongSelfAssert = noteSeed != null && noteSeed.contains("自称");
        if (!suspicious || strongSelfAssert) {
            UpsertNodeResult r = upsertFigureResult(displayName, noteSeed);
            if (r.node() != null) {
                pending.remove(r.node().id);
            }
            return new UpsertDecision(r, false, false, suspicious);
        }
        String id = normalizeId(displayName);
        if (nodes.containsKey(id)) {
            UpsertNodeResult r = upsertFigureResult(displayName, noteSeed);
            pending.remove(id);
            return new UpsertDecision(r, false, false, true);
        }
        long now = System.currentTimeMillis();
        PendingCandidate next =
                pending.compute(
                        id,
                        (k, v) -> {
                            if (v == null) {
                                return new PendingCandidate(displayName, noteSeed == null ? "" : noteSeed, 1, now, now);
                            }
                            String note =
                                    (v.noteSeed() == null || v.noteSeed().isBlank())
                                            ? (noteSeed == null ? "" : noteSeed)
                                            : v.noteSeed();
                            return new PendingCandidate(
                                    v.displayName(), note, v.count() + 1, v.firstMs(), now);
                        });
        trimPendingIfNeeded();
        if (next != null && next.count() >= PENDING_PROMOTE_THRESHOLD) {
            UpsertNodeResult r = upsertFigureResult(next.displayName(), next.noteSeed());
            pending.remove(id);
            return new UpsertDecision(r, false, r.created(), true);
        }
        return new UpsertDecision(new UpsertNodeResult(null, false), true, false, true);
    }

    private void trimPendingIfNeeded() {
        if (pending.size() <= MAX_NODES) {
            return;
        }
        List<PendingCandidate> all = new ArrayList<>(pending.values());
        all.sort(Comparator.comparingLong(PendingCandidate::lastMs));
        int over = pending.size() - MAX_NODES;
        for (int i = 0; i < over && i < all.size(); i++) {
            pending.remove(normalizeId(all.get(i).displayName()));
        }
    }

    private static String relationLabelFromTemplate(String a, String b, String role) {
        return a + " 是 " + b + " 的「" + role + "」";
    }

    private FigureNode resolvePronounOwner(String pronoun) {
        String p = pronoun == null ? "" : pronoun.trim();
        if ("我".equals(p) || "我们".equals(p)) {
            ensureHost();
            return nodes.get(HOST_ID);
        }
        if ("你".equals(p) || "你们".equals(p)) {
            return upsertFigureResult("对方", "会话对象").node();
        }
        return upsertFigureResult("第三人" + p, "代词实体").node();
    }

    private static String normalizeKinshipLabel(String kin) {
        if (kin == null || kin.isBlank()) return "亲属";
        return switch (kin) {
            case "爸爸", "父亲" -> "父亲";
            case "妈妈", "母亲" -> "母亲";
            case "老公", "丈夫" -> "丈夫";
            case "老婆", "妻子" -> "妻子";
            case "姥爷", "外公" -> "外公";
            case "姥姥", "外婆" -> "外婆";
            default -> kin;
        };
    }

    /** 两跳亲属链推断（保守低置信）：A->B, B->C 推 A->C。 */
    private int inferKinshipChains() {
        int added = 0;
        List<FigureRelation> relCopy;
        synchronized (relations) {
            relCopy = new ArrayList<>(relations);
        }
        for (FigureRelation r1 : relCopy) {
            String k1 = kinRoleFromLabel(r1.label);
            if (k1.isBlank()) continue;
            for (FigureRelation r2 : relCopy) {
                if (!r1.toId.equals(r2.fromId)) continue;
                String k2 = kinRoleFromLabel(r2.label);
                if (k2.isBlank()) continue;
                String inferred = inferRoleByChain(k1, k2);
                if (inferred.isBlank()) continue;
                double conf = Math.min(0.68, Math.min(r1.confidence, r2.confidence) * 0.88);
                String label = "推断亲属:" + inferred + " (via " + r1.toId + ")";
                if (addOrStrengthenRelationIfChanged(r1.fromId, r2.toId, label, conf)) {
                    added++;
                }
            }
        }
        return added;
    }

    private boolean addOrStrengthenRelationIfChanged(String fromId, String toId, String label, double conf) {
        if (fromId == null || toId == null || fromId.equals(toId)) return false;
        synchronized (relations) {
            for (FigureRelation r : relations) {
                if (r.fromId.equals(fromId) && r.toId.equals(toId)) {
                    boolean changed = false;
                    if (label != null
                            && !label.isBlank()
                            && (r.label == null || !r.label.startsWith("亲属:"))
                            && !label.equals(r.label)) {
                        r.label = label;
                        changed = true;
                    }
                    if (conf > r.confidence) {
                        r.confidence = Math.min(1.0, conf);
                        changed = true;
                    }
                    if (changed) {
                        r.updatedMs = System.currentTimeMillis();
                    }
                    return changed;
                }
            }
            if (relations.size() >= MAX_RELATIONS) {
                relations.sort(Comparator.comparingLong(o -> o.updatedMs));
                relations.remove(0);
            }
            relations.add(new FigureRelation(fromId, toId, label, conf));
            return true;
        }
    }

    private static String kinRoleFromLabel(String label) {
        if (label == null || label.isBlank()) return "";
        String l = label.trim();
        if (l.startsWith("亲属:")) return l.substring("亲属:".length()).trim();
        if (l.startsWith("推断亲属:")) {
            int s = "推断亲属:".length();
            int e = l.indexOf(' ', s);
            return (e > s ? l.substring(s, e) : l.substring(s)).trim();
        }
        return "";
    }

    private static String inferRoleByChain(String k1, String k2) {
        String a = normalizeKinshipLabel(k1);
        String b = normalizeKinshipLabel(k2);
        if ("父亲".equals(a) && "父亲".equals(b)) return "祖父";
        if ("父亲".equals(a) && "母亲".equals(b)) return "祖母";
        if ("母亲".equals(a) && "父亲".equals(b)) return "外祖父";
        if ("母亲".equals(a) && "母亲".equals(b)) return "外祖母";
        if ("丈夫".equals(a) && "父亲".equals(b)) return "公公";
        if ("丈夫".equals(a) && "母亲".equals(b)) return "婆婆";
        if ("妻子".equals(a) && "父亲".equals(b)) return "岳父";
        if ("妻子".equals(a) && "母亲".equals(b)) return "岳母";
        return "";
    }

    private record RelationUpsert(boolean created, boolean strengthened) {}

    private RelationUpsert addOrStrengthenRelationResult(
            String fromId, String toId, String label, double conf) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            return new RelationUpsert(false, false);
        }
        synchronized (relations) {
            for (FigureRelation r : relations) {
                if (r.fromId.equals(fromId) && r.toId.equals(toId)) {
                    boolean strengthened = false;
                    if (label != null && !label.isBlank() && (r.label == null || !label.equals(r.label))) {
                        r.label = label;
                        strengthened = true;
                    }
                    double next = Math.min(1.0, Math.max(r.confidence, conf));
                    if (next > r.confidence) {
                        r.confidence = next;
                        strengthened = true;
                    }
                    if (strengthened) {
                        r.updatedMs = System.currentTimeMillis();
                    }
                    return new RelationUpsert(false, strengthened);
                }
            }
            if (relations.size() >= MAX_RELATIONS) {
                relations.sort(Comparator.comparingLong(o -> o.updatedMs));
                relations.remove(0);
            }
            relations.add(new FigureRelation(fromId, toId, label, conf));
            return new RelationUpsert(true, false);
        }
    }

    private record UpsertNodeResult(FigureNode node, boolean created) {}

    private UpsertNodeResult upsertFigureResult(String displayName, String noteSeed) {
        if (!figureNameAllowed(displayName)) {
            return new UpsertNodeResult(null, false);
        }
        String id = normalizeId(displayName);
        boolean existed = nodes.containsKey(id);
        FigureNode node =
                nodes.compute(
                id,
                (k, v) -> {
                    if (v == null) {
                        FigureNode n = new FigureNode(k, displayName);
                        if (noteSeed != null && !noteSeed.isBlank()) {
                            n.note = noteSeed;
                        }
                        return n;
                    }
                    if (noteSeed != null && !noteSeed.isBlank() && (v.note == null || v.note.isBlank())) {
                        v.note = noteSeed;
                    }
                    v.displayName = displayName;
                    v.updatedMs = System.currentTimeMillis();
                    return v;
                });
        return new UpsertNodeResult(node, !existed && node != null);
    }

    private void mergeAlias(FigureNode n, String alias) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        LinkedHashSet<String> s = new LinkedHashSet<>();
        if (n.aliases != null && !n.aliases.isBlank()) {
            for (String x : n.aliases.split("[,，、]")) {
                if (!x.isBlank()) {
                    s.add(x.trim());
                }
            }
        }
        s.add(alias.trim());
        n.aliases = String.join("，", s);
    }

    private void ensureHost() {
        nodes.computeIfAbsent(
                HOST_ID,
                k -> {
                    FigureNode h = new FigureNode(HOST_ID, "宿主（对话者）");
                    h.note = "与生命体对话的人类一侧；关系边的默认参照。";
                    return h;
                });
    }

    private void trimIfNeeded() {
        if (nodes.size() <= MAX_NODES) {
            return;
        }
        List<FigureNode> all = new ArrayList<>(nodes.values());
        all.removeIf(n -> HOST_ID.equals(n.id));
        all.sort(Comparator.comparingLong(n -> n.updatedMs));
        int over = nodes.size() - MAX_NODES;
        for (int i = 0; i < over && i < all.size(); i++) {
            nodes.remove(all.get(i).id);
        }
        ensureHost();
    }

    /** 供主位/浓缩：与用户话相关的人物与边，短行列表。 */
    public String renderBriefForPrompt(String userMessage, int maxLines) {
        ensureHost();
        String msg = userMessage == null ? "" : userMessage;
        Set<String> hitIds = new LinkedHashSet<>();
        for (FigureNode n : nodes.values()) {
            if (msg.contains(n.displayName)) {
                hitIds.add(n.id);
            }
            if (n.aliases != null && !n.aliases.isBlank()) {
                for (String a : n.aliases.split("[,，、]")) {
                    String tt = a.trim();
                    if (tt.length() >= 1 && msg.contains(tt)) {
                        hitIds.add(n.id);
                    }
                }
            }
        }
        int cap = Math.max(5, maxLines);
        List<String> lines = new ArrayList<>();
        if (!hitIds.isEmpty()) {
            for (String id : hitIds) {
                if (lines.size() >= cap) {
                    break;
                }
                FigureNode n = nodes.get(id);
                if (n == null) {
                    continue;
                }
                lines.add(
                        "· 人物「"
                                + n.displayName
                                + "」"
                                + (n.roleHint.isBlank() ? "" : "（" + shortOne(n.roleHint, 36) + "）")
                                + (n.note.isBlank() ? "" : " — " + shortOne(n.note, 48)));
            }
        }
        synchronized (relations) {
            List<FigureRelation> relCopy = new ArrayList<>(relations);
            relCopy.sort(Comparator.comparingLong(o -> -o.updatedMs));
            int relPreview = 0;
            for (FigureRelation r : relCopy) {
                if (lines.size() >= cap) {
                    break;
                }
                if (!hitIds.isEmpty()) {
                    if (!hitIds.contains(r.fromId) && !hitIds.contains(r.toId)) {
                        continue;
                    }
                } else {
                    if (relPreview >= 4) {
                        break;
                    }
                    relPreview++;
                }
                FigureNode fa = nodes.get(r.fromId);
                FigureNode fb = nodes.get(r.toId);
                String na = fa != null ? fa.displayName : r.fromId;
                String nb = fb != null ? fb.displayName : r.toId;
                lines.add(
                        "· 关系："
                                + na
                                + " → "
                                + nb
                                + " ： "
                                + shortOne(r.label, 80)
                                + " (conf≈"
                                + String.format(Locale.US, "%.2f", r.confidence)
                                + ")");
            }
        }
        if (lines.isEmpty()) {
            List<FigureNode> all = new ArrayList<>(nodes.values());
            all.sort(Comparator.comparingLong(n -> -n.updatedMs));
            int show = Math.min(Math.min(4, cap), all.size());
            for (int i = 0; i < show; i++) {
                FigureNode n = all.get(i);
                lines.add(
                        "· 人物「"
                                + n.displayName
                                + "」"
                                + (n.note.isBlank() ? "" : " — " + shortOne(n.note, 40)));
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【人物与世界·关系草图】（启发式累加，可错可改；用于理解谁在场）\n");
        for (String ln : lines) {
            if (sb.length() > 2800) {
                break;
            }
            sb.append(ln).append('\n');
        }
        return sb.toString().trim();
    }

    public String statsLine() {
        return "nodes=" + nodes.size() + " rel=" + relations.size();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int relationCount() {
        synchronized (relations) {
            return relations.size();
        }
    }

    private static String normalizeId(String displayName) {
        String t = cleanName(displayName);
        if (t.isEmpty()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(t.length(), 18); i++) {
            char ch = t.charAt(i);
            if (Character.isLetterOrDigit(ch) || (ch >= 0x4E00 && ch <= 0x9FFF)) {
                sb.append(ch);
            }
        }
        String id = sb.toString();
        if (id.isEmpty()) {
            return "id_" + Integer.toHexString(t.hashCode());
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String s) {
        if (s == null) {
            return "";
        }
        String t =
                s.replace('\u300c', ' ')
                        .replace('\u300d', ' ')
                        .replace('\u201c', ' ')
                        .replace('\u201d', ' ')
                        .replace(" ", "")
                        .trim();
        if (t.length() > 14) {
            t = t.substring(0, 14);
        }
        return t;
    }

    private static String shortOne(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String unesc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
