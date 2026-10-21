// Gemini generateContent call with timeouts and retries for busy / rate-limited responses.
export async function callGemini({ apiKey, model, system, user, maxOutputTokens = 2048, thinking = "low", fetchImpl = fetch }) {
  let r, j;
  let useThinking = !!thinking;
  for (let attempt = 0; attempt < 3; attempt++) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 45_000);
    try {
      r = await fetchImpl(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: "POST", signal: ctrl.signal, headers: { "content-type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: system }] }, contents: [{ role: "user", parts: [{ text: user }] }],
          generationConfig: { temperature: 0.4, maxOutputTokens, ...(useThinking ? { thinkingConfig: { thinkingLevel: thinking } } : {}) },
        }),
      });
      j = await r.json().catch(() => ({}));
    } finally { clearTimeout(timer); }
    if (r.status === 400 && useThinking && /thinking/i.test(JSON.stringify(j))) { useThinking = false; continue; }
    if (r.status !== 429 && r.status !== 503) break;
    await new Promise(res => setTimeout(res, 2000 * (attempt + 1)));
  }
  if (!r.ok) throw new Error(`Gemini ${r.status}: ${JSON.stringify(j).slice(0, 200)}`);
  const cand = j.candidates?.[0];
  const text = (cand?.content?.parts ?? []).filter(p => !p.thought).map(p => p.text ?? "").join("").trim();
  return {
    text,
    truncated: cand?.finishReason === "MAX_TOKENS",
    blocked: cand?.finishReason === "SAFETY" || !!j.promptFeedback?.blockReason,
    inTok: j.usageMetadata?.promptTokenCount ?? 0,
    outTok: (j.usageMetadata?.candidatesTokenCount ?? 0) + (j.usageMetadata?.thoughtsTokenCount ?? 0),
  };
}
