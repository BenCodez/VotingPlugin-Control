const test = require('node:test');
const assert = require('node:assert/strict');
const {create, FILES} = require('../../main/resources/web/rewards.js');

function fixture(nodes = [{nodeId: 'a', sessionId: 'sa', online: true}, {nodeId: 'b', sessionId: 'sb', online: true}]) {
  const calls = []; let context = 'workspace'; let revision = 'r1'; let failNode = ''; let changed = new Set();
  const commands = new Map(nodes.map(node => [node.nodeId, node.nodeId === 'a' ? ['say a'] : ['say b']]));
  const adapter = {targets: () => nodes, context: () => context, active: () => true, changed() {},
    operation: async (path, body) => {
      calls.push([path, body]);
      if (path.endsWith('/read')) return {operationId: `${body.configuration.fileName}-${revision}`, results: Object.fromEntries(body.nodeIds.map(nodeId => [nodeId, {success: nodeId !== failNode, revision, message: 'READ_FAILED'}]))};
      if (path.endsWith('/preview')) return {operationId: `p-${body.nodeId}`, state: 'SUCCEEDED', approvalToken: `token-${body.nodeId}`, results: {[body.nodeId]: {success: true, revision}}};
      if (path.endsWith('/apply')) {
        const nodeId = body.previewOperationId.slice(2); changed.add(nodeId); commands.get(nodeId).push('say new');
        return {operationId: `a-${nodeId}`, state: 'SUCCEEDED', results: {[nodeId]: {success: true, reloaded: true}}};
      }
    }, request: async (path, body) => {
      calls.push([path, body]); if (path.endsWith('/discard-preview')) return {};
      const scope = body.fileName === 'VoteSites.yml' ? [{path: 'VoteSites.Alpha.Rewards', status: 'PRESENT', editable: true,
        fields: {Commands: commands.get(body.nodeId)}, advancedKeys: ['AdvancedPriority']}] : [];
      return {readOperationId: `${body.fileName}-${revision}`, nodeId: body.nodeId, fileName: body.fileName,
        sessionId: nodes.find(node => node.nodeId === body.nodeId).sessionId, revision, scopes: scope};
    }};
  return {adapter, calls, nodes, commands, changed, setContext(value) { context = value; }, setRevision(value) { revision = value; }, fail(value) { failNode = value; }};
}

