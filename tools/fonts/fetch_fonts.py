"""Re-download the bundled Lato fonts from google/fonts and verify their checksums.

Usage:
    python tools/fonts/fetch_fonts.py           # verify existing files, download any missing or mismatched
    python tools/fonts/fetch_fonts.py --check   # verify only, never download (exit 1 on mismatch)

Standard library only.
"""

import hashlib
import sys
import urllib.request
from pathlib import Path

COMMIT = "5d3b76120a319730fda218cc7410174a462b32cb"
BASE_URL = f"https://raw.githubusercontent.com/google/fonts/{COMMIT}/ofl/lato/"

FILES = {
    "Lato-Regular.ttf": "d636e4683231f931eda222d588e944d082bfd3bdba02f928bee461c0f185b251",
    "Lato-Bold.ttf": "8a0aace75d33794eece4b28187bfc1df0bbd2888b5d8a56e01788c8d65d16be1",
    "OFL.txt": "74ba064d03f1f1c4a952da936c3eb71866c34404916734de3cae73b34357e59e",
}

FONT_DIR = Path(__file__).resolve().parent


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    check_only = "--check" in sys.argv[1:]
    failures = 0
    for name, expected in FILES.items():
        path = FONT_DIR / name
        if path.is_file() and sha256(path.read_bytes()) == expected:
            print(f"OK        {name}")
            continue
        if check_only:
            state = "MISMATCH" if path.is_file() else "MISSING"
            print(f"{state:<9} {name}")
            failures += 1
            continue
        url = BASE_URL + name
        print(f"FETCH     {name} <- {url}")
        with urllib.request.urlopen(url, timeout=60) as resp:
            data = resp.read()
        actual = sha256(data)
        if actual != expected:
            print(f"BAD HASH  {name}: expected {expected}, got {actual}")
            failures += 1
            continue
        path.write_bytes(data)
        print(f"OK        {name}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
