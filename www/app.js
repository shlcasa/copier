/* لصق سريع — واجهة مشتركة بين تطبيق الأندرويد ونسخة الويب (Netlify). */

const NATIVE = typeof window.Android !== 'undefined';
const KEY = 'copier.phrases';

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

const store = {
  load() {
    try {
      const raw = NATIVE ? Android.loadPhrases() : localStorage.getItem(KEY);
      const list = JSON.parse(raw || '[]');
      return Array.isArray(list) ? list : [];
    } catch (e) {
      return [];
    }
  },
  save(list) {
    const raw = JSON.stringify(list);
    if (NATIVE) Android.savePhrases(raw);
    else localStorage.setItem(KEY, raw);
  },
};

let phrases = store.load();

if (phrases.length === 0) {
  phrases = DEFAULTS.map(newPhrase);
  store.save(phrases);
}

function newPhrase(text) {
  return { id: Date.now().toString(36) + Math.random().toString(36).slice(2, 7), text };
}

function persist() {
  store.save(phrases);
  render();
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

let editingId = null;

/* ---------------- عرض اللائحة ---------------- */

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
    actions.appendChild(action('▲', () => move(i, -1)));
    actions.appendChild(action('▼', () => move(i, 1)));
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

/* ---------------- العمليات ---------------- */

function move(i, dir) {
  const j = i + dir;
  if (j < 0 || j >= phrases.length) return;
  [phrases[i], phrases[j]] = [phrases[j], phrases[i]];
  persist();
}

function remove(id) {
  if (!confirm('تحذف هاد العبارة؟')) return;
  phrases = phrases.filter((p) => p.id !== id);
  persist();
  toast('تحذفات');
}

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
        .map((p) => ({ id: p.id || newPhrase('').id, text: p.text }));
      persist();
      toast('تستوردات ' + phrases.length + ' عبارة');
    } catch (e) {
      toast('الملف ماشي صالح');
    }
  };
  reader.readAsText(file);
}

/* ---------------- ربط الأحداث ---------------- */

$('fab').addEventListener('click', () => openEditor(null));
$('editor-cancel').addEventListener('click', closeEditor);
$('editor-save').addEventListener('click', saveEditor);
editorEl.addEventListener('click', (e) => { if (e.target === editorEl) closeEditor(); });
searchEl.addEventListener('input', render);

$('btn-menu').addEventListener('click', (e) => {
  e.stopPropagation();
  menuEl.classList.toggle('hidden');
});

document.addEventListener('click', () => menuEl.classList.add('hidden'));

menuEl.addEventListener('click', (e) => {
  const act = e.target.dataset.act;
  if (act === 'export') exportPhrases();
  if (act === 'import') $('file').click();
  if (act === 'reset' && confirm('ترجع للعبارات الافتراضية؟ غادي تمسح لي عندك.')) {
    phrases = DEFAULTS.map(newPhrase);
    persist();
    toast('ترجعو للافتراضي');
  }
});

$('file').addEventListener('change', (e) => {
  if (e.target.files[0]) importPhrases(e.target.files[0]);
  e.target.value = '';
});

// بعد ما يرجع المستخدم من إعدادات النظام، نعاودو نقراو الحالة.
// (MainActivity.onResume كايعيط لـ renderSetup مباشرة، وvisibilitychange للويب.)
window.renderSetup = renderSetup;
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) renderSetup();
});

if (!NATIVE && 'serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

renderSetup();
render();
