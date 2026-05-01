"""Shared interface for manufacturer flight-number scrapers.

Each per-brand scraper should subclass `MfrScraper` and implement `scrape()`.
Output schema (one row per disc mold):
{
  "brand": "Innova",
  "mold": "Destroyer",
  "speed": 12,
  "glide": 5,
  "turn": -1,
  "fade": 3,
  "disc_type": "Driver",          # raw label from manufacturer
  "stamp_url": "https://...",     # optional
  "year_released": 2008,          # optional
  "aliases": []                   # optional
}
"""

from __future__ import annotations
import json
import time
from pathlib import Path
from dataclasses import asdict, dataclass, field
from typing import Iterable

import requests

USER_AGENT = "BaggerDiscDb/1.0 (+https://github.com/LightWraith8268/bagger)"
SLEEP_SECONDS = 0.5
ROOT = Path(__file__).resolve().parents[3]
MFR_INTERMEDIATE = ROOT / "data" / "intermediate" / "mfr"


@dataclass
class MfrDisc:
    brand: str
    mold: str
    speed: float
    glide: float
    turn: float
    fade: float
    disc_type: str | None = None
    stamp_url: str | None = None
    year_released: int | None = None
    aliases: list[str] = field(default_factory=list)


class MfrScraper:
    """Base class — subclass and implement `scrape()`."""
    brand_slug: str = ""        # filename stem, e.g. "innova"
    brand_display: str = ""     # canonical brand name in disc records, e.g. "Innova"

    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT

    def scrape(self) -> Iterable[MfrDisc]:
        raise NotImplementedError

    def get(self, url: str) -> str:
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        time.sleep(SLEEP_SECONDS)
        return resp.text

    def write_output(self, discs: Iterable[MfrDisc]) -> Path:
        MFR_INTERMEDIATE.mkdir(parents=True, exist_ok=True)
        output = MFR_INTERMEDIATE / f"{self.brand_slug}.json"
        rows = [asdict(d) for d in discs]
        output.write_text(json.dumps(rows, indent=2, ensure_ascii=False))
        return output

    def run(self) -> None:
        discs = list(self.scrape())
        path = self.write_output(discs)
        print(f"Wrote {len(discs)} {self.brand_display} discs to {path}")
