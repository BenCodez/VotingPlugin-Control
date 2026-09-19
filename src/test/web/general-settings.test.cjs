const test = require('node:test');
const assert = require('node:assert/strict');
const {create} = require('../../main/resources/web/general-settings.js');
const FIELD = 'ProcessRewards';

function deferred() { let resolve; const promise = new Promise(r => { resolve = r; }); return {promise, resolve}; }
function fixture(targets = [{id: 'a', sessionId: 's1', online: true, supported: true}]) {
  const calls = []; let version = 1; let context = 'auth|scope|a:s1';
  const adapter = {targets: () => targets, context: () => context, active: () => true, changed() {},
    operation: async (path, body) => { calls.push([path, body]);
      if (path.endsWith('/read')) return {operationId: 'read-' + version, results: Object.fromEntries(body.nodeIds.map(id => {
        const target = targets.find(candidate => candidate.id === id);
        return [id, {success: true, nodeId: id, sessionId: target.sessionId, revision: 'r' + version}];
      }))};
      if (path.endsWith('/preview')) return {operationId: 'preview-' + body.nodeId, state: 'SUCCEEDED', approvalToken: 'token-' + body.nodeId, results: {[body.nodeId]: {success: true, revision: 'r' + version}}};
      return {operationId: 'apply-' + body.previewOperationId, results: {a: {success: true}}};
    }, request: async (_path, body) => ({readOperationId: body.readOperationId, nodeId: body.nodeId, sessionId: targets.find(t => t.id === body.nodeId).sessionId,
      revision: 'r' + version, fields: {[FIELD]: {status: 'AVAILABLE', value: false}}})};
  return {adapter, calls, setContext: value => { context = value; }, bump: () => { version++; }};
}

test('reads eligible targets once, retains typed snapshots, and makes no automatic write', async () => {
  const f = fixture([{id: 'a', sessionId: 's1', online: true, supported: true}, {id: 'b', sessionId: 's2', online: false, supported: true}]);
  const editor = create(f.adapter); await editor.scopeChanged(); await editor.read();
  assert.equal(f.calls.filter(c => c[0].endsWith('/read')).length, 1);
  assert.equal(f.calls.some(c => c[0].endsWith('/preview') || c[0].endsWith('/apply')), false);
  assert.equal(editor.model.targets.get('b').status, 'ERROR');
});

test('single-flight reads ignore a late result from an old context', async () => {
  const f = fixture(); const gate = deferred();
  f.adapter.operation = async () => { await gate.promise; return {operationId: 'r', results: {a: {success: true, nodeId: 'a', sessionId: 's1', revision: 'r1'}}}; };
  const editor = create(f.adapter); const first = editor.read(); const second = editor.read();
  assert.equal(first, second); f.setContext('new-context'); gate.resolve(); await first;
  assert.equal(editor.model.targets.get('a').status, 'MISSING');
});

test('previews only per-target differences and requires acknowledgement for unsupported exclusions', async () => {
  const f = fixture([{id: 'a', sessionId: 's1', online: true, supported: true}, {id: 'b', sessionId: 's2', online: true, supported: false}]);
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  assert.equal(f.calls.filter(c => c[0].endsWith('/preview')).length, 1);
  assert.equal(editor.state.ackRequired, true);
  assert.equal(await editor.apply(), false);
  assert.equal(await editor.apply(true), true);
});

test('edits invalidate previews and a forced pre-apply read rejects revision changes', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  editor.edit(FIELD, false); assert.equal(editor.state.previewState, 'Stale');
  editor.edit(FIELD, true); await editor.preview(); f.bump();
  assert.equal(await editor.apply(), false);
  assert.equal(f.calls.filter(c => c[0].endsWith('/apply')).length, 0);
});

test('partial apply results are retained and confirmed reads clear only confirmed dirty values', async () => {
  const f = fixture([{id: 'a', sessionId: 's1', online: true, supported: true}, {id: 'b', sessionId: 's2', online: true, supported: true}]);
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  f.adapter.operation = async (path, body) => {
    f.calls.push([path, body]);
    if (path.endsWith('/read')) return {operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => {
      const target = f.adapter.targets().find(candidate => candidate.id === id);
      return [id, {success: true, nodeId: id, sessionId: target.sessionId, revision: 'r1'}];
    }))};
    if (path.endsWith('/preview')) return {operationId: 'p-' + body.nodeId, state: 'SUCCEEDED', approvalToken: 't', results: {[body.nodeId]: {success: true, revision: 'r1'}}};
    if (body.previewOperationId === 'preview-b') throw new Error('b failed');
    return {operationId: 'a', results: {a: {success: true}}};
  };
  await editor.apply();
  assert.equal(editor.model.results.get('b').status, 'ERROR');
  assert.equal(editor.model.dirty.get(FIELD), true);
});

