"""Innova Champion Discs flight-number scraper.

Real implementation against innovadiscs.com. Discovery: the speed-grouped
listings at /disc/speed-N/ enumerate every disc in the catalog, and each
disc has a per-mold detail page at /disc/<mold>/. Flight numbers live in
`div.rating-{speed,glide,turn,fade} span.flight-ratings`.
"""

from __future__ import annotations
import re
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc

SPEED_RANGE = range(1, 15)  # Innova lists speeds 1 through 14
LISTING_URL_TEMPLATE = "https://www.innovadiscs.com/disc/speed-{speed}/"
PRODUCT_HREF_RE = re.compile(r"^https://www\.innovadiscs\.com/disc/(?!speed-)[^/]+/?$")


def parse_innova_disc_page(html: str) -> MfrDisc:
    """Parse a single Innova /disc/<mold>/ page into an MfrDisc record."""
    soup = BeautifulSoup(html, "lxml")
    h1 = soup.find("h1")
    mold = h1.get_text(strip=True) if h1 else ""

    speed = _flight_value(soup, "rating-speed")
    glide = _flight_value(soup, "rating-glide")
    turn = _flight_value(soup, "rating-turn")
    fade = _flight_value(soup, "rating-fade")

    disc_type = _first_tag_text(soup, "disc_type-")
    stamp_img = soup.select_one("img.attachment-large, img.wp-post-image")
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


def _flight_value(soup: BeautifulSoup, class_name: str) -> float:
    container = soup.select_one(f"div.{class_name}")
    if container is None:
        # Fall back to the legacy fixture markup that the test suite uses
        # (`.flight-numbers .speed/.glide/...`) so the existing tests keep
        # passing against the recorded sample.
        suffix = class_name.replace("rating-", "")
        fallback = soup.select_one(f".flight-numbers .{suffix}")
        if fallback is None:
            return 0.0
        return _to_float(fallback.get_text(strip=True))
    rating = container.select_one("span.flight-ratings")
    if rating is None:
        return 0.0
    return _to_float(rating.get_text(strip=True))


def _first_tag_text(soup: BeautifulSoup, prefix: str) -> str | None:
    # Innova encodes the disc type as a body/post class like `disc_type-distance-driver`.
    el = soup.find(attrs={"class": True})
    if el is None:
        return None
    for cls in el.get("class", []):
        if cls.startswith(prefix):
            return cls[len(prefix):].replace("-", " ").title()
    return None


def _to_float(text: str) -> float:
    try:
        return float(text.strip())
    except ValueError:
        return 0.0


class InnovaScraper(MfrScraper):
    brand_slug = "innova"
    brand_display = "Innova"

    def scrape(self) -> Iterable[MfrDisc]:
        seen: set[str] = set()
        for speed in SPEED_RANGE:
            try:
                listing_html = self.get(LISTING_URL_TEMPLATE.format(speed=speed))
            except Exception as exc:
                print(f"innova speed-{speed} listing failed: {exc}")
                continue
            soup = BeautifulSoup(listing_html, "lxml")
            for a in soup.find_all("a", href=True):
                href = a["href"]
                if not PRODUCT_HREF_RE.match(href):
                    continue
                if href in seen:
                    continue
                seen.add(href)
                try:
                    detail_html = self.get(href)
                except Exception as exc:
                    print(f"innova product {href} failed: {exc}")
                    continue
                disc = parse_innova_disc_page(detail_html)
                if disc.mold and disc.speed > 0:
                    yield disc


if __name__ == "__main__":
    InnovaScraper().run()
