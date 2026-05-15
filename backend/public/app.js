const API = "";
let map;
let markersLayer = L.layerGroup();
let hazardCirclesLayer = L.layerGroup();
let policeLayer = L.layerGroup();
let showPolice = false;

const els = {
  statTotal: document.querySelector("#stat-total"),
  statPending: document.querySelector("#stat-pending"),
  statSos: document.querySelector("#stat-sos"),
  statHazards: document.querySelector("#stat-hazards"),
  mapCanvas: document.querySelector("#mapCanvas"),
  sosList: document.querySelector("#sosList"),
  pendingList: document.querySelector("#pendingList"),
  pendingBadge: document.querySelector("#pendingBadge"),
  incidentTable: document.querySelector("#incidentTable"),
  refreshButton: document.querySelector("#refreshButton"),
  policeToggle: document.querySelector("#policeToggle"),
  statusFilter: document.querySelector("#statusFilter"),
  hazardForm: document.querySelector("#hazardForm"),
  hazLat: document.querySelector("#haz-lat"),
  hazLng: document.querySelector("#haz-lng"),
  hazRadius: document.querySelector("#haz-radius"),
  hazDuration: document.querySelector("#haz-duration"),
  hazNote: document.querySelector("#haz-note"),
  activeZonesList: document.querySelector("#activeZonesList")
};

let previewCircle;

const policeStations = [
  { name: "Vijay Nagar Police Station", lat: 22.7533, lng: 75.8937 },
  { name: "Palasia Police Station", lat: 22.7244, lng: 75.8839 },
  { name: "Sarafa Police Station (Rajwada)", lat: 22.7196, lng: 75.8577 },
  { name: "Bhawarkuan Police Station", lat: 22.7001, lng: 75.8701 },
  { name: "Annapurna Police Station", lat: 22.6934, lng: 75.8344 },
  { name: "Khajrana Police Station", lat: 22.7441, lng: 75.9012 },
  { name: "Tukoganj Police Station", lat: 22.7231, lng: 75.8744 },
  { name: "Aerodrome Police Station", lat: 22.7248, lng: 75.8075 }
];

// Initialize Map with a cleaner, premium style
function initMap() {
  map = L.map('mapCanvas', {
    zoomControl: false,
    scrollWheelZoom: true
  }).setView([22.7196, 75.8577], 13);

  // Modern Carto Light basemap
  L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
    attribution: '&copy; OpenStreetMap &copy; CARTO'
  }).addTo(map);

  L.control.zoom({ position: 'bottomright' }).addTo(map);

  markersLayer.addTo(map);
  hazardCirclesLayer.addTo(map);
  policeLayer.addTo(map);
  renderPoliceStations();

  // Pick coordinates on Main Map for Blacklist
  map.on('click', (e) => {
    els.hazLat.value = e.latlng.lat.toFixed(6);
    els.hazLng.value = e.latlng.lng.toFixed(6);
    updateHazardPreview();
  });
}


function updateHazardPreview() {
  const lat = parseFloat(els.hazLat.value);
  const lng = parseFloat(els.hazLng.value);
  const radius = parseInt(els.hazRadius.value);

  if (isNaN(lat) || isNaN(lng)) return;

  if (previewCircle) {
    previewCircle.setLatLng([lat, lng]);
    previewCircle.setRadius(radius);
  } else {
    previewCircle = L.circle([lat, lng], {
      color: 'var(--accent)',
      fillColor: 'var(--accent)',
      fillOpacity: 0.2,
      radius: radius,
      dashArray: '5, 10'
    }).addTo(map);
  }
}

