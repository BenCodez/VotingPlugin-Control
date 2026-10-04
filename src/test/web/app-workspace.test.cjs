const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const appSource = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
const indexSource = fs.readFileSync(path.join(__dirname, '../../main/resources/web/index.html'), 'utf8');
const ControlWorkspace = require('../../main/resources/web/workspace.js');

test('authenticated document IDs are unique and sync targets keep their visible control', () => {
  const ids = [...indexSource.matchAll(/\sid="([^"]+)"/g)].map(match => match[1]);
  assert.equal(new Set(ids).size, ids.length);
  assert.match(indexSource, /id="vote-sites-read-targets" class="settings-targets"/);
  assert.match(indexSource, /id="vote-sites-targets" class="target-list"/);
  assert.match(appSource, /const voteSitesTargets = document\.querySelector\('#vote-sites-targets'\)/);
  assert.match(appSource, /const targets = document\.querySelector\('#vote-sites-read-targets'\); targets\.replaceChildren\(\)/);
});

function declaration(name) {
  const match = new RegExp(`(?:^|\\n)(?:async )?function ${name}\\(`, 'm').exec(appSource);
  const start = match ? match.index + (match[0].startsWith('\n') ? 1 : 0) : -1;
  assert.notEqual(start, -1, `app.js declares ${name}`);
  const open = appSource.indexOf('{', start);
  let depth = 0;
  let quote = '';
  for (let index = open; index < appSource.length; index++) {
    const character = appSource[index];
    if (quote) {
      if (character === '\\') index++;
      else if (character === quote) quote = '';
      continue;
    }
    if (character === '\'' || character === '"' || character === '`') {
      quote = character;
    } else if (character === '{') {
      depth++;
    } else if (character === '}' && --depth === 0) {
      return appSource.slice(start, index + 1);
    }
  }
  throw new Error(`Could not extract ${name}`);
}

