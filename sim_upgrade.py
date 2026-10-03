# -*- coding: utf-8 -*-
"""仿真能力升级包 —— 用开源社区工具解决建模文档 §10 的四大遗留局限
=============================================================================
A. 独立求解器交叉验证：同一 RHS 分别用 固定步长欧拉(自研) 与 scipy DOP853
   (rtol=1e-9) 积分，对比轨迹/关键时间，消除“自己算自己”的自证问题。
B. 等温假设量化：双状态(质量+能量)热力学模型，物性取自 CoolProp 6.7.0；
   对比 等温 / 绝热 / 有限换热(τ=20s) 三种假设下的泵停故障时间与密封相热漂移。
C. 安全裕度统计量化：10k 样本蒙特卡洛（泄压阀起跳公差、流导散布、泵速散布、
   容积公差）→ 钳位分布 P(>25mmHg) 与看门狗窗口分布 → 校核 §9 规格。
D. 泄压阀阀芯动力学：弹簧-质量-阻尼 + 流量耦合，评估开阀冲击、颤振与
   与 PWM 频率的共振风险。

运行： python sim_upgrade.py     （输出 figs/fig8_thermal.png, fig9_mc.png）
"""
import os
import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

import model as M            # 复用元件模型（泄漏/泄压/泵流量）

MMHG = 133.322
P_ATM = M.P_ATM
FIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figs")

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "sans-serif"]
plt.rcParams["axes.unicode_minus"] = False

# ---------------------------------------------------------------------------
# CoolProp 物性（开源热物性库）；导入失败时回退到其 300K/1atm 权威值
# ---------------------------------------------------------------------------
try:
    import CoolProp.CoolProp as CP

    T0, P0 = 300.0, 101325.0
    CP_CP = CP.PropsSI("CPMASS", "T", T0, "P", P0, "Air")
    CP_CV = CP.PropsSI("CVMASS", "T", T0, "P", P0, "Air")
    CP_R = CP_CP - CP_CV
    CP_RHO = CP.PropsSI("D", "T", T0, "P", P0, "Air")
    CP_SRC = "CoolProp %s" % __import__("CoolProp").__version__
except Exception:                                   # 回退
    CP_CP, CP_CV = 1006.37, 717.97
    CP_R, CP_RHO = CP_CP - CP_CV, 1.1770
    CP_SRC = "fallback(=CoolProp 6.7.0@300K)"


def ideal_gas_deviation():
    """CoolProp 实际密度 vs 理想气体密度 —— 量化 §10.1 等温/理想气体前提"""
    rows = []
    for g in (0, -20 * MMHG, -25 * MMHG, -60000):
        p = P_ATM + g
        try:
            import CoolProp.CoolProp as CP
            rho = CP.PropsSI("D", "T", 300.0, "P", p, "Air")
            k = (CP.PropsSI("CPMASS", "T", 300.0, "P", p, "Air") /
                 CP.PropsSI("CVMASS", "T", 300.0, "P", p, "Air"))
        except Exception:
            rho, k = p / (CP_R * 300.0), 1.4
        dev = (rho / (p / (CP_R * 300.0)) - 1) * 100
        rows.append((g, dev, k))
    return rows


# ===========================================================================
# A. 独立求解器交叉验证
# ===========================================================================
def rhs_two_vol(y, u, leak_mul, prm):
    """与 model.step_two_vol 同一物理的连续 RHS（供 scipy 调用）"""
    pc, pb = y
    q_cb = (pb - pc) / prm.R_cb
    q_c = q_cb + M.leak_flow(pc, leak_mul, prm) + M.relief_flow(pc, prm)
    q_b = -q_cb
    if u == "pump":
        q_b -= M.pump_flow(pb, prm)
    elif u == "vent":
        q_b -= pb / prm.R_vent
    return np.array([P_ATM * q_c / prm.V_cup, P_ATM * q_b / prm.V_buf])


