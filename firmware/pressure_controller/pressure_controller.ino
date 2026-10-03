// ============================================================================
//  微负压闭环气动系统 —— ESP32 Fail-Safe 控制固件（Arduino 框架）
// ============================================================================
//  【免责声明】本固件仅用于台架/仿真验证（密封气球、硅胶假体、注水皮肤台架）。
//              未经任何生物学实验与临床验证，严禁用于人体。
//
//  硬件：
//    - 0-10 kPa 微差压传感器（XGZP101 类，模拟输出：0 kPa→0.5V，10 kPa→4.5V）
//    - 继电器 → 微型真空泵（12/24V DC）
//    - 常开型电磁阀（NO）：低电平=开（气路导通），PWM 调制抽气节奏
//        · 拓扑：泵 → 电磁阀 → 缓冲瓶 → 罩杯（阀串联在泵与缓冲瓶之间）
//        · 断电/复位时 GPIO 高阻 → 板级下拉 → 阀开 + 泵停 = 自动泄放（L3）
//    - 红色报警 LED；外部比较器（LM393，可选）→ 超压硬中断脚
//
//  代码组织（按要求：先配置安全，再执行控制）：
//    第 1 段  安全常量与引脚
//    第 2 段  传感器换算与合理性检查
//    第 3 段  安全执行器（所有故障路径的唯一出口 safe_state）
//    第 4 段  超压硬件中断 ISR（无视一切业务逻辑）
//    第 5 段  setup()：第一步就是安全态，最后才挂任务看门狗
//    第 6 段  loop()：快安全巡检(≈10ms) + 100ms 控制节拍
//
//  四层防护在本固件中的对应：
//    L1 滞环闭环 → control_tick()：15/20 mmHg 状态机 + 30% 闭阀占空比柔化
//    L2 看门狗   → ① 任务看门狗（防死循环）
//                  ② 巡检：≤-23 mmHg / 掉线 / ADC 卡死 → 强制断电动作
//                  ③ 外部比较器硬中断 ≤-25 mmHg（ISR 直接打安全态）
//    L3 断电泄放 → 所有故障汇入 safe_state()：泵停 + NO 阀开（与断电同构）
//    L4 机械泄压 → 固件之外的物理层；代码假定其“永远存在且不可被软件绕过”
//
//  压力约定：表压为负（-17.5 = 负压 17.5 mmHg），与设计文档一致。
//            越“小”（越负）负压越深；-25 比 -23 更危险。
// ============================================================================

// ★ 位于文件最顶部（先于所有 #include）的原因：
//   Arduino 预处理器会为每个函数自动生成原型并插到 #include 之后；
//   若枚举类型声明在函数定义之后，生成的 `void safe_state(Fault)` 原型
//   会因类型未定义而编译失败（.ino 社区已知陷阱，已实机编译复现）。
enum Fault { FAULT_NONE = 0, FAULT_OVERPRESS, FAULT_SENSOR, FAULT_STUCK };

#include <esp_task_wdt.h>
#include <esp_arduino_version.h>

// ============================================================================
// 第 1 段：安全常量与引脚
// ============================================================================

// ---- 压力阈值（mmHg 表压，负值）----
static const float P_START_MMHG  = -15.0f;  // 泵启动线：压力高于此（如 -14）→ 抽
static const float P_STOP_MMHG   = -20.0f;  // 泵停止线：达到 -20 → 停泵密封
static const float P_WATCHDOG    = -23.0f;  // L2：≤-23 强制断电泄放
static const float P_HARD_LIMIT  = -25.0f;  // 红线：≤-25 硬中断 + 软件兜底锁存

// ---- 时序 ----
static const uint32_t CTRL_PERIOD_MS = 100;   // 控制节拍 100ms（读取+决策）
static const uint32_t PUMP_MAX_ON_MS = 30000; // 泵单次连续运行上限 30s
static const uint32_t PUMP_REST_MS   = 10000; // 超时强制休息 10s
static const uint32_t VENT_PULSE_MS  = 15;    // >-20 时泄压脉冲宽（主动呼吸）
static const uint32_t VENT_PERIOD_MS = 300;   // 泄压脉冲周期（≈5% 等效占空比）
static const uint32_t STUCK_MAX_MS   = 5000;  // ADC 逐位不变 >5s → 卡死
static const uint32_t WDT_TIMEOUT_S  = 2;     // 任务看门狗 2s

