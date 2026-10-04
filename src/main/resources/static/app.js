/* Messenger web client: REST for state changes, STOMP over WebSocket for live events.
   User-provided text is only ever assigned with textContent. */
'use strict';

const $ = id => document.getElementById(id);
const state = {
  token: sessionStorage.getItem('token'),
  me: null,
  conversations: [],
  active: null,          // conversation currently open
  oldestSeq: null,       // cursor for "load older"
  stomp: null,
  typingTimer: null,
  lastTypingSent: 0,
};

// ── HTTP ──────────────────────────────────────────────────────────────────────
async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 401 && state.token) {
    signOut();
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const fields = data && data.fields ? Object.entries(data.fields).map(([k, v]) => `${k} ${v}`).join(', ') : '';
    throw new Error(fields || (data && data.detail) || `Request failed (${res.status})`);
  }
  return data;
}

// ── Auth ──────────────────────────────────────────────────────────────────────
let registering = false;

function showAuth() {
  $('app').hidden = true;
  $('auth').hidden = false;
  $('username').focus();
}

$('auth-toggle').addEventListener('click', () => {
  registering = !registering;
  $('display-name-row').hidden = !registering;
  $('auth-submit').textContent = registering ? 'Create account' : 'Sign in';
  $('auth-toggle').textContent = registering ? 'I already have an account' : 'Create an account';
  $('auth-subtitle').textContent = registering ? 'Pick a username and password' : 'Sign in to continue';
  $('password').autocomplete = registering ? 'new-password' : 'current-password';
  $('auth-error').textContent = '';
});

$('auth-form').addEventListener('submit', async e => {
  e.preventDefault();
  $('auth-error').textContent = '';
  const username = $('username').value.trim();
  const password = $('password').value;
  try {
    if (registering) {
      await api('POST', '/api/auth/register', { username, password, displayName: $('display-name').value.trim() || username });
    }
    const login = await api('POST', '/api/auth/login', { username, password });
    state.token = login.accessToken;
    sessionStorage.setItem('token', state.token);
    await start();
  } catch (err) {
    $('auth-error').textContent = err.message;
  }
});

function signOut() {
  sessionStorage.removeItem('token');
  state.token = null;
  if (state.stomp) state.stomp.deactivate();
  showAuth();
}
$('logout').addEventListener('click', signOut);

// ── Real-time ─────────────────────────────────────────────────────────────────
function connect() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  state.stomp = new StompJs.Client({
    brokerURL: `${scheme}://${location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${state.token}` },
    reconnectDelay: 3000,
    onConnect: () => {
      $('connection').classList.add('online');
      state.stomp.subscribe('/user/queue/events', frame => onEvent(JSON.parse(frame.body)));
      // Catch up on anything missed while disconnected.
      loadConversations();
      if (state.active) openConversation(state.active.id);
    },
    onWebSocketClose: () => $('connection').classList.remove('online'),
  });
  state.stomp.activate();
}

function onEvent(event) {
  const open = state.active && state.active.id === event.conversationId;
  switch (event.type) {
    case 'message.created':
      if (open) {
        appendMessage(event.data);
        scrollToBottom();
        markRead(event.data.seq);
      }
      loadConversations();
      break;
    case 'message.updated':
    case 'message.deleted':
      if (open) replaceMessage(event.data);
      loadConversations();
      break;
    case 'read':
      if (open) updateReceipt(event.data);
      break;
    case 'typing':
      if (open) showTyping(event.data.username);
      break;
    default: // conversation.created, conversation.updated, member.left
      loadConversations();
      if (open) openConversation(event.conversationId);
  }
}