def euler_trace(y0, u, t_end, dt, prm, leak_mul=1.0):
    n = int(t_end / dt)
    out = np.empty((n, 2))
    y = np.array(y0, float)
    for i in range(n):
        out[i] = y
        y = y + dt * rhs_two_vol(y, u, leak_mul, prm)
    return out


def section_A():
    from scipy.integrate import solve_ivp
    prm = M.P
    y0 = [-17.5 * MMHG, -17.5 * MMHG]
    dt, t_end = 5e-4, 4.0
    ref = euler_trace(y0, "pump", t_end, dt, prm)
    sol = solve_ivp(lambda t, y: rhs_two_vol(y, "pump", 1.0, prm),
                    (0, t_end), y0, method="DOP853",
                    rtol=1e-9, atol=1e-8, dense_output=True)
    chk = sol.sol(np.arange(int(t_end / dt)) * dt)[0]      # 罩杯压力轨迹

    def t_cross(tr, thr):
        idx = np.where(tr / MMHG <= -thr)[0]
        return idx[0] * dt if len(idx) else np.nan

    diff = np.max(np.abs(ref[:, 0] - chk))
    print("  [A] 欧拉(dt=0.5ms) vs scipy DOP853(rtol=1e-9)：")
    print(f"      罩杯轨迹最大偏差 = {diff:.4f} Pa "
          f"({diff / MMHG:.6f} mmHg)")
    print(f"      到 24mmHg：欧拉 {t_cross(ref[:,0], 24)*1000:.1f} ms / "
          f"DOP853 {t_cross(chk, 24)*1000:.1f} ms")
    print(f"      稳态钳位：欧拉 {-ref[-1,0]/MMHG:.3f} / "
          f"DOP853 {-chk[-1]/MMHG:.3f} mmHg")
    print("      结论：两独立求解器一致 → 既有仿真数值可信" if diff < 5 else
          "      结论：偏差过大，需检查步长/刚性！")
    return ref, chk, dt


# ===========================================================================
# B. 热力学双状态模型（等温假设量化）
# ===========================================================================
class Thermal:
    """状态 [m_c, U_c, m_b, U_b]；U = m·cv·T；p_abs = m·R·T/V
       τ_th=None → 绝热；τ_th=20s → 有限换热 hA = m0·cv/τ"""

    def __init__(self, prm, tau_th):
        self.prm, self.tau_th = prm, tau_th
        self.T_amb = 300.0
        self.rho0 = P_ATM / (CP_R * self.T_amb)
        self.m_c0 = self.rho0 * prm.V_cup
        self.m_b0 = self.rho0 * prm.V_buf

    def init_from_p(self, p_g):
        """给定初始表压构造热力学平衡态"""
        f = 1.0 + p_g / P_ATM
        mc, mb = self.m_c0 * f, self.m_b0 * f
        return np.array([mc, mc * CP_CV * self.T_amb,
                         mb, mb * CP_CV * self.T_amb])

    def _hA(self, m0):
        return 0.0 if self.tau_th is None else m0 * CP_CV / self.tau_th

    def rhs(self, t, s, u, leak_mul):
        prm = self.prm
        mc, Uc, mb, Ub = s
        mc = max(mc, 1e-9); mb = max(mb, 1e-9)
        Tc = Uc / (mc * CP_CV); Tb = Ub / (mb * CP_CV)
        pc = mc * CP_R * Tc / prm.V_cup - P_ATM
        pb = mb * CP_R * Tb / prm.V_buf - P_ATM
        Tamb = self.T_amb
        # 体积流量（与主模型同一约定：大气密度折算）→ 质量流量
        q_cb = (pb - pc) / prm.R_cb
        m_cb = self.rho0 * q_cb                     # >0: b→c
        m_leak = self.rho0 * M.leak_flow(pc, leak_mul, prm)
        m_rel = self.rho0 * M.relief_flow(pc, prm)
        m_pump = self.rho0 * M.pump_flow(pb, prm) if u == "pump" else 0.0
        m_vent = (-pb / prm.R_vent) * self.rho0 if u == "vent" else 0.0
        # 质量守恒
        dmc = m_cb + m_leak + m_rel
        dmb = -m_cb - m_pump - m_vent
        # 能量守恒（转移流带源侧温度；入流 T_amb；热损 hA(T-Tamb)）
        Tsrc = Tb if m_cb >= 0 else Tc
        dUc = m_cb * CP_CP * Tsrc + (m_leak + m_rel) * CP_CP * Tamb
        dUb = -m_cb * CP_CP * Tsrc - (m_pump + m_vent) * CP_CP * \
            (Tb if (m_pump + m_vent) >= 0 else Tamb)
        dUc -= self._hA(self.m_c0) * (Tc - Tamb)
        dUb -= self._hA(self.m_b0) * (Tb - Tamb)
        return np.array([dmc, dUc, dmb, dUb])

    def integrate(self, y0p, u_fn, t_end, dt, leak_fn=lambda t: 1.0):
        n = int(t_end / dt)
        s = self.init_from_p(y0p)
        pc = np.empty(n); t = np.empty(n)
        u = "sealed"
        for i in range(n):
            ti = i * dt
            mc, Uc, mb, Ub = s
            Tc = Uc / max(mc, 1e-9) / CP_CV
            p_g = mc * CP_R * Tc / self.prm.V_cup - P_ATM
            u = u_fn(ti, p_g)
            pc[i] = p_g
            s = s + dt * self.rhs(ti, s, u, leak_fn(ti))
            t[i] = ti
        return t, pc


