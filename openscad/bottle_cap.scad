// ============================================================================
// 缓冲瓶瓶盖接头（500ml 塑料瓶，螺纹口外径约 28mm）
// 特征：盖体 + 简化单头螺纹（旋紧在瓶口）+ 密封圈槽 + 两个 6mm 宝塔气管口
// 坐标约定：开口朝下（z=0 为盖口平面），顶面为闭合面（宝塔口从顶面伸出）
//
// ★★ 材料与安全要求（必须遵守）★★
// 1. 打印材料必须为【医用级光敏树脂】或【PETG】；
//    严禁普通 PLA：蠕变大 → 螺纹预紧力衰减 → 慢漏气，且可能释放有害物。
// 2. 密封圈严禁天然橡胶（乳胶，致敏）—— 请用铂金硫化硅胶 O 形圈
//    （槽型见参数 gasket_*），或在盖口涂少量惰性真空脂。
// 3. 盖内两气口的交汇腔用球面 hull 生成，全圆角无死角，防止冷凝水滞留；
//    装配后气管走向不得形成存水弯。
// 4. 纯台架验证原型，禁止用于人体。
// ============================================================================
// 用法：直接渲染；瓶口参数按实际瓶型测量后修改 neck_* 变量。
// ============================================================================

$fn = 96;

// ----------------------- 瓶口参数（按实物测量） -----------------------
neck_thread_od  = 28.0;   // 瓶口螺纹外径 [mm]
neck_od         = 26.5;   // 瓶口光滑段/唇部外径（密封圈接触）[mm]
neck_h          = 16;     // 瓶口露出高度（含螺纹）[mm]
pitch           = 3.0;    // 螺纹导程 [mm]（常见 PCO 类约 2.8~3.2）
fit_clearance   = 0.4;    // 打印/装配间隙 [mm]

// ----------------------- 盖体参数 -----------------------
cap_wall        = 3.0;    // 盖壁厚 [mm]
cap_top_t       = 4.0;    // 顶面厚 [mm]
skirt_h         = neck_h + 3;         // 盖裙高 [mm]（容纳瓶口）
gasket_groove_w = 2.5;    // 密封圈槽宽 [mm]（Ø2.5 铂金硅胶绳/O 形圈）
gasket_groove_d = 1.6;    // 密封圈槽深 [mm]
gasket_z        = skirt_h - 2;        // 密封槽中心高度（压在瓶口唇部区域）
grip_flutes     = 12;     // 盖外缘防滑齿数
grip_depth      = 1.2;    // 防滑齿深 [mm]

// ----------------------- 气管接口 -----------------------
tube_od         = 6.0;    // 医用软管外径 [mm]（仅标识）
tube_id         = 4.0;    // 医用软管内径 [mm] ★宝塔按内径设计（插入端基准）
bore_d          = 4.0;    // 顶面贯通气道 [mm]（与管内径一致）
barb_bore       = tube_id - 0.4;   // 宝塔气道 Ø3.6（壁厚≥0.45）
barb_len        = 12;     // 宝塔段长 [mm]
port_spacing    = 12;     // 两接口中心距 [mm]
sensor_boss_d   = 4.0;    // ★P0 传感器取压口直径 [mm]（闭环反馈接入点）
relief2_d       = 2.0;    // ★第二泄压阀预留口 [mm]（FMEA 双阀串联冗余）
side_port_y     = 9.5;    // 侧口在顶面的 Y 向偏置 [mm]

cap_od  = neck_od + 2 * (fit_clearance + cap_wall);  // 盖外径 36.1
cap_id  = neck_od + 2 * fit_clearance;               // 盖内径 27.3
H_total = skirt_h + cap_top_t;                       // 盖总高

// ----------------------- 模块 -----------------------

// 扇形（OpenSCAD 无内置，多边形近似；ang 以 0° 为中心）
module pie_slice(r, ang) {
    steps = max(2, floor(ang / 6));
    polygon(concat([[0, 0]],
        [for (i = [0 : steps])
            [r * cos(ang/2 - i * ang/steps),
             r * sin(ang/2 - i * ang/steps)]]));
}

// 简化单头内螺纹：扇形环截面扭转拉伸，从盖内壁减去形成螺纹槽。
// 扭转 360°/pitch → 螺纹肋（未被切去的部分）落入瓶口外螺纹的牙槽。
// 矩形牙型：仅供旋紧定位与轴向防脱，气密性由 O 形圈负责。
module thread_cutter() {
    rib_r     = cap_id / 2 - 0.4;           // 槽底深入容纳腔 0.4mm——避免与腔壁
                                            // 半径共面（共面会被 CGAL 切出碎屑）
    rib_h     = 2.4;                        // 螺纹槽深（含上述偏移，覆盖瓶口牙冠）
    rib_w_deg = 100;                        // 槽的角宽（剩余 260° 为盖内螺纹肋）
    h_start   = 1.5;                        // 螺纹区起点（距盖口）
    h_end     = gasket_z - 1.5;             // 螺纹区终点（密封槽下方留 1.5mm）
    translate([0, 0, h_start])
        linear_extrude(height = h_end - h_start,
                       twist   = -360 * (h_end - h_start) / pitch,
                       slices  = 200)
            difference() {
                circle(r = rib_r + rib_h);
                circle(r = rib_r);
                pie_slice(rib_r + rib_h + 1, 360 - rib_w_deg);
            }
}

