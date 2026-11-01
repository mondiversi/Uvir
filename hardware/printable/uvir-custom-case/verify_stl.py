"""Independently reopen the exported STL and validate the printable geometry."""
from pathlib import Path
import json
import struct
import numpy as np

out = Path(__file__).resolve().parent
import manifold3d as m

data = (out / 'uvir-custom-case-base-r03.stl').read_bytes()
n = struct.unpack_from('<I', data, 80)[0]
assert len(data) == 84 + 50 * n
rows = np.array([struct.unpack_from('<12fH', data, 84 + i * 50)[:12] for i in range(n)], dtype=np.float32)
assert np.isfinite(rows).all()
assert np.allclose(np.linalg.norm(rows[:, :3], axis=1), 1, atol=1e-5)
v, inverse = np.unique(rows[:, 3:].reshape((-1, 3)), axis=0, return_inverse=True)
f = inverse.reshape((-1, 3)).astype(np.uint32)
solid = m.Manifold(m.Mesh(v, f))
assert solid.status() == m.Error.NoError
assert len(solid.decompose()) == 1
assert solid.genus() == 1
assert np.allclose(v.min(axis=0), (0,0,0), atol=.001)
assert np.allclose(v.max(axis=0), (62,94,28.8), atol=.001)
parameters = json.loads((out / 'case-parameters.json').read_text())
def beam(x,y,low,high):
    return m.Manifold.cube((.1,.1,high-low)).translate((x-.05,y-.05,low))
for x,y in parameters['pcb_screw_centres_xy']:
    assert (solid ^ beam(x+2,y,7.42,9.0)).volume() < 1e-6
    assert (solid ^ beam(x+2,y,7.0,7.38)).volume() > .003
    assert (solid ^ beam(x,y,3.42,7.5)).volume() < 1e-6
for x,y in parameters['lid_screw_centres_xy']:
    assert (solid ^ beam(x,y,20.42,26.5)).volume() < 1e-6
# The future flush lid has four genuinely square corners, clear of the rim.
for x in (2.6, 59.3):
    for y in (2.6, 91.3):
        assert (solid ^ m.Manifold.cube((.1,.1,2.2)).translate((x,y,26.5))).volume() < 1e-6
assert abs(parameters['lid_clearance_above_highest_hardware'] - 1.0) < 1e-6
assert abs(parameters['usb_exposed_protrusion'] - 12.0) < 1e-6
assert parameters['usb_opening_width_height_bottom_z'] == [14.1, 7.0, 11.0]
assert parameters['usb_opening_top_from_outside_floor'] == 18.0
# Probe actual exported material at the centre of the front wall.
assert (solid ^ beam(31, 1.2, 11.02, 17.98)).volume() < 1e-6
assert (solid ^ beam(31, 1.2, 5.1, 10.9)).volume() > .057
assert (solid ^ beam(31, 1.2, 18.1, 20.0)).volume() > .018
for x in (24.0, 38.0):
    assert (solid ^ beam(x, 1.2, 14.45, 14.55)).volume() < 1e-6
for x in (23.85, 38.15):
    assert (solid ^ beam(x, 1.2, 14.45, 14.55)).volume() > .0009

def load_other(path):
    raw = path.read_bytes()
    count = struct.unpack_from('<I', raw, 80)[0]
    coords = np.array([struct.unpack_from('<12fH', raw, 84 + 50 * i)[3:12]
                       for i in range(count)], dtype=np.float32).reshape((-1, 3))
    verts, inverse = np.unique(coords, axis=0, return_inverse=True)
    result = m.Manifold(m.Mesh(verts, inverse.reshape((-1, 3)).astype(np.uint32)))
    assert result.status() == m.Error.NoError
    return result

lid_path = out / 'uvir-custom-case-lid-r06-5mm.stl'
lid = load_other(lid_path)
assert len(lid.decompose()) == 1 and lid.genus() == 6
lid_parameters = json.loads((out / 'lid-parameters.json').read_text())
assert lid_parameters['optical_hole_diameter'] == 5
assert np.allclose(lid.bounding_box(), (0, 0, 0, 56.8, 88.8, 2.4), atol=.001)
assert abs(lid_parameters['sensor_horizontal_offset'] - .5) < 1e-9
assert abs(lid_parameters['sensor_centre_to_centre_distance'] - 13.5) < 1e-9
for x, y in lid_parameters['optical_hole_centres_from_bottom_left'].values():
    probe = m.Manifold.cylinder(2.8, 2.49, circular_segments=128).translate((x,y,-.2))
    assert (lid ^ probe).volume() < 1e-6
lid_clearances = {lid_path.name: (solid ^ lid.translate((2.6,2.6,26.4))).volume()}
assert all(volume < .001 for volume in lid_clearances.values())
report = json.loads((out / 'case-validation.json').read_text())
report['independent_stl_readback'] = {'finite_unit_normals': True,
    'manifold': True, 'connected_components': 1, 'genus': 1,
    'support_heights_and_blind_pilots_checked': True,
    'usb_opening_Z11_to_Z18_verified': True,
    'existing_lid_intersections_mm3': lid_clearances}
(out / 'case-validation.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print('Independent STL readback passed: USB Z11..18, closed manifold, supports and current 5 mm lid fit digitally.')