function harness() {
  const calls = [];
  class Element {
    constructor(tagName = 'div') {
      this.tagName = tagName;
      this.children = [];
      this.dataset = {};
      this.listeners = new Map();
      this.hidden = false;
      this.value = '';
      this.textContent = '';
      this.className = '';
    }
    append(...items) { this.children.push(...items); }
    prepend(...items) { this.children.unshift(...items); }
    replaceChildren(...items) { this.children = items; }
    addEventListener(type, listener) { this.listeners.set(type, listener); }
    setAttribute(name, value) { this[name] = value; }
    find(predicate) {
      if (predicate(this)) return this;
      for (const child of this.children) {
        if (child && typeof child.find === 'function') {
          const found = child.find(predicate);
          if (found) return found;
        }
      }
      return null;
    }
    get lastElementChild() { return [...this.children].reverse().find(child => child instanceof Element) || null; }
  }
  const element = () => new Element();
  const elements = new Map();
  const document = {
    createElement: tagName => new Element(tagName),
    createTextNode: text => ({textContent: text}),
    querySelector: selector => {
      if (!elements.has(selector)) elements.set(selector, element());
      return elements.get(selector);
    }
  };
  const context = {
    configurationHealthChecks: () => [], settingsHealthReader: null, settingsEditor: null, voteSitesEditor: null, rewardsEditor: null,
    voteSitesHasDraft: () => false,
    ControlWorkspace,
    workspace: new ControlWorkspace.Workspace(),
    registryAvailable: false,
    selectedServerId: '',
    nodeIndex: new Map(),
    nodeCapabilities: new Map(),
    backendTopologyTruncatedNodeIds: new Set(),
    selectedNodes: new Set(['stale']),
    voteSitesTargetIds: new Set(['stale']),
    fileReadCache: new Map([['stale', {}]]),
    dedicatedSetupApprovals: new Set(['stale']),
    voteLoggingRestartPending: new Set(['stale']),
    autoLoadInFlight: new Set(['stale']),
    autoLoadPending: new Set(['stale']),
    configurationContent: element(), configurationFile: element(), logout: element(), sidebarToggle: element(),
    globalSearch: element(), headerAction: element(), authCard: element(), welcome: element(), appShell: element(),
    serverPickerLabel: element(), enrollmentCard: element(), quickPreset: element(),
    authenticated: false, csrfToken: 'old', approvedPreview: {}, approvedFilePreview: {}, approvedQuickPreview: {},
    loadedQuickSetup: {}, quickSetupDirty: true, voteSitesSourceId: 'stale', voteSitesTargetsInitialized: true,
    transportTestProxyId: 'stale', transportTestBackendId: 'stale', proxyMethodProxyId: 'stale',
    proxyMethodCurrentFor: 'stale', proxyMethodCurrentSessionId: 'stale', proxyMethodCurrentReadCapability: 'stale',
    proxyMethodCurrentValue: 'stale', lastFileReadOperation: {}, lastDiagnostics: {}, lastOverview: {},
    dashboardOverview: {}, dashboardVoteSiteHealth: {}, dashboardVoteSummary24h: {}, dashboardVoteSummary30d: {},
    dashboardLoadedContext: 'stale', dashboardInspectionStatus: {}, dashboardTopologySignature: 'stale',
    operationHistoryItems: [{}], deploymentHistoryItems: [{}], observedServerConfigurationGeneration: 1,
    operationHistoryStatus: 'loaded', enrollmentStatus: 'loaded', pendingDetectedVoteSite: {},
    configurationContentPresent: true, configurationDirty: true, configurationDraftNodeId: 'stale',
    configurationDraftSessionId: 'stale', configurationDraftFileName: 'stale', routingDirty: true,
    routingDraftNodeId: 'stale', configurationFileSelection: 'stale', inputGeneration: 0, pageOffset: 9,
    allNodeItems: [], enrollmentsLoaded: true, enrollmentIds: new Set(), MAX_CONFIGURATION_TARGETS: 100,
    operationStatus: element(), document, activeNavigationHash: '#home',
    window: {location: {hash: '#home'}, history: {replaceState: (...args) => {
      context.window.location.hash = args[2]; calls.push(['replaceState', ...args]);
    }}},
    emptyDashboardInspectionStatus: () => ({empty: true}),
    syncYamlEditorView: () => calls.push(['syncYamlEditorView']),
    syncTopbarOffset: () => calls.push(['syncTopbarOffset']),
    renderOperationHistory: () => calls.push(['renderOperationHistory']),
    populateProfilePicker: () => calls.push(['populateProfilePicker']),
    confirmDiscardUnsavedConfiguration: () => true,
    fileTargetsForSelection: () => ['file-source'],
    resetServerContextValues: reason => calls.push(['reset', reason]),
    clearApprovals: () => calls.push(['clearApprovals']),
    renderNodeViews: () => calls.push(['renderNodeViews']),
    updateConfigurationButtons: () => calls.push(['updateConfigurationButtons']),
    updateExtendedButtons: () => calls.push(['updateExtendedButtons']),
    updateQuickFields: () => calls.push(['updateQuickFields']),
    scrollToAnchor: () => calls.push(['scrollToAnchor']),
    selectPrimaryServer: id => { context.selectedServerId = id; calls.push(['selectPrimaryServer', id]); return true; },
    setActiveTab: (tab, updateHash) => calls.push(['setActiveTab', tab, updateHash]),
    operationContext: () => ({}), configurationOperationsInFlight: 0,
    authorized: () => { throw new Error('authorized should not run for rejected preview'); },
    waitForOperation: () => { throw new Error('wait should not run for rejected preview'); },
    calls
  };
  vm.createContext(context);
  const names = ['text', 'formatDateTime', 'applyAuthenticatedSession', 'isProxy', 'isBackend', 'roleLabel', 'platformLabel',
    'friendlyCapability', 'managedCapabilities', 'proxyReportsFor', 'backendCard', 'nodePresence', 'nodeCard',
    'ordinaryTargetIds', 'comparisonTargetIds', 'confirmDiscardWorkspaceDrafts', 'changeWorkspaceTargets',
    'inspectWorkspaceServer', 'openScopeOverview', 'enterGlobalWorkspace', 'applyNavigationRoute', 'startConfigurationOperation',
    'generalSettingsTargets', 'settingValueLabel'];
  vm.runInContext(names.map(declaration).join('\n'), context, {filename: 'app-workspace-helpers.js'});
  return context;
}

function backend(id, online = true) { return {nodeId: id, platform: 'BUKKIT', online}; }
function proxy(id, online = true) { return {nodeId: id, platform: 'VELOCITY', online}; }
function run(context, source) { return vm.runInContext(source, context); }

