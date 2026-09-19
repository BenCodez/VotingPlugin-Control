const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const appSource = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');

function declaration(name) {
  const match = new RegExp(`(?:^|\\n)(?:async )?function ${name}\\(`, 'm').exec(appSource);
  const start = match ? match.index + (match[0].startsWith('\\n') ? 1 : 0) : -1;
  assert.notEqual(start, -1, `app.js declares ${name}`);
  const signatureEnd = appSource.indexOf(')', start);
  const open = appSource.indexOf('{', signatureEnd);
  let depth = 0;
  let quote = '';
  for (let index = open; index < appSource.length; index++) {
    const character = appSource[index];
    if (quote) {
      if (character === '\\') index++;
      else if (character === quote) quote = '';
      continue;
    }
    if (character === '\'' || character === '"' || character === '`') quote = character;
    else if (character === '{') depth++;
    else if (character === '}' && --depth === 0) return appSource.slice(start, index + 1);
  }
  throw new Error(`Could not extract ${name}`);
}

class Element {
  constructor() { this.value = ''; this.checked = false; this.hidden = false; this.open = false; this.children = []; this.classList = {add() {}}; }
  reset() { this.resetCalled = true; this.value = ''; this.checked = false; }
  close() { this.closeCalled = true; this.open = false; }
  replaceChildren(...children) { this.children = children; }
  get firstElementChild() { return this.children[0] || new Element(); }
}

function harness() {
  const elements = new Map();
  const element = selector => {
    if (!elements.has(selector)) elements.set(selector, new Element());
    return elements.get(selector);
  };
  const context = {
    settingsEditor: {clear() {}}, voteSitesEditor: {clear() {}}, rewardsEditor: {clear() {}},
    workspace: {logout() {}}, registryAvailable: true, authenticationGeneration: 3, authenticated: true, csrfToken: 'csrf',
    approvedPreview: {}, approvedFilePreview: {}, approvedQuickPreview: {}, loadedQuickSetup: {}, quickSetupDirty: true,
    inputGeneration: 2, logout: new Element(), sidebarToggle: new Element(), globalSearch: new Element(),
    globalSearchInput: new Element(), globalSearchOptions: new Element(), headerAction: new Element(),
    appShell: new Element(), serverPickerLabel: new Element(), welcome: new Element(), authCard: new Element(),
    setupRequired: false, enrollmentCard: new Element(), enrollmentCredential: new Element(), enrollmentList: new Element(),
    enrollmentMessage: new Element(), selectedNodes: new Set(['server']), voteSitesSourceId: 'server',
    voteSitesTargetIds: new Set(['server']), voteSitesTargetsInitialized: true, transportTestProxyId: 'proxy',
    transportTestBackendId: 'backend', proxyMethodProxyId: 'proxy', proxyMethodCurrentFor: 'proxy',
    proxyMethodCurrentSessionId: 'session', proxyMethodCurrentReadCapability: 'capability', proxyMethodCurrentValue: 'HTTP',
    fileReadCache: new Map([['read', {}]]), lastFileReadOperation: {}, lastDiagnostics: {}, lastOverview: {},
    dashboardOverview: {}, dashboardVoteSiteHealth: {}, dashboardVoteSummary24h: {}, dashboardVoteSummary30d: {},
    dashboardLoadedContext: 'context', dashboardInspectionStatus: {}, dashboardTopologySignature: 'topology',
    operationHistoryItems: [{}], deploymentHistoryItems: [{}], deploymentJar: new Element(), deploymentRunGeneration: 0,
    deploymentInFlight: true, deploymentStatus: new Element(), observedServerConfigurationGeneration: 1,
    operationHistoryStatus: 'loaded', enrollmentStatus: 'loaded', dedicatedSetupApprovals: new Set(['approval']),
    voteLoggingRestartPending: new Set(['restart']), pendingDetectedVoteSite: {}, selectedServerId: 'server',
    visibleNodeItems: [{}], allNodeItems: [{}], enrollmentIds: new Set(['server']), enrollmentsLoaded: true,
    backendTopologyTruncated: true, backendTopologyTruncatedNodeIds: new Set(['server']), nodeIndex: new Map([['server', {}]]),
    nodeCapabilities: new Map([['server', []]]), nodePlugins: new Map([['server', []]]), configurationForm: new Element(),
    fileConfigurationForm: new Element(), configurationFileSelection: 'VoteSites.yml', configurationFile: new Element(),
    configurationContentPresent: true, configurationDirty: true, configurationDraftNodeId: 'server',
    configurationDraftSessionId: 'session', configurationDraftFileName: 'VoteSites.yml', routingDirty: true,
    routingDraftNodeId: 'server', autoLoadInFlight: new Set(['vote-sites']), autoLoadPending: new Set(['rewards']),
    quickSetupForm: new Element(), rewardSimulationForm: new Element(), playerLookupForm: new Element(),
    voteLogForm: new Element(), voteTraceForm: new Element(), siteResolutionForm: new Element(), snapshotForm: new Element(),
    rewardSiteLabel: new Element(), copyRewardToSetup: new Element(), voteLogFilter: new Element(), quickCommandSuggestions: new Element(),
    detectedPlugins: new Element(), operationStatus: new Element(), fileOperationStatus: new Element(), quickOperationStatus: new Element(),
    transportTestStatus: new Element(), proxyMethodStatus: new Element(), networkDoctorResults: new Element(), dataOverview: new Element(),
    playerResult: new Element(), siteHealthResult: new Element(), voteLogSummaryResult: new Element(), voteLogResult: new Element(),
    voteTraceResult: new Element(), siteResolutionResult: new Element(), rewardSimulationResult: new Element(), driftResults: new Element(),
    snapshotList: new Element(), snapshotStatus: new Element(), nodes: new Element(), serverPicker: new Element(), message: new Element(),
    document: {createElement: () => new Element(), querySelector: element},
    emptyDashboardInspectionStatus: () => ({}), syncTopbarOffset() {}, closeSidebar() {}, updateQuickFields() {}, resetDedicatedSetupValues() {},
    renderOperationHistory() {}, renderMetrics() {}, renderTopology() {}, renderSelectedServer() {},
    updateConfigurationButtons() {}, updateExtendedButtons() {}, text(target, value) { target.textContent = value; }
  };
  vm.createContext(context);
  vm.runInContext([declaration('clearSessionRewardFileOptions'), declaration('discardAuthenticationState')].join('\n'),
    context, {filename: 'authentication-state.js'});
  return {context, element};
}

