const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/web/app.js'), 'utf8');
const start = source.indexOf('const GLOBAL_PAGE_SHORTCUTS = new Map([');
const end = source.indexOf("window.addEventListener('beforeunload'", start);
assert.ok(start >= 0 && end > start, 'production global-search handler is present');

class Element {
  constructor() { this.value = ''; this.listeners = new Map(); }
  addEventListener(type, listener) { this.listeners.set(type, listener); }
}

test('global search confirms before replacing a Vote Site draft with another loaded site', () => {
  const search = new Element(); const input = new Element(); input.value = 'second';
  const selected = []; const opened = []; const prompts = [];
  const context = {
    globalSearch: search, globalSearchInput: input, GLOBAL_PAGE_SHORTCUTS: undefined,
    allNodeItems: [], GENERAL_SETTING_FIELDS: [], voteSitesHasDraft: () => true,
    voteSitesEditor: {model: {selectedSiteKey: 'first', targets: new Map([
      ['node', {sites: [{siteKey: 'second'}]}]
    ])}, selectSite: key => selected.push(key)},
    window: {confirm: message => { prompts.push(message); return false; }},
    openWorkspace: (...args) => opened.push(args), renderVoteSites: () => opened.push(['render']),
    setConfigView() {}, tabFromHash: () => 'vote-sites', openVoteSiteAdd() {}, text() {}, message: new Element()
  };
  vm.runInNewContext(source.slice(start, end), context, {filename: 'app-global-search-handler.js'});
  search.listeners.get('submit')({preventDefault() {}});
  assert.equal(prompts.length, 1);
  assert.match(prompts[0], /Discard the current Vote Site draft/);
  assert.deepEqual(selected, []);
  assert.deepEqual(opened, []);
  assert.equal(input.value, 'second');

  context.window.confirm = () => true;
  search.listeners.get('submit')({preventDefault() {}});
  assert.deepEqual(selected, ['second']);
  assert.deepEqual(opened, [['vote-sites'], ['render']]);
  assert.equal(input.value, '');
});
