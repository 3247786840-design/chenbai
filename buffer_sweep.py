# -*- coding: utf-8 -*-
"""
提示词一：气动系统建模与超压仿真 —— 缓冲瓶容积扫描（空间换时间 + 物理泄压底线）
============================================================================
场景：泵（2.5 L/min，极限 -60 kPa）因故障全速运转不停机，初始常压。
      缓冲瓶容积扫描 0 / 500 / 2000 ml（罩杯固定 300 ml）。
      ① 机械泄压阀在 -25 mmHg 开启（理想化：开启后压力保持恒定）；
      ② 对照组：没有泄压阀时压力会走到哪里（虚线，泵极限 -60 kPa）。

模型：理想气体状态方程 + 等温集总腔室
      V · dp/dt = -P_atm · Q_p        （p 为表压，负=负压；抽气使 p 下降）
      到达 -25 mmHg 后：dp/dt = 0    （泄压阀以“流入=流出”维持平台）

运行：python buffer_sweep.py   → 输出时间表 + figs/fig7_buffer_sweep.png
"""

import os
import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

MMHG = 133.322
P_ATM = 101325.0
Q_PUMP = 2.5 / 60 * 1e-3          # 2.5 L/min = 4.1667e-5 m^3/s
P_ULT = -60000.0                  # 泵极限真空（表压）
P_RELIEF = -25.0 * MMHG           # 机械泄压阀开启压力：-25 mmHg
V_CUP = 0.30e-3                   # 罩杯 300 ml
FIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figs",
                   "fig7_buffer_sweep.png")

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "sans-serif"]
plt.rcParams["axes.unicode_minus"] = False


def simulate(v_buffer, relief_on, t_end):
    """显式欧拉积分。返回 (t, p[Pa表压], 到达-25mmHg的时刻)"""
    V = V_CUP + v_buffer
    dt = 1e-4 if V < 1e-3 else 2e-4          # 小容积斜率大，步长更细
    n = int(t_end / dt)
    t = np.empty(n)
    p = np.empty(n)
    p0, t25 = 0.0, np.nan
    for i in range(n):
        # 泵有效抽速：接近极限真空时降额
        f = 1.0 - p0 / P_ULT if p0 < 0 else 1.0
        q = -Q_PUMP * max(0.0, f)
        if relief_on and p0 <= P_RELIEF:
            q = 0.0                            # 理想泄压阀：锁死平台
        p0 = p0 + dt * P_ATM * q / V
        if relief_on and p0 < P_RELIEF:
            p0 = P_RELIEF
        if np.isnan(t25) and p0 <= P_RELIEF:
            t25 = i * dt
        t[i] = i * dt
        p[i] = p0
    return t, p, t25


