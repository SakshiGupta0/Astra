"""Renders the Google Play store graphics into docs/play/ with headless Chromium.

  python3 tools/play/make_assets.py [shots_phone_dir] [shots_tablet_dir]

- icon-512.png           : app icon (512x512, full bleed; Play applies the rounded mask)
- feature-1024x500.png   : feature graphic
- phone-NN.png           : phone screenshots 1080x1920 with a caption (from CI emulator shots, synthetic data)
- tablet-NN.png          : 10-inch tablet screenshots (2560x1600)
"""
import base64, pathlib, sys
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "play"; OUT.mkdir(parents=True, exist_ok=True)
FONT = ROOT / "app/src/main/res/font"
TAROT = ROOT / "app/src/main/res/drawable-nodpi"

def data(p): return "data:image/%s;base64,%s" % (p.suffix[1:].replace("jpg", "jpeg"), base64.b64encode(p.read_bytes()).decode())

CSS = f"""
@font-face{{font-family:Cinzel;src:url('{data(FONT/'cinzel_semibold.ttf').replace('image/ttf','font/ttf')}')}}
@font-face{{font-family:Playfair;src:url('{data(FONT/'playfair_semibold.ttf').replace('image/ttf','font/ttf')}')}}
@font-face{{font-family:Inter;src:url('{data(FONT/'inter_medium.ttf').replace('image/ttf','font/ttf')}')}}
*{{margin:0;padding:0;box-sizing:border-box}}
body{{background:#080F24;color:#F5F3FF;font-family:Inter,sans-serif;overflow:hidden}}
.sky{{position:absolute;inset:0;background:radial-gradient(ellipse at 70% 20%,#2a1f6b 0%,#14103d 40%,#080F24 80%)}}
.gold{{color:#F7D794}}
"""

def stars(w, h, n, seed=7):
    import random
    r = random.Random(seed); out = []
    for _ in range(n):
        x, y, s = r.random()*w, r.random()*h, r.choice([1, 1, 1.5, 2, 2.5])
        a = r.uniform(0.3, 0.95)
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{s}" fill="#F5F3FF" opacity="{a:.2f}"/>')
    return "".join(out)

MARK = """<svg viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
<path d="M54,25a29,29 0 1,0 0.01,0z" stroke="#F7D794" stroke-width="1.2" fill="none"/>
<path d="M60,36A20.6,20.6 0 1,0 60,72A22.5,22.5 0 0,1 60,36z" fill="#F7D794"/>
<path d="M70,38L71.6,44.4L78,46L71.6,47.6L70,54L68.4,47.6L62,46L68.4,44.4z" fill="#F7D794"/>
<path d="M77,31L77.6,32.9L79.5,33.5L77.6,34.1L77,36L76.4,34.1L74.5,33.5L76.4,32.9z" fill="#A78BFA"/></svg>"""

def mandala(size, op=0.25):
    petals = "".join(f'<ellipse cx="0" cy="-{size*0.33:.0f}" rx="{size*0.045:.0f}" ry="{size*0.11:.0f}" transform="rotate({i*22.5})" fill="none" stroke="#F7D794" stroke-width="1.2"/>' for i in range(16))
    return f"""<svg width="{size}" height="{size}" viewBox="-{size/2} -{size/2} {size} {size}" style="opacity:{op}">
<circle r="{size*0.48:.0f}" fill="none" stroke="#F7D794" stroke-width="1.2"/><circle r="{size*0.46:.0f}" fill="none" stroke="#A78BFA" stroke-width="0.8"/>
<circle r="{size*0.2:.0f}" fill="none" stroke="#F7D794" stroke-width="1"/>{petals}
<polygon points="0,-{size*0.17:.0f} {size*0.147:.0f},{size*0.085:.0f} -{size*0.147:.0f},{size*0.085:.0f}" fill="none" stroke="#F7D794"/>
<polygon points="0,{size*0.17:.0f} {size*0.147:.0f},-{size*0.085:.0f} -{size*0.147:.0f},-{size*0.085:.0f}" fill="none" stroke="#F7D794"/></svg>"""

def icon():
    return f"""<html><head><style>{CSS}</style></head><body style="width:512px;height:512px">
<div class="sky" style="background:linear-gradient(135deg,#080F24 50%,#120f38 50%)"></div>
<svg width="512" height="512" style="position:absolute;inset:0">{stars(512,512,40,3)}</svg>
<div style="position:absolute;inset:0">{MARK.replace('<svg ','<svg width="512" height="512" ')}</div></body></html>"""