test('overview shortcuts keep the visible Data or Quick Setup route', () => {
  const opened = [];
  const context = {
    workspace: {route: ''},
    window: {location: {hash: ''}, history: {replaceState() { throw new Error('unexpected route rewrite'); }},
      requestAnimationFrame(callback) { callback(); }},
    document: {getElementById(id) { return {id}; }},
    setActiveTab(tab) { context.workspace.route = `#workspace/${tab}`; context.window.location.hash = context.workspace.route; opened.push(tab); },
    scrollToAnchor(element) { opened.push(element.id); }
  };
  vm.createContext(context);
  vm.runInContext(declaration('openWorkspace'), context);
  run(context, "openWorkspace('data', 'site-health-card')");
  assert.equal(context.window.location.hash, '#workspace/data');
  assert.equal(context.workspace.route, '#workspace/data');
  run(context, "openWorkspace('quick-setup', 'reward-builder-card')");
  assert.equal(context.window.location.hash, '#workspace/quick-setup');
  assert.equal(context.workspace.route, '#workspace/quick-setup');
  assert.deepEqual(opened, ['data', 'site-health-card', 'quick-setup', 'reward-builder-card']);
});

test('an empty reconciled workspace replaces stale routes with Home', () => {
  const context = harness();
  context.authenticated = true;
  context.workspace.setTargets(['disappeared']);
  context.window.location.hash = '#workspace/settings';
  context.workspace.reconcile([]);
  run(context, 'applyNavigationRoute()');
  assert.equal(context.window.location.hash, '#home');
  assert.deepEqual(context.calls.filter(call => call[0] === 'setActiveTab').at(-1), ['setActiveTab', 'home', undefined]);

  const panels = [{dataset: {panel: 'home'}, hidden: true}, {dataset: {panel: 'general-settings'}, hidden: false}];
  const active = {authenticated: true, tabPanels: panels, navigationButtons: [], tabButtons: [],
    workspace: {managementScope: null, route: '', setRoute(route) { this.route = route; }},
    window: {location: {hash: '#workspace/settings'}, history: {replaceState(_state, _title, hash) { active.window.location.hash = hash; }},
      scrollTo() {}},
    workspaceHash(tab) { return tab === 'home' ? '#home' : '#workspace/settings'; },
    renderWorkspaceChrome() {}, renderScopeOverview() {}, renderOverviewActivity() {}, closeSidebar() {},
    updateHeaderAction() {}, autoLoadTab() {}};
  vm.createContext(active);
  vm.runInContext(declaration('setActiveTab'), active);
  vm.runInContext("setActiveTab('general-settings')", active);
  assert.equal(active.window.location.hash, '#home');
  assert.equal(active.workspace.route, '#home');
  assert.equal(panels[0].hidden, false);
  assert.equal(panels[1].hidden, true);
});

test('node refresh replaces a disappeared inspected-server route without dropping valid targets', () => {
  assert.match(appSource, /normalizeWorkspaceRouteAfterNodeRefresh\(previousWorkspaceTargets, previousInspectedServerId\)/);
  const context = harness();
  context.workspace.setTargets(['lobby']);
  context.workspace.inspect('gone');
  context.window.location.hash = '#servers/gone/overview';
  const previousTargets = [...context.workspace.selectedTargetIds].join('\u0000');
  const previousInspectedServerId = context.workspace.inspectedServerId;
  context.workspace.reconcile([backend('lobby')]);
  context.workspaceHash = tab => tab === 'overview' ? '#workspace/overview' : '#home';
  vm.runInContext(declaration('normalizeWorkspaceRouteAfterNodeRefresh'), context);
  run(context, `normalizeWorkspaceRouteAfterNodeRefresh(${JSON.stringify(previousTargets)}, ${JSON.stringify(previousInspectedServerId)})`);
  assert.deepEqual([...context.workspace.selectedTargetIds], ['lobby']);
  assert.equal(context.workspace.inspectedServerId, '');
  assert.equal(context.window.location.hash, '#workspace/overview');
  assert.deepEqual(context.calls.filter(call => call[0] === 'setActiveTab').at(-1), ['setActiveTab', 'overview', undefined]);
});

