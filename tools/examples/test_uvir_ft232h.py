"""Synthetic register fixtures only: no USB access, drivers or app data."""

from contextlib import redirect_stderr, redirect_stdout
import io
import json
import types
import unittest
from unittest.mock import patch

from uvir_ft232h import UvSensor, VisibleSensor, arguments, capture, main


def word_bytes(values):
    return b"".join(value.to_bytes(2, "little") for value in values)


class Port:
    def __init__(self, registers):
        self.registers = dict(registers)
        self.reads, self.writes = [], []

    def exchange(self, outgoing, count):
        register = outgoing[0]
        self.reads.append((register, count))
        value = self.registers[register]
        if isinstance(value, list):
            value = value.pop(0)
        if isinstance(value, Exception):
            raise value
        return value

    def write(self, outgoing):
        self.writes.append(bytes(outgoing))


class RawReaderTests(unittest.TestCase):
    def test_uv_word_order_and_fixed_gain_are_explicit(self):
        port = Port({0x02: [b"\x21", word_bytes([123, 456, 789])],
                     0x00: word_bytes([0x0803])})
        sensor = UvSensor(port, gain=64, sleep=lambda _: None)
        self.assertEqual(sensor.initialize(), 0x21)
        frame = sensor.capture()
        self.assertEqual(frame["counts"], {"UVA": 123, "UVB": 456, "UVC": 789})
        self.assertEqual(frame["gain"], 64)
        self.assertTrue(frame["valid"])
        self.assertIn(bytes([0x06, 0x56]), port.writes)
        self.assertEqual(port.reads[-1], (0x02, 6))

    def test_uv_invalid_or_incomplete_data_never_becomes_zero(self):
        for status, data in ((0x0403, b""), (0x8003, b""), (0x0003, b""),
                             (0x0803, b"\x01\x02")):
            port = Port({0x00: word_bytes([status]), 0x02: data})
            with self.assertRaises(IOError):
                UvSensor(port, sleep=lambda _: None).capture()

    def test_uv_overflow_retains_raw_counts_but_marks_them_invalid(self):
        for status, counts in ((0x2803, [10, 20, 30]), (0x4803, [10, 20, 30]),
                               (0x0803, [65534, 0, 0])):
            port = Port({0x00: word_bytes([status]), 0x02: word_bytes(counts)})
            result = UvSensor(port, sleep=lambda _: None).capture()
            self.assertEqual(result["status"], "saturation")
            self.assertFalse(result["valid"])
            self.assertEqual(result["counts"]["UVA"], counts[0])

    def test_visible_rejects_previous_gain_then_decodes_coherent_snapshot(self):
        port = Port({0xC6: b"\x00", 0x90: b"\x40", 0x94:
                     [b"\x09" + word_bytes([999] * 18), b"\x00" + word_bytes(range(18))]})
        result = VisibleSensor(port, gain_index=0, sleep=lambda _: None).capture()
        self.assertTrue(result["valid"])
        self.assertEqual(result["gain"], 0.5)
        self.assertEqual(result["counts"], dict(F1=12, F2=6, FZ=0, F3=7, F4=8,
                         F5=15, FY=1, FXL=2, F6=9, F7=13, F8=14, NIR=3))
        self.assertEqual(port.reads.count((0x94, 37)), 2)
        self.assertEqual(port.writes[-1], bytes([0x80, 0]))

    def test_clear_adc_overload_also_marks_visible_invalid(self):
        raw = [1] * 18
        raw[4] = 10000  # Clear channel, not one of the named spectral channels.
        for status, data in ((0, raw), (0x80, [1] * 18)):
            port = Port({0xC6: b"\x00", 0x90: b"\x40", 0x94: bytes([status]) + word_bytes(data)})
            result = VisibleSensor(port, sleep=lambda _: None).capture()
            self.assertEqual(result["status"], "saturation")
            self.assertFalse(result["valid"])

    def test_visible_timeout_and_short_frame_always_stop_conversion(self):
        for ready, frame in ((0, b""), (0x40, b"\x00")):
            port = Port({0xC6: b"\x00", 0x90: bytes([ready]), 0x94: frame})
            with self.assertRaises(IOError):
                VisibleSensor(port, sleep=lambda _: None).capture()
            self.assertEqual(port.writes[-1], bytes([0x80, 0]))
            self.assertLessEqual(len(port.reads), 42)

    def test_failed_visible_does_not_remove_good_uv(self):
        port = Port({0x00: word_bytes([0x0803]), 0x02: word_bytes([1, 2, 3])})
        error = {"status": "error", "valid": False, "message": "No AS7343"}
        result = capture({"uv": UvSensor(port, sleep=lambda _: None)}, {"vis_ir": error})
        self.assertTrue(result["uv"]["valid"])
        self.assertEqual(result["vis_ir"], error)
        self.assertEqual(result["unit"], "raw_counts")
        self.assertNotIn("w_m2", str(result))

    def test_wrong_chip_restores_bank_and_is_not_accepted(self):
        port = Port({0xBF: b"\x00", 0x5A: b"\x09"})
        with self.assertRaisesRegex(IOError, "identity"):
            VisibleSensor(port, sleep=lambda _: None).initialize()
        self.assertEqual(port.writes[-1], bytes([0xBF, 0]))
        port = Port({0x02: b"\x00"})
        with self.assertRaisesRegex(IOError, "identity"):
            UvSensor(port, sleep=lambda _: None).initialize()

    def test_defaults_and_continuous_mode_without_importing_usb(self):
        self.assertEqual(arguments(["probe"]).visible_gain_index, 0)
        self.assertEqual(arguments(["read", "--samples", "0"]).samples, 0)

    def test_cli_returns_partial_sample_and_releases_usb(self):
        class Controller:
            closed = False

            def configure(self, *args, **kwargs):
                pass

            def get_port(self, address):
                if address == 0x74:
                    return Port({0x02: b"\x00"})  # Missing/wrong UV chip.
                return Port({0xBF: b"\x00", 0x5A: b"\x81", 0xCD: b"\x00",
                             0xD6: b"\x00", 0xC6: b"\x00", 0x90: b"\x40",
                             0x94: b"\x00" + word_bytes(range(18))})

            def close(self):
                self.closed = True

        controller = Controller()
        modules = {"pyftdi": types.ModuleType("pyftdi"),
                   "pyftdi.i2c": types.SimpleNamespace(I2cController=lambda: controller)}
        output = io.StringIO()
        with patch.dict("sys.modules", modules), redirect_stdout(output):
            self.assertEqual(main(["read", "--samples", "1"]), 1)
        result = json.loads(output.getvalue())
        self.assertEqual(result["uv"]["status"], "error")
        self.assertTrue(result["vis_ir"]["valid"])
        self.assertEqual(result["unit"], "raw_counts")
        self.assertTrue(controller.closed)

    def test_backend_failure_reports_original_error_even_if_close_also_fails(self):
        class Controller:
            def configure(self, *args, **kwargs):
                raise OSError("USB backend unavailable")

            def close(self):
                raise OSError("Already closed")

        modules = {"pyftdi": types.ModuleType("pyftdi"),
                   "pyftdi.i2c": types.SimpleNamespace(I2cController=Controller)}
        error = io.StringIO()
        with patch.dict("sys.modules", modules), redirect_stderr(error):
            self.assertEqual(main(["probe"]), 1)
        self.assertIn("USB backend unavailable", error.getvalue())
        self.assertNotIn("Already closed", error.getvalue())


if __name__ == "__main__":
    unittest.main()
