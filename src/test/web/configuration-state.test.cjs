const test = require('node:test');
const assert = require('node:assert/strict');
const {MultiTargetState} = require('../../main/resources/web/configuration-state.js');

const FIELD = 'VoteParty.Enabled';
const read = (status, revision, sessionId, fieldStatus = 'AVAILABLE', value = false, extra = {}) => ({
  status, revision, sessionId, fields: {[FIELD]: {status: fieldStatus, value}}, ...extra
});

test('aggregates mixed values while retaining partial support and explicit unsupported/read errors', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: '1'}, {id: 'b', sessionId: '1'},
    {id: 'c', sessionId: '1'}, {id: 'd', sessionId: '1'}]);
  state.setRead('a', read('AVAILABLE', '1', '1', 'AVAILABLE', true));
  state.setRead('b', read('AVAILABLE', '1', '1', 'AVAILABLE', false));
  state.setRead('c', read('AVAILABLE', '1', '1', 'UNSUPPORTED'));
  assert.deepEqual(state.aggregate(FIELD), {
    state: 'UNSUPPORTED', supportedState: 'MIXED', value: undefined,
    targets: [
      {id: 'a', status: 'AVAILABLE', value: true, revision: '1', sessionId: '1', code: '', message: ''},
      {id: 'b', status: 'AVAILABLE', value: false, revision: '1', sessionId: '1', code: '', message: ''},
      {id: 'c', status: 'UNSUPPORTED', value: undefined, revision: '1', sessionId: '1', code: '', message: ''},
      {id: 'd', status: 'MISSING', value: undefined, revision: '', sessionId: '1', code: '', message: ''}
    ]
  });
  state.setRead('d', read('ERROR', '', '1', 'MISSING', false, {code: 'OFFLINE', message: 'not reachable'}));
  assert.equal(state.aggregate(FIELD).state, 'ERROR');
  assert.equal(state.aggregate(FIELD).supportedState, 'MIXED');
});

test('plans are dirty-only and preserve distinct target values, missing fields, and no-change targets', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: 's'}, {id: 'b', sessionId: 's'}, {id: 'c', sessionId: 's'}]);
  state.setRead('a', read('AVAILABLE', 'r1', 's', 'AVAILABLE', false));
  state.setRead('b', read('AVAILABLE', 'r2', 's', 'AVAILABLE', true));
  state.setRead('c', read('AVAILABLE', 'r3', 's', 'MISSING'));
  state.edit(FIELD, true);
  assert.deepEqual(state.plans(), [
    {id: 'a', revision: 'r1', sessionId: 's', overrides: {[FIELD]: true}, skipped: [], status: 'AVAILABLE'},
    {id: 'b', revision: 'r2', sessionId: 's', overrides: {}, skipped: [], status: 'AVAILABLE'},
    {id: 'c', revision: 'r3', sessionId: 's', overrides: {}, skipped: [{field: FIELD, status: 'MISSING'}], status: 'AVAILABLE'}
  ]);
  state.setRead('c', read('UNSUPPORTED', '', 's'));
  assert.equal(state.plans()[2].skipped[0].status, 'UNSUPPORTED');
});

test('signature and preview reject stale edits, revisions, resets, and target session changes', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: 'one'}]);
  state.setRead('a', read('AVAILABLE', 'r1', 'one'));
  const signature = state.beginPreview();
  assert.equal(state.finishPreview(signature, [{id: 'a'}]), true);
  state.setRead('a', read('AVAILABLE', 'r1', 'one', 'AVAILABLE', true));
  assert.equal(state.preview, null);
  state.edit(FIELD, true);
  assert.equal(state.previewCurrent(), null);
  assert.equal(state.finishPreview(signature, []), false);
  const next = state.beginPreview();
  state.reset(FIELD);
  assert.equal(state.finishPreview(next, []), false);
  const revisionPreview = state.beginPreview();
  state.setRead('a', read('AVAILABLE', 'r2', 'one'));
  assert.equal(state.preview, null);
  assert.equal(state.finishPreview(revisionPreview, []), false);
  state.setTargets([{id: 'a', sessionId: 'two'}]);
  assert.equal(state.targets.get('a').revision, '');
  assert.equal(state.dirty.size, 0);
});

