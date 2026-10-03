// ============================================================================
// PCB 控制器外壳（ESP32 + 继电器模块 + 微差压传感器）
// 外形约 100 x 70 x 30 mm（上盖 + 底座两件，四角 M3 螺丝固定）
// 特征：顶面/侧面散热孔、四角螺丝柱、内部分区安装柱、气管出孔、电源线出孔
//
// ★★ 材料与安全要求（必须遵守）★★
// 1. 打印材料必须为【医用级光敏树脂】或【PETG】；
//    严禁普通 PLA（受热释放有害物质，且蠕变大导致密封面变形）。
// 2. 密封/缓冲件严禁天然橡胶（乳胶，致敏）；线缆护套用硅胶或 TPE。
// 3. 内部走气管的出孔必须光滑、配惰性护线圈，防止磨穿气管；
//    外壳内不得形成积水死角，底面预留 2mm 排水/透气小孔若干。
// 4. 纯台架验证原型，禁止用于人体。
// ============================================================================
// 用法：修改下方 part 变量后渲染
//   part = "assembly"  装配预览（上盖抬高 15mm 观察内部）
//   part = "base"      仅底座（打印用）
//   part = "lid"       仅上盖（打印用）
// ============================================================================

part = "assembly";   // "assembly" | "base" | "lid"

// ----------------------- 可调参数 -----------------------
$fn = 64;

// 外形
L = 100;             // 长 [mm]
W = 70;              // 宽 [mm]
H = 30;              // 总高 [mm]（底座25 + 上盖5，含扣合止口）
wall = 2;            // 壁厚 [mm]
base_h = 25;         // 底座高 [mm]
lid_h = 5;           // 上盖高 [mm]
lip = 3;             // 止口（扣合深度）[mm]

// 四角固定：M3
m3_hole_d  = 3.2;    // 上盖过孔
m3_boss_od = 8;      // 底座螺丝柱外径
m3_boss_h  = 18;     // 螺丝柱高（沉入底座内）
m3_thread_d = 2.5;   // 底座自攻底孔（或 4.2 换热熔铜螺母 M3）

// 安装孔距（按实际板卡调整）
esp_dx = 25.0;       // ESP32 安装孔 X 间距
esp_dy = 48.0;       // ESP32 安装孔 Y 间距
esp_hole_d = 2.4;    // M2.5 自攻
relay_dx = 34.0;     // 继电器模块安装孔 X 间距
relay_dy = 14.0;     // 继电器模块安装孔 Y 间距

// 开孔
tube_hole_d = 12;    // 气管束/传感器气嘴出孔（配护线圈）
pwr_hole_d  = 9;     // DC 电源线出孔

// 散热
vent_len = 26;       // 顶面散热槽长
vent_w   = 2.5;      // 顶面散热槽宽
vent_n   = 5;        // 顶面散热槽数量

corner_r = 4;        // 外角圆角

// ----------------------- 通用模块 -----------------------

// 圆角矩形板（拉伸）
module rrect(l, w, h, r) {
    linear_extrude(h)
        offset(r) offset(-r)
            square([l, w], center = true);
}

// M3 角柱 + 过孔位置（中心在四角）
corner_pos = [[ L/2 - wall - m3_boss_od/2 - 1,  W/2 - wall - m3_boss_od/2 - 1],
              [-(L/2 - wall - m3_boss_od/2 - 1), W/2 - wall - m3_boss_od/2 - 1],
              [-(L/2 - wall - m3_boss_od/2 - 1),-(W/2 - wall - m3_boss_od/2 - 1)],
              [ L/2 - wall - m3_boss_od/2 - 1, -(W/2 - wall - m3_boss_od/2 - 1)]];

// 内部安装柱（顶面带自攻孔）
module mount_boss(h, hole_d) {
    difference() {
        cylinder(d = 6, h = h);
        translate([0, 0, h - 5]) cylinder(d = hole_d, h = 5.1);
    }
}

