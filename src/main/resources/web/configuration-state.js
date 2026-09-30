(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlConfiguration = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const READ_STATUSES = new Set(['AVAILABLE', 'ERROR', 'UNSUPPORTED']);
  const FIELD_STATUSES = new Set(['AVAILABLE', 'MISSING', 'UNSUPPORTED']);
  const NETWORK_FIELDS = new Set(['OnlineMode', 'BedrockPlayerPrefix', 'AllowUnjoined']);

  function string(value) { return typeof value === 'string' ? value : ''; }

  // Shared across boolean Settings and typed site fields. Structural site
  // presence remains the Vote Sites layer's responsibility.
  function aggregateSnapshots(entries, equals) {
    const values = entries.filter(function (entry) { return entry.status === 'AVAILABLE'; })
      .map(function (entry) { return entry.value; });
    const supportedState = !values.length ? 'MISSING'
      : values.every(function (value) { return equals(value, values[0]); }) ? 'SAME' : 'MIXED';
    const state = entries.some(function (entry) { return entry.status === 'ERROR'; }) ? 'ERROR'
      : entries.some(function (entry) { return entry.status === 'UNSUPPORTED'; }) ? 'UNSUPPORTED'
        : entries.some(function (entry) { return entry.status === 'MISSING'; }) || !values.length
          ? 'MISSING' : supportedState;
    return {state, supportedState, value: supportedState === 'SAME' ? values[0] : undefined, targets: entries};
  }

  function fieldSnapshot(fields) {
    const result = {};
    if (!fields || typeof fields !== 'object') return result;
    Object.keys(fields).forEach(function (path) {
      const source = fields[path] || {};
      const value = typeof source.value === 'boolean' || typeof source.value === 'string' ? source.value : undefined;
      const status = FIELD_STATUSES.has(source.status) ? source.status : 'MISSING';
      result[path] = {
        status: status === 'AVAILABLE' && value === undefined ? 'UNSUPPORTED' : status,
        value
      };
    });
    return result;
  }

  function sameFields(left, right) {
    const leftKeys = Object.keys(left).sort();
    const rightKeys = Object.keys(right).sort();
    return leftKeys.length === rightKeys.length && leftKeys.every(function (key, index) {
      const value = rightKeys[index];
      return key === value && left[key].status === right[key].status && left[key].value === right[key].value;
    });
  }

  function targetRecord(target) {
    return {
      id: target.id,
      sessionId: string(target.sessionId),
      fileName: string(target.fileName) || 'Config.yml',
      platform: string(target.platform),
      role: string(target.role),
      managedByProxy: target.managedByProxy === true,
      reportingProxyIds: Array.isArray(target.reportingProxyIds)
        ? target.reportingProxyIds.filter(function (id) { return typeof id === 'string' && id; }).slice().sort() : [],
      networkIncomplete: target.networkIncomplete === true,
      networkOnly: target.networkOnly === true,
      status: 'MISSING',
      revision: '',
      fields: {},
      code: '',
      message: ''
    };
  }

  class MultiTargetState {
    constructor(options) {
      this.targets = new Map();
      this.dirty = new Map();
      this.results = new Map();
      this.preview = null;
      const targets = Array.isArray(options) ? options : options && options.targets;
      if (targets) this.setTargets(targets);
    }

    setTargets(targets) {
      const next = new Map();
      if (Array.isArray(targets)) {
        targets.forEach(function (target) {
          if (!target || typeof target.id !== 'string' || !target.id) return;
          const sessionId = string(target.sessionId);
          const current = this.targets.get(target.id);
          if (current && current.sessionId === sessionId
              && current.fileName === (string(target.fileName) || 'Config.yml')) {
            current.fileName = string(target.fileName) || current.fileName || 'Config.yml';
            current.platform = string(target.platform);
            current.role = string(target.role);
            current.managedByProxy = target.managedByProxy === true;
            current.reportingProxyIds = Array.isArray(target.reportingProxyIds)
              ? target.reportingProxyIds.filter(function (id) { return typeof id === 'string' && id; }).slice().sort() : [];
            current.networkIncomplete = target.networkIncomplete === true;
            current.networkOnly = target.networkOnly === true;
            next.set(target.id, current);
          } else {
            next.set(target.id, targetRecord(target));
          }
        }, this);
      }
      let changed = next.size !== this.targets.size;
      if (!changed) {
        next.forEach(function (record, id) {
          const previous = this.targets.get(id);
          if (previous !== record) changed = true;
        }, this);
      }
      this.targets = next;
      if (changed) {
        this.dirty.clear();
        this.results.clear();
        this.invalidatePreview();
      }
      return this;
    }

    setRead(id, read) {
      const target = this.targets.get(id);
      if (!target || !read || typeof read !== 'object') return this;
      const status = READ_STATUSES.has(read.status) ? read.status : 'ERROR';
      const revision = string(read.revision);
      const sessionId = string(read.sessionId);
      const fields = fieldSnapshot(read.fields);
      const changedIdentity = target.status !== status || target.revision !== revision || target.sessionId !== sessionId;
      const changedFields = !sameFields(target.fields, fields);
      target.status = status;
      target.revision = revision;
      target.sessionId = sessionId;
      target.fields = fields;
      target.code = string(read.code);
      target.message = string(read.message);
      if (changedIdentity || changedFields) this.invalidatePreview();
      return this;
    }

    edit(field, value) {
      if (typeof field !== 'string' || !field
          || (typeof value !== 'boolean' && typeof value !== 'string')) return this;
      this.dirty.set(field, value);
      this.invalidatePreview();
      return this;
    }

    reset(field) {
      if (typeof field === 'string' && field) this.dirty.delete(field);
      this.invalidatePreview();
      return this;
    }

    resetAll() {
      this.dirty.clear();
      this.invalidatePreview();
      return this;
    }

    aggregate(field) {
      const entries = [];
      this.targets.forEach(function (target) {
        if (target.networkOnly && !NETWORK_FIELDS.has(field)) return;
        let status = target.status === 'ERROR' ? 'ERROR'
          : target.status === 'UNSUPPORTED' ? 'UNSUPPORTED' : 'MISSING';
        let value;
        const snapshot = target.fields[field];
        if (target.status === 'AVAILABLE' && snapshot) {
          status = snapshot.status;
          if (status === 'AVAILABLE') value = this.logicalValue(target, field, snapshot.value);
        }
        const entry = {id: target.id, status, value, revision: target.revision, sessionId: target.sessionId,
          code: target.code, message: target.message};
        if (field === 'AllowUnjoined') {
          entry.rawValue = snapshot && snapshot.value;
          entry.role = target.role;
        }
        entries.push(entry);
      }, this);
      return aggregateSnapshots(entries, function (left, right) { return left === right; });
    }

    logicalValue(target, field, value) {
      if (field !== 'AllowUnjoined' || target.role !== 'BACKEND' || !target.managedByProxy) return value;
      const proxy = Array.from(this.targets.values()).find(function (candidate) {
        const snapshot = candidate.fields.AllowUnjoined;
        return target.reportingProxyIds.includes(candidate.id) && candidate.role === 'PROXY'
          && candidate.status === 'AVAILABLE'
          && snapshot && snapshot.status === 'AVAILABLE';
      });
      return proxy ? proxy.fields.AllowUnjoined.value : value;
    }

    requestedValue(target, field, requested) {
      if (field === 'Debug' && target.role === 'PROXY' && requested === 'EXTRA') return 'INFO';
      return field === 'AllowUnjoined' && target.role === 'BACKEND' && target.managedByProxy ? true : requested;
    }

    plans() {
      const plans = [];
      this.targets.forEach(function (target) {
        const overrides = {};
        const skipped = [];
        this.dirty.forEach(function (requested, field) {
          if (target.networkOnly && !NETWORK_FIELDS.has(field)) return;
          const snapshot = target.status === 'AVAILABLE' ? target.fields[field] : null;
          if (target.networkIncomplete
              && (field === 'OnlineMode' || field === 'BedrockPlayerPrefix' || field === 'AllowUnjoined')) {
            skipped.push({field, status: 'TOPOLOGY_INCOMPLETE'});
          } else if (snapshot && snapshot.status === 'AVAILABLE') {
            const targetRequest = this.requestedValue(target, field, requested);
            if (snapshot.value !== targetRequest) overrides[field] = targetRequest;
          } else {
            skipped.push({field, status: target.status === 'ERROR' ? 'ERROR'
              : target.status === 'UNSUPPORTED' ? 'UNSUPPORTED' : snapshot ? snapshot.status : 'MISSING'});
          }
        }, this);
        plans.push({id: target.id, revision: target.revision, sessionId: target.sessionId,
          overrides, skipped, status: target.status});
      }, this);
      return plans;
    }

    signature() {
      const targets = [];
      this.targets.forEach(function (target) {
        const fields = Object.keys(target.fields).sort().map(function (path) {
          const field = target.fields[path];
          return [path, field.status, field.value];
        });
        targets.push([target.id, target.sessionId, target.status, target.revision, fields]);
      });
      const dirty = Array.from(this.dirty.entries()).sort(function (a, b) { return a[0].localeCompare(b[0]); });
      return JSON.stringify([targets, dirty]);
    }

    beginPreview() {
      this.invalidatePreview();
      return this.signature();
    }

    finishPreview(signature, items) {
      if (signature !== this.signature() || !Array.isArray(items)) return false;
      this.preview = {signature, items};
      return true;
    }

    previewCurrent() {
      return this.preview && this.preview.signature === this.signature() ? this.preview : null;
    }

    invalidatePreview() {
      this.preview = null;
      return this;
    }

    setApplyResult(id, data) {
      if (typeof id === 'string' && id) this.results.set(id, data);
      return this;
    }

    clearConfirmedDirty() {
      if (!this.targets.size) return this;
      this.dirty.forEach(function (requested, field) {
        const relevantTargets = Array.from(this.targets.values()).filter(function (target) {
          return !target.networkOnly || NETWORK_FIELDS.has(field);
        });
        const confirmed = relevantTargets.length > 0 && relevantTargets.every(function (target) {
          const snapshot = target.status === 'AVAILABLE' && target.fields[field];
          return snapshot && snapshot.status === 'AVAILABLE'
            && snapshot.value === this.requestedValue(target, field, requested);
        }, this);
        if (confirmed) this.dirty.delete(field);
      }, this);
      return this;
    }
  }

  return {MultiTargetState, aggregateSnapshots};
}));
