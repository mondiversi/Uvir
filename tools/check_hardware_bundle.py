"""Validate the published hardware bundle; --refresh records intentional file edits.

This checks integrity, not electrical correctness or fabrication readiness.
No third-party Python packages or original private intake files are required.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true', help='Update checksums after reviewed edits')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]/'hardware/pcb/uvir-carrier-2026-10-05'
    manifest_file = root/'manifest.json'
    manifest = json.loads(manifest_file.read_text(encoding='utf-8'))
    actual = sorted(p.relative_to(root).as_posix() for p in root.rglob('*')
                    if p.is_file() and p != manifest_file)
    expected = sorted(item['path'] for item in manifest['files'])
    if actual != expected:
        raise SystemExit('Bundle inventory differs from the manifest; review added/missing paths first')
    for entry in manifest['files']:
        relative = Path(entry['path'])
        file = (root/relative).resolve()
        if relative.is_absolute() or not file.is_relative_to(root.resolve()):
            raise SystemExit('Unsafe manifest path')
        data = file.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if args.refresh:
            entry.update(bytes=len(data), sha256=digest)
        elif entry['bytes'] != len(data) or entry['sha256'] != digest:
            raise SystemExit('Checksum mismatch: '+entry['path'])
        if file.suffix == '.json':
            json.loads(data)
        elif file.suffix == '.zip':
            with zipfile.ZipFile(file) as archive:
                if archive.testzip() is not None:
                    raise SystemExit('Corrupt ZIP archive')
                for member in archive.infolist():
                    if member.filename.startswith(('/', '\\')) or '..' in Path(member.filename).parts:
                        raise SystemExit('Unsafe archive path')
    if args.refresh:
        manifest['status'] = 'development-snapshot-not-approved-for-fabrication'
        manifest_file.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(f"Hardware bundle: {len(actual)} files verified; not fabrication approval.")


if __name__ == '__main__':
    main()
