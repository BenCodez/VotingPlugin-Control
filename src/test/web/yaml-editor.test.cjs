const test = require('node:test');
const assert = require('node:assert/strict');
const {metrics, lineWindow, matches, selectedMatch, statePresentation, MAX_MATCHES, MAX_RENDERED_LINES} = require('../../main/resources/web/yaml-editor.js');

test('reports stable line, column, line count, and character count', () => {
  assert.deepEqual(metrics('first\nsecond\n', 9), {line: 2, column: 4, lines: 3, characters: 13});
  assert.deepEqual(metrics('', 100), {line: 1, column: 1, lines: 1, characters: 0});
  assert.deepEqual(metrics('\nvalue', 0), {line: 1, column: 1, lines: 2, characters: 6});
});

test('find is case insensitive, non-overlapping, and bounded', () => {
  assert.deepEqual(matches('VoteSite votesite VOTE', 'vote'), [
    {start: 0, end: 4}, {start: 9, end: 13}, {start: 18, end: 22}
  ]);
  assert.equal(matches('a'.repeat(MAX_MATCHES + 50), 'a').length, MAX_MATCHES);
  assert.deepEqual(matches('value', ''), []);
});

test('line-number rendering stays bounded for maximum-size documents', () => {
  const first = lineWindow(250000, 0, 520, 20, 20);
  const middle = lineWindow(250000, 2500000, 520, 20, 20);
  assert.deepEqual(first, {start: 1, end: 34, offset: 20});
  assert.ok(middle.start > 100000);
  assert.ok(middle.end - middle.start + 1 <= MAX_RENDERED_LINES);
});

test('next and previous selection wrap around the document', () => {
  const items = matches('one two one', 'one');
  assert.equal(selectedMatch(items, 1, 1), 1);
  assert.equal(selectedMatch(items, 12, 1), 0);
  assert.equal(selectedMatch(items, 8, -1), 0);
  assert.equal(selectedMatch(items, 0, -1), 1);
});

test('distinguishes confirmed reads, snapshot drafts, edits, and loading', () => {
  assert.deepEqual(statePresentation({loaded: true}), {className: 'pill online', label: 'Confirmed read'});
  assert.deepEqual(statePresentation({loaded: true, draft: true}), {className: 'pill warning', label: 'Snapshot draft'});
  assert.deepEqual(statePresentation({loaded: true, draft: true, dirty: true}), {className: 'pill warning', label: 'Unsaved changes'});
  assert.deepEqual(statePresentation({loaded: true, dirty: true, busy: true}), {className: 'pill neutral', label: 'Loading'});
});
