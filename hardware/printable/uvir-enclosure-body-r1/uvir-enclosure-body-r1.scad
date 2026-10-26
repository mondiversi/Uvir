// UVIR independent printable body, based on published COFFER-A/6 dimensions.
// Units: mm. This is not official TEKO CAD. No lid is included.
// See README for published dimensions versus reconstructed print details.
width = 70;
length = 125;
height = 35;
wall = 2.2;
floor_thickness = 2.2;
plan_radius = 5;
bottom_radius = 2;
lid_seat_z = 32.5;
guide_top_z = 31.4;
post_diameter = 5.5;
pilot_diameter = 3;
screw_inset = 7;

// Adjustable print details, not fully dimensioned by the manufacturer's drawing.
ledge = 1.2;
web = 1.2;
pilot_depth = 12;
guide_width = 1.8;
guide_projection = 1.8;
guide_pitch = 3.6;
guide_end_clearance = 10;
guides_enabled = true;
quarter_segments = 48;
bottom_segments = 16;
$fn = 96;

function ring(inset, z) =
    let(r = plan_radius - inset,
        cx = [width-plan_radius, width-plan_radius, plan_radius, plan_radius],
        cy = [plan_radius, length-plan_radius, length-plan_radius, plan_radius])
    [for(k=[0:3]) for(i=[0:quarter_segments-1])
        let(a=-90+90*k+90*i/(quarter_segments-1))
        [cx[k]+r*cos(a), cy[k]+r*sin(a), z]];

module rounded_outer_body() {
    n = quarter_segments*4;
    levels = concat([for(i=[0:bottom_segments])
        let(a=90*i/bottom_segments)
        [bottom_radius*(1-sin(a)), bottom_radius*(1-cos(a))]], [[0,height]]);
    nr = len(levels);
    points = concat([for(v=levels) each ring(v[0],v[1])],
                    [[width/2,length/2,0], [width/2,length/2,height]]);
    // OpenSCAD polyhedron faces use clockwise winding when viewed from outside.
    sides = [for(k=[0:nr-2]) for(i=[0:n-1])
        let(j=(i+1)%n) each [[k*n+i,(k+1)*n+j,k*n+j],
                             [k*n+i,(k+1)*n+i,(k+1)*n+j]]];
    bottom = [for(i=[0:n-1]) [nr*n,i,(i+1)%n]];
    top = [for(i=[0:n-1]) [nr*n+1,(nr-1)*n+(i+1)%n,(nr-1)*n+i]];
    polyhedron(points,concat(sides,bottom,top),convexity=10);
}

function positions(total) =
    let(n=floor((total-2*guide_end_clearance)/guide_pitch)+1,
        first=(total-(n-1)*guide_pitch)/2)
    [for(i=[0:n-1]) first+i*guide_pitch];

module shell() {
    difference() {
        rounded_outer_body();
        translate([wall,wall,floor_thickness])
            cube([width-2*wall,length-2*wall,height+1]);
    }
}

module lid_support() {
    difference() {
        translate([wall-0.1,wall-0.1,lid_seat_z-ledge])
            cube([width-2*wall+0.2,length-2*wall+0.2,ledge]);
        translate([wall+ledge,wall+ledge,lid_seat_z-ledge-0.1])
            cube([width-2*(wall+ledge),length-2*(wall+ledge),ledge+0.2]);
    }
}

module posts_and_webs() {
    for(x=[screw_inset,width-screw_inset])
        for(y=[screw_inset,length-screw_inset]) {
            xwall=x<width/2 ? wall-0.1 : width-wall+0.1;
            ywall=y<length/2 ? wall-0.1 : length-wall+0.1;
            translate([x,y,floor_thickness-0.1])
                cylinder(h=lid_seat_z-floor_thickness+0.1,d=post_diameter);
            translate([min(x,xwall),y-web/2,floor_thickness-0.1])
                cube([abs(x-xwall),web,lid_seat_z-floor_thickness+0.1]);
            translate([x-web/2,min(y,ywall),floor_thickness-0.1])
                cube([web,abs(y-ywall),lid_seat_z-floor_thickness+0.1]);
        }
}

module guides() {
    if(guides_enabled) {
        for(y=positions(length)) {
            translate([wall-0.1,y-guide_width/2,floor_thickness-0.1])
                cube([guide_projection+0.1,guide_width,guide_top_z-floor_thickness+0.1]);
            translate([width-wall-guide_projection,y-guide_width/2,floor_thickness-0.1])
                cube([guide_projection+0.1,guide_width,guide_top_z-floor_thickness+0.1]);
        }
        for(x=positions(width)) {
            translate([x-guide_width/2,wall-0.1,floor_thickness-0.1])
                cube([guide_width,guide_projection+0.1,guide_top_z-floor_thickness+0.1]);
            translate([x-guide_width/2,length-wall-guide_projection,floor_thickness-0.1])
                cube([guide_width,guide_projection+0.1,guide_top_z-floor_thickness+0.1]);
        }
    }
}

difference() {
    union() { shell(); lid_support(); posts_and_webs(); guides(); }
    for(x=[screw_inset,width-screw_inset])
        for(y=[screw_inset,length-screw_inset])
            translate([x,y,lid_seat_z-pilot_depth])
                cylinder(h=pilot_depth+0.1,d=pilot_diameter);
}