// ── Conversations ─────────────────────────────────────────────────────────────
async function loadConversations() {
  state.conversations = await api('GET', '/api/conversations');
  const list = $('conversations');
  list.replaceChildren(...state.conversations.map(c => {
    const li = document.createElement('li');
    li.dataset.id = c.id;
    if (state.active && state.active.id === c.id) li.classList.add('active');
    const title = document.createElement('strong');
    title.textContent = c.title;
    const preview = document.createElement('span');
    preview.className = 'preview';
    preview.textContent = c.lastMessage
      ? `${c.lastMessage.sender.username === state.me.username ? 'You' : c.lastMessage.sender.displayName}: ${c.lastMessage.deleted ? 'message deleted' : c.lastMessage.body}`
      : 'No messages yet';
    li.append(title, preview);
    if (c.unreadCount > 0 && !(state.active && state.active.id === c.id)) {
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = c.unreadCount > 99 ? '99+' : String(c.unreadCount);
      li.append(badge);
    }
    li.addEventListener('click', () => openConversation(c.id));
    return li;
  }));
}

async function openConversation(id) {
  const conversation = await api('GET', `/api/conversations/${id}`);
  state.active = conversation;
  $('app').classList.add('in-chat');
  $('empty').hidden = true;
  $('chat-header').hidden = false;
  $('composer').hidden = false;
  $('chat-title').textContent = conversation.title;
  $('chat-members').textContent = conversation.members.map(m => m.displayName).join(', ');
  const isGroup = conversation.type === 'GROUP';
  const iOwn = conversation.members.some(m => m.username === state.me.username && m.role === 'OWNER');
  $('add-member').hidden = !(isGroup && iOwn);
  $('leave').hidden = !isGroup;
  $('typing').textContent = '';

  const page = await api('GET', `/api/conversations/${id}/messages?limit=50`);
  $('messages').replaceChildren();
  page.messages.forEach(appendMessage);
  setOlderCursor(page.nextBefore);
  scrollToBottom();
  if (conversation.lastSeq > 0) markRead(conversation.lastSeq);
  loadConversations();
  $('message-input').focus();
}

function setOlderCursor(nextBefore) {
  state.oldestSeq = nextBefore;
  const existing = $('messages').querySelector('.older');
  if (existing) existing.remove();
  if (nextBefore != null) {
    const li = document.createElement('li');
    li.className = 'older';
    const button = document.createElement('button');
    button.className = 'link';
    button.textContent = 'Load older messages';
    button.addEventListener('click', loadOlder);
    li.append(button);
    $('messages').prepend(li);
  }
}

async function loadOlder() {
  const list = $('messages');
  const height = list.scrollHeight;
  const page = await api('GET', `/api/conversations/${state.active.id}/messages?limit=50&before=${state.oldestSeq}`);
  const anchor = list.querySelector('li:not(.older)');
  page.messages.forEach(m => list.insertBefore(renderMessage(m), anchor));
  setOlderCursor(page.nextBefore);
  list.scrollTop = list.scrollHeight - height;
}

// ── Messages ──────────────────────────────────────────────────────────────────
function renderMessage(m) {
  const li = document.createElement('li');
  li.dataset.id = m.id;
  li.dataset.seq = m.seq;
  const mine = m.sender.username === state.me.username;
  if (mine) li.classList.add('mine');
  if (m.deleted) li.classList.add('deleted');
  const meta = document.createElement('div');
  meta.className = 'meta';
  const time = new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  meta.textContent = `${mine ? 'You' : m.sender.displayName} · ${time}${m.editedAt && !m.deleted ? ' · edited' : ''}`;
  const body = document.createElement('div');
  body.className = 'body';
  body.textContent = m.deleted ? 'This message was deleted' : m.body;
  li.append(meta, body);
  if (mine && !m.deleted) {
    li.title = 'Double-click to delete';
    li.addEventListener('dblclick', async () => {
      if (confirm('Delete this message?')) {
        await api('DELETE', `/api/conversations/${m.conversationId}/messages/${m.id}`);
      }
    });
  }
  return li;
}

function appendMessage(m) {
  if ($('messages').querySelector(`li[data-id="${m.id}"]`)) return;
  $('messages').append(renderMessage(m));
}

function replaceMessage(m) {
  const existing = $('messages').querySelector(`li[data-id="${m.id}"]`);
  if (existing) existing.replaceWith(renderMessage(m));
}

