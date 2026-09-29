const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');

function declaration(name) {
  const match = new RegExp(`(?:^|\\n)function ${name}\\(`, 'm').exec(source);
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
    selectedServerId: 'proxy', authenticated: true, configurationTarget,
    document: {createElement: tagName => new Element(tagName)},
    text: (element, value) => { element.textContent = value; return element; },
    isProxy: node => ['VELOCITY', 'BUNGEE'].includes(node.platform),
    isBackend: node => node.platform === 'BUKKIT',
    roleLabel: node => node.platform === 'BUKKIT' ? 'Backend' : 'Proxy'
  };
  vm.createContext(context);
  vm.runInContext([declaration('configurationTargetNodes'), declaration('renderConfigurationTargetPicker')].join('\n'), context);
  vm.runInContext('renderConfigurationTargetPicker()', context);

  assert.deepEqual(configurationTarget.children.map(option => option.value), ['', 'backend', 'proxy']);
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
    selectedServerId: 'proxy', nodeIndex: nodes,
    isProxy: node => node.platform === 'VELOCITY', isBackend: node => node.platform === 'BUKKIT'
  };
  vm.createContext(context);
  vm.runInContext([declaration('selectedFileCapability'), declaration('fileTargetsForSelection')].join('\n'), context);
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('bungeeconfig.yml')", context)], ['proxy']);
  context.selectedServerId = 'backend';
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('Config.yml')", context)], ['backend']);
  assert.deepEqual([...vm.runInContext("fileTargetsForSelection('Rewards/StandardVote.yml')", context)], ['backend']);
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