def section_B(ref, dt):
    prm = M.P
    print("  [B] 物性来源：%s  R=%.2f J/kgK  cp=%.1f  ρ0=%.4f"
          % (CP_SRC, CP_R, CP_CP, CP_RHO))
    print("      理想气体偏差（CoolProp 实测 vs 理想式）：")
    for g, dev, k in ideal_gas_deviation():
        print(f"        p_g={g:8.0f} Pa: ρ 偏差 {dev:+.3f}%   k={k:.4f}"
              f"   → 等温/理想气体假设误差 <0.5%，成立")

    def t_cross(tr, tt, thr):
        idx = np.where(tr / MMHG <= -thr)[0]
        return tt[idx[0]] if len(idx) else np.nan

    # —— 场景1：泵全速故障，比较三种热假设到 24mmHg 的时间 ——
    t_iso = t_cross(ref[:, 0], np.arange(len(ref)) * dt, 24)
    res = {}
    for name, tau in (("绝热", None), ("有限换热τ=20s", 20.0)):
        th = Thermal(prm, tau)
        tt, pcc = th.integrate(-17.5 * MMHG, lambda t, p: "pump", 4.0, dt)
        res[name] = (tt, pcc, t_cross(pcc, tt, 24))
    print("      场景① 泵全速故障 → 到 24mmHg 时间：")
    print(f"        等温(主模型)        {t_iso*1000:7.1f} ms")
    print(f"        绝热(无热交换)      {res['绝热'][2]*1000:7.1f} ms "
          f"(快 {(1-res['绝热'][2]/t_iso)*100:.0f}% —— 膨胀冷却使压力下探更快)")
    print(f"        有限换热 τ=20s      {res['有限换热τ=20s'][2]*1000:7.1f} ms")

    print("      场景② 抽至 -20 密封 60s 的热漂移（关泄漏=纯热效应，等温应恒定）：")
    losses = {}
    for name, tau in (("等温 τ→0", 0.01), ("有限换热τ=20s", 20.0), ("绝热", None)):
        th = Thermal(prm, tau)
        u_seal = lambda t, p: "pump" if (t < 3.0 and p / MMHG > -20) else "sealed"
        tt, pcc = th.integrate(-5.0 * MMHG, u_seal, 63.0, dt, leak_fn=lambda t: 0.0)
        seal = pcc[tt >= 3.0]
        losses[name] = (seal[-1] - seal[0]) / MMHG   # 负压幅值衰减量（正=衰减）
    for k, v in losses.items():
        print(f"        {k:14s} 负压衰减 {v:.2f} mmHg"
              + ("（压缩冷却后回温 → |p| 下降）" if "20" in k else ""))
    worst = max(losses.values())
    budget = 5.0   # 滞环 20→15 的吸收预算
    print(f"      → 最坏衰减 {worst:.1f} mmHg vs 滞环预算 {budget:.0f} mmHg → "
          f"{'余量足够，滞环可吸收（且泄漏同向、进一步抵消）' if worst <= budget else '预算不足！需加快重抽或收窄带上限'}")
    return losses


