const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
function section(start, end) {
  const offset = source.indexOf(start);
  const limit = source.indexOf(end, offset);
  assert.ok(offset >= 0 && limit > offset);
  return source.slice(offset, limit);
}

function harness() {
  let click;
  let finish;
  let rereads = 0;
  const terminal = new Promise(resolve => { finish = resolve; });
  const noop = () => {};
  const editor = {authGeneration: 1, generation: 1, file: 'Config.yml', nodeId: 'backend',
    sessionId: 'session', approval: {previewOperationId: 'preview'}};
  const ctx = {
    typedFields: editor, typedFieldsDialog: {open: true, close() { this.open = false; }},
    typedFieldsApply: {addEventListener(_name, handler) { click = handler; }}, typedFieldsPreview: {},
    typedFieldsStatus: {}, fileOperationStatus: {}, operationStatus: {}, configurationContent: {},
    configurationDirty: false, configurationFile: {value: 'Config.yml'}, authenticated: true,
    authenticationGeneration: 1, inputGeneration: 1, configurationOperationsInFlight: 0,
    selectedConfigurationNodeId: () => 'backend', nodeIndex: new Map([['backend', {sessionId: 'session'}]]),
    dedicatedSetupApprovals: new Map(), fileReadCache: new Map(), settingsHealthGeneration: 0,
    dashboardConfigurationGeneration: 0, settingsEditor: null, settingsHealthReader: null,
    voteSitesEditor: null, rewardsEditor: null, autoLoadPending: new Set(),
    tabFromHash: () => 'configurations', window: {setTimeout: noop},
    operationContext: () => ({}), operationContextCurrent: () => true,
    operationSummary: operation => operation.state, text: (target, value) => { target.textContent = value; },
    authorized: async (url, request) => {
      assert.equal(url, '/api/v1/configuration/managed-fields/apply');
      assert.deepEqual(JSON.parse(request.body), {previewOperationId: 'preview'});
      return terminal;
    },
    loadFileConfiguration: async () => { rereads++; },
    updateConfigurationButtons: noop, updateExtendedButtons: noop, syncYamlEditorView: noop,
    rememberOperation: noop, rememberVoteLoggingRestart: noop, invalidateGuidedSetupReads: noop,
    invalidateDashboardInspection: noop
  };
  vm.createContext(ctx);
  vm.runInContext(section('function clearApprovals(', 'function updateEditorPosition(')
    + section('function invalidateConfigurationReads(', 'async function loadEnrollments(')
    + section('function typedFieldsCurrent(', 'function discardTypedFieldApproval(')
    + section("typedFieldsApply.addEventListener('click'", 'readFileConfiguration.addEventListener('), ctx);
  return {ctx, click, finish, rereads: () => rereads};
}

test('successful typed Apply closes the dialog and rereads after shared invalidation', async () => {
  const {ctx, click, finish, rereads} = harness();
  const pending = click();
  finish({type: 'APPLY', state: 'SUCCEEDED', results: {backend: {success: true}}});
  await pending;
  assert.equal(ctx.inputGeneration, 3);
  assert.equal(ctx.typedFieldsDialog.open, false);
  assert.equal(rereads(), 1);
});

for (const [name, change] of Object.entries({
  'input generation': ctx => ctx.clearApprovals(),
  'selected file': ctx => { ctx.configurationFile.value = 'GUI.yml'; },
  'node session': ctx => { ctx.nodeIndex.set('backend', {sessionId: 'new-session'}); },
  'authentication': ctx => { ctx.authenticationGeneration++; }
})) {
  test(`completed typed Apply leaves a changed ${name} context alone`, async () => {
    const {ctx, click, finish, rereads} = harness();
    const pending = click();
    change(ctx);
    finish({type: 'APPLY', state: 'SUCCEEDED', results: {backend: {success: true}}});
    await pending;
    assert.equal(ctx.typedFieldsDialog.open, true);
    assert.equal(rereads(), 0);
  });
}
