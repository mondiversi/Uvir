"""Check current measuring-head documentation without accessing hardware."""
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def main():
    names = subprocess.check_output([
        'git', '-C', str(ROOT), 'ls-files', '--cached', '--others',
        '--exclude-standard', '-z'
    ]).decode().split('\0')
    public_files = {ROOT / name for name in names if name and (ROOT / name).is_file()}
    files = {p for p in public_files if p.suffix == '.md' and (
        p.parent == ROOT or p.is_relative_to(ROOT / 'docs') or
        p.is_relative_to(ROOT / 'hardware'))}
    failures = []
    links = 0
    for file in sorted(files):
        text = file.read_text(encoding='utf-8')
        for match in re.finditer(r'\[[^\]\n]*\]\(([^)\n]+)\)', text):
            target = match.group(1).strip()
            target = target[1:target.index('>')] if target.startswith('<') else target.split()[0]
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            destination = (file.parent / unquote(parsed.path)).resolve()
            present = destination in public_files or any(p.is_relative_to(destination) for p in public_files)
            if not destination.is_relative_to(ROOT) or not present:
                failures.append(f'{file.relative_to(ROOT)}: unresolved published link {target}')
            links += 1

    wiring = (ROOT / 'docs/usb/WIRING.md').read_text(encoding='utf-8')
    for token in ('FT232H', 'AD0', 'AD1', 'AD2', 'AS7331', 'AS7343', '0x74', '0x39'):
        if token not in wiring:
            failures.append('Wiring reference missing: ' + token)
    case = ROOT / 'hardware/printable/uvir-custom-case'
    for name in ('uvir-custom-case-base-r03.stl', 'uvir-custom-case-lid-r06-5mm.stl',
                 'build_case.py', 'build_lid.py', 'verify_stl.py'):
        if case / name not in public_files:
            failures.append('Current printable missing: ' + name)
    status = (ROOT / 'README.md').read_text(encoding='utf-8')
    if 'OPTICAL CALIBRATION IS UNRESOLVED' not in status:
        failures.append('Main project calibration status missing')
    if failures:
        raise SystemExit('\n'.join(failures))
    print(f'Documentation: {len(files)} Markdown files, {links} published local links, FT232H wiring and current case/lid checked.')
    print('Reference coverage only; not electrical, physical-fit or optical validation.')


if __name__ == '__main__':
    main()