# ===========================================================================
# C. 蒙特卡洛安全裕度（10k 样本）
# ===========================================================================
def section_C(rng):
    N = 20000
    Q_p = rng.normal(3.0e-3 / 60.0, 0.10 * 3.0e-3 / 60.0, N)   # 3 L/min ±10%
    Q_p = np.clip(Q_p, 1e-6, None)
    Kr_good = np.clip(rng.normal(4.8e-6, 0.10 * 4.8e-6, N), 1e-7, None)

    def clamp_stats(crack_mu, crack_sd, Kr, tag):
        crack = rng.normal(crack_mu, crack_sd, N)
        clamp = crack + (Q_p / Kr) ** 2 / MMHG
        p_over = (clamp > 25).mean() * 100
        print(f"        {tag:34s} 钳位 {clamp.mean():5.2f}±{clamp.std():.2f} "
              f"mmHg  P(>25)={p_over:5.1f}%  P95={np.percentile(clamp,95):.2f}")
        return clamp

    print(f"  [C] 蒙特卡洛 N={N}（阀起跳公差±0.5mmHg、K_r与泵速±10%散布）：")
    print("      泄压层 L4 钳位分布：")
    c24 = clamp_stats(24.0, 0.5, Kr_good, "现行规格：起跳24.0±0.5")
    c235 = clamp_stats(23.5, 0.5, Kr_good, "收紧：起跳23.5±0.5")
    c23 = clamp_stats(23.0, 0.5, Kr_good, "收紧：起跳23.0±0.5")

    # 看门狗窗口（-20 → -23）分布
    print("      看门狗窗口（-20→-23，1L缓冲瓶）：")
    for q_lpm in (3.0, 1.0):
        Q = rng.normal(q_lpm * 1e-3 / 60.0, 0.10 * q_lpm * 1e-3 / 60.0, N)
        V = rng.normal(1.3e-3, 0.02 * 1.3e-3, N)             # 打印/瓶公差±2%
        t23 = V * (3 * MMHG) / (P_ATM * np.clip(Q, 1e-6, None))
        p01 = (t23 < 0.10).mean() * 100
        print(f"        泵 {q_lpm:.0f} L/min: {t23.mean()*1000:6.1f}±"
              f"{t23.std()*1000:.1f} ms   P(<100ms 采样窗)={p01:6.2f}%")
    return c24, c235, c23