function updateReceipt({ username, seq }) {
  if (state.active.type !== 'DIRECT') return;
  $('messages').querySelectorAll('.seen').forEach(el => el.remove());
  const mineRead = [...$('messages').querySelectorAll('li.mine')].filter(li => Number(li.dataset.seq) <= seq).pop();
  if (mineRead) {
    const seen = document.createElement('div');
    seen.className = 'meta seen';
    seen.textContent = `Seen by ${username}`;
    mineRead.append(seen);
  }
}

function scrollToBottom() {
  const list = $('messages');
  list.scrollTop = list.scrollHeight;
}

async function markRead(seq) {
  try {
    await api('POST', `/api/conversations/${state.active.id}/read`, { seq });
  } catch { /* best effort */ }
}

$('composer').addEventListener('submit', async e => {
  e.preventDefault();
  const input = $('message-input');
  const body = input.value.trim();
  if (!body) return;
  input.value = '';
  try {
    // The client id makes a retried send safe: the server stores it once.
    const message = await api('POST', `/api/conversations/${state.active.id}/messages`,
      { body, clientMessageId: crypto.randomUUID() });
    appendMessage(message);
    scrollToBottom();
  } catch (err) {
    input.value = body;
    alert(err.message);
  }
});

$('message-input').addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    $('composer').requestSubmit();
    return;
  }
  const now = Date.now();
  if (state.stomp && state.stomp.connected && now - state.lastTypingSent > 2000) {
    state.lastTypingSent = now;
    state.stomp.publish({ destination: `/app/conversations/${state.active.id}/typing`, body: '' });
  }
});

function showTyping(username) {
  $('typing').textContent = `${username} is typing…`;
  clearTimeout(state.typingTimer);
  state.typingTimer = setTimeout(() => { $('typing').textContent = ''; }, 3000);
}

// ── People, groups, membership ────────────────────────────────────────────────
let searchTimer;
$('people-search').addEventListener('input', e => {
  clearTimeout(searchTimer);
  const q = e.target.value.trim();
  searchTimer = setTimeout(async () => {
    const people = q ? await api('GET', `/api/users?q=${encodeURIComponent(q)}`) : [];
    $('people-results').replaceChildren(...people.map(p => {
      const li = document.createElement('li');
      li.textContent = `${p.displayName} (@${p.username})`;
      li.addEventListener('click', async () => {
        const conversation = await api('POST', '/api/conversations/direct', { username: p.username });
        $('people-search').value = '';
        $('people-results').replaceChildren();
        await openConversation(conversation.id);
      });
      return li;
    }));
  }, 250);
});

$('new-group').addEventListener('click', async () => {
  const title = prompt('Group name');
  if (!title) return;
  const members = (prompt('Usernames to add, separated by commas') || '').split(',').map(s => s.trim()).filter(Boolean);
  try {
    const group = await api('POST', '/api/conversations/groups', { title, members });
    await openConversation(group.id);
  } catch (err) {
    alert(err.message);
  }
});

$('add-member').addEventListener('click', async () => {
  const username = prompt('Username to add');
  if (!username) return;
  try {
    await api('POST', `/api/conversations/${state.active.id}/members`, { username });
    await openConversation(state.active.id);
  } catch (err) {
    alert(err.message);
  }
});

$('leave').addEventListener('click', async () => {
  if (!confirm('Leave this group?')) return;
  await api('DELETE', `/api/conversations/${state.active.id}/members/me`);
  state.active = null;
  $('app').classList.remove('in-chat');
  $('chat-header').hidden = true;
  $('composer').hidden = true;
  $('messages').replaceChildren();
  $('empty').hidden = false;
  loadConversations();
});

// ── Boot ──────────────────────────────────────────────────────────────────────
async function start() {
  state.me = await api('GET', '/api/users/me');
  $('me-name').textContent = state.me.displayName;
  $('me-username').textContent = `@${state.me.username}`;
  $('auth').hidden = true;
  $('app').hidden = false;
  await loadConversations();
  connect();
}

if (state.token) {
  start().catch(showAuth);
} else {
  showAuth();
}
