'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('refreshing agent access during issue keeps ownership of the one-time token', async () => {
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
  const start = source.indexOf('const agentAccessForm =');
  const end = source.indexOf("document.querySelector('#agent-grant-refresh')", start);
  let resolveIssue;
  const pendingIssue = new Promise(resolve => { resolveIssue = resolve; });
  const fields = new Map();
  const node = selector => {
    if (!fields.has(selector)) {
      fields.set(selector, {addEventListener(type, callback) { this[type] = callback; }});
    }
    return fields.get(selector);
  };
  const credential = {querySelector: () => node('credential-input')};
  const status = {enabled: true, configurable: true, activeGrants: 1};
  let issueCount = 0;
  const context = vm.createContext({
    document: {querySelector: node, createElement: () => ({}), createTextNode: String},
    authenticated: true, authenticationGeneration: 1, agentCredentialOutput: credential,
    agentGrantForm: node('grant-form'), agentGrantStatus: node('grant-status'),
    agentGrants: {replaceChildren() {}},
    FormData: class { get(key) { return key === 'minutes' ? '10' : ''; } getAll() { return ['discover']; } },
    clearAgentCredential() { credential.hidden = true; },
    text(element, content) { element.textContent = content; return element; },
    agentAdmin(action) {
      if (action === 'issue') { issueCount++; return pendingIssue; }
      return Promise.resolve(action === 'status' ? status : []);
    }
  });
  vm.runInContext(source.slice(start, end), context);
  await context.refreshAgentAccess();
  const submit = () => node('grant-form').submit({preventDefault() {}});
  const first = submit();
  await context.refreshAgentAccess();
  assert.equal(node('#agent-grant-issue').disabled, true);
  await submit();
  assert.equal(issueCount, 1);
  const syntheticValue = String(Symbol('fixture'));
  resolveIssue({token: syntheticValue, expiresAt: 'soon'});
  await first;
  assert.equal(credential.hidden, false);
  assert.equal(node('credential-input').value, syntheticValue);
  assert.equal(node('#agent-grant-issue').disabled, false);
});
