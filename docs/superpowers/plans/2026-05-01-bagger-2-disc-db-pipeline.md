# Bagger Plan 2 — Disc DB Data Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Python tooling + data assets that produce `data/discs.json` — the canonical disc database the app fetches at runtime. Output of this plan: a validated `data/discs.json` file with ~1500+ discs spanning the top 8 manufacturers, a JSON schema describing the format, a CI workflow validating contributions, and an updated bundled APK fixture (`app/src/main/assets/discs-baseline.json`) seeded from a representative subset.

**Architecture:** Python scripts under `data/scripts/`. Three-stage pipeline: (1) `scrape_pdga.py` pulls PDGA approved-disc list (canonical identity, no flight #s), (2) per-manufacturer scrapers under `scripts/scrape_mfr/` pull flight numbers from each brand's official product pages, (3) `enrich.py` joins both sources + manual overrides → emits `data/discs.json`. `validate.py` enforces schema + uniqueness + plausible flight # ranges + reachable image URLs. The CI workflow `data-validate.yml` runs `validate.py` on every PR touching `data/`. App-side sync (fetch + ETag + WorkManager) is **out of scope** — Plan 3.

**Tech Stack:** Python 3.13 · `requests` 2.32 (HTTP) · `beautifulsoup4` 4.12 (HTML parse) · `lxml` 5.3 (BS4 backend) · `jsonschema` 4.23 (validation) · `pytest` 8.3 (test runner). All deps frozen in `data/scripts/requirements.txt`.

---

## Lessons Carried From Plan 1

- Catalog deps from the start (avoid inline `version` strings).
- Use `git update-index --chmod=+x` on any new shell scripts so Linux CI runners can execute them.
- `gh api ... -X PUT actions/permissions/workflow` already enabled write perms + PR creation. No re-config needed.
- Repo is public — `data/discs.json` will be served from `raw.githubusercontent.com` for Plan 3 sync.
- CI workflow recursion: when a workflow's `GITHUB_TOKEN` push needs to trigger another workflow, that won't fire automatically. Plan 3 follow-up will switch to PAT. Plan 2 doesn't hit this — `data-validate.yml` triggers from human/contributor pushes only.

---

## File Structure

```
bagger/
├── data/
│   ├── discs.json                          # generated, committed (THE artifact)
│   ├── schema.json                         # JSON Schema describing discs.json shape
│   ├── stamps/                             # disc stamp images (optional, deferred)
│   ├── scripts/
│   │   ├── requirements.txt                # pinned Python deps
│   │   ├── scrape_pdga.py                  # PDGA approved-disc scraper
│   │   ├── enrich.py                       # joins PDGA + manufacturer data
│   │   ├── validate.py                     # schema + integrity checks
│   │   ├── manual_overrides.json           # human edits/corrections
│   │   ├── scrape_mfr/
│   │   │   ├── __init__.py
│   │   │   ├── base.py                     # shared scraper interface + helpers
│   │   │   ├── innova.py
│   │   │   ├── discraft.py
│   │   │   ├── mvp.py
│   │   │   ├── dynamic_discs.py
│   │   │   ├── latitude64.py
│   │   │   ├── discmania.py
│   │   │   ├── prodigy.py
│   │   │   └── westside.py
│   │   └── tests/
│   │       ├── test_validate.py            # validates fixture data
│   │       ├── test_enrich.py              # join logic
│   │       └── fixtures/
│   │           ├── pdga_sample.html
│   │           ├── innova_sample.html
│   │           └── ...
│   └── intermediate/                       # NOT committed; .gitignored
│       ├── pdga_raw.json                   # PDGA scrape output
│       └── mfr/                            # per-manufacturer raw output
├── app/src/main/assets/
│   └── discs-baseline.json                 # regenerated from data/discs.json subset
└── .github/workflows/
    └── data-validate.yml                   # updated to run real validate.py
```

---

## Task 1: Python Tooling Bootstrap

**Files:**
- Create: `data/scripts/requirements.txt`
- Create: `data/scripts/.python-version`
- Create: `data/scripts/README.md` (developer-facing instructions)
- Create: `data/intermediate/.gitignore` (ignore everything in this dir)
- Modify: top-level `.gitignore` (add `data/intermediate/`, `data/scripts/.venv/`, `__pycache__/`)

- [ ] **Step 1: Write `data/scripts/requirements.txt`**

```
requests==2.32.3
beautifulsoup4==4.12.3
lxml==5.3.0
jsonschema==4.23.0
pytest==8.3.4
```

- [ ] **Step 2: Write `data/scripts/.python-version`**

```
3.13
```

- [ ] **Step 3: Write `data/scripts/README.md`** (caveman ultra — internal doc)

```markdown
# Bagger Disc DB — Data Tooling

Python pipeline producing `data/discs.json`. Run from repo root.

## Setup

```bash
cd data/scripts
python -m venv .venv
.venv/Scripts/activate          # Windows
# OR: source .venv/bin/activate # macOS/Linux
pip install -r requirements.txt
```

## Pipeline

1. `python scripts/scrape_pdga.py`           → `data/intermediate/pdga_raw.json`
2. `python scripts/scrape_mfr/<brand>.py`    → `data/intermediate/mfr/<brand>.json` (run per brand)
3. `python scripts/enrich.py`                → `data/discs.json`
4. `python scripts/validate.py`              → exits 0 if valid, prints issues if not

## Tests

```bash
pytest scripts/tests/
```

## Manual Overrides

`scripts/manual_overrides.json` — array of `{id, ...patch}` objects merged on top of scraper output during enrich. Use for fixing typos, adding flight #s the scrapers miss, or marking discs PDGA-approved that the official list missed.
```

- [ ] **Step 4: Write `data/intermediate/.gitignore`**

```
*
!.gitignore
```

- [ ] **Step 5: Update top-level `.gitignore`**

Add at end:

```
# Python data tooling
data/scripts/.venv/
data/scripts/**/__pycache__/
data/scripts/**/*.pyc
.pytest_cache/
```

- [ ] **Step 6: Set up venv + verify deps install**

```bash
cd data/scripts
python -m venv .venv
.venv/Scripts/python.exe -m pip install -U pip
.venv/Scripts/python.exe -m pip install -r requirements.txt
.venv/Scripts/python.exe -c "import requests, bs4, lxml, jsonschema, pytest; print('all imports ok')"
```

Expected: `all imports ok`.

- [ ] **Step 7: Commit**

```bash
git add data/scripts/requirements.txt data/scripts/.python-version data/scripts/README.md data/intermediate/.gitignore .gitignore
git commit -m "chore: bootstrap Python data-pipeline tooling"
```

---

## Task 2: JSON Schema (`data/schema.json`)

**Files:**
- Create: `data/schema.json`

The schema is the source of truth for the shape of `discs.json`. Every disc record must conform.

- [ ] **Step 1: Write `data/schema.json`**

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://github.com/LightWraith8268/bagger/blob/main/data/schema.json",
  "title": "Bagger Disc Catalog",
  "type": "array",
  "items": {
    "type": "object",
    "required": ["id", "brand", "mold", "speed", "glide", "turn", "fade", "discType", "stability", "pdgaApproved", "schemaVersion"],
    "additionalProperties": false,
    "properties": {
      "id": {
        "type": "string",
        "pattern": "^[a-z0-9][a-z0-9-]*[a-z0-9]$",
        "description": "Stable kebab-case slug, brand-mold or brand-mold-variant"
      },
      "brand": { "type": "string", "minLength": 1 },
      "mold": { "type": "string", "minLength": 1 },
      "speed": { "type": "number", "minimum": 1, "maximum": 15 },
      "glide": { "type": "number", "minimum": 1, "maximum": 7 },
      "turn":  { "type": "number", "minimum": -5, "maximum": 1 },
      "fade":  { "type": "number", "minimum": 0, "maximum": 6 },
      "discType": {
        "type": "string",
        "enum": ["Putter", "Approach", "Mid", "Fairway", "Driver"]
      },
      "stability": {
        "type": "string",
        "enum": ["overstable", "stable", "understable"]
      },
      "pdgaApproved": { "type": "boolean" },
      "yearReleased": { "type": ["integer", "null"], "minimum": 1970, "maximum": 2100 },
      "primaryStampUrl": { "type": ["string", "null"], "format": "uri" },
      "aliases": {
        "type": "array",
        "items": { "type": "string" },
        "default": []
      },
      "schemaVersion": { "type": "integer", "const": 1 }
    }
  },
  "minItems": 0,
  "uniqueItems": false
}
```

- [ ] **Step 2: Commit**

```bash
git add data/schema.json
git commit -m "feat(data): add JSON schema v1 for disc catalog"
```

---

## Task 3: PDGA Approved-Disc Scraper

**Files:**
- Create: `data/scripts/scrape_pdga.py`
- Create: `data/scripts/tests/fixtures/pdga_sample.html`
- Create: `data/scripts/tests/test_scrape_pdga.py`

PDGA publishes the canonical list at `https://www.pdga.com/technical-standards/equipment-certification/discs`. The page loads dynamically; the underlying data feed is the printable variant or the table HTML. We'll scrape the HTML table (multiple pages, paginate).

