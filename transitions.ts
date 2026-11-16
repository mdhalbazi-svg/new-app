export type Status = 'pending_verification'|'published'|'under_review'|'possible_match'|'found'|'reunited'|'closed'|'rejected';
export const TRANSITIONS: Record<Status, Status[]> = {
  pending_verification: ['published','rejected','under_review'],
  under_review: ['published','rejected','closed'],
  published: ['under_review','possible_match','found','closed'],
  possible_match: ['published','found','under_review','closed'],
  found: ['reunited','closed','under_review'],
  reunited: ['closed'],
  closed: [],
  rejected: ['pending_verification'], // appeal path
};
export const OWNER_ALLOWED: Status[] = ['found','reunited','closed'];
export function canTransition(from: Status, to: Status): boolean {
  return TRANSITIONS[from]?.includes(to) ?? false;
}