// ---- PWM（对应设计文档“维持/抽气阶段 30% 闭阀占空比”）----
// NO 阀：analogWrite 值 = 闭阀(励磁)占空比。
// 抽气相输出 30% 闭合 ⇒ 仅 70% 时间导通 ⇒ 柔和渐进抽气（呼吸感）。
// 密封相必须 100% 闭合（255），否则系统经阀-停泵头缓慢漏气。
static const uint8_t PWM_SUCK_CLOSE_PCT = 30;

// ---- 引脚 ----
static const int PIN_PUMP   = 26;  // 继电器（RELAY_ON=开）
static const int PIN_VALVE  = 27;  // NO 电磁阀 PWM（LOW=开=安全态）
static const int PIN_SENSOR = 36;  // ADC1_CH0（避开 WiFi 的 ADC2）
static const int PIN_RED    = 2;   // 红色报警 LED
static const int PIN_COMP   = 4;   // 比较器超压中断（下降沿；未接则上拉恒高）

static const uint8_t RELAY_ON = HIGH; // 低电平触发继电器模块请改 LOW
static const uint8_t RELAY_OFF = LOW;
static const uint8_t VALVE_OPEN = LOW; // NO：LOW=开=断电同构安全态

// ---- 传感器换算：0-10 kPa ↔ 0.5-4.5V ↔ mmHg ----
static const float ADC_VREF      = 3.30f;
static const float ADC_FULL      = 4095.0f;
static const float SENSOR_V_MIN  = 0.5f;   // 0 kPa 输出
static const float SENSOR_V_MAX  = 4.5f;   // 10 kPa 输出
static const float SENSOR_KPA_FS = 10.0f;
static const float SENSOR_V_FAIL_HI = 4.5f; // 掉线判定上限（拉满/短路到 Vcc）
static const float SENSOR_V_FAIL_LO = 0.1f; // 掉线判定下限（断线/对地短路）
static const float KPA_TO_MMHG   = 7.5006f;

// ============================================================================
// 第 2 段：传感器换算与合理性检查
// ============================================================================

float read_voltage() {
  uint32_t acc = 0;
  for (int i = 0; i < 16; i++) acc += analogRead(PIN_SENSOR); // 16 次平均抑噪
  return (acc / 16.0f) * (ADC_VREF / ADC_FULL);
}

// 电压 → mmHg 表压（负值）。调用前先做掉线电压检查。
float pressure_from_voltage(float v) {
  float kpa = (v - SENSOR_V_MIN) / (SENSOR_V_MAX - SENSOR_V_MIN) * SENSOR_KPA_FS;
  return -kpa * KPA_TO_MMHG;          // 负表压：-17.5 表示负压 17.5 mmHg
}

// 读一次并做合理性检查：ok=false 表示读数不可信（掉线/超量程）
float read_pressure(bool *ok) {
  float v = read_voltage();
  if (v > SENSOR_V_FAIL_HI || v < SENSOR_V_FAIL_LO) { *ok = false; return 0.0f; }
  *ok = true;
  return pressure_from_voltage(v);
}

// ============================================================================
// 第 3 段：安全执行器（所有故障路径的唯一出口）
//   （enum Fault 已提前至文件顶部，见文件头注释）
// ============================================================================

volatile Fault g_fault    = FAULT_NONE;   // 故障锁存（置位后不自恢复，需复位）
volatile bool  g_hard_trip = false;       // 硬中断标志（ISR 置位）

void valve_open()   { digitalWrite(PIN_VALVE, VALVE_OPEN); }        // 导通=泄放/抽气路径
void valve_seal()   { analogWrite(PIN_VALVE, 255); }                // 全闭=密封（100% 励磁）
void valve_soft()   { analogWrite(PIN_VALVE,                       // 30% 闭=柔和抽气
                      (uint32_t)255 * PWM_SUCK_CLOSE_PCT / 100); }

// ★ 安全态：泵停 + 阀开（NO=通大气，自动泄压）——与“断电”同构。
//   上电第一行、所有故障、关机，全部经过这里。
void safe_state(Fault why) {
  g_fault = why;
  digitalWrite(PIN_PUMP, RELAY_OFF);
  valve_open();
}

// ============================================================================
// 第 4 段：超压硬件中断（无视所有当前逻辑）
// ============================================================================

// 比较器（LM393）把“真实负压 ≤ -25 mmHg”变成下降沿送入 PIN_COMP。
// 纯软件之外的电子兜底：ISR 只做两件事——停泵、开阀。不打印、不延时。
void IRAM_ATTR on_hard_overpressure() {
  digitalWrite(PIN_PUMP, RELAY_OFF);
  digitalWrite(PIN_VALVE, VALVE_OPEN);
  g_hard_trip = true;
}

