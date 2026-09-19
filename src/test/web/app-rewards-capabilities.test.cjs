const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
const start = source.indexOf('function rewardsContext()');
const end = source.indexOf('\nasync function inspectNamedRewardFiles', start);
assert.ok(start >= 0 && end > start, 'production rewards context exists');

test('Rewards context invalidates cached reads when target eligibility or discovery capability changes', () => {
  const node = {sessionId: 'session', online: true, acceptedCapabilities: ['config.files.v1', 'config.reward-files.v1', 'data.inspect.v1']};
  const context = {
    authenticationGeneration: 1,
    workspace: {managementScope: 'SERVER', selectedTargetIds: new Set(['node'])},
    nodeIndex: new Map([['node', node]]),
    isBackend: value => value === node,
    JSON
  };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  const ready = vm.runInContext('rewardsContext()', context);
  node.online = false;
  const offline = vm.runInContext('rewardsContext()', context);
  assert.notEqual(offline, ready);
  node.online = true;
  node.acceptedCapabilities = ['config.files.v1', 'config.reward-files.v1'];
  assert.notEqual(vm.runInContext('rewardsContext()', context), ready);
});
