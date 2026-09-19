const test = require('node:test');
const assert = require('node:assert/strict');
const {VoteSitesState, ADD_FIELDS, parseIntegerField, parseStringField} = require('../../main/resources/web/vote-sites-state.js');

test('Vote Site text input matches server-side constraints before staging', () => {
  for (const path of ['ServiceSite', 'VoteURL', 'VoteDelay']) {
    assert.equal(parseStringField(path, ''), null);
    assert.equal(parseStringField(path, 'bad|value'), null);
    assert.equal(parseStringField(path, 'bad\nvalue'), null);
    assert.equal(parseStringField(path, 'valid value'), 'valid value');
  }
  assert.equal(parseStringField('ServiceSite', 'x'.repeat(2049)), null);
  assert.equal(parseStringField('VoteURL', 'x'.repeat(501)), null);
  assert.equal(parseStringField('VoteDelay', 'x'.repeat(65)), null);
  assert.equal(parseStringField('DisplayItem.Material', 'STONE BLOCK'), null);
  assert.equal(parseStringField('DisplayItem.Material', 'minecraft:diamond'), 'minecraft:diamond');
  assert.equal(parseStringField('Name', ''), '');
  assert.equal(parseStringField('Name', 'x'.repeat(201)), null);
  assert.equal(parseStringField('Name', 'bad\0name'), null);
});

test('Vote Site integer input matches server-side field ranges before staging', () => {
  assert.equal(parseIntegerField('Priority', '-2147483648'), -2147483648);
  assert.equal(parseIntegerField('Priority', '2147483647'), 2147483647);
  assert.equal(parseIntegerField('Priority', '2147483648'), null);
  assert.equal(parseIntegerField('Priority', '-2147483649'), null);
  assert.equal(parseIntegerField('DisplayItem.Amount', '1'), 1);
  assert.equal(parseIntegerField('DisplayItem.Amount', '64'), 64);
  for (const raw of ['0', '65', '-1', '1.5', '', 'abc']) assert.equal(parseIntegerField('DisplayItem.Amount', raw), null);
  assert.equal(parseIntegerField('Enabled', '1'), null);
});

const allFields = (overrides = {}) => Object.assign({Enabled: true, Name: 'Planet', ServiceSite: 'PlanetMinecraft', VoteURL: 'https://vote.example', VoteDelay: 24, Priority: 1, Hidden: false, 'DisplayItem.Material': 'DIAMOND', 'DisplayItem.Amount': 2}, overrides);
const site = (key, overrides = {}) => {
  const {fields = {}, ...rest} = overrides;
  return {siteKey: key, editable: true, rewardsConfigured: false,
    fields: Object.fromEntries(Object.entries(allFields(fields)).map(([name, value]) => [name, {status: 'AVAILABLE', value}])), ...rest};
};
const read = (nodeId, sites, extra = {}) => ({nodeId, sessionId: 's-' + nodeId, revision: 'r-' + nodeId, status: 'AVAILABLE', sites, ...extra});
const targets = () => [{nodeId: 'a', sessionId: 's-a'}, {nodeId: 'b', sessionId: 's-b'}];

