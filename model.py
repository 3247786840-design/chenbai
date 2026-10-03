# -*- coding: utf-8 -*-
"""
微负压闭环气动系统 —— 数学模型与故障场景仿真
=================================================
物理原型：三段式气路（罩杯 - 泄压阀 - 传感器 - 缓冲瓶 - 常开电磁阀 - 真空泵）
安全目标：正常 15-20 mmHg；任何单点故障下 |p| <= 25 mmHg（物理保底）

模型要点（详见 微负压系统数学建模.md）：
  * 等温集总腔室方程    V·dp/dt = P_atm·Q   （p 为表压，负=负压，Q 流入为正）
  * 泄漏 / 孔口         Q = K·sqrt(Δp)
  * 机械泄压阀          起跳 p_crack，全流量背压 = (Q_p/K_r)^2  ★钳位公式
  * 真空泵              恒流 + 极限真空线性降额（p_ult = -60 kPa）
  * 四层安全防线        滞环/PI闭环 -> 看门狗截断 -> 断电常开泄压 -> 机械泄压

运行：  python model.py     （输出 figs/*.png 与指标表）
"""

import os
import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

# ----------------------------------------------------------------------------
# 全局常量与字体
# ----------------------------------------------------------------------------
MMHG = 133.322                 # 1 mmHg [Pa]
P_ATM = 101325.0               # 大气压 [Pa]
P_ULT = -60000.0               # 泵极限真空（表压）[Pa]
FIG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figs")

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "sans-serif"]
plt.rcParams["axes.unicode_minus"] = False
plt.rcParams["figure.dpi"] = 110


def _stamp(fig, name):
    """在图内嵌入文件名角标，便于核对图片与文件的对应关系"""
    fig.text(0.004, 0.995, name, fontsize=7, color="#9ca3af",
             ha="left", va="top", family="monospace")


# ----------------------------------------------------------------------------
# 参数集（基准设计点）
# ----------------------------------------------------------------------------
class P:
    """基准参数（单位 SI：Pa, m^3, s）"""

    V_cup = 0.30e-3            # 罩杯+模拟端容积 300 ml
    V_buf = 1.00e-3            # 缓冲瓶容积 1000 ml（故障场景可置 0 走单腔分支）
    Q_p = 3.0e-3 / 60.0        # 泵抽速 3 L/min = 5e-5 m^3/s
    R_cb = 8.0e5               # 罩杯<->缓冲瓶 管路层流阻力（4mm*30cm，tau~2ms）
    K_leak = 1.614e-8          # 密封泄漏系数：标称 50 ml/min @ 20 mmHg
    p_crack = 24.0 * MMHG      # 机械泄压阀起跳 24 mmHg（留 1 mmHg 背压余量）
    K_relief = 4.8e-6          # 泄压阀流导：全泵流量背压≈0.9 mmHg -> 钳位≈24.9
    R_vent = 6.0e6             # 断电泄放路径阻力（tau≈0.077 s @1.3L）
    p_on = 15.0 * MMHG         # 滞环：低于该负压开始抽
    p_off = 20.0 * MMHG        # 滞环：达到该负压停止抽
    p_watch = 23.0 * MMHG      # 第二层看门狗截断阈值
    p_sp = 17.5 * MMHG         # PI 目标 17.5 mmHg


def relief_flow(p_c, prm):
    """机械泄压阀：表压低于 -p_crack 时从大气吸入，Q = K_r*sqrt(过压量)"""
    over = -p_c - prm.p_crack
    return prm.K_relief * np.sqrt(over) if over > 0.0 else 0.0


def leak_flow(p_c, leak_mul, prm):
    """罩杯密封泄漏（外部空气漏入），孔口律 Q = K*sqrt(-p)"""
    if p_c >= 0.0:
        return 0.0
    return leak_mul * prm.K_leak * np.sqrt(-p_c)


def pump_flow(p, prm):
    """泵有效抽速：恒流 + 极限真空线性降额"""
    f = 1.0 - p / P_ULT if p < 0 else 1.0     # p=-60k 时 f=0
    return prm.Q_p * max(0.0, f)