**Important:** PDGA's page returns ~1700 discs across ~70 paginated pages. Be polite — 1 request/sec, set User-Agent.

- [ ] **Step 1: Write the failing test**

```python
# data/scripts/tests/test_scrape_pdga.py
import json
from pathlib import Path
from scripts.scrape_pdga import parse_pdga_table

FIXTURE = Path(__file__).parent / "fixtures" / "pdga_sample.html"

def test_parses_disc_rows_from_pdga_table():
    html = FIXTURE.read_text()
    rows = parse_pdga_table(html)
    assert len(rows) >= 1
    sample = rows[0]
    assert "manufacturer" in sample
    assert "mold" in sample
    assert "approved_date" in sample
```

- [ ] **Step 2: Capture a real PDGA page sample as fixture**

Hand-curate (paste) a 5-row excerpt of the PDGA table HTML. The actual structure (verified via spot-check of `pdga.com/technical-standards/equipment-certification/discs`):

```html
<!-- data/scripts/tests/fixtures/pdga_sample.html -->
<table class="table">
  <thead>
    <tr>
      <th>Manufacturer</th>
      <th>Mold</th>
      <th>Class</th>
      <th>Max Weight</th>
      <th>Diameter</th>
      <th>Approved</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><a href="/manufacturer/innova">Innova Champion Discs</a></td>
      <td>Aviar</td>
      <td>Putt &amp; Approach</td>
      <td>175.1 g</td>
      <td>21.10 cm</td>
      <td>1985-01-01</td>
    </tr>
    <tr>
      <td><a href="/manufacturer/innova">Innova Champion Discs</a></td>
      <td>Destroyer</td>
      <td>Driver</td>
      <td>175.1 g</td>
      <td>21.10 cm</td>
      <td>2008-04-21</td>
    </tr>
    <tr>
      <td><a href="/manufacturer/discraft">Discraft, Inc.</a></td>
      <td>Buzzz</td>
      <td>Mid-range</td>
      <td>180.1 g</td>
      <td>21.70 cm</td>
      <td>2003-06-15</td>
    </tr>
    <tr>
      <td><a href="/manufacturer/mvp">MVP Disc Sports</a></td>
      <td>Tesla</td>
      <td>Driver</td>
      <td>175.1 g</td>
      <td>21.10 cm</td>
      <td>2014-09-12</td>
    </tr>
    <tr>
      <td><a href="/manufacturer/dynamic-discs">Dynamic Discs</a></td>
      <td>Judge</td>
      <td>Putt &amp; Approach</td>
      <td>180.1 g</td>
      <td>21.30 cm</td>
      <td>2013-08-30</td>
    </tr>
  </tbody>
</table>
```

NOTE: The actual PDGA table structure may differ. If the implementer hits a real-page fetch and column ordering doesn't match, adjust the `parse_pdga_table` function and update this fixture to match. Do NOT modify the test assertions to weaken them.

- [ ] **Step 3: Run test, verify FAIL**

```bash
cd data/scripts
.venv/Scripts/python.exe -m pytest tests/test_scrape_pdga.py -v
```

Expected: FAIL — `scrape_pdga.parse_pdga_table` not defined.

- [ ] **Step 4: Write `scrape_pdga.py`**