test('ordinaryTargetIds accepts only the readable selected source, including global proxy tools', () => {
  const context = harness();
  context.registryAvailable = true;
  context.nodeIndex.set('backend', backend('backend'));
  context.nodeIndex.set('proxy', proxy('proxy'));
  context.workspace.setTargets(['backend']);
  context.selectedServerId = 'backend';
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], ['backend']);

  context.workspace.setTargets(['other']);
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], [], 'out-of-scope source is rejected');
  context.workspace.enterGlobal();
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], [], 'global backend source is rejected');
  context.selectedServerId = 'proxy';
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], ['proxy']);
  context.nodeIndex.set('proxy', proxy('proxy', false));
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], [], 'offline source is rejected');
  context.registryAvailable = false;
  assert.deepEqual([...run(context, 'ordinaryTargetIds()')], [], 'unavailable registry is rejected');
});

test('comparisonTargetIds keeps only selected readable online Bukkit backends', () => {
  const context = harness();
  context.workspace.setTargets(['readable', 'offline', 'proxy', 'unsupported', 'missing']);
  context.nodeIndex.set('readable', backend('readable'));
  context.nodeIndex.set('offline', backend('offline', false));
  context.nodeIndex.set('proxy', proxy('proxy'));
  context.nodeIndex.set('unsupported', backend('unsupported'));
  context.nodeCapabilities.set('readable', ['config.files.v1']);
  context.nodeCapabilities.set('offline', ['config.files.v1']);
  context.nodeCapabilities.set('proxy', ['config.files.v1']);
  context.nodeCapabilities.set('unsupported', ['config.quick-setup.v1']);
  assert.deepEqual([...run(context, 'comparisonTargetIds()')], ['readable']);
});

test('General Settings expands a selected managed node to every known proxy sibling', () => {
  const context = harness();
  context.authenticated = true;
  const backendA = {...backend('backend-a'), sessionId: 'a', acceptedCapabilities: ['config.files.v1']};
  const backendB = {...backend('backend-b'), sessionId: 'b', acceptedCapabilities: ['config.files.v1']};
  const backendOffline = {...backend('backend-offline', false), sessionId: 'c', acceptedCapabilities: ['config.files.v1']};
  const networkProxy = {...proxy('proxy'), sessionId: 'p', acceptedCapabilities: ['config.proxy-files.v1'],
    backends: [{backendId: 'backend-a'}, {backendId: 'backend-b'}, {backendId: 'backend-offline'},
      {backendId: 'not-enrolled'}]};
  for (const node of [backendA, backendB, backendOffline, networkProxy]) {
    context.nodeIndex.set(node.nodeId, node);
    context.allNodeItems.push(node);
  }
  context.workspace.setTargets(['backend-a']);
  const targets = run(context, 'generalSettingsTargets()');
  assert.deepEqual([...targets.map(target => target.id)],
    ['backend-a', 'proxy', 'backend-b', 'backend-offline', 'not-enrolled']);
  assert.equal(targets.find(target => target.id === 'backend-b').managedByProxy, true);
  assert.equal(targets.find(target => target.id === 'backend-b').networkOnly, true);
  assert.equal(targets.find(target => target.id === 'proxy').networkOnly, false);
  assert.equal(targets.find(target => target.id === 'backend-offline').online, false);
  assert.deepEqual(JSON.parse(JSON.stringify(targets.find(target => target.id === 'not-enrolled'))), {
    id: 'not-enrolled', sessionId: '', online: false, fileName: 'Config.yml', platform: '', role: 'BACKEND',
    managedByProxy: true, reportingProxyIds: ['proxy'], networkIncomplete: false, networkOnly: true,
    supported: false
  });
  assert.equal(targets.find(target => target.id === 'proxy').fileName, 'bungeeconfig.yml');

  context.workspace.setTargets(['proxy']);
  assert.deepEqual([...run(context, 'generalSettingsTargets()').map(target => target.id)],
    ['proxy', 'backend-a', 'backend-b', 'backend-offline', 'not-enrolled']);
});

test('General Settings renders an empty Bedrock prefix as a real value', () => {
  const context = harness();
  assert.equal(run(context, 'settingValueLabel("")'), 'Empty');
  assert.equal(run(context, 'settingValueLabel(null)'), 'Unavailable');
});

