const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');

function declaration(name) {
  const match = new RegExp(`(?:^|\\n)(?:async )?function ${name}\\(`, 'm').exec(source);
  assert.ok(match, `app.js declares ${name}`);
  const start = match.index + (match[0].startsWith('\n') ? 1 : 0);
  const open = source.indexOf('{', start);
  let depth = 0;
  let quote = '';
  for (let index = open; index < source.length; index++) {
    const character = source[index];
    if (quote) {
      if (character === '\\') index++;
      else if (character === quote) quote = '';
      continue;
    }
    if (character === '\'' || character === '"' || character === '`') quote = character;
    else if (character === '{') depth++;
    else if (character === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`Could not extract ${name}`);
}

class Element {
  constructor(tagName = 'div') {
    this.tagName = tagName;
    this.children = [];
    this.value = '';
    this.textContent = '';
    this.disabled = false;
  }
  replaceChildren(...children) { this.children = children; }
}

test('Full YAML target picker lists only connected capable backends and proxies', () => {
  const configurationTarget = new Element('select');
  const context = {
    allNodeItems: [
      {nodeId: 'backend', displayName: 'Backend', platform: 'BUKKIT', online: true,
        acceptedCapabilities: ['config.files.v1']},
      {nodeId: 'rewards', displayName: 'Rewards only', platform: 'BUKKIT', online: true,
        acceptedCapabilities: ['config.reward-files.v1']},
      {nodeId: 'proxy', displayName: 'Proxy', platform: 'VELOCITY', online: true,
        acceptedCapabilities: ['config.proxy-files.v1']},
      {nodeId: 'old-proxy', displayName: 'Old proxy', platform: 'BUNGEE', online: true,
        acceptedCapabilities: []},
      {nodeId: 'offline', displayName: 'Offline', platform: 'BUKKIT', online: false,
        acceptedCapabilities: ['config.files.v1']}
    ],
    selectedServerId: 'proxy', configurationTargetNodeId: null, authenticated: true, configurationTarget,
    document: {createElement: tagName => new Element(tagName)},
    text: (element, value) => { element.textContent = value; return element; },
    isProxy: node => ['VELOCITY', 'BUNGEE'].includes(node.platform),
    isBackend: node => node.platform === 'BUKKIT',
    roleLabel: node => node.platform === 'BUKKIT' ? 'Backend' : 'Proxy'
  };
  vm.createContext(context);
  vm.runInContext([declaration('selectedConfigurationNodeId'), declaration('configurationTargetNodes'), declaration('renderConfigurationTargetPicker')].join('\n'), context);
  vm.runInContext('renderConfigurationTargetPicker()', context);

  assert.deepEqual(configurationTarget.children.map(option => option.value), ['', 'backend', 'rewards', 'proxy']);
  assert.equal(configurationTarget.value, 'proxy');
  assert.equal(configurationTarget.disabled, false);
});

test('Full YAML explicit target remains operable outside the backend workspace', () => {
  const nodes = new Map([
    ['backend', {nodeId: 'backend', platform: 'BUKKIT', online: true,
      acceptedCapabilities: ['config.files.v1', 'config.reward-files.v1']}],
    ['proxy', {nodeId: 'proxy', platform: 'VELOCITY', online: true,
      acceptedCapabilities: ['config.proxy-files.v1']}]
  ]);
  const context = {
    selectedServerId: 'proxy', configurationTargetNodeId: null, nodeIndex: nodes,
    isProxy: node => node.platform === 'VELOCITY', isBackend: node => node.platform === 'BUKKIT'
  };
  vm.createContext(context);
  vm.runInContext([declaration('selectedConfigurationNodeId'), declaration('selectedFileCapability'), declaration('fileTargetsForSelection')].join('\n'), context);
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('bungeeconfig.yml')", context)], ['proxy']);
  context.selectedServerId = 'backend';
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('Config.yml')", context)], ['backend']);
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('Rewards/StandardVote.yml')", context)], ['backend']);
});

test('switching only the Full YAML target keeps the selected named reward file option', () => {
  const staticOption = {value: 'Config.yml', dataset: {}, remove() { throw new Error('must retain static option'); }};
  const selectedReward = {value: 'Rewards/Private.yml', dataset: {sessionRewardFile: 'true'},
    remove() { configurationFile.options = configurationFile.options.filter(option => option !== this); }};
  const otherReward = {value: 'Rewards/Other.yml', dataset: {sessionRewardFile: 'true'},
    remove() { configurationFile.options = configurationFile.options.filter(option => option !== this); }};
  const configurationFile = {value: selectedReward.value, options: [staticOption, selectedReward, otherReward]};
  const context = {configurationFile, configurationFileSelection: selectedReward.value};
  vm.createContext(context);
  vm.runInContext(declaration('clearSessionRewardFileOptions'), context);

  vm.runInContext('clearSessionRewardFileOptions(true)', context);

  assert.deepEqual(configurationFile.options, [staticOption, selectedReward]);
  assert.equal(configurationFile.value, 'Rewards/Private.yml');
  assert.equal(context.configurationFileSelection, 'Rewards/Private.yml');

});

test('Full YAML toolbar wraps label and select pairs according to their available width', () => {
  const css = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.css'), 'utf8');
  const html = fs.readFileSync(path.join(__dirname, '../../main/resources/web/index.html'), 'utf8');
  assert.match(css, /\.editor-toolbar \{ display: flex; flex-wrap: wrap;/);
  assert.match(css, /\.editor-field \{ display: flex; flex: 1 1 320px; flex-wrap: wrap;/);
  assert.match(css, /\.editor-toolbar select \{ flex: 1 1 180px; min-width: 0; max-width: 100%;/);
  assert.match(html, /class="editor-field">\s*<label for="configuration-target"/);
  assert.match(html, /class="editor-field">\s*<label for="configuration-file"/);
});

test('configuration workspace exposes truthful proxy, reward-file, and detected-site entry points', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/resources/web/index.html'), 'utf8');
  assert.match(html, /id="configuration-target"/);
  assert.match(html, /existing named <code>Rewards\/\*\.yml<\/code> file/);
  assert.match(html, /data-open-tab="rewards">Browse Reward Files/);
  assert.match(html, /data-quick-preset="vote-site">Add Vote Site/);
  assert.match(source, /pendingDetectedVoteSite = \{nodeId: selectedServerId, key, service:/);
  assert.match(source, /selected\.fileName\.startsWith\('Rewards\/'\)/);
  assert.match(source, /selectedFileCapability\(fileName = configurationFile\.value\)/);
});

function targetHarness({dirty = false, confirm = true} = {}) {
  const context = {
    selectedServerId: 'backend', configurationTargetNodeId: null,
    configurationTarget: {value: 'proxy'}, configurationDirty: dirty, inputGeneration: 7,
    quickSetupDirty: true, dedicatedSetupDirty: new Set(['vote-logging', 'auto-create-vote-sites']),
    quickSetupForm: {draft: 'say retained'}, loadedQuickSetup: {nodeId: 'backend'},
    approvedQuickPreview: {token: 'guided'}, dedicatedSetupApprovals: new Map([['vote-logging', {token: 'dedicated'}]]),
    routingDirty: true, routingDraftNodeId: 'backend',
    workspace: {inspectedServerId: 'backend', selectedTargetIds: new Set(['backend'])},
    selectedNodes: new Set(['backend']), resetCount: 0, reads: [], confirmations: [],
    configurationTargetNodes: () => [{nodeId: 'backend'}, {nodeId: 'proxy'}],
    window: {confirm(message) { context.confirmations.push(message); return confirm; }},
    clearSessionRewardFileOptions(preserve) { assert.equal(preserve, true); },
    resetFileEditorForSelection() { context.resetCount++; context.configurationDirty = false; },
    renderConfigurationTargetPicker() { context.configurationTarget.value = context.configurationTargetNodeId; },
    renderConfigurationSelection() {}, updateConfigurationButtons() {}, updateExtendedButtons() {},
    autoLoadTab(tab) { context.reads.push(tab); }
  };
  vm.createContext(context);
  vm.runInContext([declaration('selectedConfigurationNodeId'), declaration('selectConfigurationTarget')].join('\n'), context);
  return context;
}

test('changing Full YAML target preserves the source, workspace, routing and all guided drafts and approvals', () => {
  const context = targetHarness();
  assert.equal(vm.runInContext("selectConfigurationTarget('proxy')", context), true);
  assert.equal(context.configurationTargetNodeId, 'proxy');
  assert.equal(context.selectedServerId, 'backend');
  assert.equal(context.workspace.inspectedServerId, 'backend');
  assert.deepEqual([...context.workspace.selectedTargetIds], ['backend']);
  assert.deepEqual([...context.selectedNodes], ['backend']);
  assert.equal(context.quickSetupDirty, true);
  assert.equal(context.quickSetupForm.draft, 'say retained');
  assert.deepEqual([...context.dedicatedSetupDirty], ['vote-logging', 'auto-create-vote-sites']);
  assert.equal(context.loadedQuickSetup.nodeId, 'backend');
  assert.equal(context.approvedQuickPreview.token, 'guided');
  assert.equal(context.dedicatedSetupApprovals.get('vote-logging').token, 'dedicated');
  assert.equal(context.routingDirty, true);
  assert.equal(context.routingDraftNodeId, 'backend');
  assert.equal(context.resetCount, 1);
  assert.deepEqual(context.confirmations, []);
  assert.deepEqual(context.reads, ['configurations']);
});

test('declining a Full YAML target change retains the editor and restores the target picker', () => {
  const context = targetHarness({dirty: true, confirm: false});
  assert.equal(vm.runInContext("selectConfigurationTarget('proxy')", context), false);
  assert.equal(context.configurationTargetNodeId, null);
  assert.equal(context.configurationTarget.value, 'backend');
  assert.equal(context.configurationDirty, true);
  assert.equal(context.resetCount, 0);
  assert.equal(context.inputGeneration, 7);
  assert.deepEqual(context.reads, []);
  assert.deepEqual(context.confirmations, ['Discard unsaved YAML changes before switching the Full YAML target?']);
});

test('confirming a Full YAML discard clears only its editor and invalidates outstanding reads', () => {
  const context = targetHarness({dirty: true});
  assert.equal(vm.runInContext("selectConfigurationTarget('proxy')", context), true);
  assert.equal(context.configurationDirty, false);
  assert.equal(context.inputGeneration, 8);
  assert.equal(context.quickSetupDirty, true);
  assert.equal(context.dedicatedSetupDirty.size, 2);
  assert.equal(context.routingDirty, true);
});

test('an explicit empty YAML target does not fall back to the source server', () => {
  const context = targetHarness();
  vm.runInContext("selectConfigurationTarget('')", context);
  assert.equal(vm.runInContext('selectedConfigurationNodeId()', context), '');
  assert.equal(context.selectedServerId, 'backend');
});

test('all shared source selectors confirm every guided draft they will discard', () => {
  const context = {configurationDirty: false, routingDirty: false, quickSetupDirty: true,
    dedicatedSetupDirty: new Set(['vote-logging']), messages: [],
    window: {confirm(message) { context.messages.push(message); return false; }}};
  vm.createContext(context);
  vm.runInContext(declaration('confirmDiscardUnsavedConfiguration'), context);
  assert.equal(vm.runInContext("confirmDiscardUnsavedConfiguration('switching servers')", context), false);
  assert.match(context.messages[0], /guided setup, dedicated setup/);
  context.quickSetupDirty = false;
  context.dedicatedSetupDirty.clear();
  assert.equal(vm.runInContext("confirmDiscardUnsavedConfiguration('switching servers')", context), true);
  assert.equal(context.messages.length, 1);
});

test('Full YAML draft identity follows its target session independently of the guided source', () => {
  const context = {selectedServerId: 'backend', configurationTargetNodeId: 'proxy', configurationDirty: true,
    configurationDraftNodeId: 'proxy', configurationDraftSessionId: 'proxy-1', configurationDraftFileName: 'bungeeconfig.yml',
    configurationFile: {value: 'bungeeconfig.yml'}, nodeIndex: new Map([['proxy', {sessionId: 'proxy-1'}]])};
  vm.createContext(context);
  vm.runInContext([declaration('selectedConfigurationNodeId'), declaration('fileDraftMatchesCurrentContext')].join('\n'), context);
  assert.equal(vm.runInContext('fileDraftMatchesCurrentContext()', context), true);
  context.nodeIndex.set('proxy', {sessionId: 'proxy-2'});
  assert.equal(vm.runInContext('fileDraftMatchesCurrentContext()', context), false);
});

function readHarness() {
  let resolveRead;
  const pending = new Promise(resolve => { resolveRead = resolve; });
  const context = {
    configurationTargetNodeId: 'proxy', selectedServerId: 'backend', configurationDirty: false,
    authenticationGeneration: 2, inputGeneration: 4, authenticated: true, approvedFilePreview: null,
    configurationFile: {value: 'bungeeconfig.yml'},
    configurationContent: {value: '', setAttribute() {}, removeAttribute() {}},
    configurationContentPresent: false, readFileConfiguration: {}, fileOperationStatus: {}, requests: [],
    nodeIndex: new Map([['proxy', {online: true, platform: 'VELOCITY', sessionId: 'proxy-1',
      acceptedCapabilities: ['config.proxy-files.v1']}]]),
    isProxy: node => node.platform === 'VELOCITY', isBackend: node => node.platform === 'BUKKIT',
    cachedFile: () => null, cacheFile() {}, updateEditorPosition() {}, syncYamlEditorView() {},
    updateConfigurationButtons() {}, updateExtendedButtons() {}, operationSummary: () => 'Read complete',
    text: (element, value) => { element.textContent = value; },
    startConfigurationOperation(path, body) { context.requests.push(body); return pending; }
  };
  vm.createContext(context);
  // Async loader extraction uses the same body extractor as ordinary declarations.
  vm.runInContext([declaration('selectedConfigurationNodeId'), declaration('selectedFileCapability'),
    declaration('loadFileConfiguration')].join('\n'), context);
  return {context, resolveRead, response: {operationId: 'read', results: {proxy: {success: true,
    revision: 'revision', configuration: {content: 'Enabled: true'}}}}};
}

test('Full YAML READ uses its target and binds returned content to that node', async () => {
  const {context, resolveRead, response} = readHarness();
  const read = vm.runInContext('loadFileConfiguration(true)', context);
  assert.deepEqual([...context.requests[0].nodeIds], ['proxy']);
  resolveRead(response);
  await read;
  assert.equal(context.configurationContent.value, 'Enabled: true');
  assert.equal(context.configurationSourceNodeId, 'proxy');
  assert.equal(context.configurationContentPresent, true);
});

test('an old Full YAML READ cannot replace the document after a target switch', async () => {
  const {context, resolveRead, response} = readHarness();
  const read = vm.runInContext('loadFileConfiguration(true)', context);
  context.configurationTargetNodeId = 'backend';
  context.inputGeneration++;
  context.configurationContent.value = 'new draft';
  resolveRead(response);
  await read;
  assert.equal(context.configurationContent.value, 'new draft');
  assert.equal(context.configurationContentPresent, false);
});

test('declining a source switch keeps all target ownership and guided drafts intact', () => {
  const context = targetHarness();
  context.configurationTargetNodeId = 'proxy';
  context.nodeIndex = new Map([['backend', {}], ['other', {}]]);
  context.serverPicker = {value: 'other'};
  context.window.confirm = () => false;
  context.resetServerContextValues = () => { throw new Error('must not discard forms'); };
  vm.runInContext([declaration('confirmDiscardUnsavedConfiguration'), declaration('selectPrimaryServer')].join('\n'), context);
  assert.equal(vm.runInContext("selectPrimaryServer('other')", context), false);
  assert.equal(context.serverPicker.value, 'backend');
  assert.equal(context.selectedServerId, 'backend');
  assert.equal(context.configurationTargetNodeId, 'proxy');
  assert.equal(context.quickSetupDirty, true);
  assert.equal(context.dedicatedSetupDirty.size, 2);
});

test('typed editor YAML links select a capable workspace backend without changing the global source', () => {
  const context = targetHarness();
  context.configurationTargetNodeId = 'proxy';
  context.nodeIndex = new Map([['backend', {online: true, platform: 'BUKKIT', acceptedCapabilities: ['config.files.v1']}]]);
  context.isBackend = node => node.platform === 'BUKKIT';
  vm.runInContext([declaration('selectedFileCapability'), declaration('selectWorkspaceYamlTarget')].join('\n'), context);
  assert.equal(vm.runInContext("selectWorkspaceYamlTarget('VoteSites.yml')", context), true);
  assert.equal(context.configurationTargetNodeId, 'backend');
  assert.equal(context.selectedServerId, 'backend');
  assert.equal(context.quickSetupDirty, true);
  assert.deepEqual(context.reads, []);
});