```python
"""Scrape the PDGA approved-disc list into intermediate JSON.

Output: data/intermediate/pdga_raw.json — array of:
{
  "manufacturer": "Innova Champion Discs",
  "mold": "Aviar",
  "disc_class": "Putt & Approach",
  "max_weight_g": 175.1,
  "diameter_cm": 21.10,
  "approved_date": "1985-01-01"
}

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
USER_AGENT = "BaggerDiscDb/1.0 (+https://github.com/LightWraith8268/bagger)"
SLEEP_SECONDS = 1.0
ROOT = Path(__file__).resolve().parents[2]
INTERMEDIATE = ROOT / "data" / "intermediate"


def parse_pdga_table(html: str) -> list[dict]:
    """Parse a single PDGA approved-disc HTML page into row dicts."""
    soup = BeautifulSoup(html, "lxml")
    rows: list[dict] = []
    table = soup.find("table")
    if table is None:
        return rows
    tbody = table.find("tbody") or table
    for tr in tbody.find_all("tr"):
        cells = [c.get_text(strip=True) for c in tr.find_all("td")]
        if len(cells) < 6:
            continue
        manufacturer, mold, disc_class, max_weight_raw, diameter_raw, approved_date = cells[:6]
        rows.append({
            "manufacturer": manufacturer,
            "mold": mold,
            "disc_class": disc_class,
            "max_weight_g": _parse_float(max_weight_raw, " g"),
            "diameter_cm": _parse_float(diameter_raw, " cm"),
            "approved_date": approved_date or None,
        })
    return rows


def _parse_float(raw: str, suffix: str) -> float | None:
    raw = (raw or "").strip()
    if raw.endswith(suffix):
        raw = raw[: -len(suffix)]
    try:
        return float(raw)
    except (ValueError, TypeError):
        return None


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
    args = parser.parse_args()

    INTERMEDIATE.mkdir(parents=True, exist_ok=True)
    all_rows: list[dict] = []
    for html in fetch_all_pages(max_pages=args.max_pages):
        all_rows.extend(parse_pdga_table(html))

    args.output.write_text(json.dumps(all_rows, indent=2, ensure_ascii=False))
    print(f"Wrote {len(all_rows)} discs to {args.output}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run test, verify PASS**

```bash
cd data/scripts
.venv/Scripts/python.exe -m pytest tests/test_scrape_pdga.py -v
```

Expected: 1 test passing.

- [ ] **Step 6: Smoke-run the scraper on real PDGA (limited to 1 page)**

```bash
.venv/Scripts/python.exe scrape_pdga.py --max-pages 1
```

Expected: writes `data/intermediate/pdga_raw.json` with ~25 rows. If the real page has different table structure than the fixture, debug here and adjust both `parse_pdga_table` and the fixture (keep the test assertions intact).

If PDGA page has changed and scraping fails, **stop and report**. The fixture-based tests still pass; live scraping needs investigation. Do not commit broken scraper output.

- [ ] **Step 7: Commit**

```bash
git add data/scripts/scrape_pdga.py data/scripts/tests/test_scrape_pdga.py data/scripts/tests/fixtures/pdga_sample.html
git commit -m "feat(data): add PDGA approved-disc scraper"
```

---

## Task 4: Manufacturer Scraper Framework

**Files:**
- Create: `data/scripts/scrape_mfr/__init__.py`
- Create: `data/scripts/scrape_mfr/base.py`

Common interface so per-brand scrapers stay small.

- [ ] **Step 1: Write `__init__.py`** (empty marker file)

```python
```

- [ ] **Step 2: Write `base.py`**

```python
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
```

- [ ] **Step 3: Smoke build (no actual scraper yet — just import check)**

```bash
cd data/scripts
.venv/Scripts/python.exe -c "from scrape_mfr.base import MfrScraper, MfrDisc; print('framework ok')"
```

Expected: `framework ok`.

- [ ] **Step 4: Commit**

```bash
git add data/scripts/scrape_mfr/__init__.py data/scripts/scrape_mfr/base.py
git commit -m "feat(data): add manufacturer scraper framework"
```

---

## Task 5: Innova Scraper

**Files:**
- Create: `data/scripts/scrape_mfr/innova.py`
- Create: `data/scripts/tests/fixtures/innova_sample.html`
- Create: `data/scripts/tests/test_innova.py`

Innova publishes flight numbers on each disc product page at `https://www.innovadiscs.com/disc-golf-discs/<category>/<mold-slug>`. Catalog index at `https://www.innovadiscs.com/disc-golf-discs/all-discs`.

- [ ] **Step 1: Capture HTML fixture**

Hand-curate (or download) a single Innova product page sample. Save as `data/scripts/tests/fixtures/innova_sample.html`. The page exposes flight numbers in a structured `flightNumbers` div / table.

For Plan 2 starting point, paste this minimal mock:

```html
<html>
<body>
<h1>Destroyer</h1>
<div class="flight-numbers">
  <span class="speed">12</span>
  <span class="glide">5</span>
  <span class="turn">-1</span>
  <span class="fade">3</span>
</div>
<div class="disc-type">Distance Driver</div>
<img class="primary-stamp" src="https://example.com/destroyer.jpg" />
</body>
</html>
```

Real Innova HTML differs — adapt the parser to match the actual site once a live page is captured.

- [ ] **Step 2: Write the failing test**

```python
# data/scripts/tests/test_innova.py
from pathlib import Path
from scrape_mfr.innova import parse_innova_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "innova_sample.html"

def test_parses_innova_destroyer_flight_numbers():
    html = FIXTURE.read_text()
    disc = parse_innova_disc_page(html)
    assert disc.brand == "Innova"
    assert disc.mold == "Destroyer"
    assert disc.speed == 12
    assert disc.glide == 5
    assert disc.turn == -1
    assert disc.fade == 3
    assert disc.disc_type == "Distance Driver"
    assert disc.stamp_url == "https://example.com/destroyer.jpg"
```

- [ ] **Step 3: Run, verify FAIL**

```bash
.venv/Scripts/python.exe -m pytest tests/test_innova.py -v
```

Expected: FAIL — module not found.

- [ ] **Step 4: Write `innova.py`**

