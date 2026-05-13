"""Shared Shopify-storefront scraper.

Six of the eight tracked brands run their product catalog on Shopify
(latitude64.com, westsidediscs.com, dynamicdiscs.com, discmaniagolf.com,
prodigydisc.com, mvpdisc.com, factorystore.discraft.com). All expose
a public products.json endpoint we can paginate for handles, then
fetch each product's HTML page and regex-extract flight numbers.

Subclasses provide:
- brand_slug + brand_display
- shopify_origin (e.g. "https://latitude64.com")
- extract_mold(title) → canonical mold name (de-duped across plastic variants)

The base class handles pagination, per-product HTML fetch, flight regex,
deduplication, and writing the intermediate JSON.
"""

from __future__ import annotations
import json
import re
import time
from typing import Iterable

import requests

from scrape_mfr.base import MfrDisc, MfrScraper, SLEEP_SECONDS


FLIGHT_RX = {
    "speed": re.compile(r"Speed[\s:=\-]+(-?\d+\.?\d*)", re.I),
    "glide": re.compile(r"Glide[\s:=\-]+(-?\d+\.?\d*)", re.I),
    "turn": re.compile(r"Turn[\s:=\-]+(-?\d+\.?\d*)", re.I),
    "fade": re.compile(r"Fade[\s:=\-]+(-?\d+\.?\d*)", re.I),
}


def extract_flight_from_html(html: str) -> dict | None:
    """Return {speed, glide, turn, fade} or None if any are missing."""
    out = {}
    for key, rx in FLIGHT_RX.items():
        matches = rx.findall(html)
        if not matches:
            return None
        try:
            out[key] = float(matches[0])
        except ValueError:
            return None
    # Sanity check: speed 1-15, glide 1-7, turn -5..1, fade 0..6
    if not (1 <= out["speed"] <= 15):
        return None
    if not (1 <= out["glide"] <= 7):
        return None
    if not (-5 <= out["turn"] <= 1):
        return None
    if not (0 <= out["fade"] <= 6):
        return None
    return out


