const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');

function block(startMarker) {
  const start = source.indexOf(startMarker);
  assert.ok(start >= 0, `production handler exists: ${startMarker}`);
  const end = source.indexOf('\n});', start);
  assert.ok(end > start, `production handler closes: ${startMarker}`);
  return source.slice(start, end + 4);
}

function harness(dirty = false) {
  const listeners = new Map();
  const configurationFile = {
    value: 'Rewards/Private.yml',
    addEventListener(type, listener) { listeners.set(type, listener); },
    dispatchEvent(event) { listeners.get(event.type)?.(event); }
  };
  const voteSiteYaml = {addEventListener(type, listener) { listeners.set(`vote-site-${type}`, listener); }};
  const reads = [];
  const context = {
    configurationFile,
    configurationFileSelection: 'Rewards/Private.yml',
    configurationContent: {value: 'Commands:\n- say secret', disabled: false, removeAttribute() {}},
    configurationContentPresent: true,
    configurationDirty: dirty,
    configurationDraftNodeId: 'backend',
    configurationDraftSessionId: 'session',
    configurationDraftFileName: 'Rewards/Private.yml',
    lastFileReadOperation: {operationId: 'old-read'},
    approvedFilePreview: {approvalToken: 'old-preview'},
    readFileConfiguration: {hidden: false},
    fileOperationStatus: {},
    Event: class Event { constructor(type) { this.type = type; } },
    window: {confirm: () => false},
    document: {querySelector: selector => selector === '#vote-site-open-yaml' ? voteSiteYaml : null},
    updateEditorPosition() {}, clearApprovals() {}, updateExtendedButtons() {},
    text(element, value) { element.textContent = value; },
    autoLoadTab(tab) { reads.push(tab); },
    setActiveTab() {}, setConfigView() {}
  };
  vm.createContext(context);
  const resetStart = source.indexOf('function resetFileEditorForSelection(');
  const resetEnd = source.indexOf('\n}\n', resetStart);
  assert.ok(resetStart >= 0 && resetEnd > resetStart);
  vm.runInContext(source.slice(resetStart, resetEnd + 2), context);
  vm.runInContext(block("configurationFile.addEventListener('input', () => {"), context);
  vm.runInContext(block("document.querySelector('#vote-site-open-yaml').addEventListener('click', () => {"), context);
  return {context, reads, open: () => listeners.get('vote-site-click')()};
}

test('Vote Sites Open YAML clears a previously loaded named-reward document before reading the new file', () => {
  const {context, reads, open} = harness();
  open();
  assert.equal(context.configurationFile.value, 'VoteSites.yml');
  assert.equal(context.configurationFileSelection, 'VoteSites.yml');
  assert.equal(context.configurationContent.value, '');
  assert.equal(context.configurationContentPresent, false);
  assert.equal(context.lastFileReadOperation, null);
  assert.equal(context.approvedFilePreview, null);
  assert.deepEqual(reads, ['configurations']);
});

test('Vote Sites Open YAML does not discard an unsaved named-reward YAML draft when confirmation is declined', () => {
  const {context, reads, open} = harness(true);
  open();
  assert.equal(context.configurationFile.value, 'Rewards/Private.yml');
  assert.equal(context.configurationFileSelection, 'Rewards/Private.yml');
  assert.equal(context.configurationContent.value, 'Commands:\n- say secret');
  assert.equal(context.configurationDirty, true);
  assert.deepEqual(reads, []);
});
