const test = require('node:test');
const assert = require('node:assert/strict');
const ControlWorkspace = require('../../main/resources/web/workspace.js');
const {Workspace, parseRoute, formatRoute} = ControlWorkspace;

const backend = (nodeId, online = true, capabilities = []) => ({nodeId, platform: 'BUKKIT', online, capabilities});
const session = () => {
  const values = new Map();
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    has: key => values.has(key)
  };
};

test('login and logout clear all workspace state and Home is the default route', () => {
  const workspace = new Workspace().setTargets(['one']).inspect('one').enterGlobal();
  assert.deepEqual(parseRoute(''), {page: 'home', scope: null, inspectedServerId: '', section: ''});
  workspace.login();
  assert.equal(workspace.managementScope, null);
  assert.deepEqual([...workspace.selectedTargetIds], []);
  assert.equal(workspace.inspectedServerId, '');
  assert.deepEqual([...workspace.previousTargetIds], []);
  workspace.setTargets(['two']).inspect('two').enterGlobal().logout();
  assert.equal(workspace.managementScope, null);
  assert.deepEqual([...workspace.selectedTargetIds], []);
  assert.equal(workspace.inspectedServerId, '');
  assert.deepEqual([...workspace.previousTargetIds], []);
});

test('target selection covers single, multi, clear, global, and restore', () => {
  const workspace = new Workspace().setTargets(['one']);
  assert.equal(workspace.managementScope, 'SERVER');
  workspace.toggleTarget('two');
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
  workspace.enterGlobal();
  assert.equal(workspace.managementScope, 'GLOBAL');
  assert.deepEqual([...workspace.selectedTargetIds], []);
  workspace.returnToServers();
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
  assert.deepEqual([...workspace.selectedTargetIds], ['one', 'two']);
  workspace.clearTargets();
  assert.equal(workspace.managementScope, null);
});

test('inspecting a server never changes selected targets or management scope', () => {
  const workspace = new Workspace().setTargets(['one', 'two']);
  workspace.inspect('three');
  assert.equal(workspace.inspectedServerId, 'three');
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
  assert.deepEqual([...workspace.selectedTargetIds], ['one', 'two']);
});

test('workspace routes map to dedicated Vote Sites and Rewards pages', () => {
  assert.deepEqual(parseRoute('#workspace/settings'),
    {page: 'general-settings', scope: 'WORKSPACE', inspectedServerId: '', section: 'settings'});
  assert.deepEqual(parseRoute('#workspace/vote-sites'),
    {page: 'vote-sites', scope: 'WORKSPACE', inspectedServerId: '', section: 'vote-sites'});
  assert.deepEqual(parseRoute('#workspace/rewards'),
    {page: 'rewards', scope: 'WORKSPACE', inspectedServerId: '', section: 'rewards'});
  assert.deepEqual(parseRoute('#workspace/sync'),
    {page: 'quick-setup', scope: 'WORKSPACE', inspectedServerId: '', section: 'sync'});
  assert.deepEqual(parseRoute('#global/synchronization'),
    {page: 'quick-setup', scope: 'GLOBAL', inspectedServerId: '', section: 'sync'});
});

test('reconcile preserves known offline Bukkit targets and cleans disappeared targets and inspection', () => {
  const workspace = new Workspace().setTargets(['online', 'offline', 'proxy', 'gone']).inspect('gone');
  workspace.reconcile([
    backend('online'), backend('offline', false),
    {nodeId: 'proxy', platform: 'VELOCITY', online: true}
  ]);
  assert.deepEqual([...workspace.selectedTargetIds], ['online', 'offline']);
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
  assert.equal(workspace.inspectedServerId, '');
});

test('selectEligible chooses only online Bukkit nodes with an eligible versioned capability', () => {
  const workspace = new Workspace();
  workspace.selectEligible([
    backend('files', true, ['config.files.v1']),
    backend('quick', true, ['config.quick-setup.v2']),
    backend('inspect', true, ['data.inspect.v1']),
    backend('offline', false, ['config.files.v1']),
    backend('none', true, []),
    backend('unsupported', true, ['config.files.v9']),
    {nodeId: 'proxy', platform: 'BUNGEECORD', online: true, capabilities: ['config.files.v1']}
  ], 2);
  assert.deepEqual([...workspace.selectedTargetIds], ['files', 'quick']);
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
});

