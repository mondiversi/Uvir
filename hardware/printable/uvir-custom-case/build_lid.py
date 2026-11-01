"""R0.6 flat lid: 5 mm optical holes, same centres as R0.5; millimetres."""
from pathlib import Path
import hashlib
import json
import math
import struct

import numpy as np

OUT = Path(__file__).resolve().parent
BASE = OUT
import manifold3d as m

p = json.loads((BASE / 'case-parameters.json').read_text())
W, L = p['future_square_lid_width_length']
T = p['future_lid_nominal_thickness']
BW, BL, _ = p['external_width_length_height']
OX, OY = (BW - W) / 2, (BL - L) / 2
APERTURE_D, SCREW_D, SEGMENTS, EPS = 5.0, 2.4, 256, .02
measurements = json.loads((OUT / 'sensor-measurements.json').read_text())
uv_x = measurements['uv_centre_x_from_board_left']
horizontal_offset = measurements['uv_right_of_visible_ir']
vis_x = uv_x - horizontal_offset
vis_down = measurements['visible_ir_centre_from_board_top']
interaxial_distance = measurements['centre_to_centre_distance']
assert measurements['uv_is_below_visible_ir']
assert interaxial_distance > abs(horizontal_offset)
vertical_separation = math.sqrt(interaxial_distance ** 2 - horizontal_offset ** 2)
sensor_board_centres = {'visible_ir': [vis_x, vis_down],
                        'uv': [uv_x, vis_down + vertical_separation]}
PCB_X, PCB_Y, _ = p['pcb_bottom_left_front_xyz']
_, PCB_L, _ = p['pcb_width_length_thickness']
centres = {key: [PCB_X + x - OX, PCB_Y + PCB_L - down - OY]
           for key, (x, down) in sensor_board_centres.items()}
assert math.isclose(centres['uv'][0] - centres['visible_ir'][0], .5, abs_tol=1e-9)
assert math.isclose(math.dist(*centres.values()), 13.5, abs_tol=1e-9)
screws = [[x - OX, y - OY] for x, y in p['lid_screw_centres_xy']]

def cylinder(x, y, diameter):
    return m.Manifold.cylinder(T + 2 * EPS, diameter / 2,
                              circular_segments=SEGMENTS).translate((x, y, -EPS))

cuts = [cylinder(x, y, APERTURE_D) for x, y in centres.values()]
cuts += [cylinder(x, y, SCREW_D) for x, y in screws]
lid = m.Manifold.cube((W, L, T))
lid = m.Manifold.batch_boolean([lid] + cuts, m.OpType.Subtract)
assert lid.status() == m.Error.NoError
assert len(lid.decompose()) == 1 and lid.genus() == 6

name = 'uvir-custom-case-lid-r06-5mm'
mesh = lid.to_mesh()
vertices, indices = np.unique(mesh.vert_properties[:, :3], axis=0, return_inverse=True)
faces = indices[mesh.tri_verts]
assert np.isfinite(vertices).all()
path = OUT / f'{name}.stl'
with path.open('wb') as stream:
    stream.write(b'Uvir flat lid R0.6 for custom R0.2 base; mm; 2x5mm holes; 4x2.4mm screws'.ljust(80, b' '))
    stream.write(struct.pack('<I', len(faces)))
    for face in faces:
        a, b, c = vertices[face].astype(np.float64)
        normal = np.cross(b - a, c - a)
        length = np.linalg.norm(normal)
        assert length > 1e-10
        stream.write(struct.pack('<12fH', *(normal / length), *a, *b, *c, 0))

def load_stl(path):
    data = path.read_bytes()
    n = struct.unpack_from('<I', data, 80)[0]
    assert len(data) == 84 + 50 * n
    rows = np.array([struct.unpack_from('<12fH', data, 84 + 50 * i)[:12]
                     for i in range(n)], dtype=np.float32)
    assert np.isfinite(rows).all()
    assert np.allclose(np.linalg.norm(rows[:, :3], axis=1), 1, atol=1e-5)
    v, inverse = np.unique(rows[:, 3:].reshape((-1, 3)), axis=0, return_inverse=True)
    f = inverse.reshape((-1, 3)).astype(np.uint32)
    solid = m.Manifold(m.Mesh(v, f))
    assert solid.status() == m.Error.NoError
    assert len(solid.decompose()) == 1
    return solid, v, f

