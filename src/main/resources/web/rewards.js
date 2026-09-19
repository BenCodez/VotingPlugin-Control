(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlRewardsEditor = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const FILES = ['VoteSites.yml', 'SpecialRewards.yml', 'Config.yml'];
  const MAX_CHANGED = 8;
  const CACHE_MS = 30000;
  const STATE = '/api/v1/configuration/rewards/state';
  const PREVIEW = '/api/v1/configuration/rewards/preview';
  const DISCARD = '/api/v1/configuration/rewards/discard-preview';
  const REWARD_DIR = 'Rewards/';
  function id(target) { return target && (target.nodeId || target.id); }
  function resultFor(operation, nodeId) {
    const results = operation && operation.results;
    return Array.isArray(results) ? results.find(item => item.nodeId === nodeId) : results && results[nodeId];
  }
  function same(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
  function errorText(error) { return error && (error.message || error.code) || 'Request failed'; }
  function create(adapter) {
    if (!adapter || ['targets', 'context', 'operation', 'request'].some(name => typeof adapter[name] !== 'function')) throw new Error('Rewards adapter is required');
    const records = new Map();
    const inventories = new Map();
    const inventoryFlights = new Map();
    let context = String(adapter.context()); let generation = 0; let flight = null; let queuedRead = null;
    let queuedForce = false; let queuedOnlyFailed = false; let approval = null; let applying = false;
    let selected = {fileName: 'VoteSites.yml', rewardPath: ''}; let edit = null;
    let state = {busy: false, error: '', message: '', previewState: 'Not previewed', previews: [], results: [], ackRequired: false};
    function notify(values) { state = Object.assign({}, state, values); if (adapter.changed) adapter.changed(); }
    function targets() { return (adapter.targets() || []).filter(target => typeof id(target) === 'string' && id(target)); }
    function current(captured, serial) { return String(adapter.context()) === captured && generation === serial; }
    function key(nodeId, fileName) { return nodeId + ':' + fileName; }
    function namedFile(fileName) { return typeof fileName === 'string' && fileName.startsWith(REWARD_DIR); }
    // Listing named files uses the read-only inspection lane in addition to the
    // reward-file capability. Older adapters omit this field, where the
    // reward-file flag remains the backwards-compatible indication.
    function namedInventorySupported(target) {
      return target.rewardFilesSupported === true && target.rewardFileInventorySupported !== false;
    }
    function displayFile(fileName) { return namedFile(fileName) ? fileName.slice(REWARD_DIR.length) : fileName; }
    function validNamedFile(fileName) { return /^[A-Za-z0-9][A-Za-z0-9_-]{0,99}\.yml$/.test(fileName); }
    function release(items) { return Promise.all((items || []).filter(item => item.operationId && item.approvalToken).map(item =>
      Promise.resolve(adapter.request(DISCARD, {previewOperationId: item.operationId, approvalToken: item.approvalToken})).catch(() => {}))); }
    function invalidate(message) {
      const prior = approval; approval = null; if (prior && !applying) void release(prior.items);
      notify({previewState: prior ? 'Stale' : 'Not previewed', previews: [], ackRequired: false, message: message || ''});
    }
    function sync() {
      const next = String(adapter.context());
      if (next !== context) { const prior = approval; approval = null; if (prior) void release(prior.items); context = next; generation++; records.clear(); inventories.clear(); inventoryFlights.clear(); flight = null; queuedRead = null; queuedForce = false; queuedOnlyFailed = false; edit = null; selected = {fileName: 'VoteSites.yml', rewardPath: ''}; notify({busy: false, error: '', message: '', previewState: 'Not previewed', previews: [], results: [], ackRequired: false}); }
      const live = new Set(targets().map(id));
      [...records.keys()].forEach(recordKey => { if (!live.has(recordKey.split(':')[0])) records.delete(recordKey); });
      [...inventories.keys()].forEach(nodeId => { if (!live.has(nodeId)) inventories.delete(nodeId); });
    }
    function record(nodeId, fileName) { return records.get(key(nodeId, fileName)); }
    function signature() {
      return JSON.stringify({context, selected, edit, targets: targets().map(target => [id(target), target.sessionId,
        files().map(fileName => { const item = record(id(target), fileName); return [fileName, item && item.status, item && item.revision]; })])});
    }
    function files() {
      const names = new Set(FILES);
      inventories.forEach(item => (item.files || []).forEach(fileName => names.add(REWARD_DIR + fileName)));
      return [...names];
    }
    async function inventory(target, force) {
      sync();
      const nodeId = id(target); if (!nodeId) return {status: 'ERROR', files: [], message: 'Target has no node ID'};
      if (!namedInventorySupported(target)) {
        const unsupported = {status: 'UNSUPPORTED', files: [], message: 'Named reward file discovery is unsupported by this connector', sessionId: target.sessionId};
        inventories.set(nodeId, unsupported); return unsupported;
      }
      const cached = inventories.get(nodeId);
      if (!force && cached && cached.sessionId === target.sessionId && cached.status === 'AVAILABLE' && Date.now() - cached.loadedAt <= CACHE_MS) return cached;
      if (inventoryFlights.has(nodeId)) return inventoryFlights.get(nodeId);
      if (typeof adapter.inspect !== 'function') {
        const unavailable = {status: 'UNSUPPORTED', files: [], message: 'Named reward file inventory is unavailable', sessionId: target.sessionId};
        inventories.set(nodeId, unavailable); return unavailable;
      }
      const captured = context; const serial = generation;
      const run = (async () => {
      try {
        const response = await adapter.inspect(target);
        const raw = Array.isArray(response) ? response : response && response.files;
        if (!Array.isArray(raw) || raw.length > 100 || raw.some(fileName => typeof fileName !== 'string' || !validNamedFile(fileName)))
          throw new Error('Named reward file inventory is invalid');
        const listed = [...new Set(raw)];
        const item = {status: 'AVAILABLE', files: listed, sessionId: target.sessionId, loadedAt: Date.now()};
        if (!current(captured, serial)) return {status: 'ERROR', files: [], message: 'Workspace changed while reading named reward files'};
        inventories.set(nodeId, item); return item;
      } catch (error) {
        const failed = {status: 'ERROR', files: [], message: errorText(error), sessionId: target.sessionId};
        if (!current(captured, serial)) return failed;
        inventories.set(nodeId, failed); return failed;
      }
      })();
      inventoryFlights.set(nodeId, run);
      try { return await run; } finally { if (inventoryFlights.get(nodeId) === run) inventoryFlights.delete(nodeId); }
    }
    async function loadInventories(captured, serial, force) {
      await Promise.all(targets().map(target => inventory(target, force)));
      if (!current(captured, serial)) return false;
      const namedFiles = files().filter(namedFile);
      for (const recordKey of [...records.keys()]) {
        if (recordKey.includes(':' + REWARD_DIR) && !namedFiles.some(fileName => recordKey.endsWith(':' + fileName))) records.delete(recordKey);
      }
      for (const fileName of namedFiles) for (const target of targets()) {
        const nodeId = id(target); const inv = inventories.get(nodeId); const recordKey = key(nodeId, fileName);
        const present = inv && inv.status === 'AVAILABLE' && inv.files.includes(displayFile(fileName));
        if (!present) {
          records.set(recordKey, {status: inv && inv.status === 'AVAILABLE' ? 'MISSING' : inv ? inv.status : 'ERROR',
            files: [], scopes: [], message: inv && inv.message, sessionId: target.sessionId});
        } else if (!records.has(recordKey) || records.get(recordKey).sessionId !== target.sessionId) {
          records.set(recordKey, {status: 'UNLOADED', files: [], scopes: [], message: 'Select this file to read its contents', sessionId: target.sessionId});
        }
      }
      return current(captured, serial);
    }
    function scopes() {
      const values = new Map();
      for (const fileName of files()) for (const target of targets()) {
        const item = record(id(target), fileName);
        for (const scope of item && item.status === 'AVAILABLE' ? item.scopes : []) values.set(fileName + ':' + scope.path, {fileName, path: scope.path});
        if (namedFile(fileName)) {
          const inv = inventories.get(id(target));
          if (inv && inv.status === 'AVAILABLE' && inv.files.includes(displayFile(fileName))) values.set(fileName + ':$', {fileName, path: '$'});
        }
      }
      return [...values.values()];
    }
    function targetScope(target) {
      const item = record(id(target), selected.fileName);
      const scope = item && item.scopes && item.scopes.find(value => value.path === selected.rewardPath);
      return {nodeId: id(target), status: item ? item.status : 'MISSING', revision: item && item.revision,
        message: item && item.message, scope: scope || null};
    }
    function aggregate(field) {
      const entries = targets().map(target => {
        const info = targetScope(target);
        return {nodeId: info.nodeId, status: info.status !== 'AVAILABLE' ? info.status : !info.scope ? 'MISSING'
          : Object.prototype.hasOwnProperty.call(info.scope.fields || {}, field) ? 'AVAILABLE' : 'MISSING',
          value: info.scope && info.scope.fields && info.scope.fields[field]};
      });
      const values = entries.filter(item => item.status === 'AVAILABLE');
      return {status: entries.some(item => item.status === 'ERROR') ? 'ERROR'
        : entries.some(item => item.status === 'UNSUPPORTED') ? 'UNSUPPORTED'
          : !values.length ? 'MISSING' : entries.some(item => item.status === 'MISSING') ? 'MISSING'
            : values.every(item => same(item.value, values[0].value)) ? 'SAME' : 'MIXED',
      value: values.length && values.every(item => same(item.value, values[0].value)) ? values[0].value : undefined, targets: entries};
    }
    async function read(force, onlyFailed) {
      sync(); const captured = context; const serial = generation;
      if (flight) {
        if (!force && !onlyFailed) return flight;
        queuedForce = queuedForce || force === true;
        queuedOnlyFailed = queuedOnlyFailed || onlyFailed === true;
        if (!queuedRead) {
          const pending = flight;
          queuedRead = pending.catch(() => {}).then(() => {
            if (!current(captured, serial)) return records;
            const nextForce = queuedForce; const nextOnlyFailed = !nextForce && queuedOnlyFailed;
            queuedRead = null; queuedForce = false; queuedOnlyFailed = false;
            return read(nextForce, nextOnlyFailed);
          });
        }
        return queuedRead;
      }
      const run = (async () => {
        if (!(await loadInventories(captured, serial, force === true))) return records;
        const allFiles = FILES.concat(namedFile(selected.fileName) ? [selected.fileName] : []);
        const requested = allFiles.flatMap(fileName => targets().filter(target => {
        const item = record(id(target), fileName);
        if (namedFile(fileName)) {
          const inv = inventories.get(id(target));
          if (!inv || inv.status === 'UNSUPPORTED' || inv.status === 'ERROR' || !inv.files.includes(displayFile(fileName))) return false;
        }
        return onlyFailed ? !item || item.status !== 'AVAILABLE' : force || !item || item.status !== 'AVAILABLE' || item.sessionId !== target.sessionId || Date.now() - item.loadedAt > CACHE_MS;
      }).map(target => ({target, fileName})));
        if (!requested.length) return records;
        notify({busy: true, error: '', message: 'Reading reward configuration from each target…'});
        for (const fileName of allFiles) {
          const wanted = requested.filter(item => item.fileName === fileName);
          if (!wanted.length || !current(captured, serial)) continue;
          const supportsFile = target => namedFile(fileName) ? target.rewardFilesSupported === true : target.supported !== false;
          const eligible = wanted.filter(({target}) => target.online === true && supportsFile(target));
          for (const {target} of wanted.filter(({target}) => target.online !== true || !supportsFile(target)))
            records.set(key(id(target), fileName), {status: !supportsFile(target) ? 'UNSUPPORTED' : 'ERROR',
              message: !supportsFile(target) ? namedFile(fileName) ? 'Connector does not support named reward files'
                : 'Connector does not support file configuration' : 'Target offline', sessionId: target.sessionId, scopes: []});
          if (!eligible.length) continue;
          try {
            const operation = await adapter.operation('/api/v1/configuration/read', {nodeIds: eligible.map(({target}) => id(target)), configuration: {domain: 'file', fileName}});
            await Promise.all(eligible.map(async ({target}) => {
              const nodeId = id(target); const result = resultFor(operation, nodeId);
              if (!current(captured, serial)) return;
              if (!result || result.success !== true) { records.set(key(nodeId, fileName), {status: 'ERROR', message: result && (result.message || result.code) || 'READ failed', sessionId: target.sessionId, scopes: []}); return; }
              try {
                const typed = await adapter.request(STATE, {readOperationId: operation.operationId, nodeId, fileName});
                if (!current(captured, serial)) return;
                if (typed.nodeId !== nodeId || typed.fileName !== fileName || typed.sessionId !== target.sessionId || typed.revision !== result.revision) throw new Error('Read identity changed; read again');
                records.set(key(nodeId, fileName), Object.assign({}, typed, {status: 'AVAILABLE', loadedAt: Date.now()}));
              } catch (error) { if (current(captured, serial)) records.set(key(nodeId, fileName), {status: 'ERROR', message: errorText(error), sessionId: target.sessionId, scopes: []}); }
            }));
          } catch (error) {
            if (current(captured, serial)) eligible.forEach(({target}) => records.set(key(id(target), fileName), {status: 'ERROR', message: errorText(error), sessionId: target.sessionId, scopes: []}));
          }
        }
        if (current(captured, serial)) { if (approval && approval.signature !== signature() && !applying) invalidate('Source changed; preview again'); notify({busy: false, message: 'Reward inventory refreshed'}); }
        return records;
      })();
      const pending = run.finally(() => { if (flight === pending) flight = null; });
      flight = pending; return pending;
    }
    function plan() {
      if (!edit || !selected.rewardPath) return [];
      return targets().map(target => {
        const info = targetScope(target); const scope = info.scope;
        const namedUnsupportedAction = namedFile(selected.fileName) && ['CREATE_REWARD', 'REMOVE_REWARD'].includes(edit.operation);
        const advanced = scope?.advancedKeys || [];
        const advancedField = advanced.includes(edit.field) || (edit.field?.startsWith('Messages.') && advanced.includes('Messages'));
        const itemFieldMissing = edit.operation === 'SET_SCALAR' && /^Items\.[A-Za-z0-9_-]{1,64}\.(Material|Amount)$/.test(edit.field || '')
          && !Object.prototype.hasOwnProperty.call(scope?.fields || {}, edit.field);
        const nestedFieldMissing = edit.operation === 'SET_SCALAR' && edit.field?.includes('.')
          && !Object.prototype.hasOwnProperty.call(scope?.fields || {}, edit.field);
        const commands = scope?.fields?.Commands;
        const missingCommands = ['REMOVE_LIST_ENTRY', 'REPLACE_LIST'].includes(edit.operation) && !Array.isArray(commands);
        const ambiguousRemoval = edit.operation === 'REMOVE_LIST_ENTRY' && Array.isArray(commands)
          && commands.filter(value => value === edit.value).length !== 1;
        // The server rejects a preview whose source-preserving patch does not
        // change a target. Treat matching scalar/list values as an explicit
        // exclusion before opening a per-target preview, rather than turning a
        // mixed workspace into an all-or-nothing failed preview.
        const unchangedScalar = edit.operation === 'SET_SCALAR' && typeof scope?.fields?.[edit.field] === 'string'
          && String(scope.fields[edit.field]) === String(edit.value);
        const unchangedList = edit.operation === 'REPLACE_LIST' && Array.isArray(commands) && same(commands, edit.value);
        const unchanged = unchangedScalar || unchangedList;
        const status = namedUnsupportedAction ? 'UNSUPPORTED' : info.status !== 'AVAILABLE' ? info.status : !scope ? 'MISSING'
          : !scope.editable ? 'UNSUPPORTED' : edit.operation === 'CREATE_REWARD' ? scope.status === 'MISSING' ? 'READY' : 'CONFLICT'
            : edit.operation === 'REMOVE_REWARD' ? scope.status === 'PRESENT' ? 'READY' : 'UNCHANGED'
              : scope.status !== 'PRESENT' ? 'MISSING' : advancedField || itemFieldMissing || nestedFieldMissing ? 'UNSUPPORTED'
                : missingCommands ? 'MISSING' : ambiguousRemoval ? 'CONFLICT' : unchanged ? 'UNCHANGED' : 'READY';
        return {nodeId: info.nodeId, status, revision: info.revision, scope};
      });
    }
    async function preview() {
      sync(); if (state.busy || applying || !edit || !selected.rewardPath || adapter.active && !adapter.active()) return false;
      await release(approval && approval.items); approval = null;
      const captured = context; const serial = generation; const before = signature(); const plans = plan();
      const ready = plans.filter(item => item.status === 'READY');
      if (!ready.length || ready.length > MAX_CHANGED) { notify({error: ready.length ? 'At most eight changed targets can be previewed' : 'No eligible changed targets', previewState: 'Stale'}); return false; }
      notify({busy: true, error: '', message: 'Previewing exact per-target reward changes…', previews: [], previewState: 'Not previewed'});
      const items = await Promise.all(plans.map(async item => {
        if (item.status !== 'READY') return Object.assign({}, item, {status: 'SKIP', reason: item.status});
        const source = record(item.nodeId, selected.fileName);
        try {
          const operation = await adapter.operation(PREVIEW, {readOperationId: source.readOperationId, nodeId: item.nodeId,
            fileName: selected.fileName, rewardPath: selected.rewardPath, action: edit.operation, field: edit.field, value: edit.value});
          const result = resultFor(operation, item.nodeId);
          const before = item.scope && item.scope.fields && item.scope.fields[edit.field];
          const expectedValue = edit.operation === 'APPEND_LIST_ENTRY' ? [...(Array.isArray(before) ? before : []), edit.value]
            : edit.operation === 'REMOVE_LIST_ENTRY' && Array.isArray(before) ? before.filter(value => value !== edit.value)
              : edit.operation === 'REPLACE_LIST' ? edit.value : edit.operation === 'CREATE_REWARD' ? [edit.value] : edit.value;
          return {nodeId: item.nodeId, status: operation.state === 'SUCCEEDED' && operation.approvalToken && result && result.success === true && result.revision === item.revision ? 'READY' : 'ERROR',
            operationId: operation.operationId, approvalToken: operation.approvalToken, operation, revision: item.revision, expectedValue};
        } catch (error) { return {nodeId: item.nodeId, status: 'ERROR', message: errorText(error)}; }
      }));
      if (!current(captured, serial) || before !== signature() || items.some(item => item.status === 'ERROR')) {
        void release(items); if (current(captured, serial)) notify({busy: false, error: 'Preview failed or draft changed', previewState: 'Stale', previews: items}); return false;
      }
      approval = {signature: before, context: captured, serial, excluded: items.some(item => item.status !== 'READY'),
        items: items.filter(item => item.status === 'READY'), excludedItems: items.filter(item => item.status !== 'READY'),
        edit: Object.assign({}, edit), selected: Object.assign({}, selected)};
      notify({busy: false, previewState: 'Ready', previews: items, ackRequired: items.some(item => item.status !== 'READY'), message: 'Preview ready'}); return true;
    }
    async function apply(acknowledged) {
      if (!approval || state.busy || applying || approval.signature !== signature() || !current(approval.context, approval.serial)) { invalidate('Preview is stale'); return false; }
      if (state.ackRequired && acknowledged !== true) { notify({error: 'Acknowledge excluded or unavailable targets'}); return false; }
      const approved = approval; applying = true; await read(true);
      if (!current(approved.context, approved.serial) || approved.signature !== signature()) { applying = false; invalidate('Source changed; preview again'); return false; }
      notify({busy: true, error: '', message: 'Applying approved reward changes…'});
      const outcomes = approved.excludedItems.map(item => ({nodeId: item.nodeId, status: item.reason || item.status,
        message: 'Excluded from this apply', confirmed: false}));
      for (const item of approved.items) {
        if (!current(approved.context, approved.serial)) break;
        try {
          const operation = await adapter.operation('/api/v1/configuration/apply', {previewOperationId: item.operationId, approvalToken: item.approvalToken});
          const result = resultFor(operation, item.nodeId);
          outcomes.push({nodeId: item.nodeId, status: result && result.success === true && operation.state === 'SUCCEEDED' ? 'APPLIED' : 'ERROR', result, operation});
        } catch (error) { outcomes.push({nodeId: item.nodeId, status: 'ERROR', message: errorText(error)}); }
      }
      const order = targets().map(id);
      outcomes.sort((left, right) => order.indexOf(left.nodeId) - order.indexOf(right.nodeId));
      approval = null;
      if (current(approved.context, approved.serial)) await read(true);
      if (!current(approved.context, approved.serial)) { applying = false; return false; }
      outcomes.forEach(item => {
        const source = record(item.nodeId, approved.selected.fileName);
        const scope = source && source.scopes.find(value => value.path === approved.selected.rewardPath);
        const fieldValue = scope && scope.fields && scope.fields[approved.edit.field];
        const approvedItem = approved.items.find(candidate => candidate.nodeId === item.nodeId);
        item.confirmed = item.status === 'APPLIED' && source && source.status === 'AVAILABLE' && (
          approved.edit.operation === 'REMOVE_REWARD' ? scope && scope.status === 'MISSING'
            : approved.edit.operation === 'CREATE_REWARD' ? scope && scope.status === 'PRESENT' && same(scope.fields?.Commands, approvedItem.expectedValue)
              : approved.edit.operation === 'APPEND_LIST_ENTRY' ? same(fieldValue, approvedItem.expectedValue)
                : approved.edit.operation === 'REMOVE_LIST_ENTRY' ? same(fieldValue, approvedItem.expectedValue)
                    : approved.edit.operation === 'REPLACE_LIST' ? same(fieldValue, approvedItem.expectedValue)
                  : approved.edit.operation === 'SET_SCALAR' ? same(fieldValue, approved.edit.value) || String(fieldValue) === String(approved.edit.value) : false);
      });
      applying = false; const complete = !approved.excluded && outcomes.length === approved.items.length && outcomes.every(item => item.confirmed);
      if (complete) edit = null;
      notify({busy: false, previewState: 'Not previewed', previews: [], results: outcomes, ackRequired: false,
        message: complete ? 'Reward changes confirmed from fresh reads' : 'Some targets failed or remain unconfirmed; inspect each result'});
      return complete;
    }
    return {get state() { return state; }, get selected() { return selected; }, get edit() { return edit; }, targets, record, scopes, targetScope, aggregate, plan, read, preview, apply, files, inventory,
      scopeChanged() { sync(); return records; }, select(fileName, rewardPath) { sync(); if (applying) return; if (selected.fileName !== fileName || selected.rewardPath !== rewardPath) { selected = {fileName, rewardPath}; edit = null; invalidate(); } },
      setEdit(operation, field, value) { if (applying) return; edit = {operation, field, value}; invalidate(); }, reset() { if (applying) return; edit = null; invalidate(); },
      invalidateReads() { records.clear(); if (!applying) invalidate('Configuration changed; read and preview again'); },
      clear() { const prior = approval; approval = null; if (prior) void release(prior.items); generation++; flight = null; queuedRead = null; queuedForce = false; queuedOnlyFailed = false; records.clear(); inventories.clear(); edit = null; selected = {fileName: 'VoteSites.yml', rewardPath: ''}; notify({busy: false, error: '', message: '', previewState: 'Not previewed', previews: [], results: [], ackRequired: false}); }};
  }
  return {create, FILES};
}));
