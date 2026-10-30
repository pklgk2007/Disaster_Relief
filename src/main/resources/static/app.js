const app = document.getElementById('app');
let me = null;

const $ = (s, el = document) => el.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const label = s => String(s).replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());
const fmt = v => v ? new Date(v).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '-';
const opts = (arr, sel) => arr.map(o => `<option value="${esc(o.value)}"${o.value === sel ? ' selected' : ''}>${esc(o.text)}</option>`).join('');
const enumOpts = (arr, sel) => opts(arr.map(v => ({ value: v, text: label(v) })), sel);

const PRIORITY = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const REQ_STATUS = ['PENDING', 'APPROVED', 'DISPATCHED', 'FULFILLED', 'CANCELLED'];
const TASK_STATUS = ['ASSIGNED', 'IN_PROGRESS', 'COMPLETED'];

function toast(msg, bad) {
  const t = document.createElement('div');
  t.className = 'toast' + (bad ? ' bad' : '');
  t.textContent = msg;
  $('#toasts').append(t);
  setTimeout(() => t.remove(), 3500);
}
const guard = fn => async (...a) => { try { await fn(...a); } catch (e) { toast(e.message, true); } };

async function api(path, method = 'GET', body) {
  const res = await fetch('/api' + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined
  });
  if (res.status === 401 && me) { history.replaceState(null, '', '#login'); showLogin(); throw new Error('Session expired. Sign in again.'); }
  if (res.status === 403) throw new Error('You do not have permission to do that');
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

/* ---------------- login ---------------- */
function showLogin() {
  me = null;
  app.innerHTML = `
    <main class="login"><form id="lf" class="panel stack">
      <div><h1>Disaster <span>Relief</span> &amp; Shelter Management</h1>
      <p class="muted">Sign in with the account your administrator created.</p></div>
      <label>Username<input name="username" autocomplete="username" required></label>
      <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
      <p id="lerr" class="err" role="alert"></p>
      <button>Sign in</button>
      <p class="muted"><a href="#home">Back to home page</a></p>
    </form></main>`;
  $('#lf').onsubmit = async e => {
    e.preventDefault();
    try { me = await api('/auth/login', 'POST', Object.fromEntries(new FormData(e.target))); history.replaceState(null, '', '#dash'); showApp(); }
    catch (err) { $('#lerr').textContent = err.message; }
  };
}

/* ---------------- shell ---------------- */
const TABS = {
  ADMIN: [['requests', 'Relief requests'], ['tasks', 'Volunteer tasks'], ['users', 'Users'], ['applications', 'Join requests']],
  COORDINATOR: [['requests', 'Relief requests'], ['tasks', 'Volunteer tasks']],
  VOLUNTEER: [['mytasks', 'My tasks']]
};
const VIEWS = { requests: viewRequests, tasks: viewTasks, users: viewUsers, mytasks: viewMyTasks, applications: viewApplications };

function showApp() {
  app.innerHTML = `
    <header class="top"><strong>Disaster <span>Relief</span></strong><nav id="nav"></nav>
      <div class="who"><span>${esc(me.name || me.username)}</span><span class="badge">${label(me.role)}</span>
      <a class="btn ghost small" href="#home">Public site</a><button class="ghost small" id="out">Sign out</button></div></header>
    <main id="main"></main>`;
  $('#nav').innerHTML = TABS[me.role].map(([k, t]) => `<button data-tab="${k}">${t}</button>`).join('');
  $('#nav').onclick = e => { const b = e.target.closest('button[data-tab]'); if (b) go(b.dataset.tab); };
  $('#out').onclick = async () => { await fetch('/api/auth/logout', { method: 'POST' }); me = null; history.replaceState(null, '', '#home'); showHome(); };
  go(TABS[me.role][0][0]);
}

