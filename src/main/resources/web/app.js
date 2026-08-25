'use strict';

const health = document.querySelector('#health');
const form = document.querySelector('#auth-form');
const tokenInput = document.querySelector('#token');
const message = document.querySelector('#message');
const nodes = document.querySelector('#nodes');
const refresh = document.querySelector('#refresh');
let adminToken = '';

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
  const meta = text(document.createElement('p'),
    `${node.platform} · VotingPlugin ${node.pluginVersion} · ${node.online ? 'online' : 'offline'}`);
  const list = document.createElement('ul');
  const backends = Array.isArray(node.backends) ? node.backends : [];
  if (backends.length === 0) {
    list.append(text(document.createElement('li'), 'No backends reported.'));
  } else {
    backends.forEach(backend => list.append(backendCard(backend)));
  }
  article.append(title, meta, list);
  return article;
}

async function loadNodes() {
  if (!adminToken) return;
  refresh.disabled = true;
  text(message, 'Loading…');
  try {
    const response = await fetch('/api/v1/nodes?offset=0&limit=100', {
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
      text(nodes, 'No proxies have registered yet.');
    } else {
      body.items.forEach(node => nodes.append(nodeCard(node)));
    }
    text(message, `${body.items.length} prox${body.items.length === 1 ? 'y' : 'ies'} loaded.`);
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
  loadNodes();
});
refresh.addEventListener('click', loadNodes);
loadHealth();