class ShopifyDiscScraper(MfrScraper):
    """Base for any disc-manufacturer Shopify storefront."""

    shopify_origin: str = ""             # e.g. "https://latitude64.com"
    page_size: int = 250                 # Shopify max per page

    # Mold extraction defaults: token-level filter of plastic + color words.
    # Stored lowercase. Any title token matching one of these is dropped before
    # joining what remains as the mold name.
    plastic_tokens = frozenset({
        # Lat64 / Westside / Dynamic (trilogy)
        "opto", "opto-x", "gold", "midnight", "royal", "grand", "biogold",
        "lucid", "fuzion", "biofuzion", "prime", "supreme", "moonshine",
        "active", "classic", "evolution", "premium",
        "zero", "hard", "soft", "medium", "burst", "ice", "ace-line",
        "tournament", "tournament-x",
        # Innova
        "champion", "star", "gstar", "g-star", "metal", "flake",
        "kc", "pro", "dx", "blizzard", "xt",
        # Discraft
        "esp", "z", "elite", "elite-z", "elite-x", "big-z", "ti", "titanium",
        "fly-mat", "fly", "mat", "j-pro", "soft",
        # Discmania
        "s-line", "p-line", "c-line", "g-line", "d-line", "active-line",
        "exo", "hard-exo", "soft-exo", "neo",
        # MVP / Axiom / Streamline (Trilogy of MVP)
        "neutron", "fission", "proton", "plasma", "eclipse", "electron",
        "cosmic", "fission-glow", "proton-soft",
        # Prodigy
        "300", "400", "500", "750", "x-out", "ace-line",
        "ace", "line", "atlas",
        # Generic
        "clear", "luster", "halo", "deluxe", "first-run", "ridge",
        "decodye", "deco",
    })

    color_tokens = frozenset({
        "white", "black", "red", "blue", "green", "yellow", "orange",
        "pink", "purple", "violet", "teal", "cyan", "gold", "silver",
        "swirl", "speckle", "speckled", "metallic", "matte", "gloss",
        "glow",
    })

    # Tokens to always drop regardless of position.
    junk_tokens = frozenset({
        "the", "a", "an", "of", "with", "for", "and", "or", "le", "la",
        "edition", "ltd", "limited", "tour", "series", "team", "ts",
        "collab", "collaboration", "championship", "challenge",
        "disc", "discs", "golf",
        "custom", "custom-disc",
    })

    # Non-disc product types to skip entirely (patches, bags, apparel, etc.)
    non_disc_keywords = (
        "patch", "bag", "backpack", "towel", "marker", "mini",
        "shirt", "hat", "cap", "sticker", "sock", "glove",
        "card", "gift", "voucher", "pin", "keychain", "magnet",
        "hoodie", "jersey", "umbrella", "stool", "cart",
        "cleaner", "wax", "grip enhancer", "chalk",
        "custom-disc", "dyemax", "decodye",
    )

    def is_disc_product(self, title: str) -> bool:
        low = title.lower()
        return not any(kw in low for kw in self.non_disc_keywords)

    def extract_mold(self, title: str) -> str | None:
        """Token-level filter: keep only tokens that look like mold names."""
        # Drop bracketed/parenthetical content
        cleaned = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", title)
        # If title contains " - ", prefer the right half (collab edition titles
        # like "Disc Golf Valley - Deadly Fuse" — actual mold usually on right)
        if " - " in cleaned:
            cleaned = cleaned.rsplit(" - ", 1)[1]
        # Tokenize by whitespace and slashes
        tokens = re.split(r"[\s/]+", cleaned.strip())
        keep: list[str] = []
        plastic = self.plastic_tokens
        color = self.color_tokens
        junk = self.junk_tokens
        brand_words = {self.brand_display.lower(), self.brand_display.lower().replace(" ", "")}
        for tok in tokens:
            t = tok.strip(",.;:°!?\"'")
            if not t:
                continue
            low = t.lower()
            if low in plastic or low in color or low in junk:
                continue
            if low in brand_words:
                continue
            # Pure numbers like year stamps or model numbers — keep (e.g. "FD3", "H3")
            keep.append(t)
        mold = " ".join(keep).strip()
        return mold or None

    def list_handles(self) -> list[tuple[str, str]]:
        """Return list of (title, handle) for every product."""
        handles: list[tuple[str, str]] = []
        page = 1
        while True:
            url = f"{self.shopify_origin}/products.json?limit={self.page_size}&page={page}"
            try:
                resp = self.session.get(url, timeout=30)
                resp.raise_for_status()
            except Exception:
                break
            try:
                data = resp.json()
            except ValueError:
                break
            products = data.get("products", [])
            if not products:
                break
            for p in products:
                title = (p.get("title") or "").strip()
                handle = (p.get("handle") or "").strip()
                if title and handle:
                    handles.append((title, handle))
            time.sleep(SLEEP_SECONDS)
            page += 1
            if page > 50:                 # safety stop, ~12500 products
                break
        return handles

    def fetch_product_html(self, handle: str) -> str | None:
        try:
            resp = self.session.get(f"{self.shopify_origin}/products/{handle}", timeout=30)
            resp.raise_for_status()
            return resp.text
        except Exception:
            return None

    def scrape(self) -> Iterable[MfrDisc]:
        seen: dict[str, MfrDisc] = {}
        for title, handle in self.list_handles():
            if not self.is_disc_product(title):
                continue
            mold = self.extract_mold(title)
            if not mold or mold in seen:
                # skip duplicates by mold name (plastic/color variants)
                continue
            html = self.fetch_product_html(handle)
            time.sleep(SLEEP_SECONDS)
            if html is None:
                continue
            flight = extract_flight_from_html(html)
            if flight is None:
                continue
            disc = MfrDisc(
                brand=self.brand_display,
                mold=mold,
                speed=flight["speed"],
                glide=flight["glide"],
                turn=flight["turn"],
                fade=flight["fade"],
                disc_type=None,
                stamp_url=None,
                year_released=None,
                aliases=[]
            )
            seen[mold] = disc
            yield disc
