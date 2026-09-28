(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ControlYamlEditor = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const MAX_MATCHES = 1000;
  const MAX_RENDERED_LINES = 160;

  function metrics(value, cursor) {
    const content = typeof value === 'string' ? value : '';
    const safeCursor = Math.max(0, Math.min(Number.isSafeInteger(cursor) ? cursor : 0, content.length));
    let line = 1;
    let lines = 1;
    for (let index = 0; index < content.length; index++) {
      if (content.charCodeAt(index) === 10) {
        lines++;
        if (index < safeCursor) line++;
      }
    }
    const previousNewline = safeCursor === 0 ? -1 : content.lastIndexOf('\n', safeCursor - 1);
    return {
      line,
      column: safeCursor - previousNewline,
      lines,
      characters: content.length
    };
  }

  function lineWindow(totalLines, scrollTop, viewportHeight, lineHeight, paddingTop) {
    const total = Math.max(1, Number.isSafeInteger(totalLines) ? totalLines : 1);
    const height = Number.isFinite(lineHeight) && lineHeight > 0 ? lineHeight : 20;
    const padding = Number.isFinite(paddingTop) && paddingTop >= 0 ? paddingTop : 0;
    const scroll = Number.isFinite(scrollTop) && scrollTop > 0 ? scrollTop : 0;
    const viewport = Number.isFinite(viewportHeight) && viewportHeight > 0 ? viewportHeight : 520;
    const firstVisible = Math.max(1, Math.floor(scroll / height) + 1);
    const visibleCount = Math.max(1, Math.ceil(viewport / height));
    const start = Math.min(total, firstVisible);
    const end = Math.min(total, start + Math.min(MAX_RENDERED_LINES, visibleCount + 8) - 1);
    return {start, end, offset: padding + (start - 1) * height - scroll};
  }

  function matches(value, query, limit) {
    const content = typeof value === 'string' ? value : '';
    const needle = typeof query === 'string' ? query : '';
    const maximum = Number.isSafeInteger(limit) && limit > 0 ? limit : MAX_MATCHES;
    if (!needle) return [];
    const expression = new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'giu');
    const result = [];
    for (let match; result.length < maximum && (match = expression.exec(content)) !== null;) {
      result.push({start: match.index, end: match.index + match[0].length});
    }
    return result;
  }

  function selectedMatch(items, cursor, direction) {
    if (!Array.isArray(items) || items.length === 0) return -1;
    if (direction < 0) {
      for (let index = items.length - 1; index >= 0; index--) if (items[index].start < cursor) return index;
      return items.length - 1;
    }
    const found = items.findIndex(item => item.start >= cursor);
    return found < 0 ? 0 : found;
  }

  function statePresentation(metadata) {
    const value = metadata || {};
    if (value.busy) return {className: 'pill neutral', label: 'Loading'};
    if (value.dirty) return {className: 'pill warning', label: 'Unsaved changes'};
    if (value.draft) return {className: 'pill warning', label: 'Snapshot draft'};
    if (value.loaded) return {className: 'pill online', label: 'Confirmed read'};
    return {className: 'pill neutral', label: 'No document'};
  }

  function create(options) {
    const textarea = options && options.textarea;
    if (!textarea) throw new Error('A YAML textarea is required');
    const gutter = options.gutter;
    const position = options.position;
    const statistics = options.statistics;
    const source = options.source;
    const state = options.state;
    const search = options.search;
    const previous = options.previous;
    const next = options.next;
    const matchCount = options.matchCount;
    let currentMatches = [];
    let currentMatch = -1;
    let currentLineCount = 1;
    let metadata = {loaded: false, dirty: false, draft: false, busy: false, nodeId: '', fileName: '', revision: ''};

    function renderGutter() {
      if (!gutter) return;
      const styles = typeof getComputedStyle === 'function' ? getComputedStyle(textarea) : null;
      const lineHeight = styles ? Number.parseFloat(styles.lineHeight) : 20;
      const paddingTop = styles ? Number.parseFloat(styles.paddingTop) : 0;
      const window = lineWindow(currentLineCount, textarea.scrollTop, textarea.clientHeight, lineHeight, paddingTop);
      gutter.textContent = Array.from({length: window.end - window.start + 1},
        (_, index) => window.start + index).join('\n');
      gutter.style.paddingTop = `${window.offset}px`;
    }

    function renderDocument() {
      const current = metrics(textarea.value, textarea.selectionStart);
      currentLineCount = current.lines;
      renderGutter();
      if (position) position.textContent = `Line ${current.line}, Column ${current.column}`;
      if (statistics) statistics.textContent = `${current.lines.toLocaleString()} lines · ${current.characters.toLocaleString()} characters`;
    }

    function renderState() {
      if (source) {
        const identity = metadata.nodeId && metadata.fileName ? `${metadata.nodeId} · ${metadata.fileName}` : 'No document loaded';
        source.textContent = metadata.revision ? `${identity} · revision ${metadata.revision.slice(0, 12)}` : identity;
      }
      if (!state) return;
      const presentation = statePresentation(metadata);
      state.className = presentation.className;
      state.textContent = presentation.label;
    }

    function renderSearch(reset) {
      const query = search ? search.value : '';
      currentMatches = matches(textarea.value, query);
      if (reset || currentMatch >= currentMatches.length) currentMatch = -1;
      if (matchCount) matchCount.textContent = !query ? 'No search' : currentMatches.length >= MAX_MATCHES
        ? `${MAX_MATCHES}+ matches` : `${currentMatches.length} ${currentMatches.length === 1 ? 'match' : 'matches'}`;
      if (previous) previous.disabled = currentMatches.length === 0;
      if (next) next.disabled = currentMatches.length === 0;
    }

    function select(direction) {
      if (!currentMatches.length) return;
      const cursor = currentMatch >= 0
        ? direction < 0 ? currentMatches[currentMatch].start : currentMatches[currentMatch].end
        : textarea.selectionStart;
      currentMatch = selectedMatch(currentMatches, cursor, direction);
      const match = currentMatches[currentMatch];
      textarea.focus();
      textarea.setSelectionRange(match.start, match.end);
      renderDocument();
      if (matchCount) matchCount.textContent = `${currentMatch + 1} of ${currentMatches.length}${currentMatches.length >= MAX_MATCHES ? '+' : ''}`;
    }

    textarea.addEventListener('input', function () { renderDocument(); renderSearch(true); });
    textarea.addEventListener('click', renderDocument);
    textarea.addEventListener('keyup', renderDocument);
    textarea.addEventListener('scroll', renderGutter);
    if (search) search.addEventListener('input', function () { renderSearch(true); });
    if (previous) previous.addEventListener('click', function () { select(-1); });
    if (next) next.addEventListener('click', function () { select(1); });

    renderDocument();
    renderSearch(true);
    renderState();
    return {
      sync(values) {
        metadata = Object.assign({}, metadata, values || {});
        renderDocument();
        renderSearch(true);
        renderState();
      },
      refresh() { renderDocument(); renderSearch(true); renderState(); }
    };
  }

  return {create, metrics, lineWindow, matches, selectedMatch, statePresentation,
    MAX_MATCHES, MAX_RENDERED_LINES};
}));
