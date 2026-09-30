// Run with PLAYWRIGHT_MODULE pointing to an installed Playwright package if it is not on NODE_PATH.
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/resources/web');
const nodes = ['backend', 'proxy'].map(nodeId => ({nodeId, displayName: nodeId === 'backend' ? 'Backend' : 'Proxy',
  platform: nodeId === 'backend' ? 'BUKKIT' : 'VELOCITY', sessionId: nodeId + '-1', online: true,
  acceptedCapabilities: nodeId === 'backend' ? ['config.files.v1', 'config.reward-files.v1', 'config.quick-setup.v1']
    : ['config.proxy-files.v1'], backends: [], detectedPlugins: []}));
let nextOperation = 0;
let proxySession = 'proxy-1';
const requests = [];
const server = http.createServer(async (req, res) => {
  const uri = new URL(req.url, 'http://localhost').pathname;
  const json = value => { res.writeHead(200, {'Content-Type': 'application/json'}); res.end(JSON.stringify(value)); };
  if (!uri.startsWith('/api/')) {
    const file = path.resolve(root, '.' + (uri === '/' ? '/index.html' : uri));
    if (!file.startsWith(root + '/') || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, {'Content-Type': file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html'});
    fs.createReadStream(file).pipe(res); return;
  }
  const parts = []; for await (const part of req) parts.push(part);
  const body = JSON.parse(Buffer.concat(parts).toString() || '{}');
  if (uri === '/api/v1/auth/setup') return json({required: false});
  if (uri === '/api/v1/auth/session') return json({csrfToken: 'fixture'});
  if (uri === '/api/v1/health') return json({status: 'ok'});
  if (uri === '/api/v1/nodes') return json({items: nodes.map(node => node.nodeId === 'proxy' ? {...node, sessionId: proxySession} : node), registryRevision: 1, total: nodes.length});
  if (uri.startsWith('/api/v1/configuration/')) {
    requests.push({uri, body});
    const nodeId = body.nodeIds?.[0] || 'backend';
    const operationId = '00000000-0000-4000-8000-' + String(++nextOperation).padStart(12, '0');
    return json({operationId, state: 'SUCCEEDED', type: uri.endsWith('/preview') ? 'PREVIEW' : 'READ',
      approvalToken: uri.endsWith('/preview') ? 'approval-' + operationId : null,
      configuration: body.configuration,
      nodeStates: {[nodeId]: 'COMPLETE'}, results: {[nodeId]: {success: true, revision: 'r1',
        sessionId: nodeId === 'proxy' ? proxySession : 'backend-1', configuration: {content: 'Enabled: true\n', options: {}}}}});
  }
  return json({items: []});
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1100}});
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  try {
    await page.goto('http://127.0.0.1:' + server.address().port + '/', {waitUntil: 'networkidle'});
    await page.locator('#home-nodes .node[data-node-id=backend] input[type=checkbox]').check();
    await page.locator('#home-continue').click();
    await page.evaluate(() => {
      quickSetupDirty = true;
      quickName.value = 'Retained guided draft';
      dedicatedSetupDirty.add('vote-logging');
      voteLoggingDays.value = '47';
      routingDirty = true;
      routingDraftNodeId = selectedServerId;
      setActiveTab('configurations', true);
      setConfigView('yaml');
    });
    await page.locator('#configuration-target').selectOption('proxy');
    await page.waitForFunction(() => configurationContentPresent && configurationSourceNodeId === 'proxy');
    assert.deepEqual(await page.evaluate(() => ({source: selectedServerId, yaml: selectedConfigurationNodeId(),
      inspected: workspace.inspectedServerId, targets: [...workspace.selectedTargetIds], quick: quickSetupDirty,
      name: quickName.value, dedicated: [...dedicatedSetupDirty], days: voteLoggingDays.value, routing: routingDirty})),
    {source: 'backend', yaml: 'proxy', inspected: 'backend', targets: ['backend'], quick: true,
      name: 'Retained guided draft', dedicated: ['vote-logging'], days: '47', routing: true});
    assert.equal(requests.filter(request => request.body.configuration?.domain === 'file').at(-1).body.nodeIds[0], 'proxy');
    await page.locator('#configuration-content').fill('Enabled: false\n');
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#configuration-target').selectOption('backend');
    assert.equal(await page.locator('#configuration-target').inputValue(), 'proxy');
    assert.equal(await page.locator('#configuration-content').inputValue(), 'Enabled: false\n');
    await page.locator('#preview-file-configuration').click();
    await page.waitForFunction(() => approvedFilePreview !== null);
    assert.equal(requests.filter(request => request.uri.endsWith('/preview')).at(-1).body.nodeIds[0], 'proxy');
    proxySession = 'proxy-2';
    await page.evaluate(() => loadNodes());
    assert.equal(await page.evaluate(() => approvedFilePreview), null);
    assert.equal(await page.evaluate(() => fileDraftMatchesCurrentContext()), false);
    assert.equal(await page.locator('#configuration-content').inputValue(), 'Enabled: false\n');
    // Measure the production toolbar with both the mobile drawer and desktop sidebar.
    const widths = [320, 480, 640, 641, 700, 800, 920, 921, 1000, 1100, 1101, 1280, 1440];
    for (const width of widths) {
      await page.setViewportSize({width, height: 1100});
      const bounds = await page.evaluate(() => {
        const toolbar = document.querySelector('.editor-toolbar');
        const outer = toolbar.getBoundingClientRect();
        return {outer: {left: outer.left, right: outer.right}, client: toolbar.clientWidth, scroll: toolbar.scrollWidth,
          children: [...toolbar.querySelectorAll('label, select, span')].map(element => {
            const rect = element.getBoundingClientRect(); return {id: element.id || element.htmlFor, left: rect.left, right: rect.right, width: rect.width};
          })};
      });
      assert.ok(bounds.scroll <= bounds.client + 1, `toolbar overflow at ${width}: ${JSON.stringify(bounds)}`);
      for (const child of bounds.children) {
        assert.ok(child.left >= bounds.outer.left && child.right <= bounds.outer.right + 1 && child.width > 0,
          `clipped toolbar control at ${width}: ${JSON.stringify(child)}`);
      }
    }
    assert.deepEqual(errors, []);
    console.log('Chromium: independent YAML target, guided drafts, confirmation, preview/session binding, and 13 toolbar widths passed.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