test('same-context scope refresh retains cache and preview, while inactive navigation does no read or busy stall', async () => {
  const f = fixture(); const editor = create(f.adapter);
  await editor.read(); editor.edit(FIELD, true); await editor.preview();
  const reads = f.calls.filter(c => c[0].endsWith('/read')).length;
  await editor.scopeChanged();
  assert.equal(f.calls.filter(c => c[0].endsWith('/read')).length, reads);
  assert.equal(editor.state.previewState, 'Ready');
  f.adapter.active = () => false;
  await editor.scopeChanged();
  assert.equal(editor.state.busy, false);
  assert.equal(f.calls.filter(c => c[0].endsWith('/read')).length, reads);
});

test('a forced read queued behind an existing read performs a fresh second read', async () => {
  const f = fixture(); const gate = deferred(); let calls = 0;
  f.adapter.operation = async (path, body) => {
    if (!path.endsWith('/read')) return {operationId: 'p', state: 'SUCCEEDED', approvalToken: 't', results: {a: {success: true, revision: 'r1'}}};
    calls++; if (calls === 1) await gate.promise;
    return {operationId: 'read-' + calls, results: Object.fromEntries(body.nodeIds.map(id => [id, {success: true, nodeId: id, sessionId: 's1', revision: 'r1'}]))};
  };
  const editor = create(f.adapter); const first = editor.read(); const forced = editor.read(true);
  gate.resolve(); await Promise.all([first, forced]);
  assert.equal(calls, 2);
});

test('failed previews remain visible and cannot create approval', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true);
  f.adapter.operation = async (path, body) => path.endsWith('/preview')
    ? {operationId: 'broken', state: 'SUCCEEDED', approvalToken: 't', results: {a: {success: false, revision: 'r1'}}}
    : {operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => [id, {success: true, nodeId: id, sessionId: 's1', revision: 'r1'}]))};
  assert.equal(await editor.preview(), false);
  assert.equal(editor.state.previewItems[0].status, 'ERROR');
  assert.equal(await editor.apply(), false);
});

test('invalidating reads from an apply callback does not interrupt captured sequential approvals', async () => {
  const f = fixture([{id: 'a', sessionId: 's1', online: true, supported: true}, {id: 'b', sessionId: 's2', online: true, supported: true}]);
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  const applied = [];
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/read')) return {operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => {
      const target = f.adapter.targets().find(candidate => candidate.id === id);
      return [id, {success: true, nodeId: id, sessionId: target.sessionId, revision: 'r1'}];
    }))};
    if (path.endsWith('/apply')) { applied.push(body.previewOperationId); editor.invalidateReads(); return {operationId: 'apply', results: {[body.previewOperationId.slice(-1)]: {success: true}}}; }
    return {operationId: 'p', state: 'SUCCEEDED', approvalToken: 't', results: {[body.nodeId]: {success: true, revision: 'r1'}}};
  };
  await editor.apply();
  assert.deepEqual(applied, ['preview-a', 'preview-b']);
});

test('typed state identity mismatches fail closed as TARGET_CHANGED', async () => {
  const f = fixture(); const editor = create(f.adapter);
  f.adapter.request = async (_path, body) => ({readOperationId: body.readOperationId, nodeId: body.nodeId,
    sessionId: 'wrong-session', revision: 'wrong-revision', fields: {[FIELD]: {status: 'AVAILABLE', value: false}}});
  await editor.read();
  const target = editor.model.targets.get('a');
  assert.equal(target.status, 'ERROR');
  assert.equal(target.code, 'TARGET_CHANGED');
});

test('a late apply completion after logout cannot repopulate results', async () => {
  const f = fixture(); const gate = deferred(); const editor = create(f.adapter);
  await editor.read(); editor.edit(FIELD, true); await editor.preview();
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/read')) return {operationId: 'read', results: {a: {success: true, nodeId: 'a', sessionId: 's1', revision: 'r1'}}};
    if (path.endsWith('/apply')) { await gate.promise; return {operationId: 'apply', results: {a: {success: true}}}; }
    return {operationId: 'preview', state: 'SUCCEEDED', approvalToken: 'token', results: {a: {success: true, revision: 'r1'}}};
  };
  const applying = editor.apply();
  await Promise.resolve(); await Promise.resolve();
  f.setContext('logout'); await editor.scopeChanged(); gate.resolve();
  assert.equal(await applying, false);
  assert.equal(editor.model.results.size, 0);
});

test('an old flight cannot clear busy while a new-context flight is pending', async () => {
  const f = fixture(); const oldGate = deferred(); const newGate = deferred(); let reads = 0;
  f.adapter.operation = async (_path, body) => {
    reads++;
    if (reads === 1) await oldGate.promise; else await newGate.promise;
    return {operationId: 'read-' + reads, results: {a: {success: true, nodeId: 'a', sessionId: 's1', revision: 'r1'}}};
  };
  const editor = create(f.adapter); const oldRead = editor.read();
  f.setContext('new-scope'); await editor.scopeChanged(); const newRead = editor.read();
  oldGate.resolve(); await oldRead;
  assert.equal(editor.state.busy, true);
  newGate.resolve(); await newRead;
  assert.equal(editor.state.busy, false);
});

