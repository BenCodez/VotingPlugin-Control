const test = require('node:test');
const assert = require('node:assert/strict');
const {trustedOrigin, requestAllowed} = require('../../../scripts/webui-audit.cjs');

test('audit requires verified HTTPS or literal loopback HTTP without URL credentials', () => {
  for (const value of ['https://example.com', 'http://127.0.0.1:8080', 'http://[::1]:8080']) {
    assert.equal(trustedOrigin(value), value);
  }
  for (const value of ['http://example.com', 'http://localhost', 'http://127.1', 'http://2130706433',
    'https://example.com/ui', 'https://example.com?redirect=1', 'https://example.com#login']) {
    assert.throws(() => trustedOrigin(value));
  }
});

test('audit route allows at most the initial exact same-origin login write', () => {
  const origin = 'https://example.com';
  const allowed = (path, method, loginAvailable = true) =>
    requestAllowed(new URL(path, origin), origin, method, loginAvailable);
  assert.equal(allowed('/api/v1/nodes', 'GET'), true);
  assert.equal(allowed('/api/v1/auth/login', 'POST'), true);
  assert.equal(allowed('/api/v1/auth/login', 'POST', false), false);
  for (const path of ['/api/v1/auth/login?next=1', '/api/v1/agent-admin/status',
    '/api/v1/agent-admin/approve', '/api/v1/configuration/apply', 'https://example.org/api/v1/auth/login']) {
    assert.equal(allowed(path, 'POST'), false);
  }
  assert.equal(allowed('https://example.org', 'GET'), false);
  assert.equal(allowed('/api/v1/auth/logout', 'GET'), false);
});
