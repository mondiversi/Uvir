#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Uvir Desktop

GUI desktop per il database SQLite dell'app Android Uvir.

Funzioni:
- apre un database SQLite locale;
- collega Uvir via USB ADB, ADB wireless, Wi-Fi o Bluetooth PAN;
- mostra sul PC i canali dei sensori e gli effetti stimati in tempo reale;
- controlla da remoto salvataggio, acquisizione automatica e schermata aperta;
- scarica una copia SQLite locale anche dalle build Android release;
- raggruppa nell'elenco le misurazioni appartenenti alla stessa sessione automatica;
- mostra data/ora, nota, dettaglio di irradianza ed effetti biologici stimati;
- esporta CSV, XLSX e ODS senza librerie Python esterne;
- modifica note, elimina record e sincronizza il database sul telefono;
- crea backup prima delle operazioni distruttive.

Richiede solo Python standard. ADB è opzionale.
"""

from __future__ import annotations

import csv
import sys
import json
import locale
import os
import queue
import shutil
import socket
import sqlite3
import subprocess
import threading
import time
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from tkinter import (
    Tk, Toplevel, StringVar, BooleanVar, PhotoImage,
    filedialog, messagebox, simpledialog, END, LEFT
)
from tkinter import ttk
from xml.sax.saxutils import escape

APP_TITLE = "Uvir Desktop"
DB_FILENAME = "uvir.db"
DEFAULT_PACKAGE = "me.mondiversi.uvir"
REMOTE_PORT = 45871
WINDOWS_APP_ID = "Uvir.Desktop.2026.1"
SESSION_COUNTER_NAME = "automatic_session_id"
SETTINGS_FILE = Path(__file__).resolve().parent / "desktop_settings.json"
BIOLOGICAL_MODEL_VERSION = "v1"
DATA_EXPORT_LANGUAGE = "en"


LANG_DIR = Path(__file__).resolve().parent / "lang"


def load_language_catalog(
    language: str,
    directory: Path = LANG_DIR,
) -> dict[str, str]:
    path = directory / f"{language}.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError) as error:
        raise RuntimeError(
            f"Unable to load language file: {path}"
        ) from error

    if not isinstance(data, dict) or any(
        not isinstance(key, str) or not isinstance(value, str)
        for key, value in data.items()
    ):
        raise RuntimeError(
            f"Invalid language file: {path}"
        )
    return data


TRANSLATIONS = {
    language: load_language_catalog(language)
    for language in ("it", "en")
}

if TRANSLATIONS["it"].keys() != TRANSLATIONS["en"].keys():
    raise RuntimeError(
        "Italian and English language files contain different keys."
    )


def detect_system_language(locale_name: str | None = None) -> str:
    if locale_name is None:
        locale_name = locale.getlocale()[0]
        if not locale_name:
            locale_name = os.environ.get("LANG", "")
    normalized = str(locale_name or "").lower()
    return "it" if normalized.startswith("it") or "italian" in normalized else "en"


LANGUAGE = detect_system_language()


def tr(key: str, language: str | None = None, **values) -> str:
    selected = language or LANGUAGE
    catalog = TRANSLATIONS.get(
        selected,
        TRANSLATIONS["en"]
    )
    template = catalog.get(
        key,
        TRANSLATIONS["en"][key]
    )
    return template.format(**values)


def load_local_settings(path: Path = SETTINGS_FILE) -> dict:
    try:
        if not path.exists():
            return {}
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError, TypeError):
        return {}


def save_local_settings(data: dict, path: Path = SETTINGS_FILE) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)


def resource_path(filename: str) -> Path:
    """Percorso valido sia da .py sia da EXE PyInstaller --onefile."""
    if hasattr(sys, "_MEIPASS"):
        return Path(getattr(sys, "_MEIPASS")) / filename

    return Path(__file__).resolve().parent / filename


def configure_windows_app_id() -> None:
    """Fa usare a Windows l'identità/icone di Uvir sulla taskbar."""
    if os.name != "nt":
        return

    try:
        import ctypes
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(
            WINDOWS_APP_ID
        )
    except Exception:
        pass


def apply_uvir_icon(root: Tk) -> None:
    """Applica l'icona Uvir alla finestra Tkinter."""
    ico_path = resource_path("Uvir_windows.ico")
    png_path = resource_path("Uvir_window_64.png")

    # Su Windows iconbitmap gestisce bene titolo/Alt-Tab.
    if ico_path.exists():
        try:
            root.iconbitmap(default=str(ico_path))
        except Exception:
            pass

    # iconphoto aiuta soprattutto taskbar e scaling ad alta risoluzione.
    if png_path.exists():
        try:
            icon_image = PhotoImage(file=str(png_path))
            root.iconphoto(True, icon_image)

            # Mantiene vivo il riferimento PhotoImage.
            root._uvir_icon_image = icon_image
        except Exception:
            pass


EXPECTED_COLUMNS = {
    "id", "timestamp", "note", "uvc", "uvb", "uva",
    "violetto", "blu", "verde", "giallo", "arancione", "rosso",
    "f8", "nir"
}

EXPORT_COLUMNS_IT = [
    "ID_misurazione", "ID_sessione", "Data/Ora", "Timestamp_ms",
    "Tipo_acquisizione", "Automatico", "Nota", "Progressivo_sessione",
    "UVC_100_280_nm_uW_cm2", "UVB_280_315_nm_uW_cm2", "UVA_315_400_nm_uW_cm2",
    "UV_totale_uW_cm2", "HEV_400_500_nm_uW_cm2", "HEB_400_450_nm_uW_cm2",
    "Violetto_400_450_nm_uW_cm2", "Blu_450_495_nm_uW_cm2",
    "Verde_495_570_nm_uW_cm2", "Giallo_570_590_nm_uW_cm2",
    "Arancione_590_620_nm_uW_cm2", "Rosso_620_700_nm_uW_cm2",
    "Visibile_totale_uW_cm2", "FarRed_picco_745_nm_uW_cm2",
    "NIR_picco_855_nm_uW_cm2", "FarRed_NIR_totale_uW_cm2",
    "Modello_biologico",
    "Irradianza_pesata_stimata_UV_effetto_DNA_uW_cm2_eq",
    "Indice_spettrale_UV_effetto_DNA_0_100",
    "Irradianza_pesata_stimata_fotoinvecchiamento_UVA_uW_cm2_eq",
    "Indice_spettrale_fotoinvecchiamento_UVA_0_100",
    "Irradianza_pesata_stimata_stress_ossidativo_HEV_uW_cm2_eq",
    "Indice_spettrale_stress_ossidativo_HEV_0_100"
]

EXPORT_COLUMNS_EN = [
    "Measurement_ID", "Session_ID", "Date/Time", "Timestamp_ms",
    "Acquisition_type", "Automatic", "Note", "Session_sequence",
    "UVC_100_280_nm_uW_cm2", "UVB_280_315_nm_uW_cm2", "UVA_315_400_nm_uW_cm2",
    "Total_UV_uW_cm2", "HEV_400_500_nm_uW_cm2", "HEB_400_450_nm_uW_cm2",
    "Violet_400_450_nm_uW_cm2", "Blue_450_495_nm_uW_cm2",
    "Green_495_570_nm_uW_cm2", "Yellow_570_590_nm_uW_cm2",
    "Orange_590_620_nm_uW_cm2", "Red_620_700_nm_uW_cm2",
    "Total_visible_uW_cm2", "FarRed_peak_745_nm_uW_cm2",
    "NIR_peak_855_nm_uW_cm2", "Total_FarRed_NIR_uW_cm2",
    "Biological_model",
    "Estimated_weighted_irradiance_UV_DNA_effect_uW_cm2_eq",
    "Spectral_index_UV_DNA_effect_0_100",
    "Estimated_weighted_irradiance_UVA_photoaging_uW_cm2_eq",
    "Spectral_index_UVA_photoaging_0_100",
    "Estimated_weighted_irradiance_HEV_oxidative_stress_uW_cm2_eq",
    "Spectral_index_HEV_oxidative_stress_0_100"
]

LEGEND_ROWS_IT = [
    ["Gruppo", "Canale", "Banda / picco", "Nota"],
    ["Acquisizione", "Automatico", "0 / 1", "0 = manuale; 1 = automatica"],
    ["Acquisizione", "ID sessione", "Intero univoco", "Identifica le misurazioni appartenenti alla stessa sessione automatica"],
    ["Acquisizione", "Progressivo sessione", "1, 2, 3…", "Posizione della misurazione nella sessione automatica"],
    ["Calcolo", "Modello biologico", "Versione", "Versione del modello usato per gli effetti biologici stimati"],
    ["UV", "UVC", "100–280 nm", "Energia fotonica maggiore"],
    ["UV", "UVB", "280–315 nm", ""],
    ["UV", "UVA", "315–400 nm", ""],
    ["Visibile / derivato", "HEV", "400–500 nm", "Derivato: comprende HEB; non sommare nuovamente al totale"],
    ["Visibile / derivato", "HEB", "400–450 nm", "Derivato: sottoinsieme di HEV; non sommare nuovamente al totale"],
    ["Visibile", "Violetto", "400–450 nm", ""],
    ["Visibile", "Blu", "450–495 nm", ""],
    ["Visibile", "Verde", "495–570 nm", ""],
    ["Visibile", "Giallo", "570–590 nm", ""],
    ["Visibile", "Arancione", "590–620 nm", ""],
    ["Visibile", "Rosso", "620–700 nm", ""],
    ["Far red / NIR", "FAR-RED", "picco 745 nm", "Canale AS7343"],
    ["Far red / NIR", "NIR", "picco 855 nm", "Canale AS7343"],
    ["Effetti stimati", "UV effetto-DNA", "irradianza pesata stimata + rilevanza spettrale 0–100", "La scala 0–100 indica ponderazione spettrale relativa; non percentuale di danno né soglia di sicurezza"],
    ["Effetti stimati", "Fotoinvecchiamento UVA", "irradianza pesata stimata + rilevanza spettrale 0–100", "La scala 0–100 indica ponderazione spettrale relativa; non percentuale di danno né soglia di sicurezza"],
    ["Effetti stimati", "Stress ossidativo HEV", "irradianza pesata stimata + rilevanza spettrale 0–100", "La scala 0–100 indica ponderazione spettrale relativa; non percentuale di danno né soglia di sicurezza"],
]

LEGEND_ROWS_EN = [
    ["Group", "Channel", "Band / peak", "Note"],
    ["Acquisition", "Automatic", "0 / 1", "0 = manual; 1 = automatic"],
    ["Acquisition", "Session ID", "Unique integer", "Identifies measurements belonging to the same automatic session"],
    ["Acquisition", "Session sequence", "1, 2, 3…", "Measurement position within the automatic session"],
    ["Calculation", "Biological model", "Version", "Version of the model used for the estimated biological effects"],
    ["UV", "UVC", "100–280 nm", "Higher photon energy"],
    ["UV", "UVB", "280–315 nm", ""],
    ["UV", "UVA", "315–400 nm", ""],
    ["Visible / derived", "HEV", "400–500 nm", "Derived: includes HEB; do not add it again to the total"],
    ["Visible / derived", "HEB", "400–450 nm", "Derived: HEV subset; do not add it again to the total"],
    ["Visible", "Violet", "400–450 nm", ""],
    ["Visible", "Blue", "450–495 nm", ""],
    ["Visible", "Green", "495–570 nm", ""],
    ["Visible", "Yellow", "570–590 nm", ""],
    ["Visible", "Orange", "590–620 nm", ""],
    ["Visible", "Red", "620–700 nm", ""],
    ["Far red / NIR", "FAR-RED", "745 nm peak", "AS7343 channel"],
    ["Far red / NIR", "NIR", "855 nm peak", "AS7343 channel"],
    ["Estimated effects", "UV DNA effect", "estimated weighted irradiance + spectral relevance 0–100", "The 0–100 scale indicates relative spectral weighting; it is neither a damage percentage nor a safety threshold"],
    ["Estimated effects", "UVA photoaging", "estimated weighted irradiance + spectral relevance 0–100", "The 0–100 scale indicates relative spectral weighting; it is neither a damage percentage nor a safety threshold"],
    ["Estimated effects", "HEV oxidative stress", "estimated weighted irradiance + spectral relevance 0–100", "The 0–100 scale indicates relative spectral weighting; it is neither a damage percentage nor a safety threshold"],
]

