'use strict';

const health = document.querySelector('#health');
const setupCard = document.querySelector('#setup-card');
const setupForm = document.querySelector('#setup-form');
const setupCode = document.querySelector('#setup-code');
const setupPassword = document.querySelector('#setup-password');
const setupConfirmPassword = document.querySelector('#setup-confirm-password');
const setupMessage = document.querySelector('#setup-message');
const authCard = document.querySelector('#auth-card');
const form = document.querySelector('#auth-form');
const passwordInput = document.querySelector('#password');
const loginButton = form.querySelector('button[type="submit"]');
const logout = document.querySelector('#logout');
const message = document.querySelector('#message');
const welcome = document.querySelector('#welcome');
const appShell = document.querySelector('#app-shell');
const workspace = new ControlWorkspace.Workspace();
let settingsEditor = null;
let voteSitesEditor = null;
let rewardsEditor = null;
const scopeBar = document.querySelector('#scope-bar');
const homeNodes = document.querySelector('#home-nodes');
const homeSearch = document.querySelector('#home-server-search');
let registryAvailable = false;
let activeNavigationHash = '#home';
const sidebarToggle = document.querySelector('#sidebar-toggle');
const primaryNavigation = document.querySelector('#primary-navigation');
const topbar = document.querySelector('.topbar');
const globalSearch = document.querySelector('#global-search');
const globalSearchInput = document.querySelector('#global-search-input');
const globalSearchOptions = document.querySelector('#global-search-options');
const headerAction = document.querySelector('#header-action');
const serverPickerLabel = document.querySelector('#server-picker-label');
const serverPicker = document.querySelector('#server-picker');
const tabButtons = [...document.querySelectorAll('[data-tab]')];
const navigationButtons = [...primaryNavigation.querySelectorAll('button')];
const tabPanels = [...document.querySelectorAll('[data-panel]')];
const configViewButtons = [...document.querySelectorAll('[data-config-view]')];
const configViewPanels = [...document.querySelectorAll('[data-config-panel]')];
const metricHealth = document.querySelector('#metric-health');
const metricHealthDetail = document.querySelector('#metric-health-detail');
const metricVotesToday = document.querySelector('#metric-votes-today');
const metricVoteSites = document.querySelector('#metric-vote-sites');
const metricVoteSitesDetail = document.querySelector('#metric-vote-sites-detail');
const metricProxy = document.querySelector('#metric-proxy');
const metricProxyDetail = document.querySelector('#metric-proxy-detail');
const metricLoggedEvents = document.querySelector('#metric-logged-events');
const metricIssues = document.querySelector('#metric-issues');
const metricIssuesDetail = document.querySelector('#metric-issues-detail');
const refreshDashboardButton = document.querySelector('#refresh-dashboard');
const attentionFeed = document.querySelector('#attention-feed');
const overviewQuickActions = document.querySelector('#overview-quick-actions');
const voteActivity = document.querySelector('#vote-activity');
const overviewActivity = document.querySelector('#overview-activity');
const selectedServerName = document.querySelector('#selected-server-name');
const selectedServerState = document.querySelector('#selected-server-state');
const selectedServerSummary = document.querySelector('#selected-server-summary');
const selectedServerCapabilities = document.querySelector('#selected-server-capabilities');
const configurationContext = document.querySelector('#configuration-context');
const commentPreservationState = document.querySelector('#comment-preservation-state');
const topology = document.querySelector('#topology');
const nodes = document.querySelector('#nodes');
const refresh = document.querySelector('#refresh');
const previousPage = document.querySelector('#previous-page');
const nextPage = document.querySelector('#next-page');
const pageNumber = document.querySelector('#page-number');
const deploymentJar = document.querySelector('#deployment-jar');
const deployPlugin = document.querySelector('#deploy-plugin');
const deploymentEligibility = document.querySelector('#deployment-eligibility');
const deploymentStatus = document.querySelector('#deployment-status');
const sendAll = document.querySelector('#send-all');
const blockedServers = document.querySelector('#blocked-servers');
const readConfiguration = document.querySelector('#read-configuration');
const previewConfiguration = document.querySelector('#preview-configuration');
const applyConfiguration = document.querySelector('#apply-configuration');
const operationStatus = document.querySelector('#operation-status');
const configurationForm = document.querySelector('#configuration-form');
const configurationFile = document.querySelector('#configuration-file');
const configurationContent = document.querySelector('#configuration-content');
const editorPosition = document.querySelector('#editor-position');
const readFileConfiguration = document.querySelector('#read-file-configuration');
const previewFileConfiguration = document.querySelector('#preview-file-configuration');
const applyFileConfiguration = document.querySelector('#apply-file-configuration');
const fileOperationStatus = document.querySelector('#file-operation-status');
const fileConfigurationForm = document.querySelector('#file-configuration-form');
const quickSetupForm = document.querySelector('#quick-setup-form');
const quickPreset = document.querySelector('#quick-preset');
const quickName = document.querySelector('#quick-name');
const quickMethod = document.querySelector('#quick-method');
const quickSiteDisplayName = document.querySelector('#quick-site-display-name');
const quickService = document.querySelector('#quick-service');
const quickUrl = document.querySelector('#quick-url');
const quickDelay = document.querySelector('#quick-delay');
const quickSitePriority = document.querySelector('#quick-site-priority');
const quickSiteMaterial = document.querySelector('#quick-site-material');
const quickSiteEnabled = document.querySelector('#quick-site-enabled');
const quickSiteHidden = document.querySelector('#quick-site-hidden');
const detectedPlugins = document.querySelector('#detected-plugins');
const quickRewardScope = document.querySelector('#quick-reward-scope');
const quickCommand = document.querySelector('#quick-command');
const quickMessage = document.querySelector('#quick-message');
const quickCommandSuggestions = document.querySelector('#quick-command-suggestions');
const quickProcessRewards = document.querySelector('#quick-process-rewards');
const quickAutoSites = document.querySelector('#quick-auto-sites');
const quickAutoSitesOnly = document.querySelector('#quick-auto-sites-only');
const quickVoteLoggingEnabled = document.querySelector('#quick-vote-logging-enabled');
const quickVoteLoggingDays = document.querySelector('#quick-vote-logging-days');
const quickVoteLoggingMainMysql = document.querySelector('#quick-vote-logging-main-mysql');
const quickExtraCheck = document.querySelector('#quick-extra-check');
const quickCountFake = document.querySelector('#quick-count-fake');
const quickHideSiteWarning = document.querySelector('#quick-hide-site-warning');
const quickDisableUpdates = document.querySelector('#quick-disable-updates');
const quickPartyEnabled = document.querySelector('#quick-party-enabled');
const quickPartyVotes = document.querySelector('#quick-party-votes');
const quickPartyCommand = document.querySelector('#quick-party-command');
const quickPartyBroadcast = document.querySelector('#quick-party-broadcast');
const quickPartyAll = document.querySelector('#quick-party-all');
const quickPartyOnline = document.querySelector('#quick-party-online');
const readQuickSetup = document.querySelector('#read-quick-setup');
const previewQuickSetup = document.querySelector('#preview-quick-setup');
const applyQuickSetup = document.querySelector('#apply-quick-setup');
const quickOperationStatus = document.querySelector('#quick-operation-status');
const voteSitesSource = document.querySelector('#vote-sites-source');
const voteSitesTargets = document.querySelector('#vote-sites-targets');
const voteSitesSyncCapability = document.querySelector('#vote-sites-sync-capability');
const transportTestProxy = document.querySelector('#transport-test-proxy');
const transportTestBackend = document.querySelector('#transport-test-backend');
const transportTestCapability = document.querySelector('#transport-test-capability');
const runTransportTest = document.querySelector('#run-transport-test');
const transportTestStatus = document.querySelector('#transport-test-status');
const proxyMethodProxy = document.querySelector('#proxy-method-proxy');
const proxyMethodCapability = document.querySelector('#proxy-method-capability');
const proxyMethodButtons = [...document.querySelectorAll('[data-proxy-method]')];
const readProxyMethod = document.querySelector('#read-proxy-method');
const proxyMethodCurrent = document.querySelector('#proxy-method-current');
const proxyMethodStatus = document.querySelector('#proxy-method-status');
const enrollmentCard = document.querySelector('#enrollment-card');
const enrollmentForm = document.querySelector('#enrollment-form');
const enrollmentSubmit = enrollmentForm.querySelector('button[type="submit"]');
const enrollmentNodeId = document.querySelector('#enrollment-node-id');
const enrollmentCredential = document.querySelector('#enrollment-credential');
const enrollmentList = document.querySelector('#enrollment-list');
const enrollmentMessage = document.querySelector('#enrollment-message');
const refreshEnrollments = document.querySelector('#refresh-enrollments');
const networkDoctorCapability = document.querySelector('#network-doctor-capability');
const runNetworkDoctor = document.querySelector('#run-network-doctor');
const downloadNetworkDiagnostics = document.querySelector('#download-network-diagnostics');
const networkDoctorResults = document.querySelector('#network-doctor-results');
const driftFile = document.querySelector('#drift-file');
const driftCapability = document.querySelector('#drift-capability');
const runDriftCheck = document.querySelector('#run-drift-check');
const driftResults = document.querySelector('#drift-results');
const snapshotForm = document.querySelector('#snapshot-form');
const snapshotName = document.querySelector('#snapshot-name');
const createSnapshot = document.querySelector('#create-snapshot');
const refreshSnapshots = document.querySelector('#refresh-snapshots');
const snapshotList = document.querySelector('#snapshot-list');
const snapshotStatus = document.querySelector('#snapshot-status');
const refreshSetupChecklist = document.querySelector('#refresh-setup-checklist');
const setupChecklist = document.querySelector('#setup-checklist');
const setupChecklistStatus = document.querySelector('#setup-checklist-status');
const autoSitesEnabled = document.querySelector('#auto-sites-enabled');
const autoSitesState = document.querySelector('#auto-sites-state');
const autoSitesTargetCount = document.querySelector('#auto-sites-target-count');
const selectAllAutoSitesTargets = document.querySelector('#select-all-auto-sites-targets');
const loadAutoSites = document.querySelector('#load-auto-sites');
const previewAutoSites = document.querySelector('#preview-auto-sites');
const applyAutoSites = document.querySelector('#apply-auto-sites');
const autoSitesStatus = document.querySelector('#auto-sites-status');
const voteLoggingEnabled = document.querySelector('#vote-logging-enabled');
const voteLoggingDays = document.querySelector('#vote-logging-days');
const voteLoggingMainMysql = document.querySelector('#vote-logging-main-mysql');
const voteLoggingState = document.querySelector('#vote-logging-state');
const loadVoteLogging = document.querySelector('#load-vote-logging');
const previewVoteLogging = document.querySelector('#preview-vote-logging');
const applyVoteLogging = document.querySelector('#apply-vote-logging');
const voteLoggingStatus = document.querySelector('#vote-logging-status');
const profileName = document.querySelector('#profile-name');
const profilePicker = document.querySelector('#profile-picker');
const saveProfile = document.querySelector('#save-profile');
const loadProfile = document.querySelector('#load-profile');
const deleteProfile = document.querySelector('#delete-profile');
const profileStatus = document.querySelector('#profile-status');
const rewardSimulationForm = document.querySelector('#reward-simulation-form');
const rewardScope = document.querySelector('#reward-scope');
const rewardSiteLabel = document.querySelector('#reward-site-label');
const rewardSite = document.querySelector('#reward-site');
const rewardChance = document.querySelector('#reward-chance');
const rewardMoney = document.querySelector('#reward-money');
const rewardCommands = document.querySelector('#reward-commands');
const rewardMessages = document.querySelector('#reward-messages');
const rewardBroadcasts = document.querySelector('#reward-broadcasts');
const rewardPermissions = document.querySelector('#reward-permissions');
const rewardItems = document.querySelector('#reward-items');
const rewardOnlineOnly = document.querySelector('#reward-online-only');
const simulateReward = document.querySelector('#simulate-reward');
const previewReward = document.querySelector('#preview-reward');
const applyReward = document.querySelector('#apply-reward');
const copyRewardToSetup = document.querySelector('#copy-reward-to-setup');
const rewardSimulationCapability = document.querySelector('#reward-simulation-capability');
const rewardSimulationResult = document.querySelector('#reward-simulation-result');
const settingsFilter = document.querySelector('#settings-filter');
const settingsCatalog = document.querySelector('#settings-catalog');
const refreshDataOverview = document.querySelector('#refresh-data-overview');
const dataOverview = document.querySelector('#data-overview');
const playerLookupForm = document.querySelector('#player-lookup-form');
const playerLookup = document.querySelector('#player-lookup');
const lookupPlayer = document.querySelector('#lookup-player');
const playerResult = document.querySelector('#player-result');
const loadSiteHealth = document.querySelector('#load-site-health');
const siteHealthResult = document.querySelector('#site-health-result');
const loadVoteLogSummary = document.querySelector('#load-vote-log-summary');
const voteLogSummaryResult = document.querySelector('#vote-log-summary-result');
const voteLogForm = document.querySelector('#vote-log-form');
const voteLogFilterType = document.querySelector('#vote-log-filter-type');
const voteLogFilter = document.querySelector('#vote-log-filter');
const voteLogEvent = document.querySelector('#vote-log-event');
const voteLogDays = document.querySelector('#vote-log-days');
const voteLogLimit = document.querySelector('#vote-log-limit');
const searchVoteLog = document.querySelector('#search-vote-log');
const voteLogResult = document.querySelector('#vote-log-result');
const voteTraceForm = document.querySelector('#vote-trace-form');
const voteTraceId = document.querySelector('#vote-trace-id');
const traceVote = document.querySelector('#trace-vote');
const voteTraceResult = document.querySelector('#vote-trace-result');
const siteResolutionForm = document.querySelector('#site-resolution-form');
const siteResolutionService = document.querySelector('#site-resolution-service');
const siteResolutionDisabled = document.querySelector('#site-resolution-disabled');
const resolveSite = document.querySelector('#resolve-site');
const siteResolutionResult = document.querySelector('#site-resolution-result');
const operationHistory = document.querySelector('#operation-history');
const clearOperationHistory = document.querySelector('#clear-operation-history');
const PAGE_SIZE = 100;
const MAX_CONFIGURATION_TARGETS = 100;
const MAX_SYNC_TARGETS = 100;
const MAX_OPERATION_TARGETS = 100;
const MAX_DEPLOYMENT_BATCHES = 100;
const MAX_TRACE_NODES = 12;
const MAX_TRACE_EVENTS_PER_NODE = 100;
const MAX_PLAYER_LAST_VOTES = 100;
const PLAYER_STRING_COLUMNS = new Set(['UUID', 'PlayerName', 'LastOnline', 'DayVoteStreakLastUpdate', 'VoteRemindersLast']);
const PLAYER_BOOLEAN_COLUMNS = new Set(['TopVoterIgnore', 'Reminded', 'DisableBroadcast', 'CoolDownCheck']);
const PLAYER_INTEGER_COLUMNS = new Set(['VotePartyVotes', 'MonthTotal', 'AllTimeTotal', 'DailyTotal', 'WeeklyTotal',
  'Points', 'DayVoteStreak', 'BestDayVoteStreak', 'WeekVoteStreak', 'BestWeekVoteStreak', 'MonthVoteStreak',
  'BestMonthVoteStreak', 'HighestDailyTotal', 'HighestMonthlyTotal', 'HighestWeeklyTotal', 'LastMonthTotal',
  'LastWeeklyTotal', 'LastDailyTotal', 'AllSitesLast', 'AlmostAllSitesLast']);
const VOTE_LOG_EVENTS = new Set(['VOTE_RECEIVED', 'VOTEMILESTONE', 'VOTE_STREAK_REWARD', 'TOP_VOTER_REWARD',
  'VOTESHOP_PURCHASE']);
const VOTE_LOG_STATUSES = new Set(['IMMEDIATE', 'CACHED']);
const TRACE_DEADLINE_MS = 90_000;
const MAX_REGISTRY_SCAN_ATTEMPTS = 3;
let authenticated = false;
let csrfToken = '';
let pageOffset = 0;
let selectedNodes = new Set();
let selectedServerId = '';
let visibleNodeItems = [];
let allNodeItems = [];
let nodePageMetadata = new Map();
let nodeIndex = new Map();
let enrollmentIds = new Set();
let enrollmentsLoaded = false;
let backendTopologyTruncated = false;
let backendTopologyTruncatedNodeIds = new Set();
let approvedPreview = null;
let approvedFilePreview = null;
let approvedQuickPreview = null;
let loadedQuickSetup = null;
let quickSetupDirty = false;
let quickSetupPreserveReadGeneration = -1;
const dedicatedSetupDirty = new Set();
let voteSitesSourceId = '';
let voteSitesTargetIds = new Set();
let voteSitesTargetsInitialized = false;
let transportTestProxyId = '';
let transportTestBackendId = '';
let proxyMethodProxyId = '';
let proxyMethodCurrentFor = '';
let proxyMethodCurrentSessionId = '';
let proxyMethodCurrentReadCapability = '';
let proxyMethodCurrentValue = '';
let nodeCapabilities = new Map();
let nodePlugins = new Map();
let inputGeneration = 0;
let authenticationGeneration = 0;
let loginInFlight = false;
let logoutInFlight = false;
let setupRequired = false;
let enrollmentInFlight = false;
let enrollmentRefreshRequested = false;
let enrollmentRefreshPromise = null;
let enrollmentRefreshResolve = null;
let enrollmentMutationInFlight = false;
let configurationOperationsInFlight = 0;
let proxyMethodWorkflowInFlight = false;
let voteSiteReadTimer = null;
let deploymentInFlight = false;
let deploymentRunGeneration = 0;
const FILE_READ_CACHE_TTL_MS = 30_000;
const MAX_FILE_READ_CACHE_ENTRIES = 12;
const MAX_OPERATION_HISTORY = 50;
const SETUP_PROFILE_KEY = 'votingplugin-control.setup-profiles.v1';
let fileReadCache = new Map();
let lastFileReadOperation = null;
let configurationContentPresent = false;
let configurationDirty = false;
let configurationDraftNodeId = '';
let configurationDraftSessionId = '';
let configurationDraftFileName = '';
let routingDirty = false;
let routingDraftNodeId = '';
let configurationFileSelection = configurationFile.value;
let inspectionInFlight = false;
let lastDiagnostics = null;
let lastOverview = null;
let dashboardOverview = null;
let dashboardVoteSiteHealth = null;
let dashboardVoteSummary24h = null;
let dashboardVoteSummary30d = null;
let dashboardLoadedContext = '';
let dashboardInspectionStatus = emptyDashboardInspectionStatus();
let dashboardTopologySignature = '';
let dashboardConfigurationGeneration = 0;
let dashboardLoading = false;
let operationHistoryItems = [];
let deploymentHistoryItems = [];
let observedServerConfigurationGeneration = null;
let operationHistoryStatus = 'not-loaded';
let enrollmentStatus = 'not-loaded';
let dedicatedSetupApprovals = new Map();
let pendingDetectedVoteSite = null;
let voteLoggingRestartPending = new Map();
let autoLoadInFlight = new Set();
let autoLoadPending = new Set();
let nodeLoadInFlight = null;
let nodeLoadQueued = false;
let nodeLoadQueuedPromise = null;
let nodeLoadQueuedResolve = null;
let nodeLoadQueuedReject = null;
let suppressNodeAutoLoad = 0;
let operationHistoryLoadInFlight = null;
let operationHistoryLoadQueued = false;
const appStylesheet = Array.from(document.styleSheets).find(sheet => sheet.href?.endsWith('/app.css'));
const rootStyleRule = appStylesheet
  ? Array.from(appStylesheet.cssRules).find(rule => rule.selectorText === ':root')
  : null;

function syncTopbarOffset() {
  const topbarBounds = topbar.getBoundingClientRect();
  const searchBounds = globalSearch.hidden ? topbarBounds : globalSearch.getBoundingClientRect();
  const height = Math.ceil(Math.max(topbarBounds.bottom, searchBounds.bottom));
  rootStyleRule?.['style'].setProperty('--topbar-height', `${height}px`);
  rootStyleRule?.['style'].setProperty('--workspace-scope-height', `${scopeBar.hidden ? 0 : Math.ceil(scopeBar.getBoundingClientRect().height)}px`);
}

function scrollToAnchor(target) {
  if (!target) return;
  syncTopbarOffset();
  target.scrollIntoView({behavior: 'smooth', block: 'start'});
}

syncTopbarOffset();
if (typeof ResizeObserver === 'function') {
  const chromeObserver = new ResizeObserver(syncTopbarOffset);
  chromeObserver.observe(topbar);
  chromeObserver.observe(scopeBar);
}
window.addEventListener('resize', syncTopbarOffset);

function text(element, value) {
  element.textContent = value;
  return element;
}

const SETTINGS_SCHEMA = Object.freeze([
  {key: 'AutoCreateVoteSites', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Create a VoteSites.yml entry when an unknown service votes.'},
  {key: 'ProcessRewards', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Run configured vote rewards on this backend.'},
  {key: 'VoteLogging.Enabled', file: 'Config.yml', type: 'boolean', defaultValue: 'false', effect: 'Store supported vote events in MySQL for searches and traces.', afterApply: 'Backend restart required'},
  {key: 'VoteLogging.PurgeDays', file: 'Config.yml', type: 'integer -1 or 1–3650', defaultValue: '30', effect: 'Retention window for vote-log rows; -1 disables automatic purging.'},
  {key: 'VoteLogging.UseMainMySQL', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Reuse the main MySQL connection for vote logging.', afterApply: 'Backend restart required'},
  {key: 'CountFakeVotes', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Include explicitly generated test votes in totals.'},
  {key: 'ExtraAllSitesCheck', file: 'Config.yml', type: 'boolean', defaultValue: 'false', effect: 'Add duplicate protection for all-sites rewards.'},
  {key: 'UseBungeecord', file: 'BungeeSettings.yml', type: 'boolean', defaultValue: 'false', effect: 'Run this node as a proxy-connected backend.'},
  {key: 'BungeeMethod', file: 'BungeeSettings.yml', type: 'enum', defaultValue: 'PLUGINMESSAGING', effect: 'Select the proxy transport.'},
  {key: 'VoteSites.<site>.Enabled', file: 'VoteSites.yml', type: 'boolean', defaultValue: 'true', effect: 'Allow a configured site to resolve and reward votes.'},
  {key: 'VoteSites.<site>.ServiceSite', file: 'VoteSites.yml', type: 'text ≤200', defaultValue: '', effect: 'Match the service name supplied by the vote listener.'},
  {key: 'VoteParty.Enabled', file: 'SpecialRewards.yml', type: 'boolean', defaultValue: 'false', effect: 'Enable or disable Vote Party without changing other party settings.'},
  {key: 'VoteParty.VotesRequired', file: 'SpecialRewards.yml', type: 'integer 1–100000', defaultValue: '20', effect: 'Number of votes required to trigger a vote party.'}
]);

function inspectionCapableNode() {
  const node = nodeIndex.get(selectedServerId);
  return node?.online && node.acceptedCapabilities.includes('data.inspect.v1') ? node : null;
}

function connectedInspectionNodes() {
  return allNodeItems.filter(node => node.online && isBackend(node)
    && node.acceptedCapabilities.includes('data.inspect.v1'));
}

function selectedFileCapability(fileName = configurationFile.value) {
  return fileName === 'bungeeconfig.yml' ? 'config.proxy-files.v1'
    : /^Rewards\/[A-Za-z0-9][A-Za-z0-9_-]{0,99}\.yml$/.test(fileName) ? 'config.reward-files.v1'
      : 'config.files.v1';
}

function fileTargetsForSelection(fileName = configurationFile.value) {
  const capability = selectedFileCapability(fileName);
  const selected = nodeIndex.get(selectedServerId);
  if (capability === 'config.proxy-files.v1') {
    return workspace.managementScope === 'GLOBAL' && selected?.online && isProxy(selected) && selected.acceptedCapabilities.includes(capability)
      ? [selected.nodeId] : [];
  }
  return targets(capability).filter(nodeId => isBackend(nodeIndex.get(nodeId)));
}

function fileTargetDescription(fileName = configurationFile.value) {
  const targetsForFile = fileTargetsForSelection(fileName);
  if (fileName === 'bungeeconfig.yml') return targetsForFile.length
    ? `the selected proxy (${targetsForFile[0]})` : 'the selected proxy';
  return `every selected Bukkit node`;
}

function boundedLines(value, maximum = 20) {
  const lines = value.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
  if (lines.length > maximum) throw new Error(`At most ${maximum} reward lines are allowed.`);
  return lines;
}

function pruneFileReadCache() {
  const cutoff = Date.now() - FILE_READ_CACHE_TTL_MS;
  for (const [key, value] of fileReadCache) if (value.loadedAt < cutoff) fileReadCache.delete(key);
  while (fileReadCache.size > MAX_FILE_READ_CACHE_ENTRIES) fileReadCache.delete(fileReadCache.keys().next().value);
}

function cachedFile(key) {
  pruneFileReadCache();
  const value = fileReadCache.get(key);
  if (!value) return null;
  fileReadCache.delete(key);
  fileReadCache.set(key, value);
  return value;
}

function cacheFile(key, content, operationId) {
  if (typeof content !== 'string') return;
  pruneFileReadCache();
  fileReadCache.delete(key);
  fileReadCache.set(key, {content, operationId, loadedAt: Date.now()});
  pruneFileReadCache();
}

function renderJsonResult(element, value, emptyMessage = 'No data returned.') {
  element.replaceChildren();
  if (value == null) {
    text(element, emptyMessage);
    return;
  }
  const pre = document.createElement('pre');
  pre.className = 'json-result';
  text(pre, JSON.stringify(value, null, 2));
  element.append(pre);
}

function formatEpoch(value) {
  const epoch = Number(value);
  return Number.isFinite(epoch) && epoch > 0 ? new Date(epoch).toLocaleString() : 'Unknown';
}

function plainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function exactObjectKeys(value, expected) {
  if (!plainObject(value)) return false;
  const keys = Object.keys(value).sort();
  return keys.length === expected.length && keys.every((key, index) => key === expected[index]);
}

function validPlayerColumn(column) {
  if (!exactObjectKeys(column, ['name', 'type', 'value'])) return false;
  if (typeof column.name !== 'string' || column.name.length > 100
      || typeof column.type !== 'string' || typeof column.value !== 'string'
      || new TextEncoder().encode(column.value).length > 16 * 1024) return false;
  const runtimeSuffix = suffix => suffix.length > 0 && suffix.length <= 64
    && !/[\u0000-\u001f\u007f-\u009f]/.test(suffix);
  const runtimeString = column.name === 'CoolDownCheck_Sites'
    || column.name.startsWith('CoolDownCheck_') && column.name.endsWith('_Sites')
      && runtimeSuffix(column.name.slice('CoolDownCheck_'.length, -'_Sites'.length));
  if (PLAYER_STRING_COLUMNS.has(column.name) || runtimeString) return column.type === 'STRING';
  const runtimeBoolean = column.name.startsWith('CoolDownCheck_')
    && runtimeSuffix(column.name.slice('CoolDownCheck_'.length));
  if (PLAYER_BOOLEAN_COLUMNS.has(column.name) || runtimeBoolean) {
    return column.type === 'BOOLEAN' && /^(?:true|false)$/.test(column.value)
      || column.type === 'STRING' && /^(?:true|false)$/i.test(column.value);
  }
  const runtimeInteger = ['AllSitesLast_', 'AlmostAllSitesLast_'].some(prefix => column.name.startsWith(prefix)
    && runtimeSuffix(column.name.slice(prefix.length)));
  const integerName = PLAYER_INTEGER_COLUMNS.has(column.name) || runtimeInteger
    || /^(?:MonthTotal-(?:JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)-[0-9]{4}|VoteShopLimit[A-Za-z0-9_-]{1,64})$/.test(column.name);
  if (!integerName || column.type !== 'INTEGER' || !/^-?(?:0|[1-9][0-9]*)$/.test(column.value)) return false;
  const number = Number(column.value);
  return Number.isInteger(number) && number >= -2147483648 && number <= 2147483647;
}

function validPlayerLastVote(lastVote) {
  if (!exactObjectKeys(lastVote, ['displayName', 'serviceSite', 'siteKey', 'time'])
      || !Number.isSafeInteger(lastVote.time) || lastVote.time < 0) return false;
  const limits = {siteKey: 64, displayName: 100, serviceSite: 64};
  return Object.entries(limits).every(([field, maximum]) => typeof lastVote[field] === 'string'
    && lastVote[field].length <= maximum && !/[\u0000-\u001f\u007f-\u009f]/.test(lastVote[field]));
}

function validVoteTraceEvent(event, voteId) {
  const fields = ['cachedTotal', 'context', 'event', 'playerName', 'playerUuid', 'server', 'service', 'status',
    'voteId', 'voteTime'];
  if (!exactObjectKeys(event, fields) || event.voteId !== voteId
      || !Number.isSafeInteger(event.voteTime) || event.voteTime < 0
      || !Number.isInteger(event.cachedTotal) || event.cachedTotal < -2147483648 || event.cachedTotal > 2147483647) return false;
  const limits = {voteId: 36, playerUuid: 36, playerName: 16, service: 64, server: 64, event: 64, context: 255,
    status: 16};
  if (!Object.entries(limits).every(([field, maximum]) => typeof event[field] === 'string'
      && event[field].length <= maximum && !/[\u0000-\u001f\u007f-\u009f]/.test(event[field]))) return false;
  return VOTE_LOG_EVENTS.has(event.event) && VOTE_LOG_STATUSES.has(event.status);
}

function renderPlayerData(value) {
  playerResult.replaceChildren();
  if (!value || value.found !== true) {
    text(playerResult, value?.found === false ? 'Player not found.' : 'No player queried.');
    return;
  }
  const summary = document.createElement('p');
  summary.append(text(document.createElement('strong'), `${value.name || 'Unknown'} · ${value.uuid || 'Unknown'}`));
  const storage = typeof value.storage === 'string' ? ` · ${value.storage}`
    : value.storageRowAvailable === false ? ' · stored row unavailable' : '';
  const columnCount = Array.isArray(value.columns) ? ` · ${value.columns.length} stored columns` : '';
  summary.append(document.createTextNode(`${storage}${columnCount}`));
  playerResult.append(summary);
  const profile = document.createElement('dl');
  profile.className = 'detail-list';
  const add = (label, detail) => {
    profile.append(text(document.createElement('dt'), label));
    profile.append(text(document.createElement('dd'), detail));
  };
  const totals = value.totals && typeof value.totals === 'object' ? value.totals : {};
  add('Online', value.online === true ? 'Yes' : 'No');
  add('Votes', `Daily ${totals.daily ?? 'Unknown'} · Weekly ${totals.weekly ?? 'Unknown'} · Monthly ${totals.monthly ?? 'Unknown'} · All-time ${totals.allTime ?? 'Unknown'}`);
  const streaks = value.streaks && typeof value.streaks === 'object' ? value.streaks : {};
  add('Vote streaks', `Daily ${streaks.daily ?? 'Unknown'} · Weekly ${streaks.weekly ?? 'Unknown'} · Monthly ${streaks.monthly ?? 'Unknown'}`);
  add('Points', value.points ?? 'Unknown');
  add('Pending offline votes', value.pendingOfflineVotes ?? 'Unknown');
  add('Last vote', formatEpoch(value.lastVoteTime));
  add('Last online', formatEpoch(value.lastOnline));
  playerResult.append(profile);
  const receivedLastVotes = Array.isArray(value.lastVotes) ? value.lastVotes : [];
  const malformedLastVotes = value.lastVotes !== undefined
    && (!Array.isArray(value.lastVotes) || receivedLastVotes.some(lastVote => !validPlayerLastVote(lastVote)));
  const lastVotes = malformedLastVotes ? [] : receivedLastVotes.slice(0, MAX_PLAYER_LAST_VOTES);
  if (lastVotes.length) {
    const heading = text(document.createElement('h4'), 'VoteSite history');
    const scroll = document.createElement('div');
    scroll.className = 'table-scroll';
    const table = document.createElement('table');
    const head = document.createElement('thead');
    const headRow = document.createElement('tr');
    ['Vote Site', 'ServiceSite', 'Last vote'].forEach(label => headRow.append(text(document.createElement('th'), label)));
    head.append(headRow);
    const body = document.createElement('tbody');
    lastVotes.forEach(lastVote => {
      const row = document.createElement('tr');
      row.append(text(document.createElement('td'), lastVote.displayName || lastVote.siteKey || 'Unknown'));
      row.append(text(document.createElement('td'), lastVote.serviceSite || 'Not configured'));
      row.append(text(document.createElement('td'), formatEpoch(lastVote.time)));
      body.append(row);
    });
    table.append(head, body);
    scroll.append(table);
    playerResult.append(heading, scroll);
  }
  if (malformedLastVotes) {
    const warning = document.createElement('p');
    warning.className = 'warning-text';
    text(warning, 'VoteSite history is unavailable because the node returned malformed history data.');
    playerResult.append(warning);
  } else if (value.lastVotesTruncated === true || receivedLastVotes.length > MAX_PLAYER_LAST_VOTES) {
    const warning = document.createElement('p');
    warning.className = 'warning-text';
    text(warning, `Additional VoteSite history was omitted by the ${MAX_PLAYER_LAST_VOTES}-row inspection limit.`);
    playerResult.append(warning);
  }
  const legacyStorageMetadata = value.storageRowAvailable === undefined && value.storage === undefined
    && value.columns === undefined && value.columnsTruncated === undefined;
  const columnsOmittedForUnavailableStorage = value.storageRowAvailable === false && value.columns === undefined;
  if (!legacyStorageMetadata && !columnsOmittedForUnavailableStorage
      && (!Array.isArray(value.columns) || value.columns.some(column => !validPlayerColumn(column)))) {
    const warning = document.createElement('p');
    warning.className = 'warning-text';
    text(warning, 'Stored values are unavailable because the node returned fields outside the allow-listed column schema.');
    playerResult.append(warning);
    return;
  }
  if (legacyStorageMetadata || columnsOmittedForUnavailableStorage) return;
  const columns = value.columns.slice(0, 100);
  const scroll = document.createElement('div');
  scroll.className = 'table-scroll';
  const table = document.createElement('table');
  const head = document.createElement('thead');
  const headRow = document.createElement('tr');
  ['Column', 'Storage type', 'Exact stored value'].forEach(label => headRow.append(text(document.createElement('th'), label)));
  head.append(headRow);
  const body = document.createElement('tbody');
  columns.forEach(column => {
    const row = document.createElement('tr');
    row.append(text(document.createElement('td'), column.name));
    row.append(text(document.createElement('td'), column.type));
    const cell = document.createElement('td');
    const code = document.createElement('code');
    text(code, column.value);
    cell.append(code);
    row.append(cell);
    body.append(row);
  });
  table.append(head, body);
  scroll.append(table);
  playerResult.append(scroll);
  if (value.columnsTruncated === true || columns.length < value.columns.length) {
    const warning = document.createElement('p');
    warning.className = 'warning-text';
    text(warning, 'Some stored values were omitted by the bounded, allow-listed inspection contract.');
    playerResult.append(warning);
  }
}

async function loadPlayerData(value) {
  const request = value.trim();
  const filters = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(request)
    ? {uuid: request} : {name: request};
  if (!filters.uuid && !/^[A-Za-z0-9_]{1,16}$/.test(request)) {
    throw new Error('Enter a complete Minecraft player name or canonical UUID.');
  }
  try {
    renderPlayerData((await runInspection('player', filters, playerResult)).result);
  } catch (error) {
    text(playerResult, error.message);
    throw error;
  }
}

function renderSiteHealthResult(value) {
  renderJsonResult(siteHealthResult, value);
  const services = Array.isArray(value?.detectedUnconfiguredServices)
    ? value.detectedUnconfiguredServices.slice(0, 20) : [];
  if (services.length === 0) return;
  const actions = document.createElement('div');
  actions.className = 'detected-actions';
  actions.append(text(document.createElement('strong'), 'Create a reviewed VoteSites entry:'));
  services.forEach(service => {
    const button = text(document.createElement('button'), String(service));
    button.type = 'button';
    button.className = 'secondary compact';
    button.addEventListener('click', () => {
      const key = String(service).replace(/[^A-Za-z0-9_-]/g, '-').replace(/-+/g, '-').slice(0, 64) || 'vote-site';
      quickPreset.value = 'vote-site';
      quickName.value = key;
      quickSiteDisplayName.value = String(service).slice(0, 200);
      quickService.value = String(service).slice(0, 200);
      pendingDetectedVoteSite = {nodeId: selectedServerId, key, service: String(service).slice(0, 200)};
      selectedNodes = new Set(selectedServerId ? [selectedServerId] : []);
      loadedQuickSetup = null;
      quickSetupDirty = false;
      quickSetupPreserveReadGeneration = -1;
      updateQuickFields();
      clearApprovals();
      renderNodeViews();
      updatePluginSuggestions();
      setActiveTab('quick-setup', true);
      text(quickOperationStatus, 'Detected service copied into the VoteSite setup. Control is checking that the generated key is unused; complete the URL and delay, then preview before creating it.');
      scrollToAnchor(document.querySelector('#quick-setup-card'));
    });
    actions.append(button);
  });
  siteHealthResult.append(actions);
}

function downloadJson(name, value) {
  const blob = new Blob([JSON.stringify(value, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function runInspection(kind, filters = {}, statusElement = null, options = {}) {
  const node = inspectionCapableNode();
  if (!node) throw new Error('Choose a connected backend with data inspection support.');
  const manageBusy = options.manageBusy !== false;
  if (manageBusy && inspectionInFlight) throw new Error('Another read-only inspection is still running.');
  const nodeId = node.nodeId;
  const sessionId = node.sessionId;
  const requestAuthenticationGeneration = authenticationGeneration;
  if (manageBusy) {
    inspectionInFlight = true;
    updateExtendedButtons();
  }
  if (statusElement) text(statusElement, `Queued ${kind} inspection…`);
  try {
    const boundedFilters = {};
    Object.entries(filters).forEach(([key, value]) => {
      const serialized = String(value);
      const maximum = kind === 'reward-simulation' && key === 'proposal' ? 64 * 1024 : 500;
      const size = new TextEncoder().encode(serialized).length;
      if (size > maximum) throw new Error(`${key} exceeds the bounded inspection limit.`);
      boundedFilters[key] = serialized;
    });
    let inspection = await authorized('/api/v1/inspections', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({nodeId, query: {kind, filters: boundedFilters}})
    });
    if (statusElement) text(statusElement, `Running ${kind} inspection on ${nodeId}…`);
    const deadline = Date.now() + 180_000;
    while (inspection.state === 'RUNNING') {
      if (Date.now() >= deadline) throw new Error('Inspection is still running after three minutes. Check node connectivity and try again.');
      await new Promise(resolve => window.setTimeout(resolve, 1000));
      inspection = await authorized(`/api/v1/inspections/${inspection.inspectionId}`);
    }
    if (requestAuthenticationGeneration !== authenticationGeneration
        || nodeId !== selectedServerId || sessionId !== nodeIndex.get(nodeId)?.sessionId) {
      throw new Error('The selected server changed or reconnected while the inspection ran. Run it again.');
    }
    if (inspection.state !== 'SUCCEEDED' || !inspection.result?.success) {
      throw new Error(inspection.result?.message || inspection.result?.code || 'Inspection failed.');
    }
    let envelope = inspection.result.data;
    if (typeof envelope === 'string') {
      try { envelope = JSON.parse(envelope); } catch (_) { throw new Error('The node returned malformed inspection data.'); }
    }
    if (envelope?.schemaVersion !== 1 || envelope.kind !== kind || !Object.hasOwn(envelope, 'result')) {
      throw new Error('The node returned an unsupported inspection schema.');
    }
    return envelope;
  } finally {
    if (manageBusy) {
      inspectionInFlight = false;
      updateExtendedButtons();
    }
  }
}

async function runInspectionOnNode(node, kind, filters = {}, options = {}) {
  const nodeId = node?.nodeId;
  const sessionId = node?.sessionId;
  if (!nodeId || !sessionId) throw new Error('The inspection node is unavailable.');
  const requestAuthenticationGeneration = authenticationGeneration;
  const boundedFilters = {};
  Object.entries(filters).forEach(([key, value]) => {
    const serialized = String(value);
    if (new TextEncoder().encode(serialized).length > 500) throw new Error(`${key} exceeds the bounded inspection limit.`);
    boundedFilters[key] = serialized;
  });
  if (options.manageBusy !== false) {
    inspectionInFlight = true;
    updateExtendedButtons();
  }
  const requestedDeadline = Number(options.deadlineAt);
  const deadline = Number.isFinite(requestedDeadline)
    ? Math.min(Date.now() + 180_000, requestedDeadline)
    : Date.now() + 180_000;
  const ensureActive = () => {
    if (typeof options.contextCurrent === 'function' && !options.contextCurrent()) {
      throw new Error('The trace context changed while an inspection was running.');
    }
    if (Date.now() >= deadline || options.signal?.aborted) {
      throw new Error('Inspection did not finish within this request budget.');
    }
  };
  const request = async (path, requestOptions = {}) => {
    ensureActive();
    try {
      const response = await authorized(path, {...requestOptions, signal: options.signal});
      ensureActive();
      return response;
    } catch (error) {
      if (Date.now() >= deadline || options.signal?.aborted) {
        throw new Error('Inspection did not finish within this request budget.');
      }
      throw error;
    }
  };
  try {
    let inspection = await request('/api/v1/inspections', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({nodeId, query: {kind, filters: boundedFilters}})
    });
    while (inspection.state === 'RUNNING') {
      ensureActive();
      await new Promise(resolve => window.setTimeout(resolve, Math.min(1000, Math.max(0, deadline - Date.now()))));
      inspection = await request(`/api/v1/inspections/${inspection.inspectionId}`);
    }
    if (requestAuthenticationGeneration !== authenticationGeneration || sessionId !== nodeIndex.get(nodeId)?.sessionId) {
      throw new Error('The node reconnected or the session changed while the trace ran.');
    }
    if (inspection.state !== 'SUCCEEDED' || !inspection.result?.success) {
      throw new Error(inspection.result?.message || inspection.result?.code || 'Inspection failed.');
    }
    let envelope = inspection.result.data;
    if (typeof envelope === 'string') {
      try { envelope = JSON.parse(envelope); } catch (_) { throw new Error('The node returned malformed inspection data.'); }
    }
    if (envelope?.schemaVersion !== 1 || envelope.kind !== kind || !Object.hasOwn(envelope, 'result')) {
      throw new Error('The node returned an unsupported inspection schema.');
    }
    return envelope;
  } finally {
    if (options.manageBusy !== false) {
      inspectionInFlight = false;
      updateExtendedButtons();
    }
  }
}

function traceEventKey(event) {
  return ['voteId', 'voteTime', 'playerUuid', 'playerName', 'service', 'server', 'event', 'context', 'status', 'cachedTotal']
    .map(key => String(event?.[key] ?? '')).join('\u0000');
}

function renderVoteTrace(trace) {
  voteTraceResult.replaceChildren();
  const summary = document.createElement('p');
  text(summary, `${trace.events.length} unique retained ${trace.events.length === 1 ? 'event' : 'events'} from ${trace.sources.length} readable ${trace.sources.length === 1 ? 'node' : 'nodes'}.`);
  voteTraceResult.append(summary);
  if (trace.events.length) {
    const scroll = document.createElement('div');
    scroll.className = 'table-scroll';
    const table = document.createElement('table');
    const head = document.createElement('thead');
    const row = document.createElement('tr');
    ['Time', 'Event', 'Status', 'Player', 'Service', 'Server', 'Context', 'Source nodes'].forEach(label => row.append(text(document.createElement('th'), label)));
    head.append(row);
    const body = document.createElement('tbody');
    trace.events.forEach(item => {
      const event = item.event;
      const eventRow = document.createElement('tr');
      const time = Number(event.voteTime);
      eventRow.append(text(document.createElement('td'), Number.isFinite(time) && time > 0 ? new Date(time).toLocaleString() : 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.event || 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.status || 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.playerName || event.playerUuid || 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.service || 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.server || 'Unknown'));
      eventRow.append(text(document.createElement('td'), event.context || ''));
      eventRow.append(text(document.createElement('td'), item.sources.join(', ')));
      body.append(eventRow);
    });
    table.append(head, body);
    scroll.append(table);
    voteTraceResult.append(scroll);
  }
  const source = document.createElement('p');
  text(source, `Readable sources: ${trace.sources.join(', ') || 'none'}.`);
  voteTraceResult.append(source);
  if (trace.unavailable.length) {
    const diagnostics = document.createElement('p');
    diagnostics.className = 'warning-text';
    text(diagnostics, `Unavailable or failed sources: ${trace.unavailable.join('; ')}.`);
    voteTraceResult.append(diagnostics);
  }
  if (trace.truncatedSources.length) {
    const truncation = document.createElement('p');
    truncation.className = 'warning-text';
    text(truncation, `Node result limit reached; this trace is incomplete for: ${trace.truncatedSources.join(', ')}.`);
    voteTraceResult.append(truncation);
  }
  const boundary = document.createElement('p');
  boundary.className = 'warning-text';
  text(boundary, 'This bounded view contains only events returned by the readable nodes above. A missing logged event is unknown or not logged, not proof that an internal hop failed.');
  voteTraceResult.append(boundary);
}

async function traceVoteAcrossNodes() {
  const available = connectedInspectionNodes();
  const candidates = available.slice(0, MAX_TRACE_NODES);
  if (candidates.length === 0) throw new Error('No connected backend supports vote-log inspection.');
  const enteredVoteId = voteTraceId.value.trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(enteredVoteId)) {
    throw new Error('Enter a canonical vote UUID.');
  }
  const voteId = enteredVoteId.toLowerCase();
  const requestAuthenticationGeneration = authenticationGeneration;
  const requestInputGeneration = inputGeneration;
  const requestSelectedNodeId = selectedServerId;
  const requestSelectedSessionId = nodeIndex.get(requestSelectedNodeId)?.sessionId;
  const days = String(voteLogDays.value);
  const candidateSessions = new Map(candidates.map(node => [node.nodeId, node.sessionId]));
  const traceDeadline = Date.now() + TRACE_DEADLINE_MS;
  const contextCurrent = () => requestAuthenticationGeneration === authenticationGeneration
    && requestInputGeneration === inputGeneration && requestSelectedNodeId === selectedServerId
    && requestSelectedSessionId === nodeIndex.get(requestSelectedNodeId)?.sessionId
    && enteredVoteId === voteTraceId.value.trim() && days === String(voteLogDays.value)
    && candidates.every(node => {
      const current = nodeIndex.get(node.nodeId);
      return Boolean(current?.online) && candidateSessions.get(node.nodeId) === current.sessionId
        && Array.isArray(current.acceptedCapabilities) && current.acceptedCapabilities.includes('data.inspect.v1');
    });
  const events = new Map();
  const sources = [];
  const unavailable = [];
  const truncatedSources = [];
  if (available.length > candidates.length) unavailable.push(`${available.length - candidates.length} additional capable nodes were omitted by the ${MAX_TRACE_NODES}-node trace limit`);
  inspectionInFlight = true;
  updateExtendedButtons();
  text(voteTraceResult, `Collecting retained events from ${candidates.length} connected backend ${candidates.length === 1 ? 'node' : 'nodes'}…`);
  const traceAbortController = new AbortController();
  const abortTrace = () => {
    if (!traceAbortController.signal.aborted) traceAbortController.abort();
  };
  const deadlineTimer = window.setTimeout(abortTrace, Math.max(0, traceDeadline - Date.now()));
  const contextTimer = window.setInterval(() => {
    if (!contextCurrent()) abortTrace();
  }, 250);
  try {
    const results = await Promise.allSettled(candidates.map(async node => {
      const envelope = await runInspectionOnNode(node, 'vote-trace', {voteId, days, limit: String(MAX_TRACE_EVENTS_PER_NODE)}, {
        deadlineAt: traceDeadline, signal: traceAbortController.signal, contextCurrent, manageBusy: false
      });
      return {node, envelope};
    }));
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        const {node, envelope} = result.value;
        const source = `${node.displayName} (${node.nodeId})`;
        if (!Array.isArray(envelope.result?.events)) {
          unavailable.push(`${source}: malformed vote-trace events`);
          return;
        }
        const received = envelope.result.events;
        if (typeof envelope.result.voteId !== 'string' || envelope.result.voteId !== voteId) {
          unavailable.push(`${source}: vote-trace correlation did not match the requested vote`);
          return;
        }
        if (typeof envelope.result.found !== 'boolean' || typeof envelope.result.truncated !== 'boolean'
            || received.some(event => !validVoteTraceEvent(event, voteId))) {
          unavailable.push(`${source}: malformed vote-trace events`);
          return;
        }
        const listed = received.slice(0, MAX_TRACE_EVENTS_PER_NODE);
        sources.push(source);
        if (envelope.result?.truncated === true || received.length > MAX_TRACE_EVENTS_PER_NODE) {
          truncatedSources.push(source);
        }
        listed.forEach(event => {
          if (!event || typeof event !== 'object') return;
          const key = traceEventKey(event);
          const retained = events.get(key) || {event, sources: []};
          retained.sources.push(`${node.displayName} (${node.nodeId})`);
          events.set(key, retained);
        });
      } else {
        const node = candidates[index];
        const error = result.reason;
        unavailable.push(`${node.displayName} (${node.nodeId}): ${error.message || 'inspection failed'}`);
      }
    });
    if (!contextCurrent()) {
      if (requestAuthenticationGeneration === authenticationGeneration) {
        text(voteTraceResult, 'The selected server, node session, or trace window changed. Run the trace again.');
      }
      return;
    }
    if (Date.now() >= traceDeadline) unavailable.push('The 90-second trace budget expired before one or more node inspections completed');
    const ordered = [...events.values()].map(item => ({...item, sources: [...new Set(item.sources)].sort()})).sort((left, right) =>
      Number(left.event.voteTime || 0) - Number(right.event.voteTime || 0)
      || String(left.event.event || '').localeCompare(String(right.event.event || ''))
      || String(left.event.server || '').localeCompare(String(right.event.server || '')));
    renderVoteTrace({events: ordered, sources, unavailable, truncatedSources});
  } finally {
    window.clearTimeout(deadlineTimer);
    window.clearInterval(contextTimer);
    abortTrace();
    inspectionInFlight = false;
    updateExtendedButtons();
  }
}

function operationPhase(operation) {
  const proxyFileRestartRequired = operation.type === 'APPLY' && operation.state === 'SUCCEEDED'
    && operation.configuration?.domain === 'file' && operation.configuration?.fileName === 'bungeeconfig.yml';
  if (proxyFileRestartRequired) return operation.recovered
    ? 'Recovered history · Saved; proxy restart required' : 'Saved; proxy restart required';
  if (operation.recovered && operation.state !== 'RUNNING') return `Recovered history · ${operation.state}`;
  if (operation.state === 'RUNNING') return 'Queued or running';
  if (operation.type === 'PREVIEW' && operation.state === 'SUCCEEDED') return 'Preview ready for approval';
  if (operation.type === 'APPLY' && operation.state === 'SUCCEEDED') return 'Applied and verified';
  if (operation.state === 'COMPLETED_WITH_ERRORS') return 'Completed with failed targets';
  return operation.state;
}

function rememberOperation(operation) {
  const summary = {...operation, results: Object.fromEntries(Object.entries(operation.results || {}).map(([nodeId, result]) =>
    [nodeId, result ? {...result, configuration: null} : result]))};
  const existing = operationHistoryItems.findIndex(item => item.operationId === operation.operationId);
  if (existing >= 0) operationHistoryItems[existing] = summary;
  else operationHistoryItems.unshift(summary);
  operationHistoryItems = operationHistoryItems.slice(0, MAX_OPERATION_HISTORY);
  renderOperationHistory();
}

function renderOperationHistory() {
  operationHistory.replaceChildren();
  if (operationHistoryItems.length === 0 && deploymentHistoryItems.length === 0) {
    text(operationHistory, 'No retained configuration or plugin deployment operations.');
    renderMetrics();
    return;
  }
  operationHistoryItems.forEach(operation => {
    const item = document.createElement('article');
    item.className = 'result-item';
    const heading = document.createElement('div');
    heading.className = 'section-title';
    const identity = document.createElement('div');
    identity.append(text(document.createElement('strong'), `${operation.type} · ${operationPhase(operation)}`));
    identity.append(text(document.createElement('small'), `${operation.operationId}${operation.sourceOperationId
      ? ` · retry of ${operation.sourceOperationId}` : ''}${operation.recovered ? ' · recovered after restart' : ''}`));
    heading.append(identity);
    const actions = document.createElement('div');
    actions.className = 'operation-actions';
    const alreadyRetried = operationHistoryItems.some(item => item.sourceOperationId === operation.operationId);
    if (operation.retryable && !alreadyRetried) {
      const retry = text(document.createElement('button'), 'Retry failed targets');
      retry.type = 'button';
      retry.className = 'secondary compact';
      retry.addEventListener('click', async () => {
        retry.disabled = true;
        try {
          const retried = await authorized(`/api/v1/operations/${operation.operationId}/retry`, {method: 'POST'});
          const completed = await waitForOperation(retried, operationStatus);
          if (completed.type === 'APPLY') {
            fileReadCache.clear();
            lastFileReadOperation = null;
            approvedPreview = null;
            approvedFilePreview = null;
            approvedQuickPreview = null;
            dedicatedSetupApprovals.clear();
            inputGeneration++;
            updateConfigurationButtons();
            updateExtendedButtons();
          }
          setActiveTab('activity', true);
        } catch (error) {
          text(message, error.code === 'PREVIEW_REQUIRED'
            ? 'That apply needs a fresh preview because the failed targets may have changed.' : error.message);
        } finally { retry.disabled = false; }
      });
      actions.append(retry);
    }
    if (operation.type === 'PREVIEW' && operation.state === 'SUCCEEDED' && operation.approvalToken
        && operation.configuration?.domain === 'quick-setup'
        && !['proxy-method', 'reward-builder', 'sync-vote-sites'].includes(operation.configuration?.preset)) {
      const approve = text(document.createElement('button'), 'Approve this preview');
      approve.type = 'button';
      approve.className = 'compact';
      approve.addEventListener('click', async () => {
        if (!window.confirm('Apply this exact completed preview? Review every listed node change before continuing.')) return;
        approve.disabled = true;
        try {
          const applied = await startConfigurationOperation('/api/v1/configuration/apply', {
            previewOperationId: operation.operationId, approvalToken: operation.approvalToken
          }, operationStatus);
          if (applied.state === 'SUCCEEDED') {
            fileReadCache.clear();
            lastFileReadOperation = null;
            updateExtendedButtons();
          }
          await loadOperationHistory();
        } catch (error) { text(message, error.message); }
        finally { approve.disabled = false; }
      });
      actions.append(approve);
    }
    if (actions.childElementCount > 0) heading.append(actions);
    const detail = document.createElement('pre');
    text(detail, operationSummary(operation));
    item.append(heading, detail);
    operationHistory.append(item);
  });
  deploymentHistoryItems.forEach(deployment => {
    const item = document.createElement('article');
    item.className = 'result-item';
    const heading = document.createElement('div');
    heading.className = 'section-title';
    const identity = document.createElement('div');
    identity.append(text(document.createElement('strong'), `Plugin deployment · ${deployment.state}`));
    identity.append(text(document.createElement('small'), `${deployment.deploymentId} · ${new Date(deployment.createdAt).toLocaleString()}`));
    heading.append(identity);
    if ((deployment.nodes || []).some(node => node.state === 'FAILED')) {
      const retry = text(document.createElement('button'), 'Retry failed targets');
      retry.type = 'button';
      retry.className = 'secondary compact';
      retry.addEventListener('click', async () => {
        retry.disabled = true;
        try {
          const created = await authorized(`/api/v1/deployments/${deployment.deploymentId}/retry`, {method: 'POST'});
          const completed = await waitForDeployment(created, authenticationGeneration);
          text(deploymentStatus, deploymentSummary(completed));
          await loadOperationHistory();
        } catch (error) { text(message, error.message); }
        finally { retry.disabled = false; }
      });
      heading.append(retry);
    }
    const detail = document.createElement('pre');
    text(detail, deploymentSummary(deployment));
    item.append(heading, detail);
    operationHistory.append(item);
  });
  renderMetrics();
}

async function loadOperationHistoryOnce() {
  if (!authenticated) return;
  const historyGeneration = authenticationGeneration;
  operationHistoryStatus = 'loading';
  try {
    const [body, deploymentBody] = await Promise.all([
      authorized('/api/v1/operations'), authorized('/api/v1/deployments?offset=0&limit=50')
    ]);
    if (!authenticated || historyGeneration !== authenticationGeneration) return;
    const retainedOperations = Array.isArray(body.items) ? body.items : [];
    const serverConfigurationGeneration = finiteCount(body.configurationGeneration);
    const observedSuccessfulApply = serverConfigurationGeneration != null
      && (observedServerConfigurationGeneration == null
        ? serverConfigurationGeneration > 0 && dashboardLoadedContext === dashboardContext()
        : serverConfigurationGeneration > observedServerConfigurationGeneration);
    if (serverConfigurationGeneration != null) {
      observedServerConfigurationGeneration = observedServerConfigurationGeneration == null
        ? serverConfigurationGeneration
        : Math.max(observedServerConfigurationGeneration, serverConfigurationGeneration);
    }
    operationHistoryItems = retainedOperations.slice(0, MAX_OPERATION_HISTORY).map(operation =>
      ({...operation, results: Object.fromEntries(Object.entries(operation.results || {}).map(([nodeId, result]) =>
        [nodeId, result ? {...result, configuration: null} : result]))}));
    deploymentHistoryItems = Array.isArray(deploymentBody.items)
      ? deploymentBody.items.slice(0, MAX_OPERATION_HISTORY) : [];
    if (observedSuccessfulApply) {
      invalidateConfigurationReads();
      invalidateGuidedSetupReads();
      if (tabFromHash() === 'quick-setup') window.setTimeout(() => void autoLoadTab('quick-setup'), 0);
    }
    const pendingRestarts = new Map();
    const restartSessions = body.voteLoggingRestartSessions;
    if (restartSessions && typeof restartSessions === 'object' && !Array.isArray(restartSessions)) {
      Object.entries(restartSessions).slice(0, 10_000).forEach(([nodeId, sessionId]) => {
        if (typeof sessionId === 'string') pendingRestarts.set(nodeId, sessionId);
      });
    } else {
      retainedOperations.forEach(operation => {
        if (operation.type !== 'APPLY' || operation.configuration?.preset !== 'vote-logging') return;
        Object.entries(operation.results || {}).forEach(([nodeId, result]) => {
          if (result?.success && !pendingRestarts.has(nodeId)) {
            pendingRestarts.set(nodeId, result.sessionId || 'unknown');
          }
        });
      });
    }
    voteLoggingRestartPending = pendingRestarts;
    operationHistoryStatus = 'available';
    renderOperationHistory();
    updateSetupChecklist();
  } catch (error) {
    if (!authenticated || historyGeneration !== authenticationGeneration) return;
    operationHistoryItems = [];
    deploymentHistoryItems = [];
    voteLoggingRestartPending = new Map();
    operationHistoryStatus = 'failed';
    text(operationHistory, error.message || 'Operation history could not be loaded.');
    updateSetupChecklist();
    renderMetrics();
  }
}

function loadOperationHistory() {
  if (!authenticated) return Promise.resolve();
  if (operationHistoryLoadInFlight) {
    operationHistoryLoadQueued = true;
    return operationHistoryLoadInFlight;
  }
  const run = (async () => {
    do {
      operationHistoryLoadQueued = false;
      await loadOperationHistoryOnce();
    } while (operationHistoryLoadQueued && authenticated);
  })();
  operationHistoryLoadInFlight = run;
  return run.finally(() => {
    if (operationHistoryLoadInFlight === run) operationHistoryLoadInFlight = null;
  });
}

function renderSettingsCatalog() {
  const query = settingsFilter.value.trim().toLowerCase();
  const rows = SETTINGS_SCHEMA.filter(setting => Object.values(setting).join(' ').toLowerCase().includes(query));
  settingsCatalog.replaceChildren(...rows.map(setting => {
    const row = document.createElement('tr');
    [setting.key, setting.file, setting.type, setting.defaultValue || '—', setting.effect,
      setting.afterApply || (setting.file === 'BungeeSettings.yml'
        ? 'Connector/runtime may restart' : 'VotingPlugin reload')].forEach(value => {
      row.append(text(document.createElement('td'), value));
    });
    return row;
  }));
}

function readProfiles() {
  try {
    const value = JSON.parse(localStorage.getItem(SETUP_PROFILE_KEY) || '{}');
    const safe = Object.create(null);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      Object.entries(value).slice(0, 20).forEach(([name, profile]) => {
        if (name.length <= 60 && profile && typeof profile === 'object' && !Array.isArray(profile)) safe[name] = profile;
      });
    }
    return safe;
  } catch (_) { return Object.create(null); }
}

function writeProfiles(profiles) {
  const serialized = JSON.stringify(profiles);
  if (serialized.length > 1024 * 1024) throw new Error('Setup profiles exceed the 1 MiB browser-local limit.');
  localStorage.setItem(SETUP_PROFILE_KEY, serialized);
}

function currentProfileValues() {
  const values = {
    version: 1, preset: quickPreset.value, name: quickName.value, method: quickMethod.value,
    siteDisplayName: quickSiteDisplayName.value, service: quickService.value, url: quickUrl.value,
    delay: quickDelay.value, priority: quickSitePriority.value, material: quickSiteMaterial.value,
    siteEnabled: quickSiteEnabled.checked, siteHidden: quickSiteHidden.checked,
    rewardScope: quickRewardScope.value, command: quickCommand.value, playerMessage: quickMessage.value,
    processRewards: quickProcessRewards.checked, autoSites: quickAutoSites.checked,
    extraCheck: quickExtraCheck.checked, countFake: quickCountFake.checked,
    hideWarning: quickHideSiteWarning.checked, disableUpdates: quickDisableUpdates.checked,
    partyVotes: quickPartyVotes.value, partyCommand: quickPartyCommand.value,
    partyBroadcast: quickPartyBroadcast.value, partyAll: quickPartyAll.checked, partyOnline: quickPartyOnline.checked,
    autoSitesOnly: quickAutoSitesOnly.checked, voteLogging: quickVoteLoggingEnabled.checked,
    voteLoggingDays: quickVoteLoggingDays.value, voteLoggingMainMysql: quickVoteLoggingMainMysql.checked,
    rewardBuilder: {scope: rewardScope.value, site: rewardSite.value, chance: rewardChance.value,
      money: rewardMoney.value, commands: rewardCommands.value, messages: rewardMessages.value,
      broadcasts: rewardBroadcasts.value, permissions: rewardPermissions.value, items: rewardItems.value,
      onlineOnly: rewardOnlineOnly.checked}
  };
  // A v1 target cannot report Enabled. Omitting it preserves the confirmed live
  // value if this profile is later loaded against a v2-capable backend.
  if (quickSetupCapability() === 'config.quick-setup.v2'
      && !quickPartyEnabled.disabled && !quickPartyEnabled.indeterminate) {
    values.partyEnabled = quickPartyEnabled.checked;
  }
  return values;
}

function populateProfilePicker() {
  const profiles = readProfiles();
  const current = profilePicker.value;
  const placeholder = text(document.createElement('option'), 'Choose a profile');
  placeholder.value = '';
  profilePicker.replaceChildren(placeholder, ...Object.keys(profiles).sort().map(name => {
    const option = text(document.createElement('option'), name);
    option.value = name;
    return option;
  }));
  profilePicker.value = Object.hasOwn(profiles, current) ? current : '';
  loadProfile.disabled = !profilePicker.value;
  deleteProfile.disabled = !profilePicker.value;
}

async function loadHealth() {
  try {
    const response = await fetch('/api/v1/health', {cache: 'no-store'});
    if (!response.ok) throw new Error('health');
    const body = await response.json();
    text(health, `Online · ${body.identity.applicationVersion} · protocol ${body.identity.protocolVersion}`);
    health.classList.add('online');
  } catch (_) {
    text(health, 'Unavailable');
    health.classList.remove('online');
  }
}

async function loadSetupState() {
  try {
    const response = await fetch('/api/v1/auth/setup', {cache: 'no-store'});
    if (!response.ok) throw new Error('First-run setup status is unavailable.');
    const body = await response.json();
    setupRequired = Boolean(body.required);
    setupCard.hidden = !setupRequired;
    authCard.hidden = setupRequired;
    if (setupRequired) {
      enrollmentCard.hidden = true;
      text(setupMessage, `Enter the one-time code from ${body.codeFile} inside the configured hosted data directory.`);
    }
    return setupRequired;
  } catch (error) {
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    text(message, error.message || 'First-run setup status is unavailable.');
    return false;
  }
}

function applyAuthenticatedSession(body) {
  settingsEditor?.clear();
  voteSitesEditor?.clear();
  rewardsEditor?.clear();
  workspace.login();
  workspace.clearPersisted();
  registryAvailable = false;
  window.history.replaceState(null, '', '#home');
  authenticated = true;
  csrfToken = body.csrfToken;
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  quickSetupDirty = false;
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentReadCapability = '';
  proxyMethodCurrentValue = '';
  fileReadCache.clear();
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  dashboardOverview = null;
  dashboardVoteSiteHealth = null;
  dashboardVoteSummary24h = null;
  dashboardVoteSummary30d = null;
  dashboardLoadedContext = '';
  dashboardInspectionStatus = emptyDashboardInspectionStatus();
  dashboardTopologySignature = '';
  operationHistoryItems = [];
  deploymentHistoryItems = [];
  observedServerConfigurationGeneration = null;
  operationHistoryStatus = 'not-loaded';
  enrollmentStatus = 'not-loaded';
  dedicatedSetupApprovals.clear();
  voteLoggingRestartPending.clear();
  pendingDetectedVoteSite = null;
  configurationContent.value = '';
  configurationContentPresent = false;
  configurationDirty = false;
  configurationDraftNodeId = '';
  configurationDraftSessionId = '';
  configurationDraftFileName = '';
  routingDirty = false;
  routingDraftNodeId = '';
  configurationFileSelection = configurationFile.value;
  autoLoadInFlight.clear();
  autoLoadPending.clear();
  inputGeneration++;
  logout.hidden = false;
  sidebarToggle.hidden = false;
  globalSearch.hidden = false;
  syncTopbarOffset();
  headerAction.hidden = false;
  authCard.hidden = true;
  welcome.hidden = true;
  appShell.hidden = false;
  serverPickerLabel.hidden = true;
  enrollmentCard.hidden = false;
  pageOffset = 0;
  renderOperationHistory();
  populateProfilePicker();
  setActiveTab('home');
}

function isProxy(node) {
  return ['VELOCITY', 'BUNGEECORD'].includes(String(node.platform).toUpperCase());
}

function isBackend(node) {
  return String(node?.platform || '').toUpperCase() === 'BUKKIT';
}

function roleLabel(node) {
  return isProxy(node) ? 'Proxy' : isBackend(node) ? 'Backend' : 'Unknown role';
}

function platformLabel(platform) {
  const normalized = String(platform || '').toUpperCase();
  if (normalized === 'BUKKIT') return 'Bukkit';
  if (normalized === 'BUNGEECORD') return 'BungeeCord';
  if (normalized === 'VELOCITY') return 'Velocity';
  return platform || 'Unspecified platform';
}

function friendlyCapability(capability) {
  return ({
    'config.files.v1': 'Full configuration',
    'config.proxy-files.v1': 'Proxy configuration',
    'config.file-comments.v1': 'Comments preserved',
    'config.vote-sites-sync.v1': 'VoteSites sync',
    'config.transport-test.v1': 'Communication test',
    'config.proxy-method.v1': 'Proxy method',
    'config.proxy-method.v2': 'Proxy method · HTTP',
    'config.quick-setup.v1': 'Setup assistant',
    'config.quick-setup.v2': 'Vote Party setup',
    'config.proxy-routing.v1': 'Proxy routing',
    'data.inspect.v1': 'Read-only data inspection',
    'plugin.deploy.v1': 'Verified plugin staging'
  })[capability];
}

function managedCapabilities(node) {
  return node.acceptedCapabilities.map(friendlyCapability).filter(Boolean);
}

function proxyReportsFor(backendId) {
  return allNodeItems.filter(node => isProxy(node) && node.online).filter(proxy =>
    (Array.isArray(proxy.backends) ? proxy.backends : []).some(backend => backend.backendId === backendId));
}

function backendCard(backend, reporterOnline) {
  const item = document.createElement('li');
  const title = text(document.createElement('strong'), backend.displayName);
  const details = document.createElement('div');
  details.className = 'backend-state';
  const registered = nodeIndex.get(backend.backendId);
  details.append(text(document.createElement('span'), !enrollmentsLoaded
    ? 'Control enrollment unavailable'
    : enrollmentIds.has(backend.backendId)
    ? `Enrolled in Control · ${registered ? (registered.online ? 'Control connected' : 'Control disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (!reporterOnline) {
    details.append(text(document.createElement('span'), 'Presence stale · reporting proxy disconnected'));
  } else if (backend.presenceKnown) {
    details.append(text(document.createElement('span'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    details.append(text(document.createElement('span'), 'Presence not available'));
  }
  details.append(text(document.createElement('span'), backend.backendId));
  item.append(title, details);
  return item;
}

function nodeCard(node) {
  const article = document.createElement('article');
  article.className = `node${workspace.selectedTargetIds.has(node.nodeId) ? ' selected' : ''}`;
  article.dataset.nodeId = node.nodeId;
  const header = document.createElement('div');
  header.className = 'node-header';
  const identity = document.createElement('div');
  const title = text(document.createElement('h3'), node.displayName);
  identity.append(title, text(document.createElement('span'), node.nodeId));
  identity.lastElementChild.className = 'node-id';
  const state = text(document.createElement('span'), node.online ? 'Control connected' : 'Control disconnected');
  state.className = `pill ${node.online ? 'online' : 'offline'}`;
  header.append(identity, state);

  const meta = document.createElement('div');
  meta.className = 'node-meta';
  [roleLabel(node), platformLabel(node.platform), `VotingPlugin ${node.pluginVersion || 'version unknown'}`,
    ...managedCapabilities(node)].forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill neutral';
    meta.append(pill);
  });

  const plugins = Array.isArray(node.detectedPlugins) ? node.detectedPlugins : [];
  let detail;
  if (isBackend(node)) {
    detail = text(document.createElement('p'), plugins.length
      ? `Detected plugins: ${plugins.join(', ')}` : 'Plugin inventory is not available for this backend.');
  } else {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    detail = text(document.createElement('p'), `${backends.length} configured ${backends.length === 1 ? 'backend' : 'backends'} reported.`);
  }
  detail.className = 'node-detail';

  const selector = document.createElement('label');
  selector.className = 'node-select';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.disabled = !isBackend(node);
  checkbox.checked = workspace.selectedTargetIds.has(node.nodeId);
  selector.title = isBackend(node) ? 'Select a management target. Selection never writes configuration.'
    : 'Proxy nodes are managed through Global Settings, not Bukkit configuration.';
  checkbox.addEventListener('change', () => {
    if (checkbox.checked && workspace.selectedTargetIds.size >= MAX_CONFIGURATION_TARGETS) {
      checkbox.checked = false;
      text(operationStatus, `At most ${MAX_CONFIGURATION_TARGETS} servers can be configured at once.`);
      return;
    }
    if (!changeWorkspaceTargets(() => workspace.toggleTarget(node.nodeId))) {
      checkbox.checked = workspace.selectedTargetIds.has(node.nodeId);
    }
  });
  selector.append(checkbox, document.createTextNode(isBackend(node) ? 'Select backend workspace target' : 'Global / Network only'));

  const list = document.createElement('ul');
  list.className = 'node-backends';
  if (isProxy(node)) {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    if (backends.length === 0) {
      list.append(text(document.createElement('li'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => list.append(backendCard(backend, node.online)));
    }
  } else {
    const proxies = proxyReportsFor(node.nodeId);
    list.append(text(document.createElement('li'), proxies.length
      ? `Reported by ${proxies.map(proxy => proxy.displayName).join(', ')}.`
      : 'No connected proxy reports this backend ID.'));
  }
  const seen = text(document.createElement('p'), `Last seen: ${node.lastSeen ? new Date(node.lastSeen).toLocaleString() : 'Unknown'} · ${nodePresence(node)}`);
  seen.className = 'node-detail';
  article.append(header, meta, detail, seen, list, selector);
  if (!managedCapabilities(node).length) {
    article.append(text(document.createElement('p'), 'No supported management capability reported. Configuration tools are unavailable.'));
  }
  if (isBackend(node)) {
    const inspect = text(document.createElement('button'), 'Inspect server overview');
    inspect.type = 'button';
    inspect.className = 'secondary compact';
    inspect.addEventListener('click', () => inspectWorkspaceServer(node.nodeId));
    article.append(inspect);
  }
  return article;
}

// Workspace selection is independent from the source of legacy single-server tools.
function ordinaryTargetIds() {
  if (!registryAvailable || !selectedServerId) return [];
  const source = nodeIndex.get(selectedServerId);
  if (!source?.online) return [];
  if (workspace.managementScope === 'GLOBAL') return isProxy(source) ? [selectedServerId] : [];
  return workspace.selectedTargetIds.has(selectedServerId) ? [selectedServerId] : [];
}

function comparisonTargetIds() {
  return [...workspace.selectedTargetIds].filter(id => nodeIndex.get(id)?.online
    && isBackend(nodeIndex.get(id)) && nodeCapabilities.get(id)?.includes('config.files.v1'));
}

function isWorkspaceOverview() {
  return workspace.managementScope === 'GLOBAL'
    || workspace.managementScope === 'MULTI_SERVER' && !workspace.inspectedServerId;
}

function nodePresence(node) {
  const reports = proxyReportsFor(node.nodeId).filter(proxy => proxy.online)
    .flatMap(proxy => proxy.backends.filter(backend => backend.backendId === node.nodeId && backend.presenceKnown));
  if (!reports.length) return 'Minecraft presence unknown';
  if (reports.some(report => report.available !== reports[0].available || report.playerCount !== reports[0].playerCount)) {
    return 'Minecraft presence conflicting between proxies';
  }
  return reports[0].available ? `${reports[0].playerCount} players · proxy-reported`
    : 'Minecraft unavailable · proxy-reported';
}

function workspaceHash(tab) {
  if (tab === 'home') return '#home';
  if (tab === 'access' || tab === 'servers') return `#${tab}`;
  if (workspace.managementScope === 'GLOBAL') {
    return `#global/${tab === 'quick-setup' ? 'synchronization' : tab === 'configurations' ? 'configuration' : tab === 'overview' ? 'network' : tab}`;
  }
  if (tab === 'overview' && workspace.inspectedServerId) return `#servers/${encodeURIComponent(workspace.inspectedServerId)}/overview`;
  const section = tab === 'general-settings' ? 'settings' : tab === 'quick-setup'
    ? quickPreset.value === 'sync-vote-sites' ? 'sync' : 'quick-setup' : tab === 'configurations' ? 'configuration' : tab;
  return `#workspace/${section}`;
}

function renderChips(element) {
  element.replaceChildren();
  workspace.selectedTargetIds.forEach(id => {
    const chip = text(document.createElement('button'), `${nodeIndex.get(id)?.displayName || id} ×`);
    chip.type = 'button';
    chip.className = 'scope-chip secondary compact';
    chip.setAttribute('aria-label', `Remove ${nodeIndex.get(id)?.displayName || id} from workspace`);
    chip.addEventListener('click', () => changeWorkspaceTargets(() => workspace.toggleTarget(id)));
    element.append(chip);
  });
}

function renderHomeChooser() {
  const filter = homeSearch.value.trim().toLocaleLowerCase();
  const items = allNodeItems.filter(node => `${node.displayName} ${node.nodeId} ${node.platform}`.toLocaleLowerCase().includes(filter));
  homeNodes.replaceChildren();
  // Registry scans are already bounded; render one page at a time in the chooser.
  items.slice(0, 100).forEach(node => homeNodes.append(nodeCard(node)));
  if (!items.length) text(homeNodes, registryAvailable ? 'No registered servers match this filter.' : 'Network data unavailable. Refresh to retry.');
  if (items.length > 100) homeNodes.append(text(document.createElement('p'), 'Showing the first 100 matches. Refine your search to find more nodes.'));
  homeNodes.classList.toggle('empty', !items.length);
  text(document.querySelector('#home-selection-count'), `${workspace.selectedTargetIds.size} backend${workspace.selectedTargetIds.size === 1 ? '' : 's'} selected`);
  renderChips(document.querySelector('#home-selection-chips'));
  document.querySelector('#home-continue').disabled = !registryAvailable || !workspace.selectedTargetIds.size;
  document.querySelector('#home-restore').hidden = workspace.managementScope !== 'GLOBAL' || !workspace.previousTargetIds.size;
}

function renderWorkspaceChrome(tab) {
  settingsEditor?.scopeChanged();
  voteSitesEditor?.scopeChanged();
  rewardsEditor?.scopeChanged();
  const global = workspace.managementScope === 'GLOBAL';
  const chooser = tab === 'home' || !workspace.managementScope;
  appShell.classList.toggle('chooser-mode', chooser);
  primaryNavigation.hidden = chooser;
  sidebarToggle.hidden = !authenticated || chooser;
  scopeBar.hidden = !authenticated || chooser;
  document.querySelectorAll('[data-server-nav]').forEach(element => { element.hidden = global; });
  document.querySelectorAll('[data-global-nav]').forEach(element => { element.hidden = !global; });
  text(document.querySelector('#scope-kind'), global ? 'Global Settings / Network' : 'Managing backend workspace');
  renderChips(document.querySelector('#scope-chips'));
  if (global) text(document.querySelector('#scope-chips'), 'Network tools · targets are chosen explicitly for each operation');
  serverPickerLabel.hidden = chooser || tab === 'overview' || tab === 'general-settings' || tab === 'vote-sites' || tab === 'rewards';
  text(serverPickerLabel.querySelector('span'), global ? 'Tool source (proxy or diagnostics backend)' : 'Single-server tool source');
  const source = nodeIndex.get(selectedServerId);
  text(document.querySelector('#scope-editing-notice'), global
    ? 'Global mode is not “all servers”. Proxy switches and synchronization retain their explicit preview and target rules.'
    : tab === 'general-settings' ? 'General Settings reads each workspace target independently. Only explicit edits are proposed, with per-target revision protection.'
    : tab === 'vote-sites' ? 'Vote Sites reads each workspace target independently. Site keys are identity; property editing never invokes synchronization or rewrites rewards.'
    : tab === 'rewards' ? 'Rewards reads each workspace target independently. Only explicit bounded operations can be previewed and applied.'
    : `Legacy tools: rewards, guided presets and YAML use only ${source?.displayName || 'a selected source server'} (${selectedServerId || 'not selected'}). They do not copy its configuration to the workspace.${source && !workspace.selectedTargetIds.has(source.nodeId) ? ' This inspected server is outside the workspace; its configuration tools are disabled.' : ''}`);
  document.querySelector('#scope-overview').hidden = global;
}

function changeWorkspaceTargets(change) {
  if (!confirmDiscardWorkspaceDrafts('changing workspace targets')) return false;
  resetServerContextValues('Workspace changed. Loading the selected source when needed.');
  change();
  clearApprovals();
  if (!workspace.selectedTargetIds.has(selectedServerId) && workspace.managementScope !== 'GLOBAL') {
    selectPrimaryServer([...workspace.selectedTargetIds][0] || '');
    workspace.inspect('');
  }
  renderNodeViews();
  updateConfigurationButtons();
  return true;
}

function inspectWorkspaceServer(id) {
  if (!isBackend(nodeIndex.get(id))) return;
  if (workspace.managementScope === 'GLOBAL') {
    if (!confirmDiscardUnsavedConfiguration('returning to the server workspace')) return;
    resetServerContextValues('Returning to server tools.');
    workspace.returnToServers();
    clearApprovals();
  }
  if (!workspace.managementScope) workspace.setTargets([id]);
  if (selectPrimaryServer(id) === false) return;
  workspace.inspect(id);
  setActiveTab('overview', true);
}

function openScopeOverview() {
  if (!workspace.selectedTargetIds.size) return setActiveTab('home', true);
  if (!workspace.selectedTargetIds.has(selectedServerId) && selectPrimaryServer([...workspace.selectedTargetIds][0]) === false) return;
  workspace.inspect(workspace.managementScope === 'SERVER' ? [...workspace.selectedTargetIds][0] : '');
  setActiveTab('overview', true);
}

function enterGlobalWorkspace() {
  if (!confirmDiscardWorkspaceDrafts('opening Global Settings')) return;
  resetServerContextValues('Opening global tools.');
  workspace.enterGlobal();
  clearApprovals();
  selectPrimaryServer(allNodeItems.find(node => isProxy(node) && node.online)?.nodeId || '');
  workspace.inspect('');
  setActiveTab('network', true);
}

function applyNavigationRoute() {
  if (!authenticated) return;
  const route = ControlWorkspace.parseRoute(window.location.hash);
  const cancelNavigation = () => window.history.replaceState(null, '', activeNavigationHash);
  if (route.scope === 'GLOBAL' && workspace.managementScope !== 'GLOBAL') {
    if (!confirmDiscardWorkspaceDrafts('opening Global Settings')) return cancelNavigation();
    resetServerContextValues('Opening global tools.');
    workspace.enterGlobal();
    clearApprovals();
    selectPrimaryServer(allNodeItems.find(node => isProxy(node) && node.online)?.nodeId || '');
  } else if ((route.scope === 'WORKSPACE' || route.inspectedServerId) && workspace.managementScope === 'GLOBAL') {
    if (!confirmDiscardUnsavedConfiguration('returning to the server workspace')) return cancelNavigation();
    resetServerContextValues('Returning to server tools.');
    workspace.returnToServers();
    clearApprovals();
    selectPrimaryServer([...workspace.selectedTargetIds][0] || '');
  }
  if (route.scope === 'WORKSPACE' && !workspace.managementScope) {
    window.history.replaceState(null, '', '#home');
    return setActiveTab('home');
  }
  if (route.inspectedServerId) {
    if (!isBackend(nodeIndex.get(route.inspectedServerId))) {
      window.history.replaceState(null, '', '#home');
      return setActiveTab('home');
    }
    if (!workspace.managementScope) workspace.setTargets([route.inspectedServerId]);
    if (selectPrimaryServer(route.inspectedServerId) === false) return cancelNavigation();
    workspace.inspect(route.inspectedServerId);
  } else if (route.page === 'overview') workspace.inspect('');
  const preset = route.section === 'sync' ? 'sync-vote-sites' : '';
  if (preset && preset !== quickPreset.value) {
    quickPreset.value = preset;
    loadedQuickSetup = null;
    quickSetupDirty = false;
    clearApprovals();
  }
  if (route.page === 'quick-setup') updateQuickFields();
  setActiveTab(route.page);
  if (route.section === 'rewards') scrollToAnchor(document.querySelector('#reward-builder-card'));
}

function normalizeWorkspaceRouteAfterNodeRefresh(previousWorkspaceTargets, previousInspectedServerId) {
  if (previousWorkspaceTargets && !workspace.managementScope) {
    setActiveTab(tabFromHash());
    return;
  }
  const route = ControlWorkspace.parseRoute(window.location.hash);
  if (previousInspectedServerId && previousInspectedServerId !== workspace.inspectedServerId
      && route.inspectedServerId === previousInspectedServerId) {
    const tab = workspace.managementScope ? 'overview' : 'home';
    window.history.replaceState(null, '', workspaceHash(tab));
    setActiveTab(tab);
  }
}

function renderScopeOverview() {
  const aggregate = isWorkspaceOverview();
  const source = nodeIndex.get(selectedServerId);
  text(document.querySelector('#overview-title'), aggregate ? workspace.managementScope === 'GLOBAL'
    ? 'Network Overview' : `${workspace.selectedTargetIds.size} Servers — Workspace Overview`
    : `${source?.displayName || 'Selected server'} — Server Overview`);
  text(document.querySelector('#overview-eyebrow'), aggregate ? 'Management workspace' : 'Individual server inspection');
  document.querySelector('#individual-overview').hidden = aggregate;
  voteActivity.closest('section').hidden = aggregate;
  const grid = document.querySelector('#workspace-overview-targets');
  grid.hidden = !aggregate;
  grid.replaceChildren();
  if (aggregate) {
    const ids = workspace.managementScope === 'GLOBAL' ? allNodeItems.map(node => node.nodeId) : [...workspace.selectedTargetIds];
    ids.slice(0, 100).forEach(id => {
      const node = nodeIndex.get(id);
      if (node) grid.append(nodeCard(node));
    });
    grid.prepend(text(document.createElement('p'), 'Registry connectivity and capabilities only. Individual health requires a server inspection; unknown is not healthy.'));
  }
  text(document.querySelector('#metric-presence'), source ? nodePresence(source) : 'Unknown');
  text(document.querySelector('#metric-presence-detail'), 'Known only when a connected proxy reports it');
  const recent = overviewOperations()[0];
  text(document.querySelector('#metric-last-operation'), recent ? operationPhase(recent) : '—');
  text(document.querySelector('#metric-last-operation-detail'), recent ? `${operationLabel(recent)} · ${recent.createdAt ? new Date(recent.createdAt).toLocaleString() : 'Time unknown'}` : 'No retained operation for this scope');
  const health = document.querySelector('#overview-site-health');
  health.replaceChildren();
  const sites = dashboardLoadedContext === dashboardContext() ? dashboardVoteSiteHealth?.sites : null;
  if (!Array.isArray(sites)) text(health, 'Vote-site inspection unavailable or not loaded. Refresh or use Vote Sites.');
  else sites.slice(0, 100).forEach(site => health.append(text(document.createElement('p'), `${site.displayName || site.siteKey || site.serviceSite || 'Site'} — ${site.status || 'Unknown'}`)));
  if (Array.isArray(sites) && !sites.length) text(health, 'No configured sites reported.');
}

function overviewOperations() {
  const ids = isWorkspaceOverview() ? workspace.selectedTargetIds : new Set(selectedServerId ? [selectedServerId] : []);
  return operationHistoryItems.filter(operation => workspace.managementScope === 'GLOBAL'
    || Object.keys(operation.nodeStates || {}).some(id => ids.has(id)));
}

function tabFromHash() {
  return ControlWorkspace.parseRoute(window.location.hash).page;
}

function closeSidebar() {
  const restoreFocus = window.matchMedia('(max-width: 920px)').matches
    && primaryNavigation.contains(document.activeElement);
  document.body.classList.remove('sidebar-open');
  sidebarToggle.setAttribute('aria-expanded', 'false');
  sidebarToggle.setAttribute('aria-label', 'Open navigation');
  if (restoreFocus) sidebarToggle.focus();
}

function updateHeaderAction(tab) {
  const actions = {
    home: ['Refresh servers', () => loadNodes()],
    'general-settings': ['Read Current', () => void settingsEditor?.read(true)],
    'vote-sites': ['Read Vote Sites', () => void voteSitesEditor?.read(true)],
    rewards: ['Read Rewards', () => void rewardsEditor?.read(true)],
    overview: ['Refresh dashboard', () => refreshDashboard()],
    servers: ['Refresh servers', () => loadNodes()],
    network: ['Run Network Doctor', () => runNetworkDoctor.click()],
    configurations: ['Compare configuration', () => {
      setConfigView('compare');
      runDriftCheck.click();
    }],
    'quick-setup': ['Add Vote Site', () => openWorkspace('quick-setup', 'quick-setup-card', 'vote-site')],
    data: ['Refresh server overview', () => refreshDataOverview.click()],
    activity: ['Refresh activity', () => loadOperationHistory()],
    access: ['Refresh access', () => loadEnrollments()]
  };
  const [label, action] = actions[tab] || actions.overview;
  text(headerAction, label);
  headerAction.onclick = action;
  const unavailable = tab === 'overview' ? dashboardLoading || inspectionInFlight
    : tab === 'servers' ? nodeLoadInFlight != null
    : tab === 'network' ? runNetworkDoctor.disabled
    : tab === 'configurations' ? runDriftCheck.disabled
    : tab === 'data' ? refreshDataOverview.disabled
    : tab === 'quick-setup' ? !nodeCapabilities.get(selectedServerId)?.some(capability =>
      capability === 'config.quick-setup.v1' || capability === 'config.quick-setup.v2')
    : false;
  headerAction.disabled = !authenticated || unavailable;
}

function setActiveTab(tab, updateHash = false) {
  const requestedTab = tab;
  const previousTab = tabPanels.find(panel => !panel.hidden)?.dataset.panel;
  if (!tabPanels.some(panel => panel.dataset.panel === tab)) tab = 'home';
  if (workspace.managementScope === 'GLOBAL' && tab === 'overview') tab = 'network';
  if (workspace.managementScope === 'GLOBAL' && tab === 'general-settings') tab = 'network';
  if (workspace.managementScope === 'GLOBAL' && tab === 'vote-sites') tab = 'network';
  if (workspace.managementScope === 'GLOBAL' && tab === 'rewards') tab = 'network';
  if (authenticated && !workspace.managementScope && tab !== 'home' && tab !== 'access') tab = 'home';
  navigationButtons.forEach(button => button.removeAttribute('aria-current'));
  tabButtons.forEach(button => {
    if (button.dataset.tab === tab) button.setAttribute('aria-current', 'page');
  });
  tabPanels.forEach(panel => { panel.hidden = panel.dataset.panel !== tab; });
  if ((tab === 'general-settings' || tab === 'vote-sites' || tab === 'rewards') && previousTab !== tab) window.scrollTo({top: 0, behavior: 'instant'});
  if (updateHash || tab !== requestedTab) {
    const hash = workspaceHash(tab);
    if (window.location.hash !== hash) {
      if (updateHash && tab === requestedTab) window.history.pushState(null, '', hash);
      else window.history.replaceState(null, '', hash);
    }
  }
  activeNavigationHash = window.location.hash || '#home';
  if (authenticated) workspace.setRoute(activeNavigationHash);
  renderWorkspaceChrome(tab);
  renderScopeOverview();
  renderOverviewActivity();
  closeSidebar();
  updateHeaderAction(tab);
  void autoLoadTab(tab);
}

function openWorkspace(tab, scrollTarget = '', preset = '', navigationButton = null) {
  if (preset && quickPreset.value !== preset) {
    quickPreset.value = preset;
    loadedQuickSetup = null;
    quickSetupDirty = false;
    quickSetupPreserveReadGeneration = -1;
    pendingDetectedVoteSite = null;
    updateQuickFields();
    clearApprovals();
  }
  setActiveTab(tab, true);
  if (navigationButton && primaryNavigation.contains(navigationButton)) {
    navigationButtons.forEach(button => button.removeAttribute('aria-current'));
    navigationButton.setAttribute('aria-current', 'page');
  }
  if (scrollTarget) window.requestAnimationFrame(() => scrollToAnchor(document.getElementById(scrollTarget)));
}

function setConfigView(view) {
  if (!configViewPanels.some(panel => panel.dataset.configPanel === view)) view = 'easy';
  configViewButtons.forEach(button => button.classList.toggle('active', button.dataset.configView === view));
  configViewPanels.forEach(panel => { panel.hidden = panel.dataset.configPanel !== view; });
  if (view === 'yaml') void autoLoadTab('configurations');
}

function resetFileEditorForSelection(message) {
  configurationContent.value = '';
  configurationContentPresent = false;
  configurationDirty = false;
  configurationDraftNodeId = '';
  configurationDraftSessionId = '';
  configurationDraftFileName = '';
  lastFileReadOperation = null;
  approvedFilePreview = null;
  updateEditorPosition();
  configurationContent.disabled = false;
  configurationContent.removeAttribute('aria-busy');
  readFileConfiguration.hidden = true;
  text(fileOperationStatus, message);
}

function fileDraftMatchesCurrentContext() {
  return !configurationDirty || configurationDraftNodeId === selectedServerId
    && configurationDraftSessionId === nodeIndex.get(selectedServerId)?.sessionId
    && configurationDraftFileName === configurationFile.value;
}

function fileDraftStatus(status) {
  if (!configurationDirty) return status;
  const owner = configurationDraftNodeId
    ? `${configurationDraftNodeId}${configurationDraftSessionId ? ` (session ${configurationDraftSessionId})` : ''}`
    : 'the previous server';
  return `${status} Your unsaved ${configurationFile.value} draft is retained for ${owner}; read/reload the current file to explicitly discard it and bind the editor to this server.`;
}

function confirmDiscardUnsavedConfiguration(context) {
  if (!configurationDirty && !routingDirty) return true;
  return window.confirm(`Discard unsaved ${configurationDirty && routingDirty ? 'YAML and routing' : configurationDirty ? 'YAML' : 'routing'} changes before ${context}?`);
}

function confirmDiscardWorkspaceDrafts(context) {
  if (!confirmDiscardUnsavedConfiguration(context)) return false;
  if (settingsEditor?.model.dirty.size && !window.confirm(`Discard explicit General Settings edits before ${context}?`)) return false;
  if (voteSitesHasDraft() && !window.confirm(`Discard explicit Vote Site edits before ${context}?`)) return false;
  if (rewardsEditor?.edit && !window.confirm(`Discard explicit Rewards edits before ${context}?`)) return false;
  return true;
}

function clearSessionRewardFileOptions() {
  const selected = configurationFile.value;
  let removedSelected = false;
  for (const option of [...(configurationFile.options || [])]) {
    if (option.dataset?.sessionRewardFile !== 'true') continue;
    removedSelected ||= option.value === selected;
    option.remove();
  }
  if (removedSelected) {
    configurationFile.value = 'Config.yml';
    configurationFileSelection = 'Config.yml';
  }
}

function syncFileSelection() {
  const selected = nodeIndex.get(selectedServerId);
  const proxyFile = [...configurationFile.options].find(option => option.value === 'bungeeconfig.yml');
  if (proxyFile) proxyFile.disabled = !(selected?.online && isProxy(selected)
    && selected.acceptedCapabilities.includes('config.proxy-files.v1'));
  const proxySelected = Boolean(selected?.online && isProxy(selected)
    && selected.acceptedCapabilities.includes('config.proxy-files.v1'));
  const expected = proxySelected ? 'bungeeconfig.yml' : 'Config.yml';
  if ((proxySelected && configurationFile.value !== 'bungeeconfig.yml')
      || (!proxySelected && configurationFile.value === 'bungeeconfig.yml')) {
    if (configurationDirty) {
      text(fileOperationStatus, fileDraftStatus(
        'The selected server cannot manage this file. Reconnect the original server or explicitly switch files to discard it.'));
      return;
    }
    configurationFile.value = expected;
    configurationFileSelection = expected;
    resetFileEditorForSelection('Read the selected file before previewing changes.');
  }
}

function finishAutoLoad(tab) {
  autoLoadInFlight.delete(tab);
  if (autoLoadPending.delete(tab)) void autoLoadTab(tab);
}

async function autoLoadTab(tab) {
  if (!authenticated) return;
  if (tab === 'general-settings') { await settingsEditor?.read(false); return; }
  if (tab === 'vote-sites') {
    await voteSitesEditor?.read(false);
    if (voteSiteHealthLoadedContext !== voteSitesContext()) void refreshVoteSiteObservations();
    return;
  }
  if (tab === 'rewards') { await rewardsEditor?.read(false); return; }
  if (tab === 'overview' && isWorkspaceOverview()) return;
  if (autoLoadInFlight.has(tab)) {
    autoLoadPending.add(tab);
    return;
  }
  if (tab === 'overview' && inspectionCapableNode() && dashboardLoadedContext !== dashboardContext()
      && (dashboardLoading || inspectionInFlight)) {
    if (!autoLoadPending.has(tab)) {
      autoLoadPending.add(tab);
      window.setTimeout(() => {
        if (autoLoadPending.delete(tab)) void autoLoadTab(tab);
      }, 250);
    }
    return;
  }
  if (tab === 'overview' && inspectionCapableNode() && dashboardLoadedContext !== dashboardContext()
      && !dashboardLoading && !inspectionInFlight) {
    autoLoadInFlight.add(tab);
    try { await refreshDashboard(); } finally { finishAutoLoad(tab); }
    return;
  }
  if (tab === 'configurations') {
    if (configurationDirty || configurationContentPresent || !fileTargetsForSelection().length) return;
    autoLoadInFlight.add(tab);
    try { await loadFileConfiguration(true); } finally { finishAutoLoad(tab); }
    return;
  }
  if (tab === 'network' && !proxyMethodWorkflowInFlight) {
    autoLoadInFlight.add(tab);
    try {
      await Promise.all([proxyMethodCurrentValue ? Promise.resolve() : loadProxyMethod(true),
        routingDirty || approvedPreview ? Promise.resolve() : loadProxyRouting(true)]);
    } finally { finishAutoLoad(tab); }
    return;
  }
  if (tab === 'quick-setup' && configurationOperationsInFlight) {
    autoLoadPending.add(tab);
    return;
  }
  if (tab === 'quick-setup' && !approvedQuickPreview
      && (!dedicatedSetupDirty.has('auto-create-vote-sites') && autoSitesState.textContent === 'Not loaded'
        || !dedicatedSetupDirty.has('vote-logging') && voteLoggingState.textContent === 'Not loaded'
        || quickPresetReadable() && (!quickSetupDirty || quickSetupPreserveReadGeneration === inputGeneration)
          && !quickSetupValuesLoaded())) {
    autoLoadInFlight.add(tab);
    try {
      const autoLoadGeneration = inputGeneration;
      if (!dedicatedSetupDirty.has('auto-create-vote-sites') && autoSitesState.textContent === 'Not loaded') {
        await loadDedicatedSetup('auto-create-vote-sites', true);
      }
      if (inputGeneration !== autoLoadGeneration) {
        autoLoadPending.add(tab);
        return;
      }
      if (!dedicatedSetupDirty.has('vote-logging') && voteLoggingState.textContent === 'Not loaded') {
        await loadDedicatedSetup('vote-logging', true);
      }
      if (inputGeneration !== autoLoadGeneration) {
        autoLoadPending.add(tab);
        return;
      }
      if (quickPresetReadable() && (!quickSetupDirty || quickSetupPreserveReadGeneration === inputGeneration)
          && !quickSetupValuesLoaded()) {
        const preserveGeneration = quickSetupPreserveReadGeneration;
        await loadQuickSetupValues(true, quickSetupDirty);
        if (quickSetupPreserveReadGeneration === preserveGeneration) quickSetupPreserveReadGeneration = -1;
      }
    } finally { finishAutoLoad(tab); }
    return;
  }
  if (tab === 'data' && inspectionCapableNode() && !inspectionInFlight && !lastOverview) {
    autoLoadInFlight.add(tab);
    try { await refreshOverview(dataOverview); } finally { finishAutoLoad(tab); }
  }
}

function chooseDefaultServer(items) {
  return items.find(node => isBackend(node) && node.online) || items.find(node => isBackend(node)) ||
    items.find(node => node.online) || items[0] || null;
}

function renderServerPicker() {
  const previousValue = selectedServerId;
  const ordered = allNodeItems.filter(node => workspace.managementScope === 'GLOBAL'
    || isBackend(node) && (workspace.selectedTargetIds.has(node.nodeId) || node.nodeId === workspace.inspectedServerId)).sort((left, right) => {
    const roleOrder = Number(isProxy(left)) - Number(isProxy(right));
    return roleOrder || left.displayName.localeCompare(right.displayName);
  });
  const placeholder = text(document.createElement('option'), ordered.length ? 'Choose a server' : 'No servers registered');
  placeholder.value = '';
  serverPicker.replaceChildren(placeholder, ...ordered.map(node => {
    const option = text(document.createElement('option'),
      `${node.displayName} · ${roleLabel(node)} · ${node.online ? 'connected' : 'disconnected'}`);
    option.value = node.nodeId;
    return option;
  }));
  if (!nodeIndex.has(previousValue)) {
    selectedServerId = '';
    if (previousValue) {
      resetServerContextValues('The selected server is no longer available. Load current values from its replacement.', true);
    }
  }
  serverPicker.value = selectedServerId;
  populateGlobalSearch();
}

function renderSelectedServer() {
  const selected = nodeIndex.get(selectedServerId);
  selectedServerCapabilities.replaceChildren();
  if (!selected) {
    text(selectedServerName, 'Choose a VotingPlugin server');
    text(selectedServerState, 'No selection');
    selectedServerState.className = 'pill neutral';
    text(selectedServerSummary, 'Use the server picker above to keep configuration and setup actions focused on one node.');
    text(configurationContext, 'Choose a backend from the server picker to work with its VotingPlugin configuration.');
    text(commentPreservationState, 'Comment support unknown');
    commentPreservationState.className = 'pill warning';
    syncFileSelection();
    return;
  }
  syncFileSelection();
  text(selectedServerName, selected.displayName);
  text(selectedServerState, selected.online ? 'Control connected' : 'Control disconnected');
  selectedServerState.className = `pill ${selected.online ? 'online' : 'offline'}`;
  const relationships = isBackend(selected) ? proxyReportsFor(selected.nodeId) : [];
  const relationshipText = relationships.length
    ? ` Reported by ${relationships.map(proxy => proxy.displayName).join(', ')}.` : '';
  text(selectedServerSummary,
    `${roleLabel(selected)} · ${platformLabel(selected.platform)} · VotingPlugin ${selected.pluginVersion || 'version unknown'}.${relationshipText} Last seen: ${selected.lastSeen ? new Date(selected.lastSeen).toLocaleString() : 'unknown'}. ${nodePresence(selected)}.`);
  text(configurationContext,
    `${selected.displayName} (${selected.nodeId}) · ${roleLabel(selected)} · ${selected.online ? 'Control connected' : 'Control disconnected'}`);
  const fileTargets = fileTargetsForSelection();
  const preservesComments = fileTargets.length > 0 && fileTargets.every(nodeId =>
    nodeCapabilities.get(nodeId)?.includes('config.file-comments.v1'));
  text(commentPreservationState, preservesComments ? 'Comments preserved for every target' : 'Comments not guaranteed for every target');
  commentPreservationState.className = `pill ${preservesComments ? 'online' : 'warning'}`;
  const capabilities = managedCapabilities(selected);
  if (capabilities.length === 0) capabilities.push('Discovery only');
  capabilities.forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill';
    selectedServerCapabilities.append(pill);
  });
}

function topologyLink(backend, reporterOnline) {
  const registered = nodeIndex.get(backend.backendId);
  const link = document.createElement('span');
  link.className = `topology-link ${registered?.online ? 'online' : 'warning'}`;
  link.classList.toggle('inspected', backend.backendId === workspace.inspectedServerId);
  link.append(text(document.createElement('strong'), backend.displayName));
  link.append(text(document.createElement('small'), !enrollmentsLoaded
    ? 'Control enrollment unavailable'
    : enrollmentIds.has(backend.backendId)
    ? `Enrolled · Control ${registered ? (registered.online ? 'connected' : 'disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (!reporterOnline) {
    link.append(text(document.createElement('small'), 'Presence stale · reporting proxy disconnected'));
  } else if (backend.presenceKnown) {
    link.append(text(document.createElement('small'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    link.append(text(document.createElement('small'), 'Presence not available'));
  }
  return link;
}

function renderTopology() {
  const proxies = allNodeItems.filter(isProxy);
  topology.replaceChildren();
  topology.classList.toggle('empty', proxies.length === 0);
  if (proxies.length === 0) {
    text(topology, 'No proxy nodes have registered with Control.');
    return;
  }
  proxies.forEach(proxy => {
    const row = document.createElement('article');
    row.className = 'topology-row';
    const proxyIdentity = document.createElement('div');
    proxyIdentity.className = 'topology-proxy';
    proxyIdentity.append(text(document.createElement('strong'), proxy.displayName));
    proxyIdentity.append(text(document.createElement('small'),
      `${platformLabel(proxy.platform)} proxy · Control ${proxy.online ? 'connected' : 'disconnected'}`));
    const backendList = document.createElement('div');
    backendList.className = 'topology-backends';
    const backends = Array.isArray(proxy.backends) ? proxy.backends : [];
    if (backends.length === 0) {
      backendList.append(text(document.createElement('small'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => backendList.append(topologyLink(backend, proxy.online)));
    }
    row.append(proxyIdentity, backendList);
    topology.append(row);
  });
  if (backendTopologyTruncated) {
    const warning = text(document.createElement('p'), 'Backend topology is truncated; some proxy relationships are not shown.');
    warning.className = 'warning-text';
    topology.prepend(warning);
  }
}

function dashboardContext() {
  const node = nodeIndex.get(selectedServerId);
  return node ? `${node.nodeId}|${node.sessionId}|${node.online}|${node.acceptedCapabilities.includes('data.inspect.v1')}|${dashboardTopologySignature}|${dashboardConfigurationGeneration}` : '';
}

function topologySignature(items) {
  return items.filter(isProxy).map(proxy => `${proxy.nodeId}|${proxy.sessionId}|${proxy.online}|${proxy.snapshotSequence}`).join(';');
}

function emptyDashboardInspectionStatus() {
  return {overview: 'not-loaded', voteSiteHealth: 'not-loaded', voteLog24h: 'not-required', voteLog30d: 'not-required'};
}

function dashboardRecord(value, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} returned malformed data.`);
  }
  return value;
}

function boundedDashboardString(value, maximum, allowEmpty = false) {
  if (typeof value !== 'string') return {value: '', incomplete: true};
  const normalized = value.trim();
  if (!allowEmpty && !normalized) return {value: '', incomplete: true};
  return {value: normalized.slice(0, maximum), incomplete: normalized.length > maximum};
}

function normalizeDashboardCollection(value, maximum, normalize) {
  if (!Array.isArray(value)) return {items: [], incomplete: true};
  let incomplete = value.length > maximum;
  const items = [];
  value.slice(0, maximum).forEach(entry => {
    const normalized = normalize(entry);
    if (!normalized) incomplete = true;
    else {
      items.push(normalized.value);
      incomplete ||= normalized.incomplete;
    }
  });
  return {items, incomplete};
}

function normalizeDashboardOverview(value) {
  const source = dashboardRecord(value, 'Overview inspection');
  const result = {...source};
  let incomplete = false;
  ['configuredVoteSites', 'enabledVoteSites'].forEach(field => {
    const count = finiteCount(source[field]);
    if (count == null || !Number.isSafeInteger(count)) incomplete = true;
    result[field] = count;
  });
  if (result.configuredVoteSites != null && result.enabledVoteSites != null
      && result.enabledVoteSites > result.configuredVoteSites) incomplete = true;
  ['autoCreateVoteSites', 'processRewards', 'voteLoggingEnabled', 'voteLogAvailable', 'voteLogReadable',
    'proxyMode', 'votifierDetected', 'configurationHealthy'].forEach(field => {
    if (typeof source[field] !== 'boolean') incomplete = true;
    result[field] = typeof source[field] === 'boolean' ? source[field] : undefined;
  });
  incomplete ||= invalidVoteLoggingState(result);
  const requiredStrings = new Set(['pluginVersion', 'platform', 'serverSoftware', 'serverVersion', 'dataStorage']);
  [['pluginVersion', 80], ['platform', 32], ['serverSoftware', 80], ['serverVersion', 80],
    ['dataStorage', 32], ['proxyMethod', 32]].forEach(([field, maximum]) => {
    const normalized = boundedDashboardString(source[field], maximum, field === 'proxyMethod');
    incomplete ||= normalized.incomplete;
    if (requiredStrings.has(field) && !normalized.value) incomplete = true;
    result[field] = normalized.value;
  });
  const platforms = new Set(['BUKKIT']);
  const dataStorages = new Set(['SQLITE', 'MYSQL']);
  if (!platforms.has(result.platform.toUpperCase()) || !dataStorages.has(result.dataStorage.toUpperCase())) {
    incomplete = true;
  }
  const proxyMethods = new Set(['PLUGINMESSAGING', 'REDIS', 'MQTT', 'MYSQL', 'SOCKETS', 'HTTP']);
  if (result.proxyMode === true && !proxyMethods.has(result.proxyMethod.toUpperCase())) incomplete = true;
  return {result, incomplete};
}

function invalidVoteLoggingState(value) {
  const available = Object.hasOwn(value, 'voteLogAvailable') ? value.voteLogAvailable : value.voteLoggingAvailable;
  return available === true && value.voteLoggingEnabled !== true
    || value.voteLogReadable === true && available !== true;
}

function normalizeDashboardVoteSiteHealth(value, expectedDays = 30) {
  const source = dashboardRecord(value, 'Vote Site health inspection');
  const days = finiteCount(source.days);
  let incomplete = days == null || days !== expectedDays;
  const booleanFields = ['voteLoggingEnabled', 'voteLoggingAvailable', 'voteLogReadable', 'autoCreateVoteSites'];
  booleanFields.forEach(field => { if (typeof source[field] !== 'boolean') incomplete = true; });
  incomplete ||= invalidVoteLoggingState(source);
  ['truncated', 'detectedUnconfiguredServicesTruncated'].forEach(field => {
    if (typeof source[field] !== 'boolean' || source[field] === true) incomplete = true;
  });
  const allowedStatuses = new Set(['ACTIVE', 'DISABLED', 'SERVICE_SITE_MISSING', 'VOTE_LOG_UNAVAILABLE',
    'VOTE_LOG_UNREADABLE', 'NO_RECENT_VOTES']);
  const voteSiteKeys = new Set();
  const serviceAggregates = new Map();
  const sites = normalizeDashboardCollection(source.sites, 100, entry => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return null;
    const status = boundedDashboardString(entry.status, 64);
    const key = boundedDashboardString(entry.key, 64, true);
    const displayName = boundedDashboardString(entry.displayName, 100, true);
    const serviceSite = boundedDashboardString(entry.serviceSite, 64, true);
    const canonicalKey = key.value.toLowerCase();
    if (status.incomplete || !allowedStatuses.has(status.value) || !key.value || voteSiteKeys.has(canonicalKey)
        || typeof entry.enabled !== 'boolean' || typeof entry.hasRewards !== 'boolean' || serviceSite.incomplete) return null;
    const expectedStatuses = entry.enabled === false ? new Set(['DISABLED']) : !serviceSite.value
      ? new Set(['SERVICE_SITE_MISSING']) : source.voteLoggingAvailable !== true
      ? new Set(['VOTE_LOG_UNAVAILABLE']) : source.voteLogReadable !== true
      ? new Set(['VOTE_LOG_UNREADABLE']) : new Set(['ACTIVE', 'NO_RECENT_VOTES']);
    if (!expectedStatuses.has(status.value)) return null;
    const aggregate = source.voteLogReadable === true
      ? validDashboardVoteSiteAggregate(entry, status.value) : null;
    if (source.voteLogReadable === true && !aggregate) return null;
    const serviceIdentity = serviceSite.value.toLowerCase();
    const previousAggregate = serviceAggregates.get(serviceIdentity);
    if (previousAggregate && !dashboardVoteSiteAggregatesMatch(previousAggregate, aggregate)) return null;
    if (aggregate && serviceIdentity) serviceAggregates.set(serviceIdentity, aggregate);
    const aggregateFields = ['loggedVotes', 'immediateVotes', 'cachedVotes', 'lastVoteTime'];
    if (source.voteLogReadable !== true
        && aggregateFields.some(field => Object.hasOwn(entry, field))) return null;
    voteSiteKeys.add(canonicalKey);
    return {value: {...entry, status: status.value, key: key.value, displayName: displayName.value,
      serviceSite: serviceSite.value, ...(aggregate || {})}, incomplete: key.incomplete || displayName.incomplete};
  });
	const configuredServiceKeys = new Set(sites.items.map(site => site.serviceSite.toLowerCase()));
	const detectedServiceKeys = new Set();
  const detected = normalizeDashboardCollection(source.detectedUnconfiguredServices, 100, entry => {
    const service = boundedDashboardString(entry, 100);
    const identity = service.value.toLowerCase();
		if (service.incomplete || configuredServiceKeys.has(identity) || detectedServiceKeys.has(identity)) return null;
    detectedServiceKeys.add(identity);
    return {value: service.value, incomplete: false};
	});
	const unmatchedServiceKeys = new Set();
	const unmatched = normalizeDashboardCollection(source.unmatchedLoggedServices, 100, entry => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return null;
		const service = boundedDashboardString(entry.serviceSite, 64);
    const identity = service.value.toLowerCase();
    if (service.incomplete || configuredServiceKeys.has(identity) || unmatchedServiceKeys.has(identity)) return null;
    unmatchedServiceKeys.add(identity);
    return {value: {...entry, serviceSite: service.value}, incomplete: false};
	});
	incomplete ||= sites.incomplete || detected.incomplete || unmatched.incomplete;
	if (source.voteLogReadable !== true && unmatched.items.length > 0) incomplete = true;
	const unmatchedItems = source.voteLogReadable === true ? unmatched.items : [];
	return {result: {...source, days, ...Object.fromEntries(booleanFields.map(field =>
		[field, typeof source[field] === 'boolean' ? source[field] : undefined])), sites: sites.items,
		detectedUnconfiguredServices: detected.items, unmatchedLoggedServices: unmatchedItems}, incomplete};
}

function validDashboardVoteSiteAggregate(entry, status) {
  const loggedVotes = finiteCount(entry.loggedVotes);
  const immediateVotes = finiteCount(entry.immediateVotes);
  const cachedVotes = finiteCount(entry.cachedVotes);
  const lastVoteTime = finiteCount(entry.lastVoteTime);
  if (loggedVotes == null || immediateVotes == null || cachedVotes == null || lastVoteTime == null
      || immediateVotes + cachedVotes !== loggedVotes) return null;
  if (loggedVotes === 0 ? lastVoteTime !== 0 : lastVoteTime === 0) return null;
  if (status === 'ACTIVE' && loggedVotes === 0 || status === 'NO_RECENT_VOTES' && loggedVotes !== 0) return null;
  return {loggedVotes, immediateVotes, cachedVotes, lastVoteTime};
}

function dashboardVoteSiteAggregatesMatch(left, right) {
  return left.loggedVotes === right.loggedVotes && left.immediateVotes === right.immediateVotes
    && left.cachedVotes === right.cachedVotes && left.lastVoteTime === right.lastVoteTime;
}

function dashboardHealthContradictsOverview(overview, health) {
  if (!overview || !health) return false;
  const flagsMatch = health.autoCreateVoteSites === overview.autoCreateVoteSites
    && health.voteLoggingEnabled === overview.voteLoggingEnabled
    && health.voteLoggingAvailable === overview.voteLogAvailable
    && health.voteLogReadable === overview.voteLogReadable;
  const configured = finiteCount(overview.configuredVoteSites);
  const siteCountMatches = health.truncated === true || configured == null
    || Array.isArray(health.sites) && health.sites.length === configured;
  const enabled = finiteCount(overview.enabledVoteSites);
  const enabledSiteCountMatches = health.truncated === true || enabled == null
    || Array.isArray(health.sites) && health.sites.filter(site => site.enabled === true).length === enabled;
  return !flagsMatch || !siteCountMatches || !enabledSiteCountMatches;
}

function dashboardHealthContradictsVoteSummary(health, summary) {
  if (!health || !summary || health.voteLogReadable !== true || !Array.isArray(health.sites)) return false;
  if (dashboardHealthServicesContradictSummary(health.sites, summary.topServices)) return true;
  const rowContradiction = health.sites.some(site => {
    if (!site || typeof site !== 'object' || Array.isArray(site)) return false;
    return [['loggedVotes', 'total'], ['immediateVotes', 'immediate'], ['cachedVotes', 'cached']]
      .some(([siteField, summaryField]) => {
        const siteCount = finiteCount(site[siteField]);
        const summaryCount = finiteCount(summary[summaryField]);
        return siteCount != null && summaryCount != null && siteCount > summaryCount;
      });
  });
  if (rowContradiction) return true;
  return [['loggedVotes', 'total'], ['immediateVotes', 'immediate'], ['cachedVotes', 'cached']]
    .some(([siteField, summaryField]) => {
      const summaryCount = finiteCount(summary[summaryField]);
      return summaryCount != null && dashboardHealthAggregateExceedsSummary(
        health.sites, siteField, summaryCount);
    });
}

function dashboardHealthServicesContradictSummary(sites, topServices) {
  if (!Array.isArray(sites) || !Array.isArray(topServices)) return false;
  const topServicesComplete = topServices.length < 20;
  const topServiceCounts = new Map();
  topServices.forEach(entry => {
    const identity = dashboardCountRowIdentity(entry, 'service');
    const count = finiteCount(entry?.count);
    if (identity != null && count != null) topServiceCounts.set(identity, count);
  });
  const countedServices = new Set();
  return sites.some(site => {
    const serviceIdentity = typeof site?.serviceSite === 'string'
      ? site.serviceSite.trim().toLowerCase() : '';
    const loggedVotes = finiteCount(site?.loggedVotes);
    if (!serviceIdentity || loggedVotes == null) return false;
    if (countedServices.has(serviceIdentity)) {
      // A 64-character ServiceSite may be a truncated identity. Preserve the
      // existing fail-closed behavior instead of silently merging aliases.
      return serviceIdentity.length >= 64;
    }
    countedServices.add(serviceIdentity);
    // A complete ranking intentionally omits services with no votes. Only a
    // positive health aggregate can contradict that omission.
    if (!topServiceCounts.has(serviceIdentity)) return loggedVotes > 0 && topServicesComplete;
    return loggedVotes > topServiceCounts.get(serviceIdentity);
  });
}

function dashboardHealthAggregateExceedsSummary(sites, siteField, summaryCount) {
  let aggregate = 0;
  const countedServices = new Set();
  return sites.some(site => {
    const serviceIdentity = typeof site?.serviceSite === 'string' ? site.serviceSite.trim().toLowerCase() : '';
    if (serviceIdentity && countedServices.has(serviceIdentity)) {
      // The node bounds serialized ServiceSite values to 64 characters. A
      // duplicate at that boundary may represent two distinct truncated names,
      // so treat the aggregate as unchecked instead of silently deduplicating it.
      return serviceIdentity.length >= 64;
    }
    const siteCount = finiteCount(site?.[siteField]);
    if (siteCount == null) return false;
    if (serviceIdentity) countedServices.add(serviceIdentity);
    aggregate += siteCount;
    return aggregate > summaryCount;
  });
}

function normalizeDashboardCountRows(value, maximum, label) {
  const identities = new Set();
  return normalizeDashboardCollection(value, maximum, entry => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return null;
    const name = boundedDashboardString(entry[label], 64, false);
    const hasCount = Object.hasOwn(entry, 'count');
    const hasVotes = Object.hasOwn(entry, 'votes');
    const count = hasCount ? finiteCount(entry.count) : hasVotes ? finiteCount(entry.votes) : null;
    const legacyCount = hasVotes ? finiteCount(entry.votes) : null;
    if (name.incomplete || count == null || hasVotes && legacyCount == null
        || hasCount && hasVotes && count !== legacyCount) return null;
    const identity = name.value.toLowerCase();
    if (identities.has(identity)) return null;
    identities.add(identity);
    return {value: {...entry, [label]: name.value, count}, incomplete: false};
  });
}

function normalizeDashboardVoteSummary(value, expectedDays = 30) {
  const source = dashboardRecord(value, 'VoteLog summary inspection');
  const days = finiteCount(source.days);
  const total = finiteCount(source.total);
  const immediate = finiteCount(source.immediate);
  const cached = finiteCount(source.cached);
  const uniqueVoters = finiteCount(source.uniqueVoters);
  let incomplete = days == null || days !== expectedDays || total == null || immediate == null
    || cached == null || uniqueVoters == null;
  const services = normalizeDashboardCountRows(source.topServices, 20, 'service');
  const servers = normalizeDashboardCountRows(source.topServers, 20, 'server');
  incomplete ||= services.incomplete || servers.incomplete
    || !countRowsAreNonIncreasing(services.items) || !countRowsAreNonIncreasing(servers.items);
  incomplete ||= !countRowsArePositive(services.items) || !countRowsArePositive(servers.items);
  if (total != null) {
    incomplete ||= immediate == null || cached == null || immediate + cached !== total
      || uniqueVoters == null || total > 0 && uniqueVoters <= 0 || uniqueVoters > total;
    incomplete ||= total > 0 && (services.items.length === 0 || servers.items.length === 0);
    incomplete ||= countRowsExceedTotal(services.items, total)
      || countRowsExceedTotal(servers.items, total)
      || !countRowsSumMatchesTotal(services.items, total)
      || !countRowsSumMatchesTotal(servers.items, total);
  }
  return {result: {...source, days, total, immediate, cached, uniqueVoters,
    topServices: services.items, topServers: servers.items}, incomplete};
}

function countRowsExceedTotal(items, total) {
  let remaining = total;
  for (const entry of items) {
    if (entry.count > remaining) return true;
    remaining -= entry.count;
  }
  return false;
}

function countRowsSumMatchesTotal(items, total) {
  return items.length >= 20 || items.reduce((sum, entry) => sum + entry.count, 0) === total;
}

function countRowsAreNonIncreasing(items) {
  for (let index = 1; index < items.length; index++) {
    if (items[index].count > items[index - 1].count) return false;
  }
  return true;
}

function countRowsArePositive(items) {
  return items.every(entry => entry.count > 0);
}

function dashboardCountRowIdentity(entry, label) {
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return null;
  const name = boundedDashboardString(entry[label], 100, false);
  return name.incomplete ? null : name.value.toLowerCase();
}

function dashboardCountRowsContradict(shortRows, longRows, label) {
  if (!Array.isArray(shortRows) || !Array.isArray(longRows)) return false;
  const widerWindowIsComplete = longRows.length < 20;
  const longCounts = new Map();
  longRows.forEach(entry => {
    const identity = dashboardCountRowIdentity(entry, label);
    const count = finiteCount(entry?.count);
    if (identity != null && count != null) longCounts.set(identity, count);
  });
  return shortRows.some(entry => {
    const identity = dashboardCountRowIdentity(entry, label);
    const shortCount = finiteCount(entry?.count);
    if (identity == null || shortCount == null) return false;
    if (!longCounts.has(identity)) return widerWindowIsComplete;
    return shortCount > longCounts.get(identity);
  });
}

function dashboardVoteSummariesContradict(shortWindow, longWindow) {
  const scalarContradiction = ['total', 'immediate', 'cached', 'uniqueVoters'].some(field =>
    finiteCount(shortWindow?.[field]) != null && finiteCount(longWindow?.[field]) != null
      && shortWindow[field] > longWindow[field]);
  return scalarContradiction
    || dashboardCountRowsContradict(shortWindow?.topServices, longWindow?.topServices, 'service')
    || dashboardCountRowsContradict(shortWindow?.topServers, longWindow?.topServers, 'server');
}

function issue(severity, title, detail, action, tab, scrollTarget = '', preset = '', onAction = null) {
  return {severity, title, detail, action, tab, scrollTarget, preset, onAction};
}

function dashboardIssues() {
  const summary = {items: [], total: 0, actionable: 0, hasCritical: false, hasWarning: false};
  const issues = {push(item) {
    summary.total += 1;
    if (item.severity !== 'informational') summary.actionable += 1;
    summary.hasCritical ||= item.severity === 'critical';
    summary.hasWarning ||= item.severity === 'warning';
    if (summary.items.length < 30) summary.items.push(item);
  }};
  const selected = nodeIndex.get(selectedServerId);
  if (!selected) {
    issues.push(issue('informational', 'Choose a VotingPlugin server',
      'Server-specific health and administration actions need an explicit target.', 'Choose server', 'servers'));
  } else if (!selected.online) {
    issues.push(issue('warning', `${selected.displayName} is disconnected from Control`,
      'Minecraft presence is separate; reconnect the Control node before attempting changes.', 'View server', 'servers'));
  } else if (dashboardLoadedContext !== dashboardContext()) {
    issues.push(issue('informational', 'Detailed health has not been loaded',
      'Refresh the dashboard to inspect Vote Sites, VoteLog, configuration, and runtime state.', 'Refresh dashboard', 'overview'));
  }
  if (dashboardLoadedContext === dashboardContext() || Object.values(dashboardInspectionStatus)
      .some(status => ['failed', 'incomplete'].includes(status))) {
    const labels = {overview: 'Overview', voteSiteHealth: 'Vote Site health',
      voteLog24h: '24-hour VoteLog summary', voteLog30d: '30-day VoteLog summary'};
    Object.entries(dashboardInspectionStatus).forEach(([inspection, status]) => {
      if (!['failed', 'incomplete'].includes(status)) return;
      issues.push(issue('warning', `${labels[inspection]} is ${status === 'failed' ? 'unavailable' : 'incomplete'}`,
        'Some dashboard checks could not be verified, so this server is not reported as fully healthy.',
        'Refresh dashboard', 'overview'));
    });
  }
  if (backendTopologyTruncated) {
    issues.push(issue('informational', 'Topology view is incomplete',
      'Control bounded the backend summaries, so omitted relationships are not classified as offline.', 'View servers', 'servers'));
  }
  const disconnectedNodes = allNodeItems.filter(node => !node.online && node.nodeId !== selectedServerId);
  disconnectedNodes.forEach(node => {
    issues.push(issue('warning', `${node.displayName} is disconnected from Control`,
      `The registered ${roleLabel(node).toLowerCase()} is not currently connected. Minecraft availability is separate.`,
      'View server', 'servers'));
  });
  const reportedBackends = new Map();
  allNodeItems.filter(node => isProxy(node) && node.online).forEach(proxy => {
    (Array.isArray(proxy.backends) ? proxy.backends : []).forEach(backend => {
      if (!backend || typeof backend.backendId !== 'string' || !backend.backendId) return;
      const current = reportedBackends.get(backend.backendId) || {
        backend, proxies: new Set()
      };
      current.proxies.add(proxy.displayName);
      if (backend.presenceKnown && !backend.available) {
        issues.push(issue('warning', `${backend.displayName} is unavailable to ${proxy.displayName}`,
          `Reported unavailable by ${proxy.displayName}.`, 'Test communication', 'network', 'transport-test-card'));
      }
      reportedBackends.set(backend.backendId, current);
    });
  });
  reportedBackends.forEach(({backend, proxies}) => {
    const proxyNames = [...proxies].join(', ');
    const registered = nodeIndex.get(backend.backendId);
    const registeredBukkitBackend = registered?.platform === 'BUKKIT';
    if (!registeredBukkitBackend) issues.push(issue('warning', `${backend.displayName} is not registered as a Bukkit backend`,
      `Reported by ${proxyNames}; Control has no current Bukkit backend record.`, 'View servers', 'servers'));
    if (enrollmentsLoaded && registeredBukkitBackend && !enrollmentIds.has(backend.backendId)) {
      issues.push(issue('warning', `${backend.displayName} is not enrolled`,
        'Enroll the node before expecting authenticated Control connectivity.', 'Open access', 'access'));
    }
  });
  if (dashboardOverview && dashboardLoadedContext === dashboardContext()) {
    if (dashboardOverview.configurationHealthy === false) issues.push(issue('warning', 'VotingPlugin configuration needs attention',
      'The selected backend reported an unhealthy configuration state.', 'Open configuration', 'configurations'));
    if (dashboardOverview.votifierDetected === false) issues.push(issue('warning', 'Votifier was not detected',
      'The selected backend cannot confirm the vote-listener prerequisite.', 'Diagnose', 'network', 'network-doctor-card'));
    if (dashboardOverview.configuredVoteSites === 0) issues.push(issue('warning', 'No Vote Sites are configured',
      'Add a reviewed VoteSites entry before expecting service matches.', 'Add Vote Site', 'quick-setup', 'quick-setup-card', 'vote-site'));
    else if (dashboardOverview.enabledVoteSites === 0) issues.push(issue('warning', 'All Vote Sites are disabled',
      'Enable at least one configured Vote Site before expecting votes.', 'Open Vote Sites', 'data', 'site-health-card'));
    if (dashboardOverview.processRewards === false) issues.push(issue('warning', 'Vote rewards are disabled on this backend',
      'ProcessRewards is off. Confirm that this is intentional for the selected topology.', 'Open setting', 'quick-setup', 'settings-catalog-card'));
    if (dashboardOverview.voteLoggingEnabled === true && dashboardOverview.voteLogReadable !== true) issues.push(issue('warning', 'VoteLog is unavailable',
      'Logging is enabled, but retained logged-event history is not readable.', 'Open Vote Logs', 'data', 'vote-log-summary-result'));
    if (voteLoggingRestartRequired()) issues.push(issue('warning', `Restart required on ${selected?.displayName || selectedServerId}`,
      'Vote logging configuration was saved but is not live until the backend restarts and reconnects.', 'Open setup', 'quick-setup'));
    if (dashboardOverview.proxyMode === true && proxyReportsFor(selectedServerId).length === 0) issues.push(issue('warning', 'No proxy reports this backend',
      'Proxy mode is enabled, but no connected proxy currently reports the selected backend ID.', 'Open routing', 'network'));
  }
  if (dashboardVoteSiteHealth && dashboardLoadedContext === dashboardContext()) {
    const sites = Array.isArray(dashboardVoteSiteHealth.sites) ? dashboardVoteSiteHealth.sites : [];
    sites.filter(site => site.status === 'SERVICE_SITE_MISSING').forEach(site => {
      issues.push(issue('warning', `${site.displayName || site.key} has no ServiceSite`,
        'Votes cannot be matched reliably until the service identifier is configured.', 'Open Vote Sites', 'data', 'site-health-card'));
    });
    dashboardVoteSiteHealth.detectedUnconfiguredServices.forEach(service => {
      issues.push(issue('warning', `Detected service is not configured: ${service}`,
        dashboardVoteSiteHealth.autoCreateVoteSites === false
          ? 'AutoCreateVoteSites is disabled; review and create this site explicitly.'
          : 'VotingPlugin observed this service but no configured ServiceSite matches it.',
        'Configure', 'quick-setup', 'quick-setup-card', 'vote-site'));
    });
    dashboardVoteSiteHealth.unmatchedLoggedServices.forEach(service => {
      issues.push(issue('warning', `Logged service does not match a Vote Site: ${service.serviceSite || 'Unknown'}`,
        'A retained vote event used a service identifier with no configured match.', 'Open Vote Sites', 'data', 'site-health-card'));
    });
  }
  operationHistoryItems.filter(operation => ['FAILED', 'COMPLETED_WITH_ERRORS'].includes(operation.state))
    .forEach(operation => issues.push(issue('warning', `${operationLabel(operation)} needs review`,
      operationPhase(operation), 'View operation', 'activity')));
  if (operationHistoryStatus === 'failed') issues.push(issue('warning', 'Operation history is unavailable',
    'Recent configuration failures could not be loaded, so dashboard health is incomplete.',
    'Retry activity', 'activity', '', '', () => {
      openWorkspace('activity');
      return loadOperationHistory();
    }));
  if (enrollmentStatus === 'failed') issues.push(issue('warning', 'Enrollment state is unavailable',
    'Recent enrollment state could not be loaded, so backend access health is incomplete.',
    'Retry access', 'access', '', '', () => {
      openWorkspace('access');
      return loadEnrollments();
    }));
  return summary;
}

function renderAttention(issues, total = issues.length) {
  attentionFeed.replaceChildren();
  if (issues.length === 0) {
    const empty = text(document.createElement('p'), 'No actionable problems were found in the currently loaded state.');
    empty.className = 'attention-empty';
    attentionFeed.append(empty);
    return;
  }
  issues.slice(0, 8).forEach(item => {
    const row = document.createElement('article');
    row.className = `attention-item ${item.severity}`;
    const indicator = document.createElement('span');
    indicator.className = 'attention-indicator';
    indicator.setAttribute('aria-label', item.severity);
    const copy = document.createElement('div');
    copy.append(text(document.createElement('strong'), item.title), text(document.createElement('small'), item.detail));
    const action = text(document.createElement('button'), item.action);
    action.type = 'button';
    action.className = 'secondary compact';
    action.addEventListener('click', () => {
      if (item.onAction) void item.onAction();
      else openWorkspace(item.tab, item.scrollTarget, item.preset);
    });
    row.append(indicator, copy, action);
    attentionFeed.append(row);
  });
  if (total > 8) attentionFeed.append(text(document.createElement('small'), `${total - 8} additional issues are available in their related pages.`));
}

function addQuickAction(label, tab, scrollTarget = '', preset = '', configView = '') {
  const button = text(document.createElement('button'), label);
  button.type = 'button';
  button.className = 'secondary';
  button.addEventListener('click', () => {
    openWorkspace(tab, scrollTarget, preset);
    if (configView) setConfigView(configView);
  });
  overviewQuickActions.append(button);
}

function renderQuickActions() {
  overviewQuickActions.replaceChildren();
  const selected = nodeIndex.get(selectedServerId);
  if (workspace.selectedTargetIds.size) addQuickAction('General Settings', 'general-settings');
  if (inspectionCapableNode()) addQuickAction('Vote Sites', 'data', 'site-health-card');
  if (fileTargetsForSelection().length) addQuickAction('Configuration', 'configurations', '', '', 'yaml');
  if (comparisonTargetIds().length >= 2) addQuickAction('Compare Configuration', 'configurations', '', '', 'compare');
  if (selected?.online && isBackend(selected) && selected.acceptedCapabilities.includes('config.quick-setup.v1')) {
    addQuickAction('Add Vote Site', 'quick-setup', 'quick-setup-card', 'vote-site');
    addQuickAction('Build Reward', 'quick-setup', 'reward-builder-card');
  }
  const syncSources = syncSourceCandidates();
  const syncTargets = syncTargetCandidates();
  if (syncSources.some(source => syncTargets.some(target => target.nodeId !== source.nodeId))) {
    addQuickAction('Sync Vote Sites', 'quick-setup', 'quick-setup-card', 'sync-vote-sites');
  }
  if (inspectionCapableNode()) addQuickAction('Run Network Doctor', 'network', 'network-doctor-card');
  if (transportTestProxies().length) addQuickAction('Test Communication', 'network', 'transport-test-card');
  if (inspectionCapableNode()) addQuickAction('View Logged Events', 'data', 'vote-log-summary-result');
  if (overviewQuickActions.childElementCount === 0) {
    addQuickAction(allNodeItems.length ? 'View Servers' : 'Refresh Servers', 'servers');
  }
}

function renderVoteActivity() {
  voteActivity.replaceChildren();
  if (!authenticated || !inspectionCapableNode() || dashboardLoadedContext !== dashboardContext()) {
    text(voteActivity, 'No current retained VoteLog service summary is loaded.');
    return;
  }
  const services = Array.isArray(dashboardVoteSummary30d?.topServices)
    ? dashboardVoteSummary30d.topServices.slice(0, 6) : [];
  if (services.length === 0) {
    text(voteActivity, dashboardOverview?.voteLoggingEnabled === false
      ? 'Vote logging is disabled; no retained event chart is expected.' : 'No readable retained VoteLog service summary is loaded.');
    return;
  }
  const maximum = Math.max(...services.map(service => service.count), 1);
  services.forEach(service => {
    const row = document.createElement('div');
    row.className = 'activity-bar-row';
    row.append(text(document.createElement('strong'), service.service || 'Unknown service'));
    const track = document.createElement('progress');
    track.className = 'activity-bar-track';
    track.max = maximum;
    track.value = service.count;
    track.setAttribute('aria-label', `${service.service || 'Unknown service'} logged votes`);
    row.append(track, text(document.createElement('span'), service.count));
    voteActivity.append(row);
  });
}

function operationLabel(operation) {
  const configuration = operation.configuration || {};
  if (configuration.domain === 'quick-setup') {
    return ({'vote-site': 'Vote Site change', 'sync-vote-sites': 'Vote Site synchronization',
      'reward-builder': 'Reward update', 'proxy-method': 'Proxy method change',
      'vote-logging': 'Vote logging change'}[configuration.preset] || 'Guided setup change');
  }
  if (configuration.domain === 'file') return `${configuration.fileName || 'Configuration'} change`;
  if (configuration.domain === 'proxy-routing') return 'Proxy routing change';
  return 'Configuration operation';
}

function renderOverviewActivity() {
  overviewActivity.replaceChildren();
  const relevantOperations = overviewOperations();
  if (relevantOperations.length === 0) {
    text(overviewActivity, 'No retained configuration operations.');
    return;
  }
  relevantOperations.slice(0, 5).forEach(operation => {
    const item = document.createElement('article');
    item.className = 'activity-item';
    const results = Object.values(operation.results || {});
    const targetCount = Object.keys(operation.nodeStates || {}).length || results.length;
    const successful = results.filter(result => result?.success).length;
    item.append(text(document.createElement('strong'), operationLabel(operation)));
    const when = operation.createdAt ? new Date(operation.createdAt).toLocaleString() : 'Time unavailable';
    item.append(text(document.createElement('small'), `${operationPhase(operation)} · ${successful}/${targetCount} targets successful · ${when}`));
    overviewActivity.append(item);
  });
}

function renderMetrics() {
  const issueSummary = dashboardIssues();
  const issues = issueSummary.items;
  const selected = nodeIndex.get(selectedServerId);
  const current = dashboardLoadedContext === dashboardContext();
  const hasCritical = issueSummary.hasCritical;
  const hasWarning = issueSummary.hasWarning;
  const hasIncompleteInspection = Object.values(dashboardInspectionStatus)
    .some(status => ['failed', 'incomplete'].includes(status));
  const state = !selected ? 'Unavailable' : hasCritical ? 'Critical'
    : !selected.online || !current || hasWarning || hasIncompleteInspection ? 'Warning' : 'Healthy';
  text(metricHealth, state);
  text(metricHealthDetail, !selected ? 'Choose a server' : !selected.online ? 'Control disconnected'
    : !current ? 'Inspection not loaded' : hasCritical ? 'Immediate attention required'
    : hasIncompleteInspection ? 'Dashboard data incomplete'
    : hasWarning ? 'Review observed warnings' : 'No observed problems');
  metricHealth.closest('article').className = selected ? state.toLowerCase() : '';
  const votes24h = current ? finiteCount(dashboardVoteSummary24h?.total) : null;
  text(metricVotesToday, votes24h == null ? '—' : votes24h);
  const configured = current ? finiteCount(dashboardOverview?.configuredVoteSites) : null;
  const enabled = current ? finiteCount(dashboardOverview?.enabledVoteSites) : null;
  const siteCountsKnown = configured != null && enabled != null;
  const allConfiguredSitesDisabled = siteCountsKnown && configured > 0 && enabled === 0;
  const siteWarnings = current && Array.isArray(dashboardVoteSiteHealth?.sites)
    ? dashboardVoteSiteHealth.sites.filter(site => site.status === 'SERVICE_SITE_MISSING').length
      + dashboardVoteSiteHealth.detectedUnconfiguredServices.length
      + dashboardVoteSiteHealth.unmatchedLoggedServices.length
      + (allConfiguredSitesDisabled ? 1 : 0) : null;
  text(metricVoteSites, !siteCountsKnown ? '—' : `${enabled}/${configured}`);
  text(metricVoteSitesDetail, !siteCountsKnown ? 'Counts unavailable'
    : `${enabled} enabled${siteWarnings == null ? '' : ` · ${siteWarnings} need attention`}`);
  text(metricProxy, !current ? '—' : dashboardOverview?.proxyMode === false ? 'Standalone' : dashboardOverview?.proxyMethod || 'Unknown');
  text(metricProxyDetail, !current ? 'Method unavailable' : dashboardOverview?.proxyMode === false
    ? 'Proxy mode disabled' : proxyReportsFor(selectedServerId).length ? 'Proxy relationship reported' : 'No reporting proxy');
  const logged30d = current ? finiteCount(dashboardVoteSummary30d?.total) : null;
  text(metricLoggedEvents, logged30d == null ? '—' : logged30d);
  const actionable = issueSummary.actionable;
  text(metricIssues, actionable);
  text(metricIssuesDetail, actionable ? `${actionable} actionable` : 'No observed problems');
  renderAttention(issues, issueSummary.total);
  renderQuickActions();
  renderVoteActivity();
  renderOverviewActivity();
  renderScopeOverview();
}

function finiteCount(value) {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function syncSourceCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.files.v1') &&
    node.acceptedCapabilities.includes('config.file-comments.v1'));
}

function syncTargetCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.vote-sites-sync.v1'));
}

function selectedVoteSitesTargets() {
  const capable = new Set(syncTargetCandidates().map(node => node.nodeId));
  return [...voteSitesTargetIds].filter(nodeId => capable.has(nodeId) && nodeId !== voteSitesSourceId);
}

function invalidateVoteSitesSyncPreview(message) {
  if (approvedQuickPreview?.workflow === 'sync-vote-sites') approvedQuickPreview = null;
  if (quickPreset.value !== 'sync-vote-sites') return;
  inputGeneration++;
  text(quickOperationStatus, message);
}

function renderVoteSitesSync() {
  const sources = syncSourceCandidates();
  const targetsAvailable = syncTargetCandidates();
  const previousSourceId = voteSitesSourceId;
  if (!sources.some(node => node.nodeId === voteSitesSourceId)) {
    voteSitesSourceId = sources.find(node => node.nodeId === selectedServerId)?.nodeId || sources[0]?.nodeId || '';
  }
  if (previousSourceId && previousSourceId !== voteSitesSourceId) {
    invalidateVoteSitesSyncPreview('The sync source became unavailable. Read the replacement source and preview again.');
  }
  const targetIds = new Set(targetsAvailable.map(node => node.nodeId));
  const retainedTargets = new Set([...voteSitesTargetIds].filter(nodeId =>
    targetIds.has(nodeId) && nodeId !== voteSitesSourceId));
  if (retainedTargets.size !== voteSitesTargetIds.size) {
    invalidateVoteSitesSyncPreview('A sync target became unavailable. Preview again before syncing.');
  }
  voteSitesTargetIds = retainedTargets;
  if (!voteSitesTargetsInitialized && sources.length > 0) {
    voteSitesTargetIds = new Set(targetsAvailable.map(node => node.nodeId)
      .filter(nodeId => nodeId !== voteSitesSourceId).slice(0, MAX_SYNC_TARGETS));
    voteSitesTargetsInitialized = true;
  }

  const placeholder = text(document.createElement('option'), sources.length ? 'Choose a source' : 'No readable backends');
  placeholder.value = '';
  voteSitesSource.replaceChildren(placeholder, ...sources.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  voteSitesSource.value = voteSitesSourceId;

  voteSitesTargets.replaceChildren();
  const choices = targetsAvailable.filter(node => node.nodeId !== voteSitesSourceId);
  voteSitesTargets.classList.toggle('empty', choices.length === 0);
  if (choices.length === 0) {
    text(voteSitesTargets, targetsAvailable.length > 0
      ? 'Choose a different source or enroll another backend.' : 'No sync-capable target backends are connected.');
  } else {
    choices.forEach(node => {
      const label = document.createElement('label');
      label.className = 'target-option';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = voteSitesTargetIds.has(node.nodeId);
      checkbox.addEventListener('change', () => {
        if (checkbox.checked && voteSitesTargetIds.size >= MAX_SYNC_TARGETS) {
          checkbox.checked = false;
          text(quickOperationStatus, `A sync operation supports at most ${MAX_SYNC_TARGETS} targets.`);
          return;
        }
        if (checkbox.checked) voteSitesTargetIds.add(node.nodeId); else voteSitesTargetIds.delete(node.nodeId);
        approvedQuickPreview = null;
        inputGeneration++;
        text(quickOperationStatus, 'Targets changed. Read the source and preview again before syncing.');
        updateConfigurationButtons();
      });
      label.append(checkbox, document.createTextNode(`${node.displayName} · ${node.nodeId}`));
      voteSitesTargets.append(label);
    });
  }
  const readyCount = targetsAvailable.length;
  text(voteSitesSyncCapability, readyCount > 0
    ? `${readyCount} sync-capable ${readyCount === 1 ? 'backend' : 'backends'}` : 'Waiting for capable backends');
  voteSitesSyncCapability.className = `pill ${readyCount > 0 ? 'online' : 'neutral'}`;
}

function transportTestProxies() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    node.acceptedCapabilities.includes('config.transport-test.v1'));
}

function renderTransportTest() {
  const proxies = transportTestProxies();
  if (!proxies.some(node => node.nodeId === transportTestProxyId)) {
    transportTestProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
    transportTestBackendId = '';
  }
  const proxy = proxies.find(node => node.nodeId === transportTestProxyId);
  const reported = Array.isArray(proxy?.backends) ? proxy.backends : [];
  const backendChoices = new Map(reported.map(backend => [backend.backendId, backend]));
  allNodeItems.filter(isBackend).forEach(node => {
    if (!backendChoices.has(node.nodeId)) backendChoices.set(node.nodeId,
      {backendId: node.nodeId, displayName: node.displayName});
  });
  const backends = [...backendChoices.values()];
  if (!backends.some(backend => backend.backendId === transportTestBackendId)) {
    transportTestBackendId = backends[0]?.backendId || '';
  }

  const proxyPlaceholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  proxyPlaceholder.value = '';
  transportTestProxy.replaceChildren(proxyPlaceholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  transportTestProxy.value = transportTestProxyId;

  const backendPlaceholder = text(document.createElement('option'), backends.length ? 'Choose a backend' : 'No backends reported');
  backendPlaceholder.value = '';
  transportTestBackend.replaceChildren(backendPlaceholder, ...backends.map(backend => {
    const option = text(document.createElement('option'), `${backend.displayName} · ${backend.backendId}`);
    option.value = backend.backendId;
    return option;
  }));
  transportTestBackend.value = transportTestBackendId;

  text(transportTestCapability, proxies.length > 0
    ? `${proxies.length} test-capable ${proxies.length === 1 ? 'proxy' : 'proxies'}` : 'Waiting for a capable proxy');
  transportTestCapability.className = `pill ${proxies.length > 0 ? 'online' : 'neutral'}`;
}

function proxyMethodCandidates() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    (node.acceptedCapabilities.includes('config.proxy-method.v1')
      || node.acceptedCapabilities.includes('config.proxy-method.v2')));
}

function proxyMethodCapabilityFor(method) {
  return method === 'HTTP' ? 'config.proxy-method.v2' : 'config.proxy-method.v1';
}

function proxyMethodReadCapability() {
  const capabilities = nodeCapabilities.get(proxyMethodProxyId) || [];
  // v2 can represent every method, including HTTP; v1 cannot. Prefer the
  // richer common contract and fall back to v1 for older mixed networks.
  for (const capability of ['config.proxy-method.v2', 'config.proxy-method.v1']) {
    const network = proxyMethodNetworkFor(allNodeItems, backendTopologyTruncatedNodeIds,
      proxyMethodProxyId, capability);
    if (network.proxyReady && network.topologyComplete && network.unavailable.length === 0) return capability;
  }
  // Retain a proxy-supported fallback so the disabled-state explanation can
  // identify the missing backend capability or incomplete topology.
  return capabilities.includes('config.proxy-method.v1')
    ? 'config.proxy-method.v1' : 'config.proxy-method.v2';
}

function proxyMethodReadNetwork() {
  return proxyMethodNetwork(proxyMethodReadCapability());
}

function proxyMethodNetworkFor(items, truncatedNodeIds, proxyId, capability = 'config.proxy-method.v1') {
  const index = new Map(items.map(node => [node.nodeId, node]));
  const proxy = index.get(proxyId);
  const proxyReady = Boolean(proxy?.online && proxy.acceptedCapabilities.includes(capability));
  const reported = Array.isArray(proxy?.backends) ? proxy.backends : [];
  const backends = reported.map(backend => index.get(backend.backendId)).filter(Boolean);
  const unavailable = reported.filter(backend => {
    const node = index.get(backend.backendId);
    return !node || !isBackend(node) || !node.online || !node.acceptedCapabilities.includes(capability);
  });
  return {proxy, proxyReady, reported, backends, unavailable, topologyComplete: !truncatedNodeIds.has(proxyId),
    nodeIds: proxy ? [proxy.nodeId, ...backends.map(node => node.nodeId)] : []};
}

function proxyMethodNetwork(capability = 'config.proxy-method.v1') {
  return proxyMethodNetworkFor(allNodeItems, backendTopologyTruncatedNodeIds, proxyMethodProxyId, capability);
}

function proxyMethodNetworkSignature(network) {
  return JSON.stringify({proxyReady: network.proxyReady, topologyComplete: network.topologyComplete, nodeIds: [...network.nodeIds].sort(),
    unavailable: network.unavailable.map(backend => backend.backendId).sort()});
}

function renderProxyMethod() {
  const proxies = proxyMethodCandidates();
  if (!proxies.some(node => node.nodeId === proxyMethodProxyId)) {
    proxyMethodProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
  }
  const placeholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  placeholder.value = '';
  proxyMethodProxy.replaceChildren(placeholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  proxyMethodProxy.value = proxyMethodProxyId;
  const network = proxyMethodReadNetwork();
  const readCapability = proxyMethodReadCapability();
  if (proxyMethodCurrentFor !== proxyMethodProxyId
      || proxyMethodCurrentSessionId !== (network.proxy?.sessionId || '')
      || proxyMethodCurrentReadCapability !== readCapability) {
    proxyMethodCurrentFor = '';
    proxyMethodCurrentSessionId = '';
    proxyMethodCurrentReadCapability = '';
    proxyMethodCurrentValue = '';
  }
  const ready = network.proxyReady && network.topologyComplete && network.reported.length > 0 &&
    network.nodeIds.length <= MAX_OPERATION_TARGETS && network.unavailable.length === 0;
  const description = !network.proxyReady ? 'Waiting for a connected, capable proxy'
    : !network.topologyComplete ? 'Backend topology is truncated; switching disabled'
    : network.nodeIds.length > MAX_OPERATION_TARGETS ? `Network exceeds the ${MAX_OPERATION_TARGETS}-node operation limit`
    : network.reported.length === 0 ? 'No backends reported'
    : network.unavailable.length > 0 ? `${network.unavailable.length} backends unavailable`
    : `${network.nodeIds.length} nodes ready`;
  text(proxyMethodCapability, description);
  proxyMethodCapability.className = `pill ${ready ? 'online' : 'neutral'}`;
  text(proxyMethodCurrent, proxyMethodCurrentValue ? `Active: ${proxyMethodCurrentValue}` : 'Active method unknown');
  proxyMethodCurrent.className = `pill ${proxyMethodCurrentValue ? 'online' : 'neutral'}`;
  proxyMethodButtons.forEach(button => {
    const active = button.dataset.proxyMethod === proxyMethodCurrentValue;
    const methodNetwork = proxyMethodNetwork(proxyMethodCapabilityFor(button.dataset.proxyMethod));
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
    button.disabled = !methodNetwork.proxyReady || !methodNetwork.topologyComplete || methodNetwork.reported.length === 0
      || methodNetwork.nodeIds.length > MAX_OPERATION_TARGETS || methodNetwork.unavailable.length > 0;
  });
}

function updateSetupChecklist(overview = lastOverview) {
  const node = nodeIndex.get(selectedServerId);
  const loggingRestartPending = voteLoggingRestartRequired();
  const steps = [...setupChecklist.querySelectorAll('li')];
  const states = [
    Boolean(node?.online && isBackend(node)),
    Boolean(overview && typeof overview.proxyMode === 'boolean'),
    Boolean(overview && Number.isSafeInteger(overview.configuredVoteSites) && overview.configuredVoteSites >= 0),
    Boolean(overview?.processRewards),
    Boolean(overview?.dataStorage && !loggingRestartPending
      && (!overview.voteLoggingEnabled || overview.voteLogReadable === true)),
    Boolean(overview && (!overview.proxyMode || allNodeItems.some(item => isProxy(item) && item.online
      && item.acceptedCapabilities.includes('config.transport-test.v1'))))
  ];
  steps.forEach((step, index) => {
    step.classList.toggle('complete', states[index]);
    text(step.querySelector('.step-state'), states[index] ? '✓' : String(index + 1));
  });
  const complete = states.filter(Boolean).length;
  const loggingStatus = loggingRestartPending
    ? ' Vote logging configuration was saved, but it is not considered live until this backend restarts and reconnects.'
    : overview?.voteLoggingEnabled === false
    ? ' Vote logging is optional and currently disabled.'
    : overview?.voteLoggingEnabled && overview.voteLogReadable !== true
    ? ' Vote logging is enabled but its MySQL table is not readable.' : '';
  text(setupChecklistStatus, `${complete} of ${states.length} readiness checks complete.${loggingStatus}`);
}

function updateExtendedButtons() {
  const node = nodeIndex.get(selectedServerId);
  const inspectionReady = authenticated && Boolean(inspectionCapableNode()) && !inspectionInFlight;
  const traceReady = authenticated && connectedInspectionNodes().length > 0 && !inspectionInFlight;
  const backendTargets = backendQuickTargets();
  const allQuickBackends = allNodeItems.filter(item => isBackend(item) && item.online
    && item.acceptedCapabilities.includes('config.quick-setup.v1')).slice(0, MAX_CONFIGURATION_TARGETS);
  const quickReady = authenticated && Boolean(node?.online && isBackend(node)
    && node.acceptedCapabilities.includes('config.quick-setup.v1')) && backendTargets.length > 0
    && configurationOperationsInFlight === 0;
  const fileTargets = comparisonTargetIds();
  const driftReady = authenticated && fileTargets.length >= 2 && configurationOperationsInFlight === 0;
  refreshDashboardButton.disabled = !authenticated || inspectionInFlight || dashboardLoading;
  runNetworkDoctor.disabled = !inspectionReady;
  downloadNetworkDiagnostics.disabled = !lastDiagnostics;
  refreshSetupChecklist.disabled = !inspectionReady;
  refreshDataOverview.disabled = !inspectionReady;
  lookupPlayer.disabled = !inspectionReady || configurationOperationsInFlight > 0;
  loadSiteHealth.disabled = !inspectionReady;
  loadVoteLogSummary.disabled = !inspectionReady;
  searchVoteLog.disabled = !inspectionReady;
  traceVote.disabled = !traceReady;
  resolveSite.disabled = !inspectionReady;
  simulateReward.disabled = !inspectionReady;
  previewReward.disabled = !quickReady;
  applyReward.disabled = !quickReady || !dedicatedSetupApprovals.get('reward-builder');
  loadAutoSites.disabled = !quickReady;
  previewAutoSites.disabled = !quickReady || autoSitesState.textContent === 'Not loaded';
  applyAutoSites.disabled = !quickReady || !dedicatedSetupApprovals.get('auto-create-vote-sites');
  selectAllAutoSitesTargets.disabled = !authenticated || allQuickBackends.length === 0
    || configurationOperationsInFlight > 0;
  text(autoSitesTargetCount, `${backendTargets.length} source ${backendTargets.length === 1 ? 'backend' : 'backends'} (not a bulk editor)`);
  loadVoteLogging.disabled = !quickReady;
  previewVoteLogging.disabled = !quickReady || voteLoggingState.textContent === 'Not loaded';
  applyVoteLogging.disabled = !quickReady || !dedicatedSetupApprovals.get('vote-logging');
  runDriftCheck.disabled = !driftReady;
  createSnapshot.disabled = !lastFileReadOperation;
  const inspectionMessage = inspectionReady ? 'Read-only inspection available' : 'Choose an inspection-capable node';
  text(networkDoctorCapability, inspectionMessage);
  networkDoctorCapability.className = `pill ${inspectionReady ? 'online' : 'neutral'}`;
  const rewardReady = inspectionReady && quickReady;
  text(rewardSimulationCapability, rewardReady ? 'Simulation and preview/apply available'
    : inspectionReady ? 'Simulation available; configuration write unavailable' : 'Choose a capable node');
  rewardSimulationCapability.className = `pill ${rewardReady ? 'online' : 'neutral'}`;
  text(driftCapability, driftReady ? `${fileTargets.length} selected nodes ready` : 'Select at least two readable nodes');
  driftCapability.className = `pill ${driftReady ? 'online' : 'neutral'}`;
  updateHeaderAction(tabFromHash());
  updateSetupChecklist();
}

function renderNodeViews() {
  renderServerPicker();
  renderHomeChooser();
  nodes.replaceChildren();
  nodes.classList.toggle('empty', visibleNodeItems.length === 0);
  if (visibleNodeItems.length === 0) {
    text(nodes, pageOffset === 0 ? 'No VotingPlugin nodes have registered yet.' : 'No nodes on this page.');
  } else {
    visibleNodeItems.forEach(node => nodes.append(nodeCard(node)));
  }
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  renderVoteSitesSync();
  renderTransportTest();
  renderProxyMethod();
  renderDeploymentEligibility();
  updateExtendedButtons();
  renderWorkspaceChrome(tabFromHash());
}

function deploymentTargets() {
  return allNodeItems.filter(node => node.online && node.acceptedCapabilities.includes('plugin.deploy.v1'));
}

function renderDeploymentEligibility() {
  const eligible = deploymentTargets();
  const connected = allNodeItems.filter(node => node.online);
  const batches = Math.ceil(eligible.length / MAX_OPERATION_TARGETS);
  text(deploymentEligibility, `${eligible.length}/${connected.length} connected nodes eligible`
    + (batches > 1 ? ` · ${batches} bounded deployment batches` : ''));
  deploymentEligibility.className = `pill ${eligible.length ? 'online' : 'neutral'}`;
  deployPlugin.disabled = !authenticated || logoutInFlight || deploymentInFlight || !deploymentJar.files?.length
    || eligible.length === 0 || batches > MAX_DEPLOYMENT_BATCHES;
}

function selectNodePage(offset) {
  pageOffset = Math.max(0, offset);
  visibleNodeItems = allNodeItems.slice(pageOffset, pageOffset + PAGE_SIZE);
  const first = visibleNodeItems.length === 0 ? 0 : pageOffset + 1;
  const last = pageOffset + visibleNodeItems.length;
  const pageMeta = nodePageMetadata.get(pageOffset);
  const backendLimit = pageMeta?.backendItemsTruncated
    ? ` Backend summaries are limited to ${pageMeta.backendItemsReturned} entries on this page.` : '';
  text(message, visibleNodeItems.length === 0 ? 'No nodes on this page.'
    : `Showing nodes ${first}–${last}.${backendLimit}`);
  text(pageNumber, `Page ${Math.floor(pageOffset / PAGE_SIZE) + 1}`);
  previousPage.disabled = pageOffset === 0;
  nextPage.disabled = pageOffset + visibleNodeItems.length >= allNodeItems.length;
}

function routingDraftStatus(status) {
  return routingDirty
    ? `${status} Your unsaved proxy-routing draft is retained for ${routingDraftNodeId || 'the previous server'}; explicitly switch servers or load current values to discard it.`
    : status;
}

function resetServerConfigurationForms(status, preserveDirtyDrafts = false) {
  const retainedRoutingDraft = preserveDirtyDrafts && routingDirty;
  if (!retainedRoutingDraft) {
    configurationForm.reset();
    routingDirty = false;
    routingDraftNodeId = '';
  }
  if (preserveDirtyDrafts && configurationDirty) {
    lastFileReadOperation = null;
    approvedFilePreview = null;
    text(fileOperationStatus, fileDraftStatus(status));
  } else {
    resetFileEditorForSelection(status);
  }
  text(operationStatus, routingDraftStatus(status));
  clearApprovals();
}

function resetDedicatedSetupValues() {
  dedicatedSetupDirty.clear();
  autoSitesEnabled.checked = false;
  voteLoggingEnabled.checked = false;
  voteLoggingDays.value = '30';
  voteLoggingMainMysql.checked = true;
  text(autoSitesState, 'Not loaded');
  text(voteLoggingState, 'Not loaded');
  autoSitesState.className = 'pill neutral';
  voteLoggingState.className = 'pill neutral';
  loadAutoSites.hidden = true;
  loadVoteLogging.hidden = true;
}

function invalidateGuidedSetupReads() {
  loadedQuickSetup = null;
  if (quickSetupDirty) {
    readQuickSetup.hidden = false;
    text(quickOperationStatus, 'Configuration changed elsewhere; your unsaved guided edits were preserved. Load current values to discard them.');
  }
  [['auto-create-vote-sites', autoSitesState, autoSitesStatus, loadAutoSites],
    ['vote-logging', voteLoggingState, voteLoggingStatus, loadVoteLogging]].forEach(([preset, state, status, retry]) => {
    text(state, 'Not loaded');
    state.className = 'pill neutral';
    retry.hidden = !dedicatedSetupDirty.has(preset);
    if (dedicatedSetupDirty.has(preset)) {
      text(status, 'Configuration changed elsewhere; your unsaved edits were preserved. Load current values to discard them.');
    }
  });
}

function resetServerContextValues(reason, preserveDirtyDrafts = false) {
  if (!preserveDirtyDrafts || !configurationDirty) clearSessionRewardFileOptions();
  dedicatedSetupApprovals.clear();
  pendingDetectedVoteSite = null;
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  dashboardOverview = null;
  dashboardVoteSiteHealth = null;
  dashboardVoteSummary24h = null;
  dashboardVoteSummary30d = null;
  dashboardLoadedContext = '';
  dashboardInspectionStatus = emptyDashboardInspectionStatus();
  dashboardTopologySignature = '';
  downloadNetworkDiagnostics.disabled = true;
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentReadCapability = '';
  proxyMethodCurrentValue = '';
  fileReadCache.clear();
  text(networkDoctorResults, reason);
  text(dataOverview, reason);
  text(playerResult, reason);
  text(siteHealthResult, reason);
  text(voteLogSummaryResult, reason);
  text(voteLogResult, reason);
  text(voteTraceResult, reason);
  text(siteResolutionResult, reason);
  text(rewardSimulationResult, reason);
  resetDedicatedSetupValues();
  text(autoSitesStatus, reason);
  text(voteLoggingStatus, reason);
  loadedQuickSetup = null;
  quickSetupDirty = false;
  readQuickSetup.hidden = true;
  resetServerConfigurationForms(reason, preserveDirtyDrafts);
  const preset = quickPreset.value;
  quickSetupForm.reset();
  quickPreset.value = preset;
  updateQuickFields();
  text(quickOperationStatus, reason);
  renderMetrics();
}

function selectPrimaryServer(nodeId) {
  if (nodeId && !nodeIndex.has(nodeId)) return false;
  if (nodeId && nodeId === selectedServerId) {
    serverPicker.value = selectedServerId;
    return true;
  }
  if (nodeId !== selectedServerId && !confirmDiscardUnsavedConfiguration('switching servers')) {
    serverPicker.value = selectedServerId;
    return false;
  }
  selectedServerId = nodeId;
  workspace.inspect(nodeId);
  serverPicker.value = nodeId;
  selectedNodes = new Set(nodeId ? [nodeId] : []);
  resetServerContextValues('Server changed. Load current values before continuing.');
  updatePluginSuggestions();
  renderNodeViews();
  void autoLoadTab(tabFromHash());
  return true;
}

function updateConfigurationButtons(busy = configurationOperationsInFlight > 0 || proxyMethodWorkflowInFlight) {
  const primaryCapabilities = nodeCapabilities.get(selectedServerId) || [];
  const routingReadReady = authenticated && primaryCapabilities.includes('config.proxy-routing.v1') &&
    targets('config.proxy-routing.v1').length > 0 && !busy;
  const routingDraftReady = routingReadReady && (!routingDirty || routingDraftNodeId === selectedServerId);
  const fileCapability = selectedFileCapability();
  const fileReady = authenticated && primaryCapabilities.includes(fileCapability) &&
    fileTargetsForSelection().length > 0 && !busy;
  const fileDraftReady = fileReady && fileDraftMatchesCurrentContext();
  const syncSelected = quickPreset.value === 'sync-vote-sites';
  const quickCapability = quickSetupCapability();
  const votePartyCapabilityMismatch = quickPreset.value === 'vote-party'
    && selectedVotePartyBackends().length > 0 && !votePartyCommonCapability();
  const proxyBackendCapabilityMismatch = quickPreset.value === 'proxy-backend'
    && selectedVotePartyBackends().length > 0 && !proxyBackendCommonCapability();
  const quickReady = authenticated && !busy && (syncSelected
    ? Boolean(voteSitesSourceId && selectedVoteSitesTargets().length > 0)
    : !proxyBackendCapabilityMismatch && primaryCapabilities.includes(quickCapability)
      && quickSetupTargets().length > 0);
  readConfiguration.disabled = !routingReadReady;
  previewConfiguration.disabled = !routingDraftReady;
  applyConfiguration.disabled = !routingDraftReady || !approvedPreview;
  readFileConfiguration.disabled = !fileReady;
  previewFileConfiguration.disabled = !fileDraftReady || !configurationContentPresent;
  applyFileConfiguration.disabled = !fileDraftReady || !approvedFilePreview;
  readQuickSetup.disabled = !quickReady || !quickPresetReadable();
  previewQuickSetup.disabled = !quickReady || (quickPresetNeedsRead() && !quickSetupValuesLoaded());
  applyQuickSetup.disabled = !quickReady || !approvedQuickPreview;
  if (votePartyCapabilityMismatch && !busy) {
    text(quickOperationStatus,
      'The selected backends do not share a Vote Party configuration capability. Update their VotingPlugin versions or select compatible backends.');
  }
  if (proxyBackendCapabilityMismatch && !busy) {
    text(quickOperationStatus,
      'Every selected backend must support this proxy method. Update incompatible VotingPlugin versions or select compatible backends.');
  }
  runTransportTest.disabled = !authenticated || !transportTestProxyId || !transportTestBackendId || busy;
  const methodNetwork = proxyMethodReadNetwork();
  proxyMethodButtons.forEach(button => {
    const network = proxyMethodNetwork(proxyMethodCapabilityFor(button.dataset.proxyMethod));
    button.disabled = !authenticated || !network.proxyReady || !network.topologyComplete || network.reported.length === 0
      || network.nodeIds.length > MAX_OPERATION_TARGETS || network.unavailable.length > 0 || busy;
  });
  readProxyMethod.disabled = !authenticated || !methodNetwork.proxyReady || busy;
}

function targets(capability) {
  return ordinaryTargetIds().filter(node => nodeCapabilities.get(node)?.includes(capability));
}

function backendQuickTargets() {
  return targets('config.quick-setup.v1').filter(nodeId => nodeIndex.has(nodeId) && isBackend(nodeIndex.get(nodeId)));
}

function quickSetupCapability() {
  return quickPreset.value === 'proxy-backend'
    ? proxyBackendCommonCapability() || 'config.proxy-method.unavailable' : quickPreset.value === 'vote-party'
    ? votePartyCommonCapability() || 'config.quick-setup.unavailable' : 'config.quick-setup.v1';
}

function proxyBackendCommonCapability() {
  const selectedBackends = selectedVotePartyBackends();
  if (!selectedBackends.length) return null;
  const required = quickMethod.value === 'HTTP' ? 'config.proxy-method.v2' : 'config.quick-setup.v1';
  return selectedBackends.every(nodeId => nodeCapabilities.get(nodeId)?.includes(required)) ? required : null;
}

function votePartyUsesV2() {
  return votePartyCommonCapability() === 'config.quick-setup.v2';
}

function selectedVotePartyBackends() {
  return ordinaryTargetIds().filter(nodeId => nodeIndex.has(nodeId) && isBackend(nodeIndex.get(nodeId)));
}

function votePartyCommonCapability() {
  const selectedBackends = selectedVotePartyBackends();
  if (!selectedBackends.length) return null;
  if (selectedBackends.every(nodeId => nodeCapabilities.get(nodeId)?.includes('config.quick-setup.v2'))) {
    return 'config.quick-setup.v2';
  }
  if (selectedBackends.every(nodeId => nodeCapabilities.get(nodeId)?.includes('config.quick-setup.v1'))) {
    return 'config.quick-setup.v1';
  }
  return null;
}

function reloadVotePartyWhenTargetCapabilityChanges(previousCapability, scheduleReload = true) {
  if (quickPreset.value !== 'vote-party' || previousCapability === quickSetupCapability()) return;
  loadedQuickSetup = null;
  approvedQuickPreview = null;
  if (quickSetupDirty) {
    // The capability-dependent Enabled field must be refreshed, but the
    // operator's unsaved common Vote Party edits remain authoritative locally.
    quickSetupPreserveReadGeneration = inputGeneration;
    text(quickOperationStatus,
      'Selected backend capabilities changed. Preserving unsaved Vote Party edits while loading confirmed state…');
  } else {
    quickSetupPreserveReadGeneration = -1;
    populateQuickState({});
    text(quickOperationStatus, 'Selected backend capabilities changed. Loading confirmed Vote Party settings…');
  }
  updateConfigurationButtons();
  // Funnel capability transitions through the tab's single-flight autoloader.
  // A rapid v2/v1/v2 change therefore marks one follow-up read instead of
  // starting overlapping READ operations that can race to populate the form.
  if (scheduleReload && tabFromHash() === 'quick-setup') void autoLoadTab('quick-setup');
}

function quickSetupTargets() {
  return targets(quickSetupCapability())
    .filter(nodeId => nodeIndex.has(nodeId) && isBackend(nodeIndex.get(nodeId)));
}

function clearApprovals() {
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  dedicatedSetupApprovals.clear();
  inputGeneration++;
  updateConfigurationButtons();
}

function updateEditorPosition() {
  const beforeCursor = configurationContent.value.slice(0, configurationContent.selectionStart);
  const lines = beforeCursor.split('\n');
  text(editorPosition, `Line ${lines.length}, Column ${lines.at(-1).length + 1}`);
}

function handleEditorKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    if (!previewFileConfiguration.disabled) previewFileConfiguration.click();
    return;
  }
  if (event.key !== 'Tab') return;
  event.preventDefault();
  const start = configurationContent.selectionStart;
  const end = configurationContent.selectionEnd;
  if (event.shiftKey) {
    const lineStart = configurationContent.value.lastIndexOf('\n', start - 1) + 1;
    const removable = configurationContent.value.slice(lineStart, lineStart + 2).match(/^ {1,2}/)?.[0].length || 0;
    if (removable > 0) {
      configurationContent.setRangeText('', lineStart, lineStart + removable, 'preserve');
      configurationContent.setSelectionRange(Math.max(lineStart, start - removable), Math.max(lineStart, end - removable));
    }
  } else {
    configurationContent.setRangeText('  ', start, end, 'end');
  }
  configurationContent.dispatchEvent(new Event('input', {bubbles: true}));
  updateEditorPosition();
}

function updateQuickFields() {
  document.querySelectorAll('.quick-fields').forEach(group => {
    group.hidden = !group.dataset.presets.split(' ').includes(quickPreset.value);
  });
  quickName.closest('.quick-fields').hidden = !['proxy-backend', 'vote-site', 'easy-reward'].includes(quickPreset.value);
  const sync = quickPreset.value === 'sync-vote-sites';
  readQuickSetup.hidden = true;
  previewQuickSetup.textContent = sync ? 'Read source and preview sync' : 'Preview changes';
  applyQuickSetup.textContent = sync ? 'Approve and sync' : 'Approve and apply';
  updateConfigurationButtons();
}

function quickPresetReadable() {
  return quickPresetNeedsRead()
    && (quickPreset.value !== 'vote-site' || quickName.value.trim().length > 0);
}

function quickPresetNeedsRead() {
  return ['proxy-backend', 'vote-site', 'common-settings', 'vote-party',
    'auto-create-vote-sites', 'vote-logging'].includes(quickPreset.value);
}

function quickSetupValuesLoaded() {
  return loadedQuickSetup?.nodeId === selectedServerId
    && loadedQuickSetup.sessionId === nodeIndex.get(selectedServerId)?.sessionId
    && loadedQuickSetup.preset === quickPreset.value
    && loadedQuickSetup.selector === JSON.stringify(quickReadConfigurationOptions());
}

function updatePluginSuggestions() {
  const plugins = new Set(targets('config.quick-setup.v1').flatMap(node => nodePlugins.get(node) || [])
    .map(name => name.toLowerCase()));
  const names = [...plugins].sort();
  text(detectedPlugins, names.length ? `Detected on selected nodes: ${names.join(', ')}`
    : 'No plugin inventory is available for the selected nodes; generic commands are still available.');
  const suggestions = [
    ['give %player% diamond 1', 'Minecraft item'],
    ['xp add %player% 5 levels', 'Minecraft experience']
  ];
  if ([...plugins].some(name => name === 'essentials' || name === 'essentialsx')) {
    suggestions.push(['eco give %player% 100', 'Essentials economy']);
  }
  if (plugins.has('cmi')) suggestions.push(['money give %player% 100', 'CMI economy']);
  if (plugins.has('luckperms')) {
    suggestions.push(['lp user %player% permission set example.permission true', 'LuckPerms permission']);
  }
  quickCommandSuggestions.replaceChildren(...suggestions.map(([value, label]) => {
    const option = document.createElement('option');
    option.value = value;
    option.label = label;
    return option;
  }));
}

async function authorized(path, options = {}) {
  if (logoutInFlight && path !== '/api/v1/auth/logout') {
    throw new Error('Authentication is changing. Try again after it finishes.');
  }
  const requestGeneration = authenticationGeneration;
  const method = (options.method || 'GET').toUpperCase();
  const response = await fetch(path, {
    cache: 'no-store',
    credentials: 'same-origin',
    ...options,
    headers: {...(options.headers || {}), ...(method === 'GET' ? {} : {'X-CSRF-Token': csrfToken})}
  });
  const body = response.status === 204 ? null : await response.json();
  if (response.status === 401 && requestGeneration === authenticationGeneration) {
    discardAuthenticationState('Session expired. Sign in again.');
  }
  if (requestGeneration !== authenticationGeneration) {
    throw new Error('Authentication changed while the request was running. Try again.');
  }
  if (!response.ok) {
    const error = new Error(body?.error?.message || `Control request failed (${response.status}).`);
    error.code = body?.error?.code || '';
    error.details = Array.isArray(body?.error?.details) ? body.error.details : [];
    throw error;
  }
  return body;
}

async function deploymentFileSha256(file) {
  if (!window.isSecureContext || !window.crypto?.subtle) {
    return null;
  }
  const digest = await window.crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
}

function deploymentSummary(operation) {
  const lines = [`Deployment ${operation.deploymentId} · ${operation.state}`];
  (operation.nodes || []).forEach(node => {
    const result = node.result;
    lines.push(result ? `${result.success ? '✓' : '✗'} ${node.nodeId}: ${configurationFailureLabel(result.code)} — ${result.message}`
      : `… ${node.nodeId}: ${String(node.state).toLowerCase()}`);
  });
  return lines.join('\n');
}

async function waitForDeployment(operation, generation) {
  text(deploymentStatus, deploymentSummary(operation));
  const deadline = Date.now() + 180_000;
  while (operation.state === 'RUNNING' || operation.state === 'QUEUED' || operation.state === 'PARTIAL') {
    if (Date.now() >= deadline) {
      throw new Error(`Deployment ${operation.deploymentId} is still pending. Its durable state remains available in Activity.`);
    }
    await new Promise(resolve => window.setTimeout(resolve, 1500));
    if (generation !== authenticationGeneration) throw new Error('Authentication changed while deployment was running.');
    operation = await authorized(`/api/v1/deployments/${operation.deploymentId}`);
    text(deploymentStatus, deploymentSummary(operation));
  }
  return operation;
}

function discardAuthenticationState(reason) {
  settingsEditor?.clear();
  voteSitesEditor?.clear();
  rewardsEditor?.clear();
  workspace.logout();
  registryAvailable = false;
  authenticationGeneration++;
  authenticated = false;
  csrfToken = '';
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  quickSetupDirty = false;
  inputGeneration++;
  logout.hidden = true;
  sidebarToggle.hidden = true;
  globalSearch.hidden = true;
  syncTopbarOffset();
  globalSearchInput.value = '';
  globalSearchOptions.replaceChildren();
  headerAction.hidden = true;
  closeSidebar();
  appShell.hidden = true;
  serverPickerLabel.hidden = true;
  welcome.hidden = false;
  authCard.hidden = setupRequired;
  enrollmentCard.hidden = true;
  enrollmentCredential.value = '';
  enrollmentList.replaceChildren(text(document.createElement('li'), 'Authenticate to manage enrollments.'));
  text(enrollmentMessage, '');
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentReadCapability = '';
  proxyMethodCurrentValue = '';
  fileReadCache.clear();
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  dashboardOverview = null;
  dashboardVoteSiteHealth = null;
  dashboardVoteSummary24h = null;
  dashboardVoteSummary30d = null;
  dashboardLoadedContext = '';
  dashboardInspectionStatus = emptyDashboardInspectionStatus();
  dashboardTopologySignature = '';
  operationHistoryItems = [];
  deploymentHistoryItems = [];
  deploymentJar.value = '';
  deploymentRunGeneration++;
  deploymentInFlight = false;
  text(deploymentStatus, '');
  observedServerConfigurationGeneration = null;
  operationHistoryStatus = 'not-loaded';
  enrollmentStatus = 'not-loaded';
  dedicatedSetupApprovals.clear();
  voteLoggingRestartPending.clear();
  pendingDetectedVoteSite = null;
  selectedServerId = '';
  visibleNodeItems = [];
  allNodeItems = [];
  enrollmentIds.clear();
  enrollmentsLoaded = false;
  backendTopologyTruncated = false;
  backendTopologyTruncatedNodeIds = new Set();
  nodeIndex.clear();
  nodeCapabilities.clear();
  nodePlugins.clear();
  configurationForm.reset();
  fileConfigurationForm.reset();
  clearSessionRewardFileOptions();
  document.querySelector('#vote-site-form').reset();
  document.querySelector('#vote-site-add-form').reset();
  const voteSiteAddDialog = document.querySelector('#vote-site-add-dialog');
  if (voteSiteAddDialog.open) voteSiteAddDialog.close();
  document.querySelector('#vote-site-search').value = '';
  document.querySelector('#vote-site-filter').value = 'all';
  document.querySelector('#vote-site-ack').checked = false;
  document.querySelector('#rewards-search').value = '';
  document.querySelector('#rewards-operation').value = 'APPEND_LIST_ENTRY';
  document.querySelector('#rewards-field').value = 'Commands';
  document.querySelector('#rewards-value').value = '';
  document.querySelector('#rewards-ack').checked = false;
  configurationFileSelection = configurationFile.value;
  configurationContentPresent = false;
  configurationDirty = false;
  configurationDraftNodeId = '';
  configurationDraftSessionId = '';
  configurationDraftFileName = '';
  routingDirty = false;
  routingDraftNodeId = '';
  autoLoadInFlight.clear();
  autoLoadPending.clear();
  quickSetupForm.reset();
  resetDedicatedSetupValues();
  rewardSimulationForm.reset();
  playerLookupForm.reset();
  voteLogForm.reset();
  voteTraceForm.reset();
  siteResolutionForm.reset();
  snapshotForm.reset();
  rewardSiteLabel.hidden = false;
  copyRewardToSetup.disabled = true;
  voteLogFilter.disabled = true;
  voteLogFilter.required = false;
  voteLogFilter.placeholder = '';
  updateQuickFields();
  quickCommandSuggestions.replaceChildren();
  text(detectedPlugins, 'Authenticate to inspect detected plugins.');
  text(operationStatus, '');
  text(fileOperationStatus, '');
  text(quickOperationStatus, '');
  text(transportTestStatus, '');
  text(proxyMethodStatus, '');
  text(networkDoctorResults, 'Choose a connected backend with read-only data inspection.');
  text(dataOverview, 'Choose an inspection-capable backend.');
  text(playerResult, 'No player queried.');
  text(siteHealthResult, 'No health query run.');
  text(voteLogSummaryResult, 'No vote-log summary loaded.');
  text(voteLogResult, 'No event search run.');
  text(voteTraceResult, 'No vote traced.');
  text(siteResolutionResult, 'No service tested.');
  text(rewardSimulationResult, 'Add an action, then simulate it safely.');
  text(driftResults, 'Authenticate and choose two or more readable nodes.');
  text(snapshotList, 'Authenticate to view manual snapshots.');
  text(snapshotStatus, '');
  renderOperationHistory();
  nodes.replaceChildren();
  nodes.classList.add('empty');
  text(nodes, 'Authenticate to view the network.');
  serverPicker.replaceChildren(text(document.createElement('option'), 'Choose a server'));
  serverPicker.firstElementChild.value = '';
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  text(message, reason);
  updateConfigurationButtons();
  updateExtendedButtons();
}

function proposal() {
  return {
    sendVotesToAllServers: sendAll.checked,
    blockedServers: blockedServers.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean)
  };
}

function rememberVoteLoggingRestart(operation) {
  if (operation.type !== 'APPLY' || operation.configuration?.preset !== 'vote-logging') return;
  Object.entries(operation.results || {}).forEach(([nodeId, result]) => {
    const runtimeChanged = Array.isArray(result?.changes) && result.changes.some(change =>
      /VoteLogging runtime restart required|VoteLogging\.(Enabled|UseMainMySQL)\b/.test(change));
    if (result?.success && runtimeChanged) voteLoggingRestartPending.set(nodeId,
      result.sessionId || nodeIndex.get(nodeId)?.sessionId || 'unknown');
  });
}

function voteLoggingRestartRequired(nodeId = selectedServerId) {
  const appliedSession = voteLoggingRestartPending.get(nodeId);
  if (!appliedSession) return false;
  const currentSession = nodeIndex.get(nodeId)?.sessionId;
  if (currentSession && appliedSession !== 'unknown' && currentSession !== appliedSession) {
    voteLoggingRestartPending.delete(nodeId);
    return false;
  }
  return true;
}

function configurationFailureLabel(code) {
  const labels = {
    TRANSPORT_FAILED: 'Transport failure', CAPABILITY_LOST: 'Unsupported capability',
    UNSUPPORTED_CAPABILITY: 'Unsupported capability', INVALID_CONFIGURATION: 'Invalid configuration',
    READ_FAILED: 'Configuration read failed', CONFIGURATION_MISSING: 'Configuration missing',
    CONFIGURATION_UNREADABLE: 'Configuration unreadable', CONFIGURATION_TOO_LARGE: 'Configuration too large',
    CONFIGURATION_INVALID_ENCODING: 'Invalid configuration encoding', CONFIGURATION_UNSAFE: 'Unsafe configuration path',
    WRITE_FAILED: 'Configuration write failed',
    RELOAD_FAILED: 'Configuration reload failed', RESTART_REQUIRED: 'Restart required',
    DEPENDENCY_FAILED: 'Dependency failure', DEPENDENCY_CHANGED: 'Dependency changed'
  };
  return labels[code] || code;
}

function operationSummary(operation) {
  const lines = [`${operation.type} · ${operation.state} · ${operation.operationId}`];
  const voteLoggingOperation = operation.configuration?.preset === 'vote-logging';
  const proxyFileOperation = operation.configuration?.domain === 'file'
    && operation.configuration?.fileName === 'bungeeconfig.yml';
  let voteLoggingRuntimeChange = false;
  let voteLoggingRestartWarning = false;
  Object.entries(operation.nodeStates).forEach(([node, state]) => {
    const result = operation.results[node];
    const runtimeChanged = Array.isArray(result?.changes) && result.changes.some(change =>
      /VoteLogging runtime restart required|VoteLogging\.(Enabled|UseMainMySQL)\b/.test(change));
    const retainedSession = voteLoggingRestartPending.get(node);
    const restartRequired = voteLoggingOperation && result?.success && operation.type === 'APPLY'
      && (runtimeChanged || retainedSession && (retainedSession === 'unknown' || retainedSession === result.sessionId));
    voteLoggingRuntimeChange ||= runtimeChanged;
    voteLoggingRestartWarning ||= Boolean(restartRequired);
    const successLabel = operation.type === 'READ' ? 'values read'
      : operation.type === 'PREVIEW' ? 'preview ready'
      : restartRequired ? 'configuration saved; backend restart required'
      : proxyFileOperation && result?.success && operation.type === 'APPLY'
        ? 'configuration saved; proxy restart required'
      : result?.reloaded ? 'saved and reloaded' : 'applied';
    lines.push(`${result?.success ? '✓' : result ? '✗' : '…'} ${node}: ${result
      ? `${result.success ? successLabel : configurationFailureLabel(result.code)} — ${result.message}` : state.toLowerCase()}`);
    if (result?.changes?.length) result.changes.forEach(change => lines.push(`  ${change}`));
    if (result?.rolledBack) lines.push('  NOT SAVED — the previous file was restored because reload failed');
  });
  if (operation.configuration?.preset === 'sync-vote-sites') {
    const sites = new Set(Object.values(operation.results).flatMap(result => result.changes || [])
      .map(change => change.match(/VoteSites\.([A-Za-z0-9_-]+)/)?.[1]).filter(Boolean));
    lines.push(`${sites.size || 'No'} site ${sites.size === 1 ? 'definition' : 'definitions'} ${operation.type === 'PREVIEW' ? 'would change' : 'changed'}.`);
    lines.push('Rewards and target-only sites remain local to each backend.');
  }
  if (voteLoggingOperation && (operation.type === 'PREVIEW' && voteLoggingRuntimeChange
      || operation.type === 'APPLY' && voteLoggingRestartWarning)) {
    lines.push(operation.type === 'PREVIEW'
      ? 'Applying this preview requires restarting each changed backend; a plugin reload does not activate a new vote-log connection.'
      : 'Restart every successfully changed backend before treating the vote-logging runtime as live.');
  }
  if (proxyFileOperation && operation.type === 'APPLY' && operation.state === 'SUCCEEDED') {
    lines.push('Restart the proxy before treating the saved proxy configuration as active.');
  }
  return lines.join('\n');
}

function presentPreviewReady(statusElement, applyButton, operation) {
  const action = applyButton?.textContent?.trim() || 'Approve and apply';
  text(statusElement, `${operationSummary(operation)}\nPREVIEW ONLY — nothing has been saved yet. Click “${action}” to write these exact changes.`);
  if (applyButton && !applyButton.disabled) applyButton.focus({preventScroll: true});
}

function operationContext() {
  return {authenticationGeneration, inputGeneration, selectedServerId,
    selectedSessionId: nodeIndex.get(selectedServerId)?.sessionId};
}

function operationContextCurrent(context) {
  return context.authenticationGeneration === authenticationGeneration && context.inputGeneration === inputGeneration
    && context.selectedServerId === selectedServerId
    && context.selectedSessionId === nodeIndex.get(context.selectedServerId)?.sessionId;
}

function invalidateConfigurationReads() {
  settingsEditor?.invalidateReads();
  voteSitesEditor?.invalidateReads();
  if (authenticated && tabFromHash() === 'general-settings' && !settingsEditor?.state.busy) {
    window.setTimeout(() => void settingsEditor?.read(false), 0);
  }
  if (authenticated && tabFromHash() === 'vote-sites' && !voteSitesEditor?.state.busy) {
    window.setTimeout(() => void voteSitesEditor?.read(false), 0);
  }
  rewardsEditor?.invalidateReads();
  if (authenticated && tabFromHash() === 'rewards' && !rewardsEditor?.state.busy) {
    window.setTimeout(() => void rewardsEditor?.read(false), 0);
  }
  fileReadCache.clear();
  lastFileReadOperation = null;
  clearApprovals();
  loadedQuickSetup = null;
  if (!configurationDirty) {
    configurationContent.value = '';
    configurationContentPresent = false;
    text(fileOperationStatus, 'Configuration changed; read the current file before previewing changes.');
    if (tabFromHash() === 'configurations') window.setTimeout(() => void autoLoadTab('configurations'), 0);
  }
  lastOverview = null;
  lastDiagnostics = null;
  dashboardConfigurationGeneration++;
  invalidateDashboardInspection();
  updateExtendedButtons();
}

async function waitForOperation(operation, statusElement = operationStatus, context = operationContext()) {
  if (operationContextCurrent(context)) text(statusElement, operationSummary(operation));
  rememberOperation(operation);
  while (operation.state === 'RUNNING') {
    await new Promise(resolve => window.setTimeout(resolve, 1500));
    operation = await authorized(`/api/v1/operations/${operation.operationId}`);
    if (operationContextCurrent(context)) text(statusElement, operationSummary(operation));
    rememberOperation(operation);
  }
  if (!operationContextCurrent(context) && statusElement === operationStatus
      && operation.type === 'APPLY' && routingDirty) {
    text(statusElement, `${operationSummary(operation)}\nThe apply completed, but newer unsaved proxy-routing edits remain. Preview again before applying them.`);
  }
  rememberVoteLoggingRestart(operation);
  const applied = operation.type === 'APPLY'
    && Object.values(operation.results || {}).some(result => result?.success);
  if (applied) {
    invalidateConfigurationReads();
    invalidateGuidedSetupReads();
    if (tabFromHash() === 'quick-setup') window.setTimeout(() => void autoLoadTab('quick-setup'), 0);
    if (tabFromHash() === 'overview') {
      text(dataOverview, 'Configuration changed; refreshing server overview…');
      window.setTimeout(() => void autoLoadTab('overview'), 0);
    }
  }
  return operation;
}

async function startConfigurationOperation(path, body, statusElement = operationStatus) {
  // Legacy forms remain source-only; coordinated workflows keep explicit targets.
  if (path.endsWith('/preview') && path !== '/api/v1/configuration/general-settings/preview'
      && path !== '/api/v1/configuration/vote-sites/preview'
      && path !== '/api/v1/configuration/rewards/preview'
      && !['proxy-method', 'sync-vote-sites', 'communication-test'].includes(body.configuration?.preset)) {
    const allowed = ordinaryTargetIds();
    if (!Array.isArray(body.nodeIds) || body.nodeIds.length !== 1 || !allowed.includes(body.nodeIds[0])) {
      throw new Error('Choose a workspace source server. This form previews only that server, not the whole workspace.');
    }
  }
  const applyOperation = path.endsWith('/apply');
  if (applyOperation) {
    approvedPreview = null;
    approvedFilePreview = null;
    approvedQuickPreview = null;
    dedicatedSetupApprovals.clear();
    inputGeneration++;
  }
  const context = operationContext();
  configurationOperationsInFlight++;
  updateConfigurationButtons();
  updateExtendedButtons();
  try {
    return await waitForOperation(await authorized(path, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
    }), statusElement, context);
  } finally {
    configurationOperationsInFlight--;
    updateConfigurationButtons();
    updateExtendedButtons();
    if (configurationOperationsInFlight === 0 && autoLoadPending.has('quick-setup')
        && !autoLoadInFlight.has('quick-setup')) {
      autoLoadPending.delete('quick-setup');
      void autoLoadTab('quick-setup');
    }
  }
}

async function loadEnrollments() {
  if (!authenticated) return;
  if (enrollmentInFlight) {
    enrollmentRefreshRequested = true;
    if (!enrollmentRefreshPromise) {
      enrollmentRefreshPromise = new Promise(resolve => { enrollmentRefreshResolve = resolve; });
    }
    return enrollmentRefreshPromise;
  }
  enrollmentInFlight = true;
  const enrollmentGeneration = authenticationGeneration;
  enrollmentStatus = 'loading';
  refreshEnrollments.disabled = true;
  try {
    const body = await authorized('/api/v1/enrollments');
    if (!authenticated || enrollmentGeneration !== authenticationGeneration) return;
    enrollmentIds = new Set(Array.isArray(body.nodeIds) ? body.nodeIds : []);
    enrollmentsLoaded = true;
    enrollmentStatus = 'available';
    if (allNodeItems.length) renderNodeViews();
    enrollmentList.replaceChildren();
    if (!Array.isArray(body.nodeIds) || body.nodeIds.length === 0) {
      enrollmentList.append(text(document.createElement('li'), 'No nodes are enrolled yet.'));
      return;
    }
    body.nodeIds.forEach(nodeId => {
      const item = document.createElement('li');
      item.className = 'enrollment-item';
      item.append(text(document.createElement('code'), nodeId));
      const revoke = text(document.createElement('button'), 'Revoke');
      revoke.type = 'button';
      revoke.addEventListener('click', async () => {
        if (enrollmentMutationInFlight || !window.confirm(`Revoke the credential for ${nodeId}?`)) return;
        enrollmentMutationInFlight = true;
        revoke.disabled = true;
        enrollmentSubmit.disabled = true;
        try {
          await authorized(`/api/v1/enrollments/${encodeURIComponent(nodeId)}`, {method: 'DELETE'});
          enrollmentCredential.value = '';
          text(enrollmentMessage, `${nodeId} was revoked.`);
          await loadEnrollments();
        } catch (error) {
          text(enrollmentMessage, error.message);
        } finally {
          enrollmentMutationInFlight = false;
          enrollmentSubmit.disabled = false;
        }
      });
      item.append(revoke);
      enrollmentList.append(item);
    });
  } catch (error) {
    if (!authenticated || enrollmentGeneration !== authenticationGeneration) return;
    enrollmentsLoaded = false;
    enrollmentStatus = 'failed';
    if (allNodeItems.length) renderNodeViews();
    text(enrollmentMessage, error.message || 'Enrollments could not be loaded.');
  } finally {
    enrollmentInFlight = false;
    refreshEnrollments.disabled = false;
    const refreshAgain = enrollmentRefreshRequested && authenticated;
    enrollmentRefreshRequested = false;
    if (refreshAgain) await loadEnrollments();
    const resolveRefresh = enrollmentRefreshResolve;
    enrollmentRefreshPromise = null;
    enrollmentRefreshResolve = null;
    if (resolveRefresh) resolveRefresh();
  }
}

async function loadAllNodes() {
  for (let attempt = 0; attempt < MAX_REGISTRY_SCAN_ATTEMPTS; attempt++) {
    const items = [];
    const pageMetadata = new Map();
    const ids = new Set();
    const truncatedNodeIds = new Set();
    let revision = null;
    let total = null;
    let truncated = false;
    try {
      for (let offset = 0; ; offset += PAGE_SIZE) {
        const expected = revision == null ? '' : `&revision=${revision}`;
        const page = await authorized(`/api/v1/nodes?offset=${offset}&limit=${PAGE_SIZE}${expected}`);
        if (revision == null) {
          revision = page.registryRevision;
          total = page.total;
        } else if (page.registryRevision !== revision || page.total !== total) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
        if (page.items.some(node => ids.has(node.nodeId))) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
        page.items.forEach(node => ids.add(node.nodeId));
        items.push(...page.items);
        truncated ||= Boolean(page.backendItemsTruncated);
        (page.backendItemsTruncatedNodeIds || []).forEach(nodeId => truncatedNodeIds.add(nodeId));
        pageMetadata.set(offset, {
          backendItemsReturned: page.backendItemsReturned,
          backendItemsTruncated: Boolean(page.backendItemsTruncated)
        });
        if (items.length === total) return {items, truncated, truncatedNodeIds, pageMetadata};
        if (page.items.length === 0 || items.length > total) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
      }
    } catch (error) {
      if (error.code !== 'REGISTRY_CHANGED' || attempt + 1 === MAX_REGISTRY_SCAN_ATTEMPTS) throw error;
    }
  }
  throw new Error('Node registry could not be loaded consistently. Try refreshing again.');
}

async function loadNodesOnce() {
  if (!authenticated) return;
  refresh.disabled = true;
  previousPage.disabled = true;
  nextPage.disabled = true;
  text(message, 'Loading…');
  try {
    const registry = await loadAllNodes();
    const previousQuickCapability = quickSetupCapability();
    const previousNodeIndex = nodeIndex;
    allNodeItems = registry.items;
    nodePageMetadata = registry.pageMetadata;
    selectNodePage(pageOffset);
    dashboardTopologySignature = topologySignature(registry.items);
    backendTopologyTruncated = registry.truncated;
    backendTopologyTruncatedNodeIds = registry.truncatedNodeIds;
    nodeIndex = new Map(registry.items.map(node => [node.nodeId, node]));
    registryAvailable = true;
    const previousWorkspaceTargets = [...workspace.selectedTargetIds].join('\u0000');
    const previousInspectedServerId = workspace.inspectedServerId;
    // The node registry is complete here; truncated refers only to proxy topology.
    workspace.reconcile(registry.items);
    const workspaceTargetsChanged = previousWorkspaceTargets !== [...workspace.selectedTargetIds].join('\u0000');
    const primarySessionChanged = selectedServerId && previousNodeIndex.get(selectedServerId)?.sessionId
      && previousNodeIndex.get(selectedServerId)?.sessionId !== nodeIndex.get(selectedServerId)?.sessionId;
    const selectedSessionChanged = primarySessionChanged || [...selectedNodes].some(node => previousNodeIndex.get(node)?.sessionId
      && previousNodeIndex.get(node)?.sessionId !== nodeIndex.get(node)?.sessionId);
    if (selectedSessionChanged) {
      resetServerContextValues('A selected server reconnected. Load current values before continuing.', true);
    }
    const previousCapabilities = nodeCapabilities;
    nodeCapabilities = new Map(registry.items.map(node => [node.nodeId, node.online ? node.acceptedCapabilities : []]));
    nodePlugins = new Map(registry.items.map(node => [node.nodeId, node.online && Array.isArray(node.detectedPlugins)
      ? node.detectedPlugins : []]));
    const selectedCapabilitiesChanged = [...selectedNodes].some(node =>
      ['config.proxy-routing.v1', 'config.files.v1', 'config.proxy-files.v1', 'config.quick-setup.v1',
        'config.quick-setup.v2', 'config.proxy-method.v2', 'data.inspect.v1'].some(capability =>
        Boolean(previousCapabilities.get(node)?.includes(capability)) !==
          Boolean(nodeCapabilities.get(node)?.includes(capability))));
    if (selectedCapabilitiesChanged) {
      invalidateGuidedSetupReads();
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      lastOverview = null;
      lastDiagnostics = null;
      dashboardOverview = null;
      dashboardVoteSiteHealth = null;
      dashboardVoteSummary24h = null;
      dashboardVoteSummary30d = null;
      dashboardLoadedContext = '';
      dashboardInspectionStatus = emptyDashboardInspectionStatus();
      inputGeneration++;
      text(operationStatus, routingDraftStatus('A selected node changed capabilities during refresh. Preview again before apply.'));
    }
    const invalidRoutingApproval = approvedPreview && !approvedPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.proxy-routing.v1'));
    const invalidFileApproval = approvedFilePreview && !approvedFilePreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes(selectedFileCapability()));
    const invalidQuickApproval = approvedQuickPreview && approvedQuickPreview.workflow !== 'sync-vote-sites' &&
      !approvedQuickPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes(quickSetupCapability()));
    const invalidVoteSitesApproval = approvedQuickPreview?.workflow === 'sync-vote-sites' &&
      (!approvedQuickPreview.nodeIds.every(node =>
        nodeCapabilities.get(node)?.includes('config.vote-sites-sync.v1')) ||
       !nodeCapabilities.get(approvedQuickPreview.sourceId)?.includes('config.file-comments.v1'));
    if (invalidRoutingApproval || invalidFileApproval || invalidQuickApproval || invalidVoteSitesApproval) {
      if (invalidRoutingApproval) approvedPreview = null;
      if (invalidFileApproval) approvedFilePreview = null;
      if (invalidQuickApproval) approvedQuickPreview = null;
      if (invalidVoteSitesApproval) approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      inputGeneration++;
      text(operationStatus, routingDraftStatus('A preview target went offline or lost the required capability. Preview again before apply.'));
    }
    const visibleIds = new Set(registry.items.filter(node => node.online && node.acceptedCapabilities.some(value => value.startsWith('config.') || value.startsWith('data.')))
      .map(node => node.nodeId));
    const filteredSelection = new Set([...selectedNodes].filter(node => visibleIds.has(node)));
    renderServerPicker();
    if (selectedServerId && visibleIds.has(selectedServerId)) {
      while (!filteredSelection.has(selectedServerId) && filteredSelection.size >= MAX_CONFIGURATION_TARGETS) {
        const removable = [...filteredSelection].find(node => node !== selectedServerId);
        if (!removable) break;
        filteredSelection.delete(removable);
      }
      filteredSelection.add(selectedServerId);
    }
    if (filteredSelection.size !== selectedNodes.size ||
        [...filteredSelection].some(node => !selectedNodes.has(node))) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      inputGeneration++;
      text(operationStatus, routingDraftStatus('The selected nodes changed during refresh. Preview again before apply.'));
    }
    if (workspaceTargetsChanged) clearApprovals();
    selectedNodes = filteredSelection;
    // A registry refresh can change the effective Vote Party contract without a
    // user selection event. Clear the old v2/v1 form before the normal tab
    // auto-load runs so a delayed or failed READ cannot expose stale values.
    reloadVotePartyWhenTargetCapabilityChanges(previousQuickCapability, false);
    renderNodeViews();
    normalizeWorkspaceRouteAfterNodeRefresh(previousWorkspaceTargets, previousInspectedServerId);
    updatePluginSuggestions();
    updateConfigurationButtons();
    if (suppressNodeAutoLoad === 0) void autoLoadTab(tabFromHash());
  } catch (error) {
    registryAvailable = false;
    visibleNodeItems = [];
    allNodeItems = [];
    nodePageMetadata = new Map();
    backendTopologyTruncated = false;
    nodeIndex.clear();
    nodeCapabilities.clear();
    nodePlugins.clear();
    selectedNodes.clear();
    selectedServerId = '';
    resetServerContextValues('Network data is unavailable. Refresh and load current values before continuing.', true);
    renderServerPicker();
    renderNodeViews();
    updatePluginSuggestions();
    updateConfigurationButtons();
    text(nodes, 'Network data is unavailable.');
    text(message, error.message || 'Control request failed.');
  } finally {
    refresh.disabled = false;
  }
}

async function loadNodes() {
  if (nodeLoadInFlight) {
    nodeLoadQueued = true;
    if (!nodeLoadQueuedPromise) {
      nodeLoadQueuedPromise = new Promise((resolve, reject) => {
        nodeLoadQueuedResolve = resolve;
        nodeLoadQueuedReject = reject;
      });
    }
    return nodeLoadQueuedPromise;
  }
  const load = loadNodesOnce();
  nodeLoadInFlight = load;
  updateHeaderAction(tabFromHash());
  try {
    return await load;
  } finally {
    if (nodeLoadInFlight === load) nodeLoadInFlight = null;
    updateHeaderAction(tabFromHash());
    if (nodeLoadQueued) {
      nodeLoadQueued = false;
      const resolveQueued = nodeLoadQueuedResolve;
      const rejectQueued = nodeLoadQueuedReject;
      nodeLoadQueuedPromise = null;
      nodeLoadQueuedResolve = null;
      nodeLoadQueuedReject = null;
      loadNodes().then(resolveQueued, rejectQueued);
    }
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  loginInFlight = true;
  loginButton.disabled = true;
  logout.disabled = true;
  const loginGeneration = ++authenticationGeneration;
  const password = passwordInput.value;
  passwordInput.value = '';
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify({password})
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'Authentication failed.');
    if (loginGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
  } catch (error) {
    text(message, error.message || 'Authentication failed.');
  } finally {
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
  }
});

logout.addEventListener('click', async () => {
  if (loginInFlight) return;
  loginInFlight = true;
  logoutInFlight = true;
  const logoutGeneration = ++authenticationGeneration;
  loginButton.disabled = true;
  logout.disabled = true;
  renderDeploymentEligibility();
  try {
    await authorized('/api/v1/auth/logout', {method: 'POST'});
    if (logoutGeneration === authenticationGeneration) discardAuthenticationState('Signed out.');
  } catch (_) {
    if (logoutGeneration === authenticationGeneration) {
      text(message, 'Sign out could not be confirmed. Check your connection and try again.');
    }
  } finally {
    logoutInFlight = false;
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
    renderDeploymentEligibility();
  }
});

async function restoreSession() {
  const restoreGeneration = authenticationGeneration;
  let savedWorkspace = null;
  try { savedWorkspace = window.sessionStorage.getItem(ControlWorkspace.SESSION_KEY); } catch (_) { /* unavailable */ }
  try {
    const response = await fetch('/api/v1/auth/session', {cache: 'no-store', credentials: 'same-origin'});
    if (!response.ok || response.status === 204) return;
    const body = await response.json();
    if (restoreGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
    if (restoreGeneration === authenticationGeneration && savedWorkspace) {
      try { window.sessionStorage.setItem(ControlWorkspace.SESSION_KEY, savedWorkspace); } catch (_) { /* unavailable */ }
      workspace.restore(allNodeItems);
      window.history.replaceState(null, '', workspace.currentRoute);
      applyNavigationRoute();
    }
  } catch (_) { /* The login form remains available. */ }
}

setupForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  if (setupPassword.value !== setupConfirmPassword.value) {
    text(setupMessage, 'The new passwords do not match.');
    return;
  }
  loginInFlight = true;
  const setupGeneration = ++authenticationGeneration;
  const request = {setupCode: setupCode.value, password: setupPassword.value};
  setupCode.value = '';
  setupPassword.value = '';
  setupConfirmPassword.value = '';
  try {
    const response = await fetch('/api/v1/auth/setup', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'First-run setup failed.');
    if (setupGeneration !== authenticationGeneration) return;
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    applyAuthenticatedSession(body);
    text(message, 'First-run setup completed.');
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
  } catch (error) {
    text(setupMessage, error.message || 'First-run setup failed.');
  } finally {
    loginInFlight = false;
  }
});

enrollmentForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (!authenticated || enrollmentMutationInFlight) return;
  enrollmentMutationInFlight = true;
  enrollmentSubmit.disabled = true;
  const nodeId = enrollmentNodeId.value.trim();
  enrollmentCredential.value = '';
  try {
    const body = await authorized('/api/v1/enrollments', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({nodeId})
    });
    enrollmentCredential.value = body.credential;
    text(enrollmentMessage,
      `Credential created for ${body.nodeId}. Copy it now; rotating again immediately invalidates this value.`);
    await loadEnrollments();
  } catch (error) {
    text(enrollmentMessage, error.message || 'The credential could not be created.');
  } finally {
    enrollmentMutationInFlight = false;
    enrollmentSubmit.disabled = false;
  }
});
refreshEnrollments.addEventListener('click', loadEnrollments);

async function loadProxyRouting(automatic = false) {
  if (!automatic && routingDirty && !window.confirm('Discard unsaved routing changes and load current values?')) return;
  approvedPreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  const readNodeId = selectedServerId;
  const readSessionId = nodeIndex.get(readNodeId)?.sessionId;
  if (!nodeCapabilities.get(readNodeId)?.includes('config.proxy-routing.v1')) return;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {nodeIds: [readNodeId]});
    const retained = operation.results?.[readNodeId];
    if (retained && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration && readNodeId === selectedServerId
        && readSessionId === nodeIndex.get(readNodeId)?.sessionId && retained.sessionId === readSessionId
        && retained.success && retained.configuration) {
      sendAll.checked = retained.configuration.sendVotesToAllServers;
      blockedServers.value = retained.configuration.blockedServers.join('\n');
      approvedPreview = null;
      routingDirty = false;
      routingDraftNodeId = '';
      inputGeneration++;
      updateConfigurationButtons();
    }
  } catch (error) {
    if (!automatic) text(operationStatus, error.message);
  }
}

readConfiguration.addEventListener('click', () => { void loadProxyRouting(false); });

previewConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.proxy-routing.v1'), configuration: proposal()
    });
    if (operation.state === 'SUCCEEDED' && operation.approvalToken
        && previewGeneration === inputGeneration) {
      approvedPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.proxy-routing.v1')};
      updateConfigurationButtons();
      presentPreviewReady(operationStatus, applyConfiguration, operation);
    } else if (previewGeneration !== inputGeneration) {
      text(operationStatus, 'The targets or proposal changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(operationStatus, error.message); }
});

applyConfiguration.addEventListener('click', async () => {
  if (!approvedPreview || !window.confirm('Apply this exact preview to every selected proxy? Each node may still reject a stale revision.')) return;
  const approval = approvedPreview;
  approvedPreview = null;
  const submittedProposal = JSON.stringify({proposal: proposal(), nodeIds: approval.nodeIds});
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    });
    const currentProposal = JSON.stringify({proposal: proposal(), nodeIds: targets('config.proxy-routing.v1')});
    if (operation.state === 'SUCCEEDED' && submittedProposal === currentProposal) {
      routingDirty = false;
      routingDraftNodeId = '';
      await loadProxyRouting(true);
    }
  } catch (error) { text(operationStatus, error.message); }
});
[sendAll, blockedServers].forEach(field => field.addEventListener('input', () => {
  if (!routingDirty) routingDraftNodeId = selectedServerId;
  routingDirty = true;
  if (approvedPreview) text(operationStatus, 'The proposal changed. Preview it again before apply.');
  approvedPreview = null;
  inputGeneration++;
  updateConfigurationButtons();
}));

async function loadFileConfiguration(automatic = false) {
  if (!automatic && configurationDirty
      && !window.confirm(`Discard the unsaved ${configurationFile.value} draft and read/reload the current file for this server?`)) return;
  approvedFilePreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  const selectedFile = configurationFile.value;
  const selectedNode = nodeIndex.get(selectedServerId);
  const selectedReadNodeId = selectedNode?.online && selectedNode.acceptedCapabilities.includes(selectedFileCapability(selectedFile))
    && (selectedFile === 'bungeeconfig.yml' ? isProxy(selectedNode) : isBackend(selectedNode)) ? selectedServerId : '';
  if (!selectedReadNodeId) {
    text(fileOperationStatus, 'Choose a connected node that supports this configuration file.');
    return;
  }
  const cacheKey = `${selectedServerId}|${selectedNode?.sessionId || ''}|${selectedFile}`;
  const cached = cachedFile(cacheKey);
  if (cached) {
    configurationContent.value = cached.content;
    configurationContentPresent = true;
    configurationDirty = false;
    configurationDraftNodeId = '';
    configurationDraftSessionId = '';
    configurationDraftFileName = '';
    lastFileReadOperation = {operationId: cached.operationId};
    updateEditorPosition();
    text(fileOperationStatus, `Cached read · ${selectedServerId} · ${selectedFile}\nLoaded instantly; cache expires after 30 seconds. Preview still checks the live revision.`);
    inputGeneration++;
    updateConfigurationButtons();
    updateExtendedButtons();
    readFileConfiguration.hidden = true;
    return;
  }
  configurationContent.value = '';
  configurationContentPresent = false;
  configurationContent.disabled = true;
  configurationContent.setAttribute('aria-busy', 'true');
  readFileConfiguration.hidden = true;
  text(fileOperationStatus, `Loading ${selectedFile} from ${selectedReadNodeId}…`);
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedReadNodeId],
      configuration: {domain: 'file', fileName: selectedFile}
    }, fileOperationStatus);
    const contentResult = operation.results?.[selectedReadNodeId];
    if (contentResult && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration && selectedFile === configurationFile.value
        && selectedReadNodeId === selectedServerId && selectedNode.sessionId === nodeIndex.get(selectedReadNodeId)?.sessionId
        && contentResult.success && typeof contentResult.configuration?.content === 'string') {
      configurationContent.value = contentResult.configuration.content;
      configurationContentPresent = true;
      configurationDirty = false;
      configurationDraftNodeId = '';
      configurationDraftSessionId = '';
      configurationDraftFileName = '';
      lastFileReadOperation = {operationId: operation.operationId};
      cacheFile(cacheKey, contentResult.configuration.content, operation.operationId);
      updateEditorPosition();
      text(fileOperationStatus, operationSummary(operation));
      inputGeneration++;
      updateConfigurationButtons();
      updateExtendedButtons();
      readFileConfiguration.hidden = true;
    } else if (readAuthenticationGeneration === authenticationGeneration && readInputGeneration === inputGeneration
        && selectedFile === configurationFile.value && selectedReadNodeId === selectedServerId) {
      throw new Error(contentResult?.message || `The ${selectedFile} read did not return editable content.`);
    }
  } catch (error) {
    if (authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration && selectedFile === configurationFile.value
        && selectedReadNodeId === selectedServerId
        && selectedNode.sessionId === nodeIndex.get(selectedReadNodeId)?.sessionId) {
      configurationContent.value = '';
      configurationContentPresent = false;
      text(fileOperationStatus, `Could not load ${selectedFile}: ${error.message}`);
      readFileConfiguration.hidden = false;
    }
  } finally {
    if (authenticated && readAuthenticationGeneration === authenticationGeneration
        && selectedFile === configurationFile.value && selectedReadNodeId === selectedServerId
        && selectedNode.sessionId === nodeIndex.get(selectedReadNodeId)?.sessionId) {
      configurationContent.disabled = false;
      configurationContent.removeAttribute('aria-busy');
      updateConfigurationButtons();
    }
  }
}

readFileConfiguration.addEventListener('click', () => { void loadFileConfiguration(false); });

previewFileConfiguration.addEventListener('click', async () => {
  approvedFilePreview = null;
  if (!fileDraftMatchesCurrentContext()) {
    text(fileOperationStatus, fileDraftStatus('This draft belongs to a different node session and cannot be previewed here.'));
    updateConfigurationButtons();
    return;
  }
  const previewGeneration = inputGeneration;
  const selectedFile = configurationFile.value;
  const previewTargets = fileTargetsForSelection(selectedFile);
  const previewSessions = new Map(previewTargets.map(nodeId => [nodeId, nodeIndex.get(nodeId)?.sessionId]));
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: previewTargets,
      configuration: {domain: 'file', fileName: selectedFile, content: configurationContent.value}
    }, fileOperationStatus);
    const targetsCurrent = selectedFile === configurationFile.value && previewTargets.length > 0
      && previewTargets.every(nodeId => previewSessions.get(nodeId) === nodeIndex.get(nodeId)?.sessionId)
      && previewTargets.every(nodeId => fileTargetsForSelection(selectedFile).includes(nodeId));
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration && targetsCurrent) {
      approvedFilePreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: previewTargets, fileName: selectedFile, sessions: previewSessions};
      updateConfigurationButtons();
      presentPreviewReady(fileOperationStatus, applyFileConfiguration, operation);
    } else if (previewGeneration !== inputGeneration || !targetsCurrent) {
      text(fileOperationStatus, 'The targets or file changed while previewing. Preview again before apply.');
    } else {
      text(fileOperationStatus, operationSummary(operation));
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

applyFileConfiguration.addEventListener('click', async () => {
  const currentTargets = fileTargetsForSelection(configurationFile.value);
  if (!fileDraftMatchesCurrentContext() || !approvedFilePreview || approvedFilePreview.fileName !== configurationFile.value
      || !approvedFilePreview.nodeIds.every(nodeId => approvedFilePreview.sessions.get(nodeId) === nodeIndex.get(nodeId)?.sessionId)
      || currentTargets.length !== approvedFilePreview.nodeIds.length
      || !approvedFilePreview.nodeIds.every(nodeId => currentTargets.includes(nodeId))
      || !window.confirm(`Apply this exact ${configurationFile.value} preview to ${fileTargetDescription()}?`)) return;
  const approval = approvedFilePreview;
  approvedFilePreview = null;
  const submittedContent = configurationContent.value;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, fileOperationStatus);
    const currentFileTargets = fileTargetsForSelection(configurationFile.value);
    const submittedContextStillCurrent = approval.fileName === configurationFile.value
      && configurationContent.value === submittedContent
      && fileDraftMatchesCurrentContext()
      && currentFileTargets.length === approval.nodeIds.length
      && approval.nodeIds.every(nodeId => currentFileTargets.includes(nodeId)
        && approval.sessions.get(nodeId) === nodeIndex.get(nodeId)?.sessionId);
    if (operation.state === 'SUCCEEDED' && submittedContextStillCurrent) {
      fileReadCache.clear();
      lastFileReadOperation = null;
      configurationDirty = false;
      configurationDraftNodeId = '';
      configurationDraftSessionId = '';
      configurationDraftFileName = '';
      configurationContent.value = '';
      configurationContentPresent = false;
      updateExtendedButtons();
      await loadFileConfiguration(true);
    } else if (operation.state === 'SUCCEEDED') {
      text(fileOperationStatus, `${operationSummary(operation)}\nThe apply completed, but newer unsaved file edits remain. Preview again before applying them.`);
    } else {
      text(fileOperationStatus, operationSummary(operation));
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

function quickOptions() {
  if (quickPreset.value === 'standalone') return {};
  if (quickPreset.value === 'proxy-backend') return {server: quickName.value.trim(), method: quickMethod.value};
  if (quickPreset.value === 'vote-site') return {
      name: quickName.value.trim(), displayName: quickSiteDisplayName.value.trim() || quickName.value.trim(),
      serviceSite: quickService.value.trim(), voteUrl: quickUrl.value.trim(), voteDelay: quickDelay.value.trim(),
      priority: quickSitePriority.value, material: quickSiteMaterial.value.trim(),
      enabled: String(quickSiteEnabled.checked), hidden: String(quickSiteHidden.checked)
    };
  if (quickPreset.value === 'easy-reward') return {scope: quickRewardScope.value,
    name: quickName.value.trim(), command: quickCommand.value.trim(), message: quickMessage.value.trim()};
  if (quickPreset.value === 'auto-create-vote-sites') return {enabled: String(quickAutoSitesOnly.checked)};
  if (quickPreset.value === 'vote-logging') return {enabled: String(quickVoteLoggingEnabled.checked),
    purgeDays: validatedPurgeDays(quickVoteLoggingDays), useMainMySQL: String(quickVoteLoggingMainMysql.checked)};
  if (quickPreset.value === 'common-settings') return {
    processRewards: String(quickProcessRewards.checked), autoCreateVoteSites: String(quickAutoSites.checked),
    extraAllSitesCheck: String(quickExtraCheck.checked), countFakeVotes: String(quickCountFake.checked),
    disableNoServiceSiteMessage: String(quickHideSiteWarning.checked),
    disableUpdateChecking: String(quickDisableUpdates.checked)
  };
  const voteParty = {votesRequired: quickPartyVotes.value,
    command: quickPartyCommand.value.trim(),
    broadcast: quickPartyBroadcast.value.trim(), giveAllPlayers: String(quickPartyAll.checked),
    onlineOnly: String(quickPartyOnline.checked)};
  if (quickSetupCapability() === 'config.quick-setup.v2') voteParty.enabled = String(quickPartyEnabled.checked);
  return voteParty;
}

function quickReadOptions() {
  return quickPreset.value === 'vote-site' ? {name: quickName.value.trim()} : {};
}

function quickReadConfigurationOptions() {
  if (quickPreset.value === 'proxy-backend') return {method: quickMethod.value};
  if (quickPreset.value === 'vote-party' && quickSetupCapability() === 'config.quick-setup.v2') {
    return {enabled: 'true'};
  }
  return quickReadOptions();
}

function populateQuickState(options) {
  if (quickPreset.value === 'proxy-backend') {
    quickName.value = options.server || '';
    quickMethod.value = options.method || 'PLUGINMESSAGING';
  } else if (quickPreset.value === 'vote-site') {
    quickSiteDisplayName.value = options.displayName || quickName.value.trim();
    quickService.value = options.serviceSite || '';
    quickUrl.value = options.voteUrl || '';
    quickDelay.value = options.voteDelay || '24h';
    quickSitePriority.value = options.priority || '5';
    quickSiteMaterial.value = options.material || 'DIAMOND';
    quickSiteEnabled.checked = options.enabled !== 'false';
    quickSiteHidden.checked = options.hidden === 'true';
  } else if (quickPreset.value === 'common-settings') {
    quickProcessRewards.checked = options.processRewards === 'true';
    quickAutoSites.checked = options.autoCreateVoteSites === 'true';
    quickExtraCheck.checked = options.extraAllSitesCheck === 'true';
    quickCountFake.checked = options.countFakeVotes === 'true';
    quickHideSiteWarning.checked = options.disableNoServiceSiteMessage === 'true';
    quickDisableUpdates.checked = options.disableUpdateChecking === 'true';
  } else if (quickPreset.value === 'auto-create-vote-sites') {
    quickAutoSitesOnly.checked = options.enabled === 'true';
  } else if (quickPreset.value === 'vote-logging') {
    quickVoteLoggingEnabled.checked = options.enabled === 'true';
    quickVoteLoggingDays.value = options.purgeDays || '30';
    quickVoteLoggingMainMysql.checked = options.useMainMySQL !== 'false';
  } else if (quickPreset.value === 'vote-party') {
    const enabledAvailable = quickSetupCapability() === 'config.quick-setup.v2'
      && nodeCapabilities.get(selectedServerId)?.includes('config.quick-setup.v2')
      && Object.hasOwn(options, 'enabled');
    quickPartyEnabled.checked = enabledAvailable && options.enabled === 'true';
    quickPartyEnabled.indeterminate = !enabledAvailable;
    quickPartyEnabled.disabled = !enabledAvailable;
    quickPartyVotes.value = options.votesRequired || '20';
    quickPartyBroadcast.value = options.broadcast || '';
    quickPartyAll.checked = options.giveAllPlayers === 'true';
    quickPartyOnline.checked = options.onlineOnly !== 'false';
    quickPartyCommand.value = '';
  }
}

async function loadQuickSetupValues(automatic = false, preserveDirty = false) {
  if (!quickPresetReadable()) return false;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  const preset = quickPreset.value;
  const nodeId = selectedServerId;
  const sessionId = nodeIndex.get(nodeId)?.sessionId;
  const selector = JSON.stringify(quickReadConfigurationOptions());
  const generation = inputGeneration;
  readQuickSetup.hidden = true;
  text(quickOperationStatus, `Loading current ${preset} settings from ${nodeId}…`);
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedServerId],
      configuration: {domain: 'quick-setup', preset, options: quickReadConfigurationOptions()}
    }, quickOperationStatus);
    const result = Object.values(operation.results).find(item =>
      item.success && item.configuration?.preset === preset && item.configuration?.options);
    if (!result) throw new Error('The selected backend did not return guided settings. Update VotingPlugin on that node.');
    if (generation !== inputGeneration || preset !== quickPreset.value || nodeId !== selectedServerId
        || sessionId !== nodeIndex.get(nodeId)?.sessionId
        || selector !== JSON.stringify(quickReadConfigurationOptions())) {
      if (!quickSetupValuesLoaded()) {
        text(quickOperationStatus, 'The server or setup changed while reading. Load the current values again.');
        readQuickSetup.hidden = false;
        updateConfigurationButtons();
      }
      return false;
    }
    const detected = preset === 'vote-site' && pendingDetectedVoteSite?.nodeId === nodeId
      && pendingDetectedVoteSite.key === quickName.value.trim() ? pendingDetectedVoteSite : null;
    const selectedProxyMethod = preserveDirty && preset === 'proxy-backend' ? quickMethod.value : null;
    const editedProxyServer = preserveDirty && preset === 'proxy-backend' ? quickName.value : null;
    const editedVoteParty = preserveDirty && preset === 'vote-party' ? {
      enabled: !quickPartyEnabled.disabled && !quickPartyEnabled.indeterminate ? quickPartyEnabled.checked : null,
      votes: quickPartyVotes.value,
      broadcast: quickPartyBroadcast.value,
      giveAllPlayers: quickPartyAll.checked,
      onlineOnly: quickPartyOnline.checked,
      command: quickPartyCommand.value
    } : null;
    populateQuickState(result.configuration.options);
    if (selectedProxyMethod != null) quickMethod.value = selectedProxyMethod;
    if (editedProxyServer != null) quickName.value = editedProxyServer;
    if (editedVoteParty != null) {
      if (editedVoteParty.enabled != null && !quickPartyEnabled.disabled) {
        quickPartyEnabled.checked = editedVoteParty.enabled;
      }
      quickPartyVotes.value = editedVoteParty.votes;
      quickPartyBroadcast.value = editedVoteParty.broadcast;
      quickPartyAll.checked = editedVoteParty.giveAllPlayers;
      quickPartyOnline.checked = editedVoteParty.onlineOnly;
      quickPartyCommand.value = editedVoteParty.command;
    }
    quickSetupDirty = preserveDirty;
    if (detected && result.configuration.options.exists === 'false') {
      quickSiteDisplayName.value = detected.service;
      quickService.value = detected.service;
    }
    if (detected) pendingDetectedVoteSite = null;
    loadedQuickSetup = {nodeId, sessionId, preset,
      selector: JSON.stringify(quickReadConfigurationOptions())};
    inputGeneration++;
    const suffix = preset === 'vote-site' && result.configuration.options.exists === 'false'
      ? ` This site key does not exist yet; the form is ready to create it.${detected ? ' The detected service was retained.' : ''}`
      : preset === 'vote-site' && detected
      ? ' The generated key already exists, so its current values were kept; choose a different key for the detected service.'
      : preset === 'vote-party' && Number(result.configuration.options.rewardCommandCount || 0) > 0
      ? ` ${result.configuration.options.rewardCommandCount} existing reward command(s) will be preserved.` : '';
    text(quickOperationStatus, `Current values loaded from ${Object.keys(operation.results).find(id => operation.results[id] === result)}.${suffix}`);
    readQuickSetup.hidden = true;
    updateConfigurationButtons();
    return true;
  } catch (error) {
    if (authenticated && generation === inputGeneration && preset === quickPreset.value
        && nodeId === selectedServerId && sessionId === nodeIndex.get(nodeId)?.sessionId
        && selector === JSON.stringify(quickReadConfigurationOptions())) {
      loadedQuickSetup = null;
      text(quickOperationStatus, `Could not load current ${preset} settings: ${error.message}`);
      readQuickSetup.hidden = false;
      updateConfigurationButtons();
    }
    return false;
  }
}

readQuickSetup.addEventListener('click', () => { void loadQuickSetupValues(false); });

previewQuickSetup.addEventListener('click', async () => {
  approvedQuickPreview = null;
  const previewGeneration = inputGeneration;
  try {
    if (quickPreset.value === 'sync-vote-sites') {
      const sourceId = voteSitesSourceId;
      const nodeIds = selectedVoteSitesTargets();
      const read = await startConfigurationOperation('/api/v1/configuration/read', {
        nodeIds: [sourceId], configuration: {domain: 'file', fileName: 'VoteSites.yml'}
      }, quickOperationStatus);
      const source = Object.values(read.results).find(result =>
        result.success && result.configuration?.content != null)?.configuration?.content;
      if (source == null) throw new Error('The source backend did not return VoteSites.yml.');
      if (previewGeneration !== inputGeneration || sourceId !== voteSitesSourceId) {
        text(quickOperationStatus, 'The source or targets changed while reading. Preview again.');
        return;
      }
      const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
        nodeIds,
        configuration: {domain: 'quick-setup', preset: 'sync-vote-sites', options: {sourceContent: source}}
      }, quickOperationStatus);
      if (preview.state === 'SUCCEEDED' && preview.approvalToken && previewGeneration === inputGeneration) {
        approvedQuickPreview = {workflow: 'sync-vote-sites', operationId: preview.operationId,
          approvalToken: preview.approvalToken, nodeIds, sourceId};
        updateConfigurationButtons();
        presentPreviewReady(quickOperationStatus, applyQuickSetup, preview);
      } else {
        text(quickOperationStatus, operationSummary(preview));
      }
      return;
    }
    const nodeIds = quickSetupTargets();
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds,
      configuration: {domain: 'quick-setup', preset: quickPreset.value, options: quickOptions()}
    }, quickOperationStatus);
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration) {
      approvedQuickPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds};
      updateConfigurationButtons();
      presentPreviewReady(quickOperationStatus, applyQuickSetup, operation);
    } else if (previewGeneration !== inputGeneration) {
      text(quickOperationStatus, 'The targets or setup changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(quickOperationStatus, error.message); }
});

applyQuickSetup.addEventListener('click', async () => {
  const sync = approvedQuickPreview?.workflow === 'sync-vote-sites';
  const confirmation = sync
    ? 'Sync the previewed site definitions to every target? Rewards and target-only sites remain unchanged.'
    : quickPreset.value === 'vote-logging'
    ? 'Apply this exact vote-logging change to every selected Bukkit node? Restart every changed backend afterward; a plugin reload does not activate the new runtime connection.'
    : 'Apply this exact guided change to every selected Bukkit node?';
  if (!approvedQuickPreview || !window.confirm(confirmation)) return;
  const approval = approvedQuickPreview;
  approvedQuickPreview = null;
  const submittedQuickSetup = JSON.stringify({preset: quickPreset.value, options: quickOptions(),
    nodeIds: approval.nodeIds, sourceId: approval.sourceId || ''});
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, quickOperationStatus);
    const currentNodeIds = sync ? selectedVoteSitesTargets() : quickSetupTargets();
    const currentQuickSetup = JSON.stringify({preset: quickPreset.value, options: quickOptions(),
      nodeIds: currentNodeIds, sourceId: sync ? voteSitesSourceId : ''});
    text(quickOperationStatus, operation.state === 'SUCCEEDED' && submittedQuickSetup !== currentQuickSetup
      ? `${operationSummary(operation)}\nThe apply completed, but newer guided setup edits remain. Preview again before applying them.`
      : operationSummary(operation));
    if (operation.state === 'SUCCEEDED' && submittedQuickSetup === currentQuickSetup && !sync) {
      loadedQuickSetup = null;
      await loadQuickSetupValues(true);
    }
  } catch (error) { text(quickOperationStatus, error.message); }
});

voteSitesSource.addEventListener('change', () => {
  voteSitesSourceId = voteSitesSource.value;
  voteSitesTargetIds.delete(voteSitesSourceId);
  voteSitesTargetsInitialized = true;
  approvedQuickPreview = null;
  inputGeneration++;
  renderVoteSitesSync();
  text(quickOperationStatus, 'Source changed. Read it and preview every target before syncing.');
  updateConfigurationButtons();
});

transportTestProxy.addEventListener('change', () => {
  transportTestProxyId = transportTestProxy.value;
  transportTestBackendId = '';
  renderTransportTest();
  text(transportTestStatus, 'Choose a backend, then run a live non-vote communication test.');
  updateConfigurationButtons();
});

transportTestBackend.addEventListener('change', () => {
  transportTestBackendId = transportTestBackend.value;
  text(transportTestStatus, 'Ready to test the active VotingPlugin transport.');
  updateConfigurationButtons();
});

runTransportTest.addEventListener('click', async () => {
  const proxyId = transportTestProxyId;
  const server = transportTestBackendId;
  const requestAuthenticationGeneration = authenticationGeneration;
  const proxySessionId = nodeIndex.get(proxyId)?.sessionId;
  const backendSessionId = nodeIndex.get(server)?.sessionId;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [proxyId],
      configuration: {domain: 'quick-setup', preset: 'communication-test', options: {server}}
    }, transportTestStatus);
    if (requestAuthenticationGeneration !== authenticationGeneration || proxyId !== transportTestProxyId
        || server !== transportTestBackendId || proxySessionId !== nodeIndex.get(proxyId)?.sessionId
        || backendSessionId !== nodeIndex.get(server)?.sessionId
        || operation.results?.[proxyId]?.sessionId !== proxySessionId) {
      text(transportTestStatus, 'The proxy or backend changed while testing. Run the test again.');
      return;
    }
    text(transportTestStatus, operationSummary(operation));
  } catch (error) { text(transportTestStatus, error.message); }
});

async function loadProxyMethod(automatic = false) {
  const proxyId = proxyMethodProxyId;
  const readCapability = proxyMethodReadCapability();
  const sessionId = proxyMethodNetwork(readCapability).proxy?.sessionId;
  const requestAuthenticationGeneration = authenticationGeneration;
  if (!proxyId) return;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [proxyId],
      configuration: {domain: 'quick-setup', preset: 'proxy-method',
        options: {method: readCapability === 'config.proxy-method.v2' ? 'HTTP' : 'PLUGINMESSAGING'}}
    }, proxyMethodStatus);
    const result = operation.results[proxyId];
    const method = result?.success ? result.configuration?.options?.method : '';
    if (!method) throw new Error('The proxy did not return its active communication method.');
    if (requestAuthenticationGeneration !== authenticationGeneration || proxyId !== proxyMethodProxyId
        || sessionId !== proxyMethodNetwork(readCapability).proxy?.sessionId
        || readCapability !== proxyMethodReadCapability() || result?.sessionId !== sessionId) return;
    proxyMethodCurrentFor = proxyId;
    proxyMethodCurrentSessionId = sessionId;
    proxyMethodCurrentReadCapability = readCapability;
    proxyMethodCurrentValue = method;
    renderProxyMethod();
    text(proxyMethodStatus, `Active method on ${proxyId}: ${method}`);
  } catch (error) {
    if (!automatic) text(proxyMethodStatus, error.message);
  }
}

readProxyMethod.addEventListener('click', () => { void loadProxyMethod(false); });

proxyMethodProxy.addEventListener('change', () => {
  proxyMethodProxyId = proxyMethodProxy.value;
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentReadCapability = '';
  proxyMethodCurrentValue = '';
  renderProxyMethod();
  const network = proxyMethodReadNetwork();
  text(proxyMethodStatus, network.unavailable.length > 0
    ? `Cannot switch yet. Enroll, update, and connect: ${network.unavailable.map(backend => backend.displayName).join(', ')}.`
    : 'Choose a method to preflight every node before applying.');
  updateConfigurationButtons();
});

proxyMethodButtons.forEach(button => button.addEventListener('click', async () => {
  const method = button.dataset.proxyMethod;
  const network = proxyMethodNetwork(proxyMethodCapabilityFor(method));
  if (!network.proxyReady || !network.topologyComplete || network.reported.length === 0 ||
      network.nodeIds.length > MAX_OPERATION_TARGETS || network.unavailable.length > 0 || proxyMethodWorkflowInFlight) return;
  proxyMethodWorkflowInFlight = true;
  updateConfigurationButtons();
  try {
    const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: network.nodeIds,
      configuration: {domain: 'quick-setup', preset: 'proxy-method', options: {method}}
    }, proxyMethodStatus);
    if (preview.state !== 'SUCCEEDED' || !preview.approvalToken) return;
    if (!window.confirm(`Switch ${network.nodeIds.length} VotingPlugin nodes to ${method}? ` +
        'Backends reload their communication handler; the proxy replaces its runtime after Control records the result.')) return;
    const refreshedRegistry = await loadAllNodes();
    const refreshedNetwork = proxyMethodNetworkFor(refreshedRegistry.items, refreshedRegistry.truncatedNodeIds,
      proxyMethodProxyId, proxyMethodCapabilityFor(method));
    if (proxyMethodProxyId !== network.proxy.nodeId ||
        refreshedNetwork.proxy?.sessionId !== network.proxy.sessionId ||
        proxyMethodNetworkSignature(refreshedNetwork) !== proxyMethodNetworkSignature(network) ||
        refreshedNetwork.nodeIds.length > MAX_OPERATION_TARGETS) {
      text(proxyMethodStatus, 'The complete proxy topology changed while preflighting. Refresh and choose the method again.');
      return;
    }
    const applied = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: preview.operationId, approvalToken: preview.approvalToken
    }, proxyMethodStatus);
    if (applied.state === 'SUCCEEDED') {
      proxyMethodCurrentFor = network.proxy.nodeId;
      proxyMethodCurrentSessionId = network.proxy.sessionId;
      proxyMethodCurrentReadCapability = proxyMethodReadCapability();
      proxyMethodCurrentValue = method;
      renderProxyMethod();
    }
    const nextStep = applied.state === 'SUCCEEDED'
      ? 'Wait for the proxy to reconnect, then run the communication test to confirm the active transport.'
      : 'No network-wide method change was committed. Fix the failed nodes, refresh the active method, and preview again.';
    text(proxyMethodStatus, `${operationSummary(applied)}\n${nextStep}`);
  } catch (error) {
    text(proxyMethodStatus, error.message);
  } finally {
    proxyMethodWorkflowInFlight = false;
    updateConfigurationButtons();
  }
}));

function dedicatedSetupOptions(preset) {
  if (preset === 'auto-create-vote-sites') return {enabled: String(autoSitesEnabled.checked)};
  return {enabled: String(voteLoggingEnabled.checked), purgeDays: validatedPurgeDays(voteLoggingDays),
    useMainMySQL: String(voteLoggingMainMysql.checked)};
}

function validatedPurgeDays(field) {
  const value = Number(field.value);
  if (!Number.isInteger(value) || value !== -1 && (value < 1 || value > 3650)) {
    throw new Error('Vote-log purge days must be -1 or an integer from 1 to 3650; 0 is not valid.');
  }
  return String(value);
}

function dedicatedSetupElements(preset) {
  return preset === 'auto-create-vote-sites'
    ? {status: autoSitesStatus, state: autoSitesState, retry: loadAutoSites, apply: applyAutoSites}
    : {status: voteLoggingStatus, state: voteLoggingState, retry: loadVoteLogging, apply: applyVoteLogging};
}

async function loadDedicatedSetup(preset, automatic = false) {
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  const requestNodeId = selectedServerId;
  const requestSessionId = nodeIndex.get(requestNodeId)?.sessionId;
  const requestGeneration = inputGeneration;
  elements.retry.hidden = true;
  text(elements.status, `Loading current ${preset} settings from ${requestNodeId}…`);
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedServerId], configuration: {domain: 'quick-setup', preset, options: {}}
    }, elements.status);
    if (requestNodeId !== selectedServerId || requestSessionId !== nodeIndex.get(requestNodeId)?.sessionId
        || requestGeneration !== inputGeneration) {
      throw new Error('The selected server changed while loading setup values. Load them again.');
    }
    const options = operation.results[selectedServerId]?.configuration?.options;
    if (!options) throw new Error('The selected backend did not return this setup. Update VotingPlugin on that node.');
    if (preset === 'auto-create-vote-sites') {
      autoSitesEnabled.checked = options.enabled === 'true';
      text(autoSitesState, autoSitesEnabled.checked ? 'Enabled on primary' : 'Disabled on primary');
    } else {
      voteLoggingEnabled.checked = options.enabled === 'true';
      voteLoggingDays.value = options.purgeDays || '30';
      voteLoggingMainMysql.checked = options.useMainMySQL !== 'false';
      text(voteLoggingState, voteLoggingEnabled.checked ? 'Enabled on primary' : 'Disabled on primary');
    }
    dedicatedSetupDirty.delete(preset);
    elements.state.className = `pill ${options.enabled === 'true' ? 'online' : 'neutral'}`;
    text(elements.status, operationSummary(operation));
  } catch (error) {
    if (authenticated && requestNodeId === selectedServerId
        && requestSessionId === nodeIndex.get(requestNodeId)?.sessionId) {
      text(elements.status, `Could not load current ${preset} settings: ${error.message}`);
      elements.retry.hidden = false;
      if (automatic && requestGeneration !== inputGeneration) void autoLoadTab('quick-setup');
    }
  }
  updateExtendedButtons();
}

async function previewDedicatedSetup(preset) {
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  const previewGeneration = inputGeneration;
  try {
    const nodeIds = backendQuickTargets();
    const options = dedicatedSetupOptions(preset);
    const signature = JSON.stringify({nodeIds, options});
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds, configuration: {domain: 'quick-setup', preset, options}
    }, elements.status);
    if (previewGeneration !== inputGeneration
        || signature !== JSON.stringify({nodeIds: backendQuickTargets(), options: dedicatedSetupOptions(preset)})) {
      text(elements.status, 'The target scope or setup value changed while previewing. Preview again.');
    } else if (operation.state === 'SUCCEEDED' && operation.approvalToken) {
      dedicatedSetupApprovals.set(preset, {operationId: operation.operationId,
        approvalToken: operation.approvalToken, nodeIds});
      updateExtendedButtons();
      presentPreviewReady(elements.status, elements.apply, operation);
    }
  } catch (error) { text(elements.status, error.message); }
  updateExtendedButtons();
}

async function applyDedicatedSetup(preset) {
  const approval = dedicatedSetupApprovals.get(preset);
  const restart = preset === 'vote-logging'
    ? ' Restart every changed backend afterward; a plugin reload does not activate the new runtime connection.' : '';
  if (!approval || !window.confirm(`Apply the exact ${preset} preview to every selected Bukkit node?${restart}`)) return;
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  const submittedOptions = JSON.stringify(dedicatedSetupOptions(preset));
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, elements.status);
    const currentTargets = backendQuickTargets();
    const inputsCurrent = submittedOptions === JSON.stringify(dedicatedSetupOptions(preset))
      && currentTargets.length === approval.nodeIds.length
      && approval.nodeIds.every(nodeId => currentTargets.includes(nodeId));
    if (operation.state === 'SUCCEEDED' && inputsCurrent) {
      fileReadCache.clear();
      if (preset === 'auto-create-vote-sites') {
        text(elements.state, autoSitesEnabled.checked ? 'Enabled on selected' : 'Disabled on selected');
        elements.state.className = `pill ${autoSitesEnabled.checked ? 'online' : 'neutral'}`;
      } else {
        text(elements.state, 'Saved; restart required');
        elements.state.className = 'pill neutral';
      }
      await loadDedicatedSetup(preset, true);
      lastOverview = null;
    } else if (operation.state === 'SUCCEEDED') {
      text(elements.status, `${operationSummary(operation)}\nThe apply completed, but newer setup edits remain. Preview again before applying them.`);
    }
  } catch (error) { text(elements.status, error.message); }
  updateExtendedButtons();
}

loadAutoSites.addEventListener('click', () => loadDedicatedSetup('auto-create-vote-sites'));
previewAutoSites.addEventListener('click', () => previewDedicatedSetup('auto-create-vote-sites'));
applyAutoSites.addEventListener('click', () => applyDedicatedSetup('auto-create-vote-sites'));
selectAllAutoSitesTargets.addEventListener('click', () => {
  setActiveTab('home', true);
  text(autoSitesStatus, 'Choose workspace targets on Home. This form edits only its identified source server until mixed-aware editing is available.');
});
loadVoteLogging.addEventListener('click', () => loadDedicatedSetup('vote-logging'));
previewVoteLogging.addEventListener('click', () => previewDedicatedSetup('vote-logging'));
applyVoteLogging.addEventListener('click', () => applyDedicatedSetup('vote-logging'));
[autoSitesEnabled, voteLoggingEnabled, voteLoggingDays, voteLoggingMainMysql].forEach(field => {
  field.addEventListener('input', () => {
    const preset = field === autoSitesEnabled ? 'auto-create-vote-sites' : 'vote-logging';
    dedicatedSetupApprovals.delete(preset);
    dedicatedSetupDirty.add(preset);
    inputGeneration++;
    updateExtendedButtons();
  });
});

function invalidateDashboardInspection() {
  dashboardOverview = null;
  dashboardVoteSiteHealth = null;
  dashboardVoteSummary24h = null;
  dashboardVoteSummary30d = null;
  dashboardLoadedContext = '';
  dashboardInspectionStatus = emptyDashboardInspectionStatus();
  renderMetrics();
}

async function refreshOverview(target = dataOverview) {
  invalidateDashboardInspection();
  try {
    const envelope = await runInspection('overview', {}, target);
    lastOverview = {...(lastOverview || {}), ...envelope.result};
    renderJsonResult(target, envelope.result);
    updateSetupChecklist(lastOverview);
  } catch (error) { text(target, error.message); }
}

async function refreshDashboard() {
  if (isWorkspaceOverview()) {
    await Promise.all([loadNodes(), loadOperationHistory()]);
    renderScopeOverview();
    renderOverviewActivity();
    return;
  }
  if (dashboardLoading || inspectionInFlight) {
    renderMetrics();
    return;
  }
  dashboardLoading = true;
  inspectionInFlight = true;
  refreshDashboardButton.disabled = true;
  updateExtendedButtons();
  suppressNodeAutoLoad++;
  try {
    await loadNodes();
  } finally {
    suppressNodeAutoLoad--;
  }
  await Promise.all([loadEnrollments(), loadOperationHistory()]);
  if (!inspectionCapableNode()) {
    dashboardLoading = false;
    inspectionInFlight = false;
    renderMetrics();
    updateExtendedButtons();
    return;
  }
  const requestedContext = dashboardContext();
  dashboardLoadedContext = '';
  lastOverview = null;
  text(dataOverview, 'Refreshing server overview…');
  updateSetupChecklist();
  dashboardOverview = null;
  dashboardVoteSiteHealth = null;
  dashboardVoteSummary24h = null;
  dashboardVoteSummary30d = null;
  dashboardInspectionStatus = {overview: 'loading', voteSiteHealth: 'loading',
    voteLog24h: 'not-required', voteLog30d: 'not-required'};
  text(attentionFeed, 'Inspecting the selected VotingPlugin server…');
  try {
    const overviewEnvelope = await runInspection('overview', {}, null, {manageBusy: false});
    if (requestedContext !== dashboardContext()) throw new Error('Dashboard context changed while inspecting.');
    const overview = normalizeDashboardOverview(overviewEnvelope.result);
    dashboardOverview = overview.result;
    dashboardInspectionStatus.overview = overview.incomplete ? 'incomplete' : 'available';
    lastOverview = dashboardOverview;
    renderJsonResult(dataOverview, dashboardOverview);
    updateSetupChecklist(lastOverview);
    try {
      const healthEnvelope = await runInspection('vote-site-health', {days: '30'}, null, {manageBusy: false});
      if (requestedContext !== dashboardContext()) throw new Error('Dashboard context changed while inspecting.');
      const health = normalizeDashboardVoteSiteHealth(healthEnvelope.result);
      dashboardVoteSiteHealth = health.result;
      const healthIncomplete = health.incomplete
        || dashboardHealthContradictsOverview(dashboardOverview, health.result);
      dashboardInspectionStatus.voteSiteHealth = healthIncomplete ? 'incomplete' : 'available';
    } catch (error) {
      if (requestedContext !== dashboardContext()) throw error;
      dashboardVoteSiteHealth = null;
      dashboardInspectionStatus.voteSiteHealth = 'failed';
    }
    if (dashboardOverview.voteLogReadable === true) {
      dashboardInspectionStatus.voteLog24h = 'loading';
      dashboardInspectionStatus.voteLog30d = 'loading';
      try {
        const summaryEnvelope = await runInspection('vote-log-summary', {days: '1'}, null, {manageBusy: false});
        if (requestedContext !== dashboardContext()) throw new Error('Dashboard context changed while inspecting.');
        const summary = normalizeDashboardVoteSummary(summaryEnvelope.result, 1);
        dashboardVoteSummary24h = summary.result;
        dashboardInspectionStatus.voteLog24h = summary.incomplete ? 'incomplete' : 'available';
      } catch (error) {
        if (requestedContext !== dashboardContext()) throw error;
        dashboardVoteSummary24h = null;
        dashboardInspectionStatus.voteLog24h = 'failed';
      }
      try {
        const summaryEnvelope = await runInspection('vote-log-summary', {days: '30'}, null, {manageBusy: false});
        if (requestedContext !== dashboardContext()) throw new Error('Dashboard context changed while inspecting.');
        const summary = normalizeDashboardVoteSummary(summaryEnvelope.result);
        dashboardVoteSummary30d = summary.result;
        dashboardInspectionStatus.voteLog30d = summary.incomplete ? 'incomplete' : 'available';
      } catch (error) {
        if (requestedContext !== dashboardContext()) throw error;
        dashboardVoteSummary30d = null;
        dashboardInspectionStatus.voteLog30d = 'failed';
      }
      if (dashboardVoteSummariesContradict(dashboardVoteSummary24h, dashboardVoteSummary30d)) {
        dashboardInspectionStatus.voteLog24h = 'incomplete';
        dashboardInspectionStatus.voteLog30d = 'incomplete';
      }
      if (dashboardHealthContradictsVoteSummary(dashboardVoteSiteHealth, dashboardVoteSummary30d)) {
        dashboardInspectionStatus.voteSiteHealth = 'incomplete';
        dashboardInspectionStatus.voteLog30d = 'incomplete';
      }
    }
    const complete = Object.values(dashboardInspectionStatus)
      .every(status => ['available', 'not-required'].includes(status));
    if (requestedContext === dashboardContext() && complete) dashboardLoadedContext = requestedContext;
  } catch (error) {
    if (requestedContext === dashboardContext()) {
      dashboardLoadedContext = '';
      dashboardInspectionStatus.overview = 'failed';
      text(attentionFeed, error.message || 'Dashboard inspection failed.');
    }
  } finally {
    dashboardLoading = false;
    inspectionInFlight = false;
    renderMetrics();
    updateExtendedButtons();
    if (requestedContext !== dashboardContext() && tabFromHash() === 'overview') {
      window.setTimeout(() => void autoLoadTab('overview'), 0);
    }
  }
}

refreshSetupChecklist.addEventListener('click', async () => {
  try {
    const envelope = await runInspection('diagnostics', {}, setupChecklistStatus);
    lastOverview = envelope.result;
    invalidateDashboardInspection();
    updateSetupChecklist(envelope.result);
  } catch (error) { text(setupChecklistStatus, error.message); }
});
refreshDataOverview.addEventListener('click', () => refreshOverview(dataOverview));

runNetworkDoctor.addEventListener('click', async () => {
  downloadNetworkDiagnostics.disabled = true;
  lastDiagnostics = null;
  try {
    const diagnostics = await runInspection('diagnostics', {}, networkDoctorResults);
    lastOverview = diagnostics.result;
    invalidateDashboardInspection();
    const node = nodeIndex.get(selectedServerId);
    const voteLog = diagnostics.result.voteLoggingEnabled !== true
      ? {state: 'DISABLED', message: 'Vote logging is disabled; no retained logged-event history is expected.'}
      : diagnostics.result.voteLogReadable === true
      ? {state: 'READABLE', message: 'Retained logged-event history is readable. It is not a guaranteed record of every internal vote-delivery hop.'}
      : {state: 'UNREADABLE', message: 'Vote logging is enabled, but retained logged-event history is not currently readable.'};
    const configuredVoteSites = finiteCount(diagnostics.result.configuredVoteSites);
    const checks = {
      controlConnected: Boolean(node?.online),
      configurationHealthy: diagnostics.result.configurationHealthy,
      votifierDetected: diagnostics.result.votifierDetected,
      voteSitesConfigured: configuredVoteSites == null ? null : configuredVoteSites > 0,
      voteSitesConfiguredKnown: configuredVoteSites != null,
      processRewards: diagnostics.result.processRewards,
      voteLogging: voteLog,
      topologyReported: isBackend(node) ? proxyReportsFor(node.nodeId).length > 0 || !diagnostics.result.proxyMode : true
    };
    lastDiagnostics = {
      schemaVersion: 1, generatedAt: new Date().toISOString(), selectedNodeId: selectedServerId,
      checks, voteLog, node: diagnostics.result,
      control: {application: 'VotingPlugin Control', registeredNodes: allNodeItems.length,
        nodes: allNodeItems.slice(0, 100).map(item => ({nodeId: item.nodeId, displayName: item.displayName,
          role: roleLabel(item), online: item.online, pluginVersion: item.pluginVersion}))}
    };
    renderJsonResult(networkDoctorResults, lastDiagnostics);
    updateSetupChecklist(diagnostics.result);
    downloadNetworkDiagnostics.disabled = false;
  } catch (error) { text(networkDoctorResults, error.message); }
});

downloadNetworkDiagnostics.addEventListener('click', () => {
  if (lastDiagnostics) downloadJson(`votingplugin-diagnostics-${selectedServerId || 'node'}.json`, lastDiagnostics);
});

runDriftCheck.addEventListener('click', async () => {
  setConfigView('compare');
  const nodeIds = comparisonTargetIds();
  const selectedFile = driftFile.value;
  const requestAuthenticationGeneration = authenticationGeneration;
  const requestInputGeneration = inputGeneration;
  const requestSelectedNodeId = selectedServerId;
  const requestSelectedSessionId = nodeIndex.get(requestSelectedNodeId)?.sessionId;
  const requestSessions = new Map(nodeIds.map(nodeId => [nodeId, nodeIndex.get(nodeId)?.sessionId]));
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds, configuration: {domain: 'file', fileName: selectedFile}
    }, driftResults);
    const currentTargets = comparisonTargetIds();
    const targetsStillCurrent = selectedFile === driftFile.value && nodeIds.length >= 2
      && nodeIds.length === currentTargets.length && nodeIds.every(nodeId => currentTargets.includes(nodeId))
      && nodeIds.every(nodeId => requestSessions.get(nodeId) === nodeIndex.get(nodeId)?.sessionId
        && requestSessions.get(nodeId) === operation.results?.[nodeId]?.sessionId);
    if (requestAuthenticationGeneration !== authenticationGeneration || requestInputGeneration !== inputGeneration
        || requestSelectedNodeId !== selectedServerId || requestSelectedSessionId !== nodeIndex.get(requestSelectedNodeId)?.sessionId
        || !targetsStillCurrent) {
      text(driftResults, 'The selected targets or file changed while reading. Drift results were discarded; run the comparison again.');
      return;
    }
    const rows = nodeIds.map(nodeId => {
      const result = operation.results[nodeId];
      return {nodeId, success: Boolean(result?.success), revision: result?.revision || null,
        content: result?.configuration?.content ?? null, error: result?.success ? null : result?.message || result?.code};
    });
    const comparable = rows.filter(row => row.success && typeof row.content === 'string');
    const contentNotRetained = rows.filter(row => row.success && typeof row.content !== 'string')
      .map(row => row.nodeId);
    const groups = new Map();
    comparable.forEach(row => {
      const key = row.revision || row.content;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(row.nodeId);
    });
    lastFileReadOperation = comparable.length > 0 ? {operationId: operation.operationId} : null;
    comparable.forEach(row => {
      const session = nodeIndex.get(row.nodeId)?.sessionId || '';
      cacheFile(`${row.nodeId}|${session}|${selectedFile}`, row.content, operation.operationId);
    });
    const baseline = comparable[0];
    const differences = comparable.filter(row => row !== baseline).map(row => {
      const left = baseline.content.split('\n');
      const right = row.content.split('\n');
      const changes = [];
      for (let index = 0; index < Math.max(left.length, right.length) && changes.length < 50; index++) {
        if (left[index] !== right[index]) changes.push({line: index + 1,
          baseline: String(left[index] ?? '').slice(0, 200), target: String(right[index] ?? '').slice(0, 200)});
      }
      return {baselineNode: baseline.nodeId, targetNode: row.nodeId, changes,
        truncated: changes.length === 50};
    });
    renderJsonResult(driftResults, {fileName: selectedFile, driftDetected: groups.size > 1,
      warning: contentNotRetained.length > 0
        ? 'Some successful file bodies exceeded Control’s 8 MiB aggregate retention bound. Compare fewer targets in batches.' : null,
      contentNotRetained,
      revisionGroups: [...groups.entries()].map(([revision, nodes]) => ({revision, nodes})),
      nodes: rows.map(({content, ...row}) => ({...row, contentBytes: content == null ? 0 : new Blob([content]).size})),
      differences});
  } catch (error) { text(driftResults, error.message); }
  updateExtendedButtons();
});

async function loadSnapshots() {
  try {
    const body = await authorized('/api/v1/snapshots');
    snapshotList.replaceChildren();
    if (!Array.isArray(body.items) || body.items.length === 0) {
      text(snapshotList, 'No snapshots saved yet.');
      return;
    }
    body.items.forEach(snapshot => {
      const item = document.createElement('article');
      item.className = 'result-item';
      const detail = document.createElement('div');
      detail.append(text(document.createElement('strong'), snapshot.name));
      detail.append(text(document.createElement('small'), `${new Date(snapshot.createdAt).toLocaleString()} · ${snapshot.documents.length} document(s)`));
      const restore = text(document.createElement('button'), 'Load for restore preview');
      restore.type = 'button';
      restore.className = 'secondary compact';
      restore.addEventListener('click', async () => {
      if (configurationDirty && !window.confirm('Discard unsaved YAML changes and load this snapshot?')) return;
      restore.disabled = true;
      const restoreServerId = selectedServerId;
      const restoreGeneration = inputGeneration;
      try {
        const full = await authorized(`/api/v1/snapshots/${snapshot.snapshotId}`);
        if (restoreServerId !== selectedServerId || restoreGeneration !== inputGeneration) {
          throw new Error('The selected server changed while loading the snapshot. Load it again.');
          }
          const document = full.documents.find(value => value.nodeId === selectedServerId) || full.documents[0];
          if (!document) throw new Error('This snapshot has no restorable document.');
          const restoreNode = nodeIndex.get(selectedServerId);
          const proxyFile = document.fileName === 'bungeeconfig.yml';
          if (!restoreNode?.online || !nodeCapabilities.get(selectedServerId)?.includes(selectedFileCapability(document.fileName))
              || (proxyFile ? !isProxy(restoreNode) : !isBackend(restoreNode))) {
            throw new Error('Choose a connected node that supports this snapshot file before restoring.');
          }
          configurationFile.value = document.fileName;
          configurationFileSelection = document.fileName;
          configurationContent.value = document.content;
          configurationContentPresent = true;
          configurationDirty = false;
          configurationDraftNodeId = '';
          configurationDraftSessionId = '';
          configurationDraftFileName = '';
          lastFileReadOperation = null;
          updateEditorPosition();
          approvedFilePreview = null;
          inputGeneration++;
          setActiveTab('configurations', true);
          setConfigView('yaml');
          text(fileOperationStatus, `Loaded snapshot “${full.name}” from ${document.nodeId}. Preview the complete file, review the exact changes, then approve to restore it to the selected targets.`);
          updateConfigurationButtons();
          updateExtendedButtons();
        } catch (error) { text(snapshotStatus, error.message); }
        finally { restore.disabled = false; }
      });
      item.append(detail, restore);
      snapshotList.append(item);
    });
  } catch (error) { text(snapshotStatus, error.message); }
}

snapshotForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (!lastFileReadOperation) return;
  try {
    const created = await authorized('/api/v1/snapshots', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({name: snapshotName.value.trim(), operationId: lastFileReadOperation.operationId})
    });
    snapshotName.value = '';
    text(snapshotStatus, `Saved snapshot “${created.name}”.`);
    await loadSnapshots();
  } catch (error) { text(snapshotStatus, error.message); }
});
refreshSnapshots.addEventListener('click', loadSnapshots);

playerLookupForm.addEventListener('submit', async event => {
  event.preventDefault();
  const value = playerLookup.value.trim();
  try { await loadPlayerData(value); }
  catch (error) { text(playerResult, error.message); }
});

loadSiteHealth.addEventListener('click', async () => {
  try {
    const health = normalizeDashboardVoteSiteHealth(
      (await runInspection('vote-site-health', {days: '30'}, siteHealthResult)).result);
    dashboardVoteSiteHealth = health.result;
    if (dashboardLoadedContext === dashboardContext()) {
      const healthContradictsSummary = dashboardHealthContradictsVoteSummary(health.result, dashboardVoteSummary30d);
      const healthIncomplete = health.incomplete
        || dashboardHealthContradictsOverview(dashboardOverview, health.result) || healthContradictsSummary;
      dashboardInspectionStatus.voteSiteHealth = healthIncomplete ? 'incomplete' : 'available';
      if (healthContradictsSummary) dashboardInspectionStatus.voteLog30d = 'incomplete';
      if (dashboardInspectionStatus.voteSiteHealth === 'incomplete') dashboardLoadedContext = '';
    }
    renderSiteHealthResult(health.result);
    renderMetrics();
  }
  catch (error) {
    if (dashboardLoadedContext === dashboardContext()) {
      dashboardInspectionStatus.voteSiteHealth = 'failed';
      dashboardLoadedContext = '';
    }
    text(siteHealthResult, error.message);
    renderMetrics();
  }
});

loadVoteLogSummary.addEventListener('click', async () => {
  try {
    const summary = normalizeDashboardVoteSummary(
      (await runInspection('vote-log-summary', {days: '30'}, voteLogSummaryResult)).result);
    dashboardVoteSummary30d = summary.result;
    if (dashboardLoadedContext === dashboardContext()) {
      const summariesContradict = dashboardVoteSummariesContradict(
        dashboardVoteSummary24h, dashboardVoteSummary30d);
      const healthContradictsSummary = dashboardHealthContradictsVoteSummary(
        dashboardVoteSiteHealth, dashboardVoteSummary30d);
      dashboardInspectionStatus.voteLog30d = summary.incomplete || summariesContradict || healthContradictsSummary
        ? 'incomplete' : 'available';
      if (summariesContradict) {
        dashboardInspectionStatus.voteLog24h = 'incomplete';
      }
      if (healthContradictsSummary) dashboardInspectionStatus.voteSiteHealth = 'incomplete';
      if (summary.incomplete || summariesContradict || healthContradictsSummary) dashboardLoadedContext = '';
    }
    renderJsonResult(voteLogSummaryResult, summary.result);
    renderMetrics();
  }
  catch (error) {
    if (dashboardLoadedContext === dashboardContext()) {
      dashboardInspectionStatus.voteLog30d = 'failed';
      dashboardLoadedContext = '';
    }
    text(voteLogSummaryResult, error.message);
    renderMetrics();
  }
});

voteLogFilterType.addEventListener('change', () => {
  voteLogFilter.disabled = !voteLogFilterType.value;
  voteLogFilter.required = Boolean(voteLogFilterType.value);
  voteLogFilter.placeholder = voteLogFilterType.value ? `Exact ${voteLogFilterType.value}` : '';
});
voteLogForm.addEventListener('submit', async event => {
  event.preventDefault();
  const filters = {days: voteLogDays.value, limit: voteLogLimit.value};
  if (voteLogFilterType.value) filters[voteLogFilterType.value] = voteLogFilter.value.trim();
  if (voteLogEvent.value) filters.event = voteLogEvent.value;
  try { renderJsonResult(voteLogResult, (await runInspection('vote-log-search', filters, voteLogResult)).result); }
  catch (error) { text(voteLogResult, error.message); }
});

voteTraceForm.addEventListener('submit', async event => {
  event.preventDefault();
  try { await traceVoteAcrossNodes(); }
  catch (error) { text(voteTraceResult, error.message); }
});

siteResolutionForm.addEventListener('submit', async event => {
  event.preventDefault();
  try { renderJsonResult(siteResolutionResult, (await runInspection('vote-site-resolution',
    {serviceSite: siteResolutionService.value.trim(), includeDisabled: String(siteResolutionDisabled.checked)}, siteResolutionResult)).result); }
  catch (error) { text(siteResolutionResult, error.message); }
});

rewardScope.addEventListener('change', () => { rewardSiteLabel.hidden = rewardScope.value !== 'site'; });
function rewardProposal() {
  const items = boundedLines(rewardItems.value).map(line => {
    const match = line.match(/^([A-Za-z0-9_]{1,80})\s+([0-9]{1,2})$/);
    if (!match || Number(match[2]) < 1 || Number(match[2]) > 64) {
      throw new Error(`Invalid item “${line}”. Use MATERIAL and an amount from 1 to 64.`);
    }
    return {material: match[1].toUpperCase(), amount: Number(match[2])};
  });
  const proposal = {scope: rewardScope.value, commands: boundedLines(rewardCommands.value),
    playerMessages: boundedLines(rewardMessages.value), broadcastMessages: boundedLines(rewardBroadcasts.value),
    items, permissions: boundedLines(rewardPermissions.value), money: Number(rewardMoney.value),
    chancePercent: Number(rewardChance.value), onlineOnly: rewardOnlineOnly.checked};
  if (rewardScope.value === 'site') proposal.site = rewardSite.value.trim();
  return proposal;
}

rewardSimulationForm.addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const proposal = rewardProposal();
    const envelope = await runInspection('reward-simulation', {proposal: JSON.stringify(proposal)}, rewardSimulationResult);
    renderJsonResult(rewardSimulationResult, envelope.result);
    copyRewardToSetup.disabled = proposal.commands.length === 0;
  } catch (error) { text(rewardSimulationResult, error.message); }
});
previewReward.addEventListener('click', async () => {
  dedicatedSetupApprovals.delete('reward-builder');
  const previewGeneration = inputGeneration;
  try {
    const proposal = JSON.stringify(rewardProposal());
    if (new TextEncoder().encode(proposal).length > 64 * 1024) throw new Error('Reward proposal exceeds the 64 KiB limit.');
    const nodeIds = backendQuickTargets();
    const signature = JSON.stringify({nodeIds, proposal});
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds,
      configuration: {domain: 'quick-setup', preset: 'reward-builder', options: {proposal}}
    }, rewardSimulationResult);
    if (previewGeneration !== inputGeneration
        || signature !== JSON.stringify({nodeIds: backendQuickTargets(), proposal: JSON.stringify(rewardProposal())})) {
      text(rewardSimulationResult, 'The target scope or reward changed while previewing. Preview again.');
    } else if (operation.state === 'SUCCEEDED' && operation.approvalToken) {
      dedicatedSetupApprovals.set('reward-builder', {operationId: operation.operationId,
        approvalToken: operation.approvalToken, nodeIds});
      updateExtendedButtons();
      presentPreviewReady(rewardSimulationResult, applyReward, operation);
    }
  } catch (error) { text(rewardSimulationResult, error.message); }
  updateExtendedButtons();
});
applyReward.addEventListener('click', async () => {
  const approval = dedicatedSetupApprovals.get('reward-builder');
  if (!approval || !window.confirm('Apply this exact reward preview to every selected Bukkit node? It replaces the selected Rewards subtree; sibling sites, scopes, and settings remain unchanged.')) return;
  dedicatedSetupApprovals.delete('reward-builder');
  const submittedReward = JSON.stringify({proposal: rewardProposal(), nodeIds: approval.nodeIds});
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, rewardSimulationResult);
    const currentReward = JSON.stringify({proposal: rewardProposal(), nodeIds: backendQuickTargets()});
    if (operation.state === 'SUCCEEDED' && submittedReward === currentReward) {
      fileReadCache.clear();
    } else if (operation.state === 'SUCCEEDED') {
      text(rewardSimulationResult, `${operationSummary(operation)}\nThe apply completed, but newer reward edits remain. Preview again before applying them.`);
    }
  } catch (error) { text(rewardSimulationResult, error.message); }
  updateExtendedButtons();
});
[rewardScope, rewardSite, rewardChance, rewardMoney, rewardCommands, rewardMessages, rewardBroadcasts,
  rewardPermissions, rewardItems, rewardOnlineOnly].forEach(field => field.addEventListener('input', () => {
    dedicatedSetupApprovals.delete('reward-builder');
    inputGeneration++;
    copyRewardToSetup.disabled = boundedLines(rewardCommands.value).length === 0;
    updateExtendedButtons();
  }));
copyRewardToSetup.addEventListener('click', () => {
  const command = boundedLines(rewardCommands.value)[0];
  if (!command) return;
  pendingDetectedVoteSite = null;
  quickPreset.value = 'easy-reward';
  quickRewardScope.value = rewardScope.value === 'site' ? 'site' : 'every-site';
  quickName.value = rewardScope.value === 'site' ? rewardSite.value.trim() : '';
  quickCommand.value = command;
  quickMessage.value = boundedLines(rewardMessages.value)[0] || '';
  updateQuickFields();
  clearApprovals();
  scrollToAnchor(document.querySelector('#quick-setup-card'));
});

settingsFilter.addEventListener('input', renderSettingsCatalog);

saveProfile.addEventListener('click', () => {
  const name = profileName.value.trim();
  if (!name || name.length > 60 || /[\p{Cc}]/u.test(name)) {
    text(profileStatus, 'Enter a profile name between 1 and 60 visible characters.');
    return;
  }
  try {
    const profiles = readProfiles();
    if (!Object.hasOwn(profiles, name) && Object.keys(profiles).length >= 20) throw new Error('Delete a profile before saving another; the limit is 20.');
    profiles[name] = currentProfileValues();
    writeProfiles(profiles);
    populateProfilePicker();
    profilePicker.value = name;
    profilePicker.dispatchEvent(new Event('change'));
    text(profileStatus, `Saved “${name}” on this browser. It contains the visible setup form values, including entered URLs and commands, but no raw YAML or Control/database credentials.`);
  } catch (error) { text(profileStatus, error.message || 'The browser could not save this profile.'); }
});

profilePicker.addEventListener('change', () => {
  loadProfile.disabled = !profilePicker.value;
  deleteProfile.disabled = !profilePicker.value;
});

function applyProfileValues(profile) {
  const assign = (field, value, max = 500) => { field.value = String(value ?? '').slice(0, max); };
  if ([...quickPreset.options].some(option => option.value === profile.preset)) quickPreset.value = profile.preset;
  assign(quickName, profile.name, 64); assign(quickMethod, profile.method, 32);
  assign(quickSiteDisplayName, profile.siteDisplayName, 200); assign(quickService, profile.service, 200);
  assign(quickUrl, profile.url, 500); assign(quickDelay, profile.delay, 20);
  assign(quickSitePriority, profile.priority, 3); assign(quickSiteMaterial, profile.material, 100);
  quickSiteEnabled.checked = Boolean(profile.siteEnabled); quickSiteHidden.checked = Boolean(profile.siteHidden);
  assign(quickRewardScope, profile.rewardScope, 20); assign(quickCommand, profile.command, 500);
  assign(quickMessage, profile.playerMessage, 500); quickProcessRewards.checked = Boolean(profile.processRewards);
  quickAutoSites.checked = Boolean(profile.autoSites); quickExtraCheck.checked = Boolean(profile.extraCheck);
  quickCountFake.checked = Boolean(profile.countFake); quickHideSiteWarning.checked = Boolean(profile.hideWarning);
  quickDisableUpdates.checked = Boolean(profile.disableUpdates);
  if (Object.hasOwn(profile, 'partyEnabled')) quickPartyEnabled.checked = Boolean(profile.partyEnabled);
  assign(quickPartyVotes, profile.partyVotes, 6);
  assign(quickPartyCommand, profile.partyCommand, 500); assign(quickPartyBroadcast, profile.partyBroadcast, 500);
  quickPartyAll.checked = Boolean(profile.partyAll); quickPartyOnline.checked = Boolean(profile.partyOnline);
  quickAutoSitesOnly.checked = Boolean(profile.autoSitesOnly); quickVoteLoggingEnabled.checked = Boolean(profile.voteLogging);
  assign(quickVoteLoggingDays, profile.voteLoggingDays, 4); quickVoteLoggingMainMysql.checked = Boolean(profile.voteLoggingMainMysql);
  if (profile.rewardBuilder && typeof profile.rewardBuilder === 'object') {
    assign(rewardScope, profile.rewardBuilder.scope, 20); assign(rewardSite, profile.rewardBuilder.site, 64);
    assign(rewardChance, profile.rewardBuilder.chance, 8); assign(rewardMoney, profile.rewardBuilder.money, 20);
    assign(rewardCommands, profile.rewardBuilder.commands, 10020); assign(rewardMessages, profile.rewardBuilder.messages, 10020);
    assign(rewardBroadcasts, profile.rewardBuilder.broadcasts, 10020); assign(rewardPermissions, profile.rewardBuilder.permissions, 4020);
    assign(rewardItems, profile.rewardBuilder.items, 2020); rewardOnlineOnly.checked = Boolean(profile.rewardBuilder.onlineOnly);
    rewardSiteLabel.hidden = rewardScope.value !== 'site';
    copyRewardToSetup.disabled = boundedLines(rewardCommands.value).length === 0;
  }
}

loadProfile.addEventListener('click', async () => {
  const profileName = profilePicker.value;
  const profile = readProfiles()[profileName];
  if (!profile || profile.version !== 1) { text(profileStatus, 'That profile is unavailable or unsupported.'); return; }
  const profileSignature = JSON.stringify(profile);
  pendingDetectedVoteSite = null;
  if ([...quickPreset.options].some(option => option.value === profile.preset)) quickPreset.value = profile.preset;
  quickName.value = String(profile.name ?? '').slice(0, 64);
  quickMethod.value = String(profile.method ?? '').slice(0, 32);
  loadedQuickSetup = null;
  updateQuickFields();
  clearApprovals();
  text(profileStatus, `Loading live values before applying “${profileName}”…`);
  if (quickPresetReadable() && !await loadQuickSetupValues(true)) {
    text(profileStatus, `Could not load live values for “${profileName}”. Retry before using this profile.`);
    return;
  }
  const currentProfile = readProfiles()[profileName];
  if (profilePicker.value !== profileName || !currentProfile || JSON.stringify(currentProfile) !== profileSignature) {
    text(profileStatus, 'The selected profile changed while loading live values. Select it again before applying it.');
    return;
  }
  applyProfileValues(profile);
  quickSetupDirty = true;
  inputGeneration++;
  if (quickPreset.value === 'proxy-backend' && loadedQuickSetup) {
    loadedQuickSetup = {...loadedQuickSetup, selector: JSON.stringify(quickReadConfigurationOptions())};
  }
  updateQuickFields();
  clearApprovals();
  text(profileStatus, `Loaded “${profileName}” over the confirmed live values. Preview before applying.`);
});
deleteProfile.addEventListener('click', () => {
  const name = profilePicker.value;
  if (!name || !window.confirm(`Delete browser-local setup profile “${name}”?`)) return;
  try {
    const profiles = readProfiles();
    delete profiles[name];
    writeProfiles(profiles);
    populateProfilePicker();
    text(profileStatus, `Deleted “${name}”.`);
  } catch (error) { text(profileStatus, error.message || 'The browser could not delete this profile.'); }
});

clearOperationHistory.addEventListener('click', loadOperationHistory);

[quickSiteDisplayName, quickService, quickUrl, quickDelay,
  quickSitePriority, quickSiteMaterial, quickSiteEnabled, quickSiteHidden, quickRewardScope,
  quickCommand, quickMessage, quickProcessRewards, quickAutoSites, quickExtraCheck, quickCountFake,
  quickHideSiteWarning, quickDisableUpdates, quickPartyEnabled, quickPartyVotes, quickPartyCommand, quickPartyBroadcast,
  quickPartyAll, quickPartyOnline, quickAutoSitesOnly, quickVoteLoggingEnabled, quickVoteLoggingDays,
  quickVoteLoggingMainMysql].forEach(field => field.addEventListener('input', () => {
  quickSetupDirty = true;
  exposeDirtyVoteSiteReload();
  clearApprovals();
}));
quickMethod.addEventListener('input', clearApprovals);
quickName.addEventListener('input', () => {
  if (quickPreset.value !== 'vote-site') quickSetupDirty = true;
  clearApprovals();
});
quickMethod.addEventListener('input', () => {
  if (quickPreset.value === 'proxy-backend' && quickPresetReadable()) {
    quickSetupDirty = true;
    if (quickSetupDirty) quickSetupPreserveReadGeneration = inputGeneration;
    void autoLoadTab('quick-setup');
  }
});
quickName.addEventListener('input', () => {
  if (pendingDetectedVoteSite && pendingDetectedVoteSite.key !== quickName.value.trim()) pendingDetectedVoteSite = null;
  updateQuickFields();
  exposeDirtyVoteSiteReload();
  if (voteSiteReadTimer != null) window.clearTimeout(voteSiteReadTimer);
  if (quickPreset.value === 'vote-site' && quickPresetReadable()) {
    voteSiteReadTimer = window.setTimeout(() => {
      voteSiteReadTimer = null;
      void autoLoadTab('quick-setup');
    }, 300);
  }
});

function exposeDirtyVoteSiteReload() {
  if (quickPreset.value !== 'vote-site' || !quickSetupDirty || quickSetupValuesLoaded()) return;
  readQuickSetup.hidden = false;
  text(quickOperationStatus, 'The vote-site key changed; load its current values to discard your unsaved edits.');
}
configurationContent.addEventListener('input', () => {
  if (!configurationDirty) {
    configurationDraftNodeId = selectedServerId;
    configurationDraftSessionId = nodeIndex.get(selectedServerId)?.sessionId || '';
    configurationDraftFileName = configurationFile.value;
  }
  configurationContentPresent = true;
  configurationDirty = true;
  clearApprovals();
  updateEditorPosition();
});
configurationContent.addEventListener('click', updateEditorPosition);
configurationContent.addEventListener('keyup', updateEditorPosition);
configurationContent.addEventListener('keydown', handleEditorKeydown);
configurationFile.addEventListener('input', () => {
  const requestedFile = configurationFile.value;
  if (requestedFile !== configurationFileSelection && configurationDirty
      && !window.confirm('Discard unsaved YAML changes and switch files?')) {
    configurationFile.value = configurationFileSelection;
    return;
  }
  configurationFileSelection = requestedFile;
  resetFileEditorForSelection('Read the selected file before previewing changes.');
  clearApprovals();
  updateExtendedButtons();
  void autoLoadTab('configurations');
});
quickPreset.addEventListener('input', () => {
  loadedQuickSetup = null;
  quickSetupDirty = false;
  if (quickPreset.value !== 'vote-site') pendingDetectedVoteSite = null;
  updateQuickFields();
  clearApprovals();
  if (quickPresetNeedsRead()) {
    text(quickOperationStatus, quickPreset.value === 'vote-site'
      ? 'Enter the vote-site key, then load its current values before previewing.'
      : 'Load the current values from the primary server before previewing changes.');
  }
  void autoLoadTab('quick-setup');
});
serverPicker.addEventListener('change', () => selectPrimaryServer(serverPicker.value));
homeSearch.addEventListener('input', renderHomeChooser);
document.querySelector('#home-refresh').addEventListener('click', () => loadNodes());
document.querySelector('#home-select-all').addEventListener('click', () => changeWorkspaceTargets(() => workspace.selectEligible(allNodeItems, MAX_CONFIGURATION_TARGETS)));
document.querySelector('#home-clear').addEventListener('click', () => changeWorkspaceTargets(() => workspace.clearTargets()));
document.querySelector('#home-continue').addEventListener('click', openScopeOverview);
document.querySelector('#home-restore').addEventListener('click', () => {
  if (changeWorkspaceTargets(() => workspace.returnToServers())) openScopeOverview();
});
document.querySelector('#choose-global').addEventListener('click', enterGlobalWorkspace);
document.querySelector('#scope-overview').addEventListener('click', openScopeOverview);
deploymentJar.addEventListener('change', renderDeploymentEligibility);
deployPlugin.addEventListener('click', async () => {
  const file = deploymentJar.files?.[0];
  const eligible = deploymentTargets();
  if (!authenticated || logoutInFlight || !file || !eligible.length || deploymentInFlight) return;
  const batches = [];
  for (let offset = 0; offset < eligible.length; offset += MAX_OPERATION_TARGETS) {
    batches.push(eligible.slice(offset, offset + MAX_OPERATION_TARGETS));
  }
  if (batches.length > MAX_DEPLOYMENT_BATCHES) {
    text(deploymentStatus, `This deployment exceeds the ${MAX_DEPLOYMENT_BATCHES}-batch safety limit.`);
    return;
  }
  if (!file.name.toLowerCase().endsWith('.jar') || file.size < 1 || file.size > 64 * 1024 * 1024) {
    text(deploymentStatus, 'Choose a non-empty VotingPlugin JAR no larger than 64 MiB.');
    return;
  }
  const ineligible = allNodeItems.filter(node => node.online
    && !node.acceptedCapabilities.includes('plugin.deploy.v1')).map(node => node.displayName);
  const confirmation = `Upload ${file.name} (${file.size.toLocaleString()} bytes) and stage it on `
    + `${eligible.length} deployment-capable node(s)? Servers will require a restart. Automatic restart is disabled.`
    + (batches.length > 1 ? ` Control will use ${batches.length} bounded operations.` : '')
    + (ineligible.length ? ` Older/incompatible nodes excluded: ${ineligible.join(', ')}.` : '');
  if (!window.confirm(confirmation)) return;
  const deploymentRun = ++deploymentRunGeneration;
  deploymentInFlight = true;
  renderDeploymentEligibility();
  const generation = authenticationGeneration;
  const submittedOperations = [];
  const completedOperations = [];
  const unavailableBatchNodes = [];
  try {
    text(deploymentStatus, 'Calculating SHA-256 locally…');
    const sha256 = await deploymentFileSha256(file);
    if (generation !== authenticationGeneration) {
      throw new Error('Authentication changed before the deployment upload started.');
    }
    text(deploymentStatus, sha256 ? `Uploading artifact ${sha256.slice(0, 12)} for server verification…`
      : 'Uploading artifact for bounded server-side SHA-256 verification…');
    const uploadHeaders = {'Content-Type': 'application/java-archive', 'X-Filename': file.name};
    if (sha256) uploadHeaders['X-Artifact-SHA256'] = sha256;
    const artifact = await authorized('/api/v1/artifacts/votingplugin', {
      method: 'POST', headers: uploadHeaders, body: file
    });
    if (sha256 && artifact.sha256 !== sha256 || artifact.size !== file.size) {
      throw new Error('Control returned artifact metadata that does not match the selected JAR.');
    }
    const verifiedSha256 = artifact.sha256;
    const operations = [];
    for (const batch of batches) {
      if (generation !== authenticationGeneration) {
        throw new Error('Authentication changed before every deployment batch was submitted.');
      }
      let remaining = batch.map(node => node.nodeId);
      for (let attempt = 0; remaining.length > 0 && attempt < MAX_OPERATION_TARGETS; attempt++) {
        if (generation !== authenticationGeneration) {
          throw new Error('Authentication changed before every deployment batch was submitted.');
        }
        try {
          const operation = await authorized('/api/v1/deployments', {
            method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({
              artifactId: artifact.artifactId, sha256: verifiedSha256, size: file.size,
              nodeIds: remaining
            })
          });
          operations.push(operation);
          submittedOperations.push(operation);
          remaining = [];
        } catch (error) {
          if (error.code !== 'NODE_UNAVAILABLE') throw error;
          const unavailable = new Set((error.details || []).filter(nodeId => remaining.includes(nodeId)));
          if (unavailable.size === 0) {
            throw new Error('Deployment eligibility changed without identifying an unavailable node. Refresh and try again.');
          }
          unavailable.forEach(nodeId => {
            const node = batch.find(candidate => candidate.nodeId === nodeId);
            const label = node ? `${node.displayName} (${node.nodeId})` : nodeId;
            if (!unavailableBatchNodes.includes(label)) unavailableBatchNodes.push(label);
          });
          remaining = remaining.filter(nodeId => !unavailable.has(nodeId));
        }
      }
      if (remaining.length > 0) {
        throw new Error('Deployment eligibility could not be settled within the bounded retry limit.');
      }
    }
    for (const operation of operations) {
      completedOperations.push(await waitForDeployment(operation, generation));
    }
    const unavailableSummary = unavailableBatchNodes.length
      ? `\nUnavailable nodes skipped: ${unavailableBatchNodes.join(', ')}.` : '';
    text(deploymentStatus, completedOperations.length
      ? `${completedOperations.map(deploymentSummary).join('\n\n')}\nRestart each successfully staged server to activate this JAR.${unavailableSummary}`
      : `No deployment batches were submitted.${unavailableSummary}`);
  } catch (error) {
    if (generation === authenticationGeneration) {
      const submitted = submittedOperations.map(operation => completedOperations.find(completed =>
        completed.deploymentId === operation.deploymentId) || operation);
      const unavailableSummary = unavailableBatchNodes.length
        ? `\nUnavailable nodes skipped: ${unavailableBatchNodes.join(', ')}.` : '';
      text(deploymentStatus, submitted.length
        ? `${submitted.map(deploymentSummary).join('\n\n')}\nDeployment submission or monitoring stopped: ${error.message}`
          + `\nThe listed operations remain durable in Activity; verify them before retrying.${unavailableSummary}`
        : `${error.message}${unavailableSummary}`);
    }
  } finally {
    if (deploymentRun === deploymentRunGeneration) {
      deploymentInFlight = false;
      renderDeploymentEligibility();
    }
  }
});
tabButtons.forEach(button => button.addEventListener('click', () => {
  if (button.dataset.configShortcut) setConfigView(button.dataset.configShortcut);
  if (button.dataset.tab === 'overview' && workspace.managementScope !== 'GLOBAL') return openScopeOverview();
  setActiveTab(button.dataset.tab, true);
}));
configViewButtons.forEach(button => button.addEventListener('click', () => setConfigView(button.dataset.configView)));
document.querySelectorAll('[data-open-tab]').forEach(button => button.addEventListener('click', () => {
  openWorkspace(button.dataset.openTab, button.dataset.scrollTarget, button.dataset.quickPreset, button);
}));
document.querySelectorAll('[data-open-config-view]').forEach(button => button.addEventListener('click', () => {
  setActiveTab('configurations', true);
  setConfigView(button.dataset.openConfigView);
  if (button.dataset.file && configurationFile.value !== button.dataset.file) {
    configurationFile.value = button.dataset.file;
    configurationFile.dispatchEvent(new Event('input'));
  }
}));
window.addEventListener('hashchange', applyNavigationRoute);
window.addEventListener('popstate', applyNavigationRoute);
sidebarToggle.addEventListener('click', () => {
  const open = document.body.classList.toggle('sidebar-open');
  sidebarToggle.setAttribute('aria-expanded', String(open));
  sidebarToggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
  if (open) primaryNavigation.querySelector('button')?.focus();
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && document.body.classList.contains('sidebar-open')) {
    closeSidebar();
    sidebarToggle.focus();
    return;
  }
  if (event.key === 'Tab' && document.body.classList.contains('sidebar-open')
      && window.matchMedia('(max-width: 920px)').matches) {
    const focusable = navigationButtons.filter(button => !button.disabled);
    const first = focusable[0];
    const last = focusable.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }
});
document.addEventListener('click', event => {
  if (!document.body.classList.contains('sidebar-open') || primaryNavigation.contains(event.target)
      || sidebarToggle.contains(event.target)) return;
  closeSidebar();
});

function populateGlobalSearch() {
  const values = ['Add Vote Site', 'Vote Sites', 'Rewards', 'Setup', 'Settings', 'Vote logging',
    'Servers', 'Proxy & Routing', 'Sync Vote Sites', 'Votes & Data', 'Activity', 'Network Doctor',
    'Configuration Compare', 'Access', ...GENERAL_SETTING_FIELDS.map(setting => setting.path),
    ...SETTINGS_SCHEMA.map(setting => setting.key),
    ...new Set([...(voteSitesEditor?.model?.targets?.values?.() || [])].flatMap(target =>
      (target.sites || []).map(site => site.siteKey))),
    ...allNodeItems.flatMap(node => [node.displayName, node.nodeId])];
  const unique = [...new Set(values.filter(Boolean))].slice(0, 300);
  globalSearchOptions.replaceChildren(...unique.map(value => {
    const option = document.createElement('option');
    option.value = value;
    return option;
  }));
}

const GLOBAL_PAGE_SHORTCUTS = new Map([
  ['add vote site', {tab: 'vote-sites', action: 'add-site'}],
  ['vote sites', {tab: 'vote-sites'}],
  ['rewards', {tab: 'rewards'}],
  ['setup', {tab: 'quick-setup', scrollTarget: 'quick-setup-card'}],
  ['settings', {tab: 'general-settings'}],
  ['vote logging', {tab: 'quick-setup', scrollTarget: 'quick-setup-card', preset: 'vote-logging'}],
  ['servers', {tab: 'servers'}],
  ['proxy & routing', {tab: 'network'}],
  ['sync vote sites', {tab: 'quick-setup', scrollTarget: 'quick-setup-card', preset: 'sync-vote-sites'}],
  ['votes & data', {tab: 'data'}],
  ['activity', {tab: 'activity'}],
  ['network doctor', {tab: 'network', scrollTarget: 'network-doctor-card'}],
  ['configuration compare', {tab: 'configurations', configView: 'compare', scrollTarget: 'drift-results'}],
  ['access', {tab: 'access'}]
]);

function openGlobalShortcut(destination) {
  if (destination.configView) setConfigView(destination.configView);
  openWorkspace(destination.tab, destination.scrollTarget || '', destination.preset || '');
  if (destination.action === 'add-site' && tabFromHash() === 'vote-sites') openVoteSiteAdd();
}

globalSearch.addEventListener('submit', event => {
  event.preventDefault();
  const query = globalSearchInput.value.trim();
  const normalized = query.toLowerCase();
  if (!normalized) return;
  const exactShortcut = GLOBAL_PAGE_SHORTCUTS.get(normalized);
  if (exactShortcut) {
    openGlobalShortcut(exactShortcut);
    globalSearchInput.value = '';
    return;
  }
  const node = allNodeItems.find(item => item.nodeId.toLowerCase() === normalized
    || item.displayName.toLowerCase() === normalized)
    || allNodeItems.find(item => item.nodeId.toLowerCase().includes(normalized)
      || item.displayName.toLowerCase().includes(normalized));
  if (node) {
    const nodePosition = allNodeItems.indexOf(node);
    selectNodePage(Math.floor(nodePosition / PAGE_SIZE) * PAGE_SIZE);
    openWorkspace('servers');
    selectPrimaryServer(node.nodeId);
    // The same-server fast path intentionally avoids resetting drafts and does
    // not render. Always paint the newly selected page slice after navigation.
    renderNodeViews();
    globalSearchInput.value = '';
    return;
  }
  const setting = GENERAL_SETTING_FIELDS.find(item => Object.values(item).join(' ').toLowerCase().includes(normalized));
  const site = voteSitesEditor?.model && [...voteSitesEditor.model.targets.values()]
    .flatMap(target => target.sites || []).find(candidate => candidate.siteKey.toLowerCase() === normalized);
  if (setting) {
    openWorkspace('general-settings');
  } else if (site) {
    openWorkspace('vote-sites');
    voteSitesEditor.selectSite(site.siteKey);
    renderVoteSites();
  } else if (normalized.includes('sync')) openWorkspace('quick-setup', 'quick-setup-card', 'sync-vote-sites');
  else if (normalized.includes('reward')) openWorkspace('rewards');
  else if (normalized.includes('vote site') || normalized.includes('service') || normalized.includes('planet')) openWorkspace('vote-sites');
  else if (normalized.includes('vote logging')) openWorkspace('quick-setup', 'quick-setup-card', 'vote-logging');
  else if (normalized.includes('doctor') || normalized.includes('diagnostic')) openWorkspace('network', 'network-doctor-card');
  else if (['proxy', 'routing', 'redis', 'mqtt', 'mysql', 'sockets', 'transport'].some(value => normalized.includes(value))) openWorkspace('network');
  else if (normalized.includes('compare') || normalized.includes('drift') || normalized.includes('configuration')) {
    openGlobalShortcut(GLOBAL_PAGE_SHORTCUTS.get('configuration compare'));
  }
  else if (normalized.includes('vote') || normalized.includes('data') || normalized.includes('log')) openWorkspace('data');
  else if (normalized.includes('activity') || normalized.includes('operation') || normalized.includes('history')) openWorkspace('activity');
  else if (normalized.includes('server') || normalized.includes('topology')) openWorkspace('servers');
  else if (normalized.includes('access') || normalized.includes('enroll')) openWorkspace('access');
  else if (normalized.includes('setup')) openWorkspace('quick-setup');
  else {
    text(message, `No Control page or setting matched “${query}”.`);
    return;
  }
  globalSearchInput.value = '';
});
window.addEventListener('beforeunload', event => {
  if (!configurationDirty && !routingDirty && !settingsEditor?.model.dirty.size && !voteSitesHasDraft() && !rewardsEditor?.edit) return;
  event.preventDefault();
  event.returnValue = '';
});
const VOTE_SITE_FIELDS = [
  {path: 'Enabled', label: 'Enabled', control: '#vote-site-enabled', type: 'boolean'},
  {path: 'Name', label: 'Display name', control: '#vote-site-name', type: 'string'},
  {path: 'ServiceSite', label: 'Service site', control: '#vote-site-service', type: 'string'},
  {path: 'VoteURL', label: 'Vote URL', control: '#vote-site-url', type: 'string'},
  {path: 'VoteDelay', label: 'Vote delay', control: '#vote-site-delay', type: 'string'},
  {path: 'Priority', label: 'Priority', control: '#vote-site-priority', type: 'integer'},
  {path: 'DisplayItem.Material', label: 'Display material', control: '#vote-site-material', type: 'string'},
  {path: 'DisplayItem.Amount', label: 'Display amount', control: '#vote-site-amount', type: 'integer'},
  {path: 'Hidden', label: 'Hidden', control: '#vote-site-hidden', type: 'boolean'}
];
let voteSiteHealthByNode = new Map();
let voteSiteHealthLoadedContext = '';
let voteSiteHealthFlight = null;

function voteSitesHasDraft() {
  const model = voteSitesEditor?.model;
  return Boolean(model && (model.dirty.size || Object.keys(model.addFields || {}).length
    || model.workflow === 'REMOVE'));
}

function voteSitesContext() {
  return JSON.stringify([authenticated, authenticationGeneration, workspace.managementScope,
    [...workspace.selectedTargetIds].map(id => [id, nodeIndex.get(id)?.sessionId || '',
      nodeIndex.get(id)?.online === true, nodeIndex.get(id)?.acceptedCapabilities?.includes('config.files.v1') === true])]);
}

function voteSiteKeys(model) {
  const keys = new Set();
  model.targets.forEach(target => (target.sites || []).forEach(site => keys.add(site.siteKey)));
  return [...keys].sort((left, right) => left.localeCompare(right, undefined, {sensitivity: 'base'}));
}

function voteSiteValueLabel(value) {
  if (value === true) return 'On';
  if (value === false) return 'Off';
  return value == null ? 'Unavailable' : String(value);
}

function voteSiteFieldInput(field, aggregate) {
  const control = document.querySelector(field.control);
  const model = voteSitesEditor.model;
  const dirty = model.dirty.has(field.path);
  const addDraft = model.workflow === 'CREATE_MISSING' && Object.hasOwn(model.addFields, field.path);
  const requested = dirty ? model.dirty.get(field.path) : addDraft ? model.addFields[field.path] : undefined;
  // Missing sites do not erase the value shared by sites that exist. Failed or
  // unsupported reads, however, must never look like a fully-known workspace.
  const same = aggregate.supportedState === 'SAME' && !['ERROR', 'UNSUPPORTED'].includes(aggregate.state);
  const value = requested !== undefined ? requested : same ? aggregate.value : undefined;
  control.title = aggregate.state === 'MISSING' && same
    ? `Same on ${aggregate.targets.filter(target => target.status === 'AVAILABLE').length} present sites; other workspace targets are missing this site.` : '';
  control.dataset.dirty = String(dirty || addDraft);
  control.dataset.mixed = String(!dirty && !addDraft && aggregate.supportedState === 'MIXED');
  control.dataset.partial = String(!dirty && !addDraft && aggregate.state === 'MISSING' && same);
  if (field.type === 'boolean') {
    control.options[0].textContent = aggregate.supportedState === 'MIXED' ? '— Mixed values —'
      : aggregate.state === 'MISSING' ? 'Missing on one or more targets' : aggregate.state;
    control.value = typeof value === 'boolean' ? String(value) : '';
  } else {
    control.value = value === undefined ? '' : String(value);
    control.placeholder = aggregate.supportedState === 'MIXED' ? 'Mixed values'
      : aggregate.state === 'MISSING' ? 'Missing on one or more targets' : aggregate.state === 'SAME' ? '' : aggregate.state;
  }
  control.disabled = voteSitesEditor.state.busy || !aggregate.targets.some(target => target.status === 'AVAILABLE') && !addDraft;
}

function voteSiteStatusForList(model, key) {
  const aggregate = model.aggregate(key);
  const enabled = model.aggregateField('Enabled', key);
  if (aggregate.targets.some(target => target.status === 'ERROR')) return ['Read error', 'error'];
  if (aggregate.targets.some(target => target.status === 'UNSUPPORTED')) return ['Unsupported target', 'warning'];
  if (aggregate.presence === 'PARTIAL') return ['Missing on targets', 'warning'];
  if (Object.values(aggregate.fields).some(field => field.supportedState === 'MIXED')) return ['Mixed', 'warning'];
  if (enabled.supportedState === 'SAME' && enabled.value === false) return ['Disabled', 'neutral'];
  return ['Configured', 'online'];
}

function renderVoteSiteList(model) {
  const list = document.querySelector('#vote-site-list');
  const query = document.querySelector('#vote-site-search').value.trim().toLocaleLowerCase();
  const filter = document.querySelector('#vote-site-filter').value;
  list.replaceChildren();
  const keys = voteSiteKeys(model).filter(key => {
    const aggregate = model.aggregate(key);
    const haystack = [key, ...aggregate.targets.flatMap(target => target.site
      ? [target.site.fields.Name?.value, target.site.fields.ServiceSite?.value] : [])]
      .filter(value => typeof value === 'string').join(' ').toLocaleLowerCase();
    if (query && !haystack.includes(query)) return false;
    const enabled = model.aggregateField('Enabled', key);
    if (filter === 'enabled') return enabled.supportedState === 'SAME' && enabled.value === true;
    if (filter === 'disabled') return enabled.supportedState === 'SAME' && enabled.value === false;
    if (filter === 'mixed') return Object.values(aggregate.fields).some(field => field.supportedState === 'MIXED');
    if (filter === 'partial') return aggregate.presence === 'PARTIAL';
    return true;
  });
  keys.forEach(key => {
    const aggregate = model.aggregate(key);
    const name = model.aggregateField('Name', key);
    const service = model.aggregateField('ServiceSite', key);
    const present = aggregate.targets.filter(target => target.site).length;
    const [status, statusClass] = voteSiteStatusForList(model, key);
    const button = document.createElement('button'); button.type = 'button'; button.className = 'vote-site-list-item';
    button.setAttribute('role', 'option'); button.setAttribute('aria-selected', String(model.selectedSiteKey === key));
    button.append(text(document.createElement('strong'), name.supportedState === 'SAME' && name.value ? name.value : key),
      text(document.createElement('small'), key),
      text(document.createElement('small'), service.supportedState === 'SAME' && service.value ? `Service: ${service.value}` : `ServiceSite: ${service.supportedState.toLowerCase()}`));
    const pills = document.createElement('div'); pills.className = 'pills';
    const state = text(document.createElement('span'), status); state.className = `pill ${statusClass}`;
    pills.append(state, text(document.createElement('span'), `Targets ${present}/${model.targets.size}`));
    const observed = [...voteSiteHealthByNode.values()].flatMap(value => Array.isArray(value?.sites) ? value.sites : [])
      .filter(site => site.siteKey === key).map(site => site.status).filter(Boolean);
    if (observed.length) { const health = text(document.createElement('span'), [...new Set(observed)].join(', ')); health.className = 'pill neutral'; pills.append(health); }
    button.append(pills);
    button.addEventListener('click', () => {
      if (voteSitesHasDraft() && model.selectedSiteKey !== key && !window.confirm('Discard the current Vote Site draft and inspect another site?')) return;
      voteSitesEditor.selectSite(key); renderVoteSites();
    });
    list.append(button);
  });
  if (!keys.length) text(list, voteSiteKeys(model).length ? 'No Vote Sites match this filter.' : 'No configured Vote Sites were returned.');
}

function renderVoteSites() {
  if (!voteSitesEditor) return;
  if (voteSiteHealthLoadedContext && voteSiteHealthLoadedContext !== voteSitesContext()) {
    voteSiteHealthByNode.clear();
    voteSiteHealthLoadedContext = '';
    text(document.querySelector('#vote-sites-detected'), 'Workspace changed; loading fresh observations when available.');
  }
  const model = voteSitesEditor.model;
  const state = voteSitesEditor.state;
  document.querySelector('#vote-sites-panel').setAttribute('aria-busy', String(Boolean(state.busy)));
  text(document.querySelector('#vote-sites-status'), state.error || state.message || (state.busy ? 'Loading / processing…' : 'Only explicit site changes will be proposed.'));
  const targets = document.querySelector('#vote-sites-read-targets'); targets.replaceChildren();
  model.targets.forEach(target => {
    const item = text(document.createElement('div'), `${nodeIndex.get(target.nodeId)?.displayName || target.nodeId} · ${target.status === 'AVAILABLE' ? `${target.sites.length} sites loaded` : target.code || target.status}${target.message ? ` · ${target.message}` : ''}`);
    item.className = `settings-target ${target.status === 'AVAILABLE' ? 'loaded' : 'warning'}`; targets.append(item);
  });
  renderVoteSiteList(model);
  const key = model.selectedSiteKey;
  const editor = document.querySelector('#vote-site-form');
  document.querySelector('#vote-site-empty').hidden = Boolean(key);
  editor.hidden = !key;
  if (!key) {
    document.querySelector('#vote-site-preview').disabled = true;
    document.querySelector('#vote-site-apply').disabled = true;
    return;
  }
  const aggregate = model.aggregate(key);
  const present = aggregate.targets.filter(target => target.site).length;
  const existingSites = aggregate.targets.map(target => target.site).filter(Boolean);
  text(document.querySelector('#vote-site-title'), model.workflow === 'REMOVE' ? `Remove ${key}` : key);
  text(document.querySelector('#vote-site-key'), key);
  text(document.querySelector('#vote-site-presence'), `${aggregate.presence} · present on ${present} of ${model.targets.size} selected targets`);
  const [status, statusClass] = voteSiteStatusForList(model, key);
  text(document.querySelector('#vote-site-state'), status); document.querySelector('#vote-site-state').className = `pill ${statusClass}`;
  const rewardStates = existingSites.map(site => site.rewardsConfigured);
  text(document.querySelector('#vote-site-rewards'), !rewardStates.length ? 'Unavailable'
    : rewardStates.every(Boolean) ? 'Configured on all present targets'
      : rewardStates.some(Boolean) ? 'Mixed · configured on some targets' : 'Not configured');
  VOTE_SITE_FIELDS.forEach(field => voteSiteFieldInput(field, model.aggregateField(field.path, key)));
  const details = document.querySelector('#vote-site-target-values'); details.replaceChildren();
  aggregate.targets.forEach(target => {
    const values = target.site ? VOTE_SITE_FIELDS.map(field => {
      const snapshot = target.site.fields[field.path];
      return `${field.label}: ${snapshot?.status === 'AVAILABLE' ? voteSiteValueLabel(snapshot.value) : snapshot?.status || 'MISSING'}`;
    }).join(' · ') : target.status === 'MISSING' ? 'Site missing' : `${target.status}${target.message ? ` · ${target.message}` : ''}`;
    details.append(text(document.createElement('p'), `${nodeIndex.get(target.nodeId)?.displayName || target.nodeId}: ${values}`));
  });
  const partialLabel = document.querySelector('#vote-site-partial-choice-label');
  partialLabel.hidden = aggregate.presence !== 'PARTIAL' || model.workflow === 'REMOVE';
  document.querySelector('#vote-site-partial-choice').value = model.partialPolicy || 'existing';
  document.querySelector('#vote-site-remove').disabled = state.busy || !existingSites.some(site => site.editable);
  document.querySelector('#vote-site-reset').disabled = state.busy || !voteSitesHasDraft();
  const plans = model.plans(); const changed = plans.filter(plan => ['ADD', 'EDIT', 'REMOVE'].includes(plan.operation));
  const summary = document.querySelector('#vote-site-summary'); summary.replaceChildren(
    text(document.createElement('p'), `Target servers: ${model.targets.size}`),
    text(document.createElement('p'), `Site: ${key}`),
    text(document.createElement('p'), `Explicit dirty properties: ${model.dirty.size}`),
    text(document.createElement('p'), `Changed targets: ${changed.length}`),
    text(document.createElement('p'), `Affected file: ${changed.length ? 'VoteSites.yml' : 'none'}`),
    text(document.createElement('p'), `Runtime impact: ${changed.length ? 'VotingPlugin configuration reload required' : 'No runtime action required'}`),
    text(document.createElement('p'), `Preview: ${model.previewCurrent() ? 'Ready' : state.previewState}`));
  plans.forEach(plan => {
    const changes = plan.operation === 'EDIT' ? Object.entries(plan.changes).map(([path, value]) => `${path} → ${voteSiteValueLabel(value)}`).join(', ')
      : plan.operation === 'ADD' ? 'Site will be created with the reviewed fields'
        : plan.operation === 'REMOVE' ? `VoteSites.${key} will be removed` : plan.skipped.length ? plan.skipped.map(item => item.field ? `${item.field} (${item.status})` : item.status).join(', ') : 'No change';
    summary.append(text(document.createElement('p'), `${nodeIndex.get(plan.nodeId)?.displayName || plan.nodeId}: ${plan.operation} · ${changes}`));
  });
  const exclusions = plans.some(plan => plan.operation === 'SKIP' || plan.skipped.length);
  document.querySelector('#vote-site-ack-label').hidden = !exclusions;
  document.querySelector('#vote-site-ack').disabled = state.busy;
  document.querySelector('#vote-sites-read').disabled = state.busy || !model.targets.size;
  document.querySelector('#vote-sites-retry').disabled = state.busy || ![...model.targets.values()].some(target => target.status === 'ERROR');
  document.querySelector('#vote-site-preview').disabled = state.busy || !changed.length || model.exceedsChangedTargetLimit(8);
  document.querySelector('#vote-site-apply').disabled = state.busy || !model.previewCurrent() || (exclusions && !document.querySelector('#vote-site-ack').checked);
  const previewResults = document.querySelector('#vote-site-preview-results'); previewResults.replaceChildren();
  const previewItems = model.previewCurrent()?.items || state.previewItems || [];
  if (!previewItems.length) text(previewResults, state.previewState === 'Stale' ? 'Stale — read/re-preview required.' : 'Not previewed.');
  previewItems.forEach(item => {
    const plan = plans.find(candidate => candidate.nodeId === item.nodeId);
    const section = document.createElement('section'); section.className = 'target-operation';
    section.append(text(document.createElement('h4'), nodeIndex.get(item.nodeId)?.displayName || item.nodeId),
      text(document.createElement('p'), `${item.status}${item.operationId ? ` · operation ${item.operationId}` : ''}${item.message ? ` · ${item.message}` : ''}`));
    if (plan?.operation === 'EDIT') Object.entries(plan.changes).forEach(([path, value]) => {
      const before = model.targets.get(item.nodeId)?.sites.find(site => site.siteKey === key)?.fields[path]?.value;
      section.append(text(document.createElement('p'), `VoteSites.yml → VoteSites.${key}.${path}: ${voteSiteValueLabel(before)} → ${voteSiteValueLabel(value)}`));
    });
    if (plan?.operation === 'ADD') VOTE_SITE_FIELDS.forEach(field => section.append(text(document.createElement('p'), `VoteSites.${key}.${field.path}: ${voteSiteValueLabel(plan.fields[field.path])}`)));
    if (plan?.operation === 'REMOVE') section.append(text(document.createElement('p'), `VoteSites.yml → remove VoteSites.${key}`));
    (item.skipped || []).forEach(skipped => section.append(text(document.createElement('p'), `Excluded: ${skipped.field || 'site'} (${skipped.status})`)));
    previewResults.append(section);
  });
  const results = document.querySelector('#vote-site-results'); results.replaceChildren();
  if (!model.results.size) text(results, 'No apply performed.');
  model.results.forEach((data, nodeId) => {
    const result = data.result || data.operation?.results?.[nodeId] || data;
    const section = document.createElement('section'); section.className = 'target-operation';
    section.append(text(document.createElement('h4'), nodeIndex.get(nodeId)?.displayName || nodeId),
      text(document.createElement('p'), `${result.success ? '✓ written' : `✗ ${result.code || data.status || 'not applied'}: ${result.message || data.message || ''}`} · ${result.reloaded ? '✓ reloaded' : 'reload not confirmed'}${result.rolledBack ? ' · ✓ automatic local rollback' : ''}${data.operation?.operationId ? ` · operation ${data.operation.operationId}` : ''}`),
      text(document.createElement('p'), data.confirmed ? '✓ Confirmed by a fresh VoteSites.yml read' : '✗ Requested site state was not confirmed; do not assume it is active.'));
    results.append(section);
  });
}

function voteSiteAddValues() {
  return {Enabled: document.querySelector('#vote-site-add-enabled').checked,
    Name: document.querySelector('#vote-site-add-name').value,
    ServiceSite: document.querySelector('#vote-site-add-service').value,
    VoteURL: document.querySelector('#vote-site-add-url').value,
    VoteDelay: document.querySelector('#vote-site-add-delay').value,
    Priority: ControlVoteSites.parseIntegerField('Priority', document.querySelector('#vote-site-add-priority').value),
    Hidden: document.querySelector('#vote-site-add-hidden').checked,
    'DisplayItem.Material': document.querySelector('#vote-site-add-material').value,
    'DisplayItem.Amount': ControlVoteSites.parseIntegerField('DisplayItem.Amount', document.querySelector('#vote-site-add-amount').value)};
}

function updateVoteSiteAddConflicts() {
  const key = document.querySelector('#vote-site-add-key').value.trim();
  const caseConflict = voteSiteKeys(voteSitesEditor.model).find(siteKey => siteKey !== key && siteKey.toLowerCase() === key.toLowerCase());
  const aggregate = key && voteSitesEditor ? voteSitesEditor.model.aggregate(key) : null;
  const existing = aggregate ? aggregate.targets.filter(target => target.site).map(target => nodeIndex.get(target.nodeId)?.displayName || target.nodeId) : [];
  const missing = aggregate ? aggregate.targets.filter(target => target.status === 'MISSING' && !target.site).map(target => nodeIndex.get(target.nodeId)?.displayName || target.nodeId) : [];
  text(document.querySelector('#vote-site-add-conflicts'), caseConflict ? `A site named ${caseConflict} already exists; site keys differing only by case are ambiguous.`
    : !key ? 'Enter a key to check the current workspace inventory.'
    : `Existing: ${existing.join(', ') || 'none'} · Missing: ${missing.join(', ') || 'none'}${aggregate?.targets.some(target => !['AVAILABLE', 'MISSING'].includes(target.status)) ? ' · Some targets are unavailable or unsupported.' : ''}`);
  document.querySelector('#vote-site-add-existing-label').hidden = !existing.length;
  if (!existing.length) document.querySelector('#vote-site-add-existing').value = 'missing';
}

function openVoteSiteAdd(prefill = {}) {
  const dialog = document.querySelector('#vote-site-add-dialog');
  document.querySelector('#vote-site-add-form').reset();
  document.querySelector('#vote-site-add-key').value = prefill.siteKey || '';
  document.querySelector('#vote-site-add-name').value = prefill.Name || prefill.siteKey || '';
  document.querySelector('#vote-site-add-service').value = prefill.ServiceSite || '';
  document.querySelector('#vote-site-add-url').value = prefill.VoteURL || '';
  document.querySelector('#vote-site-add-delay').value = prefill.VoteDelay || '24h';
  document.querySelector('#vote-site-add-priority').value = prefill.Priority ?? 5;
  document.querySelector('#vote-site-add-material').value = prefill['DisplayItem.Material'] || 'DIAMOND';
  document.querySelector('#vote-site-add-amount').value = prefill['DisplayItem.Amount'] ?? 1;
  document.querySelector('#vote-site-add-enabled').checked = prefill.Enabled !== false;
  document.querySelector('#vote-site-add-hidden').checked = prefill.Hidden === true;
  document.querySelector('#vote-site-add-existing').value = 'cancel';
  updateVoteSiteAddConflicts(); dialog.showModal();
}

async function refreshVoteSiteObservations() {
  const context = voteSitesContext();
  if (voteSiteHealthFlight && voteSiteHealthFlight.context === context) return voteSiteHealthFlight.promise;
  const eligible = [...workspace.selectedTargetIds].map(id => nodeIndex.get(id)).filter(node => node?.online
    && node.acceptedCapabilities?.includes('data.inspect.v1')).slice(0, 8);
  const container = document.querySelector('#vote-sites-detected');
  if (!eligible.length) { text(container, 'No selected backend currently supports read-only vote-site health inspection.'); return; }
  text(container, 'Reading observed service names and configured-site health…');
  const flight = {context, promise: null};
  flight.promise = Promise.all(eligible.map(async node => {
    try { return [node.nodeId, normalizeDashboardVoteSiteHealth((await runInspectionOnNode(node, 'vote-site-health', {days: '30'}, {manageBusy: false, contextCurrent: () => voteSitesContext() === context})).result).result]; }
    catch (error) { return [node.nodeId, {error: error.message || 'Inspection failed'}]; }
  })).then(entries => {
    if (voteSitesContext() !== context) return;
    voteSiteHealthByNode = new Map(entries); voteSiteHealthLoadedContext = context; container.replaceChildren();
    entries.forEach(([nodeId, result]) => {
      const section = document.createElement('section'); section.className = 'target-operation';
      section.append(text(document.createElement('h4'), nodeIndex.get(nodeId)?.displayName || nodeId));
      if (result.error) section.append(text(document.createElement('p'), `Read unavailable: ${result.error}`));
      else {
        section.append(text(document.createElement('p'), `${Array.isArray(result.sites) ? result.sites.length : 0} configured site health records. “No recent votes” is not an external website failure.`));
        (result.detectedUnconfiguredServices || []).forEach(service => {
          const row = document.createElement('div'); row.className = 'detected-actions';
          row.append(text(document.createElement('span'), `${service} · observed but not configured`));
          const create = text(document.createElement('button'), 'Create Vote Site'); create.type = 'button'; create.className = 'secondary compact';
          create.addEventListener('click', () => openVoteSiteAdd({siteKey: String(service).replace(/[.\s]+/g, '_').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64), Name: String(service).slice(0, 200), ServiceSite: String(service).slice(0, 2048)}));
          row.append(create); section.append(row);
        });
      }
      container.append(section);
    });
    if (workspace.selectedTargetIds.size > eligible.length) container.append(text(document.createElement('p'), `Health inspection is bounded to 8 capable targets per refresh; configuration inventory still includes all ${workspace.selectedTargetIds.size} workspace targets.`));
    renderVoteSiteList(voteSitesEditor.model);
  }).finally(() => { if (voteSiteHealthFlight === flight) voteSiteHealthFlight = null; });
  voteSiteHealthFlight = flight; return flight.promise;
}
const GENERAL_SETTING_FIELDS = [
  {path: 'ProcessRewards', label: 'Process rewards', category: 'Voting & rewards', defaultValue: true, description: 'Allow VotingPlugin to process vote rewards.'},
  {path: 'ExtraAllSitesCheck', label: 'Extra all-sites check', category: 'Voting & rewards', defaultValue: false, description: 'Enable the additional duplicate all-sites reward check.'},
  {path: 'CountFakeVotes', label: 'Count fake votes', category: 'Voting & rewards', defaultValue: true, description: 'Include fake votes in vote totals.'},
  {path: 'AutoCreateVoteSites', label: 'Auto-create vote sites', category: 'Vote sites', defaultValue: true, description: 'Automatically create a site when an unknown service sends a vote.'},
  {path: 'DisableNoServiceSiteMessage', label: 'Hide unknown service-site warnings', category: 'Vote sites', defaultValue: false, description: 'Suppress the missing service-site warning.'},
  {path: 'ExtraVoteShopCheck', label: 'Extra vote shop check', category: 'Vote shop', defaultValue: true, description: 'Apply the additional vote shop purchase check.'},
  {path: 'UseVoteGUIMainCommand', label: 'Open vote GUI from main command', category: 'Interface', defaultValue: false, description: 'Use the vote GUI for the main vote command.'},
  {path: 'CloseInventoryOnVote', label: 'Close inventory when voting', category: 'Interface', defaultValue: true, description: 'Close the open vote inventory after a vote.'},
  {path: 'DisableUpdateChecking', label: 'Disable update checking', category: 'Maintenance', defaultValue: false, restart: true, description: 'Disable update checks. A backend restart is required to reconcile the update-check scheduler lifecycle.'}
];

function generalSettingsContext() {
  return JSON.stringify([authenticated, authenticationGeneration, workspace.managementScope,
    [...workspace.selectedTargetIds].map(id => [id, nodeIndex.get(id)?.sessionId || '',
      nodeIndex.get(id)?.online === true, nodeIndex.get(id)?.acceptedCapabilities?.includes('config.files.v1') === true])]);
}

function settingValueLabel(value) { return value === true ? 'On' : value === false ? 'Off' : 'Unavailable'; }

function renderGeneralSettings() {
  if (!settingsEditor) return;
  const model = settingsEditor.model;
  const state = settingsEditor.state;
  const plans = model.plans();
  const preview = model.previewCurrent();
  const container = document.querySelector('#general-settings-fields');
  // Keep focused controls stable during refreshes and status updates.
  if (!container.children.length) {
    [...new Set(GENERAL_SETTING_FIELDS.map(field => field.category))].forEach(category => {
      const card = document.createElement('section');
      card.className = 'card stacked-card';
      card.append(text(document.createElement('h3'), category));
      GENERAL_SETTING_FIELDS.filter(field => field.category === category).forEach(field => {
        const row = document.createElement('div'); row.className = 'general-setting-row';
        const label = text(document.createElement('label'), field.label);
        label.htmlFor = `general-setting-${field.path}`;
        label.append(text(document.createElement('small'), `${field.description} Config.yml → ${field.path}. Documented default: ${settingValueLabel(field.defaultValue)} (not substituted for missing values).`));
        const select = document.createElement('select'); select.id = label.htmlFor;
        ['', 'true', 'false'].forEach(value => {
          const option = document.createElement('option'); option.value = value;
          option.textContent = value ? value === 'true' ? 'On' : 'Off' : 'Read required';
          option.disabled = !value; select.append(option);
        });
        select.addEventListener('change', () => {
          if (select.value === 'true' || select.value === 'false') {
            document.querySelector('#general-settings-ack').checked = false;
            settingsEditor.edit(field.path, select.value === 'true');
          }
        });
        const reset = text(document.createElement('button'), 'Reset'); reset.type = 'button'; reset.className = 'secondary compact';
        reset.id = `general-setting-reset-${field.path}`;
        reset.addEventListener('click', () => { document.querySelector('#general-settings-ack').checked = false; settingsEditor.reset(field.path); });
        const details = document.createElement('details');
        details.append(text(document.createElement('summary'), 'Per-server values'));
        const values = document.createElement('div'); values.id = `general-setting-values-${field.path}`; details.append(values);
        row.append(label, select, reset, details); card.append(row);
      });
      container.append(card);
    });
  }
  GENERAL_SETTING_FIELDS.forEach(field => {
    const aggregate = model.aggregate(field.path);
    const dirty = model.dirty.has(field.path);
    const select = document.querySelector(`#general-setting-${field.path}`);
    select.options[0].textContent = aggregate.state === 'SAME' ? 'Current value'
      : aggregate.state === 'MIXED' ? '— Mixed values —' : `${aggregate.state} · ${aggregate.supportedState === 'SAME' ? `supported targets ${settingValueLabel(aggregate.value)}` : aggregate.supportedState.toLowerCase()}`;
    select.value = dirty ? String(model.dirty.get(field.path)) : aggregate.state === 'SAME' ? String(aggregate.value) : '';
    select.disabled = state.busy || !aggregate.targets.some(target => target.status === 'AVAILABLE');
    select.setAttribute('aria-describedby', `general-setting-values-${field.path}`);
    const reset = document.querySelector(`#general-setting-reset-${field.path}`); reset.disabled = state.busy || !dirty;
    const values = document.querySelector(`#general-setting-values-${field.path}`);
    values.replaceChildren(...aggregate.targets.map(target => text(document.createElement('p'),
      `${nodeIndex.get(target.id)?.displayName || target.id}: ${target.status === 'AVAILABLE' ? settingValueLabel(target.value) : target.status}${dirty ? ` · requested ${settingValueLabel(model.dirty.get(field.path))}` : ''}`)));
  });
  text(document.querySelector('#general-settings-status'), state.error || state.message || (state.busy ? 'Loading / processing…' : 'Only explicit changes will be proposed.'));
  document.querySelector('#general-settings-panel').setAttribute('aria-busy', String(Boolean(state.busy)));
  const targetList = document.querySelector('#general-settings-targets');
  targetList.replaceChildren(...[...model.targets.values()].map(target => {
    const item = text(document.createElement('div'), `${nodeIndex.get(target.id)?.displayName || target.id} · ${target.status === 'AVAILABLE' ? 'Loaded' : target.status}${target.code ? ` · ${target.code}` : ''}${target.message ? ` · ${target.message}` : ''}`);
    item.className = `settings-target ${target.status === 'AVAILABLE' ? 'loaded' : 'warning'}`;
    const chip = [...document.querySelectorAll('#scope-chips button')].find(button => button.textContent.startsWith(nodeIndex.get(target.id)?.displayName || target.id));
    if (chip) {
      chip.title = `Configuration: ${target.status}`;
      text(chip, `${nodeIndex.get(target.id)?.displayName || target.id} · Config ${target.status === 'AVAILABLE' ? 'loaded' : target.code || target.status} ×`);
    }
    return item;
  }));
  const summary = document.querySelector('#general-settings-summary');
  summary.replaceChildren(text(document.createElement('p'), `Target servers: ${model.targets.size}`),
    text(document.createElement('p'), `Explicit dirty settings: ${model.dirty.size}`),
    text(document.createElement('p'), `Affected files: ${model.dirty.size ? 'Config.yml' : 'none'}`),
    text(document.createElement('p'), `Preview: ${preview ? 'Ready' : state.previewState || 'Not previewed'}`));
  model.dirty.forEach((value, path) => {
    const field = GENERAL_SETTING_FIELDS.find(item => item.path === path);
    const aggregate = model.aggregate(path);
    summary.append(text(document.createElement('p'), `${field?.label || path}: ${aggregate.state === 'SAME' ? settingValueLabel(aggregate.value) : aggregate.state} → ${settingValueLabel(value)}`));
  });
  plans.forEach(plan => {
    const keys = Object.keys(plan.overrides);
    const impact = keys.some(path => GENERAL_SETTING_FIELDS.find(field => field.path === path)?.restart)
      ? 'Config reload + full backend restart required (restart is not performed by this editor)' : keys.length ? 'VotingPlugin configuration reload required' : 'No runtime action required';
    summary.append(text(document.createElement('p'), `${plan.id}: ${impact}${plan.skipped.length ? ` · excluded: ${plan.skipped.map(item => `${item.field} (${item.status})`).join(', ')}` : ''}`));
  });
  const exclusions = plans.some(plan => plan.skipped.length);
  document.querySelector('#general-settings-ack-label').hidden = !exclusions;
  document.querySelector('#general-settings-ack').disabled = state.busy;
  document.querySelector('#general-settings-read').disabled = state.busy || !model.targets.size;
  document.querySelector('#general-settings-retry').disabled = state.busy || ![...model.targets.values()].some(target => target.status === 'ERROR');
  document.querySelector('#general-settings-reset').disabled = state.busy || !model.dirty.size;
  document.querySelector('#general-settings-preview').disabled = state.busy || !plans.some(plan => Object.keys(plan.overrides).length);
  document.querySelector('#general-settings-apply').disabled = state.busy || !preview || (exclusions && !document.querySelector('#general-settings-ack').checked);
  const previewResults = document.querySelector('#general-settings-preview-results');
  previewResults.replaceChildren();
  const previewItems = preview?.items || state.previewItems || [];
  if (!previewItems.length) text(previewResults, state.previewState === 'Stale' ? 'Stale — read/re-preview required.' : 'Not previewed.');
  else previewItems.forEach(item => {
    const section = document.createElement('section'); section.className = 'target-operation';
    section.append(text(document.createElement('h4'), item.id));
    const operation = item.operation;
    const result = operation?.results?.[item.id];
    section.append(text(document.createElement('p'), result ? `${result.success ? 'Config.yml · preview ready' : `${result.code} · ${result.message}`}${operation?.operationId ? ` · operation ${operation.operationId}` : ''}` : `${item.status || 'Unchanged / excluded'}${item.message ? ` · ${item.message}` : ''}`));
    (item.skipped || []).forEach(skipped => section.append(text(document.createElement('p'), `${skipped.field}: excluded (${skipped.status})`)));
    const plan = plans.find(plan => plan.id === item.id);
    if (plan) Object.entries(plan.overrides).forEach(([path, value]) => section.append(text(document.createElement('p'),
      `Config.yml → ${path}: ${settingValueLabel(model.targets.get(item.id)?.fields[path]?.value)} → ${settingValueLabel(value)}`)));
    (result?.changes || []).forEach(change => section.append(text(document.createElement('p'), change)));
    previewResults.append(section);
  });
  const results = document.querySelector('#general-settings-results'); results.replaceChildren();
  if (!model.results.size) text(results, 'No apply performed.');
  model.results.forEach((data, id) => {
    const result = data.result || data.operation?.results?.[id] || data;
    const current = model.targets.get(id);
    const section = document.createElement('section'); section.className = 'target-operation';
    if (data.status === 'UNCHANGED' || data.status === 'EXCLUDED') {
      section.append(text(document.createElement('h4'), id), text(document.createElement('p'), data.status === 'UNCHANGED' ? '— unchanged' : '— excluded / unsupported or unavailable; not applied'));
      results.append(section); return;
    }
    section.append(text(document.createElement('h4'), id), text(document.createElement('p'),
      `${result.success ? '✓ written' : `✗ ${result.code || data.status || 'not applied'}: ${result.message || data.message || ''}`} · ${result.reloaded ? '✓ reloaded' : 'reload not confirmed'}${result.rolledBack ? ' · ✓ automatic local rollback' : ''}${data.operation?.operationId ? ` · operation ${data.operation.operationId}` : ''}`),
      text(document.createElement('p'), data.confirmed === true ? '✓ Confirmed requested persisted configuration after apply (not proof of a backend restart)'
        : current?.status === 'AVAILABLE' ? 'Current configuration re-read; requested values are not confirmed as successfully applied.' : '✗ Confirmed read unavailable; do not assume requested values are active.'));
    results.append(section);
  });
}

settingsEditor = ControlGeneralSettings.create({
  targets: () => workspace.managementScope === 'GLOBAL' || !authenticated ? [] : [...workspace.selectedTargetIds].map(id => {
    const node = nodeIndex.get(id);
    return {id, sessionId: node?.sessionId || '', online: node?.online === true,
      supported: isBackend(node) && node?.acceptedCapabilities?.includes('config.files.v1') === true};
  }),
  context: generalSettingsContext,
  active: () => authenticated && tabFromHash() === 'general-settings',
  operation: (path, body) => startConfigurationOperation(path, body, document.querySelector('#general-settings-status')),
  request: (path, body) => authorized(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)}),
  changed: renderGeneralSettings
});
document.querySelector('#general-settings-read').addEventListener('click', () => void settingsEditor.read(true));
document.querySelector('#general-settings-retry').addEventListener('click', () => void settingsEditor.read(true, true));
document.querySelector('#general-settings-preview').addEventListener('click', () => { document.querySelector('#general-settings-ack').checked = false; void settingsEditor.preview(); });
document.querySelector('#general-settings-apply').addEventListener('click', () => void settingsEditor.apply(document.querySelector('#general-settings-ack').checked));
document.querySelector('#general-settings-reset').addEventListener('click', () => { document.querySelector('#general-settings-ack').checked = false; settingsEditor.resetAll(); });
document.querySelector('#general-settings-ack').addEventListener('change', renderGeneralSettings);

voteSitesEditor = ControlVoteSitesEditor.create({
  targets: () => workspace.managementScope === 'GLOBAL' || !authenticated ? [] : [...workspace.selectedTargetIds].map(id => {
    const node = nodeIndex.get(id);
    return {nodeId: id, sessionId: node?.sessionId || '', online: node?.online === true,
      supported: isBackend(node) && node?.acceptedCapabilities?.includes('config.files.v1') === true};
  }),
  context: voteSitesContext,
  active: () => authenticated && tabFromHash() === 'vote-sites',
  operation: (path, body) => startConfigurationOperation(path, body, document.querySelector('#vote-sites-status')),
  request: (path, body) => authorized(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)}),
  changed: renderVoteSites
});
document.querySelector('#vote-sites-read').addEventListener('click', () => void voteSitesEditor.read(true));
document.querySelector('#vote-sites-retry').addEventListener('click', () => void voteSitesEditor.read(true, true));
document.querySelector('#vote-site-search').addEventListener('input', () => renderVoteSiteList(voteSitesEditor.model));
document.querySelector('#vote-site-filter').addEventListener('change', () => renderVoteSiteList(voteSitesEditor.model));
document.querySelector('#vote-site-add').addEventListener('click', () => openVoteSiteAdd());
document.querySelector('#vote-site-add-key').addEventListener('input', updateVoteSiteAddConflicts);
document.querySelector('#vote-site-add-cancel').addEventListener('click', () => document.querySelector('#vote-site-add-dialog').close());
document.querySelector('#vote-site-add-close').addEventListener('click', () => document.querySelector('#vote-site-add-dialog').close());
document.querySelector('#vote-site-add-form').addEventListener('submit', event => {
  event.preventDefault();
  const dialog = document.querySelector('#vote-site-add-dialog');
  const key = document.querySelector('#vote-site-add-key').value.trim();
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(key)) { text(document.querySelector('#vote-site-add-conflicts'), 'Use 1–64 letters, digits, underscores or hyphens; dots and spaces are unsafe site keys.'); return; }
  if (voteSiteKeys(voteSitesEditor.model).some(siteKey => siteKey !== key && siteKey.toLowerCase() === key.toLowerCase())) {
    text(document.querySelector('#vote-site-add-conflicts'), 'A site key differing only by letter case exists. Choose another key.'); return;
  }
  const inventory = voteSitesEditor.model.aggregate(key);
  const existing = inventory.targets.filter(target => target.site);
  const missing = inventory.targets.filter(target => target.status === 'MISSING' && !target.site);
  const policy = existing.length ? document.querySelector('#vote-site-add-existing').value : 'missing';
  if ((existing.length && policy === 'cancel') || !missing.length && policy === 'missing'
      || existing.length && missing.length && !window.confirm(`Site ${key} exists on ${existing.length} target(s) and is missing on ${missing.length}. Explicit choice: ${policy}. Continue to preview only?`)) return;
  if (!missing.length && policy !== 'existing' && policy !== 'both') return;
  const fields = voteSiteAddValues();
  if (fields.Priority === null || fields['DisplayItem.Amount'] === null) {
    text(document.querySelector('#vote-site-add-conflicts'), 'Priority must be a signed 32-bit integer and DisplayItem.Amount must be 1–64.'); return;
  }
  voteSitesEditor.beginAdd(key, fields, policy);
  document.querySelector('#vote-site-partial-choice').value = policy === 'cancel' ? 'existing' : policy;
  document.querySelector('#vote-site-ack').checked = false;
  dialog.close(); renderVoteSites();
});
document.querySelector('#vote-site-partial-choice').addEventListener('change', event => {
  const policy = event.target.value;
  if (policy !== 'existing' && !Object.keys(voteSitesEditor.model.addFields || {}).length) {
    openVoteSiteAdd({siteKey: voteSitesEditor.model.selectedSiteKey}); event.target.value = 'existing'; return;
  }
  document.querySelector('#vote-site-ack').checked = false;
  voteSitesEditor.setPartialPolicy(policy);
});
VOTE_SITE_FIELDS.forEach(field => {
  const control = document.querySelector(field.control);
  control.addEventListener('change', () => {
    let value = control.value;
    if (field.type === 'boolean') {
      if (value !== 'true' && value !== 'false') return;
      value = value === 'true';
    } else if (field.type === 'integer') {
      value = ControlVoteSites.parseIntegerField(field.path, value);
      if (value === null) {
        voteSiteFieldInput(field, voteSitesEditor.model.aggregateField(field.path));
        text(document.querySelector('#vote-sites-status'), `${field.label} is outside its supported integer range; the last staged value was retained.`); return;
      }
    }
    document.querySelector('#vote-site-ack').checked = false;
    voteSitesEditor.edit(field.path, value);
  });
});
document.querySelector('#vote-site-reset').addEventListener('click', () => { document.querySelector('#vote-site-ack').checked = false; voteSitesEditor.cancelDraft(); });
document.querySelector('#vote-site-remove').addEventListener('click', () => {
  const key = voteSitesEditor.model.selectedSiteKey;
  const present = voteSitesEditor.model.aggregate(key).targets.filter(target => target.site).map(target => target.nodeId);
  if (!present.length || !window.confirm(`Prepare removal of VoteSites.${key} on: ${present.join(', ')}? This also removes that site's inline Rewards on affected targets. Other sites and separate reward files are not touched. No configuration changes occur until an exact preview is approved and applied.`)) return;
  document.querySelector('#vote-site-ack').checked = false;
  voteSitesEditor.remove(key);
});
document.querySelector('#vote-site-preview').addEventListener('click', () => { document.querySelector('#vote-site-ack').checked = false; void voteSitesEditor.preview(); });
document.querySelector('#vote-site-apply').addEventListener('click', () => void voteSitesEditor.apply(document.querySelector('#vote-site-ack').checked));
document.querySelector('#vote-site-ack').addEventListener('change', renderVoteSites);
document.querySelector('#vote-site-open-yaml').addEventListener('click', () => {
  if (configurationFile.value !== 'VoteSites.yml') {
    configurationFile.value = 'VoteSites.yml';
    configurationFile.dispatchEvent(new Event('input'));
  }
  setActiveTab('configurations', true); setConfigView('yaml');
});
document.querySelector('#vote-site-edit-rewards').addEventListener('click', () => {
  const key = voteSitesEditor.model.selectedSiteKey;
  openWorkspace('rewards');
  rewardsEditor?.select('VoteSites.yml', `VoteSites.${key}.Rewards`);
  renderRewards();
});

function rewardsContext() {
  return JSON.stringify({generation: authenticationGeneration, scope: workspace.managementScope,
    targets: [...workspace.selectedTargetIds].map(id => [id, nodeIndex.get(id)?.sessionId || ''])});
}

async function inspectNamedRewardFiles(target) {
  const captured = rewardsContext();
  const sessionId = target.sessionId;
  const request = {nodeId: target.nodeId, query: {kind: 'reward-file-inventory', filters: {}}};
  let inspection = await authorized('/api/v1/inspections', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)
  });
  const deadline = Date.now() + 30_000;
  while (inspection.state === 'RUNNING') {
    if (Date.now() >= deadline) throw new Error('Named reward inventory is still running; retry later');
    await new Promise(resolve => window.setTimeout(resolve, 250));
    inspection = await authorized(`/api/v1/inspections/${inspection.inspectionId}`);
  }
  if (captured !== rewardsContext() || nodeIndex.get(target.nodeId)?.sessionId !== sessionId) {
    throw new Error('Target reconnected or workspace changed; read again');
  }
  if (inspection.state !== 'SUCCEEDED' || inspection.result?.success !== true) {
    throw new Error(inspection.result?.message || inspection.result?.code || 'Named reward inventory failed');
  }
  let envelope = inspection.result.data;
  if (typeof envelope === 'string') envelope = JSON.parse(envelope);
  if (envelope?.schemaVersion !== 1 || envelope.kind !== 'reward-file-inventory'
      || !Array.isArray(envelope.result?.files)) throw new Error('Named reward inventory schema is unsupported');
  return envelope.result;
}

function rewardText(value) {
  if (value === undefined) return 'Missing';
  if (Array.isArray(value)) return value.length ? value.join(' · ') : 'Empty list';
  return String(value);
}

function renderRewards() {
  if (!rewardsEditor) return;
  const state = rewardsEditor.state;
  const selected = rewardsEditor.selected;
  const targets = rewardsEditor.targets();
  const targetBox = document.querySelector('#rewards-targets'); targetBox.replaceChildren();
  targets.forEach(target => {
    const files = rewardsEditor.files().map(file => rewardsEditor.record(target.nodeId, file));
    const loaded = files.filter(item => item?.status === 'AVAILABLE').length;
    const failed = files.filter(item => item?.status === 'ERROR').length;
    const named = target.rewardFilesSupported ? '' : ' · named files unsupported';
    targetBox.append(text(document.createElement('span'), `${nodeIndex.get(target.nodeId)?.displayName || target.nodeId}: ${loaded}/${files.length} files loaded${failed ? ` · ${failed} failed` : ''}${named}`));
  });
  text(document.querySelector('#rewards-status'), state.error || state.message || (state.busy ? 'Reading…' : ''));
  const list = document.querySelector('#rewards-scope-list'); list.replaceChildren();
  const search = document.querySelector('#rewards-search').value.trim().toLowerCase();
  const scopes = rewardsEditor.scopes().filter(scope => `${scope.fileName} ${scope.path}`.toLowerCase().includes(search));
  scopes.forEach(scope => {
    const button = document.createElement('button'); button.type = 'button';
    button.className = 'vote-site-list-item'; button.setAttribute('role', 'option');
    button.setAttribute('aria-selected', String(scope.fileName === selected.fileName && scope.path === selected.rewardPath));
    const present = targets.filter(target => rewardsEditor.record(target.nodeId, scope.fileName)?.scopes?.some(item => item.path === scope.path && item.status === 'PRESENT')).length;
    button.append(text(document.createElement('strong'), scope.path), text(document.createElement('small'), `${scope.fileName} · ${present}/${targets.length} configured`));
    button.addEventListener('click', () => { rewardsEditor.select(scope.fileName, scope.path); renderRewards(); void rewardsEditor.read(false); });
    list.append(button);
  });
  if (!scopes.length) text(list, state.busy ? 'Reading reward scopes…' : 'No reward scopes match, or no successful reads are available.');
  const hasSelection = Boolean(selected.rewardPath);
  document.querySelector('#rewards-empty').hidden = hasSelection;
  document.querySelector('#rewards-detail').hidden = !hasSelection;
  if (hasSelection) {
    text(document.querySelector('#rewards-title'), selected.rewardPath === '$'
      ? selected.fileName.slice('Rewards/'.length) : selected.rewardPath.split('.').at(-2) || selected.rewardPath);
    text(document.querySelector('#rewards-path'), `${selected.fileName} → ${selected.rewardPath}`);
    const details = targets.map(target => rewardsEditor.targetScope(target));
    const present = details.filter(item => item.scope?.status === 'PRESENT').length;
    text(document.querySelector('#rewards-presence'), `${present}/${targets.length} configured`);
    const values = document.querySelector('#rewards-values'); values.replaceChildren();
    details.forEach(info => {
      const section = document.createElement('section'); section.className = 'target-operation';
      section.append(text(document.createElement('h4'), nodeIndex.get(info.nodeId)?.displayName || info.nodeId),
        text(document.createElement('p'), info.status !== 'AVAILABLE' ? `${info.status}: ${info.message || 'Read unavailable'}` : info.scope?.status || 'Missing'));
      if (info.status === 'AVAILABLE' && info.scope) Object.entries(info.scope.fields || {}).forEach(([field, value]) => {
        section.append(text(document.createElement('p'), `${field}: ${rewardText(value)}`));
      });
      values.append(section);
    });
    const advanced = document.querySelector('#rewards-advanced'); advanced.replaceChildren();
    details.forEach(info => {
      const section = document.createElement('section'); section.className = 'target-operation';
      section.append(text(document.createElement('h4'), nodeIndex.get(info.nodeId)?.displayName || info.nodeId),
        text(document.createElement('p'), info.scope?.advancedKeys?.length ? `Advanced keys preserved: ${info.scope.advancedKeys.join(', ')}` : 'No advanced keys reported'));
      (info.scope?.structurePaths || []).forEach(path => {
        const row = text(document.createElement('p'), path);
        row.className = `reward-tree-depth-${Math.min(8, path.split('.').length - 1)}`;
        section.append(row);
      });
      advanced.append(section);
    });
    const editable = selected.fileName === 'VoteSites.yml' && /^VoteSites\.[A-Za-z0-9_-]{1,64}\.Rewards$/.test(selected.rewardPath)
      || /^Rewards\/[A-Za-z0-9][A-Za-z0-9_-]{0,99}\.yml$/.test(selected.fileName) && selected.rewardPath === '$';
    document.querySelector('#rewards-editable').hidden = !editable;
    if (editable) {
      const fieldSelect = document.querySelector('#rewards-field');
      const priorField = fieldSelect.value;
      const itemFields = [...new Set(details.flatMap(info => Object.keys(info.scope?.fields || {})))]
        .filter(field => /^Items\.[A-Za-z0-9_-]{1,64}\.(Material|Amount)$/.test(field)).sort();
      [...fieldSelect.querySelectorAll('option[data-item-field]')].forEach(option => option.remove());
      itemFields.forEach(field => {
        const option = document.createElement('option'); option.value = field;
        option.dataset.itemField = 'true'; option.textContent = field; fieldSelect.append(option);
      });
      fieldSelect.value = [...fieldSelect.options].some(option => option.value === priorField) ? priorField : 'Commands';
      const field = fieldSelect.value;
      const aggregate = rewardsEditor.aggregate(field);
      const fieldState = document.querySelector('#rewards-field-state'); fieldState.replaceChildren();
      fieldState.append(text(document.createElement('strong'), `${field}: ${aggregate.status}${aggregate.status === 'SAME' ? ` · ${rewardText(aggregate.value)}` : ''}`));
      if (aggregate.status !== 'SAME') aggregate.targets.forEach(target => fieldState.append(text(document.createElement('p'),
        `${nodeIndex.get(target.nodeId)?.displayName || target.nodeId}: ${target.status === 'AVAILABLE' ? rewardText(target.value) : target.status}`)));
    }
  }
  const plans = rewardsEditor.plan(); const ready = plans.filter(item => item.status === 'READY');
  const summary = document.querySelector('#rewards-summary'); summary.replaceChildren();
  if (!rewardsEditor.edit) text(summary, 'No explicit reward edit. Reading or selecting a scope never stages a change.');
  else {
    summary.append(text(document.createElement('p'), `${rewardsEditor.edit.operation} · ${selected.fileName} → ${selected.rewardPath}${rewardsEditor.edit.field ? `.${rewardsEditor.edit.field}` : ''}${rewardsEditor.edit.value != null ? ` · ${rewardText(rewardsEditor.edit.value)}` : ''}`),
      text(document.createElement('p'), `${ready.length} target${ready.length === 1 ? '' : 's'} would change · VotingPlugin reload required`));
    plans.forEach(item => summary.append(text(document.createElement('p'), `${nodeIndex.get(item.nodeId)?.displayName || item.nodeId}: ${item.status}`)));
  }
  const excluded = plans.some(item => item.status !== 'READY');
  document.querySelector('#rewards-ack-label').hidden = !rewardsEditor.edit || !excluded;
  document.querySelector('#rewards-read').disabled = state.busy || !targets.length;
  document.querySelector('#rewards-retry').disabled = state.busy || !targets.some(target => rewardsEditor.files().some(file => rewardsEditor.record(target.nodeId, file)?.status === 'ERROR'));
  document.querySelector('#rewards-preview').disabled = state.busy || !ready.length || ready.length > 8;
  document.querySelector('#rewards-apply').disabled = state.busy || state.previewState !== 'Ready' || excluded && !document.querySelector('#rewards-ack').checked;
  const preview = document.querySelector('#rewards-preview-results'); preview.replaceChildren();
  if (!state.previews.length) text(preview, state.previewState === 'Stale' ? 'Stale — read and preview again.' : 'Not previewed.');
  state.previews.forEach(item => {
    const section = document.createElement('section'); section.className = 'target-operation';
    section.append(text(document.createElement('h4'), nodeIndex.get(item.nodeId)?.displayName || item.nodeId),
      text(document.createElement('p'), `${item.status}${item.operationId ? ` · operation ${item.operationId}` : ''}${item.message ? ` · ${item.message}` : ''}`));
    if (item.status === 'READY') {
      const change = rewardsEditor.edit;
      const scope = rewardsEditor.targetScope({nodeId: item.nodeId}).scope;
      const before = scope?.fields?.[change?.field];
      const after = change?.operation === 'APPEND_LIST_ENTRY' && Array.isArray(before) ? [...before, change.value]
        : change?.operation === 'REMOVE_LIST_ENTRY' && Array.isArray(before) ? before.filter(value => value !== change.value)
          : change?.operation === 'REPLACE_LIST' ? change.value
          : change?.operation === 'SET_SCALAR' ? change.value : change?.operation === 'REMOVE_REWARD' ? 'Removed' : 'Created';
      section.append(text(document.createElement('p'), `${selected.fileName} → ${selected.rewardPath}${change?.field ? `.${change.field}` : ''}: ${rewardText(before)} → ${rewardText(after)}`));
    } else if (item.status === 'SKIP') section.append(text(document.createElement('p'), `Excluded: ${item.reason || 'unavailable'}`));
    preview.append(section);
  });
  const results = document.querySelector('#rewards-results'); results.replaceChildren();
  if (!state.results.length) text(results, 'No apply performed.');
  state.results.forEach(item => {
    const section = document.createElement('section'); section.className = 'target-operation';
    section.append(text(document.createElement('h4'), nodeIndex.get(item.nodeId)?.displayName || item.nodeId),
      text(document.createElement('p'), `${item.status} · ${item.confirmed ? 'confirmed by fresh READ' : 'not confirmed'}${item.result?.rolledBack ? ' · previous file restored' : ''}${item.result?.code ? ` · ${item.result.code}` : ''}${item.result?.message ? ` · ${item.result.message}` : ''}${item.message ? ` · ${item.message}` : ''}`));
    results.append(section);
  });
}

rewardsEditor = ControlRewardsEditor.create({
  targets: () => workspace.managementScope === 'GLOBAL' || !authenticated ? [] : [...workspace.selectedTargetIds].map(id => {
    const node = nodeIndex.get(id);
    return {nodeId: id, sessionId: node?.sessionId || '', online: node?.online === true,
      supported: isBackend(node) && node?.acceptedCapabilities?.includes('config.files.v1') === true,
      rewardFilesSupported: isBackend(node) && node?.acceptedCapabilities?.includes('config.reward-files.v1') === true};
  }),
  context: rewardsContext,
  active: () => authenticated && tabFromHash() === 'rewards',
  operation: (path, body) => startConfigurationOperation(path, body, document.querySelector('#rewards-status')),
  request: (path, body) => authorized(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)}),
  inspect: inspectNamedRewardFiles,
  changed: renderRewards
});
document.querySelector('#rewards-read').addEventListener('click', () => void rewardsEditor.read(true));
document.querySelector('#rewards-retry').addEventListener('click', () => void rewardsEditor.read(true, true));
document.querySelector('#rewards-add').addEventListener('click', () => {
  const selected = rewardsEditor.selected;
  if (!selected.rewardPath || selected.fileName !== 'VoteSites.yml' || !/^VoteSites\.[A-Za-z0-9_-]{1,64}\.Rewards$/.test(selected.rewardPath)) {
    text(document.querySelector('#rewards-status'), 'Choose a Vote Site reward scope first. Only an absent inline Rewards section can be created here.'); return;
  }
  if (!rewardsEditor.targets().some(target => rewardsEditor.targetScope(target).scope?.status === 'MISSING')) {
    text(document.querySelector('#rewards-status'), 'This reward already exists on the selected targets. Use an explicit edit operation instead.'); return;
  }
  document.querySelector('#rewards-operation').value = 'CREATE_REWARD';
  document.querySelector('#rewards-field').value = 'Commands';
  document.querySelector('#rewards-value').focus();
  text(document.querySelector('#rewards-status'), 'Enter one console command, then stage and preview creation for missing targets. Existing rewards will be excluded.');
});
document.querySelector('#rewards-search').addEventListener('input', renderRewards);
document.querySelector('#rewards-field').addEventListener('change', renderRewards);
document.querySelector('#rewards-reset').addEventListener('click', () => { document.querySelector('#rewards-ack').checked = false; rewardsEditor.reset(); });
document.querySelector('#rewards-stage').addEventListener('click', () => {
  const operation = document.querySelector('#rewards-operation').value;
  const field = document.querySelector('#rewards-field').value;
  const raw = document.querySelector('#rewards-value').value;
  if (['APPEND_LIST_ENTRY', 'REMOVE_LIST_ENTRY', 'REPLACE_LIST'].includes(operation) && field !== 'Commands'
      || operation === 'SET_SCALAR' && field === 'Commands') {
    text(document.querySelector('#rewards-status'), 'Choose a field supported by this operation. Commands use append/remove; existing messages and numbers use Set.'); return;
  }
  if (operation === 'REMOVE_REWARD' && !window.confirm(`Stage removal of ${rewardsEditor.selected.rewardPath} from present targets? The site and named reward files remain.`)) return;
  if (operation !== 'REMOVE_REWARD' && operation !== 'REPLACE_LIST' && !raw.trim()) { text(document.querySelector('#rewards-status'), 'Enter an explicit value.'); return; }
  if (operation === 'REPLACE_LIST' && !window.confirm('Replace the entire Commands list on every eligible target? Existing target-specific commands will be removed. Preview each proposal before applying.')) return;
  const lines = operation === 'REPLACE_LIST' ? raw.split(/\r?\n/).map(line => line.trim()).filter(Boolean) : null;
  if (lines && (lines.length > 100 || lines.some(line => line.length > 500))) { text(document.querySelector('#rewards-status'), 'Use at most 100 commands of 500 characters each.'); return; }
  if (['APPEND_LIST_ENTRY', 'REMOVE_LIST_ENTRY', 'CREATE_REWARD'].includes(operation) && raw.includes('\n')) { text(document.querySelector('#rewards-status'), 'Enter exactly one command.'); return; }
  const numericScalar = operation === 'SET_SCALAR' && (['Money', 'Chance'].includes(field) || /^Items\.[A-Za-z0-9_-]{1,64}\.Amount$/.test(field));
  const value = operation === 'REMOVE_REWARD' ? null : operation === 'REPLACE_LIST' ? lines
    : numericScalar ? Number(raw) : raw;
  if (numericScalar && (!Number.isFinite(value) || value < 0)) { text(document.querySelector('#rewards-status'), 'Enter a nonnegative finite number.'); return; }
  if (operation === 'SET_SCALAR' && /^Items\.[A-Za-z0-9_-]{1,64}\.Amount$/.test(field)
      && (!Number.isInteger(value) || value < 1 || value > 64)) {
    text(document.querySelector('#rewards-status'), 'Item Amount must be a whole number from 1 to 64.'); return;
  }
  if (operation === 'SET_SCALAR' && /^Items\.[A-Za-z0-9_-]{1,64}\.Material$/.test(field)
      && !/^[A-Z0-9_]{1,80}$/.test(raw)) {
    text(document.querySelector('#rewards-status'), 'Enter the exact current Minecraft material name (uppercase letters, digits and underscores).'); return;
  }
  document.querySelector('#rewards-ack').checked = false;
  rewardsEditor.setEdit(operation, operation === 'REMOVE_REWARD' ? null : operation === 'CREATE_REWARD' ? 'Commands' : field, value);
});
document.querySelector('#rewards-preview').addEventListener('click', () => { document.querySelector('#rewards-ack').checked = false; void rewardsEditor.preview(); });
document.querySelector('#rewards-apply').addEventListener('click', () => void rewardsEditor.apply(document.querySelector('#rewards-ack').checked));
document.querySelector('#rewards-ack').addEventListener('change', renderRewards);
document.querySelector('#rewards-open-yaml').addEventListener('click', () => {
  const selected = rewardsEditor.selected;
  if (selected.fileName.startsWith('Rewards/') && ![...configurationFile.options].some(option => option.value === selected.fileName)) {
    const option = document.createElement('option'); option.value = selected.fileName;
    option.textContent = selected.fileName; option.dataset.sessionRewardFile = 'true'; configurationFile.append(option);
  }
  setActiveTab('configurations', true); setConfigView('yaml');
  if (configurationFile.value !== selected.fileName) { configurationFile.value = selected.fileName; configurationFile.dispatchEvent(new Event('input')); }
});
document.querySelector('#vote-sites-health-refresh').addEventListener('click', () => void refreshVoteSiteObservations());

refresh.addEventListener('click', loadNodes);
refreshDashboardButton.addEventListener('click', () => refreshDashboard());
previousPage.addEventListener('click', () => {
  pageOffset = Math.max(0, pageOffset - PAGE_SIZE);
  loadNodes();
});
nextPage.addEventListener('click', () => {
  pageOffset += PAGE_SIZE;
  loadNodes();
});
async function initialize() {
  await loadHealth();
  setActiveTab(tabFromHash());
  setConfigView('easy');
  updateQuickFields();
  updatePluginSuggestions();
  renderSettingsCatalog();
  populateGlobalSearch();
  populateProfilePicker();
  rewardSiteLabel.hidden = rewardScope.value !== 'site';
  updateExtendedButtons();
  if (!await loadSetupState()) {
    await restoreSession();
  }
}
initialize();
