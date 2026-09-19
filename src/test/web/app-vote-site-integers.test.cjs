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

test('Vote Site edit handler rejects invalid strings and preserves the staged value', () => {
  const edits = [];
  const controls = new Map();
  for (const selector of ['#vote-site-name', '#vote-site-service', '#vote-site-url', '#vote-site-delay',
    '#vote-site-material', '#vote-sites-status', '#vote-site-ack']) {
    controls.set(selector, {value: '', checked: true, addEventListener(type, handler) { this.handler = handler; }});
  }
  const fields = [
    {path: 'Name', label: 'Display name', control: '#vote-site-name', type: 'string'},
    {path: 'ServiceSite', label: 'Service site', control: '#vote-site-service', type: 'string'},
    {path: 'VoteURL', label: 'Vote URL', control: '#vote-site-url', type: 'string'},
    {path: 'VoteDelay', label: 'Vote delay', control: '#vote-site-delay', type: 'string'},
    {path: 'DisplayItem.Material', label: 'Display material', control: '#vote-site-material', type: 'string'}
  ];
  const context = {
    VOTE_SITE_FIELDS: fields,
    ControlVoteSites: require('../../main/resources/web/vote-sites-state.js'),
    document: {querySelector: selector => controls.get(selector)},
    voteSitesEditor: {model: {aggregateField: () => ({})}, edit: (field, value) => edits.push([field, value])},
    voteSiteFieldInput: field => { controls.get(field.control).value = 'last staged'; },
    text: (element, value) => { element.textContent = value; }
  };
  vm.runInNewContext(source.slice(start, end), context);

  for (const field of fields.slice(1)) {
    const control = controls.get(field.control);
    control.value = field.path === 'DisplayItem.Material' ? 'STONE BLOCK' : '';
    control.handler();
    assert.equal(control.value, 'last staged', `${field.path} must restore the staged value`);
    assert.equal(edits.length, 0, `${field.path} must not be staged`);
  }
  const service = controls.get('#vote-site-service');
  service.value = 'bad|service'; service.handler();
  assert.equal(edits.length, 0);
  service.value = 'PlanetMinecraft.com'; service.handler();
  assert.deepEqual(edits, [['ServiceSite', 'PlanetMinecraft.com']]);
  assert.equal(controls.get('#vote-site-ack').checked, false);
  assert.match(controls.get('#vote-sites-status').textContent, /last staged value was retained/);
});