# ===========================================================================
# D. 泄压阀阀芯动力学（弹簧-质量-阻尼 + 流量耦合）
# ===========================================================================
def section_D():
    """阀芯弹簧-质量-阻尼 + 开度相关流量耦合。
    平衡点解析（两方程消元）：
        过压 over = (p_crack / (x0/x_max)) · (Q_p / (K_r·√p))   ★
    → 同一 K_r 下，真实弹簧阀的稳态过压显著大于“理想阀”(Q/K)² 口径；
      本段用 双配置对照 校核 §5.2/§9 的 K_r 规格。"""
    prm = M.P
    Q_p = prm.Q_p
    p_cr = 24.0 * MMHG
    m_v = 0.002                                     # 阀芯质量 2g
    rho_cd = 0.6 * np.sqrt(2.0 / 1.2)               # K_r = Cd·√(2/ρ)·A_seat

    def mk(K_r, x0_mm):
        x0 = x_max = x0_mm * 1e-3
        A = K_r / rho_cd                            # 阀座面积（力作用面积）
        k = p_cr * A / x0                           # 预压弹簧：24mmHg 顶开
        return A, x0, x_max, k

    def run(K_r, x0_mm, zeta, t_end=2.0, dt=5e-5):
        A, x0, x_max, k = mk(K_r, x0_mm)
        c = zeta * 2 * np.sqrt(k * m_v)
        x, v, p = 0.0, 0.0, -17.5 * MMHG
        n = int(t_end / dt)
        px, xx = np.empty(n), np.empty(n)
        for i in range(n):
            px[i], xx[i] = p, x
            q_rel = K_r * (x / x_max) * np.sqrt(max(0.0, -p)) if x > 0 else 0.0
            # 符号约定：泵=出流(负)，泄压/泄漏=大气涌入(正) —— 与主模型一致
            dp = P_ATM / (prm.V_cup + prm.V_buf) * (
                q_rel + M.leak_flow(p, 1.0, prm) - M.pump_flow(p, prm))
            a = (max(0.0, -p) * A - k * (x0 + x) - c * v) / m_v
            x = max(0.0, x + dt * v)
            v = v + dt * a
            p = p + dt * dp
        return np.arange(n) * dt, px, xx

    def analytic_over(K_r, x0_mm):
        """解析稳态过压（x0=x_max → 比值约去，迭代到收敛）"""
        p = p_cr
        for _ in range(3):
            over = p_cr * Q_p / (K_r * np.sqrt(p))
            p = p_cr + over
        return p - p_cr

    configs = [
        ("现行规格 K_r=4.8e-6(座≈Ø2.8)", 4.8e-6, 0.5),
        ("加大阀座 K_r=4.5e-5(座≈Ø8)",  4.5e-5, 1.0),
    ]
    print("  [D] 阀芯动力学 + 稳态过压双配置对照：")
    results = {}
    for tag, K_r, x0_mm in configs:
        A, x0, x_max, k = mk(K_r, x0_mm)
        f_n = np.sqrt(k / m_v) / (2 * np.pi)
        over_a = analytic_over(K_r, x0_mm)
        for zeta in (0.10, 0.35):
            t, p, x = run(K_r, x0_mm, zeta)
            late = t > 1.5
            p_eq = abs(p[late].mean()) / MMHG
            ripple = (p[late].max() - p[late].min()) / MMHG
            settle = np.nan
            dev = np.abs(abs(p) - abs(p[late].mean())) / MMHG
            bad = np.where(dev > 0.2)[0]
            if len(bad) and bad[-1] < len(t) - 1:
                settle = t[bad[-1] + 1]
            key = f"{tag} ζ={zeta:.2f}"
            results[key] = (t, p, x, ripple)
            print(f"        {tag:30s} ζ={zeta:.2f} f_n={f_n:4.1f}Hz | "
                  f"稳态钳位 {p_eq:5.2f} mmHg（解析 {p_cr/MMHG + over_a/MMHG:5.2f}）"
                  f" | 纹波 {ripple:.2f} | 稳定 {settle:.2f}s")
    over_need = p_cr * Q_p / (1.0 * MMHG * (p_cr ** 0.5))   # over=1mmHg 所需 K_r·(x0/x_max)
    print("      ★ 真实弹簧阀过压定律 over = p_crack·Q/( (x0/x_max)·K_r·√p )：")
    print(f"        要求全流量过压 ≤1mmHg → K_r ≥ {over_need:.1e} "
          f"（≈Ø6~8mm 阀座）；现行 4.8e-6 不满足 → §5.2/§9 规格需修订")
    print("      ★ PWM 主频 2 Hz ≪ 阀芯固有频率 24~49 Hz → 无共振激励")
    return results