test('workspace target changes, inspection, and scope overview preserve workspace without operations', () => {
  const context = harness();
  context.nodeIndex.set('one', backend('one'));
  context.nodeIndex.set('two', backend('two'));
  context.workspace.setTargets(['one', 'two']);
  context.selectedServerId = 'one';
  run(context, 'changeWorkspaceTargets(() => workspace.toggleTarget("two"))');
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one']);
  assert.equal(context.selectedServerId, 'one');
  run(context, 'inspectWorkspaceServer("one")');
  assert.equal(context.workspace.inspectedServerId, 'one');
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one']);
  run(context, 'openScopeOverview()');
  assert.equal(context.workspace.inspectedServerId, 'one');
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one']);
  assert.deepEqual(context.calls.filter(call => call[0] === 'setActiveTab'), [
    ['setActiveTab', 'overview', true], ['setActiveTab', 'overview', true]
  ]);
  assert.equal(context.calls.some(call => /preview|apply/i.test(call[0])), false);
});

test('Rewards drafts require confirmation before workspace target or Global scope changes', () => {
  const context = harness();
  context.authenticated = true;
  context.nodeIndex.set('one', backend('one'));
  context.nodeIndex.set('two', backend('two'));
  context.workspace.setTargets(['one', 'two']);
  context.selectedServerId = 'one';
  context.rewardsEditor = {edit: {operation: 'APPEND_LIST_ENTRY', field: 'Commands', value: 'say staged'}};
  const prompts = [];
  context.window.confirm = message => { prompts.push(message); return false; };
  assert.equal(run(context, 'changeWorkspaceTargets(() => workspace.toggleTarget("two"))'), false);
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one', 'two']);
  run(context, 'enterGlobalWorkspace()');
  assert.equal(context.workspace.managementScope, 'MULTI_SERVER');
  context.activeNavigationHash = '#workspace/overview';
  context.window.location.hash = '#global/network';
  run(context, 'applyNavigationRoute()');
  assert.equal(context.workspace.managementScope, 'MULTI_SERVER');
  assert.equal(context.window.location.hash, '#workspace/overview');
  assert.equal(prompts.length, 3);
  assert.ok(prompts.every(message => message.includes('Rewards edits')));
  assert.equal(context.calls.some(call => call[0] === 'reset'), false);
});

test('applyAuthenticatedSession resets workspace and opens Home without preview or apply work', () => {
  const context = harness();
  context.workspace.setTargets(['one']).inspect('one').enterGlobal();
  run(context, 'applyAuthenticatedSession({csrfToken: "csrf"})');
  assert.equal(context.authenticated, true);
  assert.equal(context.csrfToken, 'csrf');
  assert.equal(context.workspace.managementScope, null);
  assert.deepEqual([...context.workspace.selectedTargetIds], []);
  assert.equal(context.workspace.inspectedServerId, '');
  assert.deepEqual(context.calls.filter(call => call[0] === 'replaceState'), [['replaceState', null, '', '#home']]);
  assert.deepEqual(context.calls.filter(call => call[0] === 'setActiveTab'), [['setActiveTab', 'home', undefined]]);
  assert.equal(context.calls.some(call => /preview|apply/i.test(call[0])), false);
});

test('nodeCard keeps offline Bukkit nodes selectable, but disables proxy and unknown platforms', () => {
  const context = harness();
  const offline = run(context, 'nodeCard({nodeId: "offline", displayName: "Offline", platform: "BUKKIT", online: false, acceptedCapabilities: []})');
  const proxyCard = run(context, 'nodeCard({nodeId: "proxy", displayName: "Proxy", platform: "VELOCITY", online: true, acceptedCapabilities: [], backends: []})');
  const unknownCard = run(context, 'nodeCard({nodeId: "unknown", displayName: "Unknown", platform: "MYSTERY", online: true, acceptedCapabilities: []})');
  const checkbox = card => card.find(node => node.tagName === 'input');
  assert.equal(checkbox(offline).disabled, false);
  assert.equal(offline.find(node => node.textContent === 'Control disconnected')?.textContent, 'Control disconnected');
  assert.equal(checkbox(proxyCard).disabled, true);
  assert.equal(checkbox(unknownCard).disabled, true);
  for (const card of [offline, proxyCard, unknownCard]) {
    assert.equal(card.find(node => node.textContent === 'No supported management capability reported. Configuration tools are unavailable.')?.textContent,
      'No supported management capability reported. Configuration tools are unavailable.');
  }
});