EXPORT_COLUMNS = EXPORT_COLUMNS_EN
LEGEND_ROWS = LEGEND_ROWS_EN
MEASUREMENTS_SHEET = "Measurements"
LEGEND_SHEET = "Legend"


@dataclass
class RemoteLink:
    mode: str
    host: str
    port: int
    pin: str = ""
    adb: str | None = None
    device: str | None = None


def format_time(ms: int) -> str:
    try:
        pattern = (
            "%d/%m/%Y %H:%M:%S"
            if LANGUAGE == "it"
            else "%Y-%m-%d %H:%M:%S"
        )
        return datetime.fromtimestamp(ms / 1000.0).strftime(pattern)
    except Exception:
        return str(ms)


def format_export_time(ms: int) -> str:
    try:
        return datetime.fromtimestamp(
            ms / 1000.0
        ).strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return str(ms)


def fnum(v) -> float:
    try:
        return float(v)
    except Exception:
        return 0.0


def is_automatic(row: sqlite3.Row) -> bool:
    try:
        return "automatic" in row.keys() and int(row["automatic"] or 0) != 0
    except Exception:
        return False


def optional_int(row: sqlite3.Row, key: str) -> int | None:
    try:
        if key in row.keys() and row[key] is not None:
            return int(row[key])
    except Exception:
        pass
    return None


def acquisition_type(row: sqlite3.Row) -> str:
    return tr("automatic") if is_automatic(row) else tr("manual")


def export_acquisition_type(row: sqlite3.Row) -> str:
    key = "automatic" if is_automatic(row) else "manual"
    return tr(
        key,
        language=DATA_EXPORT_LANGUAGE
    )


def automatic_session_id(row: sqlite3.Row) -> int | None:
    if not is_automatic(row):
        return None
    return optional_int(row, "automatic_session_id")


def automatic_session_note_name(row: sqlite3.Row) -> str:
    note = str(row["note"] or "").strip()
    sequence = optional_int(row, "automatic_sequence")
    if sequence is not None:
        suffix = f" #{sequence}"
        if note.endswith(suffix):
            note = note[:-len(suffix)].strip()
        elif note == f"#{sequence}":
            note = ""
    return note or tr("automatic_session_fallback")


def grouped_measurement_rows(
    records: list[sqlite3.Row]
) -> list[tuple[int | None, list[sqlite3.Row]]]:
    """Raggruppa le sessioni AUTO alla prima posizione in cui compaiono."""
    sessions: dict[int, list[sqlite3.Row]] = {}
    for record in records:
        session_id = automatic_session_id(record)
        if session_id is not None:
            sessions.setdefault(session_id, []).append(record)

    blocks: list[tuple[int | None, list[sqlite3.Row]]] = []
    emitted_sessions: set[int] = set()
    for record in records:
        session_id = automatic_session_id(record)
        if session_id is None:
            blocks.append((None, [record]))
        elif session_id not in emitted_sessions:
            blocks.append((session_id, sessions[session_id]))
            emitted_sessions.add(session_id)
    return blocks


def derived(row: sqlite3.Row) -> dict[str, float]:
    uvc, uvb, uva = map(fnum, (row["uvc"], row["uvb"], row["uva"]))
    vio, blu, ver = map(fnum, (row["violetto"], row["blu"], row["verde"]))
    gia, ara, ros = map(fnum, (row["giallo"], row["arancione"], row["rosso"]))
    f8, nir = map(fnum, (row["f8"], row["nir"]))
    return {
        "uvc": uvc, "uvb": uvb, "uva": uva,
        "uv_total": uvc + uvb + uva,
        "violetto": vio, "blu": blu, "verde": ver,
        "giallo": gia, "arancione": ara, "rosso": ros,
        "vis_total": vio + blu + ver + gia + ara + ros,
        "heb": vio, "hev": vio + blu,
        "f8": f8, "nir": nir, "nir_total": f8 + nir,
    }


def biological_effects(row: sqlite3.Row) -> dict[str, float]:
    """
    Proxy sperimentali a banda larga.

    Ogni effetto restituisce:
    - un segnale proxy pesato, con unità equivalenti di irradianza
      (i pesi sono adimensionali);
    - un indice spettrale relativo 0–100.

    IMPORTANTE:
    100 NON significa 100% di danno e NON è una soglia di sicurezza.
    L'indice descrive solo quanto lo spettro corrente è pesato verso
    le componenti considerate dal proxy.
    """
    d = derived(row)

    dna_uv = (
        d["uvc"] * 1.00
        + d["uvb"] * 0.60
        + d["uva"] * 0.01
    )

    dna_uv_score = (
        dna_uv / d["uv_total"] * 100.0
        if d["uv_total"] > 0
        else 0.0
    )

    uva_photoaging = (
        d["uva"]
        + d["uvb"] * 0.05
    )

    uva_photoaging_score = (
        uva_photoaging / d["uv_total"] * 100.0
        if d["uv_total"] > 0
        else 0.0
    )

    hev_oxidative = d["hev"]

    hev_oxidative_score = (
        hev_oxidative / d["vis_total"] * 100.0
        if d["vis_total"] > 0
        else 0.0
    )

    return {
        "dna_uv": dna_uv,
        "dna_uv_score": max(0.0, min(100.0, dna_uv_score)),

        "uva_photoaging": uva_photoaging,
        "uva_photoaging_score": max(
            0.0,
            min(100.0, uva_photoaging_score)
        ),

        "hev_oxidative": hev_oxidative,
        "hev_oxidative_score": max(
            0.0,
            min(100.0, hev_oxidative_score)
        ),
    }


def export_row(row: sqlite3.Row) -> list:
    d = derived(row)
    b = biological_effects(row)
    automatic = 1 if is_automatic(row) else 0
    return [
        row["id"],
        optional_int(row, "automatic_session_id"),
        format_export_time(row["timestamp"]),
        row["timestamp"],
        export_acquisition_type(row),
        automatic,
        row["note"] or "",
        optional_int(row, "automatic_sequence"),
        d["uvc"], d["uvb"], d["uva"], d["uv_total"],
        d["hev"], d["heb"],
        d["violetto"], d["blu"], d["verde"], d["giallo"], d["arancione"], d["rosso"],
        d["vis_total"], d["f8"], d["nir"], d["nir_total"],
        BIOLOGICAL_MODEL_VERSION,
        b["dna_uv"],
        b["dna_uv_score"],
        b["uva_photoaging"],
        b["uva_photoaging_score"],
        b["hev_oxidative"],
        b["hev_oxidative_score"],
    ]


def ensure_schema(path: Path) -> None:
    con = sqlite3.connect(path)
    try:
        cols = {r[1] for r in con.execute("PRAGMA table_info(measurements)").fetchall()}
        missing = EXPECTED_COLUMNS - cols
        if missing:
            if LANGUAGE == "it":
                message = "Database non compatibile. Colonne mancanti: "
            else:
                message = "Incompatible database. Missing columns: "
            raise RuntimeError(message + ", ".join(sorted(missing)))

        con.execute(
            """
            CREATE TABLE IF NOT EXISTS uvir_counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """
        )
        session_counter = con.execute(
            "SELECT value FROM uvir_counters WHERE name = ?",
            (SESSION_COUNTER_NAME,)
        ).fetchone()
        if session_counter is None:
            session_ids = [
                int(row[0])
                for row in con.execute(
                    """
                    SELECT automatic_session_id
                    FROM measurements
                    WHERE automatic_session_id IS NOT NULL
                    GROUP BY automatic_session_id
                    ORDER BY MIN(timestamp), automatic_session_id
                    """
                ).fetchall()
            ] if "automatic_session_id" in cols else []

            for new_id, old_id in enumerate(session_ids, 1):
                con.execute(
                    """
                    UPDATE measurements
                    SET automatic_session_id = ?
                    WHERE automatic_session_id = ?
                    """,
                    (-new_id, old_id)
                )
            if session_ids:
                con.execute(
                    """
                    UPDATE measurements
                    SET automatic_session_id = -automatic_session_id
                    WHERE automatic_session_id < 0
                    """
                )

            con.execute(
                "INSERT INTO uvir_counters(name, value) VALUES (?, ?)",
                (SESSION_COUNTER_NAME, len(session_ids))
            )
        con.commit()
    finally:
        con.close()


def database_counters(path: Path) -> tuple[int, int]:
    ensure_schema(path)
    con = sqlite3.connect(path)
    try:
        measurement_row = con.execute(
            "SELECT seq FROM sqlite_sequence WHERE name = ?",
            ("measurements",)
        ).fetchone()
        session_row = con.execute(
            "SELECT value FROM uvir_counters WHERE name = ?",
            (SESSION_COUNTER_NAME,)
        ).fetchone()
        return (
            int(measurement_row[0] or 0) if measurement_row else 0,
            int(session_row[0] or 0) if session_row else 0,
        )
    finally:
        con.close()


def backup_db(path: Path) -> Path:
    """Crea un backup SQLite coerente, includendo anche eventuali dati nel WAL."""
    folder = path.parent / "backups"
    folder.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out = folder / f"{path.stem}_{stamp}.db.bak"

    source = sqlite3.connect(path)
    target = sqlite3.connect(out)
    try:
        source.backup(target)
    finally:
        target.close()
        source.close()

    return out


# --------------------------- ADB ---------------------------

def find_adb() -> str | None:
    candidates = [
        shutil.which("adb"),
        str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"),
        str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"),
    ]
    for c in candidates:
        if c and Path(c).exists():
            return c
    return None


def adb_run(adb: str, args: list[str], data: bytes | None = None, timeout: int = 30):
    return subprocess.run(
        [adb] + args,
        input=data,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )


def adb_device(adb: str) -> str:
    p = adb_run(adb, ["devices"])
    for line in p.stdout.decode(errors="replace").splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    raise RuntimeError(
        "Nessun telefono autorizzato rilevato da ADB."
        if LANGUAGE == "it"
        else "No ADB-authorized phone was detected."
    )


# ------------------------ REMOTE API ------------------------

def adb_pair(adb: str, address: str, code: str) -> str:
    if not address.strip() or not code.strip():
        raise RuntimeError(
            "Inserisci indirizzo di abbinamento e codice ADB."
            if LANGUAGE == "it"
            else "Enter the pairing address and ADB code."
        )
    p = adb_run(adb, ["pair", address.strip(), code.strip()], timeout=45)
    detail = (p.stdout + p.stderr).decode(errors="replace").strip()
    if p.returncode != 0 or "success" not in detail.lower():
        raise RuntimeError(
            (
                "Abbinamento ADB non riuscito.\n\n"
                if LANGUAGE == "it"
                else "ADB pairing failed.\n\n"
            )
            + detail
        )
    return detail


