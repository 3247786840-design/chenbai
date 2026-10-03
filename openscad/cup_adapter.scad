// ============================================================================
// 硅胶罩杯转接头 (Cup Adapter)
// 一端：直径 8cm 半球形硅胶罩杯的插接端（带防脱倒刺与绑扎槽）
// 另一端：外径 6mm 医用软管宝塔接头
// 侧壁：预留 Ø2mm 小孔，用于压入微压机械泄压阀（针阀/微弹子阀）
//
// ★★ 材料与安全要求（必须遵守）★★
// 1. 打印/成型材料必须为【医用级光敏树脂（通过生物相容性测试）】或【PETG】。
//    严禁使用普通 PLA：其受热/受潮会释放小分子物质，不适用于任何
//    可能接触冷凝水或接近人体的气路。
// 2. 密封件严禁天然橡胶（乳胶）—— 引发过敏。请使用铂金硫化硅胶
//    或 EPDM/FKM等惰性材质密封圈。
// 3. 气路内壁不得有死角（dead leg）：所有内腔过渡必须圆滑，
//    禁止出现会滞留冷凝水的凹槽/台阶。本件内孔为一贯通光滑直孔。
// 4. 本项目为纯台架验证原型，禁止用于人体。
// ============================================================================

// ----------------------- 可调参数 -----------------------
$fn = 96;

cup_inner_d      = 80;    // 硅胶罩杯内径 [mm]
cup_spigot_od    = 22;    // 插入罩杯开口的插头外径 [mm]（硅胶弹性包覆）
cup_spigot_len   = 18;    // 插头长度 [mm]
tube_od          = 6;     // 医用软管外径 [mm]（仅作管材标识，不参与宝塔尺寸）
tube_id          = 4;     // 医用软管内径 [mm] ★宝塔按【内径】设计（插入端基准）
wall             = 2;     // 壁厚 [mm]（题目要求）
relief_hole_d    = 2;     // 机械泄压阀安装孔直径 [mm]
body_od          = 16;    // 中段本体外径 [mm]
bore_d           = 4;     // 主气道 Ø4 = 管内径（★必须 ≤ 宝塔基环外径Ø4.8，
                          //   否则顶面孔大于宝塔搭接圈、两者无法合并成一体）
barb_len         = 14;    // 宝塔段长度 [mm]
barb_ridges      = 3;     // 宝塔倒刺数量
barb_bore        = tube_id - 0.4;  // 宝塔段气道 Ø3.6（壁厚≥0.45，与管内径匹配）
tie_groove_d     = 1.6;   // 绑扎沟槽深度 [mm]（真凹槽，切进锥面）

// ----------------------- 基础模块 -----------------------

// 光滑贯通内孔（纯直孔；倒角必须放在各部件自身的局部坐标里，
// 否则会跟随调用位置误切其他部件——本版曾因此把宝塔底环吃掉 0.34mm）
module bore() {
    translate([0, 0, -0.1])
        cylinder(d = bore_d, h = 400);
}

// 罩杯插接头：锥度插入 + 两级倒刺 + 绑扎沟槽
module cup_spigot() {
    difference() {
        union() {
            // 锥形插入段（大端朝本体，便于插入硅胶开口）
            cylinder(d1 = cup_spigot_od - 3, d2 = cup_spigot_od,
                     h = cup_spigot_len);
            // 防脱倒刺两级（软管卡在倒刺后方）
            for (i = [1, 2])
                translate([0, 0, cup_spigot_len - i * 4])
                    cylinder(d1 = cup_spigot_od, d2 = cup_spigot_od - 3.5,
                             h = 2.2);
        }
        bore();
        // 入口倒角（本部件局部坐标 z≈0，消除台阶死角）
        translate([0, 0, -0.5])
            cylinder(d1 = bore_d + 2, d2 = bore_d, h = 1.2);
        // ★ 绑扎凹槽（真槽：从锥面切入 1.6mm，扎带落槽防滑脱）
        //   外环 d=cup_spigot_od+1.2 保证完全越过锥面，内环定槽底
        translate([0, 0, cup_spigot_len * 0.45])
            difference() {
                cylinder(d = cup_spigot_od + 1.2, h = 2.4);
                translate([0, 0, -0.1])
                    cylinder(d = cup_spigot_od - tie_groove_d * 3, h = 2.6);
            }
    }
}

// 宝塔接头：★ 按管【内径】tube_id 设计（插入端基准）
//   芯锥 4.8→4.5（+20~12% 过盈）、倒刺齿 5.2→4.5（齿尖 +30%，常规≤35%）
//   气道收窄至 Ø3.6 并以锥面接主气道 Ø6（无台阶死角）
module barb_6mm() {
    difference() {
        union() {
            cylinder(d1 = tube_id + 0.8, d2 = tube_id + 0.5, h = barb_len);
            for (i = [0 : barb_ridges - 1])
                translate([0, 0, 2.5 + i * (barb_len - 4) / barb_ridges])
                    cylinder(d1 = tube_id + 1.2, d2 = tube_id + 0.5, h = 2.0);
        }
        // 宝塔气道（Ø3.6）
        translate([0, 0, -0.3])
            cylinder(d = barb_bore, h = barb_len + 1);
        // 锥形缩径 Ø4→Ø3.6：完全落在本体内部（z 28.9..31.5），
        // 保证顶面接口孔 Ø≤4，宝塔基环(Ø4.8)与本体有实体搭接
        translate([0, 0, -2.9])
            cylinder(d1 = bore_d, d2 = barb_bore, h = 2.6);
        // 出口倒角
        translate([0, 0, barb_len - 0.7])
            cylinder(d1 = barb_bore, d2 = barb_bore + 0.8, h = 1.2);
    }
}

// 机械泄压阀侧向安装座：凸台 + Ø2 通孔直通气道（位于中段本体高度）
module relief_boss() {
    difference() {
        translate([body_od / 2 - 1, 0, cup_spigot_len + body_h / 2])
            rotate([0, 90, 0])
                cylinder(d = relief_hole_d + 5, h = 3.5);
        // Ø2 孔穿透至内气道（起点在气道中心线内，保证贯通无死角）
        translate([1, 0, cup_spigot_len + body_h / 2])
            rotate([0, 90, 0])
                cylinder(d = relief_hole_d, h = 15);
    }
}

// ----------------------- 总装 -----------------------
body_h = 14;   // 中段圆柱本体高度 [mm]

module cup_adapter() {
    union() {
        cup_spigot();
        // 中段本体（泄压阀安装于此段侧壁）
        difference() {
            translate([0, 0, cup_spigot_len])
                cylinder(d = body_od, h = body_h);
            bore();
        }
        relief_boss();
        // 宝塔接头（下沉 0.2mm 嵌入本体顶面——零重叠的面接触会被
        // CGAL/导出器断成两个壳体，必须实体咬合）
        translate([0, 0, cup_spigot_len + body_h - 0.2])
            barb_6mm();
    }
}

cup_adapter();

// ----------------------- 制造提示 -----------------------
// - 建议竖直打印（插头朝下），内孔一次性成型，后处理仅打磨宝塔段。
// - 装配：插头涂铂金硅胶脂插入罩杯开口，扎带在沟槽处锁紧；
//   机械泄压阀涂少量惰性密封胶压入 Ø2 孔，阀口朝外（通大气侧）。