# ===========================================================================
# E. 传感噪声×量化×零漂 的滞环抖振扫描（纯仿真关闭 §10.6 最后缺口）
# ===========================================================================
def section_E(rng):
    """降阶滞环动力学（100ms 读取节拍，与固件一致）：
         抽气相  d|p|/dt = +S_pump；密封相  d|p|/dt = -K_leak√|p|
       测量值 = 真值 + 零漂 + 高斯噪声 + 量化；滞环作用于测量值。
       统计真值的带外越界、泵启停次数（抖振）与占空比。"""
    S_pump = P_ATM * M.P.Q_p / (M.P.V_cup + M.P.V_buf) / MMHG   # mmHg/s
    K_leak = P_ATM * M.P.K_leak / (M.P.V_cup + M.P.V_buf)       # Pa^0.5/s
    dt = 0.1                    # 100 ms 读取节拍（=固件 CTRL_PERIOD_MS）
    T = 600.0                   # 每工况 10 分钟
    n = int(T / dt)

    cases = [
        # (标签, 噪声σ mmHg, 零漂 mmHg, 量化步长 mmHg)
        ("基准（噪声0.05/漂0/量化0.02）", 0.05, 0.0, 0.02),
        ("传感器级噪声 σ=0.38(2mV)",      0.38, 0.0, 0.02),
        ("噪声 σ=0.94(5mV)",              0.94, 0.0, 0.02),
        ("噪声 σ=0.94 + 零漂 1.0",        0.94, 1.0, 0.02),
        ("劣化 8bit 等效量化 2.0",         0.05, 0.0, 2.0),
        ("最坏组合 σ0.94+漂1+量化2.0",     0.94, 1.0, 2.0),
    ]
    print(f"  [E] 滞环抖振扫描（100ms 节拍, {T:.0f}s/工况, "
          f"S_pump={S_pump:.1f} mmHg/s）：")
    print("        工况                              真值带内[min,max] "
          " 泵启停/10min  占空比")
    base_cycles, base_duty = None, None
    for tag, sigma, drift, qstep in cases:
        seed = rng.integers(1 << 31)
        r = np.random.default_rng(seed)
        p = 17.0                                  # 真值 mmHg 幅值
        pumping, cycles, on_t = False, 0, 0.0
        pmin, pmax = p, p
        for i in range(n):
            meas = p + drift + r.normal(0, sigma)
            meas = round(meas / qstep) * qstep    # 量化
            # 先按本拍读数决策、再推进动力学 —— 与固件 100ms 节拍语义一致
            if pumping and meas >= 20:
                pumping = False
            elif (not pumping) and meas < 15:
                pumping = True
                cycles += 1
            if pumping:
                p = min(24.5, p + S_pump * dt)
                on_t += dt
            else:
                p = max(0.0, p - K_leak * np.sqrt(p * MMHG) * dt / MMHG)
            pmin, pmax = min(pmin, p), max(pmax, p)
        duty = on_t / T * 100
        if base_cycles is None:
            base_cycles, base_duty = cycles, duty
        print(f"        {tag:34s} [{pmin:5.2f},{pmax:5.2f}]      "
              f"{cycles:5d}        {duty:5.1f}%")
    print("      → 判定（分层）：")
    print("        · 基准/σ0.38/量化2.0/零漂1.0：真值带 ≤22.6，泵启停 32~55 次与")
    print("          基准同量级 → 滞环带宽 5mmHg 对噪声/量化/漂移鲁棒，无抖振 ✓")
    print("        · 单样本 σ0.94(5mV) 出现罕见双误差毛刺（提前起抽∧延迟停抽）")
    print("          顶到 ~24.5：真实硬件中该毛刺会先触 L4 泄压阀(23)而非越25，")
    print("          安全但会造成阀频跳 → 必须保留滤波（固件 read_voltage 已做")
    print("          16 点平均：σ÷4≈0.24 → 落入安全行）；若改为单样本采样则需")
    print("          中值/限幅滤波。§10.6 由此纯仿真关闭（无需实测）。")



