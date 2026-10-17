"""Check local build-guide links, full wiring coverage and firmware-map consistency.

This is a documentation regression check, not an electrical/ERC/DRC test.
Uses only the Python standard library and never connects to hardware.
"""

from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
WIRING = ROOT / "hardware/wiring"
CONFIG = ROOT / "firmware/esp32/UvirSensor/UvirHardwareConfig.h"

# Each guide must keep the relevant source-controlled GPIO/address references.
GUIDES = {
    "ESP32-CONTROLLER.md": ("kSerialBaud", "kI2cSdaPin", "kI2cSclPin",
                             "kSdChipSelectPin", "kSdClockPin", "kSdMisoPin", "kSdMosiPin",
                             "kStatusLedRedPin", "kStatusLedGreenPin", "kOperationLedBluePin",
                             "kStatusBuzzerPin", "kExternalCommandPin"),
    "ESP32-AS7343.md": ("kI2cSdaPin", "kI2cSclPin", "kAs7343Address"),
    "ESP32-UV5-AS7331.md": ("kI2cSdaPin", "kI2cSclPin", "kAs7331Address"),
    "ESP32-DS3231.md": ("kI2cSdaPin", "kI2cSclPin", "kDs3231Address"),
    "ESP32-FRAM-MB85RC256V.md": ("kI2cSdaPin", "kI2cSclPin", "kFramAddress"),
    "ESP32-MICROSD-SPI.md": ("kSdChipSelectPin", "kSdClockPin", "kSdMisoPin", "kSdMosiPin"),
    "ESP32-STATUS-LEDS.md": ("kStatusLedRedPin", "kStatusLedGreenPin", "kOperationLedBluePin"),
    "ESP32-BUZZER.md": ("kStatusBuzzerPin",),
    "ESP32-EXTERNAL-COMMAND.md": ("kExternalCommandPin",),
}


def main():
    failures = []
    config = CONFIG.read_text(encoding="utf-8")
    constants = {name: int(value, 0) for name, value in re.findall(
        r"constexpr uint(?:8|32)_t (k\w+) = (0x[0-9a-fA-F]+|\d+);", config)}
    index = (WIRING / "README.md").read_text(encoding="utf-8")
    for name, required in GUIDES.items():
        path = WIRING / name
        if not path.is_file() or f"]({name})" not in index:
            failures.append("Missing or unindexed wiring guide: " + name)
            continue
        contents = path.read_text(encoding="utf-8")
        for key in required:
            value = constants[key]
            if key.endswith("Address"):
                pattern = rf"\b0x{value:02x}\b"
            elif key == "kSerialBaud":
                pattern = rf"\b{value}\b"
            else:
                pattern = rf"\bGPIO\s*{value}\b"
            if not re.search(pattern, contents, re.IGNORECASE):
                failures.append(f"{name}: missing current {key} ({value}) reference")

    files = {ROOT / "README.md", ROOT / "RELEASE.md",
             ROOT / "firmware/esp32/UvirSensor/README.md"}
    files.update((ROOT / "docs").rglob("*.md"))
    files.update((ROOT / "hardware").rglob("*.md"))
    checked_links = 0
    for file in sorted(files):
        contents = file.read_text(encoding="utf-8")
        for match in re.finditer(r"\[[^\]\n]*\]\(([^)\n]+)\)", contents):
            target = match.group(1).strip()
            # Optional Markdown link title; current local links use no spaces.
            target = target[1:target.index(">")] if target.startswith("<") else target.split()[0]
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            destination = (file.parent / unquote(parsed.path)).resolve()
            if not destination.is_relative_to(ROOT.resolve()) or not destination.exists():
                failures.append(f"{file.relative_to(ROOT)}: unresolved local link {target}")
            checked_links += 1

    firmware = (ROOT / "firmware/esp32/UvirSensor/UvirSensor.ino").read_text(encoding="utf-8")
    version = re.search(r'kFirmwareVersion\[\] = "([^"]+)"', firmware).group(1)
    for name in ("STANDALONE_SENSOR.md", "USB_SERIAL_PROTOCOL.md"):
        if f"`{version}`" not in (ROOT / "docs" / name).read_text(encoding="utf-8"):
            failures.append(name + ": current firmware source version not documented")
    if failures:
        raise SystemExit("\n".join(failures))
    print(f"Documentation: {len(files)} Markdown files, {checked_links} local links, "
          f"{len(GUIDES)} component guides and firmware {version} references checked.")
    print("Reference coverage only; this does not certify the PCB or measurement accuracy.")


if __name__ == "__main__":
    main()
