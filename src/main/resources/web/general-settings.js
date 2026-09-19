(function (root, factory) {
  const api = factory(root.ControlConfiguration || (typeof require === 'function' ? require('./configuration-state.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlGeneralSettings = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (configuration) {
  'use strict';

  if (!configuration || !configuration.MultiTargetState) throw new Error('ControlConfiguration.MultiTargetState is required');
  const CACHE_MILLIS = 30000;
  const MAX_CHANGED_TARGETS = 8;

  function now() { return Date.now(); }
  function resultFor(operation, id) {
    const results = operation && operation.results;
    if (Array.isArray(results)) return results.find(function (item) { return item.nodeId === id || item.id === id; });
    return results && results[id];
  }
  function errorText(error) { return error && (error.message || error.code) || 'Request failed'; }
  function targetList(adapter) { return Array.isArray(adapter.targets && adapter.targets()) ? adapter.targets() : []; }
  function eligible(target) { return target && target.online === true && target.supported === true; }

  function create(adapter) {
    if (!adapter || typeof adapter.targets !== 'function' || typeof adapter.context !== 'function'
        || typeof adapter.operation !== 'function' || typeof adapter.request !== 'function') throw new Error('A settings adapter is required');
    const model = new configuration.MultiTargetState();
    const reads = new Map();
    let flight = null;
    let approval = null;
    let generation = 0;
    let busyCount = 0;
    let applying = false;
    let knownContext = context();
    let state = {busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []};

    function active() { return typeof adapter.active !== 'function' || adapter.active(); }
    function context() { return String(adapter.context()); }
    function notify() { if (typeof adapter.changed === 'function') adapter.changed(); }
    function setState(values) { state = Object.assign({}, state, values); notify(); }
    function current(captured, capturedGeneration) { return context() === captured && generation === capturedGeneration; }
    function selected() { return targetList(adapter); }
    function syncTargets() { model.setTargets(selected().map(function (target) { return {id: target.id, sessionId: target.sessionId}; })); }
    function releasePreviews(items) {
      return Promise.all((items || []).map(function (item) {
        if (!item.operationId || !item.approvalToken) return Promise.resolve();
        // Best-effort approval disposal only: no task is queued and no node configuration changes.
        return Promise.resolve().then(function () { return adapter.request('/api/v1/configuration/general-settings/discard-preview',
          {previewOperationId: item.operationId, approvalToken: item.approvalToken}); }).catch(function () {});
      }));
    }
    function syncContext() {
      const nextContext = context();
      syncTargets();
      if (nextContext !== knownContext) {
        releasePreviews(approval && approval.items);
        knownContext = nextContext;
        generation++;
        reads.clear(); approval = null; model.invalidatePreview();
        busyCount = 0; applying = false;
        setState({busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []});
      }
    }
    function clearApproval(previewState) {
      if (!applying) releasePreviews(approval && approval.items);
      approval = null;
      model.invalidatePreview();
      setState({previewState: previewState || 'Stale', ackRequired: false, previewItems: [], error: '', message: 'Preview invalidated; read/re-preview required'});
    }
    function setBusy(delta) { busyCount = Math.max(0, busyCount + delta); setState({busy: busyCount > 0}); }
    function readError(target, message, code) {
      reads.delete(target.id);
      const unavailable = target && target.supported !== true;
      model.setRead(target.id, {status: unavailable ? 'UNSUPPORTED' : 'ERROR', sessionId: target.sessionId,
        revision: '', fields: {}, code: unavailable ? 'UNSUPPORTED' : code || 'READ_FAILED', message: message || ''});
    }
    function cacheCurrent(target) {
      const cached = reads.get(target.id);
      return cached && cached.sessionId === target.sessionId && now() - cached.loadedAt < CACHE_MILLIS;
    }

    async function loadStates(operation, targets, captured, capturedGeneration) {
      await Promise.all(targets.map(async function (target) {
        if (!current(captured, capturedGeneration)) return;
        const item = resultFor(operation, target.id);
        if (!item || item.success !== true) {
          readError(target, item && item.message || item && item.code || 'Read did not succeed', item && item.code);
          return;
        }
        try {
          const typed = await adapter.request('/api/v1/configuration/general-settings/state',
            {readOperationId: operation.operationId, nodeId: target.id});
          if (!current(captured, capturedGeneration) || !model.targets.has(target.id)) return;
          if (typed.nodeId !== target.id || typed.sessionId !== target.sessionId || typed.revision !== item.revision) {
            readError(target, 'Read identity changed; read the target again', 'TARGET_CHANGED'); return;
          }
          reads.set(target.id, {readOperationId: operation.operationId, sessionId: target.sessionId,
            revision: typed.revision || '', loadedAt: now()});
          model.setRead(target.id, {status: 'AVAILABLE', sessionId: typed.sessionId || target.sessionId,
            revision: typed.revision, fields: typed.fields || {}});
        } catch (error) { if (current(captured, capturedGeneration)) readError(target, errorText(error), error && error.code); }
      }));
    }

    function read(force, onlyFailed) {
      syncContext();
      const captured = context();
      const capturedGeneration = generation;
      const key = captured + '\u0000' + capturedGeneration;
      if (flight && flight.key === key) return force ? flight.promise.then(function () { return read(true, onlyFailed); }) : flight.promise;
      const all = selected();
      const wanted = all.filter(function (target) {
        if (!eligible(target)) { readError(target, target.online ? 'General Settings is unsupported' : 'Target is offline', target.online ? 'UNSUPPORTED' : 'OFFLINE'); return false; }
        if (onlyFailed) return model.targets.get(target.id).status !== 'AVAILABLE';
        return force || !cacheCurrent(target);
      });
      if (!wanted.length) { notify(); return Promise.resolve(model); }
      setBusy(1); setState({error: '', message: 'Reading General Settings…'});
      const currentFlight = {key: key, promise: null};
      currentFlight.promise = (async function () {
        try {
          const operation = await adapter.operation('/api/v1/configuration/read', {
            nodeIds: wanted.map(function (target) { return target.id; }), configuration: {domain: 'file', fileName: 'Config.yml'}
          });
          if (!current(captured, capturedGeneration)) return model;
          await loadStates(operation, wanted, captured, capturedGeneration);
          if (current(captured, capturedGeneration) && approval && !model.previewCurrent() && !applying) clearApproval('Stale');
          if (current(captured, capturedGeneration)) setState({message: 'General Settings loaded'});
        } catch (error) {
          if (current(captured, capturedGeneration)) {
            wanted.forEach(function (target) { readError(target, errorText(error), error && error.code); });
            setState({error: errorText(error), message: ''});
          }
        } finally {
          if (current(captured, capturedGeneration)) setBusy(-1);
          if (flight === currentFlight) flight = null;
        }
        return model;
      }());
      flight = currentFlight;
      return currentFlight.promise;
    }

    async function preview() {
      syncContext();
      if (state.busy) return false;
      const cleanupContext = context(); const cleanupGeneration = generation;
      setBusy(1);
      await releasePreviews(approval && approval.items); approval = null;
      if (!current(cleanupContext, cleanupGeneration)) return false;
      syncTargets();
      const captured = context();
      const capturedGeneration = generation;
      const signature = model.beginPreview();
      const plans = model.plans();
      const changed = plans.filter(function (plan) { return Object.keys(plan.overrides).length > 0; });
      if (changed.length > MAX_CHANGED_TARGETS) {
        setBusy(-1);
        setState({error: 'At most ' + MAX_CHANGED_TARGETS + ' changed targets can be previewed at once', previewState: 'Stale', previewItems: []});
        return false;
      }
      setState({error: '', message: 'Previewing General Settings…', previewState: 'Not previewed', ackRequired: false, previewItems: []});
      const items = plans.map(function (plan) { return {id: plan.id, status: plan.skipped.length ? plan.skipped[0].status : 'UNCHANGED', skipped: plan.skipped}; });
      let failed = false;
      await Promise.all(changed.map(async function (plan) {
        const cached = reads.get(plan.id);
        if (!cached) {
          failed = true;
          const index = items.findIndex(function (candidate) { return candidate.id === plan.id; });
          items[index] = {id: plan.id, status: 'ERROR', skipped: plan.skipped, message: 'A successful read is required'};
          return;
        }
        try {
          const operation = await adapter.operation('/api/v1/configuration/general-settings/preview', {
            readOperationId: cached.readOperationId, nodeId: plan.id, overrides: plan.overrides
          });
          const item = resultFor(operation, plan.id);
          const index = items.findIndex(function (candidate) { return candidate.id === plan.id; });
          items[index] = {id: plan.id, status: operation.state === 'SUCCEEDED' && operation.approvalToken
            && item && item.success === true && item.revision === cached.revision ? 'READY' : 'ERROR',
          operationId: operation.operationId, approvalToken: operation.approvalToken, operation, skipped: plan.skipped};
          if (items[index].status !== 'READY') failed = true;
        } catch (error) {
          failed = true;
          const index = items.findIndex(function (candidate) { return candidate.id === plan.id; });
          items[index] = {id: plan.id, status: 'ERROR', skipped: plan.skipped, message: errorText(error)};
        }
      }));
      if (current(captured, capturedGeneration)) setBusy(-1);
      if (!current(captured, capturedGeneration) || signature !== model.signature() || failed || !model.finishPreview(signature, items)) {
        releasePreviews(items);
        if (current(captured, capturedGeneration)) setState({error: failed ? 'One or more previews failed' : 'Settings changed while previewing', previewState: 'Stale', previewItems: items});
        return false;
      }
      approval = {signature, context: captured, generation: capturedGeneration, items: items.filter(function (item) { return item.status === 'READY'; })};
      const ackRequired = items.some(function (item) { return item.skipped && item.skipped.length; });
      setState({message: 'Preview ready', previewState: 'Ready', ackRequired, previewItems: items});
      return true;
    }

    async function apply(acknowledged) {
      if (state.busy || applying) return false;
      if (!approval || !model.previewCurrent() || approval.signature !== model.signature() || !current(approval.context, approval.generation)) {
        clearApproval('Stale'); setState({error: 'Preview is stale; preview again'}); return false;
      }
      if (state.ackRequired && acknowledged !== true) { setState({error: 'Acknowledge excluded targets before applying'}); return false; }
      const approved = {signature: approval.signature, context: approval.context, generation: approval.generation, items: approval.items.slice()};
      applying = true;
      await read(true);
      if (approved.signature !== model.signature() || !current(approved.context, approved.generation)) {
        applying = false;
        clearApproval('Stale'); setState({error: 'Target revisions changed; preview again'}); return false;
      }
      const captured = approved.context;
      setBusy(1); setState({error: '', message: 'Applying approved previews…'});
      const planned = model.plans();
      planned.forEach(function (plan) {
        if (!approved.items.some(function (item) { return item.id === plan.id; })) {
          model.setApplyResult(plan.id, {operation: null, result: null,
            status: plan.skipped.length ? 'EXCLUDED' : 'UNCHANGED', confirmed: false});
        }
      });
      for (const item of approved.items) {
        if (!current(captured, approved.generation)) break;
        try {
          const operation = await adapter.operation('/api/v1/configuration/apply',
            {previewOperationId: item.operationId, approvalToken: item.approvalToken});
          if (!current(captured, approved.generation)) return false;
          const result = resultFor(operation, item.id);
          model.setApplyResult(item.id, {operation, result, status: result && result.success === true ? 'APPLIED' : 'ERROR', confirmed: false});
        } catch (error) {
          if (!current(captured, approved.generation)) return false;
          model.setApplyResult(item.id, {operation: null, result: null, status: 'ERROR', message: errorText(error), confirmed: false});
        }
      }
      if (!current(captured, approved.generation)) { applying = false; setBusy(-1); return false; }
      approval = null;
      const postReadStarted = now();
      await read(true);
      if (!current(captured, approved.generation)) return false;
      approved.items.forEach(function (item) {
        const outcome = model.results.get(item.id);
        const plan = planned.find(function (candidate) { return candidate.id === item.id; });
        const target = model.targets.get(item.id);
        const read = reads.get(item.id);
        if (outcome) outcome.confirmed = outcome.status === 'APPLIED' && !!plan && !!read && read.loadedAt >= postReadStarted
          && Object.keys(plan.overrides).every(function (field) {
            const snapshot = target && target.status === 'AVAILABLE' && target.fields[field];
            return snapshot && snapshot.status === 'AVAILABLE' && snapshot.value === plan.overrides[field];
          });
      });
      model.clearConfirmedDirty();
      model.invalidatePreview();
      applying = false;
      setBusy(-1);
      const failures = Array.from(model.results.values()).filter(function (result) { return result.status === 'ERROR'; }).length;
      const unconfirmed = Array.from(model.results.values()).filter(function (result) { return result.status === 'APPLIED' && !result.confirmed; }).length;
      setState({previewState: 'Not previewed', ackRequired: false, previewItems: [],
        message: failures || unconfirmed ? 'Apply completed with ' + failures + ' failed target(s) and ' + unconfirmed + ' unconfirmed target(s)' : 'Apply completed; requested persisted values confirmed'});
      return true;
    }

    const editor = {
      get model() { return model; },
      get state() { return state; },
      scopeChanged: function () {
        syncContext();
        return Promise.resolve(model);
      },
      read, preview, apply,
      edit: function (path, value) { model.edit(path, value); clearApproval('Stale'); return model; },
      reset: function (path) { model.reset(path); clearApproval('Stale'); return model; },
      resetAll: function () { model.resetAll(); clearApproval('Stale'); return model; },
      invalidateReads: function () { reads.clear(); if (!applying) clearApproval('Stale'); return model; },
      clear: function () { releasePreviews(approval && approval.items); generation++; knownContext = context(); reads.clear(); approval = null; applying = false; model.setTargets([]); state = {busy: false, error: '', message: '', previewState: 'Not previewed', ackRequired: false, previewItems: []}; busyCount = 0; notify(); return model; }
    };
    return editor;
  }
  return {create};
}));
