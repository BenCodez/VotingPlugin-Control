(function (root, factory) {
  const api = factory(root.ControlConfiguration || (typeof require === 'function' ? require('./configuration-state.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlVoteSites = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (configuration) {
  'use strict';
  if (!configuration || !configuration.aggregateSnapshots) throw new Error('ControlConfiguration.aggregateSnapshots is required');

  const TARGET_STATUSES = new Set(['AVAILABLE', 'ERROR', 'UNSUPPORTED']);
  const FIELD_STATUSES = new Set(['AVAILABLE', 'MISSING', 'UNSUPPORTED']);
  const WORKFLOWS = new Set(['EDIT_EXISTING', 'CREATE_MISSING', 'REMOVE']);
  const ADD_FIELDS = ['Enabled', 'Name', 'ServiceSite', 'VoteURL', 'VoteDelay', 'Priority', 'Hidden',
    'DisplayItem.Material', 'DisplayItem.Amount'];

  function text(value) { return typeof value === 'string' ? value : ''; }
  function clone(value) {
    if (value === undefined) return undefined;
    return JSON.parse(JSON.stringify(value));
  }
  function equal(left, right) { return JSON.stringify(canonical(left)) === JSON.stringify(canonical(right)); }
  function canonical(value) {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') {
      const result = {};
      Object.keys(value).sort().forEach(function (key) { result[key] = canonical(value[key]); });
      return result;
    }
    return value;
  }
  function fieldsSnapshot(fields) {
    const result = {};
    if (!fields || typeof fields !== 'object') return result;
    Object.keys(fields).forEach(function (name) {
      const field = fields[name] || {};
      result[name] = {status: FIELD_STATUSES.has(field.status) ? field.status : 'MISSING', value: clone(field.value)};
    });
    return result;
  }
  function siteSnapshot(site) {
    if (!site || typeof site !== 'object' || typeof site.siteKey !== 'string' || !site.siteKey) return null;
    return {siteKey: site.siteKey, editable: site.editable === true, rewardsConfigured: site.rewardsConfigured === true,
      fields: fieldsSnapshot(site.fields)};
  }
  function targetRecord(target) {
    return {nodeId: target.nodeId, sessionId: text(target.sessionId), status: 'MISSING', revision: '', sites: [],
      code: '', message: ''};
  }
  function targetSite(target, key) {
    return target.sites.find(function (site) { return site.siteKey === key; }) || null;
  }
  function targetStatus(target) {
    return target.status === 'ERROR' ? 'ERROR' : target.status === 'UNSUPPORTED' ? 'UNSUPPORTED' : 'MISSING';
  }
  function completeAddFields(fields) {
    return ADD_FIELDS.every(function (name) {
      return Object.prototype.hasOwnProperty.call(fields, name) && fields[name] !== undefined;
    });
  }

  class VoteSitesState {
    constructor(options) {
      this.targets = new Map();
      this.dirty = new Map();
      this.addFields = {};
      this.createTargetIds = new Set();
      this.createMissing = true;
      this.createEditExisting = false;
      this.selectedSiteKey = '';
      this.workflow = 'EDIT_EXISTING';
      this.mode = 'EDIT_EXISTING';
      this.preview = null;
      this.results = new Map();
      this.requestedPlans = null;
      const targets = Array.isArray(options) ? options : options && options.targets;
      if (targets) this.setTargets(targets);
    }

    setTargets(targets) {
      const next = new Map();
      (Array.isArray(targets) ? targets : []).forEach(function (target) {
        if (!target || typeof target.nodeId !== 'string' || !target.nodeId) return;
        const sessionId = text(target.sessionId);
        const current = this.targets.get(target.nodeId);
        next.set(target.nodeId, current && current.sessionId === sessionId ? current : targetRecord({nodeId: target.nodeId, sessionId}));
      }, this);
      const changed = !equal(Array.from(this.targets.keys()), Array.from(next.keys())) || Array.from(next.keys()).some(function (id) {
        return !this.targets.has(id) || this.targets.get(id).sessionId !== next.get(id).sessionId;
      }, this);
      this.targets = next;
      this.createTargetIds.forEach(function (id) { if (!next.has(id)) this.createTargetIds.delete(id); }, this);
      if (changed) {
        // A draft is bound to the exact target set and sessions. Never carry an
        // earlier workspace's overrides into a newly selected or reconnected node.
        this.dirty.clear(); this.addFields = {}; this.createTargetIds.clear();
        this.createMissing = true; this.createEditExisting = false;
        this.workflow = 'EDIT_EXISTING'; this.mode = 'EDIT_EXISTING'; this.partialPolicy = 'existing';
        this.results.clear(); this.requestedPlans = null; this.invalidatePreview();
      }
      return this;
    }

    setRead(nodeId, read) {
      const target = this.targets.get(nodeId);
      if (!target || !read || typeof read !== 'object') return this;
      const sites = Array.isArray(read.sites) ? read.sites.map(siteSnapshot).filter(Boolean) : [];
      const next = {nodeId, sessionId: text(read.sessionId), status: TARGET_STATUSES.has(read.status) ? read.status : 'ERROR',
        revision: text(read.revision), sites, code: text(read.code), message: text(read.message)};
      if (!equal(target, next)) this.invalidatePreview();
      this.targets.set(nodeId, next);
      return this;
    }

    selectSite(siteKey) {
      const next = text(siteKey);
      if (next !== this.selectedSiteKey) {
        this.selectedSiteKey = next;
        // A draft belongs to one exact key.  It must never become an edit/add/remove for another site.
        this.dirty.clear(); this.addFields = {}; this.createTargetIds.clear(); this.createEditExisting = false;
        this.workflow = 'EDIT_EXISTING'; this.mode = 'EDIT_EXISTING'; this.requestedPlans = null;
      }
      this.invalidatePreview(); return this;
    }
    setWorkflow(workflow) {
      if (WORKFLOWS.has(workflow)) { this.workflow = workflow; this.mode = workflow; this.invalidatePreview(); }
      return this;
    }
    setMode(mode) { return this.setWorkflow(mode); }
    setCreateTargets(ids) {
      this.createTargetIds = new Set((Array.isArray(ids) ? ids : []).filter(function (id) { return this.targets.has(id); }, this));
      this.invalidatePreview(); return this;
    }
    setCreateMissing(enabled) { this.createMissing = enabled === true; this.invalidatePreview(); return this; }
    setCreateEditExisting(enabled) { this.createEditExisting = enabled === true; this.invalidatePreview(); return this; }
    setAddFields(fields) { this.addFields = fields && typeof fields === 'object' ? clone(fields) : {}; this.invalidatePreview(); return this; }
    edit(field, value) {
      if (typeof field === 'string' && field && value !== undefined) { this.dirty.set(field, clone(value)); this.invalidatePreview(); }
      return this;
    }
    reset(field) { if (typeof field === 'string' && field) this.dirty.delete(field); this.invalidatePreview(); return this; }
    resetAll() { this.dirty.clear(); this.invalidatePreview(); return this; }

    aggregate(siteKey) {
      const key = siteKey === undefined ? this.selectedSiteKey : text(siteKey);
      const entries = [];
      let present = 0;
      this.targets.forEach(function (target) {
        const site = target.status === 'AVAILABLE' ? targetSite(target, key) : null;
        if (site) present += 1;
        entries.push({nodeId: target.nodeId, sessionId: target.sessionId, status: site ? 'AVAILABLE' : targetStatus(target),
          revision: target.revision, site: site && clone(site), code: target.code, message: target.message});
      });
      const presence = !present ? 'NONE' : present === this.targets.size ? 'ALL' : 'PARTIAL';
      const fields = {};
      const names = new Set();
      entries.forEach(function (entry) { if (entry.site) Object.keys(entry.site.fields).forEach(function (name) { names.add(name); }); });
      names.forEach(function (name) { fields[name] = this.aggregateField(name, key); }, this);
      return {siteKey: key, presence, fields, targets: entries};
    }

    aggregateField(field, siteKey) {
      const key = siteKey === undefined ? this.selectedSiteKey : text(siteKey);
      const entries = [];
      this.targets.forEach(function (target) {
        const site = target.status === 'AVAILABLE' ? targetSite(target, key) : null;
        const snapshot = site && site.fields[field];
        const status = target.status === 'ERROR' ? 'ERROR' : target.status === 'UNSUPPORTED' ? 'UNSUPPORTED'
          : !site || !snapshot ? 'MISSING' : snapshot.status;
        const value = status === 'AVAILABLE' ? clone(snapshot.value) : undefined;
        entries.push({nodeId: target.nodeId, sessionId: target.sessionId, revision: target.revision, status, value,
          code: target.code, message: target.message});
      });
      const aggregate = configuration.aggregateSnapshots(entries, equal);
      if (aggregate.value !== undefined) aggregate.value = clone(aggregate.value);
      return aggregate;
    }

    plans() {
      const key = this.selectedSiteKey;
      const validAdd = completeAddFields(this.addFields);
      return Array.from(this.targets.values()).map(function (target) {
        const site = target.status === 'AVAILABLE' ? targetSite(target, key) : null;
        const base = {nodeId: target.nodeId, sessionId: target.sessionId, revision: target.revision, siteKey: key,
          operation: 'UNCHANGED', changes: {}, fields: {}, skipped: [], status: target.status};
        if (target.status !== 'AVAILABLE') { base.operation = 'SKIP'; base.skipped.push({status: targetStatus(target)}); return base; }
        if (this.workflow === 'REMOVE') {
          if (!site) return base;
          if (!site.editable) { base.operation = 'SKIP'; base.skipped.push({status: 'NONEDITABLE'}); return base; }
          base.operation = 'REMOVE'; return base;
        }
        if (!site) {
          if (this.workflow !== 'CREATE_MISSING' || !this.createMissing || (this.createTargetIds.size && !this.createTargetIds.has(target.nodeId))) {
            if (this.selectedSiteKey && (this.dirty.size || this.workflow === 'REMOVE')) {
              base.skipped.push({status: 'MISSING_SITE'});
            }
            return base;
          }
          if (!validAdd) { base.operation = 'SKIP'; base.skipped.push({status: 'INVALID_ADD_FIELDS'}); return base; }
          base.operation = 'ADD'; base.fields = clone(this.addFields); return base;
        }
        if (this.workflow === 'CREATE_MISSING' && !this.createEditExisting) return base;
        if (!site.editable) { base.operation = 'SKIP'; base.skipped.push({status: 'NONEDITABLE'}); return base; }
        this.dirty.forEach(function (value, field) {
          const snapshot = site.fields[field];
          // MISSING is an editable insertion into an existing site.  Only an explicit
          // UNSUPPORTED field is excluded from an edit proposal.
          if (!snapshot || snapshot.status === 'MISSING') base.changes[field] = clone(value);
          else if (snapshot.status !== 'AVAILABLE') base.skipped.push({field, status: snapshot.status});
          else if (!equal(snapshot.value, value)) base.changes[field] = clone(value);
        });
        if (Object.keys(base.changes).length) base.operation = 'EDIT';
        return base;
      }, this);
    }

    changedCount() { return this.plans().filter(function (plan) { return ['ADD', 'EDIT', 'REMOVE'].includes(plan.operation); }).length; }
    exceedsChangedTargetLimit(limit) { return this.changedCount() > (typeof limit === 'number' ? limit : 8); }
    signature() {
      const targets = Array.from(this.targets.values()).map(function (target) { return canonical(target); });
      return JSON.stringify(canonical({targets, selectedSiteKey: this.selectedSiteKey, workflow: this.workflow, mode: this.mode,
        dirty: Array.from(this.dirty.entries()), addFields: this.addFields, createTargetIds: Array.from(this.createTargetIds),
        createMissing: this.createMissing, createEditExisting: this.createEditExisting}));
    }
    beginPreview() { this.invalidatePreview(); return this.signature(); }
    finishPreview(signature, items) {
      if (signature !== this.signature() || !Array.isArray(items)) return false;
      this.preview = {signature, items}; return true;
    }
    previewCurrent() { return this.preview && this.preview.signature === this.signature() ? this.preview : null; }
    invalidatePreview() { this.preview = null; return this; }
    markApplyRequested() { this.requestedPlans = clone(this.plans()); return this; }
    setApplyResult(nodeId, result) {
      if (!this.requestedPlans) this.markApplyRequested();
      if (typeof nodeId === 'string' && nodeId) this.results.set(nodeId, clone(result));
      return this;
    }
    clearConfirmedDirty() {
      const requested = this.requestedPlans || this.plans();
      this.dirty.forEach(function (value, field) {
        const edits = requested.filter(function (plan) { return plan.operation === 'EDIT' && Object.prototype.hasOwnProperty.call(plan.changes, field); });
        if (edits.length && edits.every(function (plan) {
          const target = this.targets.get(plan.nodeId); const site = target && target.status === 'AVAILABLE' && targetSite(target, plan.siteKey);
          const snapshot = site && site.fields[field];
          return this.results.get(plan.nodeId)?.confirmed === true
            && snapshot && snapshot.status === 'AVAILABLE' && equal(snapshot.value, value);
        }, this)) this.dirty.delete(field);
      }, this);
      const adds = requested.filter(function (plan) { return plan.operation === 'ADD'; });
      if (adds.length && adds.every(function (plan) {
        const target = this.targets.get(plan.nodeId); const site = target && target.status === 'AVAILABLE' && targetSite(target, plan.siteKey);
        return this.results.get(plan.nodeId)?.confirmed === true && site && ADD_FIELDS.every(function (field) {
          const snapshot = site.fields[field]; return snapshot && snapshot.status === 'AVAILABLE' && equal(snapshot.value, plan.fields[field]);
        });
      }, this)) this.addFields = {};
      return this;
    }
  }

  return {VoteSitesState, ADD_FIELDS};
}));
