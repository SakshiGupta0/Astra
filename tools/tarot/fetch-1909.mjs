// Fetches the 1909 Pamela Colman Smith tarot scans (public domain) from Wikimedia Commons, with each file's
// source and licence metadata, for the classic deck. Run in CI (the build sandbox has no general internet).
import { mkdirSync, writeFileSync } from "node:fs";
const MAJ = ["Fool","Magician","High_Priestess","Empress","Emperor","Hierophant","Lovers","Chariot","Strength","Hermit","Wheel_of_Fortune","Justice","Hanged_Man","Death","Temperance","Devil","Tower","Star","Moon","Sun","Judgement","World"];
const files = [
  ...MAJ.map((n, i) => [`maj${String(i).padStart(2, "0")}`, `RWS_Tarot_${String(i).padStart(2, "0")}_${n}.jpg`]),
  ...[["wands", "Wands"], ["cups", "Cups"], ["swords", "Swords"], ["pentacles", "Pents"]].flatMap(([id, n]) =>
    Array.from({ length: 14 }, (_, k) => [`${id}${String(k + 1).padStart(2, "0")}`, `${n}${String(k + 1).padStart(2, "0")}.jpg`])),
];
const UA = { "user-agent": "ASTRA-app-build/1.0 (github.com/SakshiGupta0/Astra)" };
mkdirSync("out/cards", { recursive: true });
import { existsSync, readFileSync } from "node:fs";
// Keep good files from an earlier run (out/ is pre-filled from the tarot-1909 branch by the workflow).
const isJpeg = (p) => existsSync(p) && (() => { const b = readFileSync(p); return b[0] === 0xff && b[1] === 0xd8 && b.length > 20000; })();
const manifest = [];
for (let i = 0; i < files.length; i += 40) {
  const batch = files.slice(i, i + 40);
  const url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=960&titles=" +
    encodeURIComponent(batch.map(([, f]) => "File:" + f).join("|"));
  const j = await (await fetch(url, { headers: UA })).json();
  const pages = Object.values(j.query.pages);
  for (const [id, f] of batch) {
    const p = pages.find(x => x.title.replace(/ /g, "_") === "File:" + f);
    const ii = p?.imageinfo?.[0];
    if (!ii) { manifest.push({ id, file: f, missing: true }); continue; }
    let ok = isJpeg(`out/cards/${id}.jpg`);
    for (let attempt = 0; attempt < 6 && !ok; attempt++) {
      const img = await fetch(ii.thumburl || ii.url, { headers: UA });
      if (img.status === 429) { const wait = Number(img.headers.get("retry-after") || 30); await img.arrayBuffer(); await new Promise(r => setTimeout(r, Math.min(wait, 120) * 1000)); continue; }
      const buf = Buffer.from(await img.arrayBuffer());
      if (img.ok && /image\/jpeg/.test(img.headers.get("content-type") || "") && buf[0] === 0xff && buf[1] === 0xd8) { writeFileSync(`out/cards/${id}.jpg`, buf); ok = true; }
      else await new Promise(r => setTimeout(r, 3000 * (attempt + 1)));
    }
    if (!ok) { manifest.push({ id, file: f, missing: true, reason: "download failed" }); continue; }
    const m = ii.extmetadata || {};
    manifest.push({ id, file: f, width: ii.width, height: ii.height, page: ii.descriptionurl,
      license: m.LicenseShortName?.value, artist: (m.Artist?.value || "").replace(/<[^>]+>/g, ""), date: (m.DateTimeOriginal?.value || "").replace(/<[^>]+>/g, ""),
      credit: (m.Credit?.value || "").replace(/<[^>]+>/g, "").slice(0, 200) });
    await new Promise(r => setTimeout(r, 4000));
  }
}
writeFileSync("out/manifest.json", JSON.stringify(manifest, null, 1));
console.log(`got ${manifest.filter(m => !m.missing).length}/78; missing: ${manifest.filter(m => m.missing).map(m => m.file).join(", ") || "none"}`);
