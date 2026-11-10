// Verifies Firebase Auth ID tokens (RS256) with Google's public keys, using WebCrypto (works on Cloudflare Workers and Node).
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APPCHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
let caches = {};

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = s.length % 4 ? 4 - (s.length % 4) : 0;
  const bin = atob(s + "=".repeat(pad));
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}
const json = (b) => JSON.parse(new TextDecoder().decode(b));

async function keys(fetchImpl, nowMs, url = JWKS_URL) {
  const c = caches[url];
  if (c && nowMs < c.until) return c.keys;
  const r = await fetchImpl(url);
  if (!r.ok) throw new Error(`JWKS ${r.status}`);
  const maxAge = Math.min(Number((r.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] ?? 3600), 6 * 3600);
  caches[url] = { keys: (await r.json()).keys, until: nowMs + maxAge * 1000 };
  return caches[url].keys;
}

export function resetKeyCache() { caches = {}; }

async function verifyRs256(token, fetchImpl, now, url) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) throw new Error("malformed token");
  const header = json(b64urlToBytes(parts[0]));
  const claims = json(b64urlToBytes(parts[1]));
  if (header.alg !== "RS256") throw new Error("bad algorithm");
  const jwk = (await keys(fetchImpl, now, url)).find(k => k.kid === header.kid);
  if (!jwk) throw new Error("unknown key");
  const key = await crypto.subtle.importKey("jwk", { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: "RS256", ext: true }, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlToBytes(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  if (!ok) throw new Error("bad signature");
  return { header, claims };
}

/**
 * Firebase App Check token (sent by the Play Store build in X-Firebase-AppCheck, backed by Play Integrity).
 * Returns "ok", "missing" or "invalid". Never throws.
 */
export async function verifyAppCheck(token, projectNumber, { fetchImpl = fetch, now = Date.now() } = {}) {
  if (!token) return "missing";
  try {
    const { header, claims } = await verifyRs256(token, fetchImpl, now, APPCHECK_JWKS_URL);
    if (header.typ !== "JWT") return "invalid";
    if (claims.iss !== `https://firebaseappcheck.googleapis.com/${projectNumber}`) return "invalid";
    const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (!aud.includes(`projects/${projectNumber}`)) return "invalid";
    if (!(claims.exp > Math.floor(now / 1000))) return "invalid";
    return "ok";
  } catch { return "invalid"; }
}

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
