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
const PAGE_SIZE = 100;
let adminToken = '';
let pageOffset = 0;

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
