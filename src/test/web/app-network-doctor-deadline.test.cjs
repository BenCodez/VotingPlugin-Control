const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const ControlGeneralSettings = require('../../main/resources/web/general-settings.js');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
function section(start, end) {
  const offset = source.indexOf(start);
  assert.notEqual(offset, -1);
  return source.slice(offset, source.indexOf(end, offset));
}
function context(extra = {}) {
  const ctx = {ControlGeneralSettings, authenticated: true, settingsHealthReader: {read: () => { throw new Error('old dashboard read'); }},
    configurationHealthContext: () => 'same-context', document: {createElement: () => ({})},
    renderMetrics: () => {}, window: {setTimeout, clearTimeout}, ...extra};
  vm.createContext(ctx);
  vm.runInContext(section('function createConfigurationHealthReader(', 'settingsHealthReader = createConfigurationHealthReader(')
    + section('async function refreshConfigurationHealth(', 'function settingValueLabel('), ctx);
  return ctx;
}
test('doctor configuration reads cover at most its bounded registry page and leave dashboard flight independent', async () => {
  const targets = Array.from({length: 250}, (_, i) => ({id: `node-${i}`, sessionId: `session-${i}`,
    online: true, supported: true, fileName: 'Config.yml'}));
  const calls = [];
  const ctx = context({generalSettingsTargets: () => targets,
    startConfigurationOperation: async (_path, body, _status, options) => {
      calls.push({body, options});
      return {operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => [id,
        {success: true, sessionId: targets.find(t => t.id === id).sessionId, revision: 'r'}]))};
    }, authorized: async (_path, request) => {
      const {nodeId} = JSON.parse(request.body);
      return {nodeId, sessionId: targets.find(t => t.id === nodeId).sessionId, revision: 'r', fields: {}};
    }});
  const original = ctx.settingsHealthReader;
  const controller = new AbortController();
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(targets.slice(0, 100).map(t => t.id)), signal: controller.signal});
  assert.equal(reader.model.targets.size, 100);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].body.nodeIds.length, 100);
  assert.equal(calls[0].options.signal, controller.signal);
  assert.equal(ctx.settingsHealthReader, original);
});

test('abort releases a claimed configuration read immediately without another operation poll', async () => {
  let timer;
  let polls = 0;
  const ctx = {operationStatus: {}, operationContext: () => ({}), operationContextCurrent: () => false,
    rememberOperation: () => {}, authorized: async () => { polls++; },
    window: {setTimeout: callback => { timer = callback; return 1; }, clearTimeout: () => { timer = null; }}};
  vm.createContext(ctx);
  vm.runInContext(section('async function waitForOperation(', 'async function startConfigurationOperation('), ctx);
  const controller = new AbortController();
  const pending = ctx.waitForOperation({operationId: 'stalled', state: 'RUNNING'}, {}, {}, {signal: controller.signal});
  assert.equal(typeof timer, 'function');
  controller.abort();
  await assert.rejects(pending, /evidence is unavailable/);
  assert.equal(timer, null);
  assert.equal(polls, 0);
});