# ----------------------------------------------------------------------------
# 积分器：单步动力学（显式欧拉，dt=0.5ms；耦合模态 tau>=1.8ms 稳定）
# ----------------------------------------------------------------------------
def step_one_vol(p, u, leak_mul, dt, prm):
    """单腔（无缓冲瓶）：泵直接作用于罩杯。u ∈ {'pump','sealed','vent'}"""
    q = leak_flow(p, leak_mul, prm) + relief_flow(p, prm)
    if u == "pump":
        q -= pump_flow(p, prm)
    elif u == "vent":
        q -= p / prm.R_vent
    return p + dt * P_ATM * q / prm.V_cup


def step_two_vol(pc, pb, u, leak_mul, dt, prm):
    """双腔：罩杯与缓冲瓶经 R_cb 耦合。u 作用于缓冲瓶支路"""
    q_cb = (pb - pc) / prm.R_cb              # 缓冲瓶 -> 罩杯（流入罩杯为正）
    q_c = q_cb + leak_flow(pc, leak_mul, prm) + relief_flow(pc, prm)
    q_b = -q_cb
    if u == "pump":
        q_b -= pump_flow(pb, prm)
    elif u == "vent":
        q_b -= pb / prm.R_vent
    pc += dt * P_ATM * q_c / prm.V_cup
    pb += dt * P_ATM * q_b / prm.V_buf
    return pc, pb


def integrate(t_end, dt, u_fn, prm, pc0=0.0, leak_fn=lambda t: 1.0,
              sensor_fn=None, layer2=True, single_vol=False):
    """
    通用推进器。
      u_fn(t, pc, meas_ok) -> 控制指令 u
      sensor_fn(t, pc)     -> (p_meas, ok)；ok=False 表示掉线
      layer2=True          -> 看门狗：测量值>23mmHg 或掉线 -> 断电(永久 vent)
    返回 dict(t, pc, pb, u, meas, l2)
    """
    n = int(t_end / dt)
    t = np.empty(n)
    pc_a = np.empty(n)
    pb_a = np.empty(n)
    u_a = np.empty(n, dtype=object)
    meas_a = np.full(n, np.nan)
    l2_a = np.zeros(n, dtype=bool)

    pc = pc0
    pb = pc0 if single_vol else pc0
    u = "sealed"
    l2 = False
    for i in range(n):
        ti = i * dt
        if sensor_fn is None:
            meas, ok = pc, True
        else:
            meas, ok = sensor_fn(ti, pc)
        if layer2 and not l2 and (not ok or -meas > P.p_watch):
            l2 = True                 # 截断：断电 -> 泵停 + 常开阀泄放
        if l2:
            u = "vent"
        else:
            u = u_fn(ti, pc, meas, ok)
        if single_vol:
            pc = step_one_vol(pc, u, leak_fn(ti), dt, prm)
            pb = pc
        else:
            pc, pb = step_two_vol(pc, pb, u, leak_fn(ti), dt, prm)
        t[i] = ti
        pc_a[i] = pc
        pb_a[i] = pb
        u_a[i] = u
        meas_a[i] = meas if ok else np.nan
        l2_a[i] = l2
    return dict(t=t, pc=pc_a, pb=pb_a, u=u_a, meas=meas_a, l2=l2_a)


# ----------------------------------------------------------------------------
# 控制器
# ----------------------------------------------------------------------------
def make_hysteresis():
    """第一层：15/20 mmHg 滞环（泵开/密封）"""
    state = {"pump": True}            # 初始 |p|<15 -> 抽

    def fn(t, pc, meas, ok):
        if state["pump"]:
            if -pc >= P.p_off:        # 抽到 20 mmHg -> 密封
                state["pump"] = False
        else:
            if -pc < P.p_on:          # 泄漏回升到 15 mmHg 以下 -> 再抽
                state["pump"] = True
        return "pump" if state["pump"] else "sealed"

    return fn


def make_pi_pwm(period=0.5):
    """第一层：PI 占空比 + 实际开关 PWM（周期 1s，脉冲按测量值截断）"""
    st = {"e_i": 0.0, "u": 0.0, "last_t": -1.0}
    Kp, Ki = 0.06, 0.01

    def fn(t, pc, meas, ok):
        if t - st["last_t"] >= 0.05:                  # 50 ms 控制节拍
            st["last_t"] = t
            e = (P.p_sp - (-meas)) / MMHG             # 目标-当前（mmHg幅值）
            st["e_i"] = float(np.clip(st["e_i"] + e * 0.05, -20, 100))
            st["u"] = float(np.clip(Kp * e + Ki * st["e_i"], 0.0, 1.0))
        phase = (t % period) / period
        return "pump" if phase < st["u"] else "sealed"

    return fn