```python
"""Innova Champion Discs flight-number scraper."""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_innova_disc_page(html: str) -> MfrDisc:
    """Parse a single Innova product page into a MfrDisc record."""
    soup = BeautifulSoup(html, "lxml")
    mold = (soup.find("h1") or soup.new_tag("h1")).get_text(strip=True)
    fn = soup.select_one(".flight-numbers")
    speed = _num(fn, ".speed")
    glide = _num(fn, ".glide")
    turn = _num(fn, ".turn")
    fade = _num(fn, ".fade")
    disc_type = (soup.select_one(".disc-type") or soup.new_tag("div")).get_text(strip=True) or None
    stamp_img = soup.select_one("img.primary-stamp")
    stamp_url = stamp_img["src"] if stamp_img and stamp_img.has_attr("src") else None
    return MfrDisc(
        brand="Innova",
        mold=mold,
        speed=speed,
        glide=glide,
        turn=turn,
        fade=fade,
        disc_type=disc_type,
        stamp_url=stamp_url,
    )


def _num(scope, selector: str) -> float:
    if scope is None:
        return 0.0
    el = scope.select_one(selector)
    if el is None:
        return 0.0
    try:
        return float(el.get_text(strip=True))
    except ValueError:
        return 0.0


class InnovaScraper(MfrScraper):
    brand_slug = "innova"
    brand_display = "Innova"

    INDEX_URL = "https://www.innovadiscs.com/disc-golf-discs/all-discs"

    def scrape(self) -> Iterable[MfrDisc]:
        index_html = self.get(self.INDEX_URL)
        soup = BeautifulSoup(index_html, "lxml")
        product_links = [
            a["href"] for a in soup.select("a.product-link[href]")
            if "/disc-golf-discs/" in a["href"]
        ]
        for href in product_links:
            url = href if href.startswith("http") else f"https://www.innovadiscs.com{href}"
            page = self.get(url)
            yield parse_innova_disc_page(page)


if __name__ == "__main__":
    InnovaScraper().run()
```

- [ ] **Step 5: Run test, verify PASS**

```bash
.venv/Scripts/python.exe -m pytest tests/test_innova.py -v
```

Expected: 1 test passing.

- [ ] **Step 6: Smoke-run on real Innova site**

```bash
.venv/Scripts/python.exe -m scrape_mfr.innova
```

Expected: writes `data/intermediate/mfr/innova.json` with ~50-100 discs.

If the real Innova HTML doesn't match the parser, **adapt the parser AND the fixture together** (keep test assertions intact). If Innova has lazy-loaded JS content the static scraper can't see, escalate — `requests-html` or `playwright` may be needed.

- [ ] **Step 7: Commit**

```bash
git add data/scripts/scrape_mfr/innova.py data/scripts/tests/test_innova.py data/scripts/tests/fixtures/innova_sample.html
git commit -m "feat(data): add Innova flight-number scraper"
```

---

## Task 6: Discraft Scraper

Same shape as Task 5. Discraft catalog at `https://www.discraft.com/collections/all-discs`. Each product page exposes flight #s.

**Files:**
- Create: `data/scripts/scrape_mfr/discraft.py`
- Create: `data/scripts/tests/fixtures/discraft_sample.html`
- Create: `data/scripts/tests/test_discraft.py`

Replicate the Innova pattern: capture fixture, write `parse_discraft_disc_page`, write `DiscraftScraper`, run live, commit.

- [ ] **Step 1: Fixture HTML**

Hand-curate. Discraft uses a different markup. Save as `data/scripts/tests/fixtures/discraft_sample.html`:

```html
<html>
<body>
<h1 class="product-title">Buzzz</h1>
<table class="flight-numbers">
  <tr><th>Speed</th><th>Glide</th><th>Turn</th><th>Fade</th></tr>
  <tr><td>5</td><td>4</td><td>-1</td><td>1</td></tr>
</table>
<div class="product-category">Mid-Range</div>
<img class="product-stamp" src="https://example.com/buzzz.jpg" />
</body>
</html>
```

- [ ] **Step 2: Test**

```python
# data/scripts/tests/test_discraft.py
from pathlib import Path
from scrape_mfr.discraft import parse_discraft_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "discraft_sample.html"

def test_parses_discraft_buzzz_flight_numbers():
    html = FIXTURE.read_text()
    disc = parse_discraft_disc_page(html)
    assert disc.brand == "Discraft"
    assert disc.mold == "Buzzz"
    assert disc.speed == 5
    assert disc.glide == 4
    assert disc.turn == -1
    assert disc.fade == 1
    assert disc.disc_type == "Mid-Range"
```

- [ ] **Step 3: Implementation**

```python
# data/scripts/scrape_mfr/discraft.py
from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_discraft_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    mold = (soup.select_one("h1.product-title") or soup.new_tag("h1")).get_text(strip=True)
    table = soup.select_one("table.flight-numbers")
    speed = glide = turn = fade = 0.0
    if table:
        rows = table.find_all("tr")
        if len(rows) >= 2:
            cells = [c.get_text(strip=True) for c in rows[1].find_all("td")]
            if len(cells) >= 4:
                speed = _f(cells[0])
                glide = _f(cells[1])
                turn = _f(cells[2])
                fade = _f(cells[3])
    disc_type = (soup.select_one(".product-category") or soup.new_tag("div")).get_text(strip=True) or None
    stamp_img = soup.select_one("img.product-stamp")
    stamp_url = stamp_img["src"] if stamp_img and stamp_img.has_attr("src") else None
    return MfrDisc(brand="Discraft", mold=mold, speed=speed, glide=glide, turn=turn, fade=fade, disc_type=disc_type, stamp_url=stamp_url)


def _f(raw: str) -> float:
    try:
        return float(raw)
    except ValueError:
        return 0.0


class DiscraftScraper(MfrScraper):
    brand_slug = "discraft"
    brand_display = "Discraft"
    INDEX_URL = "https://www.discraft.com/collections/all-discs"

    def scrape(self) -> Iterable[MfrDisc]:
        idx = self.get(self.INDEX_URL)
        soup = BeautifulSoup(idx, "lxml")
        for a in soup.select("a.product-card[href]"):
            href = a["href"]
            url = href if href.startswith("http") else f"https://www.discraft.com{href}"
            page = self.get(url)
            yield parse_discraft_disc_page(page)


if __name__ == "__main__":
    DiscraftScraper().run()
```

- [ ] **Step 4: Verify test pass + smoke run + commit**

Same flow as Task 5.

```bash
.venv/Scripts/python.exe -m pytest tests/test_discraft.py -v
.venv/Scripts/python.exe -m scrape_mfr.discraft
git add data/scripts/scrape_mfr/discraft.py data/scripts/tests/test_discraft.py data/scripts/tests/fixtures/discraft_sample.html
git commit -m "feat(data): add Discraft flight-number scraper"
```

---

## Task 7-12: Remaining Manufacturer Scrapers

Same pattern as Tasks 5 + 6. Six more brands:

| Task | Brand | Index URL (verify on first run) |
|---|---|---|
| 7 | MVP Disc Sports | `https://mvpdiscsports.com/collections/discs` |
| 8 | Dynamic Discs | `https://dynamicdiscs.com/collections/discs` |
| 9 | Latitude 64 | `https://latitude64.se/discs/` |
| 10 | Discmania | `https://discmania.com/collections/discs` |
| 11 | Prodigy | `https://www.prodigydisc.com/collections/discs` |
| 12 | Westside | `https://westsidediscgolf.com/collections/discs` |

**For each task:**

- [ ] Step 1: Capture a real disc-page HTML fixture from the brand site (1-disc sample).
- [ ] Step 2: Write `data/scripts/tests/test_<brand>.py` matching the Innova/Discraft test shape.
- [ ] Step 3: Run test → FAIL.
- [ ] Step 4: Write `data/scripts/scrape_mfr/<brand>.py` (parse function + `<Brand>Scraper(MfrScraper)`).
- [ ] Step 5: Run test → PASS.
- [ ] Step 6: Smoke-run live: `.venv/Scripts/python.exe -m scrape_mfr.<brand>` → writes `data/intermediate/mfr/<brand>.json`.
- [ ] Step 7: Commit individually with message `feat(data): add <Brand> flight-number scraper`.

**If any single brand site uses heavy JavaScript and `requests` + `BeautifulSoup` cannot extract flight numbers**, do NOT skip silently. Either:
- (a) Add a TODO scraper that emits an empty list + a clear warning, and rely on `manual_overrides.json` to fill that brand's flight numbers manually, OR
- (b) Add `playwright` to `requirements.txt` and switch that one scraper to JS-aware fetch.

Decision rests with the implementer based on observed site behavior. Document the choice in the commit message.

---

## Task 13: Enrich Script (`enrich.py`)

**Files:**
- Create: `data/scripts/enrich.py`
- Create: `data/scripts/manual_overrides.json` (initially `[]`)
- Create: `data/scripts/tests/test_enrich.py`

The enricher joins PDGA identity (canonical existence + approval) with manufacturer flight numbers, applies manual overrides, and emits the final `data/discs.json`.

- [ ] **Step 1: Write the failing test**

```python
# data/scripts/tests/test_enrich.py
from pathlib import Path
import json
import tempfile

from scripts.enrich import enrich

def _write(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data))

def test_enrich_joins_pdga_with_innova():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        pdga = [
            {"manufacturer": "Innova Champion Discs", "mold": "Destroyer", "disc_class": "Driver", "approved_date": "2008-04-21", "max_weight_g": 175.1, "diameter_cm": 21.10},
        ]
        innova = [
            {"brand": "Innova", "mold": "Destroyer", "speed": 12, "glide": 5, "turn": -1, "fade": 3, "disc_type": "Distance Driver", "stamp_url": None, "year_released": None, "aliases": []},
        ]
        _write(root / "intermediate" / "pdga_raw.json", pdga)
        _write(root / "intermediate" / "mfr" / "innova.json", innova)
        _write(root / "scripts" / "manual_overrides.json", [])

        out = enrich(root)

        assert len(out) == 1
        d = out[0]
        assert d["id"] == "innova-destroyer"
        assert d["brand"] == "Innova"
        assert d["mold"] == "Destroyer"
        assert d["speed"] == 12
        assert d["fade"] == 3
        assert d["pdgaApproved"] is True
        assert d["discType"] == "Driver"
        assert d["stability"] == "overstable"
        assert d["schemaVersion"] == 1

def test_manual_override_patches_disc():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        pdga = [
            {"manufacturer": "Innova", "mold": "Aviar", "disc_class": "Putt & Approach", "approved_date": "1985-01-01", "max_weight_g": 175.1, "diameter_cm": 21.10},
        ]
        innova = [
            {"brand": "Innova", "mold": "Aviar", "speed": 2, "glide": 3, "turn": 0, "fade": 1, "disc_type": "Putter", "stamp_url": None, "year_released": None, "aliases": []},
        ]
        overrides = [{"id": "innova-aviar", "yearReleased": 1985}]
        _write(root / "intermediate" / "pdga_raw.json", pdga)
        _write(root / "intermediate" / "mfr" / "innova.json", innova)
        _write(root / "scripts" / "manual_overrides.json", overrides)

        out = enrich(root)
        assert out[0]["yearReleased"] == 1985
```

- [ ] **Step 2: Run, verify FAIL**

```bash
.venv/Scripts/python.exe -m pytest tests/test_enrich.py -v
```

- [ ] **Step 3: Write `enrich.py`**