test('accepts typed string edits and clears prior results when a target session changes', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: 'one'}]);
  state.edit(FIELD, 'INFO');
  assert.equal(state.dirty.get(FIELD), 'INFO');
  state.setApplyResult('a', {success: true});
  state.setTargets([{id: 'a', sessionId: 'two'}]);
  assert.equal(state.results.size, 0);
});

test('confirmed reads only clear dirty fields when every selected target confirms requested value', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: '1'}, {id: 'b', sessionId: '1'}]);
  state.setRead('a', read('AVAILABLE', 'r', '1', 'AVAILABLE', true));
  state.setRead('b', read('ERROR', '', '1'));
  state.edit(FIELD, true).clearConfirmedDirty();
  assert.equal(state.dirty.get(FIELD), true);
  state.setRead('b', read('AVAILABLE', 'r', '1', 'AVAILABLE', true));
  state.clearConfirmedDirty();
  assert.equal(state.dirty.has(FIELD), false);
});

test('single target use, apply results, and zero-change preview work without source documents', () => {
  const state = new MultiTargetState({targets: [{id: 'only', sessionId: 's'}]});
  state.setRead('only', read('AVAILABLE', 'r', 's', 'AVAILABLE', false));
  assert.deepEqual(state.aggregate(FIELD).state, 'SAME');
  assert.equal(state.plans()[0].overrides[FIELD], undefined);
  const signature = state.beginPreview();
  assert.equal(state.finishPreview(signature, [{id: 'only', changes: []}]), true);
  assert.equal(state.previewCurrent().items[0].id, 'only');
  state.setApplyResult('only', {status: 'APPLIED'});
  assert.deepEqual(state.results.get('only'), {status: 'APPLIED'});
});

test('proxy AllowUnjoined remains effective while backend prerequisite is repaired', () => {
  const state = new MultiTargetState([{id: 'proxy', sessionId: 'p', role: 'PROXY', fileName: 'bungeeconfig.yml'},
    {id: 'backend-a', sessionId: 'a', role: 'BACKEND', managedByProxy: true, reportingProxyIds: ['proxy']},
    {id: 'backend-b', sessionId: 'b', role: 'BACKEND', managedByProxy: true, reportingProxyIds: ['proxy']}]);
  state.setRead('proxy', {status: 'AVAILABLE', sessionId: 'p', revision: 'p1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  state.setRead('backend-a', {status: 'AVAILABLE', sessionId: 'a', revision: 'a1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: true}}});
  state.setRead('backend-b', {status: 'AVAILABLE', sessionId: 'b', revision: 'b1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  assert.equal(state.aggregate('AllowUnjoined').value, false);
  state.edit('AllowUnjoined', false);
  const plans = new Map(state.plans().map(plan => [plan.id, plan]));
  assert.deepEqual(plans.get('proxy').overrides, {});
  assert.deepEqual(plans.get('backend-a').overrides, {});
  assert.deepEqual(plans.get('backend-b').overrides, {AllowUnjoined: true});
});

