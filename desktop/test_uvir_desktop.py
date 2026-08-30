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
            "automatic": False,
            "sample": sample,
        }

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "uvir.db"
            uvir.create_database_from_remote(path, [record])
            uvir.ensure_schema(path)

            connection = sqlite3.connect(path)
            try:
                saved = connection.execute(
                    "SELECT id, note, automatic, uvc FROM measurements"
                ).fetchone()
            finally:
                connection.close()

            self.assertEqual(saved, (1, "test", 0, 1.25))

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