```python
"""Join PDGA + manufacturer scraper outputs + manual overrides → data/discs.json."""

from __future__ import annotations
import argparse
import json
import re
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]


# Map raw PDGA "disc_class" string → schema discType enum
DISC_CLASS_MAP: dict[str, str] = {
    "Putt & Approach": "Putter",
    "Mid-range": "Mid",
    "Mid-Range": "Mid",
    "Driver": "Driver",
    "Fairway": "Fairway",
    "Distance Driver": "Driver",
    "Approach": "Approach",
}


def slugify(brand: str, mold: str) -> str:
    text = f"{brand}-{mold}".lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text


def normalize_brand(raw: str) -> str:
    """Map PDGA's verbose names to short canonical brand."""
    mapping = {
        "Innova Champion Discs": "Innova",
        "Discraft, Inc.": "Discraft",
        "Discraft": "Discraft",
        "MVP Disc Sports": "MVP",
        "Dynamic Discs": "Dynamic Discs",
        "Latitude 64": "Latitude 64",
        "Discmania": "Discmania",
        "Prodigy Disc": "Prodigy",
        "Westside Discs": "Westside",
        "Westside": "Westside",
    }
    return mapping.get(raw.strip(), raw.strip())


def stability_from_turn_fade(turn: float, fade: float) -> str:
    """Heuristic: total = turn + fade. Negative-leaning = understable, neutral ≈ stable, positive-leaning = overstable."""
    total = turn + fade
    if total <= 0.5:
        return "understable"
    if total >= 3.0:
        return "overstable"
    return "stable"


def enrich(root: Path) -> list[dict]:
    pdga_path = root / "intermediate" / "pdga_raw.json"
    mfr_dir = root / "intermediate" / "mfr"
    overrides_path = root / "scripts" / "manual_overrides.json"

    pdga: list[dict] = json.loads(pdga_path.read_text()) if pdga_path.exists() else []
    overrides: list[dict] = json.loads(overrides_path.read_text()) if overrides_path.exists() else []

    # Index manufacturer rows by (brand, mold) lower-cased
    mfr_index: dict[tuple[str, str], dict] = {}
    if mfr_dir.exists():
        for f in mfr_dir.glob("*.json"):
            for row in json.loads(f.read_text()):
                key = (row["brand"].strip().lower(), row["mold"].strip().lower())
                mfr_index[key] = row

    out: list[dict] = []
    for p in pdga:
        brand = normalize_brand(p.get("manufacturer", ""))
        mold = p.get("mold", "").strip()
        if not brand or not mold:
            continue
        disc_id = slugify(brand, mold)
        key = (brand.lower(), mold.lower())
        m = mfr_index.get(key)
        if m is None:
            # PDGA has identity but we don't have flight numbers — skip for now.
            # Manual overrides can re-add via providing all fields in overrides.json.
            continue
        speed = m.get("speed", 0.0)
        glide = m.get("glide", 0.0)
        turn = m.get("turn", 0.0)
        fade = m.get("fade", 0.0)
        out.append({
            "id": disc_id,
            "brand": brand,
            "mold": mold,
            "speed": speed,
            "glide": glide,
            "turn": turn,
            "fade": fade,
            "discType": DISC_CLASS_MAP.get(p.get("disc_class", ""), "Driver"),
            "stability": stability_from_turn_fade(turn, fade),
            "pdgaApproved": True,
            "yearReleased": _parse_year(p.get("approved_date")),
            "primaryStampUrl": m.get("stamp_url"),
            "aliases": m.get("aliases", []),
            "schemaVersion": 1,
        })

    # Apply overrides
    out_by_id = {d["id"]: d for d in out}
    for ov in overrides:
        oid = ov.get("id")
        if not oid:
            continue
        if oid in out_by_id:
            out_by_id[oid].update({k: v for k, v in ov.items() if k != "id"})
        else:
            # New disc entirely from manual override — must provide all required fields.
            if all(k in ov for k in ("brand", "mold", "speed", "glide", "turn", "fade")):
                ov.setdefault("schemaVersion", 1)
                ov.setdefault("pdgaApproved", False)
                ov.setdefault("aliases", [])
                ov.setdefault("discType", "Driver")
                ov.setdefault("stability", stability_from_turn_fade(ov["turn"], ov["fade"]))
                out_by_id[oid] = ov

    final = sorted(out_by_id.values(), key=lambda d: (d["brand"].lower(), d["mold"].lower()))
    return final


def _parse_year(raw: str | None) -> int | None:
    if not raw:
        return None
    try:
        return int(raw[:4])
    except ValueError:
        return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT / "data")
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "discs.json")
    args = parser.parse_args()

    discs = enrich(args.root)
    args.output.write_text(json.dumps(discs, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {len(discs)} discs to {args.output}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run test, verify PASS**

```bash
.venv/Scripts/python.exe -m pytest tests/test_enrich.py -v
```

NOTE: import path in test is `from scripts.enrich import enrich` — but `scripts/` doesn't have `__init__.py` and the script lives at `data/scripts/enrich.py` directly. Adjust imports based on `pytest`'s discovery. Either:
- Add `data/scripts/__init__.py` and run pytest from `data/`
- Or change the test to `from enrich import enrich` and run pytest from `data/scripts/`

Pick option 2 (run from `data/scripts/`) for simplicity. Adjust the test import to `from enrich import enrich`.

- [ ] **Step 5: Empty manual_overrides.json + commit**

```bash
echo '[]' > data/scripts/manual_overrides.json
git add data/scripts/enrich.py data/scripts/manual_overrides.json data/scripts/tests/test_enrich.py
git commit -m "feat(data): add enrich pipeline joining PDGA + manufacturer data"
```

---

## Task 14: Validate Script (`validate.py`)

**Files:**
- Create: `data/scripts/validate.py`
- Create: `data/scripts/tests/test_validate.py`

Runs in CI on every PR touching `data/`. Must catch:
- JSON parse errors
- Schema violations
- Duplicate ids
- Implausible flight numbers (covered by schema bounds, but double-check)
- Unreachable `primaryStampUrl` (HEAD request, optional skip via `--no-net`)

- [ ] **Step 1: Write the failing test**

```python
# data/scripts/tests/test_validate.py
import json
from pathlib import Path
import tempfile
import pytest
from validate import validate

VALID_DISC = {
    "id": "innova-aviar",
    "brand": "Innova",
    "mold": "Aviar",
    "speed": 2, "glide": 3, "turn": 0, "fade": 1,
    "discType": "Putter",
    "stability": "stable",
    "pdgaApproved": True,
    "yearReleased": 1985,
    "primaryStampUrl": None,
    "aliases": [],
    "schemaVersion": 1
}

def _write_pair(tmp_path: Path, discs: list[dict], schema: dict) -> tuple[Path, Path]:
    discs_path = tmp_path / "discs.json"
    schema_path = tmp_path / "schema.json"
    discs_path.write_text(json.dumps(discs))
    schema_path.write_text(json.dumps(schema))
    return discs_path, schema_path

