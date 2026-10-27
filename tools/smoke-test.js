/*
 * Prom 冒烟测试（零依赖，只要 Node）
 *
 *   node tools/smoke-test.js app/src/main/assets/index.html
 *
 * 用一个极简 DOM 桩把 index.html 里的内联脚本跑起来，覆盖：
 *   - 旧存档加载与清洗（非法科目 / 非法日期 / NaN 学习点）
 *   - 头像从 profile 迁到独立键
 *   - 去抖写盘 + flush 立即落盘
 *   - 备份导入的合并规则（按 id 去重、uid 保留本机、成就并集）
 *   - 同步密钥 PROM2 的生成、crc 校验、篡改拒绝、重复导入幂等
 *
 * 退出码 0 = 全部通过。改完 index.html 建议先跑一遍再打包。
 */

/* 极简 DOM 桩：只为让 index.html 的内联脚本跑起来，验证没有运行时错误 */
const fs = require('fs');
const vm = require('vm');

function makeEl(tag) {
  const el = {
    tagName: (tag || 'div').toUpperCase(),
    id: '',
    className: '',
    textContent: '',
    hidden: false,
    value: '',
    disabled: false,
    title: '',
    type: '',
    placeholder: '',
    max: '',
    offsetWidth: 100,
    offsetLeft: 0,
    clientWidth: 380,
    scrollWidth: 1140,
    scrollLeft: 0,
    children: [],
    dataset: {},
    files: null,
    style: {
      setProperty() {}, removeProperty() {}, getPropertyValue() { return ''; },
    },
    _listeners: {},
    _html: '',
    classList: {
      _s: new Set(),
      add(...c) { c.forEach(x => this._s.add(x)); },
      remove(...c) { c.forEach(x => this._s.delete(x)); },
      toggle(c, force) {
        const on = force === undefined ? !this._s.has(c) : !!force;
        if (on) this._s.add(c); else this._s.delete(c);
        return on;
      },
      contains(c) { return this._s.has(c); },
    },
    appendChild(c) { this.children.push(c); return c; },
    insertBefore(c) { this.children.unshift(c); return c; },
    removeChild(c) { this.children = this.children.filter(x => x !== c); return c; },
    remove() {},
    addEventListener(t, fn) { (this._listeners[t] = this._listeners[t] || []).push(fn); },
    removeEventListener() {},
    dispatch(t, ev) { (this._listeners[t] || []).forEach(fn => fn.call(this, ev || { target: this, preventDefault() {} })); },
    setAttribute(k, v) { this[k] = v; },
    getAttribute(k) { return this[k]; },
    focus() {}, blur() {}, click() { this.dispatch('click'); },
    closest() { return null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    getBoundingClientRect() { return { left: 0, top: 0, width: 100, height: 100 }; },
    scrollTo() {},
    set innerHTML(v) { this._html = String(v); this.children = []; },
    get innerHTML() { return this._html; },
  };
  return el;
}

const byId = new Map();
function getEl(id) {
  if (!byId.has(id)) { const e = makeEl('div'); e.id = id; byId.set(id, e); }
  return byId.get(id);
}

const documentEl = makeEl('html');
const bodyEl = makeEl('body');

const document = {
  documentElement: documentEl,
  body: bodyEl,
  getElementById: getEl,
  createElement: makeEl,
  createTextNode: (t) => ({ text: t }),
  createDocumentFragment: () => makeEl('fragment'),
  querySelector: () => null,
  querySelectorAll: () => [],
  addEventListener(t, fn) { (this._l = this._l || {}); (this._l[t] = this._l[t] || []).push(fn); },
  hidden: false,
  visibilityState: 'visible',
};
documentEl.setAttribute = (k, v) => { documentEl[k] = v; };
documentEl.getAttribute = (k) => documentEl[k];

const store = new Map();
const writes = [];
const PromStore = {
  get: (k) => (store.has(k) ? store.get(k) : null),
  set: (k, v) => { store.set(k, String(v)); writes.push(k); },
  remove: (k) => store.delete(k),
  flush: () => {},
};

const window = {
  document,
  PromStore,
  PromUI: { tap() {}, setTheme() {}, copy() {}, readClipboard: () => 'PROM2R:xyz' },
  PromNotify: {
    status: () => JSON.stringify({ on: false, h: 21, m: 0, granted: true }),
    set() {}, refresh() {}, test() {}, pomoStart() {}, pomoCancel() {},
    openSettings() {}, requestPermission() {},
  },
  PromImage: { save: () => '已保存到相册', pick() {} },
  matchMedia: () => ({ matches: false, addEventListener() {}, addListener() {} }),
  addEventListener() {},
  scrollTo() {},
  localStorage: {
    _m: new Map(),
    getItem(k) { return this._m.has(k) ? this._m.get(k) : null; },
    setItem(k, v) { this._m.set(k, String(v)); },
    removeItem(k) { this._m.delete(k); },
  },
  location: { hash: '' },
  history: { length: 1, back() {} },
  navigator: { clipboard: { writeText() {} } },
  performance: { now: () => Date.now() },
  requestAnimationFrame: (fn) => setTimeout(() => fn(Date.now()), 0),
  cancelAnimationFrame: (id) => clearTimeout(id),
  setTimeout, clearTimeout, setInterval, clearInterval,
  btoa, atob, TextEncoder, TextDecoder,
  console, Date, Math, JSON, isFinite, Number, String, Array, Object, RegExp, Error,
  FileReader: function () { this.readAsText = () => {}; },
  URL: { createObjectURL: () => 'blob:x', revokeObjectURL() {} },
  Blob: function (parts) { this.parts = parts; },
  Image: function () {},
};
window.window = window;

const sandbox = {
  window, document, console, setTimeout, clearTimeout, setInterval, clearInterval,
  btoa, atob, TextEncoder, TextDecoder, performance: window.performance,
  requestAnimationFrame: window.requestAnimationFrame,
  cancelAnimationFrame: window.cancelAnimationFrame,
  localStorage: window.localStorage, location: window.location, history: window.history,
  navigator: window.navigator, matchMedia: window.matchMedia,
  PromStore, PromUI: window.PromUI, PromNotify: window.PromNotify, PromImage: window.PromImage,
  Math, JSON, Date, Number, String, Boolean, Array, Object, RegExp, Error, isFinite, isNaN,
  parseInt, parseFloat, undefined,
};
sandbox.globalThis = sandbox;

// 预置一份"老存档"：含头像、2 条打卡、1 条成绩
const legacy = {
  profile: { name: '小明', selected: ['chinese', 'math', 'english', 'history', 'physics'], gaokao: '2027-06-07', stage: 'senior', region: 'bj', totalFull: 750, totalFullAuto: true, uid: 'PROM-AAAA-BBBB', avatar: 'data:image/jpeg;base64,AAAA' },
  records: [
    { id: 'r1', subject: 'math', gain: 'paper', gainName: '刷题', note: '导数', min: 30, points: 18, date: '2026-10-24', time: '21:00', ts: 1 },
    { id: 'r2', subject: 'chinese', gain: 'word', gainName: '背单词', note: '', min: 15, points: 6, date: '2026-10-25', time: '20:00', ts: 2 },
  ],
  exams: [{ id: 'e1', subject: 'total', kind: '月考', score: 600, full: 750, date: '2026-10-01', ts: 3 }],
  bonus: [{ id: 'b1', date: '2026-10-25', subject: 'math', pts: 10, reason: '任务' }],
  quests: { date: '', items: [] },
  ach: { unlocked: { first: 111 } },
  rewards: {}, flags: {},
};
store.set('prom.state.v1', JSON.stringify(legacy));

const html = fs.readFileSync(process.argv[2], 'utf8');
const i = html.lastIndexOf('<script>');
const j = html.lastIndexOf('</script>');
const code = html.slice(i + 8, j);

let errors = [];
try {
  vm.runInNewContext(code, sandbox, { filename: 'index.html-inline.js' });
} catch (e) {
  errors.push('INIT: ' + e.stack.split('\n').slice(0, 3).join(' | '));
}

// ---- 断言 1：初始化后状态被清洗并写回（头像已搬到独立键）----
function storedState() {
  const raw = store.get('prom.state.v1');
  return raw ? JSON.parse(raw) : null;
}
// save() 是去抖的：模拟切后台，先强制落盘再断言
const beforeFlush = writes.length;
sandbox.window.__promFlush();
const after = storedState();
const checks = [];
function ok(name, cond, extra) { checks.push([cond ? 'PASS' : 'FAIL', name, extra === undefined ? '' : extra]); }

ok('初始化无异常', errors.length === 0, errors.join(' ;; '));
ok('头像迁移到独立键', store.get('prom.avatar.v1') === 'data:image/jpeg;base64,AAAA');
ok('主存档不再含头像', after && !JSON.stringify(after).includes('data:image/jpeg'));
ok('记录保留 2 条', after && after.records.length === 2, JSON.stringify(after && after.records.length));

// ---- 断言 2：flush 立刻落盘 ----
ok('flush 触发写入', writes.length > beforeFlush, writes.slice(beforeFlush).join(','));

// ---- 断言 3：导入备份 → 合并（1 条重叠 + 1 条新增）----
const backup = {
  app: 'prom-backup', v: 2, at: Date.now(), avatar: 'data:image/jpeg;base64,BBBB',
  state: {
    profile: { name: '小红', selected: ['chinese', 'math', 'english'], gaokao: '2027-06-07', stage: 'senior', region: 'bj', totalFull: 750, uid: 'PROM-CCCC-DDDD' },
    records: [
      { id: 'r2', subject: 'chinese', gain: 'word', gainName: '背单词', note: '', min: 15, points: 6, date: '2026-10-25', time: '20:00', ts: 2 },
      { id: 'r9', subject: 'english', gain: 'word', gainName: '背单词', note: '', min: 60, points: 24, date: '2026-10-22', time: '19:00', ts: 9 },
    ],
    exams: [], bonus: [], quests: { date: '', items: [] }, ach: { unlocked: { s3: 222 } }, rewards: {}, flags: {},
  },
};
const b64 = Buffer.from(JSON.stringify(backup), 'utf8').toString('base64');
try {
  sandbox.window.__promImportB64(b64);
  // 确认框 → 点"合并导入"
  getEl('cfOk').dispatch('click');
} catch (e) {
  errors.push('IMPORT: ' + e.stack.split('\n').slice(0, 3).join(' | '));
}
const merged = storedState();
ok('导入无异常', errors.length === 0, errors.join(' ;; '));
ok('合并后 3 条打卡（去重）', merged && merged.records.length === 3, merged && merged.records.map(r => r.id).join(','));
ok('本机 uid 优先保留', merged && merged.profile.uid === 'PROM-AAAA-BBBB', merged && merged.profile.uid);
ok('导入方昵称优先', merged && merged.profile.name === '小红', merged && merged.profile.name);
ok('成就取并集', merged && merged.ach.unlocked.first && merged.ach.unlocked.s3);
ok('头像被替换', store.get('prom.avatar.v1') === 'data:image/jpeg;base64,BBBB');

// ---- 断言 4：坏数据不会污染段位 ----
const dirty = {
  records: [
    { id: 'x1', subject: 'math', points: 'NaN', date: '2026-10-25' },
    { id: 'x2', subject: 'not-a-subject', points: 10, date: '2026-10-25' },
    { id: 'x3', subject: 'math', points: 10, date: '2026-13-45' },
    { id: 'x4', subject: 'math', points: 10, date: '2026-10-25', ts: 4 },
  ],
  exams: [{ id: 'y1', subject: 'total', score: 0, full: 0, date: '2026-10-01' }],
};
const b64b = Buffer.from(JSON.stringify(dirty), 'utf8').toString('base64');
try {
  sandbox.window.__promImportB64(b64b);
  getEl('cfOk').dispatch('click');
} catch (e) { errors.push('DIRTY: ' + e.stack.split('\n').slice(0, 2).join(' | ')); }
const cleaned = storedState();
ok('脏记录被过滤（只剩 1 条合法）', cleaned && cleaned.records.filter(r => r.id.indexOf('x') === 0).length === 1,
  cleaned && cleaned.records.map(r => r.id).join(','));
ok('满分为 0 的成绩被丢弃', cleaned && cleaned.exams.every(e => e.id !== 'y1'));

// ---- 断言 5：同步密钥 v2（PROM2 + crc32）----
(async () => {
  getEl('keyGen').dispatch('click');
  await new Promise(r => setTimeout(r, 60));
  const key = String(getEl('keyText').value || '');
  ok('生成 PROM2 密钥', /^PROM2[ZR]:/.test(key), key.slice(0, 10) + '… len=' + key.length);
  ok('密钥带 8 位 crc', /^PROM2[ZR]:[A-Za-z0-9_-]+\.[0-9a-f]{8}$/.test(key));

  // 篡改一位 → 必须报"密钥不完整"，而不是默默导入半份数据
  const m = /^(PROM2[ZR]:)([A-Za-z0-9_-]+)(\.[0-9a-f]{8})$/.exec(key);
  const body = m[2];
  const tampered = m[1] + body.slice(0, -2) + (body.slice(-2) === 'AA' ? 'BB' : 'AA') + m[3];
  getEl('keyText').value = tampered;
  getEl('keyApply').dispatch('click');
  getEl('cfOk').dispatch('click');
  await new Promise(r => setTimeout(r, 120));
  const note = String(getEl('keyNote').textContent || '');
  ok('篡改的密钥被拒绝', /恢复失败/.test(note), note.slice(0, 40));

  // 原始密钥可正常解析并合并（重复导入不会让记录翻倍）
  const beforeLen = (storedState() || { records: [] }).records.length;
  getEl('keyText').value = key;
  getEl('keyApply').dispatch('click');
  getEl('cfOk').dispatch('click');
  await new Promise(r => setTimeout(r, 120));
  const afterLen = (storedState() || { records: [] }).records.length;
  ok('原密钥可导入且幂等', afterLen === beforeLen, beforeLen + ' -> ' + afterLen);

  // ---- 断言 6：经济系统（12.9 调参）----
  const E = window.__promTest;
  E.reset();
  ok('总段位与单科是两套刻度', E.rank(400, true) === 2 && E.rank(400, false) === 1,
    '总段位 idx=' + E.rank(400, true) + ' 单科 idx=' + E.rank(400, false));
  ok('七重阈值：单科 6000 / 总段位 3000', E.singleTop === 6000 && E.avgTop === 3000,
    E.singleTop + ' / ' + E.avgTop);
  ok('日上限三段：240 / 400 / 600', E.caps.full === 240 && E.caps.soft === 400 && E.caps.hard === 600);

  E.reset();
  const p0 = E.earn(18, 30, 'math');
  E.seed(250);
  const p250 = E.earn(18, 30, 'math');
  E.seed(200);
  const p450 = E.earn(18, 30, 'math');
  E.seed(148);
  const p598 = E.earn(18, 30, 'math');
  E.seed(2);
  const p600 = E.earn(18, 30, 'math');
  ok('240 以内全价', p0 === 22, '+' + p0);
  ok('240–400 打七五折', p250 === 16, '+' + p250);
  ok('400 以上打五折', p450 === 11, '+' + p450);
  ok('贴近硬顶按余量截断', p598 === 2, '+' + p598);
  ok('撞满硬顶归零', p600 === 0, '+' + p600);

  E.reset();
  const q0 = E.quest();
  E.seed(400);
  const q400 = E.quest();
  ok('任务奖励 = 当日学习点 5%（3–20 夹紧）', q0 === 3 && q400 === 20, q0 + ' / ' + q400);

  E.reset();
  E.seed(0, 240);
  E.seed(0, 240);
  const w1 = E.week('math');
  E.seed(0, 240);
  const w2 = E.week('math');
  const w3 = E.week('math');
  ok('周目标 4/8/12 小时逐档结算', w1 === 60 && w2 === 80 && w3 === 0, w1 + '/' + w2 + '/' + w3);

  report();
})();

function report() {
  let failed = 0;
  for (const [st, name, extra] of checks) {
    if (st === 'FAIL') failed++;
    console.log(`${st}  ${name}${extra ? '   [' + extra + ']' : ''}`);
  }
  console.log(`\n${checks.length - failed}/${checks.length} passed`);
  process.exit(failed ? 1 : 0);
}