// ---- 运行状态变量（须在 setup 之前声明）----
bool     g_pumping = false;
uint32_t g_pump_started_ms = 0;
uint32_t g_rest_until_ms = 0;          // 30s 超时后的强制休息截止时刻
uint32_t g_last_ctrl_ms = 0;
uint32_t g_last_vent_ms = 0;
uint32_t g_stuck_since_ms = 0;
uint32_t g_last_raw_adc = 0xFFFF;

// ============================================================================
// 第 5 段：setup —— 第一步永远是安全
// ============================================================================

void setup() {
  // ---- ① 执行器先打到安全态（先于一切其他初始化）----
  // 上电瞬间 GPIO 为高阻：板级务必为继电器/阀驱动加下拉，防上电误动。
  pinMode(PIN_PUMP, OUTPUT);
  pinMode(PIN_VALVE, OUTPUT);
  digitalWrite(PIN_PUMP, RELAY_OFF);
  digitalWrite(PIN_VALVE, VALVE_OPEN);   // 常开阀 = 开
  pinMode(PIN_RED, OUTPUT);
  digitalWrite(PIN_RED, LOW);

  // ---- ② 输入与硬中断 ----
  analogReadResolution(12);
  pinMode(PIN_SENSOR, INPUT);
  pinMode(PIN_COMP, INPUT_PULLUP);       // 未接比较器时恒高=不触发
  attachInterrupt(digitalPinToInterrupt(PIN_COMP), on_hard_overpressure, FALLING);

  // ---- ③ 任务看门狗（防代码死循环）----
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  esp_task_wdt_config_t cfg = {
    .timeout_ms = (uint32_t)WDT_TIMEOUT_S * 1000,
    .idle_core_mask = 0,
    .trigger_panic = true
  };
  esp_task_wdt_init(&cfg);
#else
  esp_task_wdt_init(WDT_TIMEOUT_S, true);
#endif
  esp_task_wdt_add(NULL);                // loop 任务挂上看门狗

  // ---- ④ 开机自检：传感器必须在合理范围，否则拒绝进入运行态 ----
  bool ok = false;
  read_pressure(&ok);
  g_fault = ok ? FAULT_NONE : FAULT_SENSOR;
  if (g_fault != FAULT_NONE) safe_state(g_fault); // 保持安全态，红灯慢闪

  // 控制状态变量
  g_pumping = false;
  g_pump_started_ms = 0;
  g_last_ctrl_ms = millis();
  g_last_vent_ms = millis();
  esp_task_wdt_reset();
}

// ============================================================================
// 第 6 段：loop —— 快安全巡检 + 100ms 控制节拍
// ============================================================================

// ---- 快速安全巡检：每轮 loop（≈10ms），独立于 100ms 控制 ----
void safety_tick() {
  if (g_hard_trip) { safe_state(FAULT_OVERPRESS); return; }

  float v = read_voltage();

  // 传感器掉线/超量程
  if (v > SENSOR_V_FAIL_HI || v < SENSOR_V_FAIL_LO) {
    safe_state(FAULT_SENSOR);
    return;
  }

  // ADC 卡死检测：读数逐位完全不变超过 5s → 短路/芯片失效（对 L2 的“隐身”故障）
  uint32_t raw_i = (uint32_t)(analogRead(PIN_SENSOR));
  if (raw_i == g_last_raw_adc) {
    if (g_stuck_since_ms == 0) g_stuck_since_ms = millis();
    else if (millis() - g_stuck_since_ms > STUCK_MAX_MS) { safe_state(FAULT_STUCK); return; }
  } else { g_last_raw_adc = raw_i; g_stuck_since_ms = 0; }

  float p = pressure_from_voltage(v);
  if (p <= P_WATCHDOG)  { safe_state(FAULT_OVERPRESS); return; }  // ≤-23：L2 动作
  if (p <= P_HARD_LIMIT){ safe_state(FAULT_OVERPRESS);           // ≤-25：软件兜底
                          g_hard_trip = true; return; }
}

