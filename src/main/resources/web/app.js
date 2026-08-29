'use strict';

const health = document.querySelector('#health');
const setupCard = document.querySelector('#setup-card');
const setupForm = document.querySelector('#setup-form');
const setupCode = document.querySelector('#setup-code');
const setupPassword = document.querySelector('#setup-password');
const setupConfirmPassword = document.querySelector('#setup-confirm-password');
const setupMessage = document.querySelector('#setup-message');
const authCard = document.querySelector('#auth-card');
const form = document.querySelector('#auth-form');
const passwordInput = document.querySelector('#password');
const loginButton = form.querySelector('button[type="submit"]');
const logout = document.querySelector('#logout');
const message = document.querySelector('#message');
const welcome = document.querySelector('#welcome');
const appShell = document.querySelector('#app-shell');
const serverPickerLabel = document.querySelector('#server-picker-label');
const serverPicker = document.querySelector('#server-picker');
const tabButtons = [...document.querySelectorAll('[data-tab]')];
const tabPanels = [...document.querySelectorAll('[data-panel]')];
const configViewButtons = [...document.querySelectorAll('[data-config-view]')];
const configViewPanels = [...document.querySelectorAll('[data-config-panel]')];
const metricNodes = document.querySelector('#metric-nodes');
const metricOnline = document.querySelector('#metric-online');
const metricBackends = document.querySelector('#metric-backends');
const metricIssues = document.querySelector('#metric-issues');
const selectedServerName = document.querySelector('#selected-server-name');
const selectedServerState = document.querySelector('#selected-server-state');
const selectedServerSummary = document.querySelector('#selected-server-summary');
const selectedServerCapabilities = document.querySelector('#selected-server-capabilities');
const configurationContext = document.querySelector('#configuration-context');
const commentPreservationState = document.querySelector('#comment-preservation-state');
const topology = document.querySelector('#topology');
const nodes = document.querySelector('#nodes');
const refresh = document.querySelector('#refresh');
const previousPage = document.querySelector('#previous-page');
const nextPage = document.querySelector('#next-page');
const pageNumber = document.querySelector('#page-number');
const sendAll = document.querySelector('#send-all');
const blockedServers = document.querySelector('#blocked-servers');
const readConfiguration = document.querySelector('#read-configuration');
const previewConfiguration = document.querySelector('#preview-configuration');
const applyConfiguration = document.querySelector('#apply-configuration');
const operationStatus = document.querySelector('#operation-status');
const configurationForm = document.querySelector('#configuration-form');
const configurationFile = document.querySelector('#configuration-file');
const configurationContent = document.querySelector('#configuration-content');
const editorPosition = document.querySelector('#editor-position');
const readFileConfiguration = document.querySelector('#read-file-configuration');
const previewFileConfiguration = document.querySelector('#preview-file-configuration');
const applyFileConfiguration = document.querySelector('#apply-file-configuration');
const fileOperationStatus = document.querySelector('#file-operation-status');
const fileConfigurationForm = document.querySelector('#file-configuration-form');
const quickSetupForm = document.querySelector('#quick-setup-form');
const quickPreset = document.querySelector('#quick-preset');
const quickName = document.querySelector('#quick-name');
const quickService = document.querySelector('#quick-service');
const quickUrl = document.querySelector('#quick-url');
const quickDelay = document.querySelector('#quick-delay');
const detectedPlugins = document.querySelector('#detected-plugins');
const quickRewardScope = document.querySelector('#quick-reward-scope');
const quickCommand = document.querySelector('#quick-command');
const quickMessage = document.querySelector('#quick-message');
const quickCommandSuggestions = document.querySelector('#quick-command-suggestions');
const quickProcessRewards = document.querySelector('#quick-process-rewards');
const quickAutoSites = document.querySelector('#quick-auto-sites');
const quickExtraCheck = document.querySelector('#quick-extra-check');
const quickCountFake = document.querySelector('#quick-count-fake');
const quickHideSiteWarning = document.querySelector('#quick-hide-site-warning');
const quickDisableUpdates = document.querySelector('#quick-disable-updates');
const quickPartyVotes = document.querySelector('#quick-party-votes');
const quickPartyCommand = document.querySelector('#quick-party-command');
const quickPartyBroadcast = document.querySelector('#quick-party-broadcast');
const quickPartyAll = document.querySelector('#quick-party-all');
const quickPartyOnline = document.querySelector('#quick-party-online');
const previewQuickSetup = document.querySelector('#preview-quick-setup');
const applyQuickSetup = document.querySelector('#apply-quick-setup');
const quickOperationStatus = document.querySelector('#quick-operation-status');
const voteSitesSource = document.querySelector('#vote-sites-source');
const voteSitesTargets = document.querySelector('#vote-sites-targets');
const voteSitesSyncCapability = document.querySelector('#vote-sites-sync-capability');
const previewVoteSitesSync = document.querySelector('#preview-vote-sites-sync');
const applyVoteSitesSync = document.querySelector('#apply-vote-sites-sync');
const voteSitesSyncStatus = document.querySelector('#vote-sites-sync-status');
const transportTestProxy = document.querySelector('#transport-test-proxy');
const transportTestBackend = document.querySelector('#transport-test-backend');
const transportTestCapability = document.querySelector('#transport-test-capability');
const runTransportTest = document.querySelector('#run-transport-test');
const transportTestStatus = document.querySelector('#transport-test-status');
const proxyMethodProxy = document.querySelector('#proxy-method-proxy');
const proxyMethodCapability = document.querySelector('#proxy-method-capability');
const proxyMethodButtons = [...document.querySelectorAll('[data-proxy-method]')];
const proxyMethodStatus = document.querySelector('#proxy-method-status');
const enrollmentCard = document.querySelector('#enrollment-card');
const enrollmentForm = document.querySelector('#enrollment-form');
const enrollmentSubmit = enrollmentForm.querySelector('button[type="submit"]');
const enrollmentNodeId = document.querySelector('#enrollment-node-id');
const enrollmentCredential = document.querySelector('#enrollment-credential');
const enrollmentList = document.querySelector('#enrollment-list');
const enrollmentMessage = document.querySelector('#enrollment-message');
const refreshEnrollments = document.querySelector('#refresh-enrollments');
const PAGE_SIZE = 100;
let authenticated = false;
let csrfToken = '';
let pageOffset = 0;
let selectedNodes = new Set();
let selectedServerId = '';
let visibleNodeItems = [];
let allNodeItems = [];
let nodeIndex = new Map();
let enrollmentIds = new Set();
let approvedPreview = null;
let approvedFilePreview = null;
let approvedQuickPreview = null;
let approvedVoteSitesPreview = null;
let voteSitesSourceId = '';
let voteSitesTargetIds = new Set();
let voteSitesTargetsInitialized = false;
let transportTestProxyId = '';
let transportTestBackendId = '';
let proxyMethodProxyId = '';
let backendTopologyTruncated = false;
let nodeCapabilities = new Map();
let nodePlugins = new Map();
let inputGeneration = 0;
let authenticationGeneration = 0;
let loginInFlight = false;
let setupRequired = false;
let enrollmentInFlight = false;
let enrollmentRefreshRequested = false;
let enrollmentMutationInFlight = false;
let configurationOperationsInFlight = 0;

