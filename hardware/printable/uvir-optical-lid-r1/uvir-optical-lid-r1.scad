// All dimensions in mm. Top is +Y; viewed from the tube side.
// 1 mm sensor-to-lid air gap is not part of the printed solid.
width = 65.4;
height = 120.3;
lid = 2.4;
projection = 9;
bore = 3;
wall = 4;
$fn = 192;
centres = [[17.6, height - 41.9], [width - 20.9, height - 32]];
difference() {
    union() {
        cube([width, height, lid]);
        for (p = centres)
            translate([p[0], p[1], lid - 0.1])
                cylinder(h=projection + 0.1, d=bore + 2 * wall);
    }
    for (p = centres)
        translate([p[0], p[1], -1])
            cylinder(h=lid + projection + 2, d=bore);
}
