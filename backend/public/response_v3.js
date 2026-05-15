// SafePath RAKSHA v3 - Master Logic
const API_URL = "";
let tacticalMap = null;
let tacticalMarker = null;

const responderTeam = [
  { name: "Aman Singh", phone: "+918889800445", area: "North Zone" },
  { name: "Support Team 1", phone: "+918450045881", area: "Central Zone" },
  { name: "Aman Singh", phone: "+918889800445", area: "South Zone" },
  { name: "Support Team 2", phone: "+918450045881", area: "East Zone" }
];

/**
 * Universal Secure Request Handler
 */
async function secureCall(endpoint, method = "GET", data = null) {
  const options = {
    method,
    headers: {
      "Content-Type": "application/json",
      "Authorization": "Basic " + btoa("admin:admin")
    }
  };
  if (data) options.body = JSON.stringify(data);

  try {
    const response = await fetch(`${API_URL}${endpoint}`, options);
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`[${response.status}] ${errorText || 'Server Denied Access'}`);
    }
    return await response.json();
  } catch (err) {
    showSystemToast(err.message, true);
    throw err;
  }
}

/**
 * Map Engine
 */
function syncTacticalMap(lat, lng) {
  if (!tacticalMap) {
    tacticalMap = L.map('map', { zoomControl: false }).setView([lat, lng], 16);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(tacticalMap);
    tacticalMarker = L.circleMarker([lat, lng], {
        radius: 12, color: '#dc2626', fillColor: '#dc2626', fillOpacity: 0.6, weight: 3
    }).addTo(tacticalMap);
  } else {
    tacticalMap.setView([lat, lng], 16);
    tacticalMarker.setLatLng([lat, lng]);
  }
}

/**
 * Dispatch Controller
 */
async function triggerDispatch(phone, name) {
  const msg = `RAKSHA EMERGENCY: SOS triggered. View details: ${window.location.origin}/response_v3.html`;
  try {
    const result = await secureCall("/api/dispatch", "POST", { to: phone, message: msg });
    if (result.success) {
        showSystemToast(`Alert dispatched to ${name}`);
    } else {
        showSystemToast("Dispatch failed on server", true);
    }
  } catch (e) {
    console.error("Dispatch Error", e);
  }
}

document.getElementById("broadcast-all").onclick = () => {
    if (confirm("Initiate MASS BROADCAST to all responders?")) {
        responderTeam.forEach(r => triggerDispatch(r.phone, r.name));
    }
};

function showSystemToast(msg, isError = false) {
    const container = document.getElementById("toast-container");
    const toast = document.createElement("div");
    toast.className = `toast ${isError ? 'error' : ''}`;
    toast.textContent = msg;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}

/**
 * Interface Rendering
 */
function renderResponders() {
  const list = document.getElementById("responder-list");
  list.innerHTML = responderTeam.map(r => `
    <div class="resp-item">
      <div class="resp-info">
        <strong>${r.name}</strong>
        <span>${r.area} · ${r.phone}</span>
      </div>
      <button class="btn-notify" onclick="triggerDispatch('${r.phone}', '${r.name}')">Dispatch</button>
    </div>
  `).join("");
}

/**
 * Active SOS Synchronization
 */
async function pollEmergencySignals() {
  try {
    const alerts = await secureCall("/api/sos");
    const active = alerts.filter(a => a.status === "active");

    const empty = document.getElementById("no-sos");
    const focus = document.getElementById("sos-focus");

    if (active.length === 0) {
      empty.style.display = "flex";
      focus.style.display = "none";
      return;
    }

    const current = active[0];
    empty.style.display = "none";
    focus.style.display = "flex";

    document.getElementById("display-user").textContent = `USER: ${current.userId || "RECEPTION"}`;
    document.getElementById("display-coords").textContent = `📍 ${current.latitude.toFixed(5)}, ${current.longitude.toFixed(5)}`;
    
    const ts = new Date(current.createdAt);
    document.getElementById("display-time").textContent = isNaN(ts.getTime()) ? "JUST NOW" : ts.toLocaleTimeString();

    syncTacticalMap(current.latitude, current.longitude);

    document.getElementById("resolve-btn").onclick = async () => {
        if (confirm("Mark this incident as RESOLVED?")) {
            await secureCall(`/api/sos/${current.id}`, "PATCH", { status: "resolved" });
            pollEmergencySignals();
        }
    };
  } catch (err) {
    console.warn("Polling Failure", err);
  }
}

// Initialization
renderResponders();
pollEmergencySignals();
setInterval(pollEmergencySignals, 5000);