def feature():
    cards = ["tarot_maj10", "tarot_maj02", "tarot_maj18"]  # Wheel of Fortune, The High Priestess, The Moon
    imgs = []
    for i, c in enumerate(cards):
        f = TAROT / (c + ".webp")
        rot = [-12, 0, 12][i]; x = [600, 720, 840][i]; y = [80, 52, 80][i]
        imgs.append(f'<div style="position:absolute;left:{x}px;top:{y}px;width:150px;height:261px;transform:rotate({rot}deg);border-radius:10px;padding:6px;background:linear-gradient(160deg,#F7D794,#B8893A);box-shadow:0 18px 40px rgba(0,0,0,.55),0 0 30px rgba(247,215,148,.25)"><img src="{data(f)}" style="width:100%;height:100%;object-fit:cover;border-radius:6px"></div>')
    return f"""<html><head><style>{CSS}</style></head><body style="width:1024px;height:500px">
<div class="sky"></div><svg width="1024" height="500" style="position:absolute;inset:0">{stars(1024,500,140)}</svg>
<div style="position:absolute;left:560px;top:-90px">{mandala(560, 0.22)}</div>
{''.join(imgs)}
<div style="position:absolute;left:64px;top:118px;width:520px">
  <div style="display:flex;align-items:center;gap:18px"><div style="width:84px;height:84px">{MARK.replace('<svg ','<svg width="84" height="84" ')}</div>
  <div class="gold" style="font-family:Cinzel;font-size:68px;letter-spacing:10px">ASTRA</div></div>
  <div style="font-family:Playfair;font-size:38px;line-height:1.2;margin-top:22px">Vedic astrology &amp; tarot,<br>read from your own chart</div>
  <div style="font-size:19px;color:#C9C3EE;margin-top:18px;letter-spacing:.5px">Graha Tracker · daily cards · Cross-Readings</div>
</div></body></html>"""

PHONE = [
    ("01_welcome.png", "Tell your story once"),
    ("101_daily_card_up.png", "A tarot card and a guidance card, every day"),
    ("10_tracker_now.png", "See where every planet is for you, today"),
    ("12_tracker_timeline.png", "Supportive and demanding periods, months ahead"),
    ("83_tarot_meanings.png", "The classic 1909 tarot deck. You choose the cards"),
    ("85_cross_reading.png", "Your chart and your cards, read side by side"),
    ("41_ask_answer_story.png", "Ask anything. Answers grounded in your chart"),
    ("110_oracle_yes_no.png", "Yes/No, Guidance, Vibe and Nakshatra oracles"),
]
TABLET = ["15_today.png", "10_tracker_now.png", "83_tarot_meanings.png", "13_chart.png"]

def cropped(shot):
    """Drops the emulator's grey navigation strip at the bottom of each capture."""
    from PIL import Image
    import io
    im = Image.open(shot).convert("RGB"); w, h = im.size
    y = h
    while y > h * 0.8 and all(abs(c - 232) < 12 for c in im.getpixel((w // 2, y - 1))): y -= 1
    buf = io.BytesIO(); im.crop((0, 0, w, y)).save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(), y / w

def phone_page(shot, caption):
    src, ratio = cropped(shot)
    hgt = int(680 * ratio)
    return f"""<html><head><style>{CSS}</style></head><body style="width:1080px;height:1920px">
<div class="sky"></div><svg width="1080" height="1920" style="position:absolute;inset:0">{stars(1080,1920,160,11)}</svg>
<div style="position:absolute;left:250px;top:-160px">{mandala(580,0.18)}</div>
<div style="position:absolute;top:96px;left:70px;right:70px;text-align:center">
 <div class="gold" style="font-family:Cinzel;font-size:30px;letter-spacing:8px">ASTRA</div>
 <div style="font-family:Playfair;font-size:62px;line-height:1.18;margin-top:22px">{caption}</div></div>
<div style="position:absolute;left:200px;top:400px;width:680px;height:{hgt}px;border-radius:40px;overflow:hidden;
 border:3px solid rgba(247,215,148,.75);box-shadow:0 30px 80px rgba(0,0,0,.6),0 0 60px rgba(167,139,250,.25)">
 <img src="{src}" style="width:100%;display:block"></div></body></html>"""

def main():
    ph = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    tb = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else None
    with sync_playwright() as p:
        b = p.chromium.launch()
        def shoot(html, w, h, name):
            pg = b.new_page(viewport={"width": w, "height": h}); pg.set_content(html); pg.wait_for_timeout(300)
            pg.screenshot(path=str(OUT / name), clip={"x": 0, "y": 0, "width": w, "height": h}); pg.close()
        shoot(icon(), 512, 512, "icon-512.png")
        shoot(feature(), 1024, 500, "feature-1024x500.png")
        if ph:
            for i, (f, cap) in enumerate(PHONE, 1):
                if (ph / f).exists(): shoot(phone_page(ph / f, cap), 1080, 1920, f"phone-{i:02d}.png")
        if tb:
            from PIL import Image
            for i, f in enumerate(TABLET, 1):
                if (tb / f).exists():
                    im = Image.open(tb / f).convert("RGB")  # 2560x1800 landscape, within Play's 2:1 limit
                    im.save(OUT / f"tablet-{i:02d}.png")
        b.close()

if __name__ == "__main__":
    main()
