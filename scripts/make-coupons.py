#!/usr/bin/env python3
"""
Batch coupon image generator.

Takes the coupon design template + a set of codes and renders one print-ready
coupon per code: the discount text on the left, a REAL QR code on the right
(encoding https://<base>/cupones?code=CODE so a phone camera scan auto-validates),
and the code printed underneath. Outputs a multi-page PDF (one coupon per page)
and optionally individual PNGs.

Codes can be:
  --generate N --token <COUPON_ADMIN_TOKEN>   (creates real coupons via the API)
  --codes-file path                            (one code per line)
  --codes "AAAA1111,BBBB2222"                  (comma separated)

Run with the project venv:
  scripts/venv/bin/python scripts/make-coupons.py --template scripts/coupon-template.png \
      --generate 20 --token PATITO-XXXX --discount-type FIXED --discount-value 10 \
      --label "Vale 10 euros" --valid-until 2026-09-01

Layout is expressed as fractions of the template size; tune the --*-cx/-cy/-font
flags (or the DEFAULTS below) to match your specific design.
"""
import argparse
from functools import lru_cache
import json
import os
import sys
import urllib.request

from PIL import Image, ImageDraw, ImageFont
import qrcode

URL_BASE_DEFAULT = "https://patitoland-terrassa.es"

# Placement as fractions of the template width/height. Tuned for the 10€
# Terrassa/Patitoland design in coupon-template.png.
DEFAULTS = {
    "discount_cx": 0.680, "discount_cy": 0.340, "discount_font": 0.235,
    "qr_cx": 0.885, "qr_cy": 0.182, "qr_size": 0.150,
    "code_cx": 0.885, "code_cy": 0.282, "code_font": 0.022,
    "date_cx": 0.300, "date_cy": 0.895, "date_font": 0.030,
}

# The 10€ design needs no default mask. A custom mask can still be supplied.
DEFAULT_MASK = "none"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

DISPLAY_FONT_CANDIDATES = [
    os.path.join(SCRIPT_DIR, "fonts", "fredoka-700.ttf"),
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/Library/Fonts/Arial.ttf",
]

CODE_FONT_CANDIDATES = [
    os.path.join(SCRIPT_DIR, "fonts", "ibm-plex-mono-600.ttf"),
    "/System/Library/Fonts/Supplemental/Courier New Bold.ttf",
    "/System/Library/Fonts/Monaco.ttf",
] + DISPLAY_FONT_CANDIDATES


@lru_cache(maxsize=128)
def load_truetype(path, size):
    return ImageFont.truetype(path, size)


def load_font(size, candidates=DISPLAY_FONT_CANDIDATES):
    for path in candidates:
        if os.path.exists(path):
            try:
                return load_truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def load_code_font(size):
    return load_font(size, CODE_FONT_CANDIDATES)


