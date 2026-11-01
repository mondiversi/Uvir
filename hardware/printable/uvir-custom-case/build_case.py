"""UVIR custom lower enclosure R0.3, corrected USB height; dimensions in mm.

Install the dependencies listed in requirements.txt. Values labelled draft in
parameters.json are deliberate allowances, not measurements of the actual parts.
"""
from pathlib import Path
import hashlib
import json
import math
import struct

import numpy as np

OUT = Path(__file__).resolve().parent
import manifold3d as m

NAME = 'uvir-custom-case-base-r03'
W, L, H = 62.0, 94.0, 28.8
WALL, FLOOR, OUTER_R = 2.4, 2.4, 2.4
BOARD_W, BOARD_L, BOARD_T, HARDWARE_H = 43.5, 85.8, 1.5, 18.0
STANDOFF_H, STANDOFF_D = 5.0, 7.2
USB_LEFT, USB_RIGHT, USB_LENGTH, USB_COVERED = 15.7, 14.7, 15.0, 3.0
USB_W = BOARD_W - USB_LEFT - USB_RIGHT
BOARD_X = W / 2 - (USB_LEFT + USB_W / 2)
BOARD_Y, BOARD_Z = USB_COVERED, FLOOR + STANDOFF_H
BOARD_CENTRES = [(BOARD_X + x, BOARD_Y + y) for y in (7.3, BOARD_L - 6.5)
                 for x in (4.2, BOARD_W - 3.5)]
LID_T, LEDGE = 2.4, 1.2
SEAT_Z = H - LID_T
LID_POST_D, LID_INSET = 5.6, 5.2
LID_CENTRES = [(x, y) for y in (LID_INSET, L - LID_INSET)
               for x in (LID_INSET, W - LID_INSET)]
PILOT_D, PCB_PILOT_DEPTH, LID_PILOT_DEPTH = 1.6, 4.0, 6.0
fit = json.loads((OUT / 'usb-fit-measurements.json').read_text())
USB_OPEN_W = fit['opening_width_unchanged']
USB_OPEN_H = fit['filed_opening_total_height'] - fit['unused_bottom_height_to_close']
USB_OPEN_Z = fit['previous_opening_bottom_from_outside_floor'] + fit['unused_bottom_height_to_close']
assert (USB_OPEN_Z, USB_OPEN_H, USB_OPEN_Z + USB_OPEN_H) == (11., 7., 18.)
sensor_reference = json.loads((OUT / 'sensor-measurements.json').read_text())
uv_x = sensor_reference['uv_centre_x_from_board_left']
vis_x = uv_x - sensor_reference['uv_right_of_visible_ir']
vis_down = sensor_reference['visible_ir_centre_from_board_top']
uv_down = vis_down + math.sqrt(sensor_reference['centre_to_centre_distance'] ** 2 -
    sensor_reference['uv_right_of_visible_ir'] ** 2)
EPS, NQ, NC = .02, 40, 128


def box(x, y, z, dx, dy, dz):
    return m.Manifold.cube((dx, dy, dz)).translate((x, y, z))


def rr(w, length, radius):
    return m.CrossSection.square((w - 2 * radius, length - 2 * radius), center=True).offset(
        radius, circular_segments=NC)


def rounded_prism(inset, z, height):
    return rr(W - 2 * inset, L - 2 * inset, OUTER_R - inset).extrude(height).translate((W / 2, L / 2, z))


def square_prism(inset, z, height):
    return box(inset, inset, z, W - 2 * inset, L - 2 * inset, height)


def ring(inset, z):
    return [(inset, inset, z), (W - inset, inset, z),
            (W - inset, L - inset, z), (inset, L - inset, z)]


def tapered_void(a, z0, b, z1):
    vertices = ring(a, z0) + ring(b, z1)
    n = len(vertices) // 2
    faces = []
    for i in range(n):
        j = (i + 1) % n
        faces.extend(((i, j, n + j), (i, n + j, n + i)))
    vertices.extend(((W / 2, L / 2, z0), (W / 2, L / 2, z1)))
    for i in range(n):
        j = (i + 1) % n
        faces.extend(((2 * n, j, i), (2 * n + 1, n + i, n + j)))
    return m.Manifold(m.Mesh(np.asarray(vertices, dtype=np.float32), np.asarray(faces, dtype=np.uint32)))


def cylinder(x, y, z, diameter, height):
    return m.Manifold.cylinder(height, diameter / 2, circular_segments=NC).translate((x, y, z))


def pilot(x, y, top, depth):
    bore = cylinder(x, y, top - depth, PILOT_D, depth + EPS)
    entry = m.Manifold.cylinder(.4 + EPS, PILOT_D / 2, 1.0,
        circular_segments=NC).translate((x, y, top - .4))
    return bore + entry