// ----------------------- 底座 -----------------------
module base() {
    difference() {
        union() {
            // 外壳（无顶）
            difference() {
                rrect(L, W, base_h, corner_r);
                translate([0, 0, wall])
                    rrect(L - 2*wall, W - 2*wall, base_h, corner_r);
            }
            // 四角螺丝柱（自攻底孔）
            for (p = corner_pos)
                translate([p[0], p[1], wall])
                    difference() {
                        cylinder(d = m3_boss_od, h = m3_boss_h);
                        translate([0, 0, m3_boss_h - 12])
                            cylinder(d = m3_thread_d, h = 12.1);
                    }
            // ESP32 安装柱 x4（-3 偏移保证与内壁留 3mm，避免相切 → 非流形）
            for (sx = [-1, 1], sy = [-1, 1])
                translate([sx*esp_dx/2, sy*esp_dy/2 - 3, wall])
                    mount_boss(6, esp_hole_d);
            // 继电器安装柱 x4（+26 偏移保证不与外壁相切 → 非流形）
            for (sx = [-1, 1], sy = [-1, 1])
                translate([sx*relay_dx/2 + 26, sy*relay_dy/2 + 18, wall])
                    mount_boss(6, relay_hole_d);
        }
        // 气管/传感器气嘴出孔（侧壁，高度居中）
        translate([L/2 - wall - 5, -W/4, base_h * 0.6])
            rotate([0, 90, 0]) cylinder(d = tube_hole_d, h = 12);
        // 电源线出孔（对侧壁）
        translate([-L/2 + wall - 5, W/4, base_h * 0.6])
            rotate([0, 90, 0]) cylinder(d = pwr_hole_d, h = 12);
        // 侧面散热百叶 x N（两条长边；中心距壁外沿 -1，保证贯穿内外壁）
        for (i = [-2 : 2])
            translate([i * 18,  (W/2 - 1), base_h * 0.35])
                cube([10, 12, 4], center = true);
        for (i = [-2 : 2])
            translate([i * 18, -(W/2 - 1), base_h * 0.35])
                cube([10, 12, 4], center = true);
        // USB 烧录口（-X 侧壁开槽；位置按 ESP32 出线方向微调）
        translate([-L/2 + wall - 4, -16, wall + 7])
            cube([12, 11, 5.5], center = true);
        // 底面排水/透气微孔（防冷凝水积存，4 处）
        for (i = [-1, 1], j = [-1, 1])
            translate([i * 30, j * 18, -0.1])
                cylinder(d = 2, h = wall + 0.2);
        // 底部四角防滑脚垫浅窝（可贴硅胶垫，乳胶禁用）
        for (p = corner_pos)
            translate([p[0], p[1], -0.05])
                cylinder(d = 10, h = 1.2);
    }
}

relay_hole_d = 2.4;  // 继电器柱自攻孔

// ----------------------- 上盖 -----------------------
module lid() {
    difference() {
        union() {
            rrect(L, W, lid_h, corner_r);
            // 止口（扣入底座内沿）
            translate([0, 0, -lip])
                difference() {
                    rrect(L - 2*wall - 0.6, W - 2*wall - 0.6, lip + 0.1, corner_r);
                    rrect(L - 2*wall - 3.2, W - 2*wall - 3.2, lip + 0.2,
                          max(0.1, corner_r - 1));
                }
        }
        // 四角 M3 过孔（沉头）
        for (p = corner_pos) {
            translate([p[0], p[1], -lip - 0.1]) cylinder(d = m3_hole_d, h = lid_h + lip + 0.2);
            translate([p[0], p[1], lid_h - 1.7]) cylinder(d1 = m3_hole_d, d2 = 6.2, h = 1.8);
        }
        // 顶面散热槽
        for (i = [0 : vent_n - 1])
            translate([-(vent_n-1)*9/2 + i*9, 8, -0.1])
                hull() {
                    translate([-vent_len/2 + vent_w/2, 0, 0])
                        cylinder(d = vent_w, h = lid_h + 0.2);
                    translate([ vent_len/2 - vent_w/2, 0, 0])
                        cylinder(d = vent_w, h = lid_h + 0.2);
                }
        // 顶面状态灯窗（红/绿 LED），Ø5 透光孔
        translate([-L/2 + 14, -W/2 + 14, -0.1])
            cylinder(d = 5, h = lid_h + 0.2);
    }
}

// ----------------------- 总装 -----------------------
if (part == "base")       base();
else if (part == "lid")   lid();
else {
    base();
    translate([0, 0, base_h + 15]) lid();   // 装配预览（抬高观察）
}
