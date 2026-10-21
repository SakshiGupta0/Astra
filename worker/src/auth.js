// Verifies Firebase Auth ID tokens (RS256) with Google's public keys, using WebCrypto (works on Cloudflare Workers and Node).
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let cache = { keys: null, until: 0 };

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = s.length % 4 ? 4 - (s.length % 4) : 0;
  const bin = atob(s + "=".repeat(pad));
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}
const json = (b) => JSON.parse(new TextDecoder().decode(b));

async function keys(fetchImpl, nowMs) {
  if (cache.keys && nowMs < cache.until) return cache.keys;
  const r = await fetchImpl(JWKS_URL);
  if (!r.ok) throw new Error(`JWKS ${r.status}`);
  const maxAge = Number((r.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] ?? 3600);
  cache = { keys: (await r.json()).keys, until: nowMs + maxAge * 1000 };
  return cache.keys;
}

export function resetKeyCache() { cache = { keys: null, until: 0 }; }

/** Returns the uid, or throws Error("unauthenticated: …"). */
export async function verifyIdToken(token, projectId, { fetchImpl = fetch, now = Date.now() } = {}) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) throw new Error("unauthenticated: malformed token");
  const header = json(b64urlToBytes(parts[0]));
  const claims = json(b64urlToBytes(parts[1]));
  if (header.alg !== "RS256") throw new Error("unauthenticated: bad algorithm");
  const jwk = (await keys(fetchImpl, now)).find(k => k.kid === header.kid);
  if (!jwk) throw new Error("unauthenticated: unknown key");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlToBytes(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  if (!ok) throw new Error("unauthenticated: bad signature");
  const sec = Math.floor(now / 1000);
  if (claims.aud !== projectId || claims.iss !== `https://securetoken.google.com/${projectId}`) throw new Error("unauthenticated: wrong project");
  if (!(claims.exp > sec - 30) || !(claims.iat <= sec + 300)) throw new Error("unauthenticated: expired");
  if (!claims.sub) throw new Error("unauthenticated: no user");
  return claims.sub;
}
