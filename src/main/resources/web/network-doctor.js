(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlNetworkDoctor = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const ORDER = ['FAIL', 'WARNING', 'UNKNOWN', 'PASS'];
  const STORAGE_MODES = new Set(['SQLITE', 'MYSQL']);

  function state(value, falseState = 'FAIL') {
    return value === true ? 'PASS' : value === false ? falseState : 'UNKNOWN';
  }

  function check(category, title, status, explanation, evidence, action = null) {
    return {category, title, status, explanation, evidence, action};
  }

  function buildReport(input = {}) {
    const diagnostics = input.diagnostics || {};
    const node = input.node || {};
    const configuredSites = Number.isSafeInteger(diagnostics.configuredVoteSites)
      && diagnostics.configuredVoteSites >= 0 ? diagnostics.configuredVoteSites : null;
    const enabledSites = Number.isSafeInteger(diagnostics.enabledVoteSites)
      && diagnostics.enabledVoteSites >= 0 ? diagnostics.enabledVoteSites : null;
    const siteCountsConsistent = configuredSites != null && enabledSites != null && enabledSites <= configuredSites;
    const proxyReports = Number.isSafeInteger(input.proxyReports) && input.proxyReports >= 0 ? input.proxyReports : null;
    const capabilities = Array.isArray(node.acceptedCapabilities) ? node.acceptedCapabilities : [];
    const storageMode = typeof diagnostics.dataStorage === 'string' ? diagnostics.dataStorage.toUpperCase() : '';
    const voteLogConsistent = !(diagnostics.voteLogAvailable === true && diagnostics.voteLoggingEnabled !== true)
      && !(diagnostics.voteLogReadable === true && diagnostics.voteLogAvailable !== true);
    const checks = [
      check('Evidence', 'Diagnostics response', input.incomplete ? 'WARNING' : 'PASS',
        input.incomplete
          ? 'Some diagnostics fields were missing, inconsistent, or outside their supported bounds. Affected checks remain unknown.'
          : 'The bounded diagnostics fields used by this report were internally consistent.',
        'Control response validation'),
      check('Connectivity', 'Control connector', state(node.online),
        node.online ? 'This node has a current authenticated Control session.' : 'This node is not currently connected to Control.',
        'Control node registry', {tab: 'servers', label: 'View server'}),
      check('Connectivity', 'Diagnostics capability', capabilities.includes('data.inspect.v1') ? 'PASS' : 'FAIL',
        capabilities.includes('data.inspect.v1')
          ? 'The node negotiated bounded, read-only diagnostics.'
          : 'The current connector did not negotiate data.inspect.v1.',
        'Negotiated connector capabilities', {tab: 'servers', label: 'View capabilities'}),
      check('Connectivity', 'Proxy topology', diagnostics.proxyMode === false ? 'PASS'
        : diagnostics.proxyMode === true && (proxyReports == null || input.topologyComplete === false && proxyReports === 0) ? 'UNKNOWN'
        : diagnostics.proxyMode === true && proxyReports > 0 ? 'PASS'
        : diagnostics.proxyMode === true ? 'WARNING' : 'UNKNOWN',
      diagnostics.proxyMode === false ? 'This backend reports standalone mode; proxy topology is not required.'
        : diagnostics.proxyMode === true && proxyReports > 0 ? `${proxyReports} connected proxy ${proxyReports === 1 ? 'relationship is' : 'relationships are'} reporting this backend.`
        : diagnostics.proxyMode === true && input.topologyComplete === false && proxyReports === 0
          ? 'Proxy summaries were truncated, so this backend relationship cannot be classified.'
        : diagnostics.proxyMode === true && proxyReports === 0 ? 'Proxy mode is enabled, but no connected proxy currently reports this backend.'
        : 'Proxy mode or topology evidence is unavailable.',
      'Diagnostics plus current proxy registry', {tab: 'network', scrollTarget: 'transport-test-card', label: 'Open routing checks'}),
      check('Configuration', 'Configuration state', state(diagnostics.configurationHealthy),
        diagnostics.configurationHealthy === true ? 'VotingPlugin reports its loaded configuration as healthy.'
          : diagnostics.configurationHealthy === false ? 'VotingPlugin reports a configuration problem.'
          : 'The connector did not provide a configuration-health result.',
        'VotingPlugin diagnostics', {tab: 'configurations', label: 'Open configuration', serverScoped: true}),
      check('Configuration', 'Vote Sites', configuredSites == null || enabledSites == null ? 'UNKNOWN'
        : !siteCountsConsistent ? 'UNKNOWN' : configuredSites === 0 ? 'WARNING'
        : enabledSites === 0 ? 'WARNING' : 'PASS',
      configuredSites == null ? 'Configured Vote Site counts are unavailable.'
        : enabledSites == null ? `${configuredSites} sites are configured, but the enabled count is unavailable.`
        : !siteCountsConsistent ? 'Configured and enabled Vote Site counts are inconsistent.'
        : configuredSites === 0 ? 'No Vote Sites are configured.'
        : enabledSites === 0 ? `${configuredSites} sites are configured and all are disabled.`
        : `${enabledSites} of ${configuredSites} configured Vote Sites are enabled. This does not test website uptime.`,
      'VotingPlugin configuration diagnostics', {tab: 'data', scrollTarget: 'site-health-card', label: 'Inspect Vote Sites'}),
      check('Configuration', 'Reward processing', state(diagnostics.processRewards, 'WARNING'),
        diagnostics.processRewards === true ? 'ProcessRewards is enabled on this backend.'
          : diagnostics.processRewards === false ? 'ProcessRewards is disabled. Confirm that this matches the network topology.'
          : 'The reward-processing setting was not reported.',
        'VotingPlugin configuration diagnostics', {tab: 'quick-setup', label: 'Open setup', serverScoped: true}),
      check('Runtime evidence', 'Votifier detected', state(diagnostics.votifierDetected, 'WARNING'),
        diagnostics.votifierDetected === true ? 'VotingPlugin detected its vote-listener prerequisite.'
          : diagnostics.votifierDetected === false ? 'VotingPlugin did not detect Votifier. External port reachability was not tested.'
          : 'Votifier detection evidence is unavailable.',
        'VotingPlugin plugin detection', {tab: 'network', label: 'Review network setup'}),
      check('Runtime evidence', 'VoteLog readability', !voteLogConsistent ? 'UNKNOWN'
        : diagnostics.voteLoggingEnabled === false ? 'UNKNOWN'
        : diagnostics.voteLoggingEnabled !== true ? 'UNKNOWN'
        : diagnostics.voteLogAvailable === false ? 'WARNING'
        : diagnostics.voteLogAvailable !== true ? 'UNKNOWN'
        : diagnostics.voteLogReadable === true ? 'PASS'
        : diagnostics.voteLogReadable === false ? 'FAIL' : 'UNKNOWN',
      !voteLogConsistent ? 'VoteLog availability and readability evidence is contradictory.'
        : diagnostics.voteLoggingEnabled === false ? 'Vote logging is disabled; retained event evidence is unavailable.'
        : diagnostics.voteLoggingEnabled !== true ? 'Vote logging state is unavailable.'
        : diagnostics.voteLogAvailable === false ? 'Vote logging is enabled, but its runtime adapter is unavailable. A restart may be required after configuration changes.'
        : diagnostics.voteLogAvailable !== true ? 'VoteLog adapter availability is unknown.'
        : diagnostics.voteLogReadable === true ? 'The configured VoteLog store is readable.'
        : diagnostics.voteLogReadable === false ? 'Vote logging is enabled, but its retained event store is not readable.'
        : 'Vote logging is enabled, but readability evidence is unavailable.',
      'VotingPlugin VoteLog diagnostics', {tab: 'data', scrollTarget: 'vote-log-summary-result', label: 'Open logged events'}),
      check('Runtime evidence', 'Storage mode reported', STORAGE_MODES.has(storageMode)
        ? 'PASS' : 'UNKNOWN', STORAGE_MODES.has(storageMode)
        ? `VotingPlugin reports ${storageMode}. This confirms configuration only, not general database health.`
        : storageMode ? 'The connector returned an unsupported storage mode.'
        : 'The configured storage mode was not reported.', 'VotingPlugin diagnostics')
    ];
    const counts = Object.fromEntries(ORDER.map(status => [status, checks.filter(item => item.status === status).length]));
    return {checks, counts, generatedAt: new Date().toISOString()};
  }

  function render(container, report, onAction) {
    container.replaceChildren();
    const summary = document.createElement('div');
    summary.className = 'doctor-summary';
    ORDER.forEach(status => {
      const item = document.createElement('article');
      item.className = `doctor-stat doctor-${status.toLowerCase()}`;
      const value = document.createElement('strong');
      value.textContent = report.counts[status];
      const label = document.createElement('span');
      label.textContent = status;
      item.append(value, label);
      summary.append(item);
    });
    const groups = document.createElement('div');
    groups.className = 'doctor-groups';
    [...new Set(report.checks.map(item => item.category))].forEach(category => {
      const section = document.createElement('section');
      section.className = 'doctor-group';
      const heading = document.createElement('h4');
      heading.textContent = category;
      section.append(heading);
      report.checks.filter(item => item.category === category).forEach(item => {
        const row = document.createElement('article');
        row.className = 'doctor-check';
        const badge = document.createElement('span');
        badge.className = `pill doctor-${item.status.toLowerCase()}`;
        badge.textContent = item.status;
        const copy = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = item.title;
        const explanation = document.createElement('p');
        explanation.textContent = item.explanation;
        const evidence = document.createElement('small');
        evidence.textContent = `Evidence: ${item.evidence}`;
        copy.append(title, explanation, evidence);
        row.append(badge, copy);
        if (item.action && typeof onAction === 'function') {
          const action = document.createElement('button');
          action.type = 'button';
          action.className = 'secondary compact';
          action.textContent = item.action.label;
          action.addEventListener('click', () => onAction(item.action));
          row.append(action);
        }
        section.append(row);
      });
      groups.append(section);
    });
    container.append(summary, groups);
  }

  function statusFor(report, title) {
    return report?.checks?.find(item => item.title === title)?.status || 'UNKNOWN';
  }

  function legacyBoolean(report, title) {
    const status = statusFor(report, title);
    return status === 'PASS' ? true : status === 'UNKNOWN' ? null : false;
  }

  return {buildReport, render, statusFor, legacyBoolean, ORDER};
}));