test('sign-out and session expiry share teardown that clears Vote Sites and Rewards DOM drafts', () => {
  assert.match(declaration('authorized'), /response\.status === 401[\s\S]*discardAuthenticationState\('Session expired\. Sign in again\.'/);
  assert.match(appSource, /authorized\('\/api\/v1\/auth\/logout', \{method: 'POST'\}\)[\s\S]*discardAuthenticationState\('Signed out\.'/);
  for (const reason of ['Signed out.', 'Session expired. Sign in again.']) {
    const {context, element} = harness();
    const dialog = element('#vote-site-add-dialog'); dialog.open = true;
    element('#vote-site-search').value = 'draft site'; element('#vote-site-filter').value = 'partial'; element('#vote-site-ack').checked = true;
    element('#rewards-search').value = 'draft reward'; element('#rewards-operation').value = 'REPLACE_LIST';
    element('#rewards-field').value = 'Money'; element('#rewards-value').value = 'give player 100'; element('#rewards-ack').checked = true;
    const staticOption = {value: 'Config.yml', dataset: {}, remove() { throw new Error('static file removed'); }};
    const dynamicOption = {value: 'Rewards/Private.yml', dataset: {sessionRewardFile: 'true'},
      remove() { context.configurationFile.options = context.configurationFile.options.filter(option => option !== this); }};
    context.configurationFile.options = [staticOption, dynamicOption];
    context.configurationFile.value = dynamicOption.value;
    vm.runInContext(`discardAuthenticationState(${JSON.stringify(reason)})`, context);
    assert.equal(element('#vote-site-form').resetCalled, true);
    assert.equal(element('#vote-site-add-form').resetCalled, true);
    assert.equal(dialog.closeCalled, true);
    assert.equal(element('#vote-site-search').value, ''); assert.equal(element('#vote-site-filter').value, 'all');
    assert.equal(element('#vote-site-ack').checked, false);
    assert.equal(element('#rewards-search').value, ''); assert.equal(element('#rewards-operation').value, 'APPEND_LIST_ENTRY');
    assert.equal(element('#rewards-field').value, 'Commands'); assert.equal(element('#rewards-value').value, '');
    assert.equal(element('#rewards-ack').checked, false);
    assert.deepEqual(context.configurationFile.options, [staticOption]);
    assert.equal(context.configurationFile.value, 'Config.yml');
  }
});
