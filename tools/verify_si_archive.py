"""Compare private before/after archive snapshots without printing their contents."""
import argparse
import json
import math
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

BANDS = {"uvc", "uvb", "uva", "violetto", "blu", "verde", "giallo", "arancione", "rosso", "f8", "nir"}


def equivalent(old, new, scale, label, tolerance=1e-12):
    assert math.isfinite(float(old)) and math.isfinite(float(new)), f"Nonfinite value: {label}"
    assert math.isclose(float(old) * scale, float(new), rel_tol=tolerance, abs_tol=1e-15), f"Physical value changed: {label}"


def alert_details(old, new):
    left, right = old.split(";"), new.split(";")
    assert len(left) == len(right), "Alert violation count changed"
    for before, after in zip(left, right):
        a, b = before.split("|"), after.split("|")
        if len(a) != 4:
            assert before == after, "Opaque alert fragment changed"
            continue
        try:
            float(a[1]); float(a[3])
        except ValueError:
            assert before == after, "Opaque alert fragment changed"
            continue
        assert len(b) == 4 and a[0] == b[0] and a[2] == b[2], "Alert rule identity changed"
        equivalent(a[1], b[1], .01, "alert value")
        equivalent(a[3], b[3], .01, "alert threshold")


def offsets(old, new):
    if old is None or new is None:
        assert old == new, "Offset presence changed"
        return
    a, b = json.loads(old), json.loads(new)
    assert a["version"] == b["version"] and len(a["values"]) == len(b["values"])
    assert b["unit"] == "W/m2"
    assert a["unit"] in ("W/m2", "uW/cm2")
    for x, y in zip(a["values"], b["values"]):
        equivalent(x, y, .01 if a["unit"] == "uW/cm2" else 1, "offset", tolerance=2e-7)


def compare(before: Path, after: Path):
    with sqlite3.connect(f"file:{before.as_posix()}?mode=ro", uri=True) as a, sqlite3.connect(
            f"file:{after.as_posix()}?mode=ro", uri=True) as b:
        a.row_factory = b.row_factory = sqlite3.Row
        assert a.execute("PRAGMA user_version").fetchone()[0] == 24
        assert b.execute("PRAGMA user_version").fetchone()[0] == 25
        for connection in (a, b):
            assert connection.execute("PRAGMA quick_check").fetchone()[0] == "ok"
        tables = [row[0] for row in a.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        count = 0
        for table in tables:
            quoted = '"' + table.replace('"', '""') + '"'
            left = list(a.execute(f"SELECT * FROM {quoted} ORDER BY rowid"))
            right = list(b.execute(f"SELECT * FROM {quoted} ORDER BY rowid"))
            assert len(left) == len(right), f"Row count changed: {table}"
            for old, new in zip(left, right):
                assert old.keys() == new.keys(), f"Schema changed: {table}"
                for key in old.keys():
                    label = f"{table}/{key}"
                    if (table == "acquisitions" and key in BANDS) or (table == "sensor_alert_rules" and key == "threshold"):
                        equivalent(old[key], new[key], .01, label)
                    elif table == "alerts" and key == "details":
                        alert_details(old[key], new[key])
                    elif table == "sensor_settings" and key == "calibration_offsets":
                        offsets(old[key], new[key])
                    elif (table, key) in (("sensor_settings", "last_synced_at"), ("sensors", "last_seen_at")):
                        continue  # Normal connection/readback metadata, never authority dates.
                    elif table == "sensor_settings" and key == "firmware_version" and old[key] == "0.5.111" and new[key] == "0.5.112":
                        continue
                    else:
                        assert old[key] == new[key], f"Non-irradiance data changed: {label}"
                count += 1
        return {"verified": True, "rows_checked": count, "tables_checked": len(tables),
            "acquisitions": a.execute("SELECT COUNT(*) FROM acquisitions").fetchone()[0],
            "alerts": a.execute("SELECT COUNT(*) FROM alerts").fetchone()[0]}


def preferences(before: Path, after: Path):
    def read(path):
        return {item.attrib["name"]: item.attrib.get("value", item.text or "") for item in ET.parse(path).getroot()}
    left, right = read(before), read(after)
    assert left.get("irradiance_storage_unit") is None
    assert right["irradiance_storage_unit"] == "W/m2"
    checked = 0
    for key, old in left.items():
        base = key.split(".", 2)[-1] if key.startswith("selected_sensor_context.") else key
        if base == "threshold_alert_value" or (base.startswith("threshold_alert_rule_") and base.endswith("_value")):
            equivalent(old, right[key], .01, "preference threshold", tolerance=2e-7)
            checked += 1
        elif base == "auto_conditional_rules":
            a, b = json.loads(old), json.loads(right[key])
            assert len(a) == len(b)
            for x, y in zip(a, b):
                equivalent(x["threshold"], y["threshold"], .01, "conditional rule", tolerance=2e-7)
                assert {k: v for k, v in x.items() if k != "threshold"} == {k: v for k, v in y.items() if k != "threshold"}
            checked += 1
        elif base == "auto_conditional_plan":
            if old:
                a, b = old.split("|"), right[key].split("|")
                assert a[:2] == b[:2] and len(a) == len(b) == 3
                x, y = a[2].split(";"), b[2].split(";")
                assert len(x) == len(y)
                for i, j in zip(x, y):
                    m, n = i.split(","), j.split(",")
                    assert m[:2] == n[:2] and len(m) == len(n) == 3
                    equivalent(m[2], n[2], .01, "conditional plan", tolerance=2e-7)
            else:
                assert right[key] == old
            checked += 1
        elif base in ("irradiance_unit", "numeric_format") or base.startswith("irradiance_decimals_"):
            assert right[key] == old, "Display preference changed"
            checked += 1
    return {"preferences_verified": True, "irradiance_and_display_keys_checked": checked}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--before-preferences", type=Path)
    parser.add_argument("--after-preferences", type=Path)
    args = parser.parse_args()
    print(json.dumps(compare(args.before, args.after)))
    if args.before_preferences or args.after_preferences:
        assert args.before_preferences and args.after_preferences
        print(json.dumps(preferences(args.before_preferences, args.after_preferences)))


if __name__ == "__main__":
    main()
