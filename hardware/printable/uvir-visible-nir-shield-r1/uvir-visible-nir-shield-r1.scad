// Units: mm. Four walls, open at both ends. No assembly clearance added.
inner_width = 13;
inner_length = 21;
height = 14;
wall = 1.2;
difference() {
    cube([inner_width + 2*wall, inner_length + 2*wall, height]);
    translate([wall, wall, -1])
        cube([inner_width, inner_length, height + 2]);
}
