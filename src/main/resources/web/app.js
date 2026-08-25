'use strict';

const health = document.querySelector('#health');
const form = document.querySelector('#auth-form');
const tokenInput = document.querySelector('#token');
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
const PAGE_SIZE = 100;
let adminToken = '';
let pageOffset = 0;
let selectedNodes = new Set();
let approvedPreview = null;

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
  checkbox.disabled = !node.online || !node.acceptedCapabilities.includes('config.proxy-routing.v1');
  checkbox.checked = selectedNodes.has(node.nodeId) && !checkbox.disabled;
  checkbox.addEventListener('change', () => {
    if (checkbox.checked) selectedNodes.add(node.nodeId); else selectedNodes.delete(node.nodeId);
    approvedPreview = null;
    updateConfigurationButtons();
  });
  selector.append(checkbox, title);
  const meta = text(document.createElement('p'),
    `${node.platform} · VotingPlugin ${node.pluginVersion} · ${node.online ? 'online' : 'offline'}`);
  const list = document.createElement('ul');
  const backends = Array.isArray(node.backends) ? node.backends : [];
  if (backends.length === 0) {
    list.append(text(document.createElement('li'), 'No backends reported.'));
  } else {
    backends.forEach(backend => list.append(backendCard(backend)));
  }
  article.append(selector, meta, list);
  return article;
}

function updateConfigurationButtons(busy = false) {
  const ready = adminToken && selectedNodes.size > 0 && !busy;
  readConfiguration.disabled = !ready;
  previewConfiguration.disabled = !ready;
  applyConfiguration.disabled = !ready || !approvedPreview;
}

async function authorized(path, options = {}) {
  const response = await fetch(path, {
    cache: 'no-store',
    ...options,
    headers: {...(options.headers || {}), 'Authorization': `Bearer ${adminToken}`}
  });
  const body = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(body?.error?.message || `Control request failed (${response.status}).`);
  return body;
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

async function waitForOperation(operation) {
  text(operationStatus, operationSummary(operation));
  while (operation.state === 'RUNNING') {
    await new Promise(resolve => window.setTimeout(resolve, 1500));
    operation = await authorized(`/api/v1/operations/${operation.operationId}`);
    text(operationStatus, operationSummary(operation));
  }
  return operation;
}

async function startConfigurationOperation(path, body) {
  updateConfigurationButtons(true);
  try {
    return await waitForOperation(await authorized(path, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
    }));
  } finally {
    updateConfigurationButtons(false);
  }
}

async function loadNodes() {
  if (!adminToken) return;
  refresh.disabled = true;
  previousPage.disabled = true;
  nextPage.disabled = true;
  text(message, 'Loading…');
  try {
    const response = await fetch(`/api/v1/nodes?offset=${pageOffset}&limit=${PAGE_SIZE}`, {
      cache: 'no-store',
      headers: {'Authorization': `Bearer ${adminToken}`}
    });
    if (!response.ok) {
      throw new Error(response.status === 401 || response.status === 429 ? 'Authentication failed.' : 'Control request failed.');
    }
    const body = await response.json();
    nodes.replaceChildren();
    nodes.classList.toggle('empty', body.items.length === 0);
    if (body.items.length === 0) {
      text(nodes, pageOffset === 0 ? 'No proxies have registered yet.' : 'No proxies on this page.');
    } else {
      body.items.forEach(node => nodes.append(nodeCard(node)));
    }
    const visibleIds = new Set(body.items.filter(node => node.online && node.acceptedCapabilities.includes('config.proxy-routing.v1'))
      .map(node => node.nodeId));
    selectedNodes = new Set([...selectedNodes].filter(node => visibleIds.has(node)));
    updateConfigurationButtons();
    const first = body.items.length === 0 ? 0 : pageOffset + 1;
    const last = pageOffset + body.items.length;
    text(message, body.items.length === 0 ? 'No proxies on this page.' : `Showing proxies ${first}–${last}.`);
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

form.addEventListener('submit', event => {
  event.preventDefault();
  adminToken = tokenInput.value.trim();
  tokenInput.value = '';
  pageOffset = 0;
  loadNodes();
});

readConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {nodeIds: [...selectedNodes]});
    const successful = Object.values(operation.results).filter(result => result.success);
    if (successful.length) {
      const first = successful[0].configuration;
      sendAll.checked = first.sendVotesToAllServers;
      blockedServers.value = first.blockedServers.join('\n');
    }
  } catch (error) { text(operationStatus, error.message); }
});

previewConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: [...selectedNodes], configuration: proposal()
    });
    if (operation.state === 'SUCCEEDED' && operation.approvalToken) {
      approvedPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken};
      updateConfigurationButtons();
    }
  } catch (error) { text(operationStatus, error.message); }
});

applyConfiguration.addEventListener('click', async () => {
  if (!approvedPreview || !window.confirm('Apply this exact preview to every selected proxy? Each node may still reject a stale revision.')) return;
  const approval = approvedPreview;
  approvedPreview = null;
  try {
    await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    });
  } catch (error) { text(operationStatus, error.message); }
});
[sendAll, blockedServers].forEach(field => field.addEventListener('input', () => {
  if (approvedPreview) text(operationStatus, 'The proposal changed. Preview it again before apply.');
  approvedPreview = null;
  updateConfigurationButtons();
}));
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
