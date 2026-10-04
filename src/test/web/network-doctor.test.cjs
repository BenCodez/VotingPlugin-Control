const test = require('node:test');
const assert = require('node:assert/strict');
const {groups, render} = require('../../main/resources/web/network-doctor.js');
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