// 6mm 宝塔接头（锥形倒刺，抓牢软管）
module barb() {
    difference() {
        union() {
            // ★按管内径 tube_id：芯锥 4.8→4.5、齿 5.2→4.5（+30% 齿过盈）
            cylinder(d1 = tube_id + 0.8, d2 = tube_id + 0.5, h = barb_len);
            for (i = [0 : 2])
                translate([0, 0, 2.5 + i * (barb_len - 4) / 3])
                    cylinder(d1 = tube_id + 1.2, d2 = tube_id + 0.5, h = 2.0);
        }
        // 宝塔气道 Ø3.6 + 底部锥形过渡接顶面气道 Ø4（无台阶死角）
        translate([0, 0, -0.3])
            cylinder(d = barb_bore, h = barb_len + 1);
        translate([0, 0, -0.45])
            cylinder(d1 = bore_d, d2 = barb_bore, h = 1.6);
        // 出口倒角
        translate([0, 0, barb_len - 0.7])
            cylinder(d1 = barb_bore, d2 = barb_bore + 0.8, h = 1.2);
    }
}

// 盖体
module cap_body() {
    difference() {
        // 外坯（含防滑齿）
        union() {
            cylinder(d = cap_od, h = H_total);
            for (i = [0 : grip_flutes - 1])
                rotate([0, 0, i * 360 / grip_flutes])
                    translate([cap_od/2 - grip_depth + 0.6, 0, H_total/2])
                        cube([grip_depth*2, 3, H_total], center = true);
            // ★侧口凸台（嵌入顶面 0.5mm）：传感器取压 + 第二泄压预留
            translate([0,  side_port_y, H_total - 0.5])
                cylinder(d = sensor_boss_d + 5, h = 3);
            translate([0, -side_port_y, H_total - 0.5])
                cylinder(d = relief2_d + 5, h = 3);
        }
        // 瓶口容纳腔（开口朝下，顶面留 cap_top_t）
        translate([0, 0, -0.1])
            cylinder(d = cap_id, h = skirt_h + 0.1);
        // 密封圈槽（切入内壁 1.6mm 深；起点略入空腔 0.2 保证布尔干净）
        translate([0, 0, gasket_z - gasket_groove_w/2])
            rotate_extrude()
                translate([cap_id/2 - 0.2, 0, 0])
                    square([gasket_groove_d + 0.4, gasket_groove_w]);
        // 内螺纹槽
        thread_cutter();
        // 两气管贯通孔（顶面钻穿）
        for (sx = [-1, 1])
            translate([sx * port_spacing/2, 0, skirt_h - 0.1])
                cylinder(d = bore_d, h = cap_top_t + 0.2);
        // ★P0 传感器取压口：贯穿顶面直通瓶内压力节点（闭环反馈接入点）
        translate([0,  side_port_y, skirt_h - 0.1])
            cylinder(d = sensor_boss_d, h = cap_top_t + 3.2);
        // ★第二泄压阀预留口（FMEA 双阀串联消共因，可按需安装）
        translate([0, -side_port_y, skirt_h - 0.1])
            cylinder(d = relief2_d, h = cap_top_t + 3.2);
        // 盖内交汇腔：球面 hull，全圆角无死角，
        // 连接两气孔并略微下探入瓶口腔（球顶进入顶板 0.3mm，避免点相切）
        hull() {
            for (sx = [-1, 1])
                translate([sx * port_spacing/2, 0, skirt_h - 0.7])
                    sphere(d = bore_d + 2);
        }
        // （原顶部沉孔已移除：它会把宝塔落脚的顶面掏空 1.5mm，
        //   使宝塔悬空无法与盖体合并成一个实体）
    }
}

// ----------------------- 总装 -----------------------
module bottle_cap() {
    cap_body();
    // 宝塔下沉 0.2mm 嵌入盖顶（零重叠面接触会断成多个壳体）
    for (sx = [-1, 1])
        translate([sx * port_spacing/2, 0, H_total - 0.2])
            barb();
}

bottle_cap();

// ----------------------- 装配说明 -----------------------
// 1. 铂金硅胶 O 形圈（Ø2.5 线径）放入盖内壁槽，瓶口旋入至压紧密封。
// 2. 两根 6mm 医用软管接宝塔口：一通电磁阀/泵侧，一通缓冲瓶下游。
// 3. 检漏：抽到 -40 kPa 静置 60 s，压降 < 1 kPa 为合格。