test('same values are ALL/SAME and partial inventory retains every target', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('exact')])); state.setRead('b', read('b', [site('exact')])); state.selectSite('exact');
  assert.equal(state.aggregate().presence, 'ALL'); assert.equal(state.aggregateField('Enabled').state, 'SAME');
  state.setRead('b', read('b', [])); assert.equal(state.aggregate().presence, 'PARTIAL'); assert.equal(state.aggregate().targets.length, 2);
  assert.equal(state.aggregateField('Priority').state, 'MISSING');
  assert.equal(state.aggregateField('Priority').supportedState, 'SAME');
  assert.equal(state.aggregateField('Priority').value, 1);
});
test('mixed Enabled and target ERROR remain explicit', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x')])); state.setRead('b', read('b', [site('x', {fields: {Enabled: false}})])); state.selectSite('x');
  assert.equal(state.aggregateField('Enabled').state, 'MIXED'); state.setRead('b', read('b', [], {status: 'ERROR', code: 'OFFLINE'}));
  assert.equal(state.aggregateField('Enabled').state, 'ERROR'); assert.equal(state.aggregateField('Enabled').targets[1].status, 'ERROR');
});
test('site identity is exact siteKey, never Name or ServiceSite', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('one', {fields: {Name: 'same', ServiceSite: 'same'}})])); state.setRead('b', read('b', [site('two', {fields: {Name: 'same', ServiceSite: 'same'}})])); state.selectSite('one');
  assert.equal(state.aggregate().presence, 'PARTIAL');
});
test('edit plans are dirty-only and preserve unrelated target-specific fields', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x')])); state.setRead('b', read('b', [site('x', {fields: {Enabled: false, Priority: 99}})])); state.selectSite('x').edit('Enabled', false);
  const plans = state.plans(); assert.deepEqual(plans[0].changes, {Enabled: false}); assert.equal(plans[1].operation, 'UNCHANGED'); assert.equal(plans[0].changes.Priority, undefined);
});
test('create missing is explicit, never overwrites existing unless requested', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x')])); state.setRead('b', read('b', [])); state.selectSite('x').setWorkflow('CREATE_MISSING').setAddFields(allFields()).edit('Enabled', false);
  assert.equal(state.plans()[0].operation, 'UNCHANGED'); assert.equal(state.plans()[1].operation, 'ADD'); state.setCreateEditExisting(true); assert.equal(state.plans()[0].operation, 'EDIT');
});
test('remove leaves missing unchanged and keeps unsupported/noneditable targets visible', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x', {editable: false})])); state.setRead('b', read('b', [])); state.selectSite('x').setWorkflow('REMOVE');
  assert.equal(state.plans()[0].operation, 'SKIP'); assert.equal(state.plans()[1].operation, 'UNCHANGED'); state.setRead('b', read('b', [], {status: 'UNSUPPORTED'})); assert.equal(state.plans()[1].skipped[0].status, 'UNSUPPORTED');
});
test('single target is supported', () => { const state = new VoteSitesState([{nodeId: 'a', sessionId: 's-a'}]); state.setRead('a', read('a', [site('x')])); state.selectSite('x'); assert.equal(state.aggregate().presence, 'ALL'); assert.equal(state.aggregateField('Name').state, 'SAME'); });
test('preview signature invalidates for edit, target, revision, session, inventory, mode, and site key', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x')])); state.setRead('b', read('b', [site('x')])); state.selectSite('x');
  const check = change => { const sig = state.beginPreview(); assert.equal(state.finishPreview(sig, []), true); change(); assert.equal(state.previewCurrent(), null); };
  check(() => state.edit('Enabled', false)); check(() => state.setTargets([{nodeId: 'a', sessionId: 's-a'}])); state.setTargets(targets()); state.setRead('a', read('a', [site('x')])); state.setRead('b', read('b', [site('x')]));
  check(() => state.setRead('a', read('a', [site('x')], {revision: 'new'}))); check(() => state.setRead('a', read('a', [site('x')], {sessionId: 'new-session'}))); check(() => state.setRead('a', read('a', [site('y')]))); check(() => state.setMode('REMOVE')); check(() => state.selectSite('y'));
});
test('partial apply retains dirty state until every requested edit has fresh confirmation', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x', {fields: {Enabled: false}})])); state.setRead('b', read('b', [site('x', {fields: {Enabled: false}})])); state.selectSite('x').edit('Enabled', true).markApplyRequested().setApplyResult('a', {status: 'APPLIED'}).setApplyResult('b', {status: 'FAILED'});
  state.setRead('a', read('a', [site('x')])); state.results.get('a').confirmed = true;
  state.clearConfirmedDirty(); assert.equal(state.dirty.has('Enabled'), true);
  state.setRead('b', read('b', [site('x')])); state.clearConfirmedDirty(); assert.equal(state.dirty.has('Enabled'), true);
  state.results.get('b').status = 'APPLIED'; state.results.get('b').confirmed = true;
  state.clearConfirmedDirty(); assert.equal(state.dirty.has('Enabled'), false);
});
test('changing the workspace or connector session discards a previous workspace draft', () => {
  const state = new VoteSitesState(targets()); state.setRead('a', read('a', [site('x')]));
  state.selectSite('x').edit('Priority', 20).setWorkflow('CREATE_MISSING').setAddFields(allFields());
  state.setTargets([{nodeId: 'a', sessionId: 'reconnected'}]);
  assert.equal(state.dirty.size, 0); assert.deepEqual(state.addFields, {});
  assert.equal(state.workflow, 'EDIT_EXISTING'); assert.equal(state.previewCurrent(), null);
});
test('changed-target helper reports more than eight without truncating plans', () => {
  const ids = Array.from({length: 9}, (_, index) => ({nodeId: 'n' + index, sessionId: 's' + index})); const state = new VoteSitesState(ids); ids.forEach(target => state.setRead(target.nodeId, read(target.nodeId, [site('x', {fields: {Enabled: false}})]))); state.selectSite('x').edit('Enabled', true);
  assert.equal(state.changedCount(), 9); assert.equal(state.exceedsChangedTargetLimit(), true); assert.equal(state.plans().length, 9);
});
test('add map must be complete', () => { const state = new VoteSitesState([{nodeId: 'a', sessionId: 's-a'}]); state.setRead('a', read('a', [])).selectSite('x').setWorkflow('CREATE_MISSING').setAddFields({Enabled: true}); assert.equal(state.plans()[0].operation, 'SKIP'); state.setAddFields(allFields()); assert.equal(Object.keys(state.plans()[0].fields).length, ADD_FIELDS.length); });
test('editing a missing field on an existing editable site proposes an insertion', () => { const state = new VoteSitesState([{nodeId: 'a', sessionId: 's-a'}]); const value = site('x'); value.fields.Extra = {status: 'MISSING'}; state.setRead('a', read('a', [value])).selectSite('x').edit('Extra', {nested: true}); assert.deepEqual(state.plans()[0].changes, {Extra: {nested: true}}); });
