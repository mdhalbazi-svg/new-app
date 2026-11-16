import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { canTransition, OWNER_ALLOWED, Status } from './transitions';
import { scoreMatch } from './matching';

initializeApp();
const db = getFirestore();
const CATEGORIES = ['child','person','motorcycle','car','phone','pet','document','other'];
const STAFF = ['super_admin','moderator'];

async function rateLimit(uid: string, key: string, max: number, windowMs: number) {
  const ref = db.doc(`rate_limits/${uid}_${key}`);
  await db.runTransaction(async tx => {
    const s = await tx.get(ref); const now = Date.now();
    const d = s.exists ? s.data()! : { count: 0, start: now };
    if (now - d.start > windowMs) { d.count = 0; d.start = now; }
    if (d.count >= max) throw new HttpsError('resource-exhausted', 'Too many requests');
    tx.set(ref, { count: d.count + 1, start: d.start });
  });
}

export const createReport = onCall({ enforceAppCheck: true }, async (req) => {
  const uid = req.auth?.uid; if (!uid) throw new HttpsError('unauthenticated', 'Login required');
  const d = req.data ?? {};
  const str = (v: unknown, min: number, max: number, f: string) => {
    if (typeof v !== 'string' || v.trim().length < min || v.length > max) throw new HttpsError('invalid-argument', `Invalid ${f}`);
    return v.trim();
  };
  if (!CATEGORIES.includes(d.category)) throw new HttpsError('invalid-argument', 'Invalid category');
  if (!['missing','found'].includes(d.type)) throw new HttpsError('invalid-argument', 'Invalid type');
  await rateLimit(uid, 'createReport', 5, 3600_000);
  const isChild = d.category === 'child';
  const ref = db.collection('reports').doc();
  const report = {
    id: ref.id, ownerId: uid, category: d.category, type: d.type,
    title: str(d.title, 3, 120, 'title'), description: str(d.description, 10, 2000, 'description'),
    province: str(d.province, 2, 60, 'province'), district: str(d.district, 2, 60, 'district'),
    city: str(d.city, 2, 60, 'city'), area: typeof d.area === 'string' ? d.area.slice(0, 80) : '',
    // Public location is rounded (~1 km) for sensitive categories.
    lat: typeof d.lat === 'number' ? Math.round(d.lat * 100) / 100 : null,
    lng: typeof d.lng === 'number' ? Math.round(d.lng * 100) / 100 : null,
    eventAt: Number(d.eventAt) || Date.now(), attrs: d.attrs ?? {},
    status: (isChild ? 'pending_verification' : 'pending_verification') as Status,
    createdAt: FieldValue.serverTimestamp(),
  };
  await ref.set(report);
  if (isChild) await db.doc(`child_emergency_reports/${ref.id}`).set({
    reportId: ref.id, ownerId: uid, guardianVerified: false, priority: 'emergency', createdAt: FieldValue.serverTimestamp() });
  await db.collection('audit_logs').add({ action: 'create_report', reportId: ref.id, actor: uid, at: FieldValue.serverTimestamp() });
  return { reportId: ref.id, status: report.status };
});

export const transitionStatus = onCall({ enforceAppCheck: true }, async (req) => {
  const uid = req.auth?.uid; if (!uid) throw new HttpsError('unauthenticated', 'Login required');
  const { reportId, to, reason } = req.data ?? {};
  if (typeof reportId !== 'string' || typeof reason !== 'string' || reason.length < 3) throw new HttpsError('invalid-argument', 'reportId and reason required');
  const isStaff = STAFF.includes(String(req.auth?.token.role));
  const ref = db.doc(`reports/${reportId}`);
  await db.runTransaction(async tx => {
    const s = await tx.get(ref); if (!s.exists) throw new HttpsError('not-found', 'No such report');
    const r = s.data()!; const from = r.status as Status;
    if (!isStaff && !(r.ownerId === uid && OWNER_ALLOWED.includes(to))) throw new HttpsError('permission-denied', 'Not allowed');
    if (!canTransition(from, to)) throw new HttpsError('failed-precondition', `Cannot go ${from} -> ${to}`);
    if (r.category === 'child' && to === 'published' && !isStaff) throw new HttpsError('permission-denied', 'Child reports need moderator approval');
    tx.update(ref, { status: to, updatedAt: FieldValue.serverTimestamp() });
    tx.set(db.collection('audit_logs').doc(), { action: 'status_change', reportId, from, to, actor: uid, reason, at: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

export const runMatching = onCall({ enforceAppCheck: true }, async (req) => {
  if (!STAFF.includes(String(req.auth?.token.role))) throw new HttpsError('permission-denied', 'Staff only');
  const { reportId } = req.data ?? {};
  const base = (await db.doc(`reports/${reportId}`).get()).data();
  if (!base) throw new HttpsError('not-found', 'No such report');
  const otherType = base.type === 'missing' ? 'found' : 'missing';
  const cands = await db.collection('reports').where('status','==','published').where('category','==',base.category).where('type','==',otherType).limit(200).get();
  let created = 0;
  for (const c of cands.docs) {
    const o = c.data(); const [m, f] = base.type === 'missing' ? [base, o] : [o, base];
    const res = scoreMatch(m as any, f as any); if (!res) continue;
    const id = `${m.id}_${f.id}`; // deterministic => no duplicate matches/alerts
    await db.doc(`possible_matches/${id}`).set({ missingId: m.id, foundId: f.id, missingOwnerId: m.ownerId, foundOwnerId: f.ownerId,
      confidence: res.confidence, reasons: res.reasons, status: 'suggested', createdAt: FieldValue.serverTimestamp() }, { merge: true });
    created++;
  }
  return { suggestions: created };
});
