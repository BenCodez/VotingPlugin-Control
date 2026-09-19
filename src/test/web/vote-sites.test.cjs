const test = require('node:test');
const assert = require('node:assert/strict');
const {create} = require('../../main/resources/web/vote-sites.js');
function deferred() { let resolve; return {promise: new Promise(r => { resolve = r; }), resolve}; }
const fields = (enabled = false) => ({Enabled: {status: 'AVAILABLE', value: enabled}, Name: {status: 'AVAILABLE', value: 'N'}, ServiceSite: {status: 'AVAILABLE', value: 'S'}, VoteURL: {status: 'AVAILABLE', value: 'u'}, VoteDelay: {status: 'AVAILABLE', value: 1}, Priority: {status: 'AVAILABLE', value: 1}, Hidden: {status: 'AVAILABLE', value: false}, 'DisplayItem.Material': {status: 'AVAILABLE', value: 'STONE'}, 'DisplayItem.Amount': {status: 'AVAILABLE', value: 1}});
const add = () => Object.fromEntries(Object.entries(fields()).map(([key, value]) => [key, value.value]));
function fixture(nodes = [{nodeId: 'a', sessionId: 'sa', online: true}]) {
  const calls = []; let ctx = 'signed-in'; let revision = 'r1'; const values = new Map(nodes.map(node => [node.nodeId, false]));
  const adapter = {targets: () => nodes, context: () => ctx, active: () => ctx !== 'logout', changed() {}, operation: async (path, body) => { calls.push([path, body]);
    if (path.endsWith('/read')) return {operationId: 'read-' + revision, results: Object.fromEntries(body.nodeIds.map(nodeId => [nodeId, {success: true, revision, nodeId}]))};
    if (path.endsWith('/preview')) return {operationId: 'preview-' + body.nodeId, state: 'SUCCEEDED', approvalToken: 'token-' + body.nodeId, results: {[body.nodeId]: {success: true, revision}}};
    if (path.endsWith('/apply')) { if (body.previewOperationId.includes('a')) values.set('a', true); return {operationId: 'apply', results: {a: {success: true}, b: {success: true}}}; }
  }, request: async (path, body) => {
    calls.push([path, body]); if (path.endsWith('/discard-preview')) return {};
    const nodeId = body.nodeId; return {nodeId, sessionId: nodes.find(node => node.nodeId === nodeId).sessionId, revision, status: 'AVAILABLE', sites: [{siteKey: 'x', editable: true, rewardsConfigured: false, fields: fields(values.get(nodeId))}]};
  }};
  return {adapter, calls, logout: () => { ctx = 'logout'; }, setContext: value => { ctx = value; }, setRevision: value => { revision = value; }, values};
}
test('READ is single-flight, cacheable, and never creates a write operation', async () => {
  const f = fixture(); const editor = create(f.adapter); const one = editor.read(); const two = editor.read(); assert.equal(one, two); await one; await editor.read();
  assert.equal(f.calls.filter(([path]) => path.endsWith('/read')).length, 1); assert.equal(f.calls.some(([path]) => /preview|apply/.test(path)), false);
});
test('concurrent forced Vote Sites reads share one queued refresh', async () => {
  const f = fixture(); const gate = deferred(); let calls = 0;
  const original = f.adapter.operation;
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/read')) { calls++; if (calls === 1) await gate.promise; }
    return original(path, body);
  };
  const editor = create(f.adapter); const first = editor.read();
  const forced = editor.read(true); const another = editor.read(true); const retry = editor.read(false, true);
  assert.equal(forced, another); assert.equal(forced, retry);
  gate.resolve(); await Promise.all([first, forced, another, retry]);
  assert.equal(calls, 2);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/read')).length, 2);
});
test('late reads from a stale context do not populate state', async () => {
  const f = fixture(); const gate = deferred(); f.adapter.operation = async () => { await gate.promise; return {operationId: 'r', results: {a: {success: true, revision: 'r1'}}}; };
  const editor = create(f.adapter); const read = editor.read(); f.setContext('other'); await editor.scopeChanged(); gate.resolve(); await read; assert.equal(editor.model.targets.get('a').status, 'MISSING');
});
test('preview sends exact per-target EDIT, ADD, and REMOVE bodies without automatic apply', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true}, {nodeId: 'b', sessionId: 'sb', online: true}]); const editor = create(f.adapter); await editor.read(); editor.selectSite('x'); editor.edit('Enabled', true); await editor.preview();
  let preview = f.calls.find(([path]) => path.endsWith('/vote-sites/preview')); assert.deepEqual(preview[1], {readOperationId: 'read-r1', nodeId: 'a', action: 'EDIT', siteKey: 'x', fields: {Enabled: true}}); assert.equal(f.calls.some(([path]) => path.endsWith('/apply')), false);
  editor.beginAdd('missing', add(), 'missing'); await editor.preview(); preview = f.calls.filter(([path]) => path.endsWith('/vote-sites/preview')).at(-1); assert.equal(preview[1].action, 'ADD'); assert.deepEqual(preview[1].fields, add());
  editor.remove('x'); await editor.preview(); preview = f.calls.filter(([path]) => path.endsWith('/vote-sites/preview')).at(-1); assert.deepEqual(preview[1], {readOperationId: 'read-r1', nodeId: 'b', action: 'REMOVE', siteKey: 'x', fields: {}});
});
test('draft invalidation disposes exact approval and over-eight targets are not truncated', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.selectSite('x'); editor.edit('Enabled', true); await editor.preview(); editor.reset('Enabled'); await Promise.resolve();
  assert.deepEqual(f.calls.find(([path]) => path.endsWith('/discard-preview'))[1], {previewOperationId: 'preview-a', approvalToken: 'token-a'});
  const nodes = Array.from({length: 9}, (_, n) => ({nodeId: 'n' + n, sessionId: 's' + n, online: true})); const many = fixture(nodes); const manyEditor = create(many.adapter); await manyEditor.read(); manyEditor.selectSite('x').edit('Enabled', true); assert.equal(await manyEditor.preview(), false); assert.equal(manyEditor.model.plans().length, 9);
});
test('apply requires acknowledgement, forces a fresh read, and confirms refreshed values', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true}, {nodeId: 'b', sessionId: 'sb', online: false}]); const editor = create(f.adapter); await editor.read(); editor.selectSite('x'); editor.edit('Enabled', true); await editor.preview(); assert.equal(await editor.apply(), false); f.setRevision('r2'); assert.equal(await editor.apply(true), false); assert.equal(f.calls.filter(([path]) => path.endsWith('/apply')).length, 0);
  const ok = fixture(); const applying = create(ok.adapter); await applying.read(); applying.selectSite('x').edit('Enabled', true); await applying.preview(); await applying.apply(); assert.equal(applying.model.results.get('a').confirmed, true); assert.equal(applying.model.dirty.size, 0);
});
test('global read-cache invalidation during APPLY does not revoke its approved preview', async () => {
  const f = fixture(); let editor;
  const operation = f.adapter.operation;
  f.adapter.operation = async (path, body) => {
    const response = await operation(path, body);
    if (path.endsWith('/apply')) editor.invalidateReads();
    return response;
  };
  editor = create(f.adapter);
  await editor.read(); editor.selectSite('x').edit('Enabled', true);
  assert.equal(await editor.preview(), true);
  assert.equal(await editor.apply(), true);
  assert.equal(editor.model.results.get('a').confirmed, true);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/read')).length, 3);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/discard-preview')).length, 0);
});
test('partial reload failure and rollback do not clear the draft or imply confirmation', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true}, {nodeId: 'b', sessionId: 'sb', online: true}]);
  const operation = f.adapter.operation;
  f.adapter.operation = (path, body) => path.endsWith('/apply') && body.previewOperationId.includes('b')
    ? Promise.resolve({operationId: 'failed-b', results: {b: {success: false, code: 'RELOAD_FAILED', reloaded: false, rolledBack: true}}})
    : operation(path, body);
  const editor = create(f.adapter);
  await editor.read(); editor.selectSite('x').edit('Enabled', true);
  assert.equal(await editor.preview(), true);
  assert.equal(await editor.apply(), false);
  assert.equal(editor.model.results.get('a').confirmed, true);
  assert.equal(editor.model.results.get('b').confirmed, false);
  assert.equal(editor.model.results.get('b').result.rolledBack, true);
  assert.equal(editor.model.dirty.has('Enabled'), true);
  assert.match(editor.state.message, /failures or unconfirmed/);
});
test('late logout apply result is ignored and MISSING fields are editable insertions', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.selectSite('x'); editor.edit('Enabled', true); await editor.preview(); const gate = deferred(); const original = f.adapter.operation; f.adapter.operation = async (path, body) => path.endsWith('/apply') ? (await gate.promise, {results: {a: {success: true}}}) : original(path, body); const applying = editor.apply(); await Promise.resolve(); f.logout(); await editor.scopeChanged(); gate.resolve(); assert.equal(await applying, false); assert.equal(editor.model.results.size, 0);
  const direct = create(fixture().adapter); await direct.read(); direct.selectSite('x'); direct.model.targets.get('a').sites[0].fields.NewField = {status: 'MISSING'}; direct.edit('NewField', 'new'); assert.deepEqual(direct.model.plans()[0].changes, {NewField: 'new'});
});
