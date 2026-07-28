/* لصق سريع — واجهة مشتركة بين تطبيق الأندرويد ونسخة الويب (Netlify). */

const NATIVE = typeof window.Android !== 'undefined';
const KEY = 'copier.phrases';

// الصور المخزنة داخل التطبيق كايقدمها WebViewAssetLoader تحت هاد النطاق
const IMG_BASE = 'https://appassets.androidplatform.net/images/';

const DEFAULTS = [
  'السلام عليكم ورحمة الله وبركاته',
  'مرحبا، كيفاش نقدر نعاونك؟',
  'شكرا بزاف على تواصلك معنا 🙏',
  'الثمن هو ... درهم، والتوصيل مجاني.',
  'التوصيل كايدوز من 24 ل 48 ساعة.',
  'عفوا، هاد المنتوج ما بقاش متوفر دابا.',
  'واخا، غادي نجاوبك من بعد شوية.',
  'بغيت غير نأكد معاك العنوان والرقم من فضلك.',
  'مرحبا بيك أي وقت 😊',
];

/* ---------------- التخزين ---------------- */

function makeStore(key, nativeLoad, nativeSave) {
  return {
    load() {
      try {
        const raw = NATIVE ? nativeLoad() : localStorage.getItem(key);
        const list = JSON.parse(raw || '[]');
        return Array.isArray(list) ? list : [];
      } catch (e) {
        return [];
      }
    },
    save(list) {
      const raw = JSON.stringify(list);
      if (NATIVE) nativeSave(raw);
      else localStorage.setItem(key, raw);
    },
  };
}

const store = makeStore(
  KEY,
  () => Android.loadPhrases(),
  (raw) => Android.savePhrases(raw)
);

const groupStore = makeStore(
  'copier.groups',
  () => Android.loadGroups(),
  (raw) => Android.saveGroups(raw)
);

let phrases = store.load();
let groups = groupStore.load();