test('a second concurrent apply is refused while the first approval is in flight', async () => {
  const f = fixture(); const gate = deferred(); const editor = create(f.adapter);
  await editor.read(); editor.edit(FIELD, true); await editor.preview();
  let applying = false;
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/read')) return {operationId: 'read', results: {a: {success: true, nodeId: 'a', sessionId: 's1', revision: 'r1'}}};
    if (path.endsWith('/apply')) { applying = true; await gate.promise; return {operationId: 'apply', results: {a: {success: true}}}; }
    return {operationId: 'preview', state: 'SUCCEEDED', approvalToken: 'token', results: {a: {success: true, revision: 'r1'}}};
  };
  const first = editor.apply();
  while (!applying) await Promise.resolve();
  assert.equal(await editor.apply(), false);
  gate.resolve(); await first;
});

test('failed automatic READ keeps structured cause visible and read-only Retry recovers', async () => {
  const f = fixture(); const normal = f.adapter.operation; let fail = true;
  f.adapter.operation = async (path, body) => fail && path.endsWith('/read')
    ? {operationId: 'failed', results: {a: {success: false, code: 'INVALID_CONFIGURATION', message: 'Invalid YAML mapping'}}}
    : normal(path, body);
  const editor = create(f.adapter); await editor.read();
  assert.equal(editor.model.targets.get('a').code, 'INVALID_CONFIGURATION');
  assert.equal(editor.model.aggregate(FIELD).state, 'ERROR');
  assert.deepEqual(editor.model.targets.get('a').fields, {});
  fail = false; await editor.read(true, true);
  assert.equal(editor.model.aggregate(FIELD).state, 'SAME');
  assert.equal(editor.model.aggregate(FIELD).value, false);
  assert.equal(f.calls.some(([path]) => /\/(preview|apply)$/.test(path)), false);
});

test('confirmed successful apply refreshes values, invalidates cache and clears resolved dirty state', async () => {
  const f = fixture(); let value = false; let revision = 'r1'; let reads = 0;
  f.adapter.request = async (_path, body) => ({nodeId: body.nodeId, sessionId: 's1', revision,
    fields: {[FIELD]: {status: 'AVAILABLE', value}}});
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/read')) { reads++; return {operationId: 'read-' + reads, results: {a: {success: true, revision}}}; }
    if (path.endsWith('/preview')) return {operationId: 'preview', state: 'SUCCEEDED', approvalToken: 'approved', results: {a: {success: true, revision}}};
    value = true; revision = 'r2';
    return {operationId: 'applied', state: 'SUCCEEDED', results: {a: {success: true, reloaded: true}}};
  };
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  await editor.apply();
  assert.equal(reads, 3);
  assert.equal(editor.model.aggregate(FIELD).value, true);
  assert.equal(editor.model.targets.get('a').revision, 'r2');
  assert.equal(editor.model.results.get('a').confirmed, true);
  assert.equal(editor.model.dirty.size, 0);
  assert.equal(editor.model.previewCurrent(), null);
});

test('unchanged targets require no subset acknowledgement and target removal invalidates approval', async () => {
  const f = fixture([{id: 'a', sessionId: 's1', online: true, supported: true}, {id: 'b', sessionId: 's2', online: true, supported: true}]);
  const original = f.adapter.request;
  f.adapter.request = async (path, body) => { const result = await original(path, body); result.fields[FIELD].value = body.nodeId === 'b'; return result; };
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  assert.equal(editor.state.ackRequired, false);
  assert.equal(editor.model.previewCurrent().items.find(item => item.id === 'b').status, 'UNCHANGED');
  f.adapter.targets = () => [{id: 'a', sessionId: 's1', online: true, supported: true}];
  f.setContext('only-a'); await editor.scopeChanged();
  assert.equal(editor.model.previewCurrent(), null);
  assert.equal(await editor.apply(), false);
  assert.equal(f.calls.some(([path]) => path.endsWith('/apply')), false);
});

test('explicit edits discard abandoned exact approvals without queuing a write', async () => {
  const f = fixture(); const requests = []; const original = f.adapter.request;
  f.adapter.request = async (path, body) => { requests.push([path, body]); return original(path, body); };
  const editor = create(f.adapter); await editor.read(); editor.edit(FIELD, true); await editor.preview();
  editor.reset(FIELD); await Promise.resolve(); await Promise.resolve();
  const disposal = requests.find(([path]) => path.endsWith('/discard-preview'));
  assert.ok(disposal);
  assert.deepEqual(disposal[1], {previewOperationId: 'preview-a', approvalToken: 'token-a'});
  assert.equal(f.calls.some(([path]) => path.endsWith('/apply')), false);
});