function renderPoliceStations() {
  policeLayer.clearLayers();
  policeStations.forEach(ps => {
    const marker = L.marker([ps.lat, ps.lng], {
      icon: L.divIcon({
        className: 'police-icon',
        html: `<div style="background: var(--blue); color: white; padding: 5px; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border: 3px solid white; box-shadow: 0 4px 12px rgba(0,0,0,0.15); font-size: 16px;">👮</div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 16]
      })
    });
    marker.bindPopup(`<strong>${ps.name}</strong><br>Status: Ready to Dispatch`);
    policeLayer.addLayer(marker);
  });
}

async function request(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    "Authorization": "Basic " + btoa("admin:admin")
  };
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...headers, ...options.headers }
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

const fmtType = (t) => String(t || "OTHER").replaceAll("_", " ");
const fmtTime = (v) => v ? new Date(v).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : "-";

function renderOverview(overview) {
  els.statTotal.textContent = overview.totalIncidents;
  els.statPending.textContent = overview.pendingReports;
  els.statSos.textContent = overview.activeSos;
  els.statHazards.textContent = overview.highRiskZones;
}

function renderMap(incidents, sosEvents) {
  markersLayer.clearLayers();

  incidents.forEach(inc => {
    const isVerified = inc.status === "verified";
    const color = isVerified ? "#059669" : "#d97706";
    const marker = L.circleMarker([inc.latitude, inc.longitude], {
      color: "#ffffff",
      fillColor: color,
      fillOpacity: 0.9,
      radius: 9,
      weight: 3
    });
    marker.bindPopup(`<strong>${fmtType(inc.type)}</strong><br>Status: ${inc.status}`);
    markersLayer.addLayer(marker);
  });

  sosEvents.filter(s => s.status === "active").forEach(sos => {
    const marker = L.divIcon({
        className: 'sos-marker',
        html: `<div style="width:24px; height:24px; background:var(--accent); border:3px solid #fff; border-radius:50%; box-shadow:0 0 0 10px var(--accent-glow); animation: pulse 1.5s infinite;"></div>`,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
    });
    const m = L.marker([sos.latitude, sos.longitude], { icon: marker });
    m.bindPopup(`<strong style="color:var(--accent)">ACTIVE SOS ALERT</strong><br>Time: ${fmtTime(sos.createdAt)}`);
    markersLayer.addLayer(m);
  });
}

function renderSos(sosEvents) {
  const active = sosEvents.filter(s => s.status === "active");
  if (active.length === 0) {
    els.sosList.innerHTML = `<div style="padding: 40px; text-align: center; color: var(--text-muted); font-size: 14px;">No active alerts</div>`;
    return;
  }
  els.sosList.innerHTML = active.map(item => `
    <div class="sos-item active" data-focus-lat="${item.latitude}" data-focus-lng="${item.longitude}">
      <div class="sos-item-header">
        <strong class="sos-user">SOS: ${item.userId || "anonymous"}</strong>
        <span class="sos-time">${fmtTime(item.createdAt)}</span>
      </div>
      <p class="sos-location">Indore Central · ${Number(item.latitude).toFixed(4)}, ${Number(item.longitude).toFixed(4)}</p>
      <button class="btn btn-primary btn-resolve" data-resolve-sos="${item.id}">Resolve Alert</button>
    </div>
  `).join("");
}

function renderPendingReports(incidents) {
  const pending = incidents.filter(i => i.status === "pending");
  if (!els.pendingList) return;

  if (pending.length === 0) {
    els.pendingBadge.style.display = "none";
    els.pendingList.innerHTML = `<div style="padding: 10px 20px; text-align: center; color: var(--text-muted); font-size: 13px;">No pending reports</div>`;
    return;
  }

  els.pendingBadge.style.display = "inline-block";
  els.pendingBadge.textContent = `${pending.length} NEW`;

  // Sort newest first
  pending.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

  els.pendingList.innerHTML = pending.map(item => `
    <div class="report-card ${item.status}" data-focus-lat="${item.latitude}" data-focus-lng="${item.longitude}">
      <div class="report-header">
        <strong class="report-type">${fmtType(item.type)}</strong>
        <span class="report-meta">Sev ${item.severity} · ${fmtTime(item.createdAt)}</span>
      </div>
      <p class="report-desc">${item.description || "No description"}</p>
      <p class="report-coords">📍 ${Number(item.latitude).toFixed(5)}, ${Number(item.longitude).toFixed(5)}</p>
      <div class="report-actions">
        <button class="btn btn-secondary btn-small" data-status="verified" data-id="${item.id}">✔ Verify</button>
        <button class="btn btn-secondary btn-small btn-blacklist"
                data-blacklist-lat="${item.latitude}" data-blacklist-lng="${item.longitude}" data-blacklist-id="${item.id}">🚫 Blacklist</button>
        <button class="btn btn-secondary btn-small btn-reject" data-status="rejected" data-id="${item.id}">✗ Reject</button>
      </div>
    </div>
  `).join("");
}

function renderIncidents(incidents) {
  const filter = els.statusFilter.value;
  const sorted = [...incidents].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const rows = filter ? sorted.filter(i => i.status === filter) : sorted;
  
  els.incidentTable.innerHTML = rows.map(item => {
    const isPending = item.status === "pending";
    const fmtDate = (v) => v ? new Date(v).toLocaleString([], {month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'}) : '-';
    
    return `
    <tr class="table-row ${item.status}">
      <td class="col-type">${isPending ? '🆕 ' : ''}${fmtType(item.type)}</td>
      <td class="col-sev">#${item.severity}</td>
      <td class="col-status"><span class="status-tag status-${item.status}">${item.status}</span></td>
      <td class="col-loc">${Number(item.latitude).toFixed(4)}, ${Number(item.longitude).toFixed(4)}</td>
      <td class="col-desc">${item.description || "-"}</td>
      <td class="col-time">${fmtDate(item.createdAt)}</td>
      <td class="col-actions">
        <div class="action-group">
          <button class="btn btn-secondary btn-xs" data-status="verified" data-id="${item.id}">Verify</button>
          <button class="btn btn-secondary btn-xs btn-blacklist" 
                  data-blacklist-lat="${item.latitude}" data-blacklist-lng="${item.longitude}" data-blacklist-id="${item.id}">Blacklist</button>
          <button class="btn btn-secondary btn-xs btn-reject" data-status="rejected" data-id="${item.id}">Reject</button>
        </div>
      </td>
    </tr>
  `}).join("");
}

function renderHazardZones(zones) {
  hazardCirclesLayer.clearLayers();
  if (zones.length === 0) {
    els.activeZonesList.innerHTML = `<div style="padding: 10px; text-align: center; color: var(--text-muted); font-size: 12px;">No active hazard zones</div>`;
    return;
  }

  els.activeZonesList.innerHTML = zones.map(z => `
    <div style="background: #fff; border: 1px solid var(--border); padding: 10px; border-radius: 8px; font-size: 12px; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <div style="font-weight: 700; color: var(--accent);">Zone #${z.id} (${z.radius_m}m)</div>
        <div style="color: var(--text-muted); font-size: 10px;">Expires: ${new Date(z.expires_at).toLocaleTimeString()}</div>
        <div style="font-style: italic;">${z.note || "No note"}</div>
      </div>
      <button class="btn-secondary" style="padding: 4px 8px; font-size: 10px; color: #ef4444;" data-delete-zone="${z.id}">Remove</button>
    </div>
  `).join("");

  zones.forEach(z => {
    L.circle([z.lat, z.lng], {
      color: '#ef4444',
      fillColor: '#ef4444',
      fillOpacity: 0.3,
      radius: z.radius_m
    }).addTo(hazardCirclesLayer);
  });
}

async function load() {
  try {
    const [overview, incidents, sosEvents, hazardZones] = await Promise.all([
      request("/api/overview"),
      request("/api/incidents"),
      request("/api/sos"),
      request("/api/hazard-zones")
    ]);
    renderOverview(overview);
    renderMap(incidents, sosEvents);
    renderSos(sosEvents);
    renderPendingReports(incidents);
    renderIncidents(incidents);
    renderHazardZones(hazardZones);
  } catch (err) {
    console.error("Dashboard Sync Failed:", err);
  }
}

document.addEventListener("click", async event => {
  const focusEl = event.target.closest("[data-focus-lat]");
  if (focusEl && !event.target.closest("button")) {
    map.setView([focusEl.dataset.focusLat, focusEl.dataset.focusLng], 16, { animate: true });
  }

  const statusBtn = event.target.closest("[data-status]");
  if (statusBtn) {
    await request(`/api/incidents/${statusBtn.dataset.id}`, {
      method: "PATCH",
      body: JSON.stringify({ status: statusBtn.dataset.status })
    });
    load();
  }

  const blacklistBtn = event.target.closest("[data-blacklist-id]");
  if (blacklistBtn) {
    els.hazLat.value = blacklistBtn.dataset.blacklistLat;
    els.hazLng.value = blacklistBtn.dataset.blacklistLng;
    els.hazNote.value = `Verified incident ${blacklistBtn.dataset.blacklistId}`;
    updateHazardPreview();
    
    // Switch to Hazards Tab
    document.querySelector('[data-tab="hazards"]').click();
    // Also auto-verify the incident
    await request(`/api/incidents/${blacklistBtn.dataset.blacklistId}`, {
      method: "PATCH",
      body: JSON.stringify({ status: "verified" })
    });
    load();
  }

  const deleteZoneBtn = event.target.closest("[data-delete-zone]");
  if (deleteZoneBtn) {
    await request(`/api/hazard-zones/${deleteZoneBtn.dataset.deleteZone}`, {
      method: "DELETE"
    });
    load();
  }

  const resolveBtn = event.target.closest("[data-resolve-sos]");
  if (resolveBtn) {
    await request(`/api/sos/${resolveBtn.dataset.resolveSos}`, {
      method: "PATCH",
      body: JSON.stringify({ status: "resolved" })
    });
    load();
  }
});

els.hazardForm.addEventListener("submit", async e => {
  e.preventDefault();
  try {
    const payload = {
      lat: parseFloat(els.hazLat.value),
      lng: parseFloat(els.hazLng.value),
      radius_m: parseInt(els.hazRadius.value),
      duration_minutes: parseInt(els.hazDuration.value),
      note: els.hazNote.value
    };
    
    if (isNaN(payload.lat) || isNaN(payload.lng)) {
      alert("Please click on the hazard map or enter coordinates.");
      return;
    }

    await request("/api/hazard-zones", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    
    els.hazNote.value = "";
    if (previewCircle) {
      map.removeLayer(previewCircle);
      previewCircle = null;
    }
    load();
    // alert("Hazard zone created successfully!"); // Optional: feedback
  } catch (err) {
    console.error("Hazard Creation Failed:", err);
    alert("Failed to create hazard zone. Check console for details.");
  }
});

els.hazRadius.addEventListener("change", updateHazardPreview);
els.hazLat.addEventListener("input", updateHazardPreview);
els.hazLng.addEventListener("input", updateHazardPreview);

els.policeToggle.addEventListener("click", () => {
  showPolice = !showPolice;
  if (showPolice) {
    map.addLayer(policeLayer);
    els.policeToggle.style.background = "var(--blue)";
    els.policeToggle.style.color = "white";
  } else {
    map.removeLayer(policeLayer);
    els.policeToggle.style.background = "#fff";
    els.policeToggle.style.color = "var(--text)";
  }
});

els.refreshButton.addEventListener("click", load);
els.statusFilter.addEventListener("change", load);

initMap();
map.removeLayer(policeLayer);
load();
setInterval(load, 5000);