def main():
    os.makedirs(os.path.dirname(FIG), exist_ok=True)
    buffers = [(0, "0 ml"), (0.5e-3, "500 ml"), (2.0e-3, "2000 ml")]
    colors = ["#059669", "#2563eb", "#dc2626"]

    fig, ax = plt.subplots(figsize=(11, 6))
    print("=" * 78)
    print("缓冲瓶容积扫描：泵全速（2.5 L/min），从常压抽到 -25 mmHg")
    print("=" * 78)
    print(f"{'缓冲瓶':>8} {'总容积':>8} {'t(-25mmHg)':>12} {'解析值':>10} "
          f"{'无阀走到-60kPa':>16}")
    print("-" * 78)

    # 注释的错位排布（右上净空区，箭头指向各自钳位点）
    note_pos = [(2.4, -7.0), (3.5, -11.5), (4.8, -16.0)]
    for (vb, name), c, (nx, ny) in zip(buffers, colors, note_pos):
        V = V_CUP + vb
        t_end = 4.0 if vb < 1e-3 else 8.0
        # ① 有泄压阀
        t, p, t25 = simulate(vb, relief_on=True, t_end=t_end)
        # ② 无泄压阀（对照）
        t_n, p_n, _ = simulate(vb, relief_on=False,
                               t_end=V * abs(P_ULT) / (P_ATM * Q_PUMP) * 1.05)
        # 解析校核：t = V·Δp / (P_atm·Q)
        t_analytic = V * abs(P_RELIEF) / (P_ATM * Q_PUMP)
        t_ult = V * abs(P_ULT) / (P_ATM * Q_PUMP)

        ax.plot(t, p / MMHG, color=c, lw=2.0,
                label=f"缓冲瓶 {name}（有泄压阀）")
        ax.plot(t_n, p_n / MMHG, color=c, lw=1.3, ls="--", alpha=0.75,
                label=f"缓冲瓶 {name}（无阀对照）")
        ax.annotate(f"{name} → {t25:.2f} s", xy=(t25, -25),
                    xytext=(nx, ny), fontsize=9, color=c,
                    arrowprops=dict(arrowstyle="->", color=c, lw=1,
                                    shrinkA=2, shrinkB=3))
        print(f"{name:>8} {V*1e6:6.0f} ml {t25:10.2f} s {t_analytic:9.2f} s"
              f" {t_ult:14.1f} s")

    ax.axhline(-25, color="#b91c1c", lw=1.6, label="安全红线 -25 mmHg（机械泄压）")
    ax.axhline(-60_000 / MMHG, color="#6b7280", lw=1.0, ls=":",
               label="泵极限真空 -60 kPa ≈ -450 mmHg")
    ax.set_xlabel("时间 [s]")
    ax.set_ylabel("容器压力（表压） [mmHg]")
    ax.set_xlim(0, 8)
    ax.set_title("泵全速故障：缓冲瓶只改变到达红线的时间，不能改变最终平衡点\n"
                 "（实线=有机械泄压阀平顶钳位；虚线=无阀，一路走到泵极限）",
                 fontsize=12)
    ax.set_ylim(-70, 3)
    ax.legend(loc="center right", fontsize=9, ncol=2)
    ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.text(0.004, 0.995, "fig7_buffer_sweep.png", fontsize=7,
             color="#9ca3af", ha="left", va="top", family="monospace")
    fig.savefig(FIG, bbox_inches="tight")
    plt.close(fig)
    print("-" * 78)
    print(f"图已保存：{FIG}")

    # ---------------- 工程语言结论 ----------------
    print("""
【工程解释：为什么大缓冲瓶挡不住稳态超压，必须靠机械泄压阀】

1. 微分方程 V·dp/dt = -P_atm·Q 告诉我们：容积 V 只出现在分母。
   加缓冲瓶 = 把压力下降的斜率变缓（0.24 s → 0.63 s → 1.82 s 到达红线），
   它只改变“什么时候到”，改变不了“会不会到”。泵只要还在转，
   气体分子就持续净流出，dp/dt 永远不为零。

2. 稳态由“流量收支”决定，与容积无关。无泄压阀时，唯一的平衡点是
   泵抽不动为止 —— 即极限真空 -60 kPa（约 -450 mmHg，红线的 18 倍）。
   缓冲瓶 0→2000 ml 只是把走到 -60 kPa 的时间从 4.3 s 推到 32.7 s，
   终点一毫米都没变。且继电器粘连时没人会来关泵，时间换来的时间窗口
   没有“被谁利用”。

3. 机械泄压阀是系统里唯一能“制造新平衡点”的元件：压力触到 -25 mmHg，
   阀口被大气压顶开，外界空气以 ≥ 泵的流量灌入 —— 此时流量收支被强制
   归零，平衡点被物理地钉在开启压力上。它不依赖电、不依赖代码、
   不依赖容积大小，泵功率再大也只是让阀开得更大一点（背压略升）。

4. 结论：缓冲瓶买的是“秒级时间余量”（供看门狗/人反应），泄压阀买的是
   “稳态正确性”。前者是时间维度的冗余，后者是物理维度的底线，
   二者不可互相替代 —— 这就是“空间换时间，物理保底线”的数学含义。
""")


if __name__ == "__main__":
    main()