def adb_connect_wireless(adb: str, address: str) -> str:
    if not address.strip():
        raise RuntimeError(
            "Inserisci l'indirizzo IP:porta del debug wireless."
            if LANGUAGE == "it"
            else "Enter the Wireless debugging IP address and port."
        )
    p = adb_run(adb, ["connect", address.strip()], timeout=30)
    detail = (p.stdout + p.stderr).decode(errors="replace").strip()
    low = detail.lower()
    if p.returncode != 0 or not ("connected" in low or "already" in low):
        raise RuntimeError(
            (
                "Connessione ADB wireless non riuscita.\n\n"
                if LANGUAGE == "it"
                else "Wireless ADB connection failed.\n\n"
            )
            + detail
        )
    return address.strip()


def adb_start_app(adb: str, device: str, package: str) -> None:
    p = adb_run(
        adb,
        ["-s", device, "shell", "am", "start", "-n", f"{package}/.MainActivity"],
        timeout=30,
    )
    if p.returncode != 0:
        detail = (p.stdout + p.stderr).decode(errors="replace").strip()
        raise RuntimeError(
            (
                f"Non riesco ad avviare {package} sul telefono.\n\n{detail}"
                if LANGUAGE == "it"
                else f"Unable to start {package} on the phone.\n\n{detail}"
            )
        )


def adb_prepare_remote(adb: str, device: str, package: str) -> RemoteLink:
    adb_start_app(adb, device, package)
    p = adb_run(
        adb,
        ["-s", device, "forward", f"tcp:{REMOTE_PORT}", f"tcp:{REMOTE_PORT}"],
        timeout=30,
    )
    if p.returncode != 0:
        detail = (p.stdout + p.stderr).decode(errors="replace").strip()
        raise RuntimeError(
            (
                "Port forwarding ADB non riuscito.\n\n"
                if LANGUAGE == "it"
                else "ADB port forwarding failed.\n\n"
            )
            + detail
        )
    return RemoteLink(
        mode="adb",
        host="127.0.0.1",
        port=REMOTE_PORT,
        adb=adb,
        device=device,
    )


