(function (root, factory) {
  const api = factory(root.ControlVoteSites || (typeof require === 'function' ? require('./vote-sites-state.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlVoteSitesEditor = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (voteSites) {
  'use strict';
  if (!voteSites || !voteSites.VoteSitesState) throw new Error('ControlVoteSites.VoteSitesState is required');
  const CACHE_MILLIS = 30000;
  const MAX_CHANGED_TARGETS = 8;
  const STATE_PATH = '/api/v1/configuration/vote-sites/state';
  const PREVIEW_PATH = '/api/v1/configuration/vote-sites/preview';
  const DISCARD_PATH = '/api/v1/configuration/vote-sites/discard-preview';
  function id(target) { return target && (target.nodeId || target.id); }
  function targets(adapter) { return (adapter.targets() || []).filter(function (target) { return typeof id(target) === 'string' && id(target); }); }
  function resultFor(operation, nodeId) { const results = operation && operation.results; return Array.isArray(results) ? results.find(function (item) { return item.nodeId === nodeId || item.id === nodeId; }) : results && results[nodeId]; }
  function errorText(error) { return error && (error.message || error.code) || 'Request failed'; }
  function create(adapter) {
    if (!adapter || ['targets', 'context', 'operation', 'request'].some(function (name) { return typeof adapter[name] !== 'function'; })) throw new Error('A vote-sites adapter is required');
    const model = new voteSites.VoteSitesState(); const reads = new Map();
    let approval = null; let flight = null; let queuedRead = null; let queuedForce = false;
    let queuedOnlyFailed = false; let generation = 0; let knownContext = context(); let applying = false;
    let state = {busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []};
    function context() { return String(adapter.context()); }
    function active() { return typeof adapter.active !== 'function' || adapter.active(); }
    function notify() { if (typeof adapter.changed === 'function') adapter.changed(); }
    function set(values) { state = Object.assign({}, state, values); notify(); }
    function current(captured, generationAtStart) { return context() === captured && generation === generationAtStart; }
    function syncTargets() { model.setTargets(targets(adapter).map(function (target) { return {nodeId: id(target), sessionId: target.sessionId}; })); }
    function release(items) { return Promise.all((items || []).map(function (item) {
      return item.operationId && item.approvalToken ? Promise.resolve(adapter.request(DISCARD_PATH, {previewOperationId: item.operationId, approvalToken: item.approvalToken})).catch(function () {}) : Promise.resolve();
    })); }
    function invalidate(message) { const old = approval; approval = null; model.invalidatePreview(); if (old && !applying) release(old.items); set({previewState: 'Stale', ackRequired: false, previewItems: [], message: message || 'Preview invalidated', error: ''}); }
    function syncContext() {
      syncTargets();
      if (approval && !model.previewCurrent() && !applying) invalidate();
      const next = context();
      if (next !== knownContext) { release(approval && approval.items); generation++; knownContext = next; queuedRead = null; queuedForce = false; queuedOnlyFailed = false; reads.clear(); model.results.clear(); approval = null; model.invalidatePreview(); set({busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []}); }
    }
    function readError(target, message, code) { reads.delete(id(target)); model.setRead(id(target), {status: target.supported === false ? 'UNSUPPORTED' : 'ERROR', sessionId: target.sessionId, revision: '', sites: [], code: code || 'READ_FAILED', message: message || ''}); }
    function eligible(target) { return target.online === true && target.supported !== false; }
    function cacheCurrent(target) { const item = reads.get(id(target)); return item && item.sessionId === target.sessionId && Date.now() - item.loadedAt < CACHE_MILLIS; }
    async function load(operation, wanted, captured, generationAtStart) {
      await Promise.all(wanted.map(async function (target) {
        const nodeId = id(target); const result = resultFor(operation, nodeId);
        if (!result || result.success !== true) { if (current(captured, generationAtStart)) readError(target, result && (result.message || result.code), result && result.code); return; }
        try {
          const typed = await adapter.request(STATE_PATH, {readOperationId: operation.operationId, nodeId});
          if (!current(captured, generationAtStart) || !model.targets.has(nodeId)) return;
          if (!typed || typed.nodeId !== nodeId || typed.sessionId !== target.sessionId || typed.revision !== result.revision) { readError(target, 'Read identity changed; read the target again', 'TARGET_CHANGED'); return; }
          reads.set(nodeId, {readOperationId: operation.operationId, sessionId: target.sessionId, revision: typed.revision, loadedAt: Date.now()});
          model.setRead(nodeId, Object.assign({}, typed, {status: 'AVAILABLE'}));
        } catch (error) { if (current(captured, generationAtStart)) readError(target, errorText(error), error && error.code); }
      }));
    }
    function read(force, onlyFailed) {
      syncContext(); const captured = context(); const generationAtStart = generation; const key = captured + ':' + generationAtStart;
      if (flight && flight.key === key) {
        if (!force && !onlyFailed) return flight.promise;
        queuedForce = queuedForce || force === true;
        queuedOnlyFailed = queuedOnlyFailed || onlyFailed === true;
        if (!queuedRead) {
          queuedRead = flight.promise.catch(function () {}).then(function () {
            if (!current(captured, generationAtStart)) return model;
            const nextForce = queuedForce;
            const nextOnlyFailed = !nextForce && queuedOnlyFailed;
            queuedRead = null; queuedForce = false; queuedOnlyFailed = false;
            return read(nextForce, nextOnlyFailed);
          });
        }
        return queuedRead;
      }
      const wanted = targets(adapter).filter(function (target) {
        if (!eligible(target)) { readError(target, target.supported === false ? 'Vote sites are unsupported' : 'Target is offline', target.supported === false ? 'UNSUPPORTED' : 'OFFLINE'); return false; }
        return onlyFailed ? model.targets.get(id(target)).status !== 'AVAILABLE' : force || !cacheCurrent(target);
      });
      if (!wanted.length) return Promise.resolve(model);
      set({busy: true, error: '', message: 'Reading VoteSites.yml…'});
      const currentFlight = {key, promise: null}; currentFlight.promise = (async function () {
        try {
          const operation = await adapter.operation('/api/v1/configuration/read', {nodeIds: wanted.map(id), configuration: {domain: 'file', fileName: 'VoteSites.yml'}});
          if (current(captured, generationAtStart)) await load(operation, wanted, captured, generationAtStart);
          if (current(captured, generationAtStart)) { if (approval && !model.previewCurrent() && !applying) invalidate(); set({message: 'Vote sites loaded'}); }
        } catch (error) { if (current(captured, generationAtStart)) { wanted.forEach(function (target) { readError(target, errorText(error), error && error.code); }); set({error: errorText(error), message: ''}); } }
        finally { if (current(captured, generationAtStart)) set({busy: false}); if (flight === currentFlight) flight = null; }
        return model;
      }()); flight = currentFlight; return currentFlight.promise;
    }
    async function preview() {
      syncContext(); if (state.busy || !active()) return false; const captured = context(); const generationAtStart = generation;
      await release(approval && approval.items); approval = null; const signature = model.beginPreview(); const plans = model.plans(); const changed = plans.filter(function (plan) { return ['EDIT', 'ADD', 'REMOVE'].includes(plan.operation); });
      if (!changed.length) { set({error: 'Make an explicit change before previewing', previewState: 'Not previewed', previewItems: []}); return false; }
      if (changed.length > MAX_CHANGED_TARGETS) { set({error: 'At most 8 changed targets can be previewed at once', previewState: 'Stale', previewItems: []}); return false; }
      set({busy: true, error: '', message: 'Previewing vote-site changes…', previewState: 'Not previewed', ackRequired: false, previewItems: []});
      const items = plans.map(function (plan) { return {nodeId: plan.nodeId, status: plan.operation, skipped: plan.skipped}; }); let failed = false;
      await Promise.all(changed.map(async function (plan) {
        const index = items.findIndex(function (item) { return item.nodeId === plan.nodeId; }); const cached = reads.get(plan.nodeId);
        if (!cached) { failed = true; items[index] = {nodeId: plan.nodeId, status: 'ERROR', message: 'A successful read is required', skipped: plan.skipped}; return; }
        const fields = plan.operation === 'ADD' ? plan.fields : plan.operation === 'EDIT' ? plan.changes : {};
        try {
          const operation = await adapter.operation(PREVIEW_PATH, {readOperationId: cached.readOperationId, nodeId: plan.nodeId, action: plan.operation, siteKey: plan.siteKey, fields}); const result = resultFor(operation, plan.nodeId);
          const ready = operation.state === 'SUCCEEDED' && operation.approvalToken && result && result.success === true && result.revision === cached.revision;
          items[index] = {nodeId: plan.nodeId, status: ready ? 'READY' : 'ERROR', operationId: operation.operationId, approvalToken: operation.approvalToken, skipped: plan.skipped, operation}; if (!ready) failed = true;
        } catch (error) { failed = true; items[index] = {nodeId: plan.nodeId, status: 'ERROR', message: errorText(error), skipped: plan.skipped}; }
      }));
      if (current(captured, generationAtStart)) set({busy: false});
      if (!current(captured, generationAtStart) || signature !== model.signature() || failed || !model.finishPreview(signature, items)) { release(items); if (current(captured, generationAtStart)) set({error: failed ? 'One or more previews failed' : 'Vote-site draft changed while previewing', previewState: 'Stale', previewItems: items}); return false; }
      approval = {signature, context: captured, generation: generationAtStart, items: items.filter(function (item) { return item.status === 'READY'; })}; const ackRequired = items.some(function (item) { return item.status === 'SKIP' || item.skipped.length; });
      set({message: 'Preview ready', previewState: 'Ready', ackRequired, previewItems: items}); return true;
    }
    async function apply(acknowledged) {
      if (state.busy || applying || !approval || !model.previewCurrent() || approval.signature !== model.signature() || !current(approval.context, approval.generation)) { if (!applying) invalidate(); return false; }
      if (state.ackRequired && acknowledged !== true) { set({error: 'Acknowledge unchanged or excluded targets before applying'}); return false; }
      const approved = approval; applying = true; await read(true);
      if (!current(approved.context, approved.generation) || approved.signature !== model.signature()) { applying = false; invalidate(); return false; }
      const plans = model.plans(); model.markApplyRequested(); set({busy: true, error: '', message: 'Applying approved changes…'});
      for (const item of approved.items) {
        if (!current(approved.context, approved.generation)) break;
        try { const operation = await adapter.operation('/api/v1/configuration/apply', {previewOperationId: item.operationId, approvalToken: item.approvalToken}); const result = resultFor(operation, item.nodeId); if (current(approved.context, approved.generation)) model.setApplyResult(item.nodeId, {operation, result, status: result && result.success === true ? 'APPLIED' : 'ERROR', confirmed: false}); }
        catch (error) { if (current(approved.context, approved.generation)) model.setApplyResult(item.nodeId, {status: 'ERROR', message: errorText(error), confirmed: false}); }
      }
      if (!current(approved.context, approved.generation)) { applying = false; return false; }
      approval = null; const postRead = Date.now(); await read(true);
      if (!current(approved.context, approved.generation)) { applying = false; return false; }
      approved.items.forEach(function (item) {
        const outcome = model.results.get(item.nodeId); const plan = plans.find(function (candidate) { return candidate.nodeId === item.nodeId; }); const cached = reads.get(item.nodeId);
        const target = model.targets.get(item.nodeId); const site = target && target.sites.find(function (candidate) { return candidate.siteKey === plan.siteKey; });
        const exact = plan && (plan.operation === 'REMOVE' ? !site : plan.operation === 'ADD'
          ? site && Object.keys(plan.fields).every(function (field) { return site.fields[field] && site.fields[field].status === 'AVAILABLE' && JSON.stringify(site.fields[field].value) === JSON.stringify(plan.fields[field]); })
          : plan.operation === 'EDIT' && site && Object.keys(plan.changes).every(function (field) { return site.fields[field] && site.fields[field].status === 'AVAILABLE' && JSON.stringify(site.fields[field].value) === JSON.stringify(plan.changes[field]); }));
        if (outcome) outcome.confirmed = outcome.status === 'APPLIED' && cached && cached.loadedAt >= postRead && exact;
      });
      model.clearConfirmedDirty(); model.invalidatePreview(); applying = false;
      const failed = [...model.results.values()].some(function (outcome) { return outcome.status !== 'APPLIED' || !outcome.confirmed; });
      set({busy: false, previewState: 'Not previewed', ackRequired: false, previewItems: [], message: failed ? 'Apply had failures or unconfirmed targets; inspect each result' : 'Apply completed; refreshed target state'}); return !failed;
    }
    function setPartialPolicy(policy) {
      if (policy === 'existing') model.setWorkflow('EDIT_EXISTING').setCreateMissing(false).setCreateEditExisting(true);
      else if (policy === 'missing') model.setWorkflow('CREATE_MISSING').setCreateMissing(true).setCreateEditExisting(false);
      else if (policy === 'both') model.setWorkflow('CREATE_MISSING').setCreateMissing(true).setCreateEditExisting(true);
      else return model;
      model.partialPolicy = policy; model.invalidatePreview(); return model;
    }
    function draft(change) { change(); invalidate(); return model; }
    return {get model() { return model; }, get state() { return state; }, scopeChanged: function () { syncContext(); return Promise.resolve(model); }, read, preview, apply,
      selectSite: function (key) { return draft(function () { model.selectSite(key); }); }, beginAdd: function (key, fields, policy) { return draft(function () {
        const selectedPolicy = policy || 'missing';
        model.selectSite(key); model.setWorkflow('CREATE_MISSING').setAddFields(fields); setPartialPolicy(selectedPolicy);
        if (selectedPolicy === 'existing' || selectedPolicy === 'both') {
          Object.keys(fields || {}).forEach(function (field) { model.edit(field, fields[field]); });
        }
      }); }, setPartialPolicy: function (policy) { return draft(function () { setPartialPolicy(policy); }); },
      edit: function (field, value) { return draft(function () { model.edit(field, value); }); }, reset: function (field) { return draft(function () { model.reset(field); }); }, resetAll: function () { return draft(function () { model.resetAll(); }); }, remove: function (key) { return draft(function () { model.selectSite(key); model.setWorkflow('REMOVE'); }); }, cancelDraft: function () { return draft(function () { model.resetAll(); model.setAddFields({}); model.setWorkflow('EDIT_EXISTING'); }); }, invalidateReads: function () { reads.clear(); if (!applying) invalidate(); return model; }, clear: function () { generation++; queuedRead = null; queuedForce = false; queuedOnlyFailed = false; reads.clear(); release(approval && approval.items); approval = null; applying = false; model.setTargets([]); set({busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []}); return model; }};
  }
  return {create};
}));
