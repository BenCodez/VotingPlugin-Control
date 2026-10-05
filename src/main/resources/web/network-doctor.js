/* Read-only presentation of server-produced checks; never interpret raw configuration here. */
(function (root) {
  'use strict';
  const rank = {FAIL: 0, WARNING: 1, UNKNOWN: 2, INFO: 3, PASS: 4};
  function groups(report) {
    const result = new Map();
    for (const check of Array.isArray(report?.checks) ? report.checks : []) {
      if (!Object.hasOwn(rank, check.status)) continue;
      const category = String(check.category || 'Other');
      if (!result.has(category)) result.set(category, []);
      result.get(category).push(check);
    }
    return [...result].map(([category, checks]) => ({category,
      checks: checks.slice().sort((a, b) => rank[a.status] - rank[b.status])}));
  }
  function withConfigurationChecks(report, checks) {
    const serverChecks = Array.isArray(report?.checks) ? report.checks : [];
    const incomingConfigurationChecks = Array.isArray(checks) ? checks : [];
    const serverLimit = 500;
    const serverChecksKept = serverChecks.slice(0, serverLimit);
    const configurationLimit = Math.min(100, Math.max(0, serverLimit - serverChecksKept.length));
    const configurationChecks = incomingConfigurationChecks.slice(0, configurationLimit);
    const affectedNodesFor = check => [...new Set([...(check.nodeIds || []), ...(check.unknownNodeIds || [])])];
    const truncated = Boolean(report?.truncated) || serverChecks.length > serverChecksKept.length
      || incomingConfigurationChecks.length > configurationChecks.length
      || configurationChecks.some(check => affectedNodesFor(check).length > 100);
    return {...report, truncated, configurationChecks, checks: [...serverChecksKept, ...configurationChecks.map(check => ({
      id: `configuration.${check.path}`, category: 'Proxy Setup',
      status: Object.hasOwn(rank, check.status) ? check.status : 'UNKNOWN', title: check.title || check.path,
      explanation: check.message || 'Managed configuration evidence is unavailable.',
      affectedNodes: affectedNodesFor(check).slice(0, 100),
      evidence: 'Current revision-bound managed configuration read',
      nextAction: 'Review applicable settings through the normal preview/apply workflow.', restartRequired: false
    }))]};
  }
  function render(container, report, document) {
    container.replaceChildren();
    const label = (tag, value) => { const element = document.createElement(tag); element.textContent = String(value); return element; };
    container.append(label('p', 'Read-only reported evidence. UNKNOWN means unverified; no votes or configuration changes were made.'));
    if (report?.truncated) {
      const notice = label('p', 'UNKNOWN: This report is incomplete. Some nodes or checks were omitted by the reporting limits; omitted evidence is not verified. Inspect a smaller workspace for the missing evidence.');
      notice.className = 'doctor-check';
      container.append(notice);
    }
    for (const group of groups(report)) {
      const section = document.createElement('section');
      section.append(label('h4', group.category));
      const extra = document.createElement('details');
      extra.append(label('summary', 'Show PASS / INFO'));
      for (const check of group.checks) {
        const item = document.createElement('div');
        item.className = 'doctor-check';
        item.append(label('strong', `${check.status}: ${check.title}`), label('p', check.explanation),
          label('p', `Nodes: ${(check.affectedNodes || []).join(', ') || 'network'} · Evidence: ${check.evidence || 'unavailable'}`),
          label('p', `Next: ${check.nextAction}${check.restartRequired ? ' (restart required)' : ''}`));
        if (check.status === 'PASS' || check.status === 'INFO') extra.append(item); else section.append(item);
      }
      section.append(extra); container.append(section);
    }
  }
  const api = {groups, render, withConfigurationChecks};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.NetworkDoctorView = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
