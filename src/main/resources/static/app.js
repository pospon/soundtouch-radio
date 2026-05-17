const API = '/api';

const el = {
    grid: document.getElementById('stations'),
    nowPlaying: document.getElementById('now-playing'),
    nowPlayingSub: document.getElementById('now-playing-sub'),
    volume: document.getElementById('volume-readout'),
    refresh: document.getElementById('refresh'),
    dot: document.getElementById('conn-dot'),
};

let stations = [];
let currentStationId = null;

async function jsonGet(path) {
    const res = await fetch(API + path);
    if (!res.ok) throw new Error(`${path} → ${res.status}`);
    return res.json();
}

async function jsonPost(path, body) {
    const res = await fetch(API + path, {
        method: 'POST',
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) throw new Error(`POST ${path} → ${res.status}`);
}

async function jsonPut(path, body) {
    const res = await fetch(API + path, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`PUT ${path} → ${res.status}`);
}

function renderStations() {
    el.grid.replaceChildren();
    for (const s of stations) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'tile';
        btn.dataset.id = s.id;
        if (s.id === currentStationId) btn.classList.add('current');
        const name = document.createElement('span');
        name.textContent = s.name;
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = s.button != null ? `Button ${s.button}` : 'Phone';
        btn.append(name, badge);
        btn.addEventListener('click', () => playStation(s.id));
        el.grid.append(btn);
    }
}

function renderState(snapshot) {
    if (!snapshot) return;
    const { stationId, stationName, source, volume, muted } = snapshot;
    currentStationId = stationId;
    if (stationName) {
        el.nowPlaying.textContent = stationName;
        el.nowPlayingSub.textContent = source ?? '';
    } else {
        el.nowPlaying.textContent = '—';
        el.nowPlayingSub.textContent = source ?? '';
    }
    if (volume != null) {
        el.volume.textContent = muted ? '🔇' : String(volume);
    }
    for (const tile of el.grid.children) {
        tile.classList.toggle('current', tile.dataset.id === currentStationId);
    }
}

async function playStation(id) {
    try {
        await jsonPost(`/play/${encodeURIComponent(id)}`);
    } catch (e) {
        console.error('play failed', e);
        alert('Failed to play: ' + e.message);
    }
}

async function pressKey(key) {
    try {
        await jsonPost(`/key/${encodeURIComponent(key)}`);
        // Volume keys change the speaker's actual volume; pull a refresh so the readout matches.
        if (key === 'VOLUME_UP' || key === 'VOLUME_DOWN' || key === 'MUTE') {
            await refreshVolume();
        }
    } catch (e) {
        console.error('key failed', e);
    }
}

async function refreshVolume() {
    try {
        const v = await jsonGet('/volume');
        renderState({ ...(latestSnapshot ?? {}), volume: v.volume, muted: v.muted });
    } catch (e) {
        console.error('volume fetch failed', e);
    }
}

let latestSnapshot = null;

async function refreshAll() {
    try {
        const [stns, np, vol] = await Promise.all([
            jsonGet('/stations'),
            jsonGet('/now-playing'),
            jsonGet('/volume'),
        ]);
        stations = stns;
        renderStations();
        latestSnapshot = { ...np, volume: vol.volume, muted: vol.muted };
        renderState(latestSnapshot);
    } catch (e) {
        console.error('refresh failed', e);
    }
}

function bindTransportButtons() {
    for (const btn of document.querySelectorAll('[data-key]')) {
        btn.addEventListener('click', () => pressKey(btn.dataset.key));
    }
}

function startSse() {
    const es = new EventSource(API + '/events');
    es.addEventListener('state', (e) => {
        try {
            latestSnapshot = JSON.parse(e.data);
            renderState(latestSnapshot);
            el.dot.classList.remove('down');
            el.dot.classList.add('up');
        } catch (err) {
            console.error('bad SSE payload', err);
        }
    });
    es.onerror = () => {
        el.dot.classList.remove('up');
        el.dot.classList.add('down');
    };
}

el.refresh.addEventListener('click', refreshAll);
bindTransportButtons();
refreshAll();
startSse();
