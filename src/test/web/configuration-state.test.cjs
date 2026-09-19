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

test('rejects non-boolean edits and clears prior results when a target session changes', () => {
  const state = new MultiTargetState([{id: 'a', sessionId: 'one'}]);
  state.edit(FIELD, 'true');
  assert.equal(state.dirty.size, 0);
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
