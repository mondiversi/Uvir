import ast
import json
import re
import socket
import sqlite3
import string
import sys
import tempfile
import threading
import unittest
import zipfile
from pathlib import Path

sys.path.insert(
    0,
    str(Path(__file__).resolve().parent)
)

import uvir_desktop as uvir


class UvirDesktopTests(unittest.TestCase):
    def test_automatic_acquisitions_are_grouped_by_session(self):
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

        groups = uvir.grouped_acquisition_rows(records)

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

    def test_every_acquisition_has_a_mode_badge(self):
        self.assertEqual(
            uvir.acquisition_badge({"automatic": 1}),
            "A",
        )
        self.assertEqual(
            uvir.acquisition_badge({"automatic": 0}),
            "M",
        )

    def test_legacy_measurements_table_is_renamed(self):
        sample = {
            key: 0.0
            for key in (
                "uvc", "uvb", "uva", "violetto", "blu", "verde",
                "giallo", "arancione", "rosso", "f8", "nir"
            )
        }
        record = {
            "id": 7,
            "timestamp": 100,
            "note": "legacy",
            "automatic": True,
            "sample": sample,
        }

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "legacy.db"
            uvir.create_database_from_remote(path, [record])
            connection = sqlite3.connect(path)
            try:
                connection.execute(
                    "ALTER TABLE acquisitions RENAME TO measurements"
                )
                connection.commit()
            finally:
                connection.close()

            uvir.ensure_schema(path)

            connection = sqlite3.connect(path)
            try:
                tables = {
                    row[0]
                    for row in connection.execute(
                        "SELECT name FROM sqlite_master WHERE type = 'table'"
                    )
                }
                row = connection.execute(
                    "SELECT id, note, automatic FROM acquisitions"
                ).fetchone()
            finally:
                connection.close()

            self.assertIn("acquisitions", tables)
            self.assertNotIn("measurements", tables)
            self.assertEqual(row, (7, "legacy", 1))

    def test_automatic_session_uses_compact_auto_label(self):
        for language in ("it", "en"):
            self.assertEqual(
                uvir.tr(
                    "automatic_session",
                    language=language,
                    id=2,
                    count=3,
                ),
                "AUTO #2 · (3)",
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
            "automatic_session_id": 3,
            "automatic_sequence": 3,
            "sample": sample,
        }

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "uvir.db"
            uvir.create_database_from_remote(
                path,
                [record],
                acquisition_counter=9,
                session_counter=4,
            )
            uvir.ensure_schema(path)

            connection = sqlite3.connect(path)
            connection.row_factory = sqlite3.Row
            try:
                saved = connection.execute(
                    "SELECT * FROM acquisitions"
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
                    3,
                    3,
                    1.25
                )
            )

            round_trip = uvir.record_to_remote_dict(saved)
            self.assertEqual(
                round_trip["automatic_session_id"],
                3
            )
            self.assertEqual(
                round_trip["automatic_sequence"],
                3
            )
            self.assertEqual(
                uvir.EXPORT_COLUMNS[:3],
                ["Acquisition_ID", "Session_ID", "Date/Time"]
            )
            self.assertEqual(
                uvir.export_row(saved)[:2],
                [1, 3]
            )
            exported = uvir.export_row(saved)
            self.assertRegex(
                exported[2],
                r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$"
            )
            self.assertEqual(exported[4], "Automatic")
            self.assertEqual(
                len(exported),
                len(uvir.EXPORT_COLUMNS)
            )
            self.assertEqual(
                exported[24],
                uvir.BIOLOGICAL_MODEL_VERSION
            )
            self.assertEqual(
                uvir.database_counters(path),
                (9, 4)
            )

    def test_empty_refresh_preserves_acquisition_sequence(self):
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
                ],
                acquisition_counter=27,
                session_counter=8,
            )

            uvir.create_database_from_remote(path, [])

            connection = sqlite3.connect(path)
            try:
                sequence = connection.execute(
                    "SELECT seq FROM sqlite_sequence WHERE name = ?",
                    ("acquisitions",)
                ).fetchone()
            finally:
                connection.close()

            self.assertEqual(sequence, (27,))
            self.assertEqual(
                uvir.database_counters(path),
                (27, 8)
            )

            uvir.create_database_from_remote(
                path,
                [],
                acquisition_counter=0,
                session_counter=0,
            )
            self.assertEqual(
                uvir.database_counters(path),
                (0, 0)
            )

    def test_legacy_session_ids_are_migrated_to_sequence(self):
        sample = {
            key: 0.0
            for key in (
                "uvc", "uvb", "uva", "violetto", "blu", "verde",
                "giallo", "arancione", "rosso", "f8", "nir"
            )
        }
        records = [
            {
                "id": 1,
                "timestamp": 100,
                "note": "",
                "automatic": True,
                "automatic_session_id": 1_700_000_000_000,
                "automatic_sequence": 1,
                "sample": sample,
            },
            {
                "id": 2,
                "timestamp": 200,
                "note": "",
                "automatic": True,
                "automatic_session_id": 1_800_000_000_000,
                "automatic_sequence": 1,
                "sample": sample,
            },
        ]

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "legacy.db"
            uvir.create_database_from_remote(path, records)
            connection = sqlite3.connect(path)
            try:
                connection.execute("DROP TABLE uvir_counters")
                connection.commit()
            finally:
                connection.close()

            uvir.ensure_schema(path)

            connection = sqlite3.connect(path)
            try:
                session_ids = connection.execute(
                    """
                    SELECT automatic_session_id
                    FROM acquisitions
                    ORDER BY timestamp
                    """
                ).fetchall()
            finally:
                connection.close()

            self.assertEqual(session_ids, [(1,), (2,)])
            self.assertEqual(
                uvir.database_counters(path),
                (2, 2)
            )

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

    def test_language_detection_and_translation_fallback(self):
        self.assertEqual(
            uvir.detect_system_language("it_IT"),
            "it"
        )
        self.assertEqual(
            uvir.detect_system_language("Italian_Italy"),
            "it"
        )
        self.assertEqual(
            uvir.detect_system_language("en_US"),
            "en"
        )
        self.assertEqual(
            uvir.detect_system_language("fr_FR"),
            "en"
        )
        self.assertEqual(
            uvir.tr("open_database", language="it"),
            "Apri database…"
        )
        self.assertEqual(
            uvir.tr("open_database", language="en"),
            "Open database…"
        )

    def test_local_settings_round_trip_and_invalid_file(self):
        self.assertEqual(
            uvir.SETTINGS_FILE.parent,
            Path(uvir.__file__).resolve().parent
        )

        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "desktop_settings.json"
            expected = {
                "wifi_address": "192.168.1.20",
                "wifi_code": "12345678",
                "bluetooth_address": "192.168.44.1",
                "last_connection_method": "wifi",
                "automatic_limit_enabled": True,
            }

            uvir.save_local_settings(expected, path)
            self.assertEqual(
                uvir.load_local_settings(path),
                expected
            )

            path.write_text("not-json", encoding="utf-8")
            self.assertEqual(
                uvir.load_local_settings(path),
                {}
            )

    def test_translation_catalog_and_export_variants_match(self):
        formatter = string.Formatter()
        self.assertEqual(
            set(uvir.TRANSLATIONS),
            {"it", "en"}
        )
        self.assertEqual(
            set(uvir.TRANSLATIONS["it"]),
            set(uvir.TRANSLATIONS["en"])
        )

        for key in uvir.TRANSLATIONS["it"]:
            italian = uvir.TRANSLATIONS["it"][key]
            english = uvir.TRANSLATIONS["en"][key]
            italian_fields = {
                name
                for _, name, _, _ in formatter.parse(italian)
                if name
            }
            english_fields = {
                name
                for _, name, _, _ in formatter.parse(english)
                if name
            }
            self.assertEqual(italian_fields, english_fields)
            self.assertTrue(italian)
            self.assertTrue(english)

        self.assertEqual(
            len(uvir.EXPORT_COLUMNS_IT),
            len(uvir.EXPORT_COLUMNS_EN)
        )
        self.assertEqual(len(uvir.EXPORT_COLUMNS_IT), 31)
        self.assertEqual(uvir.DATA_EXPORT_LANGUAGE, "en")
        self.assertEqual(
            uvir.EXPORT_COLUMNS,
            uvir.EXPORT_COLUMNS_EN
        )
        self.assertEqual(
            uvir.LEGEND_ROWS,
            uvir.LEGEND_ROWS_EN
        )
        self.assertEqual(
            (uvir.ACQUISITIONS_SHEET, uvir.LEGEND_SHEET),
            ("Acquisitions", "Legend")
        )
        self.assertEqual(
            len(uvir.LEGEND_ROWS_IT),
            len(uvir.LEGEND_ROWS_EN)
        )

        source = Path(uvir.__file__).read_text(encoding="utf-8")
        tree = ast.parse(source)
        used_keys = {
            node.args[0].value
            for node in ast.walk(tree)
            if (
                isinstance(node, ast.Call)
                and isinstance(node.func, ast.Name)
                and node.func.id == "tr"
                and node.args
                and isinstance(node.args[0], ast.Constant)
                and isinstance(node.args[0].value, str)
            )
        }
        self.assertTrue(used_keys)
        self.assertFalse(
            used_keys - set(uvir.TRANSLATIONS["en"])
        )

        self.assertEqual(
            uvir.load_language_catalog("it"),
            uvir.TRANSLATIONS["it"]
        )
        self.assertEqual(
            uvir.load_language_catalog("en"),
            uvir.TRANSLATIONS["en"]
        )

        android_source = (
            Path(uvir.__file__).resolve().parent.parent
            / "app" / "src" / "main" / "java"
            / "me" / "mondiversi" / "uvir"
            / "MainActivity.kt"
        ).read_text(encoding="utf-8")

        def android_columns(name: str) -> list[str]:
            match = re.search(
                rf"internal val {name}\s*=\s*listOf\((.*?)\n\s*\)",
                android_source,
                re.DOTALL,
            )
            self.assertIsNotNone(match)
            return re.findall(r'"([^"]+)"', match.group(1))

        self.assertEqual(
            android_columns("MEASUREMENT_EXPORT_COLUMNS_IT"),
            uvir.EXPORT_COLUMNS_IT,
        )
        self.assertEqual(
            android_columns("MEASUREMENT_EXPORT_COLUMNS_EN"),
            uvir.EXPORT_COLUMNS_EN,
        )

        with tempfile.TemporaryDirectory() as folder:
            xlsx = Path(folder) / "test.xlsx"
            ods = Path(folder) / "test.ods"
            uvir.write_xlsx(xlsx, [])
            uvir.write_ods(ods, [])

            with zipfile.ZipFile(xlsx) as archive:
                workbook = archive.read(
                    "xl/workbook.xml"
                ).decode("utf-8")
            self.assertIn(uvir.ACQUISITIONS_SHEET, workbook)
            self.assertIn(uvir.LEGEND_SHEET, workbook)

            with zipfile.ZipFile(ods) as archive:
                content = archive.read(
                    "content.xml"
                ).decode("utf-8")
            self.assertIn(uvir.ACQUISITIONS_SHEET, content)
            self.assertIn(uvir.LEGEND_SHEET, content)


if __name__ == "__main__":
    unittest.main()