if (phrases.length === 0) {
  phrases = DEFAULTS.map(newPhrase);
  store.save(phrases);
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

function newPhrase(text) {
  return { id: uid(), text };
}

function persist() {
  store.save(phrases);
  render();
}

function persistGroups() {
  groupStore.save(groups);
  renderGroups();
}

/** الجمع فالعربية ماشي بحال الإنجليزية: 3 صور، ولكن 15 صورة. */
function imgCount(n) {
  if (n === 0) return 'بلا صور';
  if (n === 1) return 'صورة وحدة';
  if (n === 2) return 'صورتين';
  if (n <= 10) return n + ' صور';
  return n + ' صورة';
}

/* ---------------- عناصر الواجهة ---------------- */

const $ = (id) => document.getElementById(id);
const listEl = $('list');
const searchEl = $('search');
const toastEl = $('toast');
const editorEl = $('editor');
const editorText = $('editor-text');
const editorTitle = $('editor-title');
const menuEl = $('menu');
const geditorEl = $('geditor');

let editingId = null;
let editingGroupId = null;
let activeTab = 'phrases';

/* ---------------- التبويبات ---------------- */

function switchTab(tab) {
  activeTab = tab;

  document.querySelectorAll('.tab').forEach((b) => {
    b.classList.toggle('active', b.dataset.tab === tab);
  });

  $('view-phrases').classList.toggle('hidden', tab !== 'phrases');
  $('view-images').classList.toggle('hidden', tab !== 'images');
  $('fab').setAttribute('aria-label', tab === 'phrases' ? 'إضافة عبارة' : 'إضافة مجموعة');
}

/* ---------------- عرض العبارات ---------------- */

function render() {
  const q = searchEl.value.trim().toLowerCase();
  const shown = q ? phrases.filter((p) => p.text.toLowerCase().includes(q)) : phrases;

  listEl.textContent = '';
  shown.forEach((p) => listEl.appendChild(cardFor(p, shown === phrases)));

  $('empty').classList.toggle('hidden', shown.length > 0);
  $('empty').textContent = q
    ? 'ما لقينا حتى عبارة فيها هاد البحث.'
    : 'ما كاين حتى عبارة. زيد وحدة بالزر ديال +.';
}

function cardFor(p, sortable) {
  const li = document.createElement('li');
  li.className = 'card';

  const text = document.createElement('button');
  text.className = 'card-text';
  text.textContent = p.text;
  text.addEventListener('click', () => copy(p.text));
  li.appendChild(text);

  const actions = document.createElement('div');
  actions.className = 'card-actions';

  actions.appendChild(action('نسخ', () => copy(p.text)));
  actions.appendChild(action('تعديل', () => openEditor(p)));

  if (sortable) {
    const i = phrases.indexOf(p);
    actions.appendChild(action('▲', () => move(phrases, i, -1, persist)));
    actions.appendChild(action('▼', () => move(phrases, i, 1, persist)));
  }

  const del = action('حذف', () => remove(p.id));
  del.classList.add('del');
  actions.appendChild(del);

  li.appendChild(actions);
  return li;
}

function action(label, fn) {
  const b = document.createElement('button');
  b.type = 'button';
  b.textContent = label;
  b.addEventListener('click', fn);
  return b;
}

function move(arr, i, dir, after) {
  const j = i + dir;
  if (j < 0 || j >= arr.length) return;
  [arr[i], arr[j]] = [arr[j], arr[i]];
  after();
}

/**
 * نافذة تأكيد ديالنا. `confirm()` ديال المتصفح كايرجع false مباشرة داخل WebView
 * بلا ما يوري والو، وهادشي كان كايخلي الحذف ما يوقعش.
 */
let confirmResolve = null;

function ask(message) {
  $('confirm-text').textContent = message;
  $('confirm').classList.remove('hidden');
  return new Promise((resolve) => {
    confirmResolve = resolve;
  });
}

function closeConfirm(answer) {
  $('confirm').classList.add('hidden');
  if (confirmResolve) {
    confirmResolve(answer);
    confirmResolve = null;
  }
}

async function remove(id) {
  if (!(await ask('تحذف هاد العبارة؟'))) return;
  phrases = phrases.filter((p) => p.id !== id);
  persist();
  toast('تحذفات');
}

/* ---------------- عرض مجموعات الصور ---------------- */

function renderGroups() {
  const wrap = $('groups');
  wrap.textContent = '';

  if (!NATIVE) {
    $('images-webnote').classList.remove('hidden');
    $('groups-empty').classList.add('hidden');
    return;
  }

  groups.forEach((g, i) => wrap.appendChild(groupCard(g, i)));
  $('groups-empty').classList.toggle('hidden', groups.length > 0);
}

function groupCard(g, index) {
  const li = document.createElement('li');
  li.className = 'card';

  const head = document.createElement('div');
  head.className = 'group-head';

  const name = document.createElement('span');
  name.className = 'group-name';
  name.textContent = g.name;

  const count = document.createElement('span');
  count.className = 'group-count';
  count.textContent = imgCount((g.images || []).length);

  head.appendChild(name);
  head.appendChild(count);
  li.appendChild(head);

  if (g.caption) {
    const cap = document.createElement('div');
    cap.className = 'group-caption';
    cap.textContent = g.caption;
    li.appendChild(cap);
  }

  li.appendChild(thumbsFor(g));

  const sendRow = document.createElement('div');
  sendRow.className = 'send-row';
  const send = document.createElement('button');
  send.className = 'btn send';
  const n = (g.images || []).length;
  send.textContent = n ? 'صيفط ' + imgCount(n) : 'زيد صور باش تصيفط';
  send.disabled = !n;
  send.addEventListener('click', () => Android.sendGroup(g.id));
  sendRow.appendChild(send);
  li.appendChild(sendRow);

  const actions = document.createElement('div');
  actions.className = 'card-actions';
  actions.appendChild(action('زيد صور', () => Android.pickImages(g.id)));
  actions.appendChild(action('تعديل', () => openGroupEditor(g)));
  actions.appendChild(action('▲', () => move(groups, index, -1, persistGroups)));
  actions.appendChild(action('▼', () => move(groups, index, 1, persistGroups)));

  const del = action('حذف', () => removeGroup(g.id));
  del.classList.add('del');
  actions.appendChild(del);

  li.appendChild(actions);
  return li;
}

function thumbsFor(g) {
  const strip = document.createElement('div');
  strip.className = 'thumbs';

  (g.images || []).forEach((name) => {
    const cell = document.createElement('div');
    cell.className = 'thumb';

    const img = document.createElement('img');
    img.src = IMG_BASE + name;
    img.alt = '';
    img.loading = 'lazy';
    cell.appendChild(img);

    // الحذف غير من العلامة، ماشي من الصورة كاملة — باش ما تتحذفش بالغلط
    const del = document.createElement('button');
    del.type = 'button';
    del.className = 'thumb-del';
    del.textContent = '✕';
    del.setAttribute('aria-label', 'حذف هاد الصورة');
    del.addEventListener('click', () => removeImage(g.id, name));
    cell.appendChild(del);

    strip.appendChild(cell);
  });

  const add = document.createElement('button');
  add.type = 'button';
  add.className = 'thumb-add';
  add.innerHTML = '<span>+</span>زيد';
  add.addEventListener('click', () => Android.pickImages(g.id));
  strip.appendChild(add);

  return strip;
}

async function removeImage(groupId, name) {
  if (!(await ask('تحذف هاد الصورة من المجموعة؟'))) return;
  const g = groups.find((x) => x.id === groupId);
  if (!g) return;
  g.images = (g.images || []).filter((n) => n !== name);
  persistGroups();
}

async function removeGroup(id) {
  const g = groups.find((x) => x.id === id);
  if (!g) return;
  if (!(await ask('تحذف مجموعة «' + g.name + '» بالصور ديالها؟'))) return;
  groups = groups.filter((x) => x.id !== id);
  persistGroups();
  toast('تحذفات');
}

/* ---------------- المحررات ---------------- */

function openEditor(p) {
  editingId = p ? p.id : null;
  editorTitle.textContent = p ? 'تعديل العبارة' : 'عبارة جديدة';
  editorText.value = p ? p.text : '';
  editorEl.classList.remove('hidden');
  editorText.focus();
}

function closeEditor() {
  editorEl.classList.add('hidden');
  editingId = null;
}

function saveEditor() {
  const text = editorText.value.trim();
  if (!text) return toast('العبارة خاوية');

  if (editingId) {
    const p = phrases.find((x) => x.id === editingId);
    if (p) p.text = text;
  } else {
    phrases.unshift(newPhrase(text));
  }

  closeEditor();
  persist();
  toast('تحفظات');
}

function openGroupEditor(g) {
  editingGroupId = g ? g.id : null;
  $('geditor-title').textContent = g ? 'تعديل المجموعة' : 'مجموعة جديدة';
  $('geditor-name').value = g ? g.name : '';
  $('geditor-caption').value = g ? g.caption || '' : '';
  geditorEl.classList.remove('hidden');
  $('geditor-name').focus();
}

function closeGroupEditor() {
  geditorEl.classList.add('hidden');
  editingGroupId = null;
}

function saveGroupEditor() {
  const name = $('geditor-name').value.trim();
  const caption = $('geditor-caption').value.trim();
  if (!name) return toast('خاص اسم للمجموعة');

  let created = null;

  if (editingGroupId) {
    const g = groups.find((x) => x.id === editingGroupId);
    if (g) {
      g.name = name;
      g.caption = caption;
    }
  } else {
    created = { id: uid(), name, caption, images: [] };
    groups.push(created);
  }

  closeGroupEditor();
  persistGroups();

  // مجموعة خاوية ما تنفع والو — كانفتحو المنتقي مباشرة
  if (created) Android.pickImages(created.id);
  else toast('تحفظات');
}

/* ---------------- الحافظة ---------------- */

function copy(text) {
  if (NATIVE) {
    Android.copy(text);
    toast('تنسخات للحافظة');
    return;
  }
  navigator.clipboard.writeText(text).then(
    () => toast('تنسخات للحافظة'),
    () => fallbackCopy(text)
  );
}

function fallbackCopy(text) {
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  try {
    document.execCommand('copy');
    toast('تنسخات للحافظة');
  } catch (e) {
    toast('ما قدرناش ننسخو');
  }
  ta.remove();
}

let toastTimer;
function toast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('show'), 1800);
}

