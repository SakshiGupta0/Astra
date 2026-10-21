// Minimal Markdown to HTML for the privacy page: headings, paragraphs, bullet lists, **bold**. Escapes everything else.
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const inline = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");

export function mdToHtml(md) {
  const out = []; let list = false; let para = [];
  const flush = () => { if (para.length) { out.push(`<p>${inline(para.join(" "))}</p>`); para = []; } };
  const endList = () => { if (list) { out.push("</ul>"); list = false; } };
  for (const raw of md.split("\n")) {
    const line = raw.trim();
    if (!line) { flush(); endList(); continue; }
    const h = /^(#{1,3})\s+(.*)$/.exec(line);
    if (h) { flush(); endList(); out.push(`<h${h[1].length}>${inline(h[2])}</h${h[1].length}>`); continue; }
    if (line.startsWith("- ")) { flush(); if (!list) { out.push("<ul>"); list = true; } out.push(`<li>${inline(line.slice(2))}</li>`); continue; }
    endList(); para.push(line);
  }
  flush(); endList();
  return out.join("\n");
}

export function privacyPage(md, contact) {
  const body = mdToHtml(md.replaceAll("{{CONTACT}}", contact));
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ASTRA privacy notice</title><style>
:root{--bg:#080F24;--card:#181648;--text:#F5F3FF;--muted:#AAB0C5;--gold:#F7D794}
@media (prefers-color-scheme: light){:root{--bg:#F7F5FF;--card:#FFFFFF;--text:#1B1A3A;--muted:#55597A;--gold:#602D89}}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 16px 48px}
h1{font-family:Georgia,"Playfair Display",serif;font-weight:600;font-size:28px;margin:8px 0 4px}
h2{font-size:18px;margin:28px 0 8px;color:var(--gold)}
p,li{color:var(--text)} p:first-of-type{color:var(--muted);font-size:14px}
ul{padding-left:20px} li{margin:6px 0} strong{font-weight:600}
</style></head><body><main>${body}</main></body></html>`;
}
