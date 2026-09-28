"""Render a local visual preview of the generated EasyEDA schematic.

This does not validate EasyEDA import. It helps spot routing and label issues
without altering the native schematic file.
"""

from __future__ import annotations

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, Rectangle


HERE = Path(__file__).resolve().parent
SOURCE = HERE / "uvir-carrier-r1-easyeda-standard-schematic.json"
OUTPUT = HERE / "uvir-carrier-r1-schematic-preview.png"


def main() -> None:
    document = json.loads(SOURCE.read_text(encoding="utf-8"))
    fig, ax = plt.subplots(figsize=(18, 19), dpi=120)
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")

    for item in document["shape"]:
        for part in item.split("#@$"):
            if part.startswith("W~"):
                xy = [int(n) for n in part.split("~")[1].split()]
                ax.plot(xy[0::2], xy[1::2], color="#15803d", linewidth=1.25)
            elif part.startswith("J~"):
                bits = part.split("~")
                ax.add_patch(Circle((int(bits[1]), int(bits[2])), 4, color="#b91c1c"))
            elif part.startswith("R~"):
                bits = part.split("~")
                x, y, w, h = map(int, (bits[1], bits[2], bits[5], bits[6]))
                ax.add_patch(Rectangle((x, y), w, h, linewidth=1, edgecolor="#991b1b", facecolor="#fffafa"))
            elif part.startswith("P~"):
                blocks = part.split("^^")
                pin = blocks[0].split("~")
                x, y = int(pin[4]), int(pin[5])
                ax.add_patch(Circle((x, y), 2.5, color="#991b1b"))
                label = blocks[3].split("~")
                ax.text(int(label[1]), int(label[2]), label[4],
                        fontsize=5.2, ha="left" if pin[6] == "180" else "right",
                        va="center", color="#334155")
            elif part.startswith("T~"):
                bits = part.split("~")
                if len(bits) > 12 and bits[1] in {"P", "N"}:
                    ax.text(int(bits[2]), int(bits[3]), bits[12],
                            fontsize=7 if bits[1] == "P" else 6,
                            fontweight="bold" if bits[1] == "P" else "normal",
                            ha="left", va="center", color="#1e3a8a")
    ax.set_xlim(50, 1720)
    ax.set_ylim(1850, 30)
    ax.set_aspect("equal")
    ax.axis("off")
    fig.tight_layout(pad=0.3)
    fig.savefig(OUTPUT, bbox_inches="tight")
    print(OUTPUT)


if __name__ == "__main__":
    main()
