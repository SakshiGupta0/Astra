// Reads the signed-in user's own Firestore documents through the REST API with their ID token,
// so the database's privacy rules apply exactly as they do in the app.
export function decodeValue(v) {
  if (v == null) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("nullValue" in v) return null;
  if ("timestampValue" in v) return v.timestampValue;
  if ("arrayValue" in v) return (v.arrayValue.values ?? []).map(decodeValue);
  if ("mapValue" in v) return decodeFields(v.mapValue.fields ?? {});
  return null;
}
export function decodeFields(fields) {
  const out = {};
  for (const [k, v] of Object.entries(fields ?? {})) out[k] = decodeValue(v);
  return out;
}

export async function getUserDoc(projectId, idToken, path, fetchImpl = fetch) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${path}`;
  const r = await fetchImpl(url, { headers: { authorization: `Bearer ${idToken}` } });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`Firestore ${r.status}`);
  const doc = await r.json();
  return decodeFields(doc.fields);
}
