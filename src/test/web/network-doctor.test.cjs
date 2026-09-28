const test = require('node:test');
const assert = require('node:assert/strict');
const {buildReport, legacyBoolean, statusFor} = require('../../main/resources/web/network-doctor.js');

function result(overrides = {}) {
  return buildReport({
    node: {online: true, acceptedCapabilities: ['data.inspect.v1']},
    proxyReports: 1,
    diagnostics: {
      configurationHealthy: true,
      configuredVoteSites: 3,
      enabledVoteSites: 2,
      processRewards: true,
      votifierDetected: true,
      voteLoggingEnabled: true,
      voteLogAvailable: true,
      voteLogReadable: true,
      proxyMode: true,
      dataStorage: 'MYSQL',
      ...overrides
    }
  });
}

test('reports only evidence-backed pass states', () => {
  const report = result();
  assert.equal(report.counts.FAIL, 0);
  assert.equal(report.counts.WARNING, 0);
  assert.equal(report.counts.UNKNOWN, 0);
  assert.equal(report.counts.PASS, report.checks.length);
  assert.match(report.checks.find(item => item.title === 'Vote Sites').explanation, /does not test website uptime/);
  assert.match(report.checks.find(item => item.title === 'Storage mode reported').explanation, /not general database health/);
});

test('surfaces incomplete diagnostics as a warning', () => {
  const report = buildReport({incomplete: true, node: {online: true, acceptedCapabilities: ['data.inspect.v1']},
    diagnostics: {}});
  const evidence = report.checks.find(item => item.title === 'Diagnostics response');
  assert.equal(evidence.status, 'WARNING');
  assert.match(evidence.explanation, /missing, inconsistent, or outside/);
});

test('does not turn missing VoteLog readability evidence into a failure', () => {
  const report = result({voteLoggingEnabled: true, voteLogReadable: undefined});
  assert.equal(report.checks.find(item => item.title === 'VoteLog readability').status, 'UNKNOWN');
});

test('reports an enabled but unavailable VoteLog adapter as a warning', () => {
  const report = result({voteLoggingEnabled: true, voteLogAvailable: false, voteLogReadable: false});
  const logging = report.checks.find(item => item.title === 'VoteLog readability');
  assert.equal(logging.status, 'WARNING');
  assert.match(logging.explanation, /runtime adapter is unavailable/);
});

test('keeps contradictory VoteLog availability and readability unknown', () => {
  const report = result({voteLoggingEnabled: true, voteLogAvailable: false, voteLogReadable: true});
  const logging = report.checks.find(item => item.title === 'VoteLog readability');
  assert.equal(logging.status, 'UNKNOWN');
  assert.match(logging.explanation, /contradictory/);
});

test('keeps inconsistent Vote Site counts unknown', () => {
  const report = result({configuredVoteSites: 1, enabledVoteSites: 2});
  const sites = report.checks.find(item => item.title === 'Vote Sites');
  assert.equal(sites.status, 'UNKNOWN');
  assert.match(sites.explanation, /inconsistent/);
});

test('keeps unsupported storage modes unknown', () => {
  const report = result({dataStorage: 'ORACLE'});
  assert.equal(report.checks.find(item => item.title === 'Storage mode reported').status, 'UNKNOWN');
});

test('keeps missing evidence unknown instead of healthy', () => {
  const report = result({configurationHealthy: null, configuredVoteSites: null, enabledVoteSites: null,
    votifierDetected: null, voteLoggingEnabled: null, dataStorage: null});
  assert.equal(report.checks.find(item => item.title === 'Configuration state').status, 'UNKNOWN');
  assert.equal(report.checks.find(item => item.title === 'Vote Sites').status, 'UNKNOWN');
  assert.equal(report.checks.find(item => item.title === 'Votifier detected').status, 'UNKNOWN');
  assert.equal(report.checks.find(item => item.title === 'VoteLog readability').status, 'UNKNOWN');
  assert.equal(report.checks.find(item => item.title === 'Storage mode reported').status, 'UNKNOWN');
});

test('distinguishes configuration failures from intentional warnings', () => {
  const report = result({configurationHealthy: false, configuredVoteSites: 0, enabledVoteSites: 0,
    processRewards: false, votifierDetected: false, voteLogReadable: false});
  assert.equal(report.checks.find(item => item.title === 'Configuration state').status, 'FAIL');
  assert.equal(report.checks.find(item => item.title === 'VoteLog readability').status, 'FAIL');
  assert.equal(report.checks.find(item => item.title === 'Vote Sites').status, 'WARNING');
  assert.equal(report.checks.find(item => item.title === 'Reward processing').status, 'WARNING');
  assert.equal(report.checks.find(item => item.title === 'Votifier detected').status, 'WARNING');
});

test('does not claim proxy health without current topology evidence', () => {
  const report = buildReport({node: {online: true, acceptedCapabilities: ['data.inspect.v1']}, proxyReports: 0,
    diagnostics: {proxyMode: true}});
  const topology = report.checks.find(item => item.title === 'Proxy topology');
  assert.equal(topology.status, 'WARNING');
  assert.match(topology.explanation, /no connected proxy/);
});

test('keeps an omitted backend relationship unknown when proxy summaries are truncated', () => {
  const report = buildReport({node: {online: true, acceptedCapabilities: ['data.inspect.v1']}, proxyReports: 0,
    topologyComplete: false, diagnostics: {proxyMode: true}});
  const topology = report.checks.find(item => item.title === 'Proxy topology');
  assert.equal(topology.status, 'UNKNOWN');
  assert.match(topology.explanation, /truncated/);
  assert.equal(statusFor(report, 'Proxy topology'), 'UNKNOWN');
  assert.equal(legacyBoolean(report, 'Proxy topology'), null);
});

test('legacy diagnostics summaries preserve pass, warning, failure, and unknown distinctions', () => {
  const report = result({configurationHealthy: false, processRewards: false, dataStorage: 'ORACLE'});
  assert.equal(legacyBoolean(report, 'Control connector'), true);
  assert.equal(legacyBoolean(report, 'Configuration state'), false);
  assert.equal(legacyBoolean(report, 'Reward processing'), false);
  assert.equal(legacyBoolean(report, 'Storage mode reported'), null);
  assert.equal(statusFor(report, 'missing check'), 'UNKNOWN');
});

test('backend configuration actions explicitly require server scope', () => {
  const report = result();
  assert.equal(report.checks.find(item => item.title === 'Configuration state').action.serverScoped, true);
  assert.equal(report.checks.find(item => item.title === 'Reward processing').action.serverScoped, true);
  assert.equal(report.checks.find(item => item.title === 'Proxy topology').action.serverScoped, undefined);
});
