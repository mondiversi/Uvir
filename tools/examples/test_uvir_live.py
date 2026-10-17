"""Host-only tests; no device, network, credentials or measurement archive used."""

import json
import unittest

from uvir_live import MAX_FRAME_BYTES, PROTOCOL, Reader, read_live, sample_output


def frame(kind, **fields):
    return (json.dumps(dict(type=kind, protocol=PROTOCOL, **fields)) + "\n").encode()


class Clock:
    now = 0.0

    def __call__(self):
        return self.now


class FakeChannel:
    def __init__(self, clock, replies=()):
        self.clock = clock
        self.replies = list(replies)
        self.writes = []

    def read(self):
        self.clock.now += 0.25
        return self.replies.pop(0) if self.replies else b""

    def write(self, value):
        self.writes.append(value)


class ReaderTests(unittest.TestCase):
    def make_reader(self, replies=()):
        clock = Clock()
        channel = FakeChannel(clock, replies)
        return Reader(channel, clock), channel

    def test_fragmented_json_and_boot_noise(self):
        reader, _ = self.make_reader([b"Booting\n", b'{"type":"hello",', b'"protocol":"uvir-sensor-v1"}\n'])
        self.assertEqual(reader.wait_for(lambda f: True)["type"], "hello")

    def test_keepalive_runs_without_samples(self):
        reader, channel = self.make_reader()
        reader.owned = True
        reader.next_ping = 4
        self.assertIsNone(reader.receive(10))
        self.assertEqual(channel.writes, [b"PING\n", b"PING\n"])

    def test_rejects_wrong_protocol(self):
        reader, _ = self.make_reader([b'{"type":"hello","protocol":"other"}\n'])
        with self.assertRaisesRegex(RuntimeError, "protocol"):
            reader.receive(2)

    def test_error_never_echoes_message(self):
        reader, _ = self.make_reader([frame("error", code="auth_failed", message="private-value")])
        with self.assertRaises(RuntimeError) as error:
            reader.receive(2)
        self.assertNotIn("private-value", str(error.exception))

    def test_rejects_overlong_or_multiline_command(self):
        reader, _ = self.make_reader()
        for command in ("A" * 513, "HELLO\nSTOP", "HELLO\rSTOP"):
            with self.assertRaises(ValueError):
                reader.send(command)

    def test_bounds_unterminated_response(self):
        reader, _ = self.make_reader([b"A" * (MAX_FRAME_BYTES + 1)])
        with self.assertRaisesRegex(RuntimeError, "Unterminated"):
            reader.receive(2)

    def test_idle_live_session_has_no_mutations_or_acks(self):
        reader, channel = self.make_reader([
            frame("hello", device_id="EXAMPLE", auth_token="private-value"),
            frame("status", app_connected=True),
            frame("status", streaming=True),
            frame("sample", bands={"uva": 1.2}, auth_token="private-value"),
        ])
        output = []
        read_live(reader, "EXAMPLE", None, 9, 500, output.append)
        self.assertEqual(output[0]["bands"], {"uva": 1.2})
        self.assertNotIn("private-value", json.dumps(output))
        self.assertEqual(channel.writes[:3], [b"HELLO\n", b"APP_CONNECT\n", b"STREAM 500\n"])
        self.assertEqual(channel.writes[-1], b"STOP\n")
        self.assertTrue(all(command.split()[0] in (b"HELLO", b"APP_CONNECT", b"STREAM", b"PING", b"STOP") for command in channel.writes))

    def test_tcp_auth_preserves_case(self):
        reader, channel = self.make_reader([
            frame("hello", device_id="EXAMPLE"),
            frame("status", app_connected=True), frame("status", streaming=True),
        ])
        token = "Gx" * 16  # Synthetic non-hex fixture, not a real credential.
        read_live(reader, "EXAMPLE", token, 1, 500, lambda f: None)
        self.assertEqual(channel.writes[0], ("AUTH " + token + "\n").encode())

    def test_wrong_identity_never_claims_device(self):
        reader, channel = self.make_reader([frame("hello", device_id="OTHER")])
        with self.assertRaisesRegex(RuntimeError, "identity"):
            read_live(reader, "EXPECTED", None, 1, 500, lambda f: None)
        self.assertEqual(channel.writes, [b"HELLO\n"])

    def test_busy_device_never_claims_session(self):
        for flag in ("app_connected", "offline_recording", "alert_monitoring_enabled", "operation_active"):
            reader, channel = self.make_reader([frame("hello", device_id="EXAMPLE", **{flag: True})])
            with self.assertRaisesRegex(RuntimeError, "busy"):
                read_live(reader, None, None, 1, 500, lambda f: None)
            self.assertEqual(channel.writes, [b"HELLO\n"])

    def test_cleanup_on_interrupted_acknowledgement(self):
        reader, channel = self.make_reader([frame("hello", device_id="EXAMPLE")])
        with self.assertRaises(TimeoutError):
            read_live(reader, None, None, 1, 500, lambda f: None)
        self.assertEqual(channel.writes[-1], b"STOP\n")
        self.assertFalse(reader.owned)

    def test_output_strips_unknown_and_nonfinite_values(self):
        output = sample_output({"seq": 3, "auth_token": "private-value", "bands": {
            "uva": 1.0, "nir": float("nan"), "blue": True, "secret": "private-value"}})
        self.assertEqual(output["bands"], {"uva": 1.0})
        self.assertNotIn("private-value", json.dumps(output, allow_nan=False))


if __name__ == "__main__":
    unittest.main()