def test_validates_clean_input(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    discs_path, schema_path = _write_pair(tmp_path, [VALID_DISC], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert issues == []

def test_catches_duplicate_ids(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    discs_path, schema_path = _write_pair(tmp_path, [VALID_DISC, VALID_DISC], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("duplicate id" in i.lower() for i in issues)

def test_catches_schema_violation(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    bad = {**VALID_DISC, "speed": 99}  # exceeds maximum 15
    discs_path, schema_path = _write_pair(tmp_path, [bad], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("speed" in i.lower() for i in issues)
```

- [ ] **Step 2: Run, verify FAIL**

```bash
.venv/Scripts/python.exe -m pytest tests/test_validate.py -v
```

- [ ] **Step 3: Write `validate.py`**

```python
"""Validate data/discs.json against schema + integrity rules."""

from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path
from typing import Iterable

import jsonschema
import requests

ROOT = Path(__file__).resolve().parents[2]


def validate(discs_path: Path, schema_path: Path, check_urls: bool = True) -> list[str]:
    issues: list[str] = []

    try:
        discs = json.loads(discs_path.read_text())
    except json.JSONDecodeError as e:
        return [f"discs.json parse error: {e}"]
    try:
        schema = json.loads(schema_path.read_text())
    except json.JSONDecodeError as e:
        return [f"schema.json parse error: {e}"]

    # Schema validation
    validator = jsonschema.Draft202012Validator(schema)
    for err in validator.iter_errors(discs):
        path = "/".join(str(p) for p in err.absolute_path)
        issues.append(f"schema: {path}: {err.message}")

    # Duplicate id check
    seen: dict[str, int] = {}
    for i, d in enumerate(discs):
        oid = d.get("id")
        if not oid:
            continue
        if oid in seen:
            issues.append(f"duplicate id '{oid}' at indices {seen[oid]} and {i}")
        else:
            seen[oid] = i

    # URL reachability check (HEAD)
    if check_urls:
        urls = {d.get("primaryStampUrl") for d in discs if d.get("primaryStampUrl")}
        for url in urls:
            try:
                resp = requests.head(url, timeout=10, allow_redirects=True)
                if resp.status_code >= 400:
                    issues.append(f"stamp URL {url} returned {resp.status_code}")
            except requests.RequestException as e:
                issues.append(f"stamp URL {url} unreachable: {e}")

    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--discs", type=Path, default=ROOT / "data" / "discs.json")
    parser.add_argument("--schema", type=Path, default=ROOT / "data" / "schema.json")
    parser.add_argument("--no-net", action="store_true", help="skip URL HEAD checks")
    args = parser.parse_args()

    issues = validate(args.discs, args.schema, check_urls=not args.no_net)
    if not issues:
        print(f"✓ {args.discs.name} valid")
        return 0
    for i in issues:
        print(f"✗ {i}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: Run test, verify PASS**

```bash
.venv/Scripts/python.exe -m pytest tests/test_validate.py -v
```

- [ ] **Step 5: Commit**

```bash
git add data/scripts/validate.py data/scripts/tests/test_validate.py
git commit -m "feat(data): add validate script for schema + integrity checks"
```

---

## Task 15: Update `data-validate.yml` Workflow

**Files:**
- Modify: `.github/workflows/data-validate.yml`

Replace the placeholder validation w/ real `validate.py`. Also runs the pytest suite for the scripts.

- [ ] **Step 1: Rewrite workflow**

```yaml
name: Validate Disc Database

on:
  push:
    paths: ['data/**']
  pull_request:
    paths: ['data/**']

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with: { python-version: "3.13" }

      - name: Install deps
        working-directory: data/scripts
        run: |
          python -m pip install -U pip
          pip install -r requirements.txt

      - name: Run script tests
        working-directory: data/scripts
        run: pytest tests/ -v

      - name: Validate discs.json
        if: hashFiles('data/discs.json') != ''
        working-directory: data/scripts
        run: python validate.py --no-net
```

`--no-net` skips URL HEAD checks during CI to avoid flakiness from external sites. Local runs can omit it for the full check.

- [ ] **Step 2: Verify YAML lint**

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/data-validate.yml')); print('ok')"
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/data-validate.yml
git commit -m "ci: switch data-validate to real validate.py + pytest"
```

---

## Task 16: Generate Initial `data/discs.json`

**Files:**
- Create: `data/discs.json` (committed artifact)

Run the full pipeline locally and commit the output. **This is the moment of truth** — if any scrapers haven't been adapted to real-site HTML yet, expect rework.

- [ ] **Step 1: Run PDGA scrape (full)**

```bash
cd data/scripts
.venv/Scripts/python.exe scrape_pdga.py
```

Expected: ~1500-1700 rows in `data/intermediate/pdga_raw.json`. Takes ~2 minutes (~70 pages × 1 sec/page).

- [ ] **Step 2: Run all 8 manufacturer scrapers**

```bash
for brand in innova discraft mvp dynamic_discs latitude64 discmania prodigy westside; do
  .venv/Scripts/python.exe -m scrape_mfr.$brand
done
```

Expected: 8 files in `data/intermediate/mfr/` totaling ~600-1000 disc records (some brands have small catalogs).

- [ ] **Step 3: Run enrich**

```bash
.venv/Scripts/python.exe enrich.py
```

Expected: writes `data/discs.json` with the joined set. Likely 400-800 final discs (intersection of PDGA-approved + has-flight-numbers from a tracked brand).

- [ ] **Step 4: Run validate**

```bash
.venv/Scripts/python.exe validate.py --no-net
```

Expected: `✓ discs.json valid`. If issues print, fix the source data via `manual_overrides.json` or fix scrapers.

- [ ] **Step 5: Spot-check `data/discs.json`**

```bash
python -c "import json; data = json.load(open('../discs.json')); print(f'count: {len(data)}'); print(json.dumps(data[0], indent=2)); print(json.dumps(data[-1], indent=2))"
```

Verify a couple records look right (correct brand, plausible flight #s, valid id slug).

- [ ] **Step 6: Commit**

```bash
cd ../..  # back to repo root
git add data/discs.json
git commit -m "feat(data): initial disc database (~XXX discs across 8 brands)"
```

Replace `~XXX` with the actual count.

---

## Task 17: Regenerate Bundled `discs-baseline.json`

**Files:**
- Modify: `app/src/main/assets/discs-baseline.json`
- Create: `data/scripts/build_baseline.py` (helper)

The bundled APK fixture should be a representative subset (~30 popular discs covering all 5 disc types + top 5 brands) so the app launches with a believable catalog before the first remote sync. Programmatic to keep deterministic.

- [ ] **Step 1: Write `build_baseline.py`**

```python
"""Pick a representative ~30-disc subset of data/discs.json for APK bundling."""

from __future__ import annotations
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

POPULAR_IDS = [
    "innova-aviar",
    "innova-destroyer",
    "innova-leopard",
    "innova-roc",
    "innova-teebird",
    "innova-wraith",
    "innova-firebird",
    "discraft-buzzz",
    "discraft-zone",
    "discraft-luna",
    "discraft-undertaker",
    "discraft-thrasher",
    "mvp-tesla",
    "mvp-volt",
    "mvp-photon",
    "mvp-wave",
    "dynamic-discs-judge",
    "dynamic-discs-escape",
    "dynamic-discs-trespass",
    "latitude-64-pure",
    "latitude-64-river",
    "latitude-64-saint",
    "discmania-fd",
    "discmania-md3",
    "discmania-cd2",
    "prodigy-h3",
    "prodigy-d2",
    "prodigy-pa-3",
    "westside-harp",
    "westside-king",
]


def build_baseline(source: Path, output: Path) -> int:
    full = json.loads(source.read_text())
    by_id = {d["id"]: d for d in full}

    # Strict pick: only ids that exist in source
    picked = [by_id[i] for i in POPULAR_IDS if i in by_id]

    # Fallback: if list short (e.g. some scrapers underdelivered), pad with first N additional
    if len(picked) < 10:
        picked = full[:30]

    output.write_text(json.dumps(picked, indent=2, ensure_ascii=False) + "\n")
    return len(picked)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=ROOT / "data" / "discs.json")
    parser.add_argument("--output", type=Path, default=ROOT / "app" / "src" / "main" / "assets" / "discs-baseline.json")
    args = parser.parse_args()

    n = build_baseline(args.source, args.output)
    print(f"Wrote {n} baseline discs to {args.output}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run it**

```bash
cd data/scripts
.venv/Scripts/python.exe build_baseline.py
```

Expected: writes `app/src/main/assets/discs-baseline.json` with ~25-30 discs.

- [ ] **Step 3: Verify the app still loads correctly**

The existing `BaselineDiscLoader` parses the same shape — just now there's a single new field per record (`schemaVersion`). The `DiscEntity` class doesn't have that field, and the loader doesn't read it, so this is harmless.

Re-verify by re-running the instrumented test from Plan 1:

```bash
cd ../..
./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.data.db.seed.BaselineDiscLoaderTest
```

NOTE: the Plan 1 test asserted `assertEquals(10, db.discDao().count())`. With ~30 baseline discs it'll fail. Update the test to assert `count >= 10` or query the count and assert it matches the file size:

```kotlin
@Test fun loadsBaselineDiscs() = runBlocking {
    val loader = BaselineDiscLoader(ApplicationProvider.getApplicationContext(), db.discDao())
    loader.loadIfEmpty()
    val count = db.discDao().count()
    assertTrue(count >= 10, "expected at least 10 baseline discs, got $count")
}
```

- [ ] **Step 4: Commit**

```bash
git add data/scripts/build_baseline.py app/src/main/assets/discs-baseline.json app/src/androidTest/java/com/inknironapps/bagger/data/db/seed/BaselineDiscLoaderTest.kt
git commit -m "feat(data): regenerate baseline fixture from real discs.json subset"
```

---

## Task 18: Push, Verify CI Green, Tag Release

- [ ] **Step 1: Push**

```bash
git push origin claude/dev
```

- [ ] **Step 2: Watch auto-merge**

```bash
gh run watch $(gh run list --workflow=auto-merge.yml --limit 1 --json databaseId -q '.[0].databaseId') --exit-status
```

- [ ] **Step 3: Watch data-validate**

```bash
gh run watch $(gh run list --workflow=data-validate.yml --limit 1 --json databaseId -q '.[0].databaseId') --exit-status
```

Expected: pytest passes (~10 tests across all scripts), validate.py passes on `data/discs.json`.

- [ ] **Step 4: Manually dispatch release.yml on main**

```bash
gh workflow run release.yml --ref main
gh run watch $(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId') --exit-status
```

Should still pass (no app code changed in this plan, only `discs-baseline.json` size).

- [ ] **Step 5: Tag CHANGELOG**

Append to `CHANGELOG.md`:

```markdown
## [0.2.0] - 2026-05-XX

### Added

- Disc catalog data pipeline: Python scrapers for the PDGA approved-disc list and eight manufacturer flight-number sources, joined by `enrich.py` into a validated `data/discs.json`.
- JSON Schema (`data/schema.json`) describing the catalog format, validated in CI.
- Continuous integration runs the full Python test suite and `validate.py --no-net` on every PR touching `data/`.
- Bundled `discs-baseline.json` regenerated from the real catalog; the app now ships with ~30 representative discs out of the box.
```

- [ ] **Step 6: Final commit + push**

```bash
git add CHANGELOG.md
git commit -m "docs: tag 0.2.0 — Plan 2 disc DB pipeline complete"
git push origin claude/dev
```

---

## Verification Checklist (Plan 2 Done When All True)

- [ ] `data/scripts/.venv` set up with all deps from `requirements.txt`
- [ ] All 8 manufacturer scrapers + PDGA scraper run without error against live sites
- [ ] All scraper tests pass: `pytest data/scripts/tests/ -v`
- [ ] `data/discs.json` exists, parses, contains ≥400 discs across ≥6 brands
- [ ] `validate.py --no-net` reports no issues on committed data
- [ ] `app/src/main/assets/discs-baseline.json` regenerated and ≥25 discs
- [ ] App still launches and loads baseline fixture (instrumented test passes w/ updated assertion)
- [ ] CI: `data-validate.yml` runs pytest + validate.py and is green
- [ ] CI: `release.yml` still green
- [ ] CHANGELOG tagged v0.2.0

---

## Out of Scope (Tracked for Future)

- **Plan 3:** App-side remote sync (WorkManager + OkHttp + ETag handling, replace baseline-only loader, schema-version mismatch banner)
- **Plan 4:** Core CRUD UI (Shelf, Bags, Discover screens, manual disc add)
- **Plan 5:** Photo-ID pipeline (CameraX + ML Kit + JaroWinkler matcher)
- **Plan 6:** Disc lifecycle features (lost-disc map, wishlist, comparison)
- **Plan 7:** Stats + Settings (charts, backup, theme switcher, in-app update card)
- **Plan 8:** Release pipeline (keystore, AAB upload to Play Store)
- Stamp images: `data/stamps/*.webp` directory tracked in repo, lazy-loaded by Coil; deferred until Plan 4 needs them
- Auto-trigger of `release.yml` after auto-merge: switch to PAT in workflow secrets so the post-merge push to `main` fires release.yml without manual dispatch
- Real per-manufacturer scraper iteration: any brand site that requires JS rendering will need a `playwright`-based scraper; first occurrence triggers framework upgrade

---

## Risk Notes

1. **Manufacturer site changes are inevitable.** Scrapers will rot — when one breaks, fix the parser + fixture together. Test assertions stay strict.
2. **Robots.txt + ToS:** Each scraper should check robots.txt before first run (`https://<site>/robots.txt`). Default 0.5–1.0 sec sleep keeps us polite. If a manufacturer's robots.txt forbids the catalog page, fall back to `manual_overrides.json` for that brand.
3. **PDGA structure may differ from fixture.** Live first run may need parser tweaks. The fixture-based test catches regressions; live runs catch reality.
4. **Slug collisions:** Two brands with the same mold name (e.g. "Champion" exists from multiple manufacturers) collide via `slugify(brand, mold)` — but the brand prefix prevents this. Same brand with multi-version molds (e.g. Innova Star Destroyer vs Innova Champion Destroyer) maps to the same `innova-destroyer` id. That's intentional — flight numbers are per-mold, not per-plastic.