async function go(tab) {
  document.querySelectorAll('#nav button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
  try { await VIEWS[tab](); } catch (e) { toast(e.message, true); }
}

/* ---------------- relief requests (coordinator / admin) ---------------- */
async function viewRequests() {
  const rows = await api('/requests');
  $('#main').innerHTML = `
    <section class="panel">
      <div class="bar"><h2>Relief requests</h2>
        <div class="row">
          <select id="fs" aria-label="Filter by status"><option value="">All statuses</option>${enumOpts(REQ_STATUS)}</select>
          <button id="nr">New request</button>
        </div></div>
      <div class="scroll"><table>
        <thead><tr><th>ID</th><th>Shelter</th><th>Disaster</th><th>Priority</th><th>Items</th><th>Status</th><th>Created</th><th></th></tr></thead>
        <tbody id="rb"></tbody></table></div>
    </section>`;

  const draw = () => {
    const f = $('#fs').value;
    const list = rows.filter(r => !f || r.status === f);
    $('#rb').innerHTML = list.length ? list.map(r => `
      <tr data-id="${r.request_id}">
        <td>${r.request_id}</td><td>${esc(r.shelter)}</td><td>${esc(r.event)}</td>
        <td><span class="badge p-${r.priority}">${label(r.priority)}</span></td>
        <td>${r.item_count}</td>
        <td><select data-st aria-label="Status of request ${r.request_id}">${enumOpts(REQ_STATUS, r.status)}</select></td>
        <td>${fmt(r.created_at)}</td>
        <td class="row"><button class="ghost small" data-act="det">Items</button><button class="danger small" data-act="del">Delete</button></td>
      </tr>`).join('')
      : `<tr><td colspan="8" class="empty">No relief requests${f ? ' with this status' : ' yet. Use New request to create one'}.</td></tr>`;
  };
  draw();
  $('#fs').onchange = draw;
  $('#nr').onclick = guard(async () => openRequestForm(await api('/ref'), () => go('requests')));

  $('#rb').onchange = guard(async e => {
    const s = e.target.closest('select[data-st]'); if (!s) return;
    const id = s.closest('tr').dataset.id;
    await api(`/requests/${id}/status`, 'PUT', { status: s.value });
    rows.find(r => r.request_id == id).status = s.value;
    toast(`Request ${id} marked ${label(s.value).toLowerCase()}`);
  });

  $('#rb').onclick = guard(async e => {
    const b = e.target.closest('button[data-act]'); if (!b) return;
    const tr = b.closest('tr'), id = tr.dataset.id;
    if (b.dataset.act === 'del') {
      if (!confirm(`Delete request ${id}? Its items and dispatch records are deleted too.`)) return;
      await api('/requests/' + id, 'DELETE');
      rows.splice(rows.findIndex(r => r.request_id == id), 1);
      draw(); toast('Request deleted');
    } else {
      if (tr.nextElementSibling?.classList.contains('detail')) { tr.nextElementSibling.remove(); return; }
      const d = await api('/requests/' + id);
      tr.insertAdjacentHTML('afterend', `<tr class="detail"><td colspan="8">
        <table class="inner"><thead><tr><th>Item</th><th>Needed</th><th>Held by shelter</th><th></th></tr></thead><tbody>
        ${d.items.map(i => `<tr><td>${esc(i.item_name)}</td><td>${i.qty_needed} ${esc(i.unit)}</td><td>${i.in_stock} ${esc(i.unit)}</td>
          <td>${i.in_stock < i.qty_needed ? `<span class="badge short">Short by ${i.qty_needed - i.in_stock}</span>` : '<span class="badge ok">Covered</span>'}</td></tr>`).join('')}
        </tbody></table></td></tr>`);
    }
  });
}

function openRequestForm(ref, done) {
  const dlg = document.createElement('dialog');
  const itemRow = () => `<div class="item-row">
    <select name="item" aria-label="Item">${opts(ref.items.map(i => ({ value: i.item_id, text: `${i.item_name} (${i.unit})` })))}</select>
    <input name="qty" type="number" min="1" value="1" aria-label="Quantity">
    <button type="button" class="ghost small rm">Remove</button></div>`;
  dlg.innerHTML = `<form id="rf" class="stack">
    <h2>New relief request</h2>
    <label>Disaster event<select name="eventId">${opts(ref.events.map(e => ({ value: e.event_id, text: `${e.type} in ${e.area} (${label(e.severity)})` })))}</select></label>
    <div class="two">
      <label>Shelter<select name="shelterId">${opts(ref.shelters.map(s => ({ value: s.shelter_id, text: s.name })))}</select></label>
      <label>Priority<select name="priority">${enumOpts(PRIORITY, 'MEDIUM')}</select></label>
    </div>
    <div><label>Items needed</label><div id="items" class="stack">${itemRow()}</div>
      <button type="button" class="ghost small" id="addItem" style="margin-top:.6rem">Add another item</button></div>
    <div class="row" style="justify-content:flex-end"><button type="button" class="ghost" id="cancel">Cancel</button><button>Create request</button></div>
  </form>`;
  document.body.append(dlg);
  dlg.onclose = () => dlg.remove();
  dlg.showModal();
  $('#cancel', dlg).onclick = () => dlg.close();
  $('#addItem', dlg).onclick = () => $('#items', dlg).insertAdjacentHTML('beforeend', itemRow());
  $('#items', dlg).onclick = e => {
    if (e.target.classList.contains('rm') && dlg.querySelectorAll('.item-row').length > 1) e.target.closest('.item-row').remove();
  };
  $('#rf', dlg).onsubmit = guard(async e => {
    e.preventDefault();
    const f = e.target.elements;
    const items = [...dlg.querySelectorAll('.item-row')].map(r => ({
      itemId: +r.querySelector('[name=item]').value, qty: +r.querySelector('[name=qty]').value }));
    await api('/requests', 'POST', { eventId: +f.eventId.value, shelterId: +f.shelterId.value, priority: f.priority.value, items });
    dlg.close(); toast('Request created'); done();
  });
}

/* ---------------- volunteer tasks (coordinator / admin) ---------------- */
async function viewTasks() {
  const [tasks, ref, reqs] = await Promise.all([api('/tasks'), api('/ref'), api('/requests')]);
  $('#main').innerHTML = `
    <div class="split">
      <section class="panel"><h2>Assign a task</h2>
        <form id="tf" class="stack" style="margin-top:1rem">
          <label>Volunteer<select name="volunteerId" required>${opts(ref.volunteers.map(v => ({ value: v.volunteer_id, text: `${v.name} (${v.shelter || 'no shelter'})` })))}</select></label>
          <label>Task<input name="title" maxlength="120" required></label>
          <label>Details<textarea name="description" rows="3" maxlength="500"></textarea></label>
          <label>Related request (optional)<select name="requestId"><option value="">None</option>${opts(reqs.map(r => ({ value: r.request_id, text: `#${r.request_id} ${r.shelter} (${label(r.priority)})` })))}</select></label>
          <button>Assign task</button>
        </form></section>
      <section class="panel"><h2>All tasks</h2>
        <div class="scroll" style="margin-top:1rem"><table>
          <thead><tr><th>Volunteer</th><th>Task</th><th>Status</th><th>Assigned</th><th></th></tr></thead>
          <tbody>${tasks.length ? tasks.map(t => `<tr data-id="${t.task_id}">
            <td>${esc(t.volunteer)}</td><td>${esc(t.title)}</td>
            <td><span class="badge t-${t.status}">${label(t.status)}</span></td><td>${fmt(t.assigned_at)}</td>
            <td><button class="danger small">Delete</button></td></tr>`).join('')
            : '<tr><td colspan="5" class="empty">No tasks assigned yet.</td></tr>'}</tbody></table></div></section>
    </div>`;
  $('#tf').onsubmit = guard(async e => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    await api('/tasks', 'POST', { ...f, volunteerId: +f.volunteerId, requestId: f.requestId ? +f.requestId : null });
    toast('Task assigned'); go('tasks');
  });
  $('#main tbody').onclick = guard(async e => {
    const b = e.target.closest('button'); if (!b) return;
    if (!confirm('Delete this task?')) return;
    await api('/tasks/' + b.closest('tr').dataset.id, 'DELETE');
    toast('Task deleted'); go('tasks');
  });
}

/* ---------------- user management (admin) ---------------- */
async function viewUsers() {
  const [users, ref] = await Promise.all([api('/admin/users'), api('/ref')]);
  $('#main').innerHTML = `
    <div class="split">
      <section class="panel"><h2>Create account</h2>
        <form id="uf" class="stack" style="margin-top:1rem">
          <label>Role<select name="role">${enumOpts(['VOLUNTEER', 'COORDINATOR', 'ADMIN'])}</select></label>
          <label>Username<input name="username" minlength="3" required autocomplete="off"></label>
          <label>Temporary password<input name="password" type="password" minlength="6" required autocomplete="new-password"></label>
          <div id="pf" class="stack">
            <div class="two"><label>First name<input name="firstName"></label><label>Last name<input name="lastName"></label></div>
            <div class="two"><label>Email<input name="email" type="email"></label><label>Phone<input name="phone"></label></div>
            <div id="vf" class="stack">
              <label>Skill<input name="skill" placeholder="e.g. First aid, driving"></label>
              <label>Availability<input name="availability" placeholder="e.g. Weekdays, 9am to 5pm"></label>
              <label>Shelter<select name="shelterId"><option value="">Not assigned</option>${opts(ref.shelters.map(s => ({ value: s.shelter_id, text: s.name })))}</select></label>
            </div>
            <label id="cf" hidden>Coordinator role<input name="coordRole" placeholder="e.g. Logistics lead"></label>
          </div>
          <button>Create account</button>
        </form></section>
      <section class="panel"><h2>Accounts</h2>
        <div class="scroll" style="margin-top:1rem"><table>
          <thead><tr><th>Username</th><th>Name</th><th>Role</th><th>Created</th><th></th></tr></thead>
          <tbody>${users.map(u => `<tr data-id="${u.user_id}" data-name="${esc(u.username)}">
            <td>${esc(u.username)}</td><td>${esc(u.name || '-')}</td><td><span class="badge">${label(u.role)}</span></td>
            <td>${fmt(u.created_at)}</td>
            <td>${u.username === me.username ? '' : '<button class="danger small">Delete</button>'}</td></tr>`).join('')}</tbody></table></div></section>
    </div>`;

  const f = $('#uf');
  const sync = () => {
    const r = f.elements.role.value;
    $('#pf').hidden = r === 'ADMIN';
    $('#vf').hidden = r !== 'VOLUNTEER';
    $('#cf').hidden = r !== 'COORDINATOR';
  };
  f.elements.role.onchange = sync; sync();
  f.onsubmit = guard(async e => {
    e.preventDefault();
    await api('/admin/users', 'POST', Object.fromEntries(new FormData(f)));
    toast('Account created'); go('users');
  });
  $('#main tbody').onclick = guard(async e => {
    const b = e.target.closest('button'); if (!b) return;
    const tr = b.closest('tr');
    if (!confirm(`Delete account "${tr.dataset.name}"? Their profile and tasks are removed too.`)) return;
    await api('/admin/users/' + tr.dataset.id, 'DELETE');
    toast('Account deleted'); go('users');
  });
}

/* ---------------- my tasks (volunteer) ---------------- */
async function viewMyTasks() {
  const tasks = await api('/my-tasks');
  const open = tasks.filter(t => t.status !== 'COMPLETED').length;
  $('#main').innerHTML = `
    <div class="bar"><h2>My tasks</h2><span class="muted">${open} open, ${tasks.length - open} completed</span></div>
    <div class="cards">${tasks.length ? tasks.map(t => `
      <article class="card${t.status === 'COMPLETED' ? ' done' : ''}" data-id="${t.task_id}">
        <div class="row"><h3>${esc(t.title)}</h3>${t.priority ? `<span class="badge p-${t.priority}">${label(t.priority)} priority</span>` : ''}</div>
        ${t.description ? `<p>${esc(t.description)}</p>` : ''}
        <p class="muted">${t.shelter ? 'Shelter: ' + esc(t.shelter) + '. ' : ''}Assigned ${fmt(t.assigned_at)}</p>
        <div class="seg" role="group" aria-label="Task status">
          ${TASK_STATUS.map(s => `<button data-st="${s}" aria-pressed="${t.status === s}">${label(s)}</button>`).join('')}
        </div></article>`).join('')
      : '<div class="panel empty">No tasks assigned to you yet. Check back after a coordinator assigns one.</div>'}</div>`;
  $('#main').onclick = guard(async e => {
    const b = e.target.closest('button[data-st]'); if (!b) return;
    await api(`/my-tasks/${b.closest('.card').dataset.id}/status`, 'PUT', { status: b.dataset.st });
    toast('Status updated'); go('mytasks');
  });
}

/* ---------------- join requests (admin) ---------------- */
async function viewApplications() {
  const rows = await api('/admin/applications');
  $('#main').innerHTML = `
    <section class="panel">
      <div class="bar"><h2>Join requests</h2><span class="muted">${rows.filter(r => r.status === 'NEW').length} new</span></div>
      <div class="scroll"><table>
        <thead><tr><th>Name</th><th>Phone</th><th>Email</th><th>Skills</th><th>Message</th><th>Status</th><th>Received</th><th></th></tr></thead>
        <tbody>${rows.length ? rows.map(r => `<tr data-id="${r.app_id}">
          <td>${esc(r.name)}</td><td>${esc(r.phone)}</td><td>${esc(r.email)}</td><td>${esc(r.skills)}</td><td>${esc(r.message || '-')}</td>
          <td><select data-st>${enumOpts(['NEW', 'CONTACTED'], r.status)}</select></td><td>${fmt(r.created_at)}</td>
          <td><button class="danger small">Delete</button></td></tr>`).join('')
          : '<tr><td colspan="8" class="empty">No one has applied yet. Submissions from the public Want to join form appear here.</td></tr>'}</tbody></table></div>
    </section>`;
  $('#main tbody').onchange = guard(async e => {
    const sel = e.target.closest('select[data-st]'); if (!sel) return;
    await api(`/admin/applications/${sel.closest('tr').dataset.id}/status`, 'PUT', { status: sel.value });
    toast('Status updated');
  });
  $('#main tbody').onclick = guard(async e => {
    const b = e.target.closest('button'); if (!b) return;
    if (!confirm('Delete this application?')) return;
    await api('/admin/applications/' + b.closest('tr').dataset.id, 'DELETE');
    toast('Application deleted'); go('applications');
  });
}

/* ---------------- public home page ---------------- */
const num = n => Number(n || 0).toLocaleString('en-IN');

async function showHome() {
  app.innerHTML = `
    <header class="top"><strong>Disaster <span>Relief</span></strong>
      <nav>
        <button class="ghost" data-jump="about">What we do</button>
        <button class="ghost" data-jump="why">Why it matters</button>
        <button class="ghost" data-jump="stats">Live numbers</button>
        <button class="ghost" data-jump="join">Want to join</button>
      </nav>
      <a class="btn" href="${me ? '#dash' : '#login'}">${me ? 'Open dashboard' : 'Staff login'}</a>
    </header>

    <main class="home">
      <section class="hero">
        <p class="eyebrow">Disaster relief coordination</p>
        <h1>Disaster <span>Relief</span> &amp; Shelter Management</h1>
        <p class="lead">One shared system for the shelters, supplies, requests and volunteers behind a disaster response, so help reaches people faster and nothing gets lost between spreadsheets and paper lists.</p>
        <div class="row"><button data-jump="join">Want to join</button><a class="btn ghost" href="${me ? '#dash' : '#login'}">${me ? 'Open dashboard' : 'Staff login'}</a></div>
      </section>

      <section id="stats" class="block"><h2>Live from the system</h2>
        <div class="stats" id="statGrid"><p class="muted">Loading numbers...</p></div></section>

      <section id="about" class="block"><h2>What we do</h2>
        <div class="grid4">
          <div class="card"><h3>Shelters and stock</h3><p class="muted">Capacity, status and supplies for every shelter, recorded in one place.</p></div>
          <div class="card"><h3>Prioritised requests</h3><p class="muted">Shelters request items with a priority. Coordinators see the most urgent first and spot shortages against current stock.</p></div>
          <div class="card"><h3>Volunteers and tasks</h3><p class="muted">Coordinators assign tasks. Volunteers update progress from any device, with several tasks open at once.</p></div>
          <div class="card"><h3>Clear accountability</h3><p class="muted">Every request carries a status from pending to fulfilled, and uniqueness checks stop duplicate records.</p></div>
        </div></section>

      <section id="why" class="block"><h2>Why a management system is necessary</h2>
        <p class="muted narrow">During floods, earthquakes and fires, agencies must find shelters, record people, organise volunteers, track donated supplies and send resources fast. Disconnected spreadsheets and paper lists break down exactly when pressure is highest.</p>
        <div class="scroll"><table class="compare">
          <thead><tr><th>Spreadsheets and paper lists</th><th>One management system</th></tr></thead>
          <tbody>
            <tr><td>Duplicate records of people, shelters and items</td><td>One record each, enforced by keys and uniqueness checks</td></tr>
            <tr><td>Wrong inventory counts</td><td>Stock held per shelter and compared with every request</td></tr>
            <tr><td>Slow allocation of urgent needs</td><td>Requests sorted by priority, shortages flagged</td></tr>
            <tr><td>No view of where help stands</td><td>A status on every request and every volunteer task</td></tr>
          </tbody></table></div></section>

      <section id="join" class="block"><div class="split">
        <div><h2>Want to join</h2>
          <p class="muted">Share your details and the skills you can offer, such as first aid, driving, cooking or logistics. A coordinator will contact you.</p></div>
        <form id="jf" class="panel stack">
          <div class="two"><label>Full name<input name="name" required maxlength="100" autocomplete="name"></label>
          <label>Phone number<input name="phone" type="tel" required maxlength="20" autocomplete="tel"></label></div>
          <label>Email<input name="email" type="email" required maxlength="100" autocomplete="email"></label>
          <label>Skills<input name="skills" required maxlength="200" placeholder="e.g. First aid, driving, cooking"></label>
          <label>Anything else (optional)<textarea name="message" rows="3" maxlength="500"></textarea></label>
          <input name="website" class="hp" tabindex="-1" autocomplete="off" aria-hidden="true">
          <p id="jerr" class="err" role="alert"></p>
          <button>Send my details</button>
        </form></div></section>

      <footer class="foot">Academic database prototype built with MySQL, Spring Boot and JavaScript.</footer>
    </main>`;

  document.querySelectorAll('[data-jump]').forEach(b => b.onclick = () =>
    document.getElementById(b.dataset.jump).scrollIntoView({ behavior: 'smooth' }));

  $('#jf').onsubmit = async e => {
    e.preventDefault();
    const form = e.target, f = Object.fromEntries(new FormData(form)), btn = $('button', form);
    $('#jerr').textContent = ''; btn.disabled = true;
    try {
      await api('/public/join', 'POST', f);
      form.outerHTML = `<div class="panel"><h3>Thank you, ${esc(f.name)}</h3><p class="muted">We have your details. A coordinator will contact you on ${esc(f.phone)} or ${esc(f.email)}.</p></div>`;
    } catch (err) { $('#jerr').textContent = err.message; btn.disabled = false; }
  };

  try {
    const s = await api('/public/stats');
    const cards = [
      [s.shelters, 'Shelters registered'], [s.capacity, 'People they can house'], [s.events, 'Disaster events tracked'],
      [s.volunteers, 'Registered volunteers'], [`${s.open_requests} of ${s.requests}`, 'Relief requests still open'], [s.stock_units, 'Units of supplies in stock']
    ];
    $('#statGrid').innerHTML = cards.map(([v, k]) => `<div class="metric"><div class="v">${typeof v === 'number' ? num(v) : esc(v)}</div><div class="k">${k}</div></div>`).join('');
  } catch { $('#statGrid').innerHTML = '<p class="muted">Numbers are unavailable right now.</p>'; }
}

/* ---------------- start / routing ---------------- */
async function route() {
  try { const r = await fetch('/api/auth/me'); me = r.ok ? await r.json() : null; } catch { me = null; }
  const h = location.hash;
  if (h === '#login' && !me) return showLogin();
  if (h === '#home' || !me) return showHome();
  showApp();
}
window.addEventListener('hashchange', route);
route();
