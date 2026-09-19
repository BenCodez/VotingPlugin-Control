(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlWorkspace = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const PAGE_NAMES = new Set(['home', 'overview', 'general-settings', 'vote-sites', 'rewards', 'quick-setup', 'data', 'network',
    'configurations', 'activity', 'servers', 'access']);
  const SECTION_NAMES = new Set(['settings', 'vote-sites', 'rewards', 'sync']);
  const ELIGIBLE_CAPABILITIES = new Set([
    'config.files.v1', 'config.quick-setup.v1', 'config.quick-setup.v2', 'data.inspect.v1'
  ]);
  const SESSION_KEY = 'votingplugin-control.workspace.v1';

  function idSet(ids) {
    const result = new Set();
    if (!ids || typeof ids[Symbol.iterator] !== 'function') return result;
    for (const id of ids) {
      if (typeof id === 'string' && id.trim()) result.add(id);
    }
    return result;
  }

  function scopeFor(ids) {
    return ids.size === 1 ? 'SERVER' : ids.size > 1 ? 'MULTI_SERVER' : null;
  }

  function safeDecode(value) {
    try {
      const decoded = decodeURIComponent(value);
      return decoded && decoded.indexOf('/') === -1 ? decoded : '';
    } catch (_) {
      return '';
    }
  }

  function homeRoute() {
    return {page: 'home', scope: null, inspectedServerId: '', section: ''};
  }

  function parseRoute(hash) {
    if (typeof hash !== 'string') return homeRoute();
    const fragment = hash.startsWith('#') ? hash.slice(1) : hash;
    if (!fragment) return homeRoute();
    const parts = fragment.split('/');
    if (parts.some(part => !part)) return homeRoute();

    if (parts.length === 1 && PAGE_NAMES.has(parts[0])) {
      return {page: parts[0], scope: null, inspectedServerId: '', section: ''};
    }
    if (parts[0] === 'servers' && parts.length === 3 && parts[2] === 'overview') {
      const inspectedServerId = safeDecode(parts[1]);
      return inspectedServerId
        ? {page: 'overview', scope: null, inspectedServerId, section: ''}
        : homeRoute();
    }

    const workspacePages = {
      overview: 'overview',
      network: 'network',
      'quick-setup': 'quick-setup',
      configuration: 'configurations',
      configurations: 'configurations',
      data: 'data',
      activity: 'activity'
    };
    const workspaceSections = {
      settings: ['general-settings', 'settings'],
      'vote-sites': ['vote-sites', 'vote-sites'],
      rewards: ['rewards', 'rewards'],
      sync: ['quick-setup', 'sync']
    };
    if (parts[0] === 'workspace') {
      if (parts.length === 2 && workspacePages[parts[1]]) {
        return {page: workspacePages[parts[1]], scope: 'WORKSPACE', inspectedServerId: '', section: ''};
      }
      if (parts.length === 2 && workspaceSections[parts[1]]) {
        const [page, section] = workspaceSections[parts[1]];
        return {page, scope: 'WORKSPACE', inspectedServerId: '', section};
      }
      if (parts.length === 3 && parts[1] === 'overview' && workspaceSections[parts[2]]) {
        const [page, section] = workspaceSections[parts[2]];
        return {page, scope: 'WORKSPACE', inspectedServerId: '', section};
      }
      return homeRoute();
    }

    const globalPages = {
      network: 'network', routing: 'network', synchronization: 'quick-setup',
      configuration: 'configurations', configurations: 'configurations', activity: 'activity'
    };
    if (parts[0] === 'global' && parts.length === 2 && globalPages[parts[1]]) {
      return {
        page: globalPages[parts[1]], scope: 'GLOBAL', inspectedServerId: '',
        section: parts[1] === 'synchronization' ? 'sync' : ''
      };
    }
    return homeRoute();
  }

  function formatRoute(route) {
    const normalized = route || homeRoute();
    if (normalized.inspectedServerId && normalized.page === 'overview') {
      return '#servers/' + encodeURIComponent(normalized.inspectedServerId) + '/overview';
    }
    if (normalized.scope === 'GLOBAL') {
      const globalPart = normalized.page === 'configurations' ? 'configuration'
        : normalized.page === 'quick-setup' && normalized.section === 'sync' ? 'synchronization'
          : normalized.page;
      return globalPart === 'network' || globalPart === 'synchronization' || globalPart === 'configuration' || globalPart === 'activity'
        ? '#global/' + globalPart : '#home';
    }
    if (normalized.scope === 'WORKSPACE') {
      const section = SECTION_NAMES.has(normalized.section) ? normalized.section : '';
      if (section) return '#workspace/' + section;
      const page = normalized.page === 'configurations' ? 'configuration'
        : normalized.page === 'general-settings' ? 'settings'
          : normalized.page === 'vote-sites' ? 'vote-sites' : normalized.page;
      return ['overview', 'network', 'settings', 'quick-setup', 'configuration', 'data', 'activity'].includes(page)
        ? '#workspace/' + page : '#home';
    }
    return normalized.page && PAGE_NAMES.has(normalized.page) ? '#' + normalized.page : '#home';
  }

  function isBukkit(node) {
    return node && String(node.platform || '').toUpperCase() === 'BUKKIT'
      && typeof node.nodeId === 'string' && node.nodeId.trim();
  }

  function capabilitiesOf(node) {
    const capabilities = Array.isArray(node && node.acceptedCapabilities) ? node.acceptedCapabilities
      : Array.isArray(node && node.capabilities) ? node.capabilities : [];
    return new Set(capabilities.filter(value => typeof value === 'string'));
  }

  class Workspace {
    constructor(storage) {
      this.storage = storage || (typeof sessionStorage !== 'undefined' ? sessionStorage : null);
      this.currentRoute = '#home';
      this.login();
    }

    login() {
      this.managementScope = null;
      this.selectedTargetIds = new Set();
      this.inspectedServerId = '';
      this.previousTargetIds = new Set();
      this.currentRoute = '#home';
      return this;
    }

    logout() {
      this.clearPersisted();
      return this.login();
    }

    persist(route) {
      if (typeof route === 'string' && route) this.currentRoute = route;
      if (!this.storage || typeof this.storage.setItem !== 'function') return this;
      const snapshot = {
        managementScope: this.managementScope,
        selectedTargetIds: [...this.selectedTargetIds],
        inspectedServerId: this.inspectedServerId || '',
        route: this.currentRoute || '#home'
      };
      try { this.storage.setItem(SESSION_KEY, JSON.stringify(snapshot)); } catch (_) { /* private storage may be unavailable */ }
      return this;
    }

    setRoute(route) {
      return this.persist(typeof route === 'string' && route.startsWith('#') ? route : '#home');
    }

    restore(nodes) {
      if (!this.storage || typeof this.storage.getItem !== 'function') return this;
      let snapshot;
      try { snapshot = JSON.parse(this.storage.getItem(SESSION_KEY) || 'null'); } catch (_) { snapshot = null; }
      if (!snapshot || typeof snapshot !== 'object') return this;
      const scope = snapshot.managementScope;
      const ids = idSet(snapshot.selectedTargetIds);
      if (scope === 'GLOBAL') {
        this.managementScope = 'GLOBAL';
        this.selectedTargetIds = new Set();
      } else {
        this.selectedTargetIds = ids;
        this.managementScope = scopeFor(ids);
      }
      this.inspectedServerId = typeof snapshot.inspectedServerId === 'string' ? snapshot.inspectedServerId : '';
      this.currentRoute = typeof snapshot.route === 'string' && snapshot.route.startsWith('#') ? snapshot.route : '#home';
      this.previousTargetIds = new Set();
      this.reconcile(nodes);
      this.persist(this.currentRoute);
      return this;
    }

    clearPersisted() {
      if (!this.storage || typeof this.storage.removeItem !== 'function') return this;
      try { this.storage.removeItem(SESSION_KEY); } catch (_) { /* private storage may be unavailable */ }
      return this;
    }

    setTargets(ids) {
      this.selectedTargetIds = idSet(ids);
      this.managementScope = scopeFor(this.selectedTargetIds);
      this.persist();
      return this;
    }

    toggleTarget(id) {
      if (typeof id !== 'string' || !id.trim()) return this;
      const targets = new Set(this.selectedTargetIds);
      if (targets.has(id)) targets.delete(id); else targets.add(id);
      return this.setTargets(targets);
    }

    clearTargets() { return this.setTargets([]); }

    enterGlobal() {
      if (this.managementScope !== 'GLOBAL') this.previousTargetIds = new Set(this.selectedTargetIds);
      this.selectedTargetIds = new Set();
      this.managementScope = 'GLOBAL';
      this.persist();
      return this;
    }

    returnToServers() {
      this.selectedTargetIds = new Set(this.previousTargetIds);
      this.previousTargetIds = new Set();
      this.managementScope = scopeFor(this.selectedTargetIds);
      this.persist();
      return this;
    }

    inspect(id) {
      this.inspectedServerId = typeof id === 'string' && id.trim() ? id : '';
      this.persist();
      return this;
    }

    reconcile(nodes) {
      const validIds = new Set((Array.isArray(nodes) ? nodes : []).filter(isBukkit).map(node => node.nodeId));
      const keep = ids => new Set([...ids].filter(id => validIds.has(id)));
      this.selectedTargetIds = keep(this.selectedTargetIds);
      this.previousTargetIds = keep(this.previousTargetIds);
      if (this.inspectedServerId && !validIds.has(this.inspectedServerId)) this.inspectedServerId = '';
      if (this.managementScope !== 'GLOBAL') this.managementScope = scopeFor(this.selectedTargetIds);
      this.persist();
      return this;
    }

    selectEligible(nodes, max) {
      const limit = max === undefined ? 100 : Math.max(0, Math.floor(Number(max) || 0));
      const ids = [];
      for (const node of Array.isArray(nodes) ? nodes : []) {
        if (ids.length >= limit) break;
        if (!isBukkit(node) || !node.online) continue;
        const capabilities = capabilitiesOf(node);
        if ([...capabilities].some(capability => ELIGIBLE_CAPABILITIES.has(capability))) ids.push(node.nodeId);
      }
      return this.setTargets(ids);
    }
  }

  return {Workspace, parseRoute, formatRoute, SESSION_KEY};
}));
