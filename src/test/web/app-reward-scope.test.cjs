const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');

function section(startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start);
  assert.ok(start >= 0 && end > start, `production section exists: ${startMarker}`);
  return source.slice(start, end);
}

function fixture() {
  const calls = [];
  const listeners = new Map();
  const scope = {fileName: 'VoteSites.yml', path: 'VoteSites.Beta.Rewards'};
  const editor = {
    selected: {fileName: 'VoteSites.yml', rewardPath: 'VoteSites.Alpha.Rewards'},
    edit: {operation: 'APPEND_LIST_ENTRY', field: 'Commands', value: 'say staged'},
    select(fileName, rewardPath) {
      calls.push(['select', fileName, rewardPath]);
      if (this.selected.fileName !== fileName || this.selected.rewardPath !== rewardPath) {
        this.selected = {fileName, rewardPath}; this.edit = null;
      }
    },
    scopes: () => [scope],
    record: () => ({scopes: [{path: scope.path, status: 'PRESENT'}]}),
    read: async () => { calls.push(['read']); }
  };
  let confirm = false;
  const element = () => ({children: [], setAttribute() {}, append(...children) { this.children.push(...children); },
    addEventListener(name, listener) { this[name] = listener; }});
  const list = element();
  const context = {
    rewardsEditor: editor,
    voteSitesEditor: {model: {selectedSiteKey: 'Beta'}},
    window: {confirm: message => { calls.push(['confirm', message]); return confirm; }},
    document: {querySelector: selector => ({addEventListener: (name, listener) => listeners.set(selector + ':' + name, listener)}),
      createElement: element},
    search: '', targets: [{nodeId: 'a'}], selected: editor.selected, list,
    text: (node, value) => { node.textContent = value; return node; },
    renderRewards: () => { calls.push(['render']); },
    openWorkspace: tab => { calls.push(['open', tab]); }
  };
  vm.createContext(context);
  vm.runInContext(section('function chooseRewardScope(', '\nfunction rewardsContext()'), context);
  vm.runInContext(section("document.querySelector('#vote-site-edit-rewards').addEventListener", '\nfunction chooseRewardScope('), context);
  vm.runInContext(section('  const scopes = rewardsEditor.scopes()', '  if (!scopes.length)'), context);
  return {context, calls, list, editor, scope, listeners, allow: value => { confirm = value; }};
}

test('scope list keeps a staged Rewards edit when discard is declined and changes after consent', () => {
  const f = fixture();
  f.list.children[0].click();
  assert.equal(f.editor.selected.rewardPath, 'VoteSites.Alpha.Rewards');
  assert.equal(f.editor.edit.value, 'say staged');
  assert.equal(f.calls.filter(([name]) => name === 'select' || name === 'read').length, 0);
  f.allow(true);
  f.list.children[0].click();
  assert.equal(f.editor.selected.rewardPath, f.scope.path);
  assert.equal(f.editor.edit, null);
  assert.equal(f.calls.filter(([name]) => name === 'select').length, 1);
  assert.equal(f.calls.filter(([name]) => name === 'read').length, 1);
});

test('Vote Sites Edit rewards keeps the current page and draft when discard is declined', () => {
  const f = fixture();
  const click = f.listeners.get('#vote-site-edit-rewards:click');
  click();
  assert.equal(f.editor.selected.rewardPath, 'VoteSites.Alpha.Rewards');
  assert.equal(f.editor.edit.value, 'say staged');
  assert.equal(f.calls.some(([name]) => name === 'open'), false);
  f.allow(true);
  click();
  assert.equal(f.editor.selected.rewardPath, 'VoteSites.Beta.Rewards');
  assert.equal(f.editor.edit, null);
  assert.deepEqual(f.calls.filter(([name]) => name === 'open'), [['open', 'rewards']]);
});

test('selecting the current reward scope leaves its draft without a discard prompt', () => {
  const f = fixture();
  assert.equal(vm.runInContext("chooseRewardScope('VoteSites.yml', 'VoteSites.Alpha.Rewards')", f.context), true);
  assert.equal(f.editor.edit.value, 'say staged');
  assert.equal(f.calls.some(([name]) => name === 'confirm'), false);
});

test('Rewards staging rejects oversized scalar and single-command values before creating an edit', () => {
  const listener = new Map(); const values = new Map(); const status = {};
  const element = selector => values.get(selector) || (selector === '#rewards-status' ? status : {
    value: '', checked: false, addEventListener: (name, handler) => listener.set(`${selector}:${name}`, handler)
  });
  const editor = {selected: {rewardPath: 'VoteSites.Alpha.Rewards'}, setEdit: (...args) => { editor.edit = args; }};
  const context = {
    rewardsEditor: editor,
    window: {confirm: () => true},
    document: {querySelector: element},
    text: (node, value) => { node.textContent = value; return node; }
  };
  const stage = section("document.querySelector('#rewards-stage').addEventListener", "document.querySelector('#rewards-preview')");
  vm.createContext(context); vm.runInContext(stage, context);
  const click = listener.get('#rewards-stage:click');
  for (const [operation, field] of [
    ['APPEND_LIST_ENTRY', 'Commands'], ['REMOVE_LIST_ENTRY', 'Commands'], ['CREATE_REWARD', 'Commands'],
    ['SET_SCALAR', 'Messages.Player'], ['SET_SCALAR', 'Messages.Broadcast']
  ]) {
    values.set('#rewards-operation', {value: operation});
    values.set('#rewards-field', {value: field});
    values.set('#rewards-value', {value: 'x'.repeat(501)});
    values.set('#rewards-ack', {checked: true});
    editor.edit = null; click();
    assert.equal(editor.edit, null, `${operation}/${field} must not be staged`);
    assert.equal(status.textContent, 'Use at most 500 characters.');
  }
});
