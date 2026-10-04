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