def draw_centered(draw, cx, cy, text, font, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((cx - w / 2, cy - h / 2 - bbox[1]), text, font=font, fill=fill)


def fitted_font(draw, text, preferred_size, max_width, minimum_size=12):
    size = preferred_size
    while size > minimum_size:
        font = load_font(size)
        bbox = draw.textbbox((0, 0), text, font=font)
        if bbox[2] - bbox[0] <= max_width:
            return font
        size -= 2
    return load_font(minimum_size)


def make_coupon(template, code, discount_text, discount_label, date_text, url_base,
                cfg, mask=None, recipient_text=None):
    img = template.convert("RGB").copy()
    W, H = img.size
    draw = ImageDraw.Draw(img)

    # Optional white rounded rectangle to cover a decorative element (e.g. the fake barcode).
    if mask:
        x0, y0, x1, y1 = (mask[0] * W, mask[1] * H, mask[2] * W, mask[3] * H)
        r = (y1 - y0) * 0.18
        draw.rounded_rectangle([x0, y0, x1, y1], radius=r, fill="white")

    # The approved artwork prints 10€ directly. For percentages or other fixed
    # values, cover only the central amount block and redraw it dynamically.
    if discount_text:
        draw.rounded_rectangle(
            [W * 0.45, H * 0.195, W * 0.92, H * 0.585],
            radius=H * 0.035, fill=(255, 244, 223))
        draw_centered(draw, W * cfg["discount_cx"], H * cfg["discount_cy"],
                      discount_text, load_font(int(H * cfg["discount_font"])), (242, 107, 0))
        draw.rounded_rectangle(
            [W * 0.485, H * 0.455, W * 0.875, H * 0.560],
            radius=H * 0.018, fill=(8, 123, 167), outline=(230, 247, 255), width=max(2, int(H * 0.003)))
        draw_centered(draw, W * cfg["discount_cx"], H * 0.508,
                      discount_label, load_font(int(H * 0.060)), (255, 255, 255))

    if recipient_text:
        recipient = recipient_text if recipient_text.startswith("@") else f"@{recipient_text}"
        recipient = f"PARA: {recipient}"
        font = fitted_font(draw, recipient, int(H * 0.027), W * 0.43)
        bbox = draw.textbbox((0, 0), recipient, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        cx, cy = W * cfg["discount_cx"], H * 0.615
        padx, pady = W * 0.015, H * 0.010
        draw.rounded_rectangle(
            [cx - tw / 2 - padx, cy - th / 2 - pady,
             cx + tw / 2 + padx, cy + th / 2 + pady],
            radius=H * 0.018, fill=(255, 250, 240), outline=(242, 194, 126),
            width=max(2, int(H * 0.002)))
        draw_centered(draw, cx, cy, recipient, font, (0, 61, 95))

    # Valid-until date written into the "VÁLIDO HASTA" field, on a white pill that
    # covers the dotted "__/__/__" placeholder.
    if date_text:
        f = load_font(int(H * cfg["date_font"]))
        bbox = draw.textbbox((0, 0), date_text, font=f)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        cx, cy = W * cfg["date_cx"], H * cfg["date_cy"]
        padx, pady = tw * 0.22, th * 0.55
        draw.rounded_rectangle(
            [cx - tw / 2 - padx, cy - th / 2 - pady, cx + tw / 2 + padx, cy + th / 2 + pady],
            radius=th * 0.5, fill="white")
        draw_centered(draw, cx, cy, date_text, f, (38, 38, 38))

    # Real QR (paste a white margin behind it so it stays scannable over artwork).
    qr = qrcode.make(f"{url_base}/cupones?code={code}").convert("RGB")
    size = int(H * cfg["qr_size"])
    qr = qr.resize((size, size))
    pad = max(6, size // 16)
    backing = Image.new("RGB", (size + 2 * pad, size + 2 * pad), "white")
    backing.paste(qr, (pad, pad))
    bx, by = backing.size
    img.paste(backing, (int(W * cfg["qr_cx"] - bx / 2), int(H * cfg["qr_cy"] - by / 2)))

    draw_centered(draw, W * cfg["code_cx"], H * cfg["code_cy"],
                  code, load_code_font(int(H * cfg["code_font"])), (0, 61, 95))
    return img


def fetch_generated(url_base, token, count, dtype, dval, valid_until, label):
    body = json.dumps({
        "count": count, "discountType": dtype, "discountValue": dval,
        "validUntil": valid_until, "label": label,
    }).encode()
    req = urllib.request.Request(
        f"{url_base}/api/coupons/generate", data=body, method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Coupon-Token": token,
            # Cloudflare's WAF 403s the default python-urllib User-Agent.
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        },
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)["codes"]


def main():
    ap = argparse.ArgumentParser(description="Generate printable coupon images.")
    ap.add_argument("--template", required=True, help="Path to the coupon design image")
    ap.add_argument("--output", default="scripts/coupons-out/coupons.pdf")
    ap.add_argument("--url-base", default=URL_BASE_DEFAULT)
    ap.add_argument("--discount-text", default="", help="Override the discount text (e.g. '10%%'). Leave empty if the design already prints the amount.")
    ap.add_argument("--discount-label", help="Override the blue ribbon text (e.g. 'GRATIS').")
    ap.add_argument("--date-text", default="", help="Text for the 'válido hasta' field (defaults to --valid-until formatted dd/mm/yyyy)")
    ap.add_argument("--png-dir", help="Also save individual PNGs here")
    ap.add_argument("--mask", default=DEFAULT_MASK,
                    help="White cover rectangle 'x0,y0,x1,y1' (default hides the template's fake barcode). Use 'none' to disable.")
    # codes source
    ap.add_argument("--generate", type=int, help="Create N real coupons via the API")
    ap.add_argument("--codes-file", help="File with one code per line")
    ap.add_argument("--codes", help="Comma-separated codes")
    ap.add_argument("--recipients-file", help="Optional file with one recipient/account per coupon")
    ap.add_argument("--token", help="COUPON_ADMIN_TOKEN (required with --generate)")
    ap.add_argument("--discount-type", default="PERCENT", choices=["PERCENT", "FIXED"])
    ap.add_argument("--discount-value", type=float, default=10)
    ap.add_argument("--valid-until", help="YYYY-MM-DD (optional)")
    ap.add_argument("--label")
    for k, v in DEFAULTS.items():
        ap.add_argument(f"--{k.replace('_', '-')}", type=float, default=v)
    args = ap.parse_args()

    cfg = {k: getattr(args, k) for k in DEFAULTS}

    if args.generate:
        if not args.token:
            sys.exit("--token is required with --generate")
        codes = fetch_generated(args.url_base, args.token, args.generate,
                                args.discount_type, args.discount_value, args.valid_until, args.label)
    elif args.codes_file:
        codes = [ln.strip() for ln in open(args.codes_file) if ln.strip()]
    elif args.codes:
        codes = [c.strip() for c in args.codes.split(",") if c.strip()]
    else:
        sys.exit("Provide one of: --generate N (+--token), --codes-file, --codes")

    # Keep the original artwork untouched only for its native 10€ value. Other
    # fixed amounts and all percentage discounts are drawn dynamically.
    def display_number(value):
        return str(int(value)) if value.is_integer() else str(round(value, 2))

    if args.discount_text:
        discount_text = args.discount_text
    elif args.discount_type == "PERCENT":
        discount_text = f"{display_number(args.discount_value)}%"
    elif args.discount_value != 10:
        discount_text = f"{display_number(args.discount_value)}€"
    else:
        discount_text = ""
    discount_label = args.discount_label or ("DESCUENTO" if args.discount_type == "PERCENT" else "EUROS")

    date_text = args.date_text
    if not date_text and args.valid_until:
        parts = args.valid_until.split("-")  # YYYY-MM-DD -> DD/MM/YYYY
        if len(parts) == 3:
            date_text = f"{parts[2]}/{parts[1]}/{parts[0]}"

    mask = None
    if args.mask and args.mask.lower() != "none":
        mask = [float(x) for x in args.mask.split(",")]

    template = Image.open(args.template)
    recipients = [None] * len(codes)
    if args.recipients_file:
        with open(args.recipients_file, encoding="utf-8") as recipient_file:
            recipients = [line.strip() for line in recipient_file if line.strip()]
        if len(recipients) != len(codes):
            sys.exit("--recipients-file must contain exactly one non-empty line per coupon")

    pages = [
        make_coupon(template, code, discount_text, discount_label, date_text,
                    args.url_base, cfg, mask, recipient)
        for code, recipient in zip(codes, recipients)
    ]

    os.makedirs(os.path.dirname(args.output) or ".", exist_ok=True)
    pages[0].save(args.output, save_all=True, append_images=pages[1:], resolution=150.0)
    print(f"Wrote {len(pages)} coupons -> {args.output}")

    if args.png_dir:
        os.makedirs(args.png_dir, exist_ok=True)
        for code, im in zip(codes, pages):
            im.save(os.path.join(args.png_dir, f"{code}.png"))
        print(f"Individual PNGs -> {args.png_dir}")


if __name__ == "__main__":
    main()