# ----------------------------------------------------------------------------
# 场景 1：正常闭环（滞环 / PI 对比，含 120s 泄漏恶化阶跃）
# ----------------------------------------------------------------------------
def scenario_normal(pwm=False):
    prm = P
    leak = lambda t: 8.0 if t >= 120.0 else 1.0     # 密封恶化 x8
    if pwm:
        u_fn = make_pi_pwm()
        res = integrate(240.0, 5e-4, u_fn, prm, pc0=-16.0 * MMHG,
                        leak_fn=leak, sensor_fn=None, layer2=True)
    else:
        u_fn = make_hysteresis()
        res = integrate(240.0, 5e-4, u_fn, prm, pc0=-16.0 * MMHG,
                        leak_fn=leak, sensor_fn=None, layer2=True)

    p = -res["pc"] / MMHG
    tt = res["t"]
    m = {}
    for name, t0, t1 in (("标称", 0.0, 120.0), ("恶化", 120.0, 240.0)):
        sel = (tt >= t0 + 0.3 * (t1 - t0)) & (tt < t1)   # 取该段后 70% 稳态窗口
        steady = p[sel]
        m[f"{name}_min"] = steady.min()
        m[f"{name}_max"] = steady.max()
    # 占空比：泵开时间占比（用数值微分信号判）
    is_pump = np.array([u == "pump" for u in res["u"]])
    n2 = len(is_pump) // 2
    m["duty_标称"] = is_pump[:n2].mean() * 100
    m["duty_恶化"] = is_pump[n2:].mean() * 100
    m["L2触发"] = bool(res["l2"].any())
    res["metrics"] = m
    return res


# ----------------------------------------------------------------------------
# 场景 2：灾难连锁（MCU死机 + 继电器粘连 + 传感器短路读低 -5mmHg）
#   阀卡在开启 + 泵全速 -> 只有第四层机械泄压阀兜底
# ----------------------------------------------------------------------------
def scenario_fault(V_buf=1.0e-3, relief="good", t_end=4.0):
    class Prm(P):
        pass

    Prm.V_buf = V_buf
    single = V_buf <= 0
    if relief == "good":
        Prm.p_crack, Prm.K_relief = 24.0 * MMHG, 4.8e-6      # 钳位 ~24.9
    elif relief == "bad":
        Prm.p_crack, Prm.K_relief = 25.0 * MMHG, 2.5e-6      # 起跳25+背压3 -> ~28

    # 传感器短路：恒读 -5 mmHg（对第二层完全隐身）
    sensor = lambda t, pc: (-5.0 * MMHG, True)
    # MCU 死机：执行器卡死在“抽气”；但看门狗（独立硬件）仍按规程评估
    u_fn = lambda t, pc, meas, ok: "pump"
    res = integrate(t_end, 5e-4, u_fn, Prm, pc0=-17.5 * MMHG,
                    sensor_fn=sensor, layer2=True, single_vol=single)
    p_true = -res["pc"] / MMHG
    p_meas = -res["meas"] / MMHG
    dt = 5e-4

    def t_cross(th):
        idx = np.where(p_true >= th)[0]
        return res["t"][idx[0]] if len(idx) else np.nan

    res["metrics"] = dict(
        t23=t_cross(23.0), t24=t_cross(24.0),
        clamp=p_true.max(), meas_max=p_meas.max(),
        L2触发=bool(res["l2"].any()),
    )
    return res


# ----------------------------------------------------------------------------
# 场景 3：断电泄放（常开阀 -> tau 泄放）
# ----------------------------------------------------------------------------
def scenario_power_fail():
    prm = P
    res = integrate(1.5, 5e-4, lambda t, pc, m, ok: "vent", prm,
                    pc0=-17.5 * MMHG, layer2=False)
    p = -res["pc"] / MMHG
    idx = np.where(p < 1.0)[0]
    res["metrics"] = dict(t_to1=res["t"][idx[0]] if len(idx) else np.nan,
                          tau=prm.R_vent * (prm.V_cup + prm.V_buf) / P_ATM)
    return res


