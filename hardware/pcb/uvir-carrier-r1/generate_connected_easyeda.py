"""Generate a visibly wired EasyEDA Standard schematic for the Uvir carrier.

The ESP32 header labels follow the user's USB-down 38-pin reference photo.
This is an electrical schematic, not an assigned-footprint PCB layout.
"""

from __future__ import annotations

from collections import defaultdict, deque
import json
from pathlib import Path
import re


HERE = Path(__file__).resolve().parent
SOURCE = HERE / "uvir-carrier-r1-pin-netlist.json"
OUTPUT = HERE / "uvir-carrier-r1-easyeda-standard-schematic.json"
FIRMWARE_PINS = HERE.parents[2] / "firmware/esp32/UvirSensor/UvirHardwareConfig.h"
GRID = 5


def main() -> None:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    firmware = FIRMWARE_PINS.read_text(encoding="utf-8")
    gpio_constants = {
        "kI2cSdaPin": 21, "kI2cSclPin": 22,
        "kSdChipSelectPin": 13, "kSdClockPin": 18,
        "kSdMisoPin": 19, "kSdMosiPin": 23,
        "kStatusLedRedPin": 25, "kStatusLedGreenPin": 26,
        "kOperationLedBluePin": 27, "kStatusBuzzerPin": 32,
        "kExternalCommandPin": 33,
    }
    for name, pin in gpio_constants.items():
        if re.search(rf"\b{name}\s*=\s*{pin}\s*;", firmware) is None:
            raise ValueError(f"Schematic GPIO differs from firmware: {name} != {pin}")
    connection_nets = {
        connection: net["name"]
        for net in source["nets"]
        for connection in net["connections"]
    }
    if len(connection_nets) != sum(len(net["connections"]) for net in source["nets"]):
        raise ValueError("A component pin belongs to more than one net")
    expected_nets = {net["name"]: set(net["connections"]) for net in source["nets"]}
    serial = 0

    def gid() -> str:
        nonlocal serial
        serial += 1
        return f"ggeuvir{serial:05d}"

    shapes: list[str] = []
    pin_points: dict[str, tuple[int, int]] = {}
    segments: dict[str, list[tuple[tuple[int, int], tuple[int, int]]]] = defaultdict(list)
    junctions: list[tuple[str, tuple[int, int]]] = []

    def text(x: int, y: int, value: str, *, size: int = 10, bold: bool = False) -> None:
        shapes.append(
            f"T~L~{x}~{y}~0~#000080~Arial~{size}pt~"
            f"{'bold' if bold else 'normal'}~normal~~comment~{value}~1~start~{gid()}~0"
        )

    def symbol(
        ref: str, name: str, x: int, y: int, width: int,
        left: list[tuple[str, str, str]], right: list[tuple[str, str, str]],
    ) -> None:
        height = 28 + 25 * max(len(left), len(right))
        # Package intentionally blank until the real socket dimensions are measured.
        parts = [
            f"LIB~{x}~{y}~package``nameAlias`Value`Value`{name}`spicePre`{ref[0]}`"
            f"~0~0~{gid()}",
            f"T~P~{x}~{y - 17}~0~#000080~Arial~~~~~comment~{ref}~1~start~{gid()}~0",
            f"T~N~{x + 35}~{y - 17}~0~#000080~Arial~~~~~comment~{name}~1~start~{gid()}~0",
            f"R~{x}~{y}~0~0~{width}~{height}~#A00000~1~0~none~{gid()}~0",
        ]
        for side, pins in (("left", left), ("right", right)):
            for index, (connection, label, number) in enumerate(pins):
                if connection not in connection_nets or connection in pin_points:
                    raise ValueError(f"Missing or duplicate connection: {connection}")
                py = y + 30 + 25 * index
                if side == "left":
                    dot_x, body_x, rotation, anchor = x - 20, x, 180, "start"
                    path = f"M {dot_x} {py} h 20"
                    name_x, number_x = x + 5, x - 15
                else:
                    dot_x, body_x, rotation, anchor = x + width + 20, x + width, 0, "end"
                    path = f"M {dot_x} {py} h -20"
                    name_x, number_x = x + width - 5, x + width + 15
                pin_points[connection] = (dot_x, py)
                parts.append(
                    f"P~show~0~{number}~{dot_x}~{py}~{rotation}~{gid()}~0"
                    f"^^{dot_x}~{py}"
                    f"^^{path}~#880000"
                    f"^^1~{name_x}~{py + 3}~0~{label}~{anchor}~~7pt"
                    f"^^0~{number_x}~{py - 4}~0~{number}~{anchor}~~7pt"
                    f"^^0~{body_x}~{py}"
                    f"^^0~M {body_x} {py - 3} L {body_x} {py + 3}"
                )
        shapes.append("#@$".join(parts))

    def point(connection: str) -> tuple[int, int]:
        return pin_points[connection]

    def wire(net: str, *points: tuple[int, int]) -> None:
        for a, b in zip(points, points[1:]):
            if a == b:
                continue
            if a[0] != b[0] and a[1] != b[1]:
                raise ValueError(f"Non-orthogonal wire {net}: {a} -> {b}")
            if any(coord % GRID for p in (a, b) for coord in p):
                raise ValueError(f"Off-grid wire {net}: {a} -> {b}")
            segments[net].append((a, b))
            shapes.append(f"W~{a[0]} {a[1]} {b[0]} {b[1]}~#008800~2~0~none~{gid()}~0")

    def junction(net: str, p: tuple[int, int]) -> None:
        junctions.append((net, p))
        shapes.append(f"J~{p[0]}~{p[1]}~2.5~#CC0000~{gid()}~0")

    def net_label(net: str, p: tuple[int, int]) -> None:
        # These labels supplement visible continuous wires; they do not replace them.
        shapes.append(
            f"N~{p[0]}~{p[1]}~0~#000080~{net}~{gid()}~start~"
            f"{p[0] + 5}~{p[1] - 5}~Arial~8pt~0"
        )

    text(55, 55, "UVIR CARRIER R1 - CONNECTED ELECTRICAL SCHEMATIC", size=14, bold=True)
    text(55, 78, "ESP32 header labels J2/J3 correspond to the USB-down 38-pin reference.", size=9)
    text(55, 96, "Green lines are continuous wires; red dots are intentional branch junctions.", size=9)

    symbol(
        "U1", "ESP32-WROOM-32 DEV BOARD", 220, 260, 230,
        [("U1.GND", "GND (J3-1)", "J3-1"),
         ("U1.5V_or_VIN", "5V (J2-19)", "J2-19")],
        [("U1.3V3", "3V3 (J2-1)", "J2-1"),
         ("U1.GPIO21", "GPIO21 / SDA (J3-6)", "J3-6"),
         ("U1.GPIO22", "GPIO22 / SCL (J3-3)", "J3-3"),
         ("U1.GPIO13", "GPIO13 / SD CS (J2-15)", "J2-15"),
         ("U1.GPIO18", "GPIO18 / SD SCK (J3-9)", "J3-9"),
         ("U1.GPIO19", "GPIO19 / SD MISO (J3-8)", "J3-8"),
         ("U1.GPIO23", "GPIO23 / SD MOSI (J3-2)", "J3-2"),
         ("U1.GPIO25", "GPIO25 / RGB RED (J2-9)", "J2-9"),
         ("U1.GPIO26", "GPIO26 / RGB GREEN (J2-10)", "J2-10"),
         ("U1.GPIO27", "GPIO27 / BLUE LED (J2-11)", "J2-11"),
         ("U1.GPIO32", "GPIO32 / PIEZO (J2-7)", "J2-7"),
         ("U1.GPIO33", "GPIO33 / EXT LOW (J2-8)", "J2-8")],
    )
    symbol(
        "U2", "GY-AS7343 VISIBLE/NIR", 900, 260, 230,
        [("U2.VCC", "VCC", "VCC"), ("U2.SDA", "SDA", "SDA"),
         ("U2.SCL", "SCL", "SCL")],
        [("U2.GND", "GND", "GND")],
    )
    symbol(
        "U3", "MIKROE UV 5 CLICK / AS7331", 900, 440, 250,
        [("U3.pin7_3V3", "3V3 (7)", "7"), ("U3.pin11_SDA", "SDA (11)", "11"),
         ("U3.pin12_SCL", "SCL (12)", "12")],
        [("U3.pin8_GND", "GND (8)", "8"), ("U3.pin9_GND", "GND (9)", "9")],
    )
    symbol(
        "U4", "HW-084 DS3231 RTC", 900, 640, 230,
        [("U4.VCC", "VCC", "VCC"), ("U4.SDA", "SDA", "SDA"),
         ("U4.SCL", "SCL", "SCL")],
        [("U4.GND", "GND", "GND")],
    )
    symbol(
        "U5", "MB85RC256V I2C FRAM", 900, 820, 230,
        [("U5.VCC", "VCC", "VCC"), ("U5.SDA", "SDA", "SDA"),
         ("U5.SCL", "SCL", "SCL")],
        [("U5.GND", "GND", "GND")],
    )
    symbol(
        "U6", "6-PIN SPI MICROSD ADAPTER", 900, 1030, 230,
        [("U6.CS", "CS", "CS"), ("U6.SCK", "SCK", "SCK"),
         ("U6.MISO", "MISO", "MISO"), ("U6.MOSI", "MOSI", "MOSI")],
        [("U6.VCC", "VCC / 5V", "VCC"), ("U6.GND", "GND", "GND")],
    )
    for index, y in enumerate((1260, 1350, 1440), start=1):
        ref = f"R{index}"
        symbol(ref, "330 OHM", 900, y, 140,
               [(f"{ref}.pin1", "INPUT", "1")],
               [(f"{ref}.pin2", "OUTPUT", "2")])
    symbol(
        "D1", "RGB LED - R/G CHANNELS", 1300, 1260, 240,
        [("D1.red_anode", "RED ANODE", "R"),
         ("D1.green_anode", "GREEN ANODE", "G")],
        [("D1.common_cathode", "COMMON CATHODE", "K")],
    )
    symbol(
        "D2", "BLUE LED", 1300, 1440, 220,
        [("D2.anode", "ANODE", "A")],
        [("D2.cathode", "CATHODE", "K")],
    )
    symbol(
        "BZ1", "LOW-CURRENT PASSIVE PIEZO", 900, 1550, 230,
        [("BZ1.positive", "+", "1")],
        [("BZ1.negative", "-", "2")],
    )
    symbol(
        "SW1", "EXTERNAL N.O. SWITCH", 900, 1660, 230,
        [("SW1.contact_A", "SIDE A", "A")],
        [("SW1.contact_B", "SIDE B", "B")],
    )
    if set(pin_points) != set(connection_nets):
        raise ValueError(f"Pin mismatch: {sorted(set(connection_nets) - set(pin_points))}")

    # Shared 3.3 V and I2C buses with explicit T-junctions.
    for net, origin, bus_x, destination_pins in [
        ("3V3", "U1.3V3", 800,
         ["U2.VCC", "U3.pin7_3V3", "U4.VCC", "U5.VCC"]),
        ("I2C_SDA", "U1.GPIO21", 780,
         ["U2.SDA", "U3.pin11_SDA", "U4.SDA", "U5.SDA"]),
        ("I2C_SCL", "U1.GPIO22", 760,
         ["U2.SCL", "U3.pin12_SCL", "U4.SCL", "U5.SCL"]),
    ]:
        start = point(origin)
        destinations = [point(connection) for connection in destination_pins]
        wire(net, start, (bus_x, start[1]), (bus_x, destinations[-1][1]))
        for target in destinations:
            branch = (bus_x, target[1])
            wire(net, branch, target)
            if target != destinations[-1]:
                junction(net, branch)
        net_label(net, (bus_x, start[1]))

    # Ground runs around the top/right. Crossings without dots are separate nets.
    wire("GND", point("U1.GND"), (110, 290), (110, 150),
         (1600, 150), (1600, 1690))
    ground_pins = [
        "U2.GND", "U3.pin8_GND", "U3.pin9_GND", "U4.GND",
        "U5.GND", "U6.GND", "D1.common_cathode", "D2.cathode",
        "BZ1.negative", "SW1.contact_B",
    ]
    for connection in ground_pins:
        target = point(connection)
        wire("GND", target, (1600, target[1]))
        if connection != ground_pins[-1]:
            junction("GND", (1600, target[1]))
    net_label("GND", (1600, 150))

    wire("5V_USB", point("U1.5V_or_VIN"), (130, 315), (130, 180),
         (1580, 180), (1580, 1060), point("U6.VCC"))
    net_label("5V_USB", (1580, 180))

    # Dedicated lanes make every ESP32 signal visibly traceable.
    signal_routes = [
        ("SD_CS", "U1.GPIO13", "U6.CS", 700),
        ("SD_SCK", "U1.GPIO18", "U6.SCK", 680),
        ("SD_MISO", "U1.GPIO19", "U6.MISO", 660),
        ("SD_MOSI", "U1.GPIO23", "U6.MOSI", 640),
        ("LED_RED_DRIVE", "U1.GPIO25", "R1.pin1", 620),
        ("LED_GREEN_DRIVE", "U1.GPIO26", "R2.pin1", 600),
        ("LED_BLUE_DRIVE", "U1.GPIO27", "R3.pin1", 580),
        ("BUZZER_PWM", "U1.GPIO32", "BZ1.positive", 560),
        ("EXTERNAL_COMMAND_ACTIVE_LOW", "U1.GPIO33", "SW1.contact_A", 540),
    ]
    for net, origin, destination, lane_x in signal_routes:
        start, end = point(origin), point(destination)
        wire(net, start, (lane_x, start[1]), (lane_x, end[1]), end)

    wire("LED_RED_ANODE", point("R1.pin2"), point("D1.red_anode"))
    wire("LED_GREEN_ANODE", point("R2.pin2"), (1230, 1380),
         (1230, 1315), point("D1.green_anode"))
    wire("LED_BLUE_ANODE", point("R3.pin2"), point("D2.anode"))

    # Verify physical wire reachability, not only matching net-label text.
    coverage: dict[str, set[tuple[int, int]]] = {}
    graph: dict[str, dict[tuple[int, int], set[tuple[int, int]]]] = {}
    occupied_edges: dict[frozenset[tuple[int, int]], str] = {}
    for net, net_segments in segments.items():
        adjacency: dict[tuple[int, int], set[tuple[int, int]]] = defaultdict(set)
        for a, b in net_segments:
            dx = GRID if b[0] > a[0] else -GRID if b[0] < a[0] else 0
            dy = GRID if b[1] > a[1] else -GRID if b[1] < a[1] else 0
            steps = max(abs(b[0] - a[0]), abs(b[1] - a[1])) // GRID
            prev = a
            for index in range(1, steps + 1):
                current = (a[0] + dx * index, a[1] + dy * index)
                edge = frozenset((prev, current))
                other = occupied_edges.get(edge)
                if other is not None and other != net:
                    raise ValueError(f"Overlapping wires {net} and {other} at {edge}")
                occupied_edges[edge] = net
                adjacency[prev].add(current)
                adjacency[current].add(prev)
                prev = current
        graph[net] = adjacency
        coverage[net] = set(adjacency)

    for net, connection_set in expected_nets.items():
        pins = {point(connection) for connection in connection_set}
        if not pins <= coverage.get(net, set()):
            raise ValueError(f"Unwired pin on {net}: {pins - coverage.get(net, set())}")
        first = next(iter(pins))
        visited = {first}
        queue = deque([first])
        while queue:
            for neighbor in graph[net][queue.popleft()]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    queue.append(neighbor)
        if not pins <= visited:
            raise ValueError(f"Disconnected net {net}: {pins - visited}")

    for net, location in junctions:
        if any(location in nodes for other, nodes in coverage.items() if other != net):
            raise ValueError(f"Junction for {net} touches another net at {location}")
        if len(graph[net][location]) < 3:
            raise ValueError(f"Unnecessary junction on {net} at {location}")

    text(55, 1760, "SCHEMATIC ONLY: verify every socket footprint, pin 1 and physical pitch before PCB layout.", size=9)
    text(55, 1780, "5V_USB is exclusively for the tested level-shifted microSD module; all I2C devices use 3V3.", size=9)
    text(55, 1800, "Check I2C pull-ups, regulator budget, antenna clearance, optical shielding and RTC charging path.", size=9)
    text(55, 1820, "SW1 uses opposite contact sides; GPIO33 is active LOW. R1/R2/R3 = 330 ohm.", size=9)

    document = {
        "head": {
            "docType": "1", "editorVersion": "6.3.0", "newgId": True,
            "c_para": {"Prefix Start": "1"}, "c_spiceCmd": None, "isSheet": True,
        },
        "canvas": "CA~1750~1900~#FFFFFF~yes~#CCCCCC~10~1750~1900~line~10~pixel~5~0~0",
        "shape": shapes,
        "BBox": {"x": 40, "y": 35, "width": 1650, "height": 1800},
        "colors": {},
    }
    OUTPUT.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Generated {OUTPUT.name}: {len(shapes)} shapes, "
        f"{len(pin_points)} wired pins, {len(expected_nets)} connected nets, "
        f"{sum(map(len, segments.values()))} wire segments"
    )


if __name__ == "__main__":
    main()
