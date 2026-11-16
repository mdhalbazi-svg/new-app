import { describe, it, expect } from 'vitest';
import { scoreMatch } from './matching';
import { canTransition } from './transitions';
const base = { category:'motorcycle', title:'Honda CG125 red', description:'red honda 125 stolen near market', district:'Lahore', lat:31.52, lng:74.35, eventAt:1e12, attrs:{color:'red'} };
describe('matching', () => {
  it('matches similar records', () => expect(scoreMatch(base, {...base, title:'Red Honda 125 found', eventAt:1e12+864e5})?.confidence).toBeGreaterThan(55));
  it('rejects different category', () => expect(scoreMatch(base, {...base, category:'phone'})).toBeNull());
  it('rejects found-before-missing', () => expect(scoreMatch(base, {...base, eventAt:1e12-5*864e5})).toBeNull());
});
describe('transitions', () => {
  it('allows valid', () => expect(canTransition('published','found')).toBe(true));
  it('blocks invalid', () => expect(canTransition('closed','published')).toBe(false));
});