/* ---------------- إعداد الزر العائم (أندرويد فقط) ---------------- */

function renderSetup() {
  if (!NATIVE) {
    $('webnote').classList.remove('hidden');
    return;
  }

  $('setup').classList.remove('hidden');
  const steps = $('setup-steps');
  steps.textContent = '';

  const running = Android.isBubbleRunning();

  addStep(steps, 'إذن الظهور فوق التطبيقات', Android.hasOverlay(), 'فتح الإعدادات', () =>
    Android.requestOverlay()
  );

  addStep(
    steps,
    'خدمة اللصق التلقائي (إمكانية الوصول)',
    Android.hasAccessibility(),
    'فتح الإعدادات',
    () => Android.openAccessibility()
  );

  addStep(
    steps,
    'جهات الاتصال (اختياري) — للزبناء المسجلين',
    Android.hasContacts(),
    'سماح',
    () => Android.requestContacts()
  );

  addStep(steps, running ? 'الزر العائم خدام' : 'الزر العائم مطفي', running, running ? 'إطفاء' : 'تشغيل', () => {
    if (running) Android.stopBubble();
    else Android.startBubble();
    setTimeout(renderSetup, 400);
  });
}

function addStep(parent, label, ok, btnLabel, onClick) {
  const li = document.createElement('li');

  const row = document.createElement('div');
  row.className = 'step-row';

  const left = document.createElement('span');
  left.className = 'step-label';

  const dot = document.createElement('span');
  dot.className = 'dot ' + (ok ? 'ok' : 'bad');
  left.appendChild(dot);
  left.appendChild(document.createTextNode(label));

  const btn = document.createElement('button');
  btn.className = 'btn small' + (ok ? ' gray' : '');
  btn.textContent = btnLabel;
  btn.addEventListener('click', onClick);

  row.appendChild(left);
  row.appendChild(btn);
  li.appendChild(row);
  parent.appendChild(li);
}