test('reads each target and each real file once, then caches without preview/apply', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); await editor.read();
  assert.equal(f.calls.filter(([path]) => path.endsWith('/read')).length, FILES.length);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/state')).length, FILES.length * 2);
  assert.equal(f.calls.some(([path]) => path.endsWith('/preview') || path.endsWith('/apply')), false);
});
test('mixed command lists stay separate and append is one exact dirty operation', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  assert.equal(editor.aggregate('Commands').status, 'MIXED');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new');
  assert.equal(editor.plan().length, 2); assert.equal(editor.plan()[0].status, 'READY');
  assert.equal(await editor.preview(), true);
  const previews = f.calls.filter(([path]) => path.endsWith('/rewards/preview'));
  assert.equal(previews.length, 2); assert.equal(previews[0][1].readOperationId, 'VoteSites.yml-r1');
  assert.deepEqual(previews[0][1].value, 'say new'); assert.equal(f.calls.some(([path]) => path.endsWith('/apply')), false);
  assert.equal(await editor.apply(false), true);
  assert.deepEqual(f.commands.get('a'), ['say a', 'say new']); assert.deepEqual(f.commands.get('b'), ['say b', 'say new']);
  assert.equal(editor.edit, null); assert.equal(editor.state.results.every(item => item.confirmed), true);
});
test('edit and workspace changes dispose preview; revision and reconnect reject apply', async () => {
  const f = fixture(); const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); await editor.preview(); editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say other');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.calls.filter(([path]) => path.endsWith('/discard-preview')).length, 2);
  await editor.preview(); f.setRevision('r2'); assert.equal(await editor.apply(true), false);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/apply')).length, 0);
  await editor.read(true); await editor.preview(); f.nodes[0].sessionId = 'reconnected'; f.setContext('new-workspace'); editor.scopeChanged();
  assert.equal(editor.edit, null); assert.equal(editor.state.previewState, 'Not previewed');
});
test('failed READ remains explicit and partial apply never becomes green', async () => {
  const f = fixture(); f.fail('b'); const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); assert.equal(editor.plan()[1].status, 'ERROR');
  assert.equal(await editor.preview(), true); assert.equal(editor.state.ackRequired, true);
  assert.equal(await editor.apply(false), false);
  assert.equal(f.calls.filter(([path]) => path.endsWith('/apply')).length, 0);
  assert.equal(await editor.apply(true), false); assert.equal(editor.edit.operation, 'APPEND_LIST_ENTRY');
  assert.equal(editor.state.results[0].confirmed, true);
});
test('reload failure with rollback stays failed and never retries APPLY automatically', async () => {
  const f = fixture(); const original = f.adapter.operation;
  f.adapter.operation = async (path, body) => path.endsWith('/apply') && body.previewOperationId === 'p-b'
    ? {operationId: 'failed-b', state: 'COMPLETED_WITH_ERRORS',
      results: {b: {success: false, code: 'RELOAD_FAILED', rolledBack: true, reloaded: false}}}
    : original(path, body);
  const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); assert.equal(await editor.preview(), true);
  assert.equal(await editor.apply(true), false);
  assert.equal(editor.state.results.find(item => item.nodeId === 'a').confirmed, true);
  const failure = editor.state.results.find(item => item.nodeId === 'b');
  assert.equal(failure.confirmed, false); assert.equal(failure.result.rolledBack, true);
  assert.equal(editor.edit.operation, 'APPEND_LIST_ENTRY');
  assert.equal(f.calls.filter(([path]) => path.endsWith('/apply')).length, 1);
});
test('successful write response is not confirmation if exact appended list is absent', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true}]); const original = f.adapter.operation;
  f.adapter.operation = async (path, body) => path.endsWith('/apply')
    ? {operationId: 'not-applied', state: 'SUCCEEDED', results: {a: {success: true, reloaded: true}}}
    : original(path, body);
  const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); await editor.preview();
  assert.equal(await editor.apply(false), false);
  assert.equal(editor.state.results[0].confirmed, false);
  assert.equal(editor.edit.operation, 'APPEND_LIST_ENTRY');
});
test('draft controls cannot change while approved apply is in flight', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true}]);
  const original = f.adapter.operation;
  let releaseApply; let enteredApply;
  const applying = new Promise(resolve => { enteredApply = resolve; });
  const gate = new Promise(resolve => { releaseApply = resolve; });
  f.adapter.operation = async (path, body) => {
    if (path.endsWith('/apply')) { enteredApply(); await gate; }
    return original(path, body);
  };
  const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); await editor.preview();
  const pending = editor.apply(false); await applying;
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say different');
  editor.select('Config.yml', 'EverySiteReward'); editor.reset();
  assert.equal(await editor.preview(), false);
  assert.equal(editor.selected.rewardPath, 'VoteSites.Alpha.Rewards');
  assert.equal(editor.edit.value, 'say new');
  releaseApply(); assert.equal(await pending, true);
  assert.equal(editor.edit, null);
});

test('named reward inventory is bounded, per-target, and read without writes', async () => {
  const nodes = [
    {nodeId: 'a', sessionId: 'sa', online: true, rewardFilesSupported: true},
    {nodeId: 'b', sessionId: 'sb', online: true, rewardFilesSupported: false},
    {nodeId: 'c', sessionId: 'sc', online: true, rewardFilesSupported: true}
  ];
  const f = fixture(nodes); const inventoryCalls = [];
  f.adapter.inspect = async target => {
    inventoryCalls.push(target.nodeId);
    if (target.nodeId === 'a') return {files: ['StandardVote.yml']};
    if (target.nodeId === 'c') return {files: ['Other.yml']};
    throw new Error('inventory unavailable');
  };
  const originalRequest = f.adapter.request;
  f.adapter.request = async (path, body) => {
    if (path.endsWith('/state') && String(body.fileName).startsWith('Rewards/')) {
      const node = nodes.find(item => item.nodeId === body.nodeId);
      return {readOperationId: `${body.fileName}-r1`, nodeId: body.nodeId, fileName: body.fileName,
        sessionId: node.sessionId, revision: 'r1', scopes: [{path: '$', status: 'PRESENT', editable: true,
          fields: {Commands: [`say ${body.fileName}`]}}]};
    }
    return originalRequest(path, body);
  };
  const editor = create(f.adapter); editor.select('Rewards/StandardVote.yml', '$'); await editor.read();
  assert.deepEqual(editor.files(), ['VoteSites.yml', 'SpecialRewards.yml', 'Config.yml', 'Rewards/StandardVote.yml', 'Rewards/Other.yml']);
  assert.deepEqual(inventoryCalls.sort(), ['a', 'c']);
  assert.equal(editor.record('a', 'Rewards/StandardVote.yml').status, 'AVAILABLE');
  assert.equal(editor.record('c', 'Rewards/StandardVote.yml').status, 'MISSING');
  assert.equal(editor.record('b', 'Rewards/StandardVote.yml').status, 'UNSUPPORTED');
  assert.equal(editor.record('c', 'Rewards/Other.yml').status, 'UNLOADED');
  assert.equal(f.calls.filter(([path]) => path.endsWith('/preview') || path.endsWith('/apply')).length, 0);
  const namedReads = f.calls.filter(([path, body]) => path.endsWith('/read') && String(body.configuration.fileName).startsWith('Rewards/'));
  assert.deepEqual(namedReads.map(([, body]) => body.configuration.fileName).sort(), ['Rewards/StandardVote.yml']);
});