test('proxy card opens a proxy workspace for settings without making it a backend target', () => {
  const context = harness();
  const proxyNode = {nodeId: 'proxy', displayName: 'Velocity', platform: 'VELOCITY', online: true,
    sessionId: 'proxy-session', acceptedCapabilities: ['config.proxy-files.v1'], backends: []};
  context.nodeIndex.set('proxy', proxyNode);
  context.proxyNode = proxyNode;
  context.nodeIndex.set('backend-a', backend('backend-a'));
  context.nodeIndex.set('backend-b', backend('backend-b'));
  context.workspace.setTargets(['backend-a', 'backend-b']);
  context.selectedServerId = 'backend-a';
  const card = run(context, 'nodeCard(proxyNode)');
  const action = card.find(node => node.tagName === 'button' && node.textContent === 'Manage proxy settings');
  assert.ok(action);
  action.listeners.get('click')();
  assert.deepEqual([...context.workspace.selectedTargetIds], ['proxy']);
  assert.equal(context.workspace.inspectedServerId, 'proxy');
  assert.equal(context.selectedServerId, 'proxy');
  assert.equal(context.calls.at(-1)[1], 'overview');
});

test('backend inspection preserves a multi-server workspace and honors dirty cancellation', () => {
  const context = harness();
  context.authenticated = true;
  context.nodeIndex.set('backend-a', backend('backend-a'));
  context.nodeIndex.set('backend-b', backend('backend-b'));
  context.workspace.setTargets(['backend-a', 'backend-b']);
  context.selectedServerId = 'backend-a';
  context.backendB = {...backend('backend-b'), displayName: 'Backend B', acceptedCapabilities: [], backends: []};
  context.nodeIndex.set('backend-b', context.backendB);
  const card = run(context, 'nodeCard(backendB)');
  const action = card.find(node => node.tagName === 'button' && node.textContent === 'Inspect server overview');
  action.listeners.get('click')();
  assert.deepEqual([...context.workspace.selectedTargetIds], ['backend-a', 'backend-b']);
  assert.equal(context.selectedServerId, 'backend-b');
});

test('back and forward scope navigation retains targets, maps presets, and restores a cancelled hash', () => {
  const context = harness();
  context.authenticated = true;
  context.workspace.setTargets(['one', 'two']);
  context.selectedServerId = 'one';
  context.nodeIndex.set('one', backend('one'));
  context.nodeIndex.set('two', backend('two'));
  context.nodeIndex.set('proxy', proxy('proxy'));
  context.allNodeItems.push(context.nodeIndex.get('one'), context.nodeIndex.get('two'), context.nodeIndex.get('proxy'));
  for (const hash of ['#workspace/overview', '#servers/two/overview', '#global/network', '#servers/two/overview']) {
    context.window.location.hash = hash;
    run(context, 'applyNavigationRoute()');
  }
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one', 'two']);
  assert.equal(context.workspace.managementScope, 'MULTI_SERVER');
  assert.equal(context.workspace.inspectedServerId, 'two');
  context.window.location.hash = '#workspace/settings';
  run(context, 'applyNavigationRoute()');
  assert.equal(context.calls.at(-1)[1], 'general-settings');
  context.window.location.hash = '#workspace/vote-sites';
  run(context, 'applyNavigationRoute()');
  assert.equal(context.calls.at(-1)[1], 'vote-sites');
  context.window.location.hash = '#global/synchronization';
  run(context, 'applyNavigationRoute()');
  assert.equal(context.quickPreset.value, 'sync-vote-sites');
  context.activeNavigationHash = '#global/network';
  context.confirmDiscardUnsavedConfiguration = () => false;
  context.window.location.hash = '#workspace/overview';
  run(context, 'applyNavigationRoute()');
  assert.deepEqual(context.calls.filter(call => call[0] === 'replaceState').at(-1),
    ['replaceState', null, '', '#global/network']);
});

test('source-only preview rejects a multi-target request before posting an operation', async () => {
  const context = harness();
  context.registryAvailable = true;
  context.workspace.setTargets(['one', 'two']);
  context.selectedServerId = 'one';
  context.nodeIndex.set('one', backend('one'));
  await assert.rejects(() => run(context,
    'startConfigurationOperation("/api/v1/operations/preview", {nodeIds: ["one", "two"], configuration: {preset: "common-settings"}})'),
  /Choose a workspace source server/);
  assert.equal(context.configurationOperationsInFlight, 0);
  assert.equal(context.calls.some(call => /preview|apply/i.test(call[0])), false);
});