test('deadline-expired configuration evidence cannot reuse previous healthy dashboard values', async () => {
  const controller = new AbortController(); controller.abort();
  const ctx = context({generalSettingsTargets: () => [{id: 'node', sessionId: 's', online: true, supported: true}],
    startConfigurationOperation: async (_path, _body, _status, options) => {
      assert.equal(options.signal.aborted, true);
      throw new Error('aborted');
    }, authorized: async () => { throw new Error('must not read typed state'); }});
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(['node']), signal: controller.signal});
  assert.equal(reader.model.targets.get('node').status, 'ERROR');
  assert.equal(ControlGeneralSettings.healthChecks(reader.model).some(check => check.status === 'PASS'), false);
});
test('doctor keeps unenrolled topology placeholders unknown without reading them', async () => {
  const targets = [{id: 'proxy', sessionId: 'p', online: true, supported: true, networkOnly: false},
    {id: 'missing-backend', sessionId: '', online: false, supported: false, networkOnly: false, reportingProxyIds: ['proxy']}];
  const calls = [];
  const ctx = context({generalSettingsTargets: () => targets,
    startConfigurationOperation: async (_path, body) => {
      calls.push(body.nodeIds); return {operationId: 'read', results: {proxy: {success: true, sessionId: 'p', revision: 'r'}}};
    }, authorized: async (_path, request) => {
      const body = JSON.parse(request.body); return {nodeId: body.nodeId, sessionId: 'p', revision: 'r', fields: {
        OnlineMode: {status: 'AVAILABLE', value: true}, BedrockPlayerPrefix: {status: 'AVAILABLE', value: '.'}
      }};
    }});
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(['proxy'])});
  assert.deepEqual(calls, [['proxy']]);
  assert.equal(reader.model.targets.get('missing-backend').status, 'UNSUPPORTED');
  const checks = ControlGeneralSettings.healthChecks(reader.model);
  assert.equal(checks.find(check => check.path === 'OnlineMode').status, 'UNKNOWN');
  assert.equal(checks.find(check => check.path === 'BedrockPlayerPrefix').status, 'UNKNOWN');
});
test('doctor keeps the read page bounded when related enrolled peers exceed it', async () => {
  const targets = Array.from({length: 105}, (_, i) => ({id: `backend-${i}`, sessionId: `s-${i}`,
    online: true, supported: true, networkOnly: true, role: 'BACKEND'}));
  const calls = [];
  const ctx = context({generalSettingsTargets: () => targets,
    startConfigurationOperation: async (_path, body) => {
      calls.push(body.nodeIds); return {operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => [id,
        {success: true, sessionId: `s-${id.slice(8)}`, revision: 'r'}]))};
    }, authorized: async (_path, request) => {
      const body = JSON.parse(request.body); return {nodeId: body.nodeId, sessionId: `s-${body.nodeId.slice(8)}`, revision: 'r', fields: {}};
    }});
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(targets.map(target => target.id))});
  assert.equal(calls.flat().length, 100);
  assert.equal(reader.model.targets.size, 105);
  assert.equal(ControlGeneralSettings.healthChecks(reader.model).find(check => check.path === 'OnlineMode').status, 'UNKNOWN');
});
test('doctor reports matching enrolled network values as verified', async () => {
  const targets = [{id: 'proxy', sessionId: 'proxy', online: true, supported: true, role: 'PROXY'},
    {id: 'backend', sessionId: 'backend', online: true, supported: true, role: 'BACKEND', reportingProxyIds: ['proxy']}];
  const ctx = context({generalSettingsTargets: () => targets,
    startConfigurationOperation: async (_path, body) => ({operationId: 'read', results: Object.fromEntries(body.nodeIds.map(id => [id, {success: true, sessionId: id, revision: 'r'}]))}),
    authorized: async (_path, request) => ({nodeId: JSON.parse(request.body).nodeId, sessionId: JSON.parse(request.body).nodeId, revision: 'r', fields: {
      OnlineMode: {status: 'AVAILABLE', value: true}, BedrockPlayerPrefix: {status: 'AVAILABLE', value: '.'}
    }})});
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(['proxy', 'backend'])});
  const checks = ControlGeneralSettings.healthChecks(reader.model);
  assert.equal(checks.find(check => check.path === 'OnlineMode').status, 'PASS');
  assert.equal(checks.find(check => check.path === 'BedrockPlayerPrefix').status, 'PASS');
});
test('doctor retains enrolled related peers outside the page but excludes standalone nodes', async () => {
  const targets = [
    {id: 'proxy', sessionId: 'proxy', online: true, supported: true, role: 'PROXY'},
    {id: 'backend-outside-page', sessionId: 'backend', online: true, supported: true, role: 'BACKEND', networkOnly: false, reportingProxyIds: ['proxy']},
    {id: 'standalone', sessionId: 'standalone', online: true, supported: true, role: 'BACKEND', networkOnly: false}
  ];
  const ctx = context({generalSettingsTargets: () => targets,
    startConfigurationOperation: async (_path, body) => ({operationId: 'read', results: {proxy: {success: true, sessionId: 'proxy', revision: 'r'}}}),
    authorized: async () => ({nodeId: 'proxy', sessionId: 'proxy', revision: 'r', fields: {
      OnlineMode: {status: 'AVAILABLE', value: true}, BedrockPlayerPrefix: {status: 'AVAILABLE', value: '.'}
    }})});
  const reader = await ctx.refreshConfigurationHealth({nodeIds: new Set(['proxy'])});
  assert.equal(reader.model.targets.has('backend-outside-page'), true);
  assert.equal(reader.model.targets.has('standalone'), false);
  assert.equal(ControlGeneralSettings.healthChecks(reader.model).find(check => check.path === 'OnlineMode').status, 'UNKNOWN');
});