test('invalid named inventory fails closed and forced reads refresh the cached union', async () => {
  const nodes = [{nodeId: 'a', sessionId: 'sa', online: true, rewardFilesSupported: true}];
  const f = fixture(nodes); let listing = ['StandardVote.yml']; let inspections = 0;
  f.adapter.inspect = async () => { inspections++; return {files: listing}; };
  const editor = create(f.adapter); await editor.read();
  assert.equal(editor.files().includes('Rewards/StandardVote.yml'), true);
  listing = ['../escape.yml']; await editor.read(true);
  assert.equal(inspections, 2);
  assert.equal((await editor.inventory(nodes[0])).status, 'ERROR');
  assert.equal(editor.files().includes('Rewards/StandardVote.yml'), false);
});

test('named reward files allow existing-root edits but reject create/remove operations', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true, rewardFilesSupported: true}]);
  f.adapter.inspect = async () => ({files: ['StandardVote.yml']});
  const originalRequest = f.adapter.request;
  f.adapter.request = async (path, body) => {
    if (path.endsWith('/state') && body.fileName === 'Rewards/StandardVote.yml') return {
      readOperationId: 'Rewards/StandardVote.yml-r1', nodeId: 'a', fileName: body.fileName, sessionId: 'sa', revision: 'r1',
      scopes: [{path: '$', status: 'PRESENT', editable: true, fields: {Commands: ['say old']}}]
    };
    return originalRequest(path, body);
  };
  const editor = create(f.adapter); editor.select('Rewards/StandardVote.yml', '$'); await editor.read();
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new'); assert.equal(editor.plan()[0].status, 'READY');
  editor.setEdit('CREATE_REWARD', '', {Commands: ['say created']}); assert.equal(editor.plan()[0].status, 'UNSUPPORTED');
  editor.setEdit('REMOVE_REWARD', '', null); assert.equal(editor.plan()[0].status, 'UNSUPPORTED');
});

test('refresh before APPLY preserves other loaded named-file snapshots', async () => {
  const f = fixture([{nodeId: 'a', sessionId: 'sa', online: true, rewardFilesSupported: true}]);
  f.adapter.inspect = async () => ({files: ['First.yml', 'Second.yml']});
  const originalRequest = f.adapter.request;
  f.adapter.request = async (path, body) => {
    if (path.endsWith('/state') && String(body.fileName).startsWith('Rewards/')) return {
      readOperationId: `${body.fileName}-r1`, nodeId: 'a', fileName: body.fileName, sessionId: 'sa', revision: 'r1',
      scopes: [{path: '$', status: 'PRESENT', editable: true, fields: {Commands: f.commands.get('a')}}]
    };
    return originalRequest(path, body);
  };
  const editor = create(f.adapter);
  editor.select('Rewards/Second.yml', '$'); await editor.read();
  assert.equal(editor.record('a', 'Rewards/Second.yml').status, 'AVAILABLE');
  editor.select('Rewards/First.yml', '$'); await editor.read();
  editor.setEdit('APPEND_LIST_ENTRY', 'Commands', 'say new');
  assert.equal(await editor.preview(), true);
  assert.equal(await editor.apply(false), true);
  assert.equal(editor.record('a', 'Rewards/Second.yml').status, 'AVAILABLE');
});

test('item leaf edit excludes a target without that exact leaf instead of inventing item metadata', async () => {
  const f = fixture();
  const originalRequest = f.adapter.request;
  f.adapter.request = async (path, body) => {
    const response = await originalRequest(path, body);
    if (path.endsWith('/state') && body.fileName === 'VoteSites.yml') {
      response.scopes[0].fields['Items.Diamond.Amount'] = body.nodeId === 'a' ? '1' : undefined;
      if (body.nodeId === 'b') delete response.scopes[0].fields['Items.Diamond.Amount'];
    }
    return response;
  };
  const editor = create(f.adapter); await editor.read(); editor.select('VoteSites.yml', 'VoteSites.Alpha.Rewards');
  editor.setEdit('SET_SCALAR', 'Items.Diamond.Amount', 2);
  assert.deepEqual(editor.plan().map(item => item.status), ['READY', 'UNSUPPORTED']);
  assert.equal(await editor.preview(), true);
  assert.equal(editor.state.ackRequired, true);
  assert.deepEqual(f.calls.filter(([path]) => path.endsWith('/rewards/preview')).map(([, body]) => body.nodeId), ['a']);
});