test('Full YAML preview accepts only its explicit capability-gated target', async () => {
  const context = harness();
  context.authorized = async (path, request) => ({operationId: 'preview', state: 'SUCCEEDED',
    request: JSON.parse(request.body)});
  context.waitForOperation = async operation => operation;
  const accepted = await run(context,
    `startConfigurationOperation('/api/v1/configuration/preview', {nodeIds: ['file-source'], configuration: {domain: 'file', fileName: 'bungeeconfig.yml', content: 'Enabled: true'}})`);
  assert.deepEqual(accepted.request.nodeIds, ['file-source']);
  await assert.rejects(() => run(context,
    `startConfigurationOperation('/api/v1/configuration/preview', {nodeIds: ['other'], configuration: {domain: 'file', fileName: 'bungeeconfig.yml', content: 'Enabled: true'}})`),
  /Choose the connected Full YAML target again/);
});

test('opening workspace overview replaces an inspected view with aggregate content, without changing targets', () => {
  const context = harness();
  const source = {nodeId: 'one', displayName: 'One', platform: 'BUKKIT', online: true, acceptedCapabilities: []};
  context.nodeIndex.set('one', source);
  context.nodeIndex.set('two', {...source, nodeId: 'two', displayName: 'Two'});
  context.workspace.setTargets(['one', 'two']).inspect('two');
  context.selectedServerId = 'one';
  context.operationHistoryItems = [];
  context.dashboardContext = () => 'not-loaded';
  const activitySection = {hidden: false};
  context.voteActivity = {closest: () => activitySection};
  run(context, ['isWorkspaceOverview', 'overviewOperations', 'renderScopeOverview'].map(declaration).join('\n'));
  run(context, 'renderScopeOverview()');
  assert.equal(context.document.querySelector('#individual-overview').hidden, false);
  context.authenticated = true;
  context.navigationButtons = [];
  context.tabButtons = [];
  context.tabPanels = [{dataset: {panel: 'home'}}, {dataset: {panel: 'overview'}}];
  context.renderWorkspaceChrome = () => {};
  context.renderOverviewActivity = () => {};
  context.closeSidebar = () => {};
  context.updateHeaderAction = () => {};
  context.autoLoadTab = () => {};
  context.window.history.pushState = (_, __, hash) => { context.window.location.hash = hash; };
  run(context, ['workspaceHash', 'setActiveTab'].map(declaration).join('\n'));
  run(context, 'openScopeOverview()');
  assert.equal(context.document.querySelector('#individual-overview').hidden, true);
  assert.equal(context.document.querySelector('#workspace-overview-targets').hidden, false);
  assert.equal(activitySection.hidden, true);
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one', 'two']);
  assert.equal(context.workspace.inspectedServerId, '');
  assert.equal(context.window.location.hash, '#workspace/overview');
});

test('cancelled server selection restores its checkbox and leaves workspace unchanged', () => {
  const context = harness();
  context.workspace.setTargets(['one']);
  context.confirmDiscardUnsavedConfiguration = () => false;
  const card = run(context, 'nodeCard({nodeId: "two", displayName: "Two", platform: "BUKKIT", online: true, acceptedCapabilities: []})');
  const checkbox = card.find(node => node.tagName === 'input');
  checkbox.checked = true;
  checkbox.listeners.get('change')();
  assert.equal(checkbox.checked, false);
  assert.deepEqual([...context.workspace.selectedTargetIds], ['one']);
});

test('source-only form and explicit coordinated previews preserve existing operation API', async () => {
  const context = harness();
  context.registryAvailable = true;
  context.workspace.setTargets(['one', 'two']);
  context.selectedServerId = 'one';
  context.nodeIndex.set('one', backend('one'));
  context.autoLoadPending.clear();
  context.authorized = async (url, options) => {
    context.calls.push(['post', url, JSON.parse(options.body)]);
    return {operationId: 'preview'};
  };
  context.waitForOperation = async operation => operation;
  for (const [preset, nodeIds] of [['common-settings', ['one']], ['proxy-method', ['one', 'two']], ['sync-vote-sites', ['one', 'two']]]) {
    const result = await run(context, `startConfigurationOperation('/api/v1/configuration/preview', ${JSON.stringify({nodeIds, configuration: {preset}})})`);
    assert.equal(result.operationId, 'preview');
  }
  assert.equal(context.calls.filter(call => call[0] === 'post').length, 3);
  assert.equal(context.configurationOperationsInFlight, 0);
});

