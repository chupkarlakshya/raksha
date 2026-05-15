// SafePath Emergency Response - Client Logic
const API_BASE = ""; 
let map = null;
let marker = null;

const responders = [
  { name: "Aman Singh", phone: "8889800445", role: "North Zone" },
  { name: "Support Team 1", phone: "8889091106", role: "Central Zone" },
  { name: "Aman Singh", phone: "8889800445", role: "South Zone" },
  { name: "Support Team 2", phone: "8889091106", role: "East Zone" }
];

/**
 * Robust API request helper with Admin Auth
 */
async function apiRequest(path, options = {}) {
  const headers = { 
    "Content-Type": "application/json",
    "Authorization": "Basic " + btoa("admin:admin")
  };
  
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { ...headers, ...options.headers }
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `HTTP ${response.status}`);
  }
  return response.json();
}

/**
 * Leaflet Map Initialization (Light Theme)
 */
function updateMap(lat, lng) {
  if (!map) {
    map = L.map('sos-map', { zoomControl: false }).setView([lat, lng], 16);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '©OpenStreetMap'
    }).addTo(map);
    
    marker = L.circleMarker([lat, lng], {
        radius: 12,
        color: '#ff3c5f',
        fillColor: '#ff3c5f',
        fillOpacity: 0.7,
        weight: 3
    }).addTo(map);
  } else {
    map.setView([lat, lng], 16);
    marker.setLatLng([lat, lng]);
  }
}

/**
 * Dispatch Logic
 */
async function sendDispatch(to, message) {
  try {
    const res = await apiRequest("/api/dispatch", {
      method: "POST",
      body: JSON.stringify({ to, message })
    });
    if (res.success) {
      alert(`Success: Emergency alert sent to ${to}`);
    } else {
      alert("Dispatch system error. Contact IT.");
    }
  } catch (err) {
    console.error("Dispatch Error:", err);
    alert(`Connectivity Issue: ${err.message}`);
  }
}

function triggerSms(phone) {
  const msg = "URGENT: SOS Alert. Check: http://192.168.1.4:8080/response.html";
  sendDispatch(phone, msg);
}

document.getElementById("broadcast-all-btn").onclick = () => {
    if (confirm("Send emergency broadcast to ALL responders?")) {
        responders.forEach(r => triggerSms(r.phone));
    }
};

function renderResponders() {
  const list = document.getElementById("responder-list");
  if (!list) return;
  list.innerHTML = responders.map(r => `
    <div class="responder-item">
      <div class="item-info">
        <strong>${r.name}</strong>
        <span>${r.phone} · ${r.role}</span>
      </div>
      <button class="btn-sm" onclick="triggerSms('${r.phone}')">Notify</button>
    </div>
  `).join("");
}

/**
 * Real-time SOS Sync
 */
async function syncEmergencyState() {
  try {
    const sosEvents = await apiRequest("/api/sos");
    const active = sosEvents.filter(s => s.status === "active");

    const empty = document.getElementById("no-active-sos");
    const card = document.getElementById("focus-card");

    if (active.length === 0) {
      empty.style.display = "flex";
      card.style.display = "none";
      return;
    }

    const current = active[0];
    empty.style.display = "none";
    card.style.display = "block";

    document.getElementById("sos-source").textContent = (current.userId || "RECEPTION").toUpperCase();
    document.getElementById("sos-coords").textContent = `📍 ${current.latitude.toFixed(5)}, ${current.longitude.toFixed(5)}`;
    
    const date = new Date(current.createdAt);
    document.getElementById("stat-updated").textContent = isNaN(date.getTime()) ? "Just Now" : date.toLocaleTimeString();
    document.getElementById("stat-routed").textContent = `${responders.length} Active`;

    updateMap(current.latitude, current.longitude);

    document.getElementById("resolve-btn").onclick = async () => {
      if(confirm("Confirm: Resolve and archive this emergency?")) {
          await apiRequest(`/api/sos/${current.id}`, {
            method: "PATCH",
            body: JSON.stringify({ status: "resolved" })
          });
          syncEmergencyState();
      }
    };
  } catch (err) {
    console.error("State Sync Failure:", err);
  }
}

// Start Services
renderResponders();
syncEmergencyState();
setInterval(syncEmergencyState, 5000);
