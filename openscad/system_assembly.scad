// ============================================================================
// 系统总装串联验证（interface audit 可视化）
// 真实零件：cup_adapter / bottle_cap / pcb_enclosure(base+lid)
// 代理件：  硅胶罩杯、内4外6硅胶管、微差压传感器、缓冲瓶、常开电磁阀、真空泵
// 沿 +X 单线串联，所有接口按 BOM 实际尺寸；⚠/✓ 标注为审计结论。
// ============================================================================
use <cup_adapter.scad>
use <bottle_cap.scad>
use <pcb_enclosure.scad>

$fn = 64;

tube_id = 4;          // BOM：医用硅胶管 内4 / 外6
tube_od = 6;
cup_r   = 40;         // Ø80 半球罩杯
bottle_d = 62;        // 500ml 瓶
bottle_h = 165;
cap_top  = bottle_h + 23;     // 瓶盖顶（H_total=23）
barb_mid = cap_top + 7;       // 宝塔中段 ≈ 接管高度 195
side_y   = 9.5;               // 瓶盖传感器口 Y 偏置
cap_topz = bottle_h + 25.5;   // 传感器口凸台顶高度（23-0.5+3）

module label(txt, pos, sz = 4.5, col = "red") {
    translate(pos) linear_extrude(0.5)
        color(col) text(txt, size = sz, halign = "left", font = "Microsoft YaHei");
}

module tube(len) {
    %rotate([0, 90, 0]) difference() {          // % 背景透明：可见 Ø4 内径
        cylinder(d = tube_od, h = len);
        translate([0, 0, -1]) cylinder(d = tube_id, h = len + 2);
    }
}

// ---- 节点1：罩杯(代理) + 真实转接头（插头朝左入罩杯，宝塔朝右接管）----
module node_cup() {
    // 半球罩杯：x≤0 半球，平面贴在转接头端面 x=0
    intersection() {
        sphere(cup_r);
        translate([-cup_r - 1, -cup_r - 1, -cup_r - 1])
            cube([cup_r + 1, 2 * cup_r + 2, 2 * cup_r + 2]);
    }
    rotate([0, 90, 0]) cup_adapter();   // spigot x0..18 / 本体..32 / 宝塔..46
}

// ---- 节点2：传感器（旁挂，气嘴朝上顶到主线）----
module node_sensor() {
    color("#e5e7eb") cube([30, 25, 12], center = true);
    color("#9ca3af") translate([0, 0, 9]) cylinder(d = 4, h = 7);
}

// ---- 节点3：缓冲瓶(代理) + 真实瓶盖 —— 瓶身全透明，避免遮挡链路 ----
module node_bottle() {
    %color("#dbeafe") translate([0, 0, bottle_h/2])
        cylinder(d = bottle_d, h = bottle_h, center = true);
    translate([0, 0, bottle_h]) bottle_cap();
}

module node_valve() {
    color("#fef9c3") cube([42, 22, 26], center = true);
    // 进/出气嘴贴在左右壁面上（±21），不再悬空
    color("#9ca3af") translate([-21, 0, 0]) rotate([0, -90, 0]) cylinder(d = 5, h = 7);
    color("#9ca3af") translate([ 21, 0, 0]) rotate([0,  90, 0]) cylinder(d = 5, h = 7);
}
module node_pump() {
    color("#e9d5ff") cube([50, 35, 40], center = true);
    color("#9ca3af") translate([-25, 0, 0]) rotate([0, -90, 0]) cylinder(d = 6, h = 7);
}

// ================= 总装（x 坐标逐段对齐） =================
// 转接头宝塔端 x=46 → 主线 46..100 → 立管 → 瓶盖左口 → 瓶 → 瓶盖右口
// → 阀进嘴 → 泵；传感器接【瓶盖顶面第3口 Ø4】(P0修复)；控制盒旁置
node_cup();
translate([46, 0, 0])  tube(54);                 // 主线 46 → 100
translate([100, 0, 0]) rotate([0, -90, 0]) tube(barb_mid);   // 立管 0 → 195
translate([102, 0, barb_mid]) tube(12);          // 102 → 114 接入瓶盖左口
translate([120, 0, 0]) node_bottle();
translate([128, 0, barb_mid]) tube(49);          // 128 → 177 接阀进嘴尖
translate([205, 0, barb_mid]) node_valve();      // 阀体 184..226
translate([226, 0, barb_mid]) tube(14);          // 226 → 240 套住阀出嘴与泵进嘴
translate([265, 0, barb_mid]) node_pump();       // 泵体 240..290
// ★传感器：经短立管接在瓶盖顶面传感器口（y=+9.5, 孔Ø4）
translate([120, side_y, cap_topz]) rotate([0, -90, 0]) tube(10);
translate([120, side_y, cap_topz + 13]) rotate([180, 0, 0]) node_sensor();
// 控制盒移到链路【后方】(y=+95)，杜绝遮挡左侧链路
translate([70, 95, 0]) { base(); translate([0, 0, 45]) lid(); }

// ================= 审计标注（顶部双行横幅，不被几何遮挡） =================
row1 = 252;   row2 = 243;   row_y = 30;
label("✓ L4 泄压阀座贴罩杯端",      [-45, row_y, row1], 5.2, "green");
label("✓ 主线 Ø6 管径全链一致",      [90,  row_y, row1], 5.2, "green");
label("⚠ 阀/泵接口采购配对",         [210, row_y, row1], 5.2, "red");
label("✓ 宝塔按内径4重设计(齿5.2)",  [-45, row_y, row2], 5.2, "green");
label("✓ 传感器接盖顶第3口 Ø4",      [90,  row_y, row2], 5.2, "green");
label("✓ 双泄压预留口 Ø2",           [210, row_y, row2], 5.2, "green");
label("控制盒：散热贯穿 + USB 口",    [40,  150, 40], 5.2, "blue");