test('global health includes registered nodes while the editing workspace remains empty', () => {
  const context = harness();
  context.authenticated = true;
  context.workspace.managementScope = 'GLOBAL';
  context.allNodeItems = [{...backend('a'), acceptedCapabilities: ['config.files.v1']}];
  context.nodeIndex = new Map(context.allNodeItems.map(node => [node.nodeId, node]));
  assert.equal(run(context, 'generalSettingsTargets()').length, 0);
  assert.deepEqual([...run(context, 'generalSettingsTargets(true)').map(target => target.id)], ['a']);
});

test('fresh dashboard refresh loads configuration health even without inspection capability', async () => {
  const context = harness();
  const calls = context.calls;
  Object.assign(context, {isWorkspaceOverview: () => false, dashboardLoading: false, inspectionInFlight: false,
    refreshDashboardButton: {}, suppressNodeAutoLoad: 0, loadNodes: async () => {},
    loadEnrollments: async () => {}, loadOperationHistory: async () => {},
    refreshConfigurationHealth: async () => calls.push(['configurationHealthRead']),
    inspectionCapableNode: () => false, renderMetrics: () => {}});
  vm.runInContext(declaration('refreshDashboard'), context);
  await run(context, 'refreshDashboard()');
  assert.equal(calls.filter(call => call[0] === 'configurationHealthRead').length, 1);
});

test('health reader ignores late results after changing authentication or target context', async () => {
  const context = harness();
  let finish;
  let key = 'a';
  let renders = 0;
  Object.assign(context, {authenticated: true, settingsHealthContext: '',
    settingsHealthReader: {read: () => new Promise(resolve => {finish = resolve;})},
    configurationHealthContext: () => key, renderMetrics: () => renders++});
  vm.runInContext(declaration('refreshConfigurationHealth'), context);
  const pending = run(context, 'refreshConfigurationHealth()');
  key = 'b'; finish(); await pending;
  assert.equal(context.settingsHealthContext, '');
  assert.equal(renders, 0);
});

test('Network Doctor reads configuration health and includes typed checks on its first run', async () => {
  const context = harness();
  let click;
  let reads = 0;
  Object.assign(context, {runNetworkDoctor: {addEventListener: (_event, handler) => {click = handler;}},
    downloadNetworkDiagnostics: {}, lastDiagnostics: null, networkDoctorResults: {},
    inspectionInFlight: false, authenticationGeneration: 1, allNodeItems: [], updateExtendedButtons: () => {},
    AbortController, window: {setTimeout: () => 1, clearTimeout: () => {}},
    NetworkDoctorView: require('../../main/resources/web/network-doctor.js'),
    authorized: async () => ({checks: []}),
    runInspection: async () => ({result: {configuredVoteSites: 1}}), lastOverview: null,
    invalidateDashboardInspection: () => {}, finiteCount: value => value,
    configurationHealthChecks: () => [{path: 'OnlineMode', status: 'WARNING'}],
    refreshConfigurationHealth: async () => {reads++;}, renderJsonResult: () => {}, updateSetupChecklist: () => {}});
  context.NetworkDoctorView = {...context.NetworkDoctorView, render: () => {}};
  const start = appSource.indexOf("runNetworkDoctor.addEventListener('click'");
  const end = appSource.indexOf("downloadNetworkDiagnostics.addEventListener", start);
  vm.runInContext(appSource.slice(start, end), context);
  await click();
  assert.equal(reads, 1);
  assert.equal(context.lastDiagnostics.configurationChecks[0].status, 'WARNING');
});

test('configuration mutation fences an in-flight health read and forces a new context', () => {
  const context = harness();
  Object.assign(context, {authenticated: true, settingsHealthGeneration: 1, authenticationGeneration: 1,
    settingsHealthReader: {model: {}}, settingsHealthContext: '',
    ControlGeneralSettings: {healthChecks: () => [{status: 'PASS'}]}});
  vm.runInContext(['configurationHealthContext', 'configurationHealthChecks'].map(declaration).join('\n'), context);
  context.settingsHealthContext = run(context, 'configurationHealthContext()');
  assert.equal(run(context, 'configurationHealthChecks()')[0].status, 'PASS');
  context.settingsHealthGeneration++;
  assert.equal(run(context, 'configurationHealthChecks()')[0].status, 'UNKNOWN');
});