def remote_request(
    link: RemoteLink,
    action: str,
    payload: dict | None = None,
    timeout: float = 25.0,
) -> dict:
    request = {
        "action": action,
        "payload": payload or {},
    }
    if link.pin:
        request["pin"] = link.pin

    encoded = (json.dumps(request, ensure_ascii=False) + "\n").encode("utf-8")

    with socket.create_connection((link.host, link.port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(encoded)
        source = sock.makefile("rb")
        line = source.readline(8 * 1024 * 1024 + 1)

    if not line:
        raise RuntimeError(
            "Uvir non ha restituito alcuna risposta."
            if LANGUAGE == "it"
            else "Uvir returned no response."
        )
    if len(line) > 8 * 1024 * 1024:
        raise RuntimeError(
            "Risposta remota troppo grande."
            if LANGUAGE == "it"
            else "The remote response is too large."
        )

    try:
        response = json.loads(line.decode("utf-8"))
    except Exception as exc:
        raise RuntimeError(
            "Risposta remota non valida."
            if LANGUAGE == "it"
            else "Invalid remote response."
        ) from exc

    if not response.get("ok"):
        fallback = (
            "Operazione remota fallita."
            if LANGUAGE == "it"
            else "Remote operation failed."
        )
        raise RuntimeError(str(response.get("error") or fallback))
    return response.get("data") or {}


def record_to_remote_dict(row: sqlite3.Row) -> dict:
    return {
        "id": int(row["id"]),
        "timestamp": int(row["timestamp"]),
        "note": row["note"] or "",
        "automatic": is_automatic(row),
        "automatic_session_id": optional_int(
            row,
            "automatic_session_id"
        ),
        "automatic_sequence": optional_int(
            row,
            "automatic_sequence"
        ),
        "sample": {
            key: fnum(row[key])
            for key in (
                "uvc", "uvb", "uva", "violetto", "blu", "verde",
                "giallo", "arancione", "rosso", "f8", "nir"
            )
        },
    }


def create_database_from_remote(
    path: Path,
    records: list[dict],
    measurement_counter: int | None = None,
    session_counter: int | None = None,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    if temporary.exists():
        temporary.unlink()

    previous_sequence = 0
    previous_session_counter = 0
    if path.exists():
        previous = sqlite3.connect(path)
        try:
            sequence_row = previous.execute(
                "SELECT seq FROM sqlite_sequence WHERE name = ?",
                ("measurements",)
            ).fetchone()
            if sequence_row:
                previous_sequence = int(sequence_row[0] or 0)
        except sqlite3.DatabaseError:
            pass
        try:
            session_row = previous.execute(
                "SELECT value FROM uvir_counters WHERE name = ?",
                (SESSION_COUNTER_NAME,)
            ).fetchone()
            if session_row:
                previous_session_counter = int(session_row[0] or 0)
        except sqlite3.DatabaseError:
            pass
        finally:
            previous.close()

    con = sqlite3.connect(temporary)
    try:
        con.execute(
            """
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                note TEXT NOT NULL,
                automatic INTEGER NOT NULL DEFAULT 0,
                automatic_session_id INTEGER,
                automatic_sequence INTEGER,
                uvc REAL NOT NULL,
                uvb REAL NOT NULL,
                uva REAL NOT NULL,
                violetto REAL NOT NULL,
                blu REAL NOT NULL,
                verde REAL NOT NULL,
                giallo REAL NOT NULL,
                arancione REAL NOT NULL,
                rosso REAL NOT NULL,
                f8 REAL NOT NULL,
                nir REAL NOT NULL
            )
            """
        )
        con.execute(
            """
            CREATE TABLE uvir_counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """
        )
        for record in records:
            sample = record.get("sample") or {}
            con.execute(
                """
                INSERT INTO measurements (
                    id, timestamp, note, automatic,
                    automatic_session_id, automatic_sequence,
                    uvc, uvb, uva, violetto, blu, verde,
                    giallo, arancione, rosso, f8, nir
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    int(record.get("id", 0)),
                    int(record.get("timestamp", 0)),
                    str(record.get("note") or ""),
                    1 if record.get("automatic") else 0,
                    record.get("automatic_session_id"),
                    record.get("automatic_sequence"),
                    *[
                        fnum(sample.get(key, 0.0))
                        for key in (
                            "uvc", "uvb", "uva", "violetto", "blu", "verde",
                            "giallo", "arancione", "rosso", "f8", "nir"
                        )
                    ],
                ),
            )

        imported_sequence = max(
            (
                int(record.get("id", 0))
                for record in records
            ),
            default=0
        )
        preserved_sequence = max(
            (
                previous_sequence
                if measurement_counter is None
                else int(measurement_counter)
            ),
            imported_sequence
        )
        if preserved_sequence > 0:
            con.execute(
                "DELETE FROM sqlite_sequence WHERE name = ?",
                ("measurements",)
            )
            con.execute(
                "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                ("measurements", preserved_sequence)
            )

        imported_session_counter = max(
            (
                int(record.get("automatic_session_id") or 0)
                for record in records
            ),
            default=0
        )
        preserved_session_counter = max(
            (
                previous_session_counter
                if session_counter is None
                else int(session_counter)
            ),
            imported_session_counter
        )
        con.execute(
            "INSERT INTO uvir_counters(name, value) VALUES (?, ?)",
            (
                SESSION_COUNTER_NAME,
                preserved_session_counter
            )
        )
        con.commit()
    finally:
        con.close()

    temporary.replace(path)


def phone_database_path() -> Path:
    folder = Path.home() / "UvirDesktop" / "phone_db"
    folder.mkdir(parents=True, exist_ok=True)
    return folder / DB_FILENAME


# ---------------------- XLSX writer ------------------------

def col_name(n: int) -> str:
    s = ""
    while n:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def xlsx_cell(r: int, c: int, value, header: bool = False) -> str:
    ref = f"{col_name(c)}{r}"
    style = 1 if header else (2 if isinstance(value, (int, float)) else 0)
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return f'<c r="{ref}" s="{style}"><v>{value}</v></c>'
    txt = escape("" if value is None else str(value))
    return f'<c r="{ref}" s="{style}" t="inlineStr"><is><t xml:space="preserve">{txt}</t></is></c>'


def xlsx_sheet(rows: list[list], widths: list[float]) -> str:
    max_cols = max(len(r) for r in rows)
    max_rows = len(rows)
    cols = "".join(
        f'<col min="{i}" max="{i}" width="{w}" customWidth="1"/>'
        for i, w in enumerate(widths, 1)
    )
    row_xml = []
    for ri, row in enumerate(rows, 1):
        cells = "".join(xlsx_cell(ri, ci, v, header=(ri == 1)) for ci, v in enumerate(row, 1))
        row_xml.append(f'<row r="{ri}">{cells}</row>')
    return f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<dimension ref="A1:{col_name(max_cols)}{max_rows}"/>
<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
<cols>{cols}</cols>
<sheetData>{''.join(row_xml)}</sheetData>
<autoFilter ref="A1:{col_name(max_cols)}{max_rows}"/>
</worksheet>'''


def write_xlsx(path: Path, records: list[sqlite3.Row]) -> None:
    measurements = [EXPORT_COLUMNS] + [export_row(r) for r in records]
    sheet1 = xlsx_sheet(
        measurements,
        [16, 20, 20, 17, 24, 12, 38, 20]
        + [23] * (len(EXPORT_COLUMNS) - 8)
    )
    sheet2 = xlsx_sheet(LEGEND_ROWS, [22, 20, 22, 62])

    content_types = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>'''
    root_rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>'''
    workbook = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="{escape(MEASUREMENTS_SHEET)}" sheetId="1" r:id="rId1"/><sheet name="{escape(LEGEND_SHEET)}" sheetId="2" r:id="rId2"/></sheets>
</workbook>'''
    wb_rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>'''
    styles = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/><color rgb="FFFFFFFF"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF263238"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>'''

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", content_types)
        z.writestr("_rels/.rels", root_rels)
        z.writestr("xl/workbook.xml", workbook)
        z.writestr("xl/_rels/workbook.xml.rels", wb_rels)
        z.writestr("xl/styles.xml", styles)
        z.writestr("xl/worksheets/sheet1.xml", sheet1)
        z.writestr("xl/worksheets/sheet2.xml", sheet2)


# ----------------------- ODS writer ------------------------

def ods_cell(value, header=False) -> str:
    style = ' table:style-name="Header"' if header else ""
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return f'<table:table-cell office:value-type="float" office:value="{value}"{style}><text:p>{value}</text:p></table:table-cell>'
    txt = escape("" if value is None else str(value))
    return f'<table:table-cell office:value-type="string"{style}><text:p>{txt}</text:p></table:table-cell>'


def ods_table(name: str, rows: list[list]) -> str:
    out = []
    for ri, row in enumerate(rows):
        out.append("<table:table-row>" + "".join(ods_cell(v, ri == 0) for v in row) + "</table:table-row>")
    return f'<table:table table:name="{escape(name)}">' + "".join(out) + "</table:table>"


def write_ods(path: Path, records: list[sqlite3.Row]) -> None:
    measurements = [EXPORT_COLUMNS] + [export_row(r) for r in records]
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.2">
<office:automatic-styles><style:style style:name="Header" style:family="table-cell"><style:table-cell-properties fo:background-color="#263238"/><style:text-properties fo:color="#ffffff" fo:font-weight="bold"/></style:style></office:automatic-styles>
<office:body><office:spreadsheet>{ods_table(MEASUREMENTS_SHEET, measurements)}{ods_table(LEGEND_SHEET, LEGEND_ROWS)}</office:spreadsheet></office:body>
</office:document-content>'''
    styles = '''<?xml version="1.0" encoding="UTF-8"?><office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" office:version="1.2"><office:styles/></office:document-styles>'''
    manifest = '''<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/></manifest:manifest>'''
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("mimetype", "application/vnd.oasis.opendocument.spreadsheet", compress_type=zipfile.ZIP_STORED)
        z.writestr("content.xml", content, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("styles.xml", styles, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("META-INF/manifest.xml", manifest, compress_type=zipfile.ZIP_DEFLATED)


# --------------------------- GUI ---------------------------

class App:
    def __init__(self, root: Tk):
        self.root = root
        self.settings = load_local_settings()
        self._settings_save_job: str | None = None
        self.last_connection_method = str(
            self.settings.get("last_connection_method") or "usb"
        )
        root.title(APP_TITLE)
        root.minsize(1050, 650)
        root.columnconfigure(0, weight=1)
        root.rowconfigure(1, weight=1)

        self.db_path: Path | None = None
        self.remote_link: RemoteLink | None = None
        self.row_map: dict[int, sqlite3.Row] = {}
        self.package = StringVar(
            value=str(self.settings.get("package") or DEFAULT_PACKAGE)
        )
        self.status = StringVar(value=tr("initial_status"))

        self.wireless_address = StringVar(
            value=str(self.settings.get("wireless_address") or "")
        )
        self.pairing_address = StringVar(
            value=str(self.settings.get("pairing_address") or "")
        )
        self.pairing_code = StringVar(
            value=str(self.settings.get("pairing_code") or "")
        )
        self.direct_host = StringVar(
            value=str(self.settings.get("wifi_address") or "")
        )
        self.direct_pin = StringVar(
            value=str(self.settings.get("wifi_code") or "")
        )
        self.bluetooth_host = StringVar(
            value=str(self.settings.get("bluetooth_address") or "")
        )
        self.bluetooth_pin = StringVar(
            value=str(self.settings.get("bluetooth_code") or "")
        )
        self.remote_state = StringVar(value=tr("not_connected"))

        self.remote_note = StringVar(value="")
        self.remote_interval = StringVar(
            value=str(self.settings.get("automatic_interval") or "60")
        )
        self.remote_auto_note = StringVar(
            value=str(self.settings.get("automatic_note") or "")
        )
        self.remote_limit_enabled = BooleanVar(
            value=bool(self.settings.get("automatic_limit_enabled", False))
        )
        self.remote_max_count = StringVar(
            value=str(self.settings.get("automatic_max_count") or "10")
        )

        self.live_window: Toplevel | None = None
        self.connection_window: Toplevel | None = None
        self.acquisition_window: Toplevel | None = None
        self.live_poll_generation = 0
        self.live_poll_inflight: int | None = None
        self.live_results: queue.SimpleQueue = queue.SimpleQueue()
        self.live_header = StringVar(value=tr("live_connect_hint"))
        self.live_auto_state = StringVar(value=tr("auto_stopped"))
        self.live_value_vars: dict[str, StringVar] = {}
        self.live_bio_vars: dict[str, StringVar] = {}
        self.live_bio_bars: dict[str, ttk.Progressbar] = {}

        self.build_ui()
        self._install_settings_memory()
        self._restore_last_database()
        root.protocol("WM_DELETE_WINDOW", self._close_app)

    def _persistent_variables(self):
        return {
            "package": self.package,
            "wireless_address": self.wireless_address,
            "pairing_address": self.pairing_address,
            "pairing_code": self.pairing_code,
            "wifi_address": self.direct_host,
            "wifi_code": self.direct_pin,
            "bluetooth_address": self.bluetooth_host,
            "bluetooth_code": self.bluetooth_pin,
            "automatic_interval": self.remote_interval,
            "automatic_note": self.remote_auto_note,
            "automatic_limit_enabled": self.remote_limit_enabled,
            "automatic_max_count": self.remote_max_count,
        }

    def _install_settings_memory(self):
        for variable in self._persistent_variables().values():
            variable.trace_add(
                "write",
                lambda *_args: self._schedule_settings_save(),
            )

    def _settings_snapshot(self) -> dict:
        data = {
            key: variable.get()
            for key, variable in self._persistent_variables().items()
        }
        data["last_connection_method"] = self.last_connection_method
        data["last_database"] = str(self.db_path) if self.db_path else ""
        return data

    def _schedule_settings_save(self):
        if self._settings_save_job is not None:
            self.root.after_cancel(self._settings_save_job)
        self._settings_save_job = self.root.after(
            350,
            self._save_settings_memory,
        )

    def _save_settings_memory(self):
        self._settings_save_job = None
        try:
            save_local_settings(self._settings_snapshot())
        except OSError as error:
            self.status.set(
                tr("settings_saved_error", error=error)
            )

    def _restore_last_database(self):
        saved_path = str(self.settings.get("last_database") or "").strip()
        if not saved_path:
            return
        path = Path(saved_path)
        if not path.is_file():
            return
        try:
            ensure_schema(path)
            self.db_path = path
            self.mode.config(text=tr("local_database"))
            self.status.set(str(path))
            self.refresh()
        except (OSError, sqlite3.DatabaseError, RuntimeError):
            self.db_path = None

    def _close_app(self):
        if self._settings_save_job is not None:
            self.root.after_cancel(self._settings_save_job)
            self._settings_save_job = None
        self._save_settings_memory()
        self.root.destroy()

    def build_ui(self):
        bar = ttk.Frame(self.root, padding=10)
        bar.grid(row=0, column=0, sticky="ew")
        bar.columnconfigure(3, weight=1)

        ttk.Button(
            bar,
            text=tr("open_database"),
            command=self.open_db,
        ).grid(row=0, column=0, padx=(0, 4))
        ttk.Button(
            bar,
            text=tr("connect_phone"),
            command=self.open_remote_window,
        ).grid(row=0, column=1, padx=4)
        ttk.Button(
            bar,
            text=tr("open_live"),
            command=self.open_live_window,
        ).grid(row=0, column=2, padx=4)
        ttk.Button(
            bar,
            text=tr("refresh_data"),
            command=self.smart_refresh,
        ).grid(row=0, column=4, padx=(4, 0))

        export_bar = ttk.Frame(bar)
        export_bar.grid(
            row=1,
            column=0,
            columnspan=5,
            sticky="ew",
            pady=(8, 0),
        )
        ttk.Label(export_bar, text=tr("export")).pack(side=LEFT, padx=(0, 4))
        ttk.Button(
            export_bar,
            text="CSV",
            command=lambda: self.do_export("csv"),
        ).pack(side=LEFT, padx=2)
        ttk.Button(
            export_bar,
            text="Excel",
            command=lambda: self.do_export("xlsx"),
        ).pack(side=LEFT, padx=2)
        ttk.Button(
            export_bar,
            text="LibreOffice",
            command=lambda: self.do_export("ods"),
        ).pack(side=LEFT, padx=2)
        ttk.Button(
            export_bar,
            text=tr("reset_counters"),
            command=self.reset_counters,
        ).pack(side="right", padx=(8, 0))

        paned = ttk.Panedwindow(self.root, orient="horizontal")
        paned.grid(row=1, column=0, sticky="nsew", padx=10, pady=(0, 6))
        left, right = ttk.Frame(paned), ttk.Frame(paned)
        paned.add(left, weight=2)
        paned.add(right, weight=3)

        left.columnconfigure(0, weight=1)
        left.rowconfigure(1, weight=1)
        lh = ttk.Frame(left)
        lh.grid(row=0, column=0, sticky="ew", pady=(0, 5))
        lh.columnconfigure(0, weight=1)
        self.count = ttk.Label(lh, text=tr("measurements_count", count=0))
        self.count.grid(row=0, column=0, sticky="w")
        actions = ttk.Frame(lh)
        actions.grid(row=0, column=1, sticky="e")
        ttk.Button(actions, text=tr("edit_note"), command=self.edit_note).pack(side=LEFT, padx=3)
        ttk.Button(actions, text=tr("delete_selected"), command=self.delete_one).pack(side=LEFT, padx=3)
        ttk.Button(actions, text=tr("delete_all"), command=self.delete_all).pack(side=LEFT, padx=3)

        self.tree = ttk.Treeview(
            left,
            columns=("id", "session_id", "time", "auto", "note"),
            show="headings",
            selectmode="browse"
        )
        self.tree.heading("id", text=tr("measurement_id"))
        self.tree.heading("session_id", text=tr("session_id"))
        self.tree.heading("time", text=tr("date_time"))
        self.tree.heading("auto", text=tr("automatic_short"))
        self.tree.heading("note", text=tr("note"))
        self.tree.column("id", width=100, stretch=False, anchor="center")
        self.tree.column("session_id", width=85, stretch=False, anchor="center")
        self.tree.column("time", width=145, stretch=False)
        self.tree.column("auto", width=35, stretch=False, anchor="center")
        self.tree.column("note", width=220)
        self.tree.grid(row=1, column=0, sticky="nsew")
        self.tree.tag_configure(
            "session_header",
            background="#EDE7F6",
            foreground="#4B1F78",
            font=("Segoe UI", 9, "bold")
        )
        self.tree.tag_configure(
            "session_member",
            background="#FAF8FF"
        )
        sb = ttk.Scrollbar(left, orient="vertical", command=self.tree.yview)
        sb.grid(row=1, column=1, sticky="ns")
        self.tree.configure(yscrollcommand=sb.set)
        self.tree.bind("<<TreeviewSelect>>", self.selection_changed)

        right.columnconfigure(0, weight=1)
        right.rowconfigure(3, weight=1)

        self.detail_title = ttk.Label(
            right,
            text=tr("select_measurement"),
            font=("Segoe UI", 15, "bold")
        )
        self.detail_title.grid(
            row=0,
            column=0,
            sticky="w",
            padx=10,
            pady=(3, 2)
        )

        self.detail_acquisition = ttk.Label(
            right,
            text=""
        )
        self.detail_acquisition.grid(
            row=1,
            column=0,
            sticky="w",
            padx=10,
            pady=(0, 2)
        )

        self.detail_note = ttk.Label(
            right,
            text="",
            wraplength=650
        )
        self.detail_note.grid(
            row=2,
            column=0,
            sticky="ew",
            padx=10,
            pady=(0, 7)
        )

        self.detail_notebook = ttk.Notebook(
            right
        )
        self.detail_notebook.grid(
            row=3,
            column=0,
            sticky="nsew",
            padx=8,
            pady=4
        )

        self.irradiance_tab = ttk.Frame(
            self.detail_notebook,
            padding=6
        )

        self.biological_tab = ttk.Frame(
            self.detail_notebook,
            padding=10
        )

        self.detail_notebook.add(
            self.irradiance_tab,
            text=tr("irradiance")
        )

        self.detail_notebook.add(
            self.biological_tab,
            text=tr("estimated_biological_effects")
        )

        self.irradiance_tab.columnconfigure(
            0,
            weight=1
        )

        self.detail_vars: dict[
            str,
            list[tuple[StringVar, StringVar]]
        ] = {}

        groups = [
            (
                "UV",
                [
                    "UVC 100–280 nm",
                    "UVB 280–315 nm",
                    "UVA 315–400 nm"
                ]
            ),
            (
                "HEV / HEB",
                [
                    f"HEV 400–500 nm\n{tr('energetic_visible')}",
                    f"HEB 400–450 nm\n{tr('energetic_blue_violet')}"
                ]
            ),
            (
                tr("visible"),
                [
                    f"{tr('violet')} 400–450 nm",
                    f"{tr('blue')} 450–495 nm",
                    f"{tr('green')} 495–570 nm",
                    f"{tr('yellow')} 570–590 nm",
                    f"{tr('orange')} 590–620 nm",
                    f"{tr('red')} 620–700 nm"
                ]
            ),
            (
                "FAR-RED / NIR",
                [
                    "FAR-RED picco 745 nm",
                    "NIR picco 855 nm"
                ]
            ),
        ]

        for gi, (name, labels) in enumerate(
            groups
        ):
            frame = ttk.LabelFrame(
                self.irradiance_tab,
                text=name,
                padding=8
            )
            frame.grid(
                row=gi,
                column=0,
                sticky="ew",
                padx=2,
                pady=4
            )
            frame.columnconfigure(
                1,
                weight=1
            )

            rows = []

            for ri, label in enumerate(
                labels
            ):
                ttk.Label(
                    frame,
                    text=label
                ).grid(
                    row=ri,
                    column=0,
                    sticky="w",
                    padx=(0, 12),
                    pady=2
                )

                vv = StringVar(
                    value="—"
                )
                pv = StringVar(
                    value=""
                )

                ttk.Label(
                    frame,
                    textvariable=vv,
                    font=("Consolas", 10, "bold")
                ).grid(
                    row=ri,
                    column=1,
                    sticky="e"
                )

                ttk.Label(
                    frame,
                    textvariable=pv,
                    width=13
                ).grid(
                    row=ri,
                    column=2,
                    sticky="e"
                )

                rows.append(
                    (vv, pv)
                )

            self.detail_vars[name] = rows

        self.biological_tab.columnconfigure(
            0,
            weight=1
        )

        disclaimer = tr("bio_disclaimer")

        ttk.Label(
            self.biological_tab,
            text=disclaimer,
            wraplength=620
        ).grid(
            row=0,
            column=0,
            sticky="ew",
            pady=(0, 10)
        )

        self.bio_vars: dict[str, StringVar] = {
            "dna_uv": StringVar(value="—"),
            "dna_uv_score": StringVar(value="—"),
            "uva_photoaging": StringVar(value="—"),
            "uva_photoaging_score": StringVar(value="—"),
            "hev_oxidative": StringVar(value="—"),
            "hev_oxidative_score": StringVar(value="—"),
        }

        self.bio_bars: dict[str, ttk.Progressbar] = {}

        bio_rows = [
            (
                tr("dna_title"),
                "dna_uv",
                "dna_uv_score",
                tr("dna_description")
            ),
            (
                tr("photoaging_title"),
                "uva_photoaging",
                "uva_photoaging_score",
                tr("photoaging_description")
            ),
            (
                tr("oxidative_title"),
                "hev_oxidative",
                "hev_oxidative_score",
                tr("oxidative_description")
            ),
        ]

        for i, (title, key, score_key, description) in enumerate(
            bio_rows,
            start=1
        ):
            frame = ttk.LabelFrame(
                self.biological_tab,
                text=title,
                padding=10
            )
            frame.grid(
                row=i,
                column=0,
                sticky="ew",
                pady=5
            )
            frame.columnconfigure(
                0,
                weight=1
            )

            ttk.Label(
                frame,
                text=description,
                wraplength=590
            ).grid(
                row=0,
                column=0,
                sticky="w"
            )

            ttk.Label(
                frame,
                textvariable=self.bio_vars[key],
                font=("Consolas", 12, "bold")
            ).grid(
                row=1,
                column=0,
                sticky="w",
                pady=(6, 2)
            )

            ttk.Label(
                frame,
                textvariable=self.bio_vars[score_key]
            ).grid(
                row=2,
                column=0,
                sticky="w",
                pady=(0, 4)
            )

            bar = ttk.Progressbar(
                frame,
                orient="horizontal",
                mode="determinate",
                maximum=100.0
            )
            bar.grid(
                row=3,
                column=0,
                sticky="ew",
                pady=(0, 3)
            )
            self.bio_bars[score_key] = bar

            ttk.Label(
                frame,
                text=tr("relative_scale"),
                wraplength=590
            ).grid(
                row=4,
                column=0,
                sticky="w"
            )

        status = ttk.Frame(self.root, padding=(10, 4, 10, 9))
        status.grid(row=2, column=0, sticky="ew")
        status.columnconfigure(0, weight=1)
        ttk.Label(status, textvariable=self.status).grid(row=0, column=0, sticky="w")
        self.mode = ttk.Label(status, text=tr("local"))
        self.mode.grid(row=0, column=1, sticky="e")

    # --------------------- COLLEGAMENTO REMOTO ---------------------

    def open_live_window(self):
        if self.live_window is not None and self.live_window.winfo_exists():
            self.live_window.deiconify()
            self.live_window.lift()
            self.live_window.focus_force()
            return

        window = Toplevel(self.root)
        self.live_window = window
        self.live_poll_generation += 1
        generation = self.live_poll_generation

        window.title(tr("live_window_title"))
        window.minsize(820, 650)
        window.geometry("920x720")
        window.columnconfigure(0, weight=1)
        window.rowconfigure(1, weight=1)
        window.protocol(
            "WM_DELETE_WINDOW",
            lambda: self._close_live_window(generation),
        )

        header = ttk.Frame(window, padding=(14, 12, 14, 8))
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)

        ttk.Label(
            header,
            text="Uvir LIVE",
            font=("Segoe UI", 17, "bold"),
        ).grid(row=0, column=0, sticky="w")

        ttk.Label(
            header,
            textvariable=self.live_header,
            foreground="#16823b",
        ).grid(row=1, column=0, sticky="w", pady=(2, 0))

        actions = ttk.Frame(header)
        actions.grid(row=0, column=1, rowspan=2, sticky="e")
        ttk.Button(
            actions,
            text=tr("connect"),
            command=self.open_remote_window,
        ).pack(side=LEFT, padx=3)
        ttk.Button(
            actions,
            text=tr("acquisition"),
            command=self.open_acquisition_window,
        ).pack(side=LEFT, padx=3)

        notebook = ttk.Notebook(window)
        notebook.grid(row=1, column=0, sticky="nsew", padx=14, pady=(0, 8))

        irradiance = ttk.Frame(notebook, padding=10)
        effects = ttk.Frame(notebook, padding=10)
        notebook.add(irradiance, text=tr("live_irradiance"))
        notebook.add(effects, text=tr("estimated_biological_effects"))

        irradiance.columnconfigure(0, weight=1)
        irradiance.columnconfigure(1, weight=1)
        irradiance.rowconfigure(1, weight=1)

        channel_groups = [
            (
                "UV",
                [
                    ("UVC · 100–280 nm", "uvc"),
                    ("UVB · 280–315 nm", "uvb"),
                    ("UVA · 315–400 nm", "uva"),
                    (tr("uv_total"), "uv_total"),
                ],
                0,
                0,
                1,
            ),
            (
                "HEV / HEB",
                [
                    ("HEV · 400–500 nm", "hev"),
                    ("HEB · 400–450 nm", "heb"),
                ],
                0,
                1,
                1,
            ),
            (
                tr("visible_spectrum"),
                [
                    (tr("violet"), "violetto"),
                    (tr("blue"), "blu"),
                    (tr("green"), "verde"),
                    (tr("yellow"), "giallo"),
                    (tr("orange"), "arancione"),
                    (tr("red"), "rosso"),
                    (tr("visible_total"), "vis_total"),
                ],
                1,
                0,
                2,
            ),
            (
                "Far-red / NIR",
                [
                    ("Far-red · picco 745 nm", "f8"),
                    ("NIR · picco 855 nm", "nir"),
                    (tr("far_nir_total"), "nir_total"),
                ],
                2,
                0,
                2,
            ),
        ]

        self.live_value_vars = {}
        for title, channels, row, column, span in channel_groups:
            group = ttk.LabelFrame(irradiance, text=title, padding=9)
            group.grid(
                row=row,
                column=column,
                columnspan=span,
                sticky="nsew",
                padx=4,
                pady=4,
            )
            columns = 4 if span == 2 else 2
            for grid_column in range(columns):
                group.columnconfigure(grid_column, weight=1)

            for index, (label, key) in enumerate(channels):
                card = ttk.Frame(group, padding=(8, 5))
                card.grid(
                    row=index // columns,
                    column=index % columns,
                    sticky="nsew",
                    padx=2,
                    pady=2,
                )
                variable = StringVar(window, value="—")
                self.live_value_vars[key] = variable
                ttk.Label(card, text=label).grid(row=0, column=0, sticky="w")
                ttk.Label(
                    card,
                    textvariable=variable,
                    font=("Consolas", 12, "bold"),
                ).grid(row=1, column=0, sticky="w", pady=(2, 0))

        effects.columnconfigure(0, weight=1)
        ttk.Label(
            effects,
            text=tr("live_bio_disclaimer"),
            wraplength=820,
        ).grid(row=0, column=0, sticky="ew", pady=(0, 8))

        self.live_bio_vars = {}
        self.live_bio_bars = {}
        biological_rows = [
            (tr("dna_title"), "dna_uv", "dna_uv_score"),
            (tr("photoaging_title"), "uva_photoaging", "uva_photoaging_score"),
            (tr("oxidative_title"), "hev_oxidative", "hev_oxidative_score"),
        ]

        for row, (title, value_key, score_key) in enumerate(
            biological_rows,
            start=1,
        ):
            group = ttk.LabelFrame(effects, text=title, padding=10)
            group.grid(row=row, column=0, sticky="ew", pady=5)
            group.columnconfigure(0, weight=1)

            value_var = StringVar(window, value="—")
            score_var = StringVar(window, value="—")
            self.live_bio_vars[value_key] = value_var
            self.live_bio_vars[score_key] = score_var

            ttk.Label(
                group,
                textvariable=value_var,
                font=("Consolas", 12, "bold"),
            ).grid(row=0, column=0, sticky="w")
            ttk.Label(group, textvariable=score_var).grid(
                row=1,
                column=0,
                sticky="w",
                pady=(3, 3),
            )
            progress = ttk.Progressbar(
                group,
                orient="horizontal",
                mode="determinate",
                maximum=100.0,
            )
            progress.grid(row=2, column=0, sticky="ew")
            self.live_bio_bars[score_key] = progress

        footer = ttk.Frame(window, padding=(14, 4, 14, 12))
        footer.grid(row=2, column=0, sticky="ew")
        footer.columnconfigure(0, weight=1)
        ttk.Label(
            footer,
            textvariable=self.live_auto_state,
            font=("Segoe UI", 10, "bold"),
        ).grid(row=0, column=0, sticky="w")
        ttk.Label(
            footer,
            text=tr("automatic_update"),
        ).grid(row=0, column=1, sticky="e")

        self._schedule_live_poll(generation, 0)

    def _close_live_window(self, generation: int):
        if generation != self.live_poll_generation:
            return
        self.live_poll_generation += 1
        window = self.live_window
        self.live_window = None
        if window is not None and window.winfo_exists():
            window.destroy()

    def _schedule_live_poll(self, generation: int, delay_ms: int = 500):
        if generation != self.live_poll_generation:
            return
        window = self.live_window
        if window is None or not window.winfo_exists():
            return
        self.root.after(
            delay_ms,
            lambda: self._begin_live_poll(generation),
        )

    def _begin_live_poll(self, generation: int):
        if generation != self.live_poll_generation:
            return
        if self.live_poll_inflight == generation:
            return

        link = self.remote_link
        if link is None:
            self.live_header.set(tr("phone_not_connected"))
            self.live_auto_state.set("AUTO —")
            self._schedule_live_poll(generation, 750)
            return

        self.live_poll_inflight = generation

        def request_snapshot():
            try:
                data = remote_request(
                    link,
                    "status",
                    timeout=2.0,
                )
                self.live_results.put((generation, data, None))
            except Exception as error:
                self.live_results.put((generation, None, str(error)))

        threading.Thread(
            target=request_snapshot,
            name="UvirLivePoll",
            daemon=True,
        ).start()
        self.root.after(
            40,
            lambda: self._collect_live_result(generation),
        )

    def _collect_live_result(self, generation: int):
        if generation != self.live_poll_generation:
            return
        try:
            result_generation, data, error = self.live_results.get_nowait()
        except queue.Empty:
            self.root.after(
                40,
                lambda: self._collect_live_result(generation),
            )
            return

        if result_generation != generation:
            self.root.after(
                0,
                lambda: self._collect_live_result(generation),
            )
            return

        if self.live_poll_inflight == generation:
            self.live_poll_inflight = None

        if error:
            self.live_header.set(
                tr("live_unavailable", error=error)
            )
            self._schedule_live_poll(generation, 1200)
            return

        self._apply_live_snapshot(data or {})
        self._schedule_live_poll(generation, 500)

    def _apply_live_snapshot(self, data: dict):
        sample_source = data.get("measurement") or {}
        sample = {
            key: fnum(sample_source.get(key, 0.0))
            for key in (
                "uvc", "uvb", "uva", "violetto", "blu", "verde",
                "giallo", "arancione", "rosso", "f8", "nir"
            )
        }
        values = derived(sample)

        for key, variable in self.live_value_vars.items():
            variable.set(f"{values.get(key, 0.0):,.1f} µW/cm²")

        effects = biological_effects(sample)
        for value_key, score_key in (
            ("dna_uv", "dna_uv_score"),
            ("uva_photoaging", "uva_photoaging_score"),
            ("hev_oxidative", "hev_oxidative_score"),
        ):
            self.live_bio_vars[value_key].set(
                f"{tr('weighted_irradiance')}: {effects[value_key]:,.2f} µW/cm² eq."
            )
            self.live_bio_vars[score_key].set(
                f"{tr('spectral_relevance')}: {effects[score_key]:.0f} / 100"
            )
            self.live_bio_bars[score_key]["value"] = effects[score_key]

        ready = bool(data.get("live_ready"))
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.live_header.set(
            ("● LIVE" if ready else tr("sensors_initializing"))
            + " · "
            + tr("last_update", time=timestamp)
        )

        if data.get("auto_enabled"):
            completed = int(data.get("auto_completed_count") or 0)
            interval = int(data.get("auto_interval_seconds") or 0)
            next_save_ms = int(data.get("auto_next_save_ms") or 0)
            remaining_ms = max(0, next_save_ms - int(time.time() * 1000))
            remaining_seconds = (remaining_ms + 999) // 1000
            limit = ""
            if data.get("auto_limit_enabled"):
                limit = f" / {int(data.get('auto_max_count') or 0)}"
            self.live_auto_state.set(
                tr(
                    "auto_active",
                    completed=completed,
                    limit=limit,
                    interval=interval,
                    remaining=remaining_seconds,
                )
            )
        else:
            self.live_auto_state.set(tr("auto_stopped"))

    def open_acquisition_window(self):
        if (
            self.acquisition_window is not None
            and self.acquisition_window.winfo_exists()
        ):
            self.acquisition_window.deiconify()
            self.acquisition_window.lift()
            self.acquisition_window.focus_force()
            return

        window = Toplevel(self.root)
        self.acquisition_window = window
        window.title(tr("control_acquisition"))
        window.minsize(560, 430)
        window.geometry("620x500")
        window.columnconfigure(0, weight=1)
        window.protocol(
            "WM_DELETE_WINDOW",
            self._close_acquisition_window,
        )

        ttk.Label(
            window,
            textvariable=self.remote_state,
            font=("Segoe UI", 10, "bold"),
            padding=(14, 12, 14, 6),
        ).grid(row=0, column=0, sticky="ew")

        body = ttk.Frame(window, padding=(14, 6, 14, 14))
        body.grid(row=1, column=0, sticky="nsew")
        body.columnconfigure(0, weight=1)

        manual = ttk.LabelFrame(
            body,
            text=tr("manual_measurement"),
            padding=12,
        )
        manual.grid(row=0, column=0, sticky="ew", pady=(0, 12))
        manual.columnconfigure(0, weight=1)
        ttk.Label(manual, text=tr("optional_note")).grid(
            row=0, column=0, sticky="w"
        )
        ttk.Entry(manual, textvariable=self.remote_note).grid(
            row=1, column=0, sticky="ew", pady=(4, 10)
        )
        ttk.Button(
            manual,
            text=tr("save_measurement"),
            command=self.remote_save_current,
        ).grid(row=2, column=0, sticky="w")

        automatic = ttk.LabelFrame(
            body,
            text=tr("automatic_acquisition"),
            padding=12,
        )
        automatic.grid(row=1, column=0, sticky="ew")
        automatic.columnconfigure(1, weight=1)

        ttk.Label(automatic, text=tr("interval_seconds")).grid(
            row=0, column=0, sticky="w", pady=4
        )
        ttk.Entry(
            automatic,
            textvariable=self.remote_interval,
            width=10,
        ).grid(row=0, column=1, sticky="w", padx=(10, 0), pady=4)

        ttk.Label(automatic, text=tr("optional_note")).grid(
            row=1, column=0, sticky="w", pady=4
        )
        ttk.Entry(
            automatic,
            textvariable=self.remote_auto_note,
        ).grid(row=1, column=1, sticky="ew", padx=(10, 0), pady=4)

        limit = ttk.Frame(automatic)
        limit.grid(row=2, column=1, sticky="w", padx=(10, 0), pady=4)
        ttk.Checkbutton(
            limit,
            text=tr("maximum_acquisitions"),
            variable=self.remote_limit_enabled,
        ).pack(side=LEFT)
        ttk.Entry(
            limit,
            textvariable=self.remote_max_count,
            width=8,
        ).pack(side=LEFT, padx=6)

        actions = ttk.Frame(automatic)
        actions.grid(row=3, column=1, sticky="w", padx=(10, 0), pady=(10, 0))
        ttk.Button(
            actions,
            text=tr("start_automatic"),
            command=self.remote_start_auto,
        ).pack(side=LEFT, padx=(0, 6))
        ttk.Button(
            actions,
            text=tr("stop"),
            command=self.remote_stop_auto,
        ).pack(side=LEFT)

    def _close_acquisition_window(self):
        window = self.acquisition_window
        self.acquisition_window = None
        if window is not None and window.winfo_exists():
            window.destroy()

    def open_remote_window(self):
        if (
            self.connection_window is not None
            and self.connection_window.winfo_exists()
        ):
            self.connection_window.deiconify()
            self.connection_window.lift()
            self.connection_window.focus_force()
            return

        window = Toplevel(self.root)
        self.connection_window = window
        window.title(tr("connect_phone_title"))
        window.minsize(760, 420)
        window.geometry("860x500")
        window.columnconfigure(0, weight=1)
        window.rowconfigure(1, weight=1)
        window.protocol(
            "WM_DELETE_WINDOW",
            self._close_connection_window,
        )

        header = ttk.Frame(window, padding=(12, 10))
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)
        ttk.Label(
            header,
            textvariable=self.remote_state,
            font=("Segoe UI", 11, "bold"),
        ).grid(row=0, column=0, sticky="w")

        body = ttk.Frame(window, padding=(12, 0, 12, 12))
        body.grid(row=1, column=0, sticky="nsew")
        body.columnconfigure(0, weight=1)
        body.rowconfigure(0, weight=1)

        notebook = ttk.Notebook(body)
        notebook.grid(row=0, column=0, sticky="nsew")

        usb = ttk.Frame(notebook, padding=12)
        wifi = ttk.Frame(notebook, padding=12)
        bluetooth = ttk.Frame(notebook, padding=12)
        advanced = ttk.Frame(notebook, padding=12)
        notebook.add(usb, text="USB")
        notebook.add(wifi, text="Wi-Fi")
        notebook.add(bluetooth, text="Bluetooth")
        notebook.add(advanced, text=tr("advanced"))

        remembered_tab = {
            "usb": usb,
            "wifi": wifi,
            "bluetooth": bluetooth,
            "wireless_adb": advanced,
        }.get(self.last_connection_method)
        if remembered_tab is not None:
            notebook.select(remembered_tab)

        ttk.Label(
            usb,
            text=tr("usb_instructions"),
            justify="left",
            wraplength=760,
        ).grid(row=0, column=0, sticky="w", pady=(0, 16))
        ttk.Button(
            usb,
            text=tr("connect_usb"),
            command=self.connect_usb_remote,
        ).grid(row=1, column=0, sticky="w")

        wifi.columnconfigure(1, weight=1)
        ttk.Label(
            wifi,
            text=tr("wifi_instructions"),
            justify="left",
            wraplength=760,
        ).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 16))
        ttk.Label(wifi, text=tr("wifi_address")).grid(row=1, column=0, sticky="w", pady=5)
        ttk.Entry(wifi, textvariable=self.direct_host).grid(
            row=1, column=1, sticky="ew", padx=(10, 0), pady=5
        )
        ttk.Label(wifi, text=tr("connection_code")).grid(row=2, column=0, sticky="w", pady=5)
        ttk.Entry(wifi, textvariable=self.direct_pin, width=18, show="•").grid(
            row=2, column=1, sticky="w", padx=(10, 0), pady=5
        )
        ttk.Button(
            wifi,
            text=tr("connect_wifi"),
            command=lambda: self.connect_direct_remote("wifi"),
        ).grid(row=3, column=1, sticky="w", padx=(10, 0), pady=(12, 0))

        bluetooth.columnconfigure(1, weight=1)
        ttk.Label(
            bluetooth,
            text=tr("bluetooth_instructions"),
            justify="left",
            wraplength=760,
        ).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 16))
        ttk.Label(bluetooth, text=tr("bluetooth_address")).grid(row=1, column=0, sticky="w", pady=5)
        ttk.Entry(bluetooth, textvariable=self.bluetooth_host).grid(
            row=1, column=1, sticky="ew", padx=(10, 0), pady=5
        )
        ttk.Label(bluetooth, text=tr("connection_code")).grid(row=2, column=0, sticky="w", pady=5)
        ttk.Entry(bluetooth, textvariable=self.bluetooth_pin, width=18, show="•").grid(
            row=2, column=1, sticky="w", padx=(10, 0), pady=5
        )
        ttk.Button(
            bluetooth,
            text=tr("connect_bluetooth"),
            command=lambda: self.connect_direct_remote("bluetooth"),
        ).grid(row=3, column=1, sticky="w", padx=(10, 0), pady=(12, 0))

        advanced.columnconfigure(1, weight=1)
        ttk.Label(
            advanced,
            text=tr("wireless_debug_description"),
            wraplength=760,
        ).grid(row=0, column=0, columnspan=3, sticky="w", pady=(0, 14))
        ttk.Label(advanced, text=tr("pairing_address")).grid(row=1, column=0, sticky="w", pady=4)
        ttk.Entry(advanced, textvariable=self.pairing_address).grid(
            row=1, column=1, sticky="ew", padx=8, pady=4
        )
        ttk.Entry(advanced, textvariable=self.pairing_code, width=10).grid(row=1, column=2, sticky="w", pady=4)
        ttk.Button(
            advanced,
            text=tr("pair"),
            command=self.pair_wireless_adb,
        ).grid(row=2, column=2, sticky="e", pady=(4, 12))
        ttk.Separator(advanced).grid(row=3, column=0, columnspan=3, sticky="ew", pady=6)
        ttk.Label(advanced, text=tr("connection_address")).grid(row=4, column=0, sticky="w", pady=4)
        ttk.Entry(advanced, textvariable=self.wireless_address).grid(
            row=4, column=1, sticky="ew", padx=8, pady=4
        )
        ttk.Button(
            advanced,
            text=tr("connect_wireless_debug"),
            command=self.connect_wireless_remote,
        ).grid(row=4, column=2, sticky="e", pady=4)

    def _close_connection_window(self):
        window = self.connection_window
        self.connection_window = None
        if window is not None and window.winfo_exists():
            window.destroy()

    def _finish_remote_connection(
        self,
        link: RemoteLink,
        label: str,
        method_key: str,
    ):
        last_error = None
        for _ in range(20):
            try:
                info = remote_request(link, "ping", timeout=3.0)
                if info.get("package") != DEFAULT_PACKAGE:
                    raise RuntimeError(
                        tr(
                            "unexpected_package",
                            actual=info.get("package"),
                            expected=DEFAULT_PACKAGE,
                        )
                    )
                self.remote_link = link
                self.last_connection_method = method_key
                self._schedule_settings_save()
                self.remote_state.set(
                    tr(
                        "connected",
                        label=label,
                        version=info.get("version", ""),
                    )
                )
                self.mode.config(text=label)
                self.pull_remote_database(show_message=False)
                self.refresh_remote_status(show_message=False)
                self._close_connection_window()
                self.open_live_window()
                return
            except Exception as exc:
                last_error = exc
                time.sleep(0.25)
        raise RuntimeError(
            tr("remote_unresponsive", error=last_error)
        )

    def connect_usb_remote(self):
        try:
            adb = find_adb()
            if not adb:
                raise RuntimeError(tr("adb_not_found"))
            device = adb_device(adb)
            link = adb_prepare_remote(adb, device, DEFAULT_PACKAGE)
            self._finish_remote_connection(
                link,
                f"USB ADB · {device}",
                "usb",
            )
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def pair_wireless_adb(self):
        try:
            adb = find_adb()
            if not adb:
                raise RuntimeError(tr("adb_not_found"))
            detail = adb_pair(adb, self.pairing_address.get(), self.pairing_code.get())
            self.last_connection_method = "wireless_adb"
            self._schedule_settings_save()
            messagebox.showinfo(APP_TITLE, detail)
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def connect_wireless_remote(self):
        try:
            adb = find_adb()
            if not adb:
                raise RuntimeError(tr("adb_not_found"))
            device = adb_connect_wireless(adb, self.wireless_address.get())
            link = adb_prepare_remote(adb, device, DEFAULT_PACKAGE)
            self._finish_remote_connection(
                link,
                f"ADB wireless · {device}",
                "wireless_adb",
            )
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def connect_direct_remote(self, method_key: str = "wifi"):
        try:
            if method_key == "bluetooth":
                host = self.bluetooth_host.get().strip()
                pin = self.bluetooth_pin.get().strip()
                method = "Bluetooth"
            else:
                host = self.direct_host.get().strip()
                pin = self.direct_pin.get().strip()
                method = "Wi-Fi"

            port = REMOTE_PORT
            if not host:
                raise RuntimeError(
                    tr("enter_address", method=method)
                )
            if not pin:
                raise RuntimeError(
                    tr("enter_connection_code")
                )
            link = RemoteLink(mode="direct", host=host, port=port, pin=pin)
            self._finish_remote_connection(
                link,
                f"{method} · {host}",
                method_key,
            )
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def _remote(self, action: str, payload: dict | None = None) -> dict:
        if not self.remote_link:
            raise RuntimeError(tr("connect_desktop_first"))
        return remote_request(self.remote_link, action, payload)

    def refresh_remote_status(self, show_message: bool = True):
        try:
            data = self._remote("status")
            ready = (
                tr("live_ready")
                if data.get("live_ready")
                else tr("live_initializing")
            )
            if data.get("auto_enabled"):
                auto = tr(
                    "saved_count",
                    count=data.get("auto_completed_count", 0),
                )
            else:
                auto = tr("auto_stopped")
            self.remote_state.set(
                tr(
                    "screen_status",
                    ready=ready,
                    auto=auto,
                    screen=data.get("screen", "live"),
                )
            )
            self.status.set(self.remote_state.get())
            if show_message:
                messagebox.showinfo(APP_TITLE, self.remote_state.get())
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def remote_open_screen(self, screen: str):
        try:
            self._remote("open_screen", {"screen": screen})
            self.refresh_remote_status(show_message=False)
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def remote_save_current(self):
        try:
            result = self._remote("save_measurement", {"note": self.remote_note.get().strip()})
            self.remote_note.set("")
            self.pull_remote_database(show_message=False)
            messagebox.showinfo(
                APP_TITLE,
                tr("measurement_saved", id=result.get("id")),
            )
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def remote_start_auto(self):
        try:
            interval = int(self.remote_interval.get().strip())
            maximum = int(self.remote_max_count.get().strip() or "1")
            if interval <= 0:
                raise RuntimeError(tr("positive_interval"))
            if self.remote_limit_enabled.get() and maximum <= 0:
                raise RuntimeError(tr("positive_limit"))
            self._remote(
                "start_auto",
                {
                    "interval_seconds": interval,
                    "note": self.remote_auto_note.get().strip(),
                    "use_start_delay": False,
                    "start_delay_seconds": 0,
                    "use_duration": False,
                    "duration_seconds": 0,
                    "limit_enabled": bool(self.remote_limit_enabled.get()),
                    "max_acquisitions": maximum,
                },
            )
            self.refresh_remote_status(show_message=False)
            messagebox.showinfo(APP_TITLE, tr("auto_started"))
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def remote_stop_auto(self):
        try:
            self._remote("stop_auto")
            self.refresh_remote_status(show_message=False)
            messagebox.showinfo(APP_TITLE, tr("auto_stopped_message"))
        except Exception as exc:
            messagebox.showerror(APP_TITLE, str(exc))

    def pull_remote_database(self, show_message: bool = True):
        try:
            data = self._remote("list_measurements")
            records = data.get("records") or []
            db = phone_database_path()
            if db.exists():
                try:
                    ensure_schema(db)
                    backup_db(db)
                except Exception:
                    pass
            create_database_from_remote(
                db,
                records,
                measurement_counter=
                    data.get("measurement_counter"),
                session_counter=
                    data.get("session_counter"),
            )
            ensure_schema(db)
            self.db_path = db
            self._schedule_settings_save()
            self.refresh()
            self.status.set(
                tr(
                    "local_copy_updated",
                    count=len(records),
                    path=db,
                )
            )
            if show_message:
                messagebox.showinfo(
                    APP_TITLE,
                    tr("local_copy_updated_dialog", path=db),
                )
        except Exception as exc:
            if show_message:
                messagebox.showerror(APP_TITLE, str(exc))
            else:
                raise

    def sync_local_to_remote(self):
        if not self.db_path:
            messagebox.showinfo(APP_TITLE, tr("open_local_first"))
            return
        if not self.remote_link:
            messagebox.showinfo(APP_TITLE, tr("connect_phone_first"))
            return
        if not messagebox.askyesno(
            APP_TITLE,
            tr("replace_phone_database"),
        ):
            return
        try:
            ensure_schema(self.db_path)
            local_backup = backup_db(self.db_path)
            records = [record_to_remote_dict(row) for row in self.rows()]
            measurement_counter, session_counter = database_counters(
                self.db_path
            )
            result = self._remote(
                "replace_measurements",
                {
                    "records": records,
                    "measurement_counter": measurement_counter,
                    "session_counter": session_counter,
                }
            )
            self.pull_remote_database(show_message=False)
            messagebox.showinfo(
                APP_TITLE,
                tr(
                    "synced_measurements",
                    count=result.get("replaced", 0),
                    backup=local_backup,
                ),
            )
        except Exception as exc:
            messagebox.showerror(
                APP_TITLE,
                tr("sync_failed", error=exc),
            )

    def smart_refresh(self):
        if self.remote_link:
            self.pull_remote_database(show_message=False)
            self.refresh_remote_status(show_message=False)
        else:
            self.refresh()

    def edit_note(self):
        record_id = self.selected_record_id()
        if not self.db_path or record_id is None:
            messagebox.showinfo(APP_TITLE, tr("select_a_measurement"))
            return
        row = self.row_map.get(record_id)
        if row is None:
            return
        note = simpledialog.askstring(
            APP_TITLE,
            tr("measurement_note", id=record_id),
            initialvalue=row["note"] or "",
            parent=self.root,
        )
        if note is None:
            return
        try:
            local_backup = backup_db(self.db_path)
            con = self.connect()
            try:
                con.execute("UPDATE measurements SET note=? WHERE id=?", (note.strip(), record_id))
                con.commit()
            finally:
                con.close()
            if self.remote_link:
                updated = dict(record_to_remote_dict(row))
                updated["note"] = note.strip()
                self._remote("update_measurement", {"record": updated})
                self.pull_remote_database(show_message=False)
            else:
                self.refresh()
            self.status.set(
                tr("note_updated", backup=local_backup)
            )
        except Exception as exc:
            messagebox.showerror(
                APP_TITLE,
                tr("edit_failed", error=exc),
            )

    def connect(self):
        if not self.db_path:
            raise RuntimeError(tr("no_database_open"))
        con = sqlite3.connect(self.db_path)
        con.row_factory = sqlite3.Row
        return con

    def open_db(self):
        p = filedialog.askopenfilename(
            title=tr("open_database_title"),
            filetypes=[
                ("SQLite", "*.db *.sqlite *.sqlite3"),
                (tr("all_files"), "*.*"),
            ]
        )
        if not p:
            return
        try:
            path = Path(p)
            ensure_schema(path)
            self.db_path = path
            self.remote_link = None
            self.mode.config(text=tr("local_database"))
            self.status.set(str(path))
            self.refresh()
            self._schedule_settings_save()
        except Exception as e:
            messagebox.showerror(APP_TITLE, str(e))

    def rows(self) -> list[sqlite3.Row]:
        if not self.db_path:
            return []

        con = self.connect()
        try:
            columns = {
                r[1]
                for r in con.execute(
                    "PRAGMA table_info(measurements)"
                ).fetchall()
            }

            automatic_sql = (
                "automatic"
                if "automatic" in columns
                else "0 AS automatic"
            )

            automatic_session_sql = (
                "automatic_session_id"
                if "automatic_session_id" in columns
                else "NULL AS automatic_session_id"
            )

            automatic_sequence_sql = (
                "automatic_sequence"
                if "automatic_sequence" in columns
                else "NULL AS automatic_sequence"
            )

            return con.execute(
                f"""
                SELECT
                    id,
                    timestamp,
                    note,
                    {automatic_sql},
                    {automatic_session_sql},
                    {automatic_sequence_sql},
                    uvc,
                    uvb,
                    uva,
                    violetto,
                    blu,
                    verde,
                    giallo,
                    arancione,
                    rosso,
                    f8,
                    nir
                FROM measurements
                ORDER BY timestamp DESC, id DESC
                """
            ).fetchall()
        finally:
            con.close()

    def refresh(self):
        if not self.db_path:
            return
        try:
            rows = self.rows()
            self.row_map = {int(r["id"]): r for r in rows}
            for x in self.tree.get_children():
                self.tree.delete(x)
            for session_id, block_rows in grouped_measurement_rows(rows):
                if session_id is not None:
                    count = len(block_rows)
                    session_first_record = min(
                        block_rows,
                        key=lambda row: int(row["timestamp"])
                    )
                    session_start = int(
                        session_first_record["timestamp"]
                    )
                    session_note = (
                        automatic_session_note_name(
                            session_first_record
                        )
                    )
                    self.tree.insert(
                        "",
                        END,
                        iid=f"session:{session_id}",
                        values=(
                            "",
                            session_id,
                            format_time(session_start),
                            "A",
                            tr(
                                "automatic_session",
                                note=session_note,
                                id=session_id,
                                count=count,
                            )
                        ),
                        tags=("session_header",)
                    )

                for r in block_rows:
                    note = (r["note"] or "").replace("\n", " ").strip()
                    if len(note) > 80:
                        note = note[:77] + "..."

                    auto_badge = (
                        ""
                        if session_id is not None
                        else ("A" if is_automatic(r) else "")
                    )
                    tags = (
                        ("session_member",)
                        if session_id is not None
                        else ()
                    )

                    self.tree.insert(
                        "",
                        END,
                        iid=str(r["id"]),
                        values=(
                            r["id"],
                            r["automatic_session_id"] or "",
                            format_time(r["timestamp"]),
                            auto_badge,
                            note
                        ),
                        tags=tags
                    )

            self.count.config(
                text=tr("measurements_count", count=len(rows))
            )
            if rows:
                iid = str(rows[0]["id"])
                self.tree.selection_set(iid)
                self.show_detail(rows[0])
            else:
                self.clear_detail()
        except Exception as e:
            messagebox.showerror(
                APP_TITLE,
                tr("database_read_error", error=e),
            )

    @staticmethod
    def pct(v: float, total: float) -> str:
        return "0.0%" if total <= 0 else f"{100*v/total:.1f}%"

    def clear_detail(self):
        self.detail_title.config(text=tr("no_measurement"))
        self.detail_acquisition.config(text="")
        self.detail_note.config(text="")

        for rows in self.detail_vars.values():
            for v, p in rows:
                v.set("—")
                p.set("")

        for value in self.bio_vars.values():
            value.set("—")

    def selected_record_id(self) -> int | None:
        selected = self.tree.selection()
        if not selected:
            return None
        try:
            record_id = int(selected[0])
        except (TypeError, ValueError):
            return None
        return record_id if record_id in self.row_map else None

    def selection_changed(self, _event=None):
        record_id = self.selected_record_id()
        if record_id is not None:
            self.show_detail(self.row_map[record_id])

    def show_detail(self, r: sqlite3.Row):
        d = derived(r)

        self.detail_title.config(
            text=tr(
                "measurement_title",
                id=r["id"],
                time=format_time(r["timestamp"]),
            )
        )

        self.detail_acquisition.config(
            text=tr(
                "acquisition_label",
                type=acquisition_type(r),
            )
        )

        note = (r["note"] or "").strip()
        self.detail_note.config(
            text=tr(
                "note_label",
                note=note if note else tr("no_note"),
            )
        )

        vals = {
            "UV": [
                (d["uvc"], self.pct(d["uvc"], d["uv_total"])),
                (d["uvb"], self.pct(d["uvb"], d["uv_total"])),
                (d["uva"], self.pct(d["uva"], d["uv_total"]))
            ],
            "HEV / HEB": [
                (d["hev"], self.pct(d["hev"], d["vis_total"])),
                (d["heb"], self.pct(d["heb"], d["vis_total"]))
            ],
            tr("visible"): [
                (d[k], self.pct(d[k], d["vis_total"]))
                for k in (
                    "violetto",
                    "blu",
                    "verde",
                    "giallo",
                    "arancione",
                    "rosso"
                )
            ],
            "FAR-RED / NIR": [
                (d["f8"], self.pct(d["f8"], d["nir_total"])),
                (d["nir"], self.pct(d["nir"], d["nir_total"]))
            ],
        }
        for name, pairs in vals.items():
            for (value, pct), (vv, pv) in zip(
                pairs,
                self.detail_vars[name]
            ):
                vv.set(
                    f"{value:,.1f} µW/cm²"
                )
                pv.set(
                    pct
                    + (
                        " vis."
                        if name == "HEV / HEB"
                        else ""
                    )
                )

        b = biological_effects(r)

        self.bio_vars["dna_uv"].set(
            f"{tr('weighted_irradiance')}:\n{b['dna_uv']:,.2f} µW/cm² eq."
        )
        self.bio_vars["dna_uv_score"].set(
            f"{tr('spectral_relevance')}: {b['dna_uv_score']:.0f} / 100"
        )
        self.bio_bars["dna_uv_score"]["value"] = b["dna_uv_score"]

        self.bio_vars["uva_photoaging"].set(
            f"{tr('weighted_irradiance')}:\n{b['uva_photoaging']:,.2f} µW/cm² eq."
        )
        self.bio_vars["uva_photoaging_score"].set(
            f"{tr('spectral_relevance')}: {b['uva_photoaging_score']:.0f} / 100"
        )
        self.bio_bars["uva_photoaging_score"]["value"] = b["uva_photoaging_score"]

        self.bio_vars["hev_oxidative"].set(
            f"{tr('weighted_irradiance')}:\n{b['hev_oxidative']:,.2f} µW/cm² eq."
        )
        self.bio_vars["hev_oxidative_score"].set(
            f"{tr('spectral_relevance')}: {b['hev_oxidative_score']:.0f} / 100"
        )
        self.bio_bars["hev_oxidative_score"]["value"] = b["hev_oxidative_score"]

    def do_export(self, kind: str):
        if not self.db_path:
            messagebox.showinfo(APP_TITLE, tr("open_database_first"))
            return
        rows = self.rows()
        if not rows:
            messagebox.showinfo(APP_TITLE, tr("nothing_to_export"))
            return
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        ext, types = {
            "csv": (".csv", [("CSV", "*.csv")]),
            "xlsx": (".xlsx", [("Excel", "*.xlsx")]),
            "ods": (".ods", [("LibreOffice Calc", "*.ods")]),
        }[kind]
        p = filedialog.asksaveasfilename(defaultextension=ext, initialfile=f"uvir_{stamp}{ext}", filetypes=types)
        if not p:
            return
        try:
            out = Path(p)
            if kind == "csv":
                with out.open("w", newline="", encoding="utf-8-sig") as f:
                    w = csv.writer(f, delimiter=";")
                    w.writerow(EXPORT_COLUMNS)
                    w.writerows(export_row(r) for r in rows)
            elif kind == "xlsx":
                write_xlsx(out, rows)
            else:
                write_ods(out, rows)
            self.status.set(
                tr("exported", count=len(rows), path=out)
            )
            messagebox.showinfo(
                APP_TITLE,
                tr("export_complete", path=out),
            )
        except Exception as e:
            messagebox.showerror(
                APP_TITLE,
                tr("export_error", error=e),
            )

    def delete_one(self):
        rid = self.selected_record_id()
        if not self.db_path or rid is None:
            messagebox.showinfo(APP_TITLE, tr("select_measurement_short"))
            return
        if messagebox.askyesno(
            APP_TITLE,
            tr("delete_one_question", id=rid),
        ):
            self.delete_sql("DELETE FROM measurements WHERE id=?", (rid,))

    def delete_all(self):
        if not self.db_path or not self.row_map:
            return
        if not messagebox.askyesno(
            APP_TITLE,
            tr("delete_all_question", count=len(self.row_map)),
        ):
            return
        if not messagebox.askyesno(
            APP_TITLE,
            tr("delete_all_final"),
        ):
            return
        self.delete_sql(
            "DELETE FROM measurements",
            (),
            delete_all_records=True
        )

    def reset_counters(self):
        if not self.db_path and not self.remote_link:
            messagebox.showinfo(
                APP_TITLE,
                tr("open_or_connect")
            )
            return

        if not messagebox.askyesno(
            APP_TITLE,
            tr("reset_question")
        ):
            return

        try:
            if self.remote_link:
                self._remote("reset_counters")
                self.pull_remote_database(show_message=False)
                self.status.set(
                    tr("counters_reset_remote")
                )
                messagebox.showinfo(
                    APP_TITLE,
                    tr("counters_reset_remote_dialog")
                )
                return

            if not self.db_path:
                return

            ensure_schema(self.db_path)
            local_backup = backup_db(self.db_path)
            con = self.connect()
            try:
                con.execute("DELETE FROM measurements")
                con.execute(
                    "DELETE FROM sqlite_sequence WHERE name = ?",
                    ("measurements",)
                )
                con.execute(
                    """
                    UPDATE uvir_counters
                    SET value = 0
                    WHERE name = ?
                    """,
                    (SESSION_COUNTER_NAME,)
                )
                con.commit()
            finally:
                con.close()

            self.refresh()
            self.status.set(
                tr("counters_reset_backup", backup=local_backup)
            )
            messagebox.showinfo(
                APP_TITLE,
                tr(
                    "counters_reset_local_dialog",
                    backup=local_backup,
                )
            )
        except Exception as exc:
            messagebox.showerror(
                APP_TITLE,
                tr("reset_failed", error=exc)
            )

    def delete_sql(
        self,
        sql: str,
        params: tuple,
        delete_all_records: bool = False
    ):
        if not self.db_path:
            return

        try:
            local_backup = backup_db(self.db_path)

            # Applica realmente la cancellazione al DB PC.
            con = self.connect()
            try:
                cur = con.execute(sql, params)
                affected = cur.rowcount

                con.commit()

                # Consolida tutto nel file principale uvir.db.
                try:
                    con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                except sqlite3.DatabaseError:
                    pass
            finally:
                con.close()

            self.refresh()

            # Se non è stato modificato nulla, lo segnaliamo chiaramente.
            if affected == 0:
                messagebox.showwarning(
                    APP_TITLE,
                    tr("nothing_deleted")
                )
                return

            # Con il nuovo canale remoto le modifiche vengono applicate tramite
            # API anche alle build release, senza accesso run-as al file privato.
            if self.remote_link:
                try:
                    if delete_all_records:
                        self._remote("delete_all")
                    else:
                        ids = [int(value) for value in params]
                        self._remote("delete_measurements", {"ids": ids})

                    self.pull_remote_database(show_message=False)

                    self.status.set(
                        tr(
                            "deletion_synced",
                            backup=local_backup,
                        )
                    )

                    messagebox.showinfo(
                        APP_TITLE,
                        tr("measurement_deleted_remote")
                    )

                except Exception as sync_error:
                    self.status.set(
                        tr("deletion_sync_failed_status")
                    )

                    messagebox.showerror(
                        APP_TITLE,
                        tr(
                            "deletion_sync_failed",
                            error=sync_error,
                            backup=local_backup,
                        )
                    )

            else:
                self.status.set(
                    tr(
                        "deletion_local_status",
                        backup=local_backup,
                    )
                )

                messagebox.showinfo(
                    APP_TITLE,
                    tr("measurement_deleted_local")
                )

        except Exception as e:
            self.refresh()
            messagebox.showerror(
                APP_TITLE,
                tr("deletion_error", error=e)
            )

def main():
    configure_windows_app_id()

    root = Tk()
    apply_uvir_icon(root)

    try:
        style = ttk.Style(root)
        if "vista" in style.theme_names():
            style.theme_use("vista")
    except Exception:
        pass
    App(root)
    root.mainloop()


if __name__ == "__main__":
    main()