// ---- 控制节拍：100ms ----
void control_tick() {
  if (g_fault != FAULT_NONE) return;          // 故障锁存：只闪灯，不做业务

  uint32_t now = millis();
  if (now < g_rest_until_ms) return;          // 30s 超时后的强制休息期

  bool ok = false;
  float p = read_pressure(&ok);               // 表压负值
  if (!ok) { safe_state(FAULT_SENSOR); return; }

  if (!g_pumping) {
    // ---------- 待机/密封相 ----------
    if (p > P_START_MMHG) {
      // 负压不足（如 -14）→ 启动柔和抽气
      valve_soft();                           // 30% 闭阀占空比 = 70% 导通
      digitalWrite(PIN_PUMP, RELAY_ON);
      if (g_pump_started_ms == 0) g_pump_started_ms = now;
      g_pumping = true;
    } else if (p > P_STOP_MMHG) {
      // 15~20 之间且未在抽：完全密封保持
      valve_seal();
      digitalWrite(PIN_PUMP, RELAY_OFF);
    } else {
      // 已深于 -20 却未在抽（例如刚从故障恢复）：开阀快速泄压回到带内
      valve_open();
      digitalWrite(PIN_PUMP, RELAY_OFF);
      g_last_vent_ms = now;
    }
  } else {
    // ---------- 抽气相 ----------
    // 30s 连续工作上限：超时强制停机休息
    if (now - g_pump_started_ms >= PUMP_MAX_ON_MS) {
      digitalWrite(PIN_PUMP, RELAY_OFF);
      valve_seal();
      g_pumping = false;
      g_pump_started_ms = 0;
      g_rest_until_ms = now + PUMP_REST_MS;
      return;
    }
    if (p <= P_STOP_MMHG) {
      // 达到 -20：停泵 → 主动呼吸泄压脉冲 → 回到带内
      digitalWrite(PIN_PUMP, RELAY_OFF);
      g_pumping = false;
      g_pump_started_ms = 0;
      valve_seal();
      // 每 VENT_PERIOD_MS 插入一个 VENT_PULSE_MS 的开阀脉冲，
      // 使压力带内缓慢回落（等效泄放时间常数 ×20，见注2），近似呼吸节奏
      if (now - g_last_vent_ms >= VENT_PERIOD_MS) {
        g_last_vent_ms = now;
        valve_open();
        delay(VENT_PULSE_MS);                // 15ms 开阀（本节拍内完成）
        valve_seal();
      }
    }
  }

  // 业务正常走到这里才喂狗 → 死循环/卡死不会喂 → 2s 复位（L2-①）
  esp_task_wdt_reset();
}

void loop() {
  // ---- 报警灯：故障锁存时闪烁（超压=快闪 100ms，其他=慢闪 500ms）----
  if (g_fault != FAULT_NONE) {
    uint32_t period = (g_fault == FAULT_OVERPRESS) ? 100 : 500;
    digitalWrite(PIN_RED, (millis() / period) % 2);
    esp_task_wdt_reset();                    // 报警态是有意的正常路径，仍喂狗
    return;
  }
  digitalWrite(PIN_RED, LOW);

  safety_tick();                             // 快巡检：每轮 loop（≈10ms）
  if (millis() - g_last_ctrl_ms >= CTRL_PERIOD_MS) {
    g_last_ctrl_ms = millis();
    control_tick();                          // 控制节拍：100ms
  }
}

// ============================================================================
// 注1：占空比语义（关键）
//   analogWrite(PIN_VALVE, x)：x = 闭阀(励磁)占空比。
//     抽气相 x=30% → 导通 70% → 平均抽速 70% Q_p（柔和）
//     密封相 x=100% → 完全截止（30% 会导致经阀-泵头缓慢漏气，不可用）
//     泄放/安全 x=0%  → 全开（NO 默认态=断电同构）
//
// 注2：为什么“>20 开阀快速泄压”改成 15ms/300ms 脉冲：
//   1L 缓冲瓶泄放时间常数 τ≈80ms，全开泄放 20→15mmHg 只需 ~20ms，
//   与 <15 重抽组合成 ~5Hz 压力抖动（建模报告 §5.4、仿真 fig5）。
//   脉冲化后等效 τ≈1.6s，回落平缓。
//
// 注3：上电防误动：ESP32 启动期 GPIO 浮空，板级必须给继电器驱动管加下拉；
//   复位/死机时 GPIO 回高阻 → 下拉生效 → 泵停阀开（与 safe_state 同构）。
//
// 注4：即便固件整体死机，仍有：
//   任务看门狗 2s 复位（L2-①）→ 比较器硬中断（L2-③）→ 断电泄放（L3）
//   → 机械泄压阀 25 mmHg 物理钳位（L4，仿真验证钳位 24.7）。
//
// 注5：本代码未启用 WiFi/蓝牙（ADC2 冲突 + 实时性）；若启用，传感器必须在 ADC1。
// ============================================================================