# ----------------------------------------------------------------------------
# 场景 4：传感器掉线 -> 第二层看门狗 20ms 内截断 -> 泄放
# ----------------------------------------------------------------------------
def scenario_sensor_dropout():
    prm = P
    u_fn = make_hysteresis()

    def sensor(t, pc):
        if t < 10.0:
            return pc + np.random.RandomState(0).normal(0, 5), True
        return np.nan, False                      # 掉线

    res = integrate(13.0, 5e-4, u_fn, prm, pc0=-16.0 * MMHG,
                    sensor_fn=sensor, layer2=True)
    p = -res["pc"] / MMHG
    i_trip = int(np.argmax(res["l2"]))
    idx = np.where((res["t"] > 10.0) & (p < 1.0))[0]
    res["metrics"] = dict(
        trip_time=res["t"][i_trip],
        detect_lat=res["t"][i_trip] - 10.0,
        t_to1=res["t"][idx[0]] - 10.0 if len(idx) else np.nan)
    return res


# ----------------------------------------------------------------------------
# 场景 5：开环固定占空比 PWM（开1s关3s）的危险性 vs 闭环截断
# ----------------------------------------------------------------------------
def scenario_pwm_openloop():
    prm = P

    def loop(u_fn, layer2, t_end=30.0):
        return integrate(t_end, 5e-4, u_fn, prm, pc0=0.0,
                         layer2=layer2)

    def make_openloop():
        ph = {"k": 0.0}

        def fn(t, pc, m, ok):
            ph["k"] = (ph["k"] + 5e-4) % 4.0
            return "pump" if ph["k"] < 1.0 else "sealed"
        return fn

    # (a) 固定 1s 抽 / 3s 密封，电子防线全失效（无看门狗）
    a = loop(make_openloop(), layer2=False)
    # (b) 同样开环，但看门狗有效
    b = loop(make_openloop(), layer2=True)
    # (c) 闭环滞环（对照）
    c = loop(make_hysteresis(), layer2=True)

    def mx(r):
        return (-r["pc"] / MMHG).max()

    trip_b = float(np.argmax(b["l2"]) * 5e-4) if b["l2"].any() else np.nan
    a["metrics"] = b["metrics"] = c["metrics"] = dict(
        max_a=mx(a), max_b=mx(b), max_c=mx(c), trip_b=trip_b)
    return a, b, c


