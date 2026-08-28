'use strict';

const health = document.querySelector('#health');
const form = document.querySelector('#auth-form');
const passwordInput = document.querySelector('#password');
const loginButton = form.querySelector('button[type="submit"]');
const logout = document.querySelector('#logout');
const message = document.querySelector('#message');
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
const PAGE_SIZE = 100;
let authenticated = false;
let csrfToken = '';
let pageOffset = 0;
let selectedNodes = new Set();
let approvedPreview = null;
let approvedFilePreview = null;
let approvedQuickPreview = null;
let nodeCapabilities = new Map();
let nodePlugins = new Map();
let inputGeneration = 0;
let authenticationGeneration = 0;
let loginInFlight = false;
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

function backendCard(backend) {
  const item = document.createElement('li');
  const state = backend.presenceKnown ? (backend.available ? 'online' : 'offline') : 'unknown';
  const title = text(document.createElement('strong'), backend.displayName);
  const details = text(document.createElement('span'),
    `${state} · ${backend.presenceKnown ? backend.playerCount : '–'} players · ${backend.backendId}`);
  item.append(title, details);
  return item;
}

function nodeCard(node) {
  const article = document.createElement('article');
  article.className = 'node';
  const title = text(document.createElement('h3'), node.displayName);
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
  selector.append(checkbox, title);
  const meta = text(document.createElement('p'),
    `${node.platform} · VotingPlugin ${node.pluginVersion} · ${node.online ? 'online' : 'offline'} · ${node.acceptedCapabilities.filter(value => value.startsWith('config.')).join(', ') || 'discovery only'}`);
  const plugins = Array.isArray(node.detectedPlugins) ? node.detectedPlugins : [];
  const pluginMeta = text(document.createElement('p'), plugins.length
    ? `Detected plugins: ${plugins.join(', ')}` : 'No Bukkit plugin inventory reported.');
  const list = document.createElement('ul');
  const backends = Array.isArray(node.backends) ? node.backends : [];
  if (backends.length === 0) {
    list.append(text(document.createElement('li'), 'No backends reported.'));
  } else {
    backends.forEach(backend => list.append(backendCard(backend)));
  }
  article.append(selector, meta, pluginMeta, list);
  return article;
}

function updateConfigurationButtons(busy = configurationOperationsInFlight > 0) {
  const routingReady = authenticated && targets('config.proxy-routing.v1').length > 0 && !busy;
  const fileReady = authenticated && targets('config.files.v1').length > 0 && !busy;
  const quickReady = authenticated && targets('config.quick-setup.v1').length > 0 && !busy;
  readConfiguration.disabled = !routingReady;
  previewConfiguration.disabled = !routingReady;
  applyConfiguration.disabled = !routingReady || !approvedPreview;
  readFileConfiguration.disabled = !fileReady;
  previewFileConfiguration.disabled = !fileReady || !configurationContent.value;
  applyFileConfiguration.disabled = !fileReady || !approvedFilePreview;
  previewQuickSetup.disabled = !quickReady;
  applyQuickSetup.disabled = !quickReady || !approvedQuickPreview;
}

function targets(capability) {
  return [...selectedNodes].filter(node => nodeCapabilities.get(node)?.includes(capability));
}

function clearApprovals() {
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  inputGeneration++;
  updateConfigurationButtons();
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
  inputGeneration++;
  logout.hidden = true;
  selectedNodes.clear();
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
  nodes.replaceChildren();
  nodes.classList.add('empty');
  text(nodes, 'Authenticate to view the network.');
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

async function loadNodes() {
  if (!authenticated) return;
  refresh.disabled = true;
  previousPage.disabled = true;
  nextPage.disabled = true;
  text(message, 'Loading…');
  try {
    const body = await authorized(`/api/v1/nodes?offset=${pageOffset}&limit=${PAGE_SIZE}`);
    nodes.replaceChildren();
    nodes.classList.toggle('empty', body.items.length === 0);
    if (body.items.length === 0) {
      text(nodes, pageOffset === 0 ? 'No proxies have registered yet.' : 'No proxies on this page.');
    } else {
      body.items.forEach(node => nodes.append(nodeCard(node)));
    }
    const previousCapabilities = nodeCapabilities;
    nodeCapabilities = new Map(body.items.map(node => [node.nodeId, node.online ? node.acceptedCapabilities : []]));
    nodePlugins = new Map(body.items.map(node => [node.nodeId, node.online && Array.isArray(node.detectedPlugins)
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
    if (invalidRoutingApproval || invalidFileApproval || invalidQuickApproval) {
      if (invalidRoutingApproval) approvedPreview = null;
      if (invalidFileApproval) approvedFilePreview = null;
      if (invalidQuickApproval) approvedQuickPreview = null;
      inputGeneration++;
      text(operationStatus, 'A preview target went offline or lost the required capability. Preview again before apply.');
    }
    const visibleIds = new Set(body.items.filter(node => node.online && node.acceptedCapabilities.some(value => value.startsWith('config.')))
      .map(node => node.nodeId));
    const filteredSelection = new Set([...selectedNodes].filter(node => visibleIds.has(node)));
    if (filteredSelection.size !== selectedNodes.size) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      inputGeneration++;
      text(operationStatus, 'The selected proxies changed during refresh. Preview again before apply.');
    }
    selectedNodes = filteredSelection;
    updatePluginSuggestions();
    updateConfigurationButtons();
    const first = body.items.length === 0 ? 0 : pageOffset + 1;
    const last = pageOffset + body.items.length;
    const backendLimit = body.backendItemsTruncated
      ? ` Backend summaries are limited to ${body.backendItemsReturned} entries on this page.` : '';
    text(message, body.items.length === 0 ? 'No proxies on this page.'
      : `Showing proxies ${first}–${last}.${backendLimit}`);
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
    authenticated = true;
    csrfToken = body.csrfToken;
    approvedPreview = null;
    approvedFilePreview = null;
    approvedQuickPreview = null;
    selectedNodes.clear();
    configurationContent.value = '';
    inputGeneration++;
    logout.hidden = false;
    pageOffset = 0;
    await loadNodes();
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
    authenticated = true;
    csrfToken = body.csrfToken;
    logout.hidden = false;
    await loadNodes();
  } catch (_) { /* The login form remains available. */ }
}

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

[configurationContent, quickName, quickService, quickUrl, quickDelay, quickRewardScope,
  quickCommand, quickMessage, quickProcessRewards, quickAutoSites, quickExtraCheck, quickCountFake,
  quickHideSiteWarning, quickDisableUpdates, quickPartyVotes, quickPartyCommand, quickPartyBroadcast,
  quickPartyAll, quickPartyOnline].forEach(field => field.addEventListener('input', clearApprovals));
configurationFile.addEventListener('input', () => {
  configurationContent.value = '';
  text(fileOperationStatus, 'Read the selected file before previewing changes.');
  clearApprovals();
});
quickPreset.addEventListener('input', () => { updateQuickFields(); clearApprovals(); });
refresh.addEventListener('click', loadNodes);
previousPage.addEventListener('click', () => {
  pageOffset = Math.max(0, pageOffset - PAGE_SIZE);
  loadNodes();
});
nextPage.addEventListener('click', () => {
  pageOffset += PAGE_SIZE;
  loadNodes();
});
loadHealth();
updateQuickFields();
updatePluginSuggestions();
restoreSession();