function text(element, value) {
  element.textContent = value;
  return element;
}

async function loadHealth() {
  try {
    const response = await fetch('/api/v1/health', {cache: 'no-store'});
    if (!response.ok) throw new Error('health');
    const body = await response.json();
    text(health, `Online · ${body.identity.applicationVersion} · protocol ${body.identity.protocolVersion}`);
    health.classList.add('online');
  } catch (_) {
    text(health, 'Unavailable');
    health.classList.remove('online');
  }
}

async function loadSetupState() {
  try {
    const response = await fetch('/api/v1/auth/setup', {cache: 'no-store'});
    if (!response.ok) throw new Error('First-run setup status is unavailable.');
    const body = await response.json();
    setupRequired = Boolean(body.required);
    setupCard.hidden = !setupRequired;
    authCard.hidden = setupRequired;
    if (setupRequired) {
      enrollmentCard.hidden = true;
      text(setupMessage, `Enter the one-time code from ${body.codeFile} inside the configured hosted data directory.`);
    }
    return setupRequired;
  } catch (error) {
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    text(message, error.message || 'First-run setup status is unavailable.');
    return false;
  }
}

function applyAuthenticatedSession(body) {
  authenticated = true;
  csrfToken = body.csrfToken;
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  approvedVoteSitesPreview = null;
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  configurationContent.value = '';
  inputGeneration++;
  logout.hidden = false;
  authCard.hidden = true;
  welcome.hidden = true;
  appShell.hidden = false;
  serverPickerLabel.hidden = false;
  enrollmentCard.hidden = false;
  pageOffset = 0;
  setActiveTab(tabFromHash());
}

function isProxy(node) {
  return ['VELOCITY', 'BUNGEECORD'].includes(String(node.platform).toUpperCase()) ||
    node.acceptedCapabilities.includes('config.proxy-routing.v1');
}

function isBackend(node) {
  return !isProxy(node);
}

function roleLabel(node) {
  return isProxy(node) ? 'Proxy' : 'Backend';
}

function platformLabel(platform) {
  const normalized = String(platform || '').toUpperCase();
  if (normalized === 'BUKKIT') return 'Bukkit';
  if (normalized === 'BUNGEECORD') return 'BungeeCord';
  if (normalized === 'VELOCITY') return 'Velocity';
  return platform || 'Unspecified platform';
}

function friendlyCapability(capability) {
  return ({
    'config.files.v1': 'Full configuration',
    'config.file-comments.v1': 'Comments preserved',
    'config.vote-sites-sync.v1': 'VoteSites sync',
    'config.transport-test.v1': 'Communication test',
    'config.proxy-method.v1': 'Proxy method',
    'config.quick-setup.v1': 'Quick Setup',
    'config.proxy-routing.v1': 'Proxy routing'
  })[capability];
}

function managedCapabilities(node) {
  return node.acceptedCapabilities.map(friendlyCapability).filter(Boolean);
}

function proxyReportsFor(backendId) {
  return allNodeItems.filter(isProxy).filter(proxy =>
    (Array.isArray(proxy.backends) ? proxy.backends : []).some(backend => backend.backendId === backendId));
}

function backendCard(backend) {
  const item = document.createElement('li');
  const title = text(document.createElement('strong'), backend.displayName);
  const details = document.createElement('div');
  details.className = 'backend-state';
  const registered = nodeIndex.get(backend.backendId);
  details.append(text(document.createElement('span'), enrollmentIds.has(backend.backendId)
    ? `Enrolled in Control · ${registered ? (registered.online ? 'Control connected' : 'Control disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (backend.presenceKnown) {
    details.append(text(document.createElement('span'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    details.append(text(document.createElement('span'), 'Presence not available'));
  }
  details.append(text(document.createElement('span'), backend.backendId));
  item.append(title, details);
  return item;
}

function nodeCard(node) {
  const article = document.createElement('article');
  article.className = `node${selectedServerId === node.nodeId ? ' selected' : ''}`;
  article.dataset.nodeId = node.nodeId;
  const header = document.createElement('div');
  header.className = 'node-header';
  const identity = document.createElement('div');
  const title = text(document.createElement('h3'), node.displayName);
  identity.append(title, text(document.createElement('span'), node.nodeId));
  identity.lastElementChild.className = 'node-id';
  const state = text(document.createElement('span'), node.online ? 'Control connected' : 'Control disconnected');
  state.className = `pill ${node.online ? 'online' : 'offline'}`;
  header.append(identity, state);

  const meta = document.createElement('div');
  meta.className = 'node-meta';
  [roleLabel(node), platformLabel(node.platform), `VotingPlugin ${node.pluginVersion}`,
    ...managedCapabilities(node)].forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill neutral';
    meta.append(pill);
  });

  const plugins = Array.isArray(node.detectedPlugins) ? node.detectedPlugins : [];
  let detail;
  if (isBackend(node)) {
    detail = text(document.createElement('p'), plugins.length
      ? `Detected plugins: ${plugins.join(', ')}` : 'Plugin inventory is not available for this backend.');
  } else {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    detail = text(document.createElement('p'), `${backends.length} configured ${backends.length === 1 ? 'backend' : 'backends'} reported.`);
  }
  detail.className = 'node-detail';

  const selector = document.createElement('label');
  selector.className = 'node-select';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  const controllable = ['config.proxy-routing.v1', 'config.files.v1', 'config.quick-setup.v1']
    .some(capability => node.acceptedCapabilities.includes(capability));
  checkbox.disabled = !node.online || !controllable;
  checkbox.checked = selectedNodes.has(node.nodeId) && !checkbox.disabled;
  checkbox.addEventListener('change', () => {
    if (checkbox.checked) selectedNodes.add(node.nodeId); else selectedNodes.delete(node.nodeId);
    approvedPreview = null;
    approvedFilePreview = null;
    approvedQuickPreview = null;
    inputGeneration++;
    updatePluginSuggestions();
    updateConfigurationButtons();
  });
  selector.append(checkbox, document.createTextNode('Include in configuration changes'));

  const list = document.createElement('ul');
  list.className = 'node-backends';
  if (isProxy(node)) {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    if (backends.length === 0) {
      list.append(text(document.createElement('li'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => list.append(backendCard(backend)));
    }
  } else {
    const proxies = proxyReportsFor(node.nodeId);
    list.append(text(document.createElement('li'), proxies.length
      ? `Reported by ${proxies.map(proxy => proxy.displayName).join(', ')}.`
      : 'No connected proxy reports this backend ID.'));
  }
  article.append(header, meta, detail, list, selector);
  return article;
}

function tabFromHash() {
  const requested = window.location.hash.replace(/^#/, '');
  return tabPanels.some(panel => panel.dataset.panel === requested) ? requested : 'overview';
}

function setActiveTab(tab, updateHash = false) {
  if (!tabPanels.some(panel => panel.dataset.panel === tab)) tab = 'overview';
  tabButtons.forEach(button => button.setAttribute('aria-selected', String(button.dataset.tab === tab)));
  tabPanels.forEach(panel => { panel.hidden = panel.dataset.panel !== tab; });
  if (updateHash && window.location.hash !== `#${tab}`) window.history.replaceState(null, '', `#${tab}`);
}

function setConfigView(view) {
  if (!configViewPanels.some(panel => panel.dataset.configPanel === view)) view = 'easy';
  configViewButtons.forEach(button => button.classList.toggle('active', button.dataset.configView === view));
  configViewPanels.forEach(panel => { panel.hidden = panel.dataset.configPanel !== view; });
}

function chooseDefaultServer(items) {
  return items.find(node => isBackend(node) && node.online) || items.find(node => isBackend(node)) ||
    items.find(node => node.online) || items[0] || null;
}

function renderServerPicker() {
  const previousValue = selectedServerId;
  const ordered = [...allNodeItems].sort((left, right) => {
    const roleOrder = Number(isProxy(left)) - Number(isProxy(right));
    return roleOrder || left.displayName.localeCompare(right.displayName);
  });
  const placeholder = text(document.createElement('option'), ordered.length ? 'Choose a server' : 'No servers registered');
  placeholder.value = '';
  serverPicker.replaceChildren(placeholder, ...ordered.map(node => {
    const option = text(document.createElement('option'),
      `${node.displayName} · ${roleLabel(node)} · ${node.online ? 'connected' : 'disconnected'}`);
    option.value = node.nodeId;
    return option;
  }));
  if (!nodeIndex.has(previousValue)) selectedServerId = chooseDefaultServer(ordered)?.nodeId || '';
  serverPicker.value = selectedServerId;
}

function renderSelectedServer() {
  const selected = nodeIndex.get(selectedServerId);
  selectedServerCapabilities.replaceChildren();
  if (!selected) {
    text(selectedServerName, 'Choose a VotingPlugin server');
    text(selectedServerState, 'No selection');
    selectedServerState.className = 'pill neutral';
    text(selectedServerSummary, 'Use the server picker above to keep configuration and setup actions focused on one node.');
    text(configurationContext, 'Choose a backend from the server picker to work with its VotingPlugin configuration.');
    text(commentPreservationState, 'Comment support unknown');
    commentPreservationState.className = 'pill warning';
    return;
  }
  text(selectedServerName, selected.displayName);
  text(selectedServerState, selected.online ? 'Control connected' : 'Control disconnected');
  selectedServerState.className = `pill ${selected.online ? 'online' : 'offline'}`;
  const relationships = isBackend(selected) ? proxyReportsFor(selected.nodeId) : [];
  const relationshipText = relationships.length
    ? ` Reported by ${relationships.map(proxy => proxy.displayName).join(', ')}.` : '';
  text(selectedServerSummary,
    `${roleLabel(selected)} · ${platformLabel(selected.platform)} · VotingPlugin ${selected.pluginVersion}.${relationshipText}`);
  text(configurationContext,
    `${selected.displayName} (${selected.nodeId}) · ${roleLabel(selected)} · ${selected.online ? 'Control connected' : 'Control disconnected'}`);
  const preservesComments = selected.acceptedCapabilities.includes('config.file-comments.v1');
  text(commentPreservationState, preservesComments ? 'Comments preserved' : 'Comments not guaranteed');
  commentPreservationState.className = `pill ${preservesComments ? 'online' : 'warning'}`;
  const capabilities = managedCapabilities(selected);
  if (capabilities.length === 0) capabilities.push('Discovery only');
  capabilities.forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill';
    selectedServerCapabilities.append(pill);
  });
}