body = rounded_prism(0, 0, H) - square_prism(WALL, FLOOR, H + 1)
# A 45-degree underside makes the inward lid ledge print-friendly.
ledge = square_prism(WALL - .1, SEAT_Z - LEDGE, LEDGE)
ledge -= tapered_void(WALL, SEAT_Z - LEDGE - EPS,
                      WALL + LEDGE + EPS, SEAT_Z + EPS)
parts = [body, ledge]
for x, y in BOARD_CENTRES:
    parts.append(cylinder(x, y, FLOOR - EPS, STANDOFF_D, STANDOFF_H + EPS))
for x, y in LID_CENTRES:
    parts.append(cylinder(x, y, FLOOR - EPS, LID_POST_D, SEAT_Z - FLOOR + EPS))
    # Solid corner backing ties tall pillars to the shell without sealed air pockets.
    near_x, near_y = (WALL - .1 if x < W / 2 else W - WALL + .1), (WALL - .1 if y < L / 2 else L - WALL + .1)
    parts.append(box(min(x, near_x), min(y, near_y), FLOOR - EPS,
                     abs(x - near_x), abs(y - near_y), SEAT_Z - FLOOR + EPS))
body = m.Manifold.batch_boolean(parts, m.OpType.Add)
cuts = [pilot(x, y, BOARD_Z, PCB_PILOT_DEPTH) for x, y in BOARD_CENTRES]
cuts += [pilot(x, y, SEAT_Z, LID_PILOT_DEPTH) for x, y in LID_CENTRES]
# Extrude the rounded XY rectangle, rotate into the front XZ plane.
usb_cut = rr(USB_OPEN_W, USB_OPEN_H, 1.0).extrude(WALL + 2 * EPS).rotate((90, 0, 0)).translate(
    (W / 2, WALL + EPS, USB_OPEN_Z + USB_OPEN_H / 2))
body = m.Manifold.batch_boolean([body, usb_cut] + cuts, m.OpType.Subtract).simplify(.001)
assert body.status() == m.Error.NoError, body.status()
if len(body.decompose()) != 1:
    print('Components:', [(part.volume(), part.bounding_box()) for part in body.decompose()])
assert len(body.decompose()) == 1


def write_stl(solid, filename):
    mesh = solid.to_mesh()
    v, f = mesh.vert_properties[:, :3], mesh.tri_verts
    # Binary STL uses float32: weld vertices that become identical at that precision
    # and remove the collapsed triangles, then independently validate that mesh.
    v, inverse = np.unique(v, axis=0, return_inverse=True)
    f = inverse[f]
    f = f[(f[:, 0] != f[:, 1]) & (f[:, 1] != f[:, 2]) & (f[:, 2] != f[:, 0])]
    used, inverse = np.unique(f, return_inverse=True)
    v, f = v[used], inverse.reshape((-1, 3)).astype(np.uint32)
    checked = m.Manifold(m.Mesh(v.astype(np.float32), f))
    assert checked.status() == m.Error.NoError, 'Invalid float32 STL topology'
    assert len(checked.decompose()) == 1
    assert abs(checked.volume() - solid.volume()) < .01
    with filename.open('wb') as stream:
        stream.write(b'Uvir lower case R0.3; mm; USB 14.1x7 at Z11..18; square lid seat; no lid'.ljust(80, b' '))
        stream.write(struct.pack('<I', len(f)))
        for indices in f:
            a, b, c = v[indices].astype(np.float64)
            normal = np.cross(b - a, c - a)
            norm = np.linalg.norm(normal)
            assert norm > 1e-10, 'Degenerate STL face'
            normal /= norm
            stream.write(struct.pack('<12fH', *normal, *a, *b, *c, 0))
    return v, f


vertices, faces = write_stl(body, OUT / f'{NAME}.stl')
# Verify material geometry, not only the visual render.
edges = np.sort(np.concatenate((faces[:, [0, 1]], faces[:, [1, 2]], faces[:, [2, 0]])), axis=1)
unique_edges, counts = np.unique(edges, axis=0, return_counts=True)
assert np.all(counts == 2), 'Non-manifold STL edge'
euler = len(vertices) - len(unique_edges) + len(faces)
assert euler == 0, euler  # One through-wall USB window; eight blind pilot bores.
size = vertices.max(axis=0) - vertices.min(axis=0)
assert np.allclose(size, (W, L, H), atol=.001), size
assert body.volume() > 0
board = box(BOARD_X, BOARD_Y, BOARD_Z, BOARD_W, BOARD_L, BOARD_T)
hardware = box(BOARD_X, BOARD_Y, BOARD_Z + EPS, BOARD_W, BOARD_L, HARDWARE_H - EPS)
future_lid = square_prism(WALL + .2, SEAT_Z, LID_T)
collisions = {'pcb': max(0., (body ^ board).volume()),
              'hardware_envelope': max(0., (body ^ hardware).volume()),
              'future_blank_lid': max(0., (body ^ future_lid).volume())}
