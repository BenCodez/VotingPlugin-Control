const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
const start = source.indexOf('VOTE_SITE_FIELDS.forEach(field => {\n  const control = document.querySelector(field.control);');
const end = source.indexOf("document.querySelector('#vote-site-reset')", start);
assert.ok(start >= 0 && end > start, 'production Vote Site edit handlers are present');

test('Vote Site edit handler rejects out-of-range integers without leaving an approved draft hidden behind the input', () => {
  const edits = [];
  const controls = new Map();
  for (const selector of ['#vote-site-priority', '#vote-site-amount', '#vote-sites-status', '#vote-site-ack']) {
    controls.set(selector, {value: '', checked: true, addEventListener(type, handler) { this.handler = handler; }});
  }
  const fields = [
    {path: 'Priority', label: 'Priority', control: '#vote-site-priority', type: 'integer'},
    {path: 'DisplayItem.Amount', label: 'Display amount', control: '#vote-site-amount', type: 'integer'}
  ];
  const context = {
    VOTE_SITE_FIELDS: fields,
    ControlVoteSites: require('../../main/resources/web/vote-sites-state.js'),
    document: {querySelector: selector => controls.get(selector)},
    voteSitesEditor: {model: {aggregateField: () => ({})}, edit: (field, value) => edits.push([field, value])},
    voteSiteFieldInput: field => { controls.get(field.control).value = '20'; },
    text: (element, value) => { element.textContent = value; }
  };
  vm.runInNewContext(source.slice(start, end), context);

  const priority = controls.get('#vote-site-priority');
  priority.value = '2147483648'; priority.handler();
  assert.deepEqual(edits, []);
  assert.equal(priority.value, '20');
  assert.match(controls.get('#vote-sites-status').textContent, /last staged value was retained/);
  priority.value = '2147483647'; priority.handler();
  assert.deepEqual(edits, [['Priority', 2147483647]]);

  const amount = controls.get('#vote-site-amount');
  amount.value = '65'; amount.handler();
  assert.equal(amount.value, '20');
  assert.equal(edits.length, 1);
  amount.value = '64'; amount.handler();
  assert.deepEqual(edits.at(-1), ['DisplayItem.Amount', 64]);
});
