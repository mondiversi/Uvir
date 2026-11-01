// Millimetres. R0.6 lid, 5 mm optical holes; same centres as R0.5.
$fn=256;
width=56.8; length=88.8; thickness=2.4;
optical_diameter=5.0; screw_diameter=2.4;
optical_holes=[[29.949999999999996, 79.79999999999998], [30.449999999999996, 66.30926243676797]];
screw_holes=[[2.5999999999999988, 2.5999999999999988], [54.199999999999996, 2.5999999999999988], [2.5999999999999988, 86.19999999999999], [54.199999999999996, 86.19999999999999]];
difference() {
  cube([width,length,thickness]);
  for(p=optical_holes) translate([p[0],p[1],-0.1]) cylinder(h=thickness+0.2,d=optical_diameter);
  for(p=screw_holes) translate([p[0],p[1],-0.1]) cylinder(h=thickness+0.2,d=screw_diameter);
}