assert all(value < .001 for value in collisions.values()), collisions
floor = rounded_prism(0, 0, FLOOR)
assert abs((body ^ floor).volume() - floor.volume()) < .001

parameters = {
    'units': 'mm', 'revision': 'R0.3 - USB opening 7 mm high, bottom raised to Z11 mm',
    'external_width_length_height': [W, L, H], 'outer_corner_radius': OUTER_R,
    'wall_and_floor': [WALL, FLOOR],
    'pcb_width_length_thickness': [BOARD_W, BOARD_L, BOARD_T],
    'pcb_bottom_left_front_xyz': [BOARD_X, BOARD_Y, BOARD_Z],
    'max_hardware_height_including_pcb': HARDWARE_H,
    'highest_hardware_z': BOARD_Z + HARDWARE_H,
    'estimated_sensor_plane_z': BOARD_Z + HARDWARE_H - 1,
    'pcb_standoff_height_diameter': [STANDOFF_H, STANDOFF_D],
    'pcb_screw_centres_xy': BOARD_CENTRES,
    'lid_screw_centres_xy': LID_CENTRES,
    'draft_screw_nominal_diameter': 2.0, 'draft_pilot_diameter': PILOT_D,
    'pcb_pilot_depth': PCB_PILOT_DEPTH, 'lid_pilot_depth': LID_PILOT_DEPTH,
    'lid_seat_z': SEAT_Z, 'lid_ledge_width': LEDGE,
    'future_lid_nominal_thickness': LID_T,
    'lid_recess_corner_radius': 0.0,
    'future_square_lid_width_length': [W - 2 * (WALL + .2), L - 2 * (WALL + .2)],
    'future_lid_clearance_per_side': .2,
    'lid_clearance_above_highest_hardware': SEAT_Z - (BOARD_Z + HARDWARE_H),
    'lid_clearance_above_estimated_sensor_plane': SEAT_Z - (BOARD_Z + HARDWARE_H - 1),
    'sensor_centres_from_board_top_left': {'visible_ir': [vis_x, vis_down], 'uv': [uv_x, uv_down]},
    'sensor_centres_case_xyz': {'visible_ir': [BOARD_X + vis_x, BOARD_Y + BOARD_L - vis_down, BOARD_Z + HARDWARE_H - 1],
                                'uv': [BOARD_X + uv_x, BOARD_Y + BOARD_L - uv_down, BOARD_Z + HARDWARE_H - 1]},
    'usb_width_derived_from_side_measurements': USB_W,
    'usb_offset_from_pcb_centre_to_right': USB_LEFT + USB_W / 2 - BOARD_W / 2,
    'usb_case_centre_x': W / 2,
    'usb_covered_protrusion': USB_COVERED,
    'usb_exposed_protrusion': USB_LENGTH - USB_COVERED,
    'draft_usb_opening_width_height_bottom_z': [USB_OPEN_W, USB_OPEN_H, USB_OPEN_Z],
    'usb_opening_width_height_bottom_z': [USB_OPEN_W, USB_OPEN_H, USB_OPEN_Z],
    'usb_opening_top_from_outside_floor': USB_OPEN_Z + USB_OPEN_H,
    'usb_fit_measurement_source': 'usb-fit-measurements.json',
    'sensor_measurement_source': 'sensor-measurements.json',
    'draft_assumptions': ['USB opening follows the user-reported filed opening; final fit still needs confirmation.',
                          'Pilot holes assume approximately 2 mm self-tapping screws.',
                          'Independent lid screws add side clearance outside the PCB.',
                          'Sensor centres are approximate user measurements; no optical apertures are modeled.',
                          'The future lid is a square-cornered rectangle. No lid is exported.'],
}
(OUT / 'case-parameters.json').write_text(json.dumps(parameters, indent=2) + '\n', encoding='utf-8')
report = {'watertight_edges': True, 'connected_components': 1, 'euler_characteristic': int(euler),
          'triangles': len(faces), 'external_dimensions_mm': size.tolist(),
          'material_volume_cm3': body.volume() / 1000, 'clearance_intersection_mm3': collisions,
          'stl_sha256': hashlib.sha256((OUT / f'{NAME}.stl').read_bytes()).hexdigest()}
(OUT / 'case-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