checked, v, f = load_stl(path)
assert checked.genus() == 6
edges = np.sort(np.concatenate((f[:, [0, 1]], f[:, [1, 2]], f[:, [2, 0]])), axis=1)
unique_edges, counts = np.unique(edges, axis=0, return_counts=True)
assert np.all(counts == 2)
assert np.allclose(v.min(axis=0), (0, 0, 0), atol=.001)
assert np.allclose(v.max(axis=0), (W, L, T), atol=.001)
for x, y in centres.values():
    assert (checked ^ cylinder(x, y, APERTURE_D - .02)).volume() < 1e-6
base, _, _ = load_stl(BASE / 'uvir-custom-case-base-r03.stl')
assembled_lid = checked.translate((OX, OY, p['lid_seat_z']))
intersection = (assembled_lid ^ base).volume()
assert intersection < .001, intersection
spacing = math.dist(*centres.values())
assert math.isclose(spacing - APERTURE_D, 8.5, abs_tol=1e-9)
assert math.isclose(L - centres['visible_ir'][1] - APERTURE_D / 2, 6.5, abs_tol=1e-9)

parameters = dict(units='mm', revision='R0.6 - 5 mm hole variant; same centres as R0.5',
    compatible_base='uvir-custom-case-base-r03.stl',
    sensor_centres_from_board_top_left=sensor_board_centres,
    source_measurements='sensor-measurements.json',
    sensor_centre_to_centre_distance=spacing,
    sensor_horizontal_offset=horizontal_offset,
    sensor_vertical_separation=vertical_separation,
    width_length_thickness=[W, L, T], optical_hole_diameter=APERTURE_D,
    external_projection=0, optical_hole_centres_from_bottom_left=centres,
    optical_hole_centres_from_top_left={k: [xy[0], L - xy[1]] for k, xy in centres.items()},
    screw_clearance_diameter=SCREW_D, screw_centres_from_bottom_left=screws,
    assembled_translation=[OX, OY, p['lid_seat_z']],
    aperture_edge_to_edge_distance=spacing - APERTURE_D,
    visible_aperture_to_top_edge=L - centres['visible_ir'][1] - APERTURE_D / 2,
    sensor_to_inner_lid_gap=p['lid_clearance_above_estimated_sensor_plane'])
(OUT / 'lid-parameters.json').write_text(json.dumps(parameters, indent=2) + '\n', encoding='utf-8')
report = dict(watertight=True, connected_components=1, genus=checked.genus(),
    triangles=len(f), dimensions_mm=(v.max(axis=0) - v.min(axis=0)).tolist(),
    material_volume_cm3=checked.volume() / 1000,
    independent_stl_readback=True, optical_holes_verified=True,
    base_intersection_mm3=intersection,
    sha256=hashlib.sha256(path.read_bytes()).hexdigest())
(OUT / 'lid-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

scad = ('// Millimetres. R0.6 lid, 5 mm optical holes; same centres as R0.5.\n$fn=256;\n'
        f'width={W}; length={L}; thickness={T};\n'
        f'optical_diameter={APERTURE_D}; screw_diameter={SCREW_D};\n'
        f'optical_holes={json.dumps(list(centres.values()))};\n'
        f'screw_holes={json.dumps(screws)};\n'
        'difference() {\n  cube([width,length,thickness]);\n'
        '  for(p=optical_holes) translate([p[0],p[1],-0.1]) cylinder(h=thickness+0.2,d=optical_diameter);\n'
        '  for(p=screw_holes) translate([p[0],p[1],-0.1]) cylinder(h=thickness+0.2,d=screw_diameter);\n}\n')
(OUT / f'{name}.scad').write_text(scad, encoding='utf-8')

print(json.dumps(report, indent=2))
