"""Transfer only a named private dry-run fixture to/from UVIR's disposable test package."""
import argparse
import io
import hashlib
import subprocess
import tarfile
from pathlib import Path

PACKAGE = "me.mondiversi.uvir.verification"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    database = args.source / "databases/uvir.db"
    preferences = args.source / "shared_prefs/uvir_preferences.xml"
    assert database.is_file() and preferences.is_file()
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        archive.add(database, arcname="files/si-real-dryrun/uvir.db")
        archive.add(preferences, arcname="shared_prefs/si-real-dryrun.xml")
    subprocess.run([args.adb, "-s", args.serial, "exec-in", "run-as", PACKAGE,
                    "tar", "-x", "-f", "-"], input=stream.getvalue(), check=True)
    for source, target in ((database, "files/si-real-dryrun/uvir.db"),
                           (preferences, "shared_prefs/si-real-dryrun.xml")):
        digest = subprocess.check_output([args.adb, "-s", args.serial, "exec-out", "run-as", PACKAGE,
                                         "sha256sum", target]).decode("ascii").split()[0]
        assert digest == hashlib.sha256(source.read_bytes()).hexdigest(), "Fixture transfer hash mismatch"
    print("Private dry-run fixtures copied to the disposable package only.")


if __name__ == "__main__":
    main()
