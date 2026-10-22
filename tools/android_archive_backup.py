"""Local maintenance helper: prepare a public wrapping key or verify/decrypt UVIR's backup.

Private keys and archives must stay outside the repository. Never prints credentials,
notes, sample values or key material other than the public encryption key.
"""
import argparse
import base64
import io
import json
import sqlite3
import struct
import zipfile
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.archive is None:
        if not args.key.exists():
            args.key.parent.mkdir(parents=True, exist_ok=True)
            key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
            with args.key.open("xb") as destination:
                destination.write(key.private_bytes(serialization.Encoding.PEM,
                    serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        key = serialization.load_pem_private_key(args.key.read_bytes(), password=None)
        print(base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo)).decode("ascii"))
        return
    key = serialization.load_pem_private_key(args.key.read_bytes(), password=None)
    data = args.archive.read_bytes()
    assert data[:11] == b"UVIRBACKUP1", "Invalid encrypted backup header"
    size = struct.unpack(">I", data[11:15])[0]
    secret = key.decrypt(data[15:15 + size], padding.OAEP(
        mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
    start = 15 + size
    plaintext = AESGCM(secret).decrypt(data[start:start + 12], data[start + 12:], None)
    assert args.output is not None and not args.output.exists(), "Use a new private output directory"
    args.output.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(plaintext)) as archive:
        assert archive.testzip() is None
        for member in archive.infolist():
            destination = (args.output / member.filename).resolve()
            assert destination.is_relative_to(args.output.resolve()), "Unsafe backup path"
        archive.extractall(args.output)
    database = args.output / "databases/uvir.db"
    with sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True) as connection:
        assert connection.execute("PRAGMA quick_check").fetchone()[0] == "ok"
        print(json.dumps({"verified": True, "database_version": connection.execute("PRAGMA user_version").fetchone()[0],
            "acquisitions": connection.execute("SELECT COUNT(*) FROM acquisitions").fetchone()[0],
            "alerts": connection.execute("SELECT COUNT(*) FROM alerts").fetchone()[0],
            "preferences_files": len(list((args.output / "shared_prefs").glob("*.xml")))}))


if __name__ == "__main__":
    main()