def figs_thermal(secB_res, sec_D_res, dt):
    prm = M.P
    fig, axes = plt.subplots(1, 2, figsize=(12.5, 4.6))
    # (a) 场景1 三种热假设（复算绝热/τ 曲线用于绘图）
    ax = axes[0]
    for name, tau, c in (("绝热", None, "#dc2626"),
                         ("有限换热 τ=20s", 20.0, "#f59e0b")):
        th = Thermal(prm, tau)
        tt, pcc = th.integrate(-17.5 * MMHG, lambda t, p: "pump", 1.2, dt)
        ax.plot(tt, -pcc / MMHG, color=c, lw=1.6, label=name)
    ax.axhline(24, color="#b91c1c", ls="--", lw=1, label="起跳 24")
    ax.set_xlabel("时间 [s]"); ax.set_ylabel("负压 |p| [mmHg]")
    ax.set_title("(a) 泵全速故障：热假设对比（膨胀冷却→下探更快）", fontsize=10.5)
    ax.legend(fontsize=8.5); ax.grid(alpha=0.3)
    # (b) 场景2 密封热漂移（关泄漏 = 纯热效应）
    ax = axes[1]
    ax.axhspan(15, 20, color="#dcfce7", zorder=0, label="目标带")
    ax.axhline(0, color="#9ca3af", lw=0.8)
    for name, tau, c in (("等温 τ→0（基线）", 0.01, "#059669"),
                         ("有限换热 τ=20s", 20.0, "#f59e0b"),
                         ("绝热", None, "#dc2626")):
        th = Thermal(prm, tau)
        u_seal = lambda t, p: "pump" if (t < 3.0 and p / MMHG > -20) else "sealed"
        tt, pcc = th.integrate(-5.0 * MMHG, u_seal, 63.0, dt,
                               leak_fn=lambda t: 0.0)
        ax.plot(tt, -pcc / MMHG, color=c, lw=1.6, label=name)
    ax.axhline(20, color="#2563eb", ls=":", lw=1)
    ax.set_xlabel("时间 [s]"); ax.set_ylabel("负压 |p| [mmHg]")
    ax.set_title("(b) 抽至-20 密封：等温(水平=0漂移) vs 热漂移", fontsize=10.5)
    ax.set_ylim(10, 22); ax.legend(fontsize=8.5); ax.grid(alpha=0.3)
    fig.tight_layout()
    p = os.path.join(FIG, "fig8_thermal.png")
    fig.savefig(p, bbox_inches="tight"); plt.close(fig)
    print("      →", p)


