const test = require('node:test');
const assert = require('node:assert/strict');
const {groups, render, withConfigurationChecks} = require('../../main/resources/web/network-doctor.js');
test('groups stable statuses by severity and preserves UNKNOWN', () => {
  const result = groups({checks: ['PASS', 'UNKNOWN', 'FAIL', 'INFO', 'WARNING'].map(status => ({status, category: 'Transport'}))});
  assert.deepEqual(result[0].checks.map(check => check.status), ['FAIL', 'WARNING', 'UNKNOWN', 'INFO', 'PASS']);
});
test('untrusted explanations remain text and PASS/INFO stay collapsed', () => {
  const create = tag => ({tag, children: [], append(...items) {this.children.push(...items);}, replaceChildren() {this.children=[];}});
  const container = create('div');
  render(container, {checks: [{category:'<img>', status:'FAIL', title:'<script>', explanation:'<img onerror=x>', affectedNodes:['<node>'], nextAction:'review'}, {category:'<img>', status:'PASS', title:'ok'}]}, {createElement:create});
  const section = container.children[1];
  assert.equal(section.children[0].textContent, '<img>');
  assert.equal(section.children[1].children[0].textContent, 'FAIL: <script>');
  const details = section.children[2];
  assert.equal(details.tag, 'details');
  assert.equal(details.open, undefined);
  assert.equal(details.children[1].children[0].textContent, 'PASS: ok');
});
test('configuration checks share the report cap and mark combined truncation', () => {
  const report = {checks: Array.from({length: 498}, (_, i) => ({status: 'PASS', title: `server-${i}`}))};
  const result = withConfigurationChecks(report, Array.from({length: 4}, (_, i) => ({path: `Config-${i}`, status: 'PASS'})));
  assert.equal(result.checks.length, 500);
  assert.equal(result.configurationChecks.length, 2);
  assert.equal(result.truncated, true);
});
test('a complete report remains untruncated at the combined boundary', () => {
  const result = withConfigurationChecks({checks: Array.from({length: 400}, () => ({status: 'PASS'}))},
    Array.from({length: 100}, (_, i) => ({path: `Config-${i}`, status: 'PASS'})));
  assert.equal(result.checks.length, 500);
  assert.equal(result.configurationChecks.length, 100);
  assert.equal(result.truncated, false);
});

test('browser-side report truncation is visible outside collapsed PASS checks', () => {
  const create = tag => ({tag, children: [], append(...items) {this.children.push(...items);}, replaceChildren() {this.children=[];}});
  const report = withConfigurationChecks({checks: Array.from({length: 498}, () => ({status: 'PASS', category: 'Runtime'}))},
    Array.from({length: 4}, (_, i) => ({path: `Config-${i}`, status: 'PASS'})));
  const container = create('div');
  render(container, report, {createElement: create});
  assert.equal(container.children[1].tag, 'p');
  assert.match(container.children[1].textContent, /^UNKNOWN: This report is incomplete/);
  assert.equal(JSON.parse(JSON.stringify(report)).truncated, true);
  assert.equal(report.checks.length, 500);
});
test('upstream truncation remains visible even when the browser adds no checks', () => {
  const create = tag => ({tag, children: [], append(...items) {this.children.push(...items);}, replaceChildren() {this.children=[];}});
  const report = withConfigurationChecks({truncated: true, checks: []}, []);
  const container = create('div');
  render(container, report, {createElement: create});
  assert.match(container.children[1].textContent, /^UNKNOWN:/);
});

test('affected-node overflow marks the rendered and downloaded report incomplete', () => {
  const nodes = Array.from({length: 100}, (_, i) => `backend-${i}`);
  const result = withConfigurationChecks({checks: []}, [{path: 'OnlineMode', status: 'UNKNOWN',
    nodeIds: nodes, unknownNodeIds: [nodes[0], 'outside-page']}]);
  assert.equal(result.checks[0].affectedNodes.length, 100);
  assert.equal(result.truncated, true);
  assert.equal(JSON.parse(JSON.stringify(result)).truncated, true);
  const create = tag => ({tag, children: [], append(...items) {this.children.push(...items);}, replaceChildren() {this.children=[];}});
  const container = create('div');
  render(container, result, {createElement: create});
  assert.match(container.children[1].textContent, /^UNKNOWN: This report is incomplete/);
});
test('exactly 100 distinct affected nodes stays complete despite repeated IDs', () => {
  const nodes = Array.from({length: 100}, (_, i) => `backend-${i}`);
  const result = withConfigurationChecks({checks: []}, [{path: 'OnlineMode', status: 'PASS',
    nodeIds: [...nodes, nodes[0]], unknownNodeIds: nodes}]);
  assert.equal(result.checks[0].affectedNodes.length, 100);
  assert.equal(result.truncated, false);
});

test('registry snapshot matching requires identical bounded tokens from both peers', () => {
  const {registrySnapshotMatches} = require('../../main/resources/web/network-doctor.js');
  const token = 'a'.repeat(64);
  assert.equal(registrySnapshotMatches(token, {registrySnapshot: token}), true);
  for (const expected of [undefined, '', 'a'.repeat(65), 'A'.repeat(64)])
    assert.equal(registrySnapshotMatches(expected, {registrySnapshot: expected}), false);
  assert.equal(registrySnapshotMatches(token, {}), false);
  assert.equal(registrySnapshotMatches(token, {registrySnapshot: 'b'.repeat(64)}), false);
});