test('navigation parse and format preserve selected scope when separately inspecting a server', () => {
  const workspace = new Workspace().setTargets(['one', 'two']);
  const routes = ['#home', '#workspace/overview/settings', '#workspace/vote-sites',
    '#workspace/configuration', '#workspace/data', '#workspace/activity',
    '#global/network', '#global/routing', '#global/synchronization', '#global/configuration',
    '#global/activity', '#servers/server%201/overview', '#overview'];
  for (const hash of routes) {
    const route = parseRoute(hash);
    assert.deepEqual(parseRoute(formatRoute(route)), route, hash);
    assert.deepEqual([...workspace.selectedTargetIds], ['one', 'two']);
    assert.equal(workspace.managementScope, 'MULTI_SERVER');
  }
  workspace.inspect('server 1');
  assert.equal(workspace.inspectedServerId, 'server 1');
  assert.deepEqual([...workspace.selectedTargetIds], ['one', 'two']);
  assert.equal(workspace.managementScope, 'MULTI_SERVER');
});

test('unknown or malformed routes fail closed to Home', () => {
  for (const hash of ['#workspace/nope', '#servers/%E0%A4%A/overview', '#servers//overview',
    '#global/network/extra', '#not-a-panel', '#workspace/overview/nope']) {
    assert.deepEqual(parseRoute(hash), {page: 'home', scope: null, inspectedServerId: '', section: ''});
  }
});

test('session persistence restores a server workspace, inspection, and route after reload', () => {
  const storage = session();
  const original = new Workspace(storage).setTargets(['one', 'two']).inspect('two').setRoute('#workspace/settings');
  const restored = new Workspace(storage).restore([
    backend('one'), backend('two'), backend('three')
  ]);
  assert.equal(restored.managementScope, 'MULTI_SERVER');
  assert.deepEqual([...restored.selectedTargetIds], ['one', 'two']);
  assert.equal(restored.inspectedServerId, 'two');
  assert.equal(restored.currentRoute, '#workspace/settings');
  assert.equal(original.currentRoute, restored.currentRoute);
});

test('session persistence restores Global without pretending all backends are selected', () => {
  const storage = session();
  new Workspace(storage).setTargets(['one', 'two']).enterGlobal().setRoute('#global/network');
  const restored = new Workspace(storage).restore([backend('one'), backend('two')]);
  assert.equal(restored.managementScope, 'GLOBAL');
  assert.deepEqual([...restored.selectedTargetIds], []);
  assert.deepEqual([...restored.previousTargetIds], ['one', 'two']);
  assert.equal(restored.currentRoute, '#global/network');
  restored.returnToServers();
  assert.equal(restored.managementScope, 'MULTI_SERVER');
  assert.deepEqual([...restored.selectedTargetIds], ['one', 'two']);
});

test('Global restore filters disappeared and non-Bukkit previous targets', () => {
  const storage = session();
  new Workspace(storage).setTargets(['one', 'gone', 'proxy']).enterGlobal().setRoute('#global/network');
  const restored = new Workspace(storage).restore([backend('one'), {nodeId: 'proxy', platform: 'VELOCITY', online: true}]);
  assert.deepEqual([...restored.previousTargetIds], ['one']);
  assert.deepEqual(JSON.parse(storage.getItem(ControlWorkspace.SESSION_KEY)).previousTargetIds, ['one']);
  restored.returnToServers();
  assert.deepEqual([...restored.selectedTargetIds], ['one']);
});

test('session restore removes stale or non-Bukkit IDs and rewrites the safe snapshot', () => {
  const storage = session();
  new Workspace(storage).setTargets(['one', 'gone', 'proxy']).inspect('gone').setRoute('#servers/gone/overview');
  const restored = new Workspace(storage).restore([
    backend('one'), {nodeId: 'proxy', platform: 'VELOCITY', online: true}
  ]);
  assert.equal(restored.managementScope, 'SERVER');
  assert.deepEqual([...restored.selectedTargetIds], ['one']);
  assert.equal(restored.inspectedServerId, '');
  const saved = JSON.parse(storage.getItem(ControlWorkspace.SESSION_KEY));
  assert.deepEqual(saved.selectedTargetIds, ['one']);
  assert.equal(saved.inspectedServerId, '');
});

test('logout clears persisted workspace and does not restore it for the next session', () => {
  const storage = session();
  const workspace = new Workspace(storage).setTargets(['one']).inspect('one').setRoute('#servers/one/overview');
  workspace.logout();
  assert.equal(storage.has(ControlWorkspace.SESSION_KEY), false);
  const next = new Workspace(storage).restore([backend('one')]);
  assert.equal(next.managementScope, null);
  assert.deepEqual([...next.selectedTargetIds], []);
  assert.equal(next.inspectedServerId, '');
});
