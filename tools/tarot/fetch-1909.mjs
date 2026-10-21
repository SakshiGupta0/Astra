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
const manifest = [];
for (let i = 0; i < files.length; i += 40) {
  const batch = files.slice(i, i + 40);
  const url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=1000&titles=" +
    encodeURIComponent(batch.map(([, f]) => "File:" + f).join("|"));
  const j = await (await fetch(url, { headers: UA })).json();
  const pages = Object.values(j.query.pages);
  for (const [id, f] of batch) {
    const p = pages.find(x => x.title.replace(/ /g, "_") === "File:" + f);
    const ii = p?.imageinfo?.[0];
    if (!ii) { manifest.push({ id, file: f, missing: true }); continue; }
    const img = await fetch(ii.thumburl || ii.url, { headers: UA });
    writeFileSync(`out/cards/${id}.jpg`, Buffer.from(await img.arrayBuffer()));
    const m = ii.extmetadata || {};
    manifest.push({ id, file: f, width: ii.width, height: ii.height, page: ii.descriptionurl,
      license: m.LicenseShortName?.value, artist: (m.Artist?.value || "").replace(/<[^>]+>/g, ""), date: (m.DateTimeOriginal?.value || "").replace(/<[^>]+>/g, ""),
      credit: (m.Credit?.value || "").replace(/<[^>]+>/g, "").slice(0, 200) });
    await new Promise(r => setTimeout(r, 300));
  }
}
writeFileSync("out/manifest.json", JSON.stringify(manifest, null, 1));
console.log(`got ${manifest.filter(m => !m.missing).length}/78; missing: ${manifest.filter(m => m.missing).map(m => m.file).join(", ") || "none"}`);