/* ---------------- القائمة: تصدير / استيراد ---------------- */

function exportPhrases() {
  const blob = new Blob([JSON.stringify(phrases, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'phrases.json';
  a.click();
  URL.revokeObjectURL(a.href);
}

function importPhrases(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const list = JSON.parse(reader.result);
      if (!Array.isArray(list)) throw new Error('bad');
      phrases = list
        .map((p) => (typeof p === 'string' ? newPhrase(p) : p))
        .filter((p) => p && typeof p.text === 'string')
        .map((p) => ({ id: p.id || uid(), text: p.text }));
      persist();
      toast('تستوردات ' + phrases.length + ' عبارة');
    } catch (e) {
      toast('الملف ماشي صالح');
    }
  };
  reader.readAsText(file);
}

/* ---------------- ربط الأحداث ---------------- */

document.querySelectorAll('.tab').forEach((b) => {
  b.addEventListener('click', () => switchTab(b.dataset.tab));
});

$('fab').addEventListener('click', () => {
  if (activeTab === 'phrases') return openEditor(null);
  if (!NATIVE) return toast('مجموعات الصور كايخدمو غير فالتطبيق');
  openGroupEditor(null);
});

$('editor-cancel').addEventListener('click', closeEditor);
$('editor-save').addEventListener('click', saveEditor);
editorEl.addEventListener('click', (e) => { if (e.target === editorEl) closeEditor(); });

$('geditor-cancel').addEventListener('click', closeGroupEditor);
$('geditor-save').addEventListener('click', saveGroupEditor);
geditorEl.addEventListener('click', (e) => { if (e.target === geditorEl) closeGroupEditor(); });

searchEl.addEventListener('input', render);

$('btn-menu').addEventListener('click', (e) => {
  e.stopPropagation();
  menuEl.classList.toggle('hidden');
});

document.addEventListener('click', () => menuEl.classList.add('hidden'));

menuEl.addEventListener('click', async (e) => {
  const act = e.target.dataset.act;

  // فالتطبيق النسخة الاحتياطية كتشمل الصور حتى هي، فنسخة الويب غير العبارات
  if (act === 'backup') NATIVE ? Android.exportBackup() : exportPhrases();

  if (act === 'restore') {
    if (!NATIVE) return $('file').click();
    if (await ask('الاسترجاع غادي يعوض العبارات والمجموعات اللي عندك دابا. نكملو؟')) {
      Android.importBackup();
    }
  }

  if (act === 'reset' && (await ask('ترجع للعبارات الافتراضية؟ غادي تمسح لي عندك.'))) {
    phrases = DEFAULTS.map(newPhrase);
    persist();
    toast('ترجعو للافتراضي');
  }
});

$('confirm-yes').addEventListener('click', () => closeConfirm(true));
$('confirm-no').addEventListener('click', () => closeConfirm(false));
$('confirm').addEventListener('click', (e) => {
  if (e.target === $('confirm')) closeConfirm(false);
});

$('file').addEventListener('change', (e) => {
  if (e.target.files[0]) importPhrases(e.target.files[0]);
  e.target.value = '';
});

// كايتنادى من MainActivity بعد ما تتزاد الصور
window.reloadAll = () => {
  phrases = store.load();
  groups = groupStore.load();
  render();
  renderGroups();
};

// بعد ما يرجع المستخدم من إعدادات النظام، نعاودو نقراو الحالة.
window.renderSetup = renderSetup;
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) renderSetup();
});

if (!NATIVE && 'serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

switchTab('phrases');
renderSetup();
render();
renderGroups();
