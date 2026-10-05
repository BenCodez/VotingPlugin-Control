const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
const styles = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.css'), 'utf8');

function declaration(name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notEqual(start, -1);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let index = open; index < source.length; index++) {
    if (source[index] === '{') depth++;
    if (source[index] === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`Could not extract ${name}`);
}

test('date rendering accepts API epoch seconds and ISO instants', () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(`${declaration('formatDateTime')}\n${declaration('formatEpoch')}`, context);
  assert.match(context.formatEpoch(1770000000), /2026/);
  assert.match(context.formatDateTime('2026-01-21T00:00:00Z'), /2026/);
  assert.equal(context.formatEpoch(0), 'Unknown');
  assert.equal(context.formatDateTime(-1), 'Unknown');
  assert.equal(context.formatDateTime(true), 'Unknown');
  assert.equal(context.formatDateTime('not-a-date'), 'Unknown');
});

test('deployment status wraps long staging messages', () => {
  assert.match(styles, /#deployment-status \{[^}]*overflow-wrap: anywhere;[^}]*white-space: pre-wrap;/s);
});