# ----------------------------------------------------------------------------
# 图 1：气路拓扑 + 四层安全防线
# ----------------------------------------------------------------------------
def fig_topology():
    fig, ax = plt.subplots(figsize=(13.2, 5.6))
    ax.set_xlim(0, 132)
    ax.set_ylim(0, 56)
    ax.axis("off")

    # 主气路（单排，从左到右）
    chain = [
        (4,  "模拟端 / 罩杯\n300 ml  V_c",  "#dbeafe"),
        (25, "机械泄压阀\n起跳 24 → 钳位 25", "#fee2e2"),
        (46, "微差压传感器\n0–10 kPa",       "#dcfce7"),
        (67, "缓冲瓶\n1 L  V_b",            "#dbeafe"),
        (88, "常开电磁阀\nPWM / 断电=开",     "#fef9c3"),
        (109, "微型真空泵\n3 L/min",          "#e9d5ff"),
    ]
    W, H, Y = 19, 13, 32
    for x, s, fc in chain:
        ax.add_patch(FancyBboxPatch((x, Y), W, H, boxstyle="round,pad=0.6",
                                    fc=fc, ec="#374151", lw=1.4))
        ax.text(x + W / 2, Y + H / 2, s, ha="center", va="center", fontsize=9.5)
    for x in (23, 44, 65, 86, 107):
        ax.add_patch(FancyArrowPatch((x, Y + H / 2), (x + 2, Y + H / 2),
                                     arrowstyle="-|>", mutation_scale=15,
                                     color="#111827", lw=1.6))

    def arrow(x1, y1, x2, y2, c="#111827", ls="-"):
        ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>",
                                     mutation_scale=14, color=c, lw=1.6,
                                     linestyle=ls))

    # 控制盒（下方）
    ax.add_patch(FancyBboxPatch((46, 6), 41, 11, boxstyle="round,pad=0.6",
                                fc="#f3f4f6", ec="#374151", lw=1.4))
    ax.text(66.5, 11.5, "MCU + 独立看门狗\n② >23 mmHg 或掉线 → 断电",
            ha="center", va="center", fontsize=9.5)
    # ① 反馈：传感器底 -> MCU（蓝虚线）
    ax.plot([55.5, 55.5], [Y, 19], color="#2563eb", lw=1.4, ls="--")
    arrow(55.5, 19.8, 55.5, 17.6, c="#2563eb")
    ax.text(43.5, 24, "① 闭环反馈 15–20 mmHg", fontsize=9, color="#2563eb",
            ha="right")
    # 控制：MCU -> 电磁阀（蓝虚线）
    ax.plot([87, 93, 93], [11.5, 11.5, Y - 1], color="#2563eb",
            lw=1.4, ls="--")
    arrow(93, Y - 4, 93, Y - 1.4, c="#2563eb")
    ax.text(86, 15.5, "PWM 驱动", fontsize=9, color="#2563eb")
    # ③ 断电泄放：常开阀复位导通大气（红虚线向下）
    arrow(102, Y, 102, 4.5, c="#dc2626")
    ax.plot([102], [Y], marker="o", ms=4, color="#dc2626")
    ax.text(99.5, 2.2, "③ 断电：阀弹簧复位导通大气 → 0.1 s 级泄放（τ≈80 ms）",
            fontsize=9, color="#dc2626")
    # ④ 泄压阀 -> 大气
    arrow(34.5, Y + H, 34.5, Y + H + 5.5, c="#b91c1c")
    ax.text(36.5, Y + H + 6, "④ 纯机械：|p|≥25 mmHg 时大气强行涌入钳位",
            fontsize=9.5, color="#b91c1c")
    # 动态泄漏
    arrow(13.5, Y + H, 13.5, Y + H + 5.5, c="#6b7280")
    ax.plot([13.5], [Y + H + 5.5], marker="v", ms=6, color="#6b7280")
    ax.text(2, Y + H + 6.5, "动态泄漏（湿度/位移）", fontsize=9, color="#6b7280")
    # 泵排气
    arrow(128, Y + H / 2, 131, Y + H / 2, c="#6b7280")
    ax.text(126.5, Y + H / 2 + 2.2, "排气", fontsize=8.5, color="#6b7280")

    ax.text(66, 54, "三段式气路拓扑与四层安全防线", fontsize=13.5,
            ha="center", fontweight="bold")
    fig.tight_layout()
    fig.savefig(os.path.join(FIG_DIR, "fig1_topology.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 图 2：正常闭环（滞环 vs PI-PWM，120s 泄漏恶化）
# ----------------------------------------------------------------------------
def fig_normal(r_h, r_pi):
    fig, axes = plt.subplots(2, 1, figsize=(11.5, 7.4), sharex=True)
    for ax, r, name in ((axes[0], r_h, "(a) 滞环控制 15/20 mmHg"),
                        (axes[1], r_pi, "(b) PI + 0.5s PWM 闭环")):
        t = r["t"]
        ax.axhspan(15, 20, color="#dcfce7", zorder=0, label="目标带 15-20")
        ax.axhline(23, color="#f59e0b", ls="--", lw=1, label="看门狗 23")
        ax.axhline(25, color="#dc2626", ls="-", lw=1.2, label="物理红线 25")
        ax.axvline(120, color="#6b7280", ls=":", lw=1.2)
        ax.text(121, 26.4, "泄漏 ×8", fontsize=9, color="#6b7280")
        ax.plot(t, -r["pc"] / MMHG, lw=1.0, color="#2563eb", label="罩杯压力")
        m = r["metrics"]
        ax.set_ylim(0, 28)
        ax.set_ylabel("负压 |p|  [mmHg]")
        ax.set_title(f"{name}   标称 {m['标称_min']:.1f}~{m['标称_max']:.1f}  "
                     f"恶化 {m['恶化_min']:.1f}~{m['恶化_max']:.1f} mmHg  |  "
                     f"泵占空比 {m['duty_标称']:.1f}% / {m['duty_恶化']:.1f}%",
                     fontsize=10.5)
        ax.legend(loc="lower right", fontsize=8.5, ncol=2)
        ax.grid(alpha=0.3)
    axes[1].set_xlabel("时间 [s]")
    fig.tight_layout()
    _stamp(fig, "fig2_normal.png")
    fig.savefig(os.path.join(FIG_DIR, "fig2_normal.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 图 3：灾难连锁 + 第四层物理钳位
# ----------------------------------------------------------------------------
def fig_fault(cases):
    fig, ax = plt.subplots(figsize=(11.5, 5.6))
    ax.axhspan(23, 40, color="#fff7ed", zorder=0)
    ax.axhline(25, color="#dc2626", lw=1.6, label="物理红线 25 mmHg")
    ax.axhline(23, color="#f59e0b", ls="--", lw=1, label="看门狗阈值（本场景被短路隐身）")
    ax.axhline(24, color="#991b1b", ls=":", lw=1, label="泄压阀起跳 24")

    styles = [
        ("1L 缓冲瓶 + 合格泄压阀", "#2563eb", "-"),
        ("无缓冲瓶 + 合格泄压阀", "#059669", "-"),
        ("1L + 泄压阀选型错误(起跳25/背压3)", "#dc2626", "--"),
    ]
    notes = [(0.55, 19.5), (0.30, 9.5), (1.15, 29.5)]
    for (key, r), (lab, c, ls), (nx, ny) in zip(cases.items(), styles, notes):
        t = r["t"]
        ax.plot(t, -r["pc"] / MMHG, color=c, ls=ls, lw=1.6, label=lab)
        m = r["metrics"]
        if not np.isnan(m["t24"]):
            if key == "bad":
                txt = f"稳态钳位 {m['clamp']:.1f} mmHg > 25 ×"
                xy = (1.7, m["clamp"])
            else:
                txt = f"到 24 mmHg: {m['t24']*1000:.0f} ms"
                xy = (m["t24"], 24)
            ax.annotate(txt, xy=xy, xytext=(nx, ny),
                        fontsize=9, color=c,
                        arrowprops=dict(arrowstyle="->", color=c, lw=1))
    ax.plot(cases["buf"]["t"], -cases["buf"]["meas"] / MMHG, color="#9ca3af",
            lw=1.4, ls="-.", label="传感器读数（短路恒 -5，对看门狗隐身）")
    ax.set_xlim(0, 3.0)
    ax.set_ylim(0, 31)
    ax.set_xlabel("时间 [s]")
    ax.set_ylabel("负压 |p|  [mmHg]")
    ax.set_title("灾难场景：MCU死机 + 继电器粘连 + 传感器短路 → 前三层全部失效，"
                 "第四层机械泄压阀钳位", fontsize=11.5)
    ax.legend(loc="lower right", fontsize=8.5)
    ax.grid(alpha=0.3)
    fig.tight_layout()
    _stamp(fig, "fig3_fault.png")
    fig.savefig(os.path.join(FIG_DIR, "fig3_fault.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 图 4：断电泄放 + 传感器掉线看门狗
# ----------------------------------------------------------------------------
def fig_power(r_pf, r_do):
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.6))
    ax = axes[0]
    t, p = r_pf["t"], -r_pf["pc"] / MMHG
    ax.plot(t, p, color="#2563eb", lw=1.8, label="罩杯压力")
    ax.plot(t, -r_pf["pb"] / MMHG, color="#059669", lw=1.2, ls="--", label="缓冲瓶")
    t1 = r_pf["metrics"]["t_to1"]
    ax.axhline(1, color="#6b7280", ls=":", lw=1)
    ax.axvline(t1, color="#6b7280", ls=":", lw=1)
    ax.annotate(f"|p|<1 mmHg 用时 {t1*1000:.0f} ms", xy=(t1, 1),
                xytext=(t1 + 0.12, 8), fontsize=9.5,
                arrowprops=dict(arrowstyle="->", color="#374151"))
    ax.set_title(f"(a) 断电 → 常开阀泄放（τ = {r_pf['metrics']['tau']*1000:.0f} ms）",
                 fontsize=10.5)
    ax.set_xlabel("时间 [s]")
    ax.set_ylabel("负压 |p|  [mmHg]")
    ax.legend(fontsize=8.5)
    ax.grid(alpha=0.3)

    ax = axes[1]
    t, p = r_do["t"], -r_do["pc"] / MMHG
    ax.axvspan(10, 13, color="#fee2e2", zorder=0, label="传感器掉线区")
    ax.axhline(15, color="#dcfce7", zorder=0)
    ax.axhspan(15, 20, color="#dcfce7", zorder=0, label="目标带")
    ax.plot(t, p, color="#2563eb", lw=1.5, label="罩杯压力")
    m = r_do["metrics"]
    ax.axvline(m["trip_time"], color="#dc2626", ls="--", lw=1.4,
               label=f"看门狗截断 +{m['detect_lat']*1000:.0f} ms")
    ax.set_xlim(8, 13)
    ax.set_ylim(-1, 24)
    ax.set_title("(b) 传感器掉线 → 第二层看门狗 20 ms 内断电泄放", fontsize=10.5)
    ax.set_xlabel("时间 [s]")
    ax.set_ylabel("负压 |p|  [mmHg]")
    ax.legend(fontsize=8.5, loc="center right")
    ax.grid(alpha=0.3)
    fig.tight_layout()
    _stamp(fig, "fig4_power_dropout.png")
    fig.savefig(os.path.join(FIG_DIR, "fig4_power_dropout.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 图 5：开环固定 PWM 的危险性
# ----------------------------------------------------------------------------
def fig_pwm(a, b, c):
    fig, ax = plt.subplots(figsize=(11.5, 5.2))
    ax.axhspan(23, 30, color="#fff7ed", zorder=0)
    ax.axhline(25, color="#dc2626", lw=1.5, label="物理红线 25")
    ax.axhline(20, color="#dcfce7", lw=8, zorder=0, label="目标带 15-20")
    ax.plot(a["t"], -a["pc"] / MMHG, color="#dc2626", lw=1.5,
            label="开环 1s/3s + 电子防线全失效")
    ax.plot(b["t"], -b["pc"] / MMHG, color="#f59e0b", lw=1.3, ls="--",
            label="开环 + 看门狗有效")
    ax.plot(c["t"], -c["pc"] / MMHG, color="#2563eb", lw=1.5,
            label="闭环滞环（对照）")
    ax.axvline(b["metrics"]["trip_b"], color="#f59e0b", ls=":", lw=1)
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 31)
    ax.set_xlabel("时间 [s]")
    ax.set_ylabel("负压 |p|  [mmHg]")
    ax.set_title("开环固定占空比：1 秒内冲过安全线并被机械阀锁死在高位；"
                 "闭环截断脉冲稳定在带内", fontsize=11)
    ax.legend(loc="lower right", fontsize=8.5)
    ax.grid(alpha=0.3)
    fig.tight_layout()
    _stamp(fig, "fig5_pwm.png")
    fig.savefig(os.path.join(FIG_DIR, "fig5_pwm.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 图 6：设计图表（缓冲瓶选型时间律 / 泄压阀流导选型）
# ----------------------------------------------------------------------------
def fig_design():
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.8))

    ax = axes[0]
    V = np.linspace(0.1e-3, 3e-3, 60)
    for Q_lpm, c in zip([0.3, 1.0, 3.0, 5.0], ["#059669", "#2563eb",
                                                "#f59e0b", "#dc2626"]):
        Q = Q_lpm * 1e-3 / 60.0
        t = V * (25 * MMHG) / (P_ATM * Q)
        ax.plot(V * 1e3, t, color=c, label=f"泵 {Q_lpm} L/min")
    ax.axhline(0.3, color="#374151", ls="--", lw=1)
    ax.text(2.3, 0.42, "看门狗可用窗口 ≥0.3 s\n（100Hz 采样）", fontsize=8.5)
    ax.axvline(1.3, color="#9ca3af", ls=":", lw=1)
    ax.text(1.35, 5.5, "基准 1.3 L", fontsize=8.5, color="#6b7280")
    ax.set_xlabel("总容积 V_c+V_b  [L]")
    ax.set_ylabel("0 → 25 mmHg 时间 [s]")
    ax.set_title("“空间换时间”定律  t = V·Δp/(P_atm·Q)", fontsize=10.5)
    ax.set_yscale("log")
    ax.legend(fontsize=8.5)
    ax.grid(alpha=0.3, which="both")

    ax = axes[1]
    K = np.logspace(-6, -5.3, 80)
    for crack, c in zip([23, 24, 25], ["#059669", "#2563eb", "#dc2626"]):
        clamp = crack + (P.Q_p / K) ** 2 / MMHG
        ax.plot(K, clamp, color=c, label=f"起跳 {crack} mmHg")
    ax.axhline(25, color="#dc2626", ls="-", lw=1.5, label="红线 25")
    ax.axvline(4.8e-6, color="#9ca3af", ls=":", lw=1)
    ax.plot([4.8e-6], [24.9], "ko", ms=5)
    ax.text(5.0e-6, 22.6, "设计点\nK_r=4.8e-6", fontsize=8.5)
    ax.set_xscale("log")
    ax.set_xlabel("泄压阀流导 K_r  [m³/(s·Pa^0.5)]")
    ax.set_ylabel("稳态钳位压力 [mmHg]")
    ax.set_title("钳位 = 起跳 + (Q_p/K_r)²  —— 全流量背压选型", fontsize=10.5)
    ax.legend(fontsize=8.5)
    ax.grid(alpha=0.3, which="both")

    fig.tight_layout()
    _stamp(fig, "fig6_design.png")
    fig.savefig(os.path.join(FIG_DIR, "fig6_design.png"), bbox_inches="tight")
    plt.close(fig)


# ----------------------------------------------------------------------------
# 主流程
# ----------------------------------------------------------------------------
def main():
    os.makedirs(FIG_DIR, exist_ok=True)
    print("=" * 74)
    print("微负压闭环气动系统 —— 数学建模仿真")
    print("=" * 74)

    print("\n[1/6] 正常闭环（滞环 / PI-PWM，含泄漏恶化）...")
    r_h = scenario_normal(pwm=False)
    r_pi = scenario_normal(pwm=True)
    for name, r in (("滞环", r_h), ("PI-PWM", r_pi)):
        m = r["metrics"]
        print(f"    {name:7s} 标称泄漏带内 {m['标称_min']:.2f}~{m['标称_max']:.2f} mmHg | "
              f"恶化后 {m['恶化_min']:.2f}~{m['恶化_max']:.2f} mmHg | "
              f"占空比 {m['duty_标称']:.1f}%/{m['duty_恶化']:.1f}% | "
              f"看门狗触发 {m['L2触发']}")

    print("\n[2/6] 灾难连锁（传感器短路恒-5mmHg，看门狗隐身）...")
    cases = {
        "buf": scenario_fault(V_buf=1.0e-3, relief="good"),
        "nob": scenario_fault(V_buf=0.0, relief="good"),
        "bad": scenario_fault(V_buf=1.0e-3, relief="bad"),
    }
    for k, r in cases.items():
        m = r["metrics"]
        print(f"    {k:4s} 到23: {m['t23']*1000:7.1f} ms | 到24: {m['t24']*1000:7.1f} ms | "
              f"钳位 {m['clamp']:.2f} mmHg | 读数峰值 {m['meas_max']:.1f} | "
              f"L2触发 {m['L2触发']}")

    print("\n[3/6] 断电泄放...")
    r_pf = scenario_power_fail()
    print(f"    tau = {r_pf['metrics']['tau']*1000:.0f} ms | "
          f"|p|<1 mmHg 用时 {r_pf['metrics']['t_to1']*1000:.0f} ms")

    print("\n[4/6] 传感器掉线 → 看门狗...")
    r_do = scenario_sensor_dropout()
    print(f"    截断延迟 {r_do['metrics']['detect_lat']*1000:.0f} ms | "
          f"泄放到 <1 mmHg {r_do['metrics']['t_to1']*1000:.0f} ms")

    print("\n[5/6] 开环固定 PWM 危险性...")
    a, b, c = scenario_pwm_openloop()
    print(f"    开环+无电子防线 峰值 {a['metrics']['max_a']:.1f} mmHg（机械阀锁死）| "
          f"开环+看门狗 峰值 {b['metrics']['max_b']:.1f}，"
          f"{b['metrics']['trip_b']*1000:.0f} ms 截断 | "
          f"闭环 峰值 {c['metrics']['max_c']:.1f} mmHg")

    print("\n[6/6] 绘图...")
    fig_topology()
    fig_normal(r_h, r_pi)
    fig_fault(cases)
    fig_power(r_pf, r_do)
    fig_pwm(a, b, c)
    fig_design()
    print(f"    完成 → {FIG_DIR}")
    print("=" * 74)


if __name__ == "__main__":
    main()
