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
    settingsEditor: null, voteSitesEditor: null, rewardsEditor: null,
    voteSitesHasDraft: () => false,
    ControlWorkspace,
    workspace: new ControlWorkspace.Workspace(),
    registryAvailable: false,
    selectedServerId: '',
    nodeIndex: new Map(),
    nodeCapabilities: new Map(),
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
    syncTopbarOffset: () => calls.push(['syncTopbarOffset']),
    renderOperationHistory: () => calls.push(['renderOperationHistory']),
    populateProfilePicker: () => calls.push(['populateProfilePicker']),
    confirmDiscardUnsavedConfiguration: () => true,
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
  const names = ['text', 'applyAuthenticatedSession', 'isProxy', 'isBackend', 'roleLabel', 'platformLabel',
    'friendlyCapability', 'managedCapabilities', 'proxyReportsFor', 'backendCard', 'nodePresence', 'nodeCard',
    'ordinaryTargetIds', 'comparisonTargetIds', 'changeWorkspaceTargets', 'inspectWorkspaceServer',
    'openScopeOverview', 'applyNavigationRoute', 'startConfigurationOperation'];
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