function topologyLink(backend) {
  const registered = nodeIndex.get(backend.backendId);
  const link = document.createElement('span');
  link.className = `topology-link ${registered?.online ? 'online' : 'warning'}`;
  link.append(text(document.createElement('strong'), backend.displayName));
  link.append(text(document.createElement('small'), enrollmentIds.has(backend.backendId)
    ? `Enrolled · Control ${registered ? (registered.online ? 'connected' : 'disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (backend.presenceKnown) {
    link.append(text(document.createElement('small'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    link.append(text(document.createElement('small'), 'Presence not available'));
  }
  return link;
}

function renderTopology() {
  const proxies = allNodeItems.filter(isProxy);
  topology.replaceChildren();
  topology.classList.toggle('empty', proxies.length === 0);
  if (proxies.length === 0) {
    text(topology, 'No proxy nodes have registered with Control.');
    return;
  }
  proxies.forEach(proxy => {
    const row = document.createElement('article');
    row.className = 'topology-row';
    const proxyIdentity = document.createElement('div');
    proxyIdentity.className = 'topology-proxy';
    proxyIdentity.append(text(document.createElement('strong'), proxy.displayName));
    proxyIdentity.append(text(document.createElement('small'),
      `${platformLabel(proxy.platform)} proxy · Control ${proxy.online ? 'connected' : 'disconnected'}`));
    const backendList = document.createElement('div');
    backendList.className = 'topology-backends';
    const backends = Array.isArray(proxy.backends) ? proxy.backends : [];
    if (backends.length === 0) {
      backendList.append(text(document.createElement('small'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => backendList.append(topologyLink(backend)));
    }
    row.append(proxyIdentity, backendList);
    topology.append(row);
  });
}

function renderMetrics() {
  const backendIds = new Set(allNodeItems.filter(isProxy).flatMap(node =>
    (Array.isArray(node.backends) ? node.backends : []).map(backend => backend.backendId)));
  const unenrolledBackends = [...backendIds].filter(backendId => !enrollmentIds.has(backendId)).length;
  text(metricNodes, allNodeItems.length);
  text(metricOnline, allNodeItems.filter(node => node.online).length);
  text(metricBackends, backendIds.size);
  text(metricIssues, allNodeItems.filter(node => !node.online).length + unenrolledBackends);
}

function syncSourceCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.files.v1') &&
    node.acceptedCapabilities.includes('config.file-comments.v1'));
}

function syncTargetCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.vote-sites-sync.v1'));
}

function selectedVoteSitesTargets() {
  const capable = new Set(syncTargetCandidates().map(node => node.nodeId));
  return [...voteSitesTargetIds].filter(nodeId => capable.has(nodeId) && nodeId !== voteSitesSourceId);
}

function renderVoteSitesSync() {
  const sources = syncSourceCandidates();
  const targetsAvailable = syncTargetCandidates();
  if (!sources.some(node => node.nodeId === voteSitesSourceId)) {
    voteSitesSourceId = sources.find(node => node.nodeId === selectedServerId)?.nodeId || sources[0]?.nodeId || '';
  }
  const targetIds = new Set(targetsAvailable.map(node => node.nodeId));
  const retainedTargets = new Set([...voteSitesTargetIds].filter(nodeId =>
    targetIds.has(nodeId) && nodeId !== voteSitesSourceId));
  if (retainedTargets.size !== voteSitesTargetIds.size) {
    approvedVoteSitesPreview = null;
    inputGeneration++;
    text(voteSitesSyncStatus, 'A sync target became unavailable. Preview again before syncing.');
  }
  voteSitesTargetIds = retainedTargets;
  if (!voteSitesTargetsInitialized && sources.length > 0) {
    voteSitesTargetIds = new Set(targetsAvailable.map(node => node.nodeId)
      .filter(nodeId => nodeId !== voteSitesSourceId));
    voteSitesTargetsInitialized = true;
  }

  const placeholder = text(document.createElement('option'), sources.length ? 'Choose a source' : 'No readable backends');
  placeholder.value = '';
  voteSitesSource.replaceChildren(placeholder, ...sources.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  voteSitesSource.value = voteSitesSourceId;

  voteSitesTargets.replaceChildren();
  const choices = targetsAvailable.filter(node => node.nodeId !== voteSitesSourceId);
  voteSitesTargets.classList.toggle('empty', choices.length === 0);
  if (choices.length === 0) {
    text(voteSitesTargets, targetsAvailable.length > 0
      ? 'Choose a different source or enroll another backend.' : 'No sync-capable target backends are connected.');
  } else {
    choices.forEach(node => {
      const label = document.createElement('label');
      label.className = 'target-option';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = voteSitesTargetIds.has(node.nodeId);
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) voteSitesTargetIds.add(node.nodeId); else voteSitesTargetIds.delete(node.nodeId);
        approvedVoteSitesPreview = null;
        inputGeneration++;
        text(voteSitesSyncStatus, 'Targets changed. Read the source and preview again before syncing.');
        updateConfigurationButtons();
      });
      label.append(checkbox, document.createTextNode(`${node.displayName} · ${node.nodeId}`));
      voteSitesTargets.append(label);
    });
  }
  const readyCount = targetsAvailable.length;
  text(voteSitesSyncCapability, readyCount > 0
    ? `${readyCount} sync-capable ${readyCount === 1 ? 'backend' : 'backends'}` : 'Waiting for capable backends');
  voteSitesSyncCapability.className = `pill ${readyCount > 0 ? 'online' : 'neutral'}`;
}

function transportTestProxies() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    node.acceptedCapabilities.includes('config.transport-test.v1'));
}

function renderTransportTest() {
  const proxies = transportTestProxies();
  if (!proxies.some(node => node.nodeId === transportTestProxyId)) {
    transportTestProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
    transportTestBackendId = '';
  }
  const proxy = proxies.find(node => node.nodeId === transportTestProxyId);
  const backends = Array.isArray(proxy?.backends) ? proxy.backends : [];
  if (!backends.some(backend => backend.backendId === transportTestBackendId)) {
    transportTestBackendId = backends[0]?.backendId || '';
  }

  const proxyPlaceholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  proxyPlaceholder.value = '';
  transportTestProxy.replaceChildren(proxyPlaceholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  transportTestProxy.value = transportTestProxyId;

  const backendPlaceholder = text(document.createElement('option'), backends.length ? 'Choose a backend' : 'No backends reported');
  backendPlaceholder.value = '';
  transportTestBackend.replaceChildren(backendPlaceholder, ...backends.map(backend => {
    const option = text(document.createElement('option'), `${backend.displayName} · ${backend.backendId}`);
    option.value = backend.backendId;
    return option;
  }));
  transportTestBackend.value = transportTestBackendId;

  text(transportTestCapability, proxies.length > 0
    ? `${proxies.length} test-capable ${proxies.length === 1 ? 'proxy' : 'proxies'}` : 'Waiting for a capable proxy');
  transportTestCapability.className = `pill ${proxies.length > 0 ? 'online' : 'neutral'}`;
}

function proxyMethodCandidates() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    node.acceptedCapabilities.includes('config.proxy-method.v1'));
}

function proxyMethodNetwork() {
  const proxy = nodeIndex.get(proxyMethodProxyId);
  const reported = Array.isArray(proxy?.backends) ? proxy.backends : [];
  const backends = reported.map(backend => nodeIndex.get(backend.backendId)).filter(Boolean);
  const unavailable = reported.filter(backend => {
    const node = nodeIndex.get(backend.backendId);
    return !node || !node.online || !node.acceptedCapabilities.includes('config.proxy-method.v1');
  });
  return {proxy, reported, backends, unavailable, topologyComplete: !backendTopologyTruncated,
    nodeIds: proxy ? [proxy.nodeId, ...backends.map(node => node.nodeId)] : []};
}

function renderProxyMethod() {
  const proxies = proxyMethodCandidates();
  if (!proxies.some(node => node.nodeId === proxyMethodProxyId)) {
    proxyMethodProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
  }
  const placeholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  placeholder.value = '';
  proxyMethodProxy.replaceChildren(placeholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  proxyMethodProxy.value = proxyMethodProxyId;
  const network = proxyMethodNetwork();
  const ready = Boolean(network.proxy) && network.topologyComplete && network.reported.length > 0 && network.unavailable.length === 0;
  const description = !network.proxy ? 'Waiting for a capable proxy'
    : !network.topologyComplete ? 'Backend topology is truncated; switching disabled'
    : network.reported.length === 0 ? 'No backends reported'
    : network.unavailable.length > 0 ? `${network.unavailable.length} backends unavailable`
    : `${network.nodeIds.length} nodes ready`;
  text(proxyMethodCapability, description);
  proxyMethodCapability.className = `pill ${ready ? 'online' : 'neutral'}`;
}

function renderNodeViews() {
  nodes.replaceChildren();
  nodes.classList.toggle('empty', visibleNodeItems.length === 0);
  if (visibleNodeItems.length === 0) {
    text(nodes, pageOffset === 0 ? 'No VotingPlugin nodes have registered yet.' : 'No nodes on this page.');
  } else {
    visibleNodeItems.forEach(node => nodes.append(nodeCard(node)));
  }
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  renderVoteSitesSync();
  renderTransportTest();
  renderProxyMethod();
}

function selectPrimaryServer(nodeId) {
  if (nodeId && !nodeIndex.has(nodeId)) return;
  selectedServerId = nodeId;
  serverPicker.value = nodeId;
  selectedNodes.clear();
  if (nodeId) selectedNodes.add(nodeId);
  clearApprovals();
  updatePluginSuggestions();
  renderNodeViews();
}

function updateConfigurationButtons(busy = configurationOperationsInFlight > 0) {
  const routingReady = authenticated && targets('config.proxy-routing.v1').length > 0 && !busy;
  const fileReady = authenticated && targets('config.files.v1').length > 0 && !busy;
  const quickReady = authenticated && targets('config.quick-setup.v1').length > 0 && !busy;
  const voteSitesReady = authenticated && voteSitesSourceId && selectedVoteSitesTargets().length > 0 && !busy;
  readConfiguration.disabled = !routingReady;
  previewConfiguration.disabled = !routingReady;
  applyConfiguration.disabled = !routingReady || !approvedPreview;
  readFileConfiguration.disabled = !fileReady;
  previewFileConfiguration.disabled = !fileReady || !configurationContent.value;
  applyFileConfiguration.disabled = !fileReady || !approvedFilePreview;
  previewQuickSetup.disabled = !quickReady;
  applyQuickSetup.disabled = !quickReady || !approvedQuickPreview;
  previewVoteSitesSync.disabled = !voteSitesReady;
  applyVoteSitesSync.disabled = !voteSitesReady || !approvedVoteSitesPreview;
  runTransportTest.disabled = !authenticated || !transportTestProxyId || !transportTestBackendId || busy;
  const methodNetwork = proxyMethodNetwork();
  const methodReady = authenticated && Boolean(methodNetwork.proxy) && methodNetwork.topologyComplete && methodNetwork.reported.length > 0 &&
    methodNetwork.unavailable.length === 0 && !busy;
  proxyMethodButtons.forEach(button => { button.disabled = !methodReady; });
}

function targets(capability) {
  return [...selectedNodes].filter(node => nodeCapabilities.get(node)?.includes(capability));
}

function clearApprovals() {
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  approvedVoteSitesPreview = null;
  approvedVoteSitesPreview = null;
  inputGeneration++;
  updateConfigurationButtons();
}

function updateEditorPosition() {
  const beforeCursor = configurationContent.value.slice(0, configurationContent.selectionStart);
  const lines = beforeCursor.split('\n');
  text(editorPosition, `Line ${lines.length}, Column ${lines.at(-1).length + 1}`);
}

function handleEditorKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    if (!previewFileConfiguration.disabled) previewFileConfiguration.click();
    return;
  }
  if (event.key !== 'Tab') return;
  event.preventDefault();
  const start = configurationContent.selectionStart;
  const end = configurationContent.selectionEnd;
  if (event.shiftKey) {
    const lineStart = configurationContent.value.lastIndexOf('\n', start - 1) + 1;
    const removable = configurationContent.value.slice(lineStart, lineStart + 2).match(/^ {1,2}/)?.[0].length || 0;
    if (removable > 0) {
      configurationContent.setRangeText('', lineStart, lineStart + removable, 'preserve');
      configurationContent.setSelectionRange(Math.max(lineStart, start - removable), Math.max(lineStart, end - removable));
    }
  } else {
    configurationContent.setRangeText('  ', start, end, 'end');
  }
  configurationContent.dispatchEvent(new Event('input', {bubbles: true}));
  updateEditorPosition();
}

function updateQuickFields() {
  document.querySelectorAll('.quick-fields').forEach(group => {
    group.hidden = !group.dataset.presets.split(' ').includes(quickPreset.value);
  });
  quickName.closest('.quick-fields').hidden = !['proxy-backend', 'vote-site', 'easy-reward'].includes(quickPreset.value);
}

function updatePluginSuggestions() {
  const plugins = new Set(targets('config.quick-setup.v1').flatMap(node => nodePlugins.get(node) || [])
    .map(name => name.toLowerCase()));
  const names = [...plugins].sort();
  text(detectedPlugins, names.length ? `Detected on selected nodes: ${names.join(', ')}`
    : 'No plugin inventory is available for the selected nodes; generic commands are still available.');
  const suggestions = [
    ['give %player% diamond 1', 'Minecraft item'],
    ['xp add %player% 5 levels', 'Minecraft experience']
  ];
  if ([...plugins].some(name => name === 'essentials' || name === 'essentialsx')) {
    suggestions.push(['eco give %player% 100', 'Essentials economy']);
  }
  if (plugins.has('cmi')) suggestions.push(['money give %player% 100', 'CMI economy']);
  if (plugins.has('luckperms')) {
    suggestions.push(['lp user %player% permission set example.permission true', 'LuckPerms permission']);
  }
  quickCommandSuggestions.replaceChildren(...suggestions.map(([value, label]) => {
    const option = document.createElement('option');
    option.value = value;
    option.label = label;
    return option;
  }));
}

async function authorized(path, options = {}) {
  const requestGeneration = authenticationGeneration;
  const method = (options.method || 'GET').toUpperCase();
  const response = await fetch(path, {
    cache: 'no-store',
    credentials: 'same-origin',
    ...options,
    headers: {...(options.headers || {}), ...(method === 'GET' ? {} : {'X-CSRF-Token': csrfToken})}
  });
  const body = response.status === 204 ? null : await response.json();
  if (response.status === 401 && requestGeneration === authenticationGeneration) {
    discardAuthenticationState('Session expired. Sign in again.');
  }
  if (requestGeneration !== authenticationGeneration) {
    throw new Error('Authentication changed while the request was running. Try again.');
  }
  if (!response.ok) throw new Error(body?.error?.message || `Control request failed (${response.status}).`);
  return body;
}

function discardAuthenticationState(reason) {
  authenticationGeneration++;
  authenticated = false;
  csrfToken = '';
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  approvedVoteSitesPreview = null;
  inputGeneration++;
  logout.hidden = true;
  appShell.hidden = true;
  serverPickerLabel.hidden = true;
  welcome.hidden = false;
  authCard.hidden = setupRequired;
  enrollmentCard.hidden = true;
  enrollmentCredential.value = '';
  enrollmentList.replaceChildren(text(document.createElement('li'), 'Authenticate to manage enrollments.'));
  text(enrollmentMessage, '');
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  backendTopologyTruncated = false;
  proxyMethodProxyId = '';
  selectedServerId = '';
  visibleNodeItems = [];
  allNodeItems = [];
  enrollmentIds.clear();
  nodeIndex.clear();
  nodeCapabilities.clear();
  nodePlugins.clear();
  configurationForm.reset();
  fileConfigurationForm.reset();
  quickSetupForm.reset();
  updateQuickFields();
  quickCommandSuggestions.replaceChildren();
  text(detectedPlugins, 'Authenticate to inspect detected plugins.');
  text(operationStatus, '');
  text(fileOperationStatus, '');
  text(quickOperationStatus, '');
  text(voteSitesSyncStatus, '');
  text(transportTestStatus, '');
  text(proxyMethodStatus, '');
  nodes.replaceChildren();
  nodes.classList.add('empty');
  text(nodes, 'Authenticate to view the network.');
  serverPicker.replaceChildren(text(document.createElement('option'), 'Choose a server'));
  serverPicker.firstElementChild.value = '';
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  text(message, reason);
  updateConfigurationButtons();
}

function proposal() {
  return {
    sendVotesToAllServers: sendAll.checked,
    blockedServers: blockedServers.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean)
  };
}

function operationSummary(operation) {
  const lines = [`${operation.type} · ${operation.state} · ${operation.operationId}`];
  Object.entries(operation.nodeStates).forEach(([node, state]) => {
    const result = operation.results[node];
    lines.push(`${node}: ${result ? `${result.success ? 'success' : result.code} — ${result.message}` : state}`);
    if (result?.changes?.length) result.changes.forEach(change => lines.push(`  ${change}`));
    if (result?.rolledBack) lines.push('  previous file restored after reload failure');
  });
  return lines.join('\n');
}

async function waitForOperation(operation, statusElement = operationStatus) {
  text(statusElement, operationSummary(operation));
  while (operation.state === 'RUNNING') {
    await new Promise(resolve => window.setTimeout(resolve, 1500));
    operation = await authorized(`/api/v1/operations/${operation.operationId}`);
    text(statusElement, operationSummary(operation));
  }
  return operation;
}

async function startConfigurationOperation(path, body, statusElement = operationStatus) {
  configurationOperationsInFlight++;
  updateConfigurationButtons();
  try {
    return await waitForOperation(await authorized(path, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
    }), statusElement);
  } finally {
    configurationOperationsInFlight--;
    updateConfigurationButtons();
  }
}

async function loadEnrollments() {
  if (!authenticated) return;
  if (enrollmentInFlight) {
    enrollmentRefreshRequested = true;
    return;
  }
  enrollmentInFlight = true;
  refreshEnrollments.disabled = true;
  try {
    const body = await authorized('/api/v1/enrollments');
    enrollmentIds = new Set(Array.isArray(body.nodeIds) ? body.nodeIds : []);
    if (allNodeItems.length) renderNodeViews();
    enrollmentList.replaceChildren();
    if (!Array.isArray(body.nodeIds) || body.nodeIds.length === 0) {
      enrollmentList.append(text(document.createElement('li'), 'No nodes are enrolled yet.'));
      return;
    }
    body.nodeIds.forEach(nodeId => {
      const item = document.createElement('li');
      item.className = 'enrollment-item';
      item.append(text(document.createElement('code'), nodeId));
      const revoke = text(document.createElement('button'), 'Revoke');
      revoke.type = 'button';
      revoke.addEventListener('click', async () => {
        if (enrollmentMutationInFlight || !window.confirm(`Revoke the credential for ${nodeId}?`)) return;
        enrollmentMutationInFlight = true;
        revoke.disabled = true;
        enrollmentSubmit.disabled = true;
        try {
          await authorized(`/api/v1/enrollments/${encodeURIComponent(nodeId)}`, {method: 'DELETE'});
          enrollmentCredential.value = '';
          text(enrollmentMessage, `${nodeId} was revoked.`);
          await loadEnrollments();
        } catch (error) {
          text(enrollmentMessage, error.message);
        } finally {
          enrollmentMutationInFlight = false;
          enrollmentSubmit.disabled = false;
        }
      });
      item.append(revoke);
      enrollmentList.append(item);
    });
  } catch (error) {
    text(enrollmentMessage, error.message || 'Enrollments could not be loaded.');
  } finally {
    enrollmentInFlight = false;
    refreshEnrollments.disabled = false;
    const refreshAgain = enrollmentRefreshRequested && authenticated;
    enrollmentRefreshRequested = false;
    if (refreshAgain) await loadEnrollments();
  }
}

async function loadAllNodes() {
  const items = [];
  let truncated = false;
  for (let offset = 0; ; offset += PAGE_SIZE) {
    const page = await authorized(`/api/v1/nodes?offset=${offset}&limit=${PAGE_SIZE}`);
    items.push(...page.items);
    truncated ||= Boolean(page.backendItemsTruncated);
    if (page.items.length < PAGE_SIZE) return {items, truncated};
  }
}

async function loadNodes() {
  if (!authenticated) return;
  refresh.disabled = true;
  previousPage.disabled = true;
  nextPage.disabled = true;
  text(message, 'Loading…');
  try {
    const [body, registry] = await Promise.all([
      authorized(`/api/v1/nodes?offset=${pageOffset}&limit=${PAGE_SIZE}`),
      loadAllNodes()
    ]);
    visibleNodeItems = body.items;
    allNodeItems = registry.items;
    backendTopologyTruncated = registry.truncated;
    nodeIndex = new Map(registry.items.map(node => [node.nodeId, node]));
    const needsDefaultTargets = !selectedServerId || !nodeIndex.has(selectedServerId);
    const previousCapabilities = nodeCapabilities;
    nodeCapabilities = new Map(registry.items.map(node => [node.nodeId, node.online ? node.acceptedCapabilities : []]));
    nodePlugins = new Map(registry.items.map(node => [node.nodeId, node.online && Array.isArray(node.detectedPlugins)
      ? node.detectedPlugins : []]));
    const selectedCapabilitiesChanged = [...selectedNodes].some(node =>
      ['config.proxy-routing.v1', 'config.files.v1', 'config.quick-setup.v1'].some(capability =>
        Boolean(previousCapabilities.get(node)?.includes(capability)) !==
          Boolean(nodeCapabilities.get(node)?.includes(capability))));
    if (selectedCapabilitiesChanged) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      inputGeneration++;
      text(operationStatus, 'A selected node changed capabilities during refresh. Preview again before apply.');
    }
    const invalidRoutingApproval = approvedPreview && !approvedPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.proxy-routing.v1'));
    const invalidFileApproval = approvedFilePreview && !approvedFilePreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.files.v1'));
    const invalidQuickApproval = approvedQuickPreview && !approvedQuickPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.quick-setup.v1'));
    const invalidVoteSitesApproval = approvedVoteSitesPreview &&
      (!approvedVoteSitesPreview.nodeIds.every(node =>
        nodeCapabilities.get(node)?.includes('config.vote-sites-sync.v1')) ||
       !nodeCapabilities.get(approvedVoteSitesPreview.sourceId)?.includes('config.file-comments.v1'));
    if (invalidRoutingApproval || invalidFileApproval || invalidQuickApproval || invalidVoteSitesApproval) {
      if (invalidRoutingApproval) approvedPreview = null;
      if (invalidFileApproval) approvedFilePreview = null;
      if (invalidQuickApproval) approvedQuickPreview = null;
      if (invalidVoteSitesApproval) approvedVoteSitesPreview = null;
      inputGeneration++;
      text(operationStatus, 'A preview target went offline or lost the required capability. Preview again before apply.');
    }
    const visibleIds = new Set(registry.items.filter(node => node.online && node.acceptedCapabilities.some(value => value.startsWith('config.')))
      .map(node => node.nodeId));
    const filteredSelection = new Set([...selectedNodes].filter(node => visibleIds.has(node)));
    if (filteredSelection.size !== selectedNodes.size) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      inputGeneration++;
      text(operationStatus, 'The selected nodes changed during refresh. Preview again before apply.');
    }
    selectedNodes = filteredSelection;
    renderServerPicker();
    if (needsDefaultTargets && selectedServerId &&
        nodeCapabilities.get(selectedServerId)?.some(value => value.startsWith('config.'))) {
      selectedNodes.add(selectedServerId);
    }
    renderNodeViews();
    updatePluginSuggestions();
    updateConfigurationButtons();
    const first = body.items.length === 0 ? 0 : pageOffset + 1;
    const last = pageOffset + body.items.length;
    const backendLimit = body.backendItemsTruncated
      ? ` Backend summaries are limited to ${body.backendItemsReturned} entries on this page.` : '';
    text(message, body.items.length === 0 ? 'No nodes on this page.'
      : `Showing nodes ${first}–${last}.${backendLimit}`);
    text(pageNumber, `Page ${Math.floor(pageOffset / PAGE_SIZE) + 1}`);
    previousPage.disabled = pageOffset === 0;
    nextPage.disabled = body.items.length < PAGE_SIZE;
  } catch (error) {
    nodes.replaceChildren();
    nodes.classList.add('empty');
    text(nodes, 'Network data is unavailable.');
    text(message, error.message || 'Control request failed.');
  } finally {
    refresh.disabled = false;
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  loginInFlight = true;
  loginButton.disabled = true;
  logout.disabled = true;
  const loginGeneration = ++authenticationGeneration;
  const password = passwordInput.value;
  passwordInput.value = '';
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify({password})
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'Authentication failed.');
    if (loginGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes()]);
  } catch (error) {
    text(message, error.message || 'Authentication failed.');
  } finally {
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
  }
});

logout.addEventListener('click', async () => {
  if (loginInFlight) return;
  loginInFlight = true;
  const logoutGeneration = ++authenticationGeneration;
  loginButton.disabled = true;
  logout.disabled = true;
  try {
    await authorized('/api/v1/auth/logout', {method: 'POST'});
    if (logoutGeneration === authenticationGeneration) discardAuthenticationState('Signed out.');
  } catch (_) {
    if (logoutGeneration === authenticationGeneration) {
      text(message, 'Sign out could not be confirmed. Check your connection and try again.');
    }
  } finally {
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
  }
});

async function restoreSession() {
  const restoreGeneration = authenticationGeneration;
  try {
    const response = await fetch('/api/v1/auth/session', {cache: 'no-store', credentials: 'same-origin'});
    if (!response.ok || response.status === 204) return;
    const body = await response.json();
    if (restoreGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes()]);
  } catch (_) { /* The login form remains available. */ }
}

setupForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  if (setupPassword.value !== setupConfirmPassword.value) {
    text(setupMessage, 'The new passwords do not match.');
    return;
  }
  loginInFlight = true;
  const setupGeneration = ++authenticationGeneration;
  const request = {setupCode: setupCode.value, password: setupPassword.value};
  setupCode.value = '';
  setupPassword.value = '';
  setupConfirmPassword.value = '';
  try {
    const response = await fetch('/api/v1/auth/setup', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'First-run setup failed.');
    if (setupGeneration !== authenticationGeneration) return;
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    applyAuthenticatedSession(body);
    text(message, 'First-run setup completed.');
    await Promise.all([loadEnrollments(), loadNodes()]);
  } catch (error) {
    text(setupMessage, error.message || 'First-run setup failed.');
  } finally {
    loginInFlight = false;
  }
});

enrollmentForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (!authenticated || enrollmentMutationInFlight) return;
  enrollmentMutationInFlight = true;
  enrollmentSubmit.disabled = true;
  const nodeId = enrollmentNodeId.value.trim();
  enrollmentCredential.value = '';
  try {
    const body = await authorized('/api/v1/enrollments', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({nodeId})
    });
    enrollmentCredential.value = body.credential;
    text(enrollmentMessage,
      `Credential created for ${body.nodeId}. Copy it now; rotating again immediately invalidates this value.`);
    await loadEnrollments();
  } catch (error) {
    text(enrollmentMessage, error.message || 'The credential could not be created.');
  } finally {
    enrollmentMutationInFlight = false;
    enrollmentSubmit.disabled = false;
  }
});
refreshEnrollments.addEventListener('click', loadEnrollments);

readConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {nodeIds: targets('config.proxy-routing.v1')});
    const retained = Object.values(operation.results).find(result => result.success && result.configuration);
    if (retained && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration) {
      sendAll.checked = retained.configuration.sendVotesToAllServers;
      blockedServers.value = retained.configuration.blockedServers.join('\n');
      approvedPreview = null;
      inputGeneration++;
      updateConfigurationButtons();
    }
  } catch (error) { text(operationStatus, error.message); }
});

previewConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.proxy-routing.v1'), configuration: proposal()
    });
    if (operation.state === 'SUCCEEDED' && operation.approvalToken
        && previewGeneration === inputGeneration) {
      approvedPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.proxy-routing.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(operationStatus, 'The targets or proposal changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(operationStatus, error.message); }
});

applyConfiguration.addEventListener('click', async () => {
  if (!approvedPreview || !window.confirm('Apply this exact preview to every selected proxy? Each node may still reject a stale revision.')) return;
  const approval = approvedPreview;
  approvedPreview = null;
  inputGeneration++;
  try {
    await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    });
  } catch (error) { text(operationStatus, error.message); }
});
[sendAll, blockedServers].forEach(field => field.addEventListener('input', () => {
  if (approvedPreview) text(operationStatus, 'The proposal changed. Preview it again before apply.');
  approvedPreview = null;
  inputGeneration++;
  updateConfigurationButtons();
}));

readFileConfiguration.addEventListener('click', async () => {
  approvedFilePreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  const selectedFile = configurationFile.value;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: targets('config.files.v1'),
      configuration: {domain: 'file', fileName: selectedFile}
    }, fileOperationStatus);
    const contentResult = Object.values(operation.results).find(result =>
      result.success && result.configuration?.content != null);
    if (contentResult && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration && selectedFile === configurationFile.value) {
      configurationContent.value = contentResult.configuration.content;
      updateEditorPosition();
      text(fileOperationStatus, operationSummary(operation));
      inputGeneration++;
      updateConfigurationButtons();
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

previewFileConfiguration.addEventListener('click', async () => {
  approvedFilePreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.files.v1'),
      configuration: {domain: 'file', fileName: configurationFile.value, content: configurationContent.value}
    }, fileOperationStatus);
    text(fileOperationStatus, operationSummary(operation));
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration) {
      approvedFilePreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.files.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(fileOperationStatus, 'The targets or file changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

applyFileConfiguration.addEventListener('click', async () => {
  if (!approvedFilePreview || !window.confirm(`Apply this exact ${configurationFile.value} preview to every selected Bukkit node?`)) return;
  const approval = approvedFilePreview;
  approvedFilePreview = null;
  inputGeneration++;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, fileOperationStatus);
    text(fileOperationStatus, operationSummary(operation));
  } catch (error) { text(fileOperationStatus, error.message); }
});

function quickOptions() {
  if (quickPreset.value === 'standalone') return {};
  if (quickPreset.value === 'proxy-backend') return {server: quickName.value.trim(), method: 'PLUGINMESSAGING'};
  if (quickPreset.value === 'vote-site') return {
      name: quickName.value.trim(), displayName: quickName.value.trim(), serviceSite: quickService.value.trim(),
      voteUrl: quickUrl.value.trim(), voteDelay: quickDelay.value.trim(), priority: '5', material: 'DIAMOND'
    };
  if (quickPreset.value === 'easy-reward') return {scope: quickRewardScope.value,
    name: quickName.value.trim(), command: quickCommand.value.trim(), message: quickMessage.value.trim()};
  if (quickPreset.value === 'common-settings') return {
    processRewards: String(quickProcessRewards.checked), autoCreateVoteSites: String(quickAutoSites.checked),
    extraAllSitesCheck: String(quickExtraCheck.checked), countFakeVotes: String(quickCountFake.checked),
    disableNoServiceSiteMessage: String(quickHideSiteWarning.checked),
    disableUpdateChecking: String(quickDisableUpdates.checked)
  };
  return {votesRequired: quickPartyVotes.value, command: quickPartyCommand.value.trim(),
    broadcast: quickPartyBroadcast.value.trim(), giveAllPlayers: String(quickPartyAll.checked),
    onlineOnly: String(quickPartyOnline.checked)};
}

previewQuickSetup.addEventListener('click', async () => {
  approvedQuickPreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.quick-setup.v1'),
      configuration: {domain: 'quick-setup', preset: quickPreset.value, options: quickOptions()}
    }, quickOperationStatus);
    text(quickOperationStatus, operationSummary(operation));
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration) {
      approvedQuickPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.quick-setup.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(quickOperationStatus, 'The targets or setup changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(quickOperationStatus, error.message); }
});

applyQuickSetup.addEventListener('click', async () => {
  if (!approvedQuickPreview || !window.confirm('Apply this exact quick setup to every selected Bukkit node?')) return;
  const approval = approvedQuickPreview;
  approvedQuickPreview = null;
  inputGeneration++;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, quickOperationStatus);
    text(quickOperationStatus, operationSummary(operation));
  } catch (error) { text(quickOperationStatus, error.message); }
});

voteSitesSource.addEventListener('change', () => {
  voteSitesSourceId = voteSitesSource.value;
  voteSitesTargetIds.delete(voteSitesSourceId);
  voteSitesTargetsInitialized = true;
  approvedVoteSitesPreview = null;
  inputGeneration++;
  renderVoteSitesSync();
  text(voteSitesSyncStatus, 'Source changed. Read it and preview every target before syncing.');
  updateConfigurationButtons();
});

previewVoteSitesSync.addEventListener('click', async () => {
  approvedVoteSitesPreview = null;
  const previewGeneration = inputGeneration;
  const sourceId = voteSitesSourceId;
  const nodeIds = selectedVoteSitesTargets();
  try {
    const read = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [sourceId], configuration: {domain: 'file', fileName: 'VoteSites.yml'}
    }, voteSitesSyncStatus);
    const source = Object.values(read.results).find(result =>
      result.success && result.configuration?.content != null)?.configuration?.content;
    if (source == null) throw new Error('The source backend did not return VoteSites.yml.');
    if (previewGeneration !== inputGeneration || sourceId !== voteSitesSourceId) {
      text(voteSitesSyncStatus, 'The source or targets changed while reading. Preview again.');
      return;
    }
    const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds,
      configuration: {domain: 'quick-setup', preset: 'sync-vote-sites', options: {sourceContent: source}}
    }, voteSitesSyncStatus);
    if (preview.state === 'SUCCEEDED' && preview.approvalToken && previewGeneration === inputGeneration) {
      approvedVoteSitesPreview = {operationId: preview.operationId, approvalToken: preview.approvalToken,
        nodeIds, sourceId};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(voteSitesSyncStatus, 'The source or targets changed while previewing. Preview again before syncing.');
    }
  } catch (error) { text(voteSitesSyncStatus, error.message); }
});

applyVoteSitesSync.addEventListener('click', async () => {
  if (!approvedVoteSitesPreview || !window.confirm(
      'Sync the previewed VoteSites definitions to every selected backend? Target rewards and target-only sites are preserved.')) return;
  const approval = approvedVoteSitesPreview;
  approvedVoteSitesPreview = null;
  inputGeneration++;
  try {
    await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, voteSitesSyncStatus);
  } catch (error) { text(voteSitesSyncStatus, error.message); }
});

transportTestProxy.addEventListener('change', () => {
  transportTestProxyId = transportTestProxy.value;
  transportTestBackendId = '';
  renderTransportTest();
  text(transportTestStatus, 'Choose a backend, then run a live non-vote communication test.');
  updateConfigurationButtons();
});

transportTestBackend.addEventListener('change', () => {
  transportTestBackendId = transportTestBackend.value;
  text(transportTestStatus, 'Ready to test the active VotingPlugin transport.');
  updateConfigurationButtons();
});

runTransportTest.addEventListener('click', async () => {
  const proxyId = transportTestProxyId;
  const server = transportTestBackendId;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [proxyId],
      configuration: {domain: 'quick-setup', preset: 'communication-test', options: {server}}
    }, transportTestStatus);
    if (proxyId !== transportTestProxyId || server !== transportTestBackendId) {
      text(transportTestStatus, 'The proxy or backend changed while testing. Run the test again.');
      return;
    }
    text(transportTestStatus, operationSummary(operation));
  } catch (error) { text(transportTestStatus, error.message); }
});

proxyMethodProxy.addEventListener('change', () => {
  proxyMethodProxyId = proxyMethodProxy.value;
  renderProxyMethod();
  const network = proxyMethodNetwork();
  text(proxyMethodStatus, network.unavailable.length > 0
    ? `Cannot switch yet. Enroll, update, and connect: ${network.unavailable.map(backend => backend.displayName).join(', ')}.`
    : 'Choose a method to preflight every node before applying.');
  updateConfigurationButtons();
});

proxyMethodButtons.forEach(button => button.addEventListener('click', async () => {
  const method = button.dataset.proxyMethod;
  const network = proxyMethodNetwork();
  if (!network.proxy || !network.topologyComplete || network.reported.length === 0 || network.unavailable.length > 0) return;
  try {
    const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: network.nodeIds,
      configuration: {domain: 'quick-setup', preset: 'proxy-method', options: {method}}
    }, proxyMethodStatus);
    if (preview.state !== 'SUCCEEDED' || !preview.approvalToken) return;
    if (proxyMethodProxyId !== network.proxy.nodeId) {
      text(proxyMethodStatus, 'The selected proxy changed while preflighting. Choose the method again.');
      return;
    }
    if (!window.confirm(`Switch ${network.nodeIds.length} VotingPlugin nodes to ${method}? ` +
        'The proxy runtime will restart after Control records the result.')) return;
    const applied = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: preview.operationId, approvalToken: preview.approvalToken
    }, proxyMethodStatus);
    text(proxyMethodStatus, `${operationSummary(applied)}\nReconnect the proxy if needed, then run the communication test.`);
  } catch (error) { text(proxyMethodStatus, error.message); }
}));

[configurationContent, quickName, quickService, quickUrl, quickDelay, quickRewardScope,
  quickCommand, quickMessage, quickProcessRewards, quickAutoSites, quickExtraCheck, quickCountFake,
  quickHideSiteWarning, quickDisableUpdates, quickPartyVotes, quickPartyCommand, quickPartyBroadcast,
  quickPartyAll, quickPartyOnline].forEach(field => field.addEventListener('input', clearApprovals));
configurationContent.addEventListener('input', updateEditorPosition);
configurationContent.addEventListener('click', updateEditorPosition);
configurationContent.addEventListener('keyup', updateEditorPosition);
configurationContent.addEventListener('keydown', handleEditorKeydown);
configurationFile.addEventListener('input', () => {
  configurationContent.value = '';
  updateEditorPosition();
  text(fileOperationStatus, 'Read the selected file before previewing changes.');
  clearApprovals();
});
quickPreset.addEventListener('input', () => { updateQuickFields(); clearApprovals(); });
serverPicker.addEventListener('change', () => selectPrimaryServer(serverPicker.value));
tabButtons.forEach(button => button.addEventListener('click', () => setActiveTab(button.dataset.tab, true)));
configViewButtons.forEach(button => button.addEventListener('click', () => setConfigView(button.dataset.configView)));
document.querySelectorAll('[data-open-tab]').forEach(button => button.addEventListener('click', () => {
  setActiveTab(button.dataset.openTab, true);
}));
document.querySelectorAll('[data-open-config-view]').forEach(button => button.addEventListener('click', () => {
  setActiveTab('configurations', true);
  setConfigView(button.dataset.openConfigView);
  if (button.dataset.file && configurationFile.value !== button.dataset.file) {
    configurationFile.value = button.dataset.file;
    configurationFile.dispatchEvent(new Event('input'));
  }
}));
window.addEventListener('hashchange', () => setActiveTab(tabFromHash()));
refresh.addEventListener('click', loadNodes);
previousPage.addEventListener('click', () => {
  pageOffset = Math.max(0, pageOffset - PAGE_SIZE);
  loadNodes();
});
nextPage.addEventListener('click', () => {
  pageOffset += PAGE_SIZE;
  loadNodes();
});
async function initialize() {
  await loadHealth();
  setActiveTab(tabFromHash());
  setConfigView('easy');
  updateQuickFields();
  updatePluginSuggestions();
  if (!await loadSetupState()) {
    await restoreSession();
  }
}
initialize();
