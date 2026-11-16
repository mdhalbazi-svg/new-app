export interface Rec { category: string; title: string; description: string; district: string;
  lat?: number; lng?: number; eventAt: number; attrs?: Record<string,string>; }
export interface MatchResult { confidence: number; reasons: string[]; }
const tokens = (s: string) => new Set(s.toLowerCase().normalize('NFKC').split(/[^\p{L}\p{N}]+/u).filter(t => t.length > 1));
export function haversineKm(a: Rec, b: Rec): number | null {
  if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) return null;
  const R = 6371, rad = (d: number) => d * Math.PI / 180;
  const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLng/2)**2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
/** Suggestion only – never a confirmed identity. No facial recognition. */
export function scoreMatch(missing: Rec, found: Rec): MatchResult | null {
  if (missing.category !== found.category) return null;
  let score = 30; const reasons = ['Same category'];
  const a = tokens(missing.title + ' ' + missing.description), b = tokens(found.title + ' ' + found.description);
  const inter = [...a].filter(t => b.has(t)).length, uni = new Set([...a, ...b]).size || 1;
  const jac = inter / uni; score += Math.round(jac * 30);
  if (jac > 0.15) reasons.push('Similar description keywords');
  if (missing.district && missing.district === found.district) { score += 10; reasons.push('Same district'); }
  const km = haversineKm(missing, found);
  if (km != null) { if (km <= 5) { score += 15; reasons.push('Within 5 km'); } else if (km <= 25) { score += 8; reasons.push('Within 25 km'); } }
  const days = (found.eventAt - missing.eventAt) / 864e5;
  if (days >= -1 && days <= 14) { score += 10; reasons.push('Found shortly after reported missing'); }
  else if (days < -1) return null;
  for (const k of ['plate','color','make','model','imei_last4']) {
    const x = missing.attrs?.[k]?.toLowerCase(), y = found.attrs?.[k]?.toLowerCase();
    if (x && y && x === y) { score += 8; reasons.push(`Matching ${k}`); }
  }
  score = Math.min(score, 95);
  return score >= 55 ? { confidence: score, reasons } : null;
}