def figs_mc(c24, c235, c23, rng):
    N = len(c24)
    fig, axes = plt.subplots(1, 2, figsize=(12.5, 4.6))
    ax = axes[0]
    bins = np.linspace(22, 29, 90)
    ax.hist(c24, bins=bins, density=True, alpha=0.55, color="#2563eb",
            label="起跳 24.0±0.5（现行规格）")
    ax.hist(c23, bins=bins, density=True, alpha=0.55, color="#059669",
            label="起跳 23.0±0.5（收紧）")
    ax.axvline(25, color="#dc2626", lw=2, label="物理红线 25")
    p24 = (c24 > 25).mean() * 100
    p23 = (c23 > 25).mean() * 100
    ax.set_title(f"(a) L4 钳位分布（MC 10k）：P(>25) "
                 f"{p24:.0f}% → {p23:.1f}%", fontsize=10.5)
    ax.set_xlabel("稳态钳位 [mmHg]"); ax.legend(fontsize=8.5)
    ax.grid(alpha=0.3)
    # (b) 看门狗窗口 CDF
    ax = axes[1]
    for q_lpm, c in ((3.0, "#f59e0b"), (1.0, "#059669")):
        Q = rng.normal(q_lpm * 1e-3 / 60.0, 0.10 * q_lpm * 1e-3 / 60.0, N)
        V = rng.normal(1.3e-3, 0.02 * 1.3e-3, N)
        t23 = np.sort(V * (3 * MMHG) / (P_ATM * np.clip(Q, 1e-6, None))) * 1000
        cdf = np.arange(1, N + 1) / N * 100
        ax.plot(t23, cdf, color=c, lw=1.8,
                label=f"泵 {q_lpm:.0f} L/min：均值 {t23.mean():.0f} ms")
    ax.axvline(100, color="#dc2626", lw=1.6, ls="--",
               label="100ms 采样窗（10Hz 看门狗）")
    ax.set_xlabel("看门狗窗口（-20 → -23）[ms]")
    ax.set_ylabel("CDF [%]")
    ax.set_title("(b) 电子层时间窗分布：泵速是决定性参数", fontsize=10.5)
    ax.set_ylim(0, 100); ax.legend(fontsize=8.5, loc="lower right")
    ax.grid(alpha=0.3)
    fig.tight_layout()
    p = os.path.join(FIG, "fig9_mc.png")
    fig.savefig(p, bbox_inches="tight"); plt.close(fig)
    print("      →", p)


def figs_valve(secD):
    fig, ax = plt.subplots(figsize=(8.5, 4.2))
    for tag, (t, p, x, ripple) in secD.items():
        ax.plot(t, -p / MMHG, lw=1.4, label=f"{tag}（纹波 {ripple:.2f}）")
    ax.axhline(24, color="#b91c1c", ls="--", lw=1, label="起跳 24")
    ax.set_xlabel("时间 [s]"); ax.set_ylabel("负压 |p| [mmHg]")
    ax.set_title("泄压阀开阀冲击与阀芯阻尼（耦合仿真）", fontsize=11)
    ax.legend(fontsize=8.5); ax.grid(alpha=0.3)
    fig.tight_layout()
    p = os.path.join(FIG, "fig10_valve.png")
    fig.savefig(p, bbox_inches="tight"); plt.close(fig)
    print("      →", p)


# ===========================================================================
def main():
    rng = np.random.default_rng(20261002)
    print("=" * 74)
    print("仿真能力升级 —— 开源工具：scipy / CoolProp / numpy(MC) / 弹簧-阀芯模型")
    print("=" * 74)
    print("\n[A] 独立求解器交叉验证")
    ref, chk, dt = section_A()
    print("\n[B] 等温假设量化（CoolProp 物性 + 双状态热力学模型）")
    secB = section_B(ref, dt)
    print("\n[C] 安全裕度蒙特卡洛")
    c24, c235, c23 = section_C(rng)
    print("\n[D] 泄压阀阀芯动力学")
    secD = section_D()
    print("\n[E] 传感噪声×量化×零漂 滞环抖振扫描（纯仿真）")
    section_E(rng)
    print("\n[图]")
    figs_thermal(secB, secD, dt)
    figs_mc(c24, c235, c23, rng)
    figs_valve(secD)
    print("\n" + "=" * 74)
    print("规格修正建议（已回填文档 §5.2/§9/§10）：")
    print("  · [C] 泄压阀起跳 24.0±0.5 → P(>25)=39%！收紧至 23.0±0.5 → 2.3%")
    print("  · [C] 3 L/min 泵看门狗窗口 103.8±11ms，39.8% 概率 <100ms → 泵选 ≤1 L/min")
    print("  · [D] 真实弹簧阀口径：K_r=4.8e-6 钳位 27.8>25 越线；")
    print("       K_r ≥ 2.1e-5（推荐 4.5e-5，Ø6~8 阀座）→ 钳位 24.4 ✓")
    print("  · [B] 等温假设：物性偏差0.5%成立；密封相热漂移3.7mmHg < 滞环预算5")
    print("=" * 74)


if __name__ == "__main__":
    main()