test('AllowUnjoined does not rewrite an unrelated standalone backend', () => {
  const state = new MultiTargetState([{id: 'proxy', sessionId: 'p', role: 'PROXY', fileName: 'bungeeconfig.yml'},
    {id: 'managed', sessionId: 'a', role: 'BACKEND', managedByProxy: true, reportingProxyIds: ['proxy']},
    {id: 'standalone', sessionId: 'b', role: 'BACKEND', managedByProxy: false}]);
  state.setRead('proxy', {status: 'AVAILABLE', sessionId: 'p', revision: 'p1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  state.setRead('managed', {status: 'AVAILABLE', sessionId: 'a', revision: 'a1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  state.setRead('standalone', {status: 'AVAILABLE', sessionId: 'b', revision: 'b1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  state.edit('AllowUnjoined', false);
  const plans = new Map(state.plans().map(plan => [plan.id, plan]));
  assert.deepEqual(plans.get('managed').overrides, {AllowUnjoined: true});
  assert.deepEqual(plans.get('standalone').overrides, {});
});

test('offline proxy topology retains the backend AllowUnjoined prerequisite', () => {
  const state = new MultiTargetState([
    {id: 'proxy', sessionId: 'p', role: 'PROXY', networkIncomplete: true},
    {id: 'backend', sessionId: 'b', role: 'BACKEND', managedByProxy: true,
      reportingProxyIds: ['proxy'], networkIncomplete: true}]);
  state.setRead('proxy', {status: 'ERROR', code: 'OFFLINE'});
  state.setRead('backend', {status: 'AVAILABLE', revision: 'r1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: true}}});
  state.edit('AllowUnjoined', false);
  const plans = new Map(state.plans().map(plan => [plan.id, plan]));
  assert.deepEqual(plans.get('backend').overrides, {});
  assert.equal(state.requestedValue(state.targets.get('backend'), 'AllowUnjoined', false), true);
});

test('each managed backend uses its own reporting proxy AllowUnjoined value', () => {
  const state = new MultiTargetState([
    {id: 'proxy-a', sessionId: 'pa', role: 'PROXY'},
    {id: 'proxy-b', sessionId: 'pb', role: 'PROXY'},
    {id: 'backend-a', sessionId: 'ba', role: 'BACKEND', managedByProxy: true, reportingProxyIds: ['proxy-a']},
    {id: 'backend-b', sessionId: 'bb', role: 'BACKEND', managedByProxy: true, reportingProxyIds: ['proxy-b']}]);
  state.setRead('proxy-a', {status: 'AVAILABLE', revision: 'pa1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: true}}});
  state.setRead('proxy-b', {status: 'AVAILABLE', revision: 'pb1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: false}}});
  state.setRead('backend-a', {status: 'AVAILABLE', revision: 'ba1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: true}}});
  state.setRead('backend-b', {status: 'AVAILABLE', revision: 'bb1',
    fields: {AllowUnjoined: {status: 'AVAILABLE', value: true}}});
  assert.equal(state.logicalValue(state.targets.get('backend-a'), 'AllowUnjoined', true), true);
  assert.equal(state.logicalValue(state.targets.get('backend-b'), 'AllowUnjoined', true), false);
  assert.equal(state.aggregate('AllowUnjoined').state, 'MIXED');
});

test('proxy debug maps Extra requests to its supported Info level', () => {
  const state = new MultiTargetState([{id: 'proxy', sessionId: 'p', role: 'PROXY', fileName: 'bungeeconfig.yml'},
    {id: 'backend', sessionId: 'b', role: 'BACKEND'}]);
  state.setRead('proxy', {status: 'AVAILABLE', sessionId: 'p', revision: 'p1',
    fields: {Debug: {status: 'AVAILABLE', value: 'INFO'}}});
  state.setRead('backend', {status: 'AVAILABLE', sessionId: 'b', revision: 'b1',
    fields: {Debug: {status: 'AVAILABLE', value: 'INFO'}}});
  state.edit('Debug', 'EXTRA');
  const plans = new Map(state.plans().map(plan => [plan.id, plan]));
  assert.deepEqual(plans.get('proxy').overrides, {});
  assert.deepEqual(plans.get('backend').overrides, {Debug: 'EXTRA'});
});

test('network synchronization skips a backend whose proxy topology is truncated', () => {
  const state = new MultiTargetState([
    {id: 'proxy', sessionId: 'p', role: 'PROXY', networkIncomplete: true},
    {id: 'backend', sessionId: 'b', role: 'BACKEND', networkIncomplete: true}]);
  state.setRead('proxy', {status: 'AVAILABLE', sessionId: 'p', revision: 'p1', fields: {
    OnlineMode: {status: 'AVAILABLE', value: false}, BedrockPlayerPrefix: {status: 'AVAILABLE', value: '.'}}});
  state.setRead('backend', {status: 'AVAILABLE', sessionId: 'b', revision: 'b1', fields: {
    OnlineMode: {status: 'AVAILABLE', value: false}, BedrockPlayerPrefix: {status: 'AVAILABLE', value: '.'}}});
  state.edit('OnlineMode', true); state.edit('BedrockPlayerPrefix', '_'); state.edit('AllowUnjoined', false);
  state.plans().forEach(plan => {
    assert.deepEqual(plan.overrides, {});
    assert.deepEqual(plan.skipped, [{field: 'OnlineMode', status: 'TOPOLOGY_INCOMPLETE'},
      {field: 'BedrockPlayerPrefix', status: 'TOPOLOGY_INCOMPLETE'},
      {field: 'AllowUnjoined', status: 'TOPOLOGY_INCOMPLETE'}]);
  });
});
