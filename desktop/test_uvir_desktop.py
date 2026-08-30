import json
import socket
import sqlite3
import sys
import tempfile
import threading
import unittest
from pathlib import Path

sys.path.insert(
    0,
    str(Path(__file__).resolve().parent)
)

import uvir_desktop as uvir


class UvirDesktopTests(unittest.TestCase):
    def test_automatic_measurements_are_grouped_by_session(self):
        records = [
            {
                "id": 5,
                "automatic": 1,
                "automatic_session_id": 2_000
            },
            {
                "id": 4,
                "automatic": 0,
                "automatic_session_id": None
            },
            {
                "id": 3,
                "automatic": 1,
                "automatic_session_id": 2_000
            },
            {
                "id": 2,
                "automatic": 1,
                "automatic_session_id": None
            },
        ]

        groups = uvir.grouped_measurement_rows(records)

        self.assertEqual(
            [
                (session_id, [row["id"] for row in rows])
                for session_id, rows in groups
            ],
            [
                (2_000, [5, 3]),
                (None, [4]),
                (None, [2]),
            ]
        )

    def test_remote_records_create_compatible_database(self):
        sample = {
            key: 1.25
            for key in (
                "uvc", "uvb", "uva", "violetto", "blu", "verde",
                "giallo", "arancione", "rosso", "f8", "nir"
            )
        }
        record = {
            "id": 1,
            "timestamp": 1_234_567_890_000,
            "note": "test",
            "automatic": True,
            "automatic_session_id": 1_234_567_800_000,
            "automatic_sequence": 3,
            "sample": sample,
        }

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "uvir.db"
            uvir.create_database_from_remote(path, [record])
            uvir.ensure_schema(path)

            connection = sqlite3.connect(path)
            connection.row_factory = sqlite3.Row
            try:
                saved = connection.execute(
                    "SELECT * FROM measurements"
                ).fetchone()
            finally:
                connection.close()

            self.assertEqual(
                (
                    saved["id"],
                    saved["note"],
                    saved["automatic"],
                    saved["automatic_session_id"],
                    saved["automatic_sequence"],
                    saved["uvc"]
                ),
                (
                    1,
                    "test",
                    1,
                    1_234_567_800_000,
                    3,
                    1.25
                )
            )

            round_trip = uvir.record_to_remote_dict(saved)
            self.assertEqual(
                round_trip["automatic_session_id"],
                1_234_567_800_000
            )
            self.assertEqual(
                round_trip["automatic_sequence"],
                3
            )
            self.assertEqual(
                uvir.EXPORT_COLUMNS[:3],
                ["ID_misurazione", "ID_sessione", "Data/Ora"]
            )
            self.assertEqual(
                uvir.export_row(saved)[:2],
                [1, 1_234_567_800_000]
            )

    def test_empty_refresh_preserves_measurement_sequence(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "uvir.db"
            sample = {
                key: 0.0
                for key in (
                    "uvc", "uvb", "uva", "violetto", "blu", "verde",
                    "giallo", "arancione", "rosso", "f8", "nir"
                )
            }
            uvir.create_database_from_remote(
                path,
                [
                    {
                        "id": 27,
                        "timestamp": 1,
                        "note": "",
                        "automatic": False,
                        "sample": sample,
                    }
                ]
            )

            uvir.create_database_from_remote(path, [])

            connection = sqlite3.connect(path)
            try:
                sequence = connection.execute(
                    "SELECT seq FROM sqlite_sequence WHERE name = ?",
                    ("measurements",)
                ).fetchone()
            finally:
                connection.close()

            self.assertEqual(sequence, (27,))

    def test_remote_json_request(self):
        server = socket.socket()
        server.bind(("127.0.0.1", 0))
        server.listen(1)
        port = server.getsockname()[1]

        def serve_once():
            client, _ = server.accept()
            with client:
                request = json.loads(
                    client.makefile("rb").readline()
                )
                self.assertEqual(request["action"], "ping")
                response = {
                    "ok": True,
                    "data": {
                        "package": uvir.DEFAULT_PACKAGE
                    },
                }
                client.sendall(
                    (
                        json.dumps(response) + "\n"
                    ).encode("utf-8")
                )
            server.close()

        thread = threading.Thread(
            target=serve_once,
            daemon=True
        )
        thread.start()

        response = uvir.remote_request(
            uvir.RemoteLink(
                mode="test",
                host="127.0.0.1",
                port=port
            ),
            "ping"
        )

        thread.join(timeout=2.0)
        self.assertEqual(
            response["package"],
            "me.mondiversi.uvir"
        )


if __name__ == "__main__":
    unittest.main()
