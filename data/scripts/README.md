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

1. `python scrape_pdga.py`           → `data/intermediate/pdga_raw.json`
2. `python -m scrape_mfr.<brand>`    → `data/intermediate/mfr/<brand>.json` (run per brand)
3. `python enrich.py`                → `data/discs.json`
4. `python validate.py`              → exits 0 if valid, prints issues if not

## Tests

```bash
pytest tests/
```

## Manual Overrides

`manual_overrides.json` — array of `{id, ...patch}` objects merged on top of scraper output during enrich. Use for fixing typos, adding flight #s the scrapers miss, or marking discs PDGA-approved that the official list missed.
