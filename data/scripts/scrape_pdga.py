"""Scrape the PDGA approved-disc list into intermediate JSON.

Output: data/intermediate/pdga_raw.json — array of:
{
  "manufacturer": "Innova Champion Discs",
  "mold": "Aviar",
  "disc_class": null,           # not present on listing page; fetched per-detail later
  "max_weight_g": null,         # not present on listing page; fetched per-detail later
  "diameter_cm": null,          # not present on listing page; fetched per-detail later
  "approved_date": "1985-01-01",
  "detail_url": "https://www.pdga.com/technical-standards/equipment-certification/discs/aviar"
}

The PDGA listing page only exposes manufacturer, mold, and approved date in the
table itself. Flight specs (class, max weight, diameter) live on each disc's
detail page (linked from the mold cell). A follow-up task can hydrate those
fields by fetching `detail_url` for each row.

Run from data/scripts/ with the venv active. Polite 1 req/sec.
"""

from __future__ import annotations
import argparse
import json
import time
from pathlib import Path
from typing import Iterable

import requests
from bs4 import BeautifulSoup

PDGA_BASE = "https://www.pdga.com/technical-standards/equipment-certification/discs"
PDGA_ORIGIN = "https://www.pdga.com"
USER_AGENT = "BaggerDiscDb/1.0 (+https://github.com/LightWraith8268/bagger)"
SLEEP_SECONDS = 1.0
ROOT = Path(__file__).resolve().parents[2]
INTERMEDIATE = ROOT / "data" / "intermediate"


def parse_pdga_table(html: str) -> list[dict]:
    """Parse a single PDGA approved-disc HTML page into row dicts.

    The live PDGA listing has 3 columns: manufacturer, mold, approved date.
    Flight specs are not on the listing — see module docstring.
    """
    soup = BeautifulSoup(html, "lxml")
    rows: list[dict] = []
    table = soup.find("table")
    if table is None:
        return rows
    tbody = table.find("tbody") or table
    for tr in tbody.find_all("tr"):
        tds = tr.find_all("td")
        if len(tds) < 3:
            continue
        manufacturer_cell, mold_cell, date_cell = tds[:3]

        manufacturer = manufacturer_cell.get_text(strip=True)
        mold = mold_cell.get_text(strip=True)
        if not manufacturer or not mold:
            continue

        approved_date = _parse_approved_date(date_cell)
        detail_url = _extract_detail_url(mold_cell)

        rows.append({
            "manufacturer": manufacturer,
            "mold": mold,
            "disc_class": None,
            "max_weight_g": None,
            "diameter_cm": None,
            "approved_date": approved_date,
            "detail_url": detail_url,
        })
    return rows


def _parse_approved_date(date_cell) -> str | None:
    """Pull ISO date from the listing's <span content="2008-04-21T..."> attr."""
    span = date_cell.find("span", attrs={"content": True})
    if span is not None:
        iso = span.get("content", "")
        # Trim time/timezone portion, keep YYYY-MM-DD.
        if "T" in iso:
            return iso.split("T", 1)[0]
        if iso:
            return iso
    text = date_cell.get_text(strip=True)
    return text or None


def _extract_detail_url(mold_cell) -> str | None:
    a = mold_cell.find("a", href=True)
    if a is None:
        return None
    href = a["href"]
    if href.startswith("http"):
        return href
    return f"{PDGA_ORIGIN}{href}"


def _parse_float(text: str, suffix: str = "") -> float | None:
    if not text:
        return None
    cleaned = text.replace(suffix, "").strip()
    try:
        return float(cleaned.split()[0])
    except (ValueError, IndexError):
        return None


def fetch_detail_page(detail_url: str, session: requests.Session) -> dict:
    """Pull max weight, diameter, and disc class from a PDGA disc detail page.

    Returns a dict with keys max_weight_g, diameter_cm, disc_class — values
    are floats / strings or None if the corresponding spec row was absent.
    """
    out = {"max_weight_g": None, "diameter_cm": None, "disc_class": None}
    resp = session.get(detail_url, timeout=30)
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "lxml")
    selectors = [
        "div.field-name-field-disc-class-tax",
        "div.field-name-field-maximum-weight",
        "div.field-name-field-diameter",
    ]
    for selector in selectors:
        for row in soup.select(selector):
            label_el = row.select_one(".field-label")
            value_el = row.select_one(".field-item")
            if not label_el or not value_el:
                continue
            label = label_el.get_text(strip=True).lower().rstrip(":")
            value = value_el.get_text(strip=True)
            if "class" in label:
                out["disc_class"] = value
            elif "weight" in label:
                out["max_weight_g"] = _parse_float(value, " g")
            elif "diameter" in label:
                out["diameter_cm"] = _parse_float(value, " cm")
    return out


def fetch_all_pages(start_page: int = 0, max_pages: int = 100) -> Iterable[str]:
    """Yield raw HTML for each paginated PDGA results page until empty."""
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    for page in range(start_page, start_page + max_pages):
        url = PDGA_BASE if page == 0 else f"{PDGA_BASE}?page={page}"
        resp = session.get(url, timeout=30)
        resp.raise_for_status()
        rows = parse_pdga_table(resp.text)
        if not rows:
            return
        yield resp.text
        time.sleep(SLEEP_SECONDS)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-pages", type=int, default=100)
    parser.add_argument("--output", type=Path, default=INTERMEDIATE / "pdga_raw.json")
    parser.add_argument(
        "--hydrate",
        action="store_true",
        help="Fetch each disc's detail page for max weight, diameter, and class (slow).",
    )
    args = parser.parse_args()

    INTERMEDIATE.mkdir(parents=True, exist_ok=True)
    all_rows: list[dict] = []
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    for html in fetch_all_pages(max_pages=args.max_pages):
        rows = parse_pdga_table(html)
        if args.hydrate:
            for row in rows:
                detail_url = row.get("detail_url")
                if not detail_url:
                    continue
                try:
                    detail = fetch_detail_page(detail_url, session)
                    row.update(detail)
                except Exception as exc:
                    print(f"detail fetch failed for {detail_url}: {exc}")
                time.sleep(SLEEP_SECONDS)
        all_rows.extend(rows)

    args.output.write_text(json.dumps(all_rows, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote {len(all_rows)} discs to {args.output}")


if __name__ == "__main__":
    main()
