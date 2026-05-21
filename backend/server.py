"""SafePath backend.

Stdlib-only HTTP server backed by SQLite.

Reads config from `.env` at the repo root. See `.env.example` for the
expected fields.
"""

import base64
import http.server
import json
import os
import sqlite3
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone, timedelta
from urllib.parse import parse_qs, urlparse
try:
    from twilio.rest import Client
    TWILIO_AVAILABLE = True
except ImportError:
    TWILIO_AVAILABLE = False
    print("Warning: 'twilio' module not found. SMS notifications will be logged to console only.")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(BASE_DIR)
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_FILE = os.path.join(DATA_DIR, "safepath.db")
SEED_FILE = os.path.join(DATA_DIR, "seed.json")
LEGACY_JSON_FILE = os.path.join(DATA_DIR, "safepath.json")
PUBLIC_DIR = os.path.join(BASE_DIR, "public")


# -------------------------------------------------------------- env loader ---

def _load_env():
    """Tiny `.env` parser so we don't add python-dotenv as a dep."""
    env_path = os.path.join(ROOT_DIR, ".env")
    if not os.path.exists(env_path):
        return
    with open(env_path, "r", encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, _, val = line.partition("=")
            key = key.strip()
            val = val.strip().strip('"').strip("'")
            # Don't clobber values explicitly set in the shell.
            os.environ.setdefault(key, val)


_load_env()

PORT = int(os.environ.get("PORT", "8080"))
TWILIO_SID = os.environ.get("TWILIO_ACCOUNT_SID", "")
TWILIO_TOKEN = os.environ.get("TWILIO_AUTH_TOKEN", "")
TWILIO_FROM = os.environ.get("TWILIO_FROM_NUMBER", "")
DEMO_MODE = os.environ.get("DEMO_MODE", "true").lower() in ("1", "true", "yes", "on")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ADMIN_PASS", "admin")
RISK_MODEL_PATH = os.path.join(
    BASE_DIR, os.environ.get("RISK_MODEL_PATH", "data/risk_model.joblib")
)


# ----------------------------------------------------------------- twilio ---

def send_sms(to_number, message_body):
    """Sends an SMS via Twilio or logs to console if Twilio is unavailable."""
    if DEMO_MODE:
        print(f"[DEMO_MODE] To {to_number}: {message_body}")
        return True

    if not TWILIO_AVAILABLE:
        print(f"\n[MOCK SMS] To: {to_number}")
        print(f"[MOCK SMS] Body: {message_body}\n")
        return True

    if not all([TWILIO_SID, TWILIO_TOKEN, TWILIO_FROM]):
        print("\n[MOCK SMS (Missing Keys)] To: " + to_number)
        print("[MOCK SMS (Missing Keys)] Body: " + message_body + "\n")
        return False

    try:
        print(f"[SMS AUDIT] Sending to {to_number}...")
        client = Client(TWILIO_SID, TWILIO_TOKEN)
        client.messages.create(
            body=message_body,
            from_=TWILIO_FROM,
            to=to_number
        )
        print(f"[SMS AUDIT] Success: Sent to {to_number}")
        return True
    except Exception as e:
        print(f"[SMS AUDIT] FAILED: {to_number} -> {e}")
        return False


# ---------------------------------------------------------------- sqlite ---

SCHEMA = """
CREATE TABLE IF NOT EXISTS incidents (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    description TEXT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    severity INTEGER DEFAULT 3,
    status TEXT DEFAULT 'pending',
    reported_by TEXT,
    created_at TEXT NOT NULL,
    reviewed_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_created ON incidents(created_at);

CREATE TABLE IF NOT EXISTS sos_events (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    contact TEXT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    status TEXT DEFAULT 'active',
    created_at TEXT NOT NULL,
    resolved_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_sos_status ON sos_events(status);
CREATE INDEX IF NOT EXISTS idx_sos_created ON sos_events(created_at);

CREATE TABLE IF NOT EXISTS risk_zones (
    id TEXT PRIMARY KEY,
    name TEXT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius_meters REAL,
    risk_score REAL,
    source TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS hazard_zones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius_meters INTEGER NOT NULL DEFAULT 200,
    risk_boost REAL NOT NULL DEFAULT 1000.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    creator TEXT DEFAULT 'admin',
    note TEXT
);

CREATE TABLE IF NOT EXISTS tracking_sessions (
    session_id TEXT PRIMARY KEY,
    emergency_contact TEXT,
    latest_lat REAL,
    latest_lng REAL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    active INTEGER DEFAULT 1
);
"""


def db_connect():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db():
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)
    fresh = not os.path.exists(DB_FILE)
    conn = db_connect()
    try:
        conn.executescript(SCHEMA)
        conn.commit()
        if fresh:
            _seed_db(conn)
    finally:
        conn.close()


def _seed_db(conn):
    """Seed the DB. Pulls from the legacy safepath.json if present, otherwise seed.json."""
    source = LEGACY_JSON_FILE if os.path.exists(LEGACY_JSON_FILE) else SEED_FILE
    if not os.path.exists(source):
        return
    with open(source, "r", encoding="utf-8") as f:
        seed = json.load(f)
    cur = conn.cursor()
    for item in seed.get("incidents", []):
        cur.execute(
            "INSERT OR IGNORE INTO incidents (id, type, description, latitude, longitude, severity, status, reported_by, created_at, reviewed_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.get("id") or f"inc_seed_{uuid.uuid4().hex[:8]}",
                str(item.get("type", "OTHER")).upper(),
                item.get("description", ""),
                float(item.get("latitude", item.get("lat", 0))),
                float(item.get("longitude", item.get("lng", 0))),
                int(item.get("severity", 3)),
                item.get("status", "pending"),
                item.get("reportedBy", "demo"),
                item.get("createdAt", datetime.now(timezone.utc).isoformat()),
                item.get("reviewedAt"),
            ),
        )
    for item in seed.get("sosEvents", []):
        cur.execute(
            "INSERT OR IGNORE INTO sos_events (id, user_id, contact, latitude, longitude, status, created_at, resolved_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.get("id") or f"sos_seed_{uuid.uuid4().hex[:8]}",
                item.get("userId", "anonymous"),
                item.get("contact", ""),
                float(item.get("latitude", item.get("lat", 0))),
                float(item.get("longitude", item.get("lng", 0))),
                item.get("status", "active"),
                item.get("createdAt", datetime.now(timezone.utc).isoformat()),
                item.get("resolvedAt"),
            ),
        )
    for item in seed.get("riskZones", []):
        cur.execute(
            "INSERT OR IGNORE INTO risk_zones (id, name, latitude, longitude, radius_meters, risk_score, source, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.get("id") or f"zone_seed_{uuid.uuid4().hex[:8]}",
                item.get("name", ""),
                float(item.get("latitude", 0)),
                float(item.get("longitude", 0)),
                float(item.get("radiusMeters", 200)),
                float(item.get("riskScore", 50)),
                item.get("source", "seed"),
                item.get("updatedAt", datetime.now(timezone.utc).isoformat() + "Z"),
            ),
        )
    conn.commit()


def _row_to_incident(row):
    return {
        "id": row["id"],
        "type": row["type"],
        "description": row["description"] or "",
        "latitude": row["latitude"],
        "longitude": row["longitude"],
        "severity": row["severity"],
        "status": row["status"],
        "reportedBy": row["reported_by"],
        "createdAt": row["created_at"],
        "reviewedAt": row["reviewed_at"],
    }


def _row_to_sos(row):
    return {
        "id": row["id"],
        "userId": row["user_id"],
        "contact": row["contact"],
        "latitude": row["latitude"],
        "longitude": row["longitude"],
        "status": row["status"],
        "createdAt": row["created_at"],
        "resolvedAt": row["resolved_at"],
    }


def _row_to_zone(row):
    return {
        "id": row["id"],
        "name": row["name"],
        "latitude": row["latitude"],
        "longitude": row["longitude"],
        "radiusMeters": row["radius_meters"],
        "riskScore": row["risk_score"],
        "source": row["source"],
        "updatedAt": row["updated_at"],
    }


def _row_to_hazard_zone(row):
    return {
        "id": row["id"],
        "lat": row["latitude"],
        "lng": row["longitude"],
        "radius_m": row["radius_meters"],
        "risk": row["risk_boost"],
        "expires_at": row["expires_at"],
        "note": row["note"]
    }


def get_overview(conn):
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM incidents")
    total = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM incidents WHERE status='verified'")
    verified = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM incidents WHERE status='pending'")
    pending = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sos_events WHERE status='active'")
    active_sos = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM risk_zones WHERE risk_score >= 65")
    high_zones = cur.fetchone()[0]
    return {
        "totalIncidents": total,
        "verifiedReports": verified,
        "pendingReports": pending,
        "activeSos": active_sos,
        "highRiskZones": high_zones,
        "lastUpdated": datetime.now(timezone.utc).isoformat() + "Z",
    }


# -------------------------------------------------------------- ML serving ---

_risk_model = None
_risk_model_load_attempted = False


def get_risk_model():
    global _risk_model, _risk_model_load_attempted
    if _risk_model_load_attempted:
        return _risk_model
    _risk_model_load_attempted = True
    if not os.path.exists(RISK_MODEL_PATH):
        print(f"[risk-model] not found at {RISK_MODEL_PATH} — predictions disabled")
        return None
    try:
        # Check if dependencies are available
        import joblib
        import numpy
        _risk_model = joblib.load(RISK_MODEL_PATH)
        print(f"[risk-model] loaded successfully from {RISK_MODEL_PATH}")
    except ImportError:
        print("[risk-model] joblib or numpy not installed — ML features disabled")
        _risk_model = None
    except Exception as e:
        print(f"[risk-model] load failed: {e}")
        _risk_model = None
    return _risk_model


def score_risk(latitude, longitude, hour, day_of_week):
    model = get_risk_model()
    if model is None:
        return None
    try:
        import numpy as np
        X = np.array([[latitude, longitude, hour, day_of_week]])
        score = float(model.predict(X)[0])
        # Clamp to 0-100 for the app.
        return max(0.0, min(100.0, score))
    except Exception as e:
        print(f"[risk-model] predict failed: {e}")
        return None


# ---------------------------------------------------------------- handler ---

class SafePathHandler(http.server.BaseHTTPRequestHandler):

    # --- shared helpers ---------------------------------------------------

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header(
            "Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS"
        )
        self.send_header(
            "Access-Control-Allow-Headers", "Content-Type, Authorization"
        )
        super().end_headers()

    def log_message(self, fmt, *args):
        # Slightly tidier console output than the default.
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.end_headers()
        self.wfile.write(body)

    def read_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {}

    def is_admin(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return False
        try:
            decoded = base64.b64decode(header.split(" ", 1)[1]).decode("utf-8")
            user, pw = decoded.split(":", 1)
            is_valid = (user == ADMIN_USER and pw == ADMIN_PASS)
            if not is_valid:
                print(f"[AUTH] Denied login for user: {user}")
            return is_valid
        except Exception as e:
            print(f"CRITICAL SERVER ERROR: {e}")
            return False

    def require_admin(self):
        if self.is_admin():
            return True
        # Send 401 with WWW-Authenticate to trigger browser login prompt
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="SafePath Admin"')
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"error": "unauthorized"}).encode())
        return False

    def handle_one_request(self):
        """Override to provide a global catch-all for 500 errors."""
        try:
            super().handle_one_request()
        except Exception as e:
            print(f"[server] critical error: {e}")
            try:
                self.send_error(500, str(e))
            except Exception:
                pass

    # --- verbs ------------------------------------------------------------

    def do_OPTIONS(self):
        self.send_response(204)
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/"):
            self.handle_api_get(parsed.path, parse_qs(parsed.query))
        else:
            self.handle_static(parsed.path)

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/"):
            self.handle_api_post(parsed.path)
        else:
            self.send_error(404)

    def do_DELETE(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/"):
            self.handle_api_delete(parsed.path)
        else:
            self.send_error(404)

    def do_PATCH(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/"):
            self.handle_api_patch(parsed.path)
        else:
            self.send_error(404)

    # --- static -----------------------------------------------------------

    # Pages that need admin auth (the dashboard's mutation endpoints already
    # require it; gating the HTML keeps random URL-pasters out too).
    _PROTECTED_PAGES = {"/", "/index.html", "/response.html"}

    def handle_static(self, path):
        # Normalize path for case-insensitive check (Windows security)
        norm_path = path.lower()
        if norm_path == "/":
            norm_path = "/index.html"
            
        is_protected = any(norm_path == p.lower() for p in self._PROTECTED_PAGES)
        if is_protected and not self.require_admin():
            return

        if path == "/":
            path = "/index.html"
        
        # Security: Normalize and ensure path is within PUBLIC_DIR
        relative_path = path.lstrip("/")
        file_path = os.path.normpath(os.path.join(PUBLIC_DIR, relative_path))
        
        if not file_path.startswith(os.path.normpath(PUBLIC_DIR)):
            self.send_error(403, "Access denied")
            return
            
        if not os.path.isfile(file_path):
            # Fallback for SPA routing if needed, but here just 404
            self.send_error(404, "File not found")
            return

        # Improved Mime-type mapping
        ext = os.path.splitext(file_path)[1].lower()
        mime_types = {
            ".html": "text/html; charset=utf-8",
            ".css": "text/css; charset=utf-8",
            ".js": "application/javascript; charset=utf-8",
            ".json": "application/json; charset=utf-8",
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".svg": "image/svg+xml",
            ".ico": "image/x-icon"
        }
        content_type = mime_types.get(ext, "application/octet-stream")

        with open(file_path, "rb") as f:
            data = f.read()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    # --- GET routes -------------------------------------------------------

    def handle_api_get(self, path, query):
        conn = db_connect()
        try:
            if path == "/api/health":
                self.send_json(200, {"ok": True, "service": "SafePath", "demoMode": DEMO_MODE})

            elif path == "/api/overview":
                self.send_json(200, get_overview(conn))

            elif path == "/api/incidents":
                status = query.get("status", [None])[0]
                if status:
                    rows = conn.execute(
                        "SELECT * FROM incidents WHERE status=? ORDER BY created_at DESC",
                        (status,),
                    ).fetchall()
                else:
                    rows = conn.execute(
                        "SELECT * FROM incidents ORDER BY created_at DESC"
                    ).fetchall()
                self.send_json(200, [_row_to_incident(r) for r in rows])

            elif path == "/api/sos":
                rows = conn.execute(
                    "SELECT * FROM sos_events ORDER BY created_at DESC"
                ).fetchall()
                self.send_json(200, [_row_to_sos(r) for r in rows])

            elif path == "/api/risk-zones":
                rows = conn.execute("SELECT * FROM risk_zones").fetchall()
                seeded = [_row_to_zone(r) for r in rows]
                verified = conn.execute(
                    "SELECT * FROM incidents WHERE status='verified'"
                ).fetchall()
                derived = []
                for v in verified:
                    sev = int(v["severity"] or 3)
                    derived.append({
                        "id": f"report_zone_{v['id']}",
                        "name": (v["type"] or "OTHER").replace("_", " "),
                        "latitude": v["latitude"],
                        "longitude": v["longitude"],
                        "radiusMeters": 220 + sev * 40,
                        "riskScore": min(100, 45 + sev * 10),
                        "source": "verified crowd report",
                        "updatedAt": v["reviewed_at"] or v["created_at"],
                    })
                self.send_json(200, seeded + derived)

            elif path == "/api/risk":
                # Score a single (lat, lng, [hour], [day]).
                try:
                    lat = float(query.get("lat", [None])[0])
                    lng = float(query.get("lng", [None])[0])
                except (TypeError, ValueError):
                    self.send_json(400, {"error": "lat and lng required"})
                    return
                
                # Default to now if missing
                now = datetime.now(timezone.utc)
                hour = int(query.get("hour", [now.hour])[0])
                day = int(query.get("day", [now.weekday()])[0])
                score = score_risk(lat, lng, hour, day)
                if score is None:
                    self.send_json(503, {"error": "risk model not available; train it first"})
                    return
                self.send_json(200, {
                    "lat": lat, "lng": lng, "hour": hour, "day": day,
                    "riskScore": score,
                })

            elif path == "/api/risk-grid":
                # Grid scoring for the app's heatmap overlay.
                try:
                    min_lat = float(query.get("minLat", ["22.65"])[0])
                    max_lat = float(query.get("maxLat", ["22.80"])[0])
                    max_lng = float(query.get("maxLng", ["75.95"])[0])
                    
                    steps = max(8, min(32, int(query.get("steps", ["20"])[0])))
                    now = datetime.now(timezone.utc)
                    hour = int(query.get("hour", [now.hour])[0])
                    day = int(query.get("day", [now.weekday()])[0])
                except (ValueError, TypeError, IndexError):
                    self.send_json(400, {"error": "bad query parameters"})
                    return

                model = get_risk_model()
                if model is None:
                    self.send_json(503, {"error": "risk model not available; train it first"})
                    return

                import numpy as np
                lats = np.linspace(min_lat, max_lat, steps)
                lngs = np.linspace(min_lng, max_lng, steps)
                grid = []
                Xs = []
                for la in lats:
                    for ln in lngs:
                        Xs.append([la, ln, hour, day])
                preds = model.predict(np.array(Xs))
                idx = 0
                for la in lats:
                    for ln in lngs:
                        score = max(0.0, min(100.0, float(preds[idx])))
                        grid.append({"lat": float(la), "lng": float(ln), "score": score})
                        idx += 1
                self.send_json(200, {
                    "hour": hour, "day": day, "steps": steps, "cells": grid,
                })

            elif path == "/api/hazard-zones":
                # only return active zones
                now_utc = datetime.now(timezone.utc).isoformat()
                rows = conn.execute(
                    "SELECT * FROM hazard_zones WHERE expires_at > ? ORDER BY expires_at ASC",
                    (now_utc,)
                ).fetchall()
                self.send_json(200, [_row_to_hazard_zone(r) for r in rows])

            elif path == "/api/tracking/location":
                sid = query.get("session_id", [None])[0]
                if not sid:
                    self.send_json(400, {"error": "session_id required"})
                    return
                row = conn.execute(
                    "SELECT latest_lat, latest_lng FROM tracking_sessions WHERE session_id=? AND active=1",
                    (sid,)
                ).fetchone()
                if not row:
                    self.send_json(404, {"error": "session not found"})
                    return
                self.send_json(200, {"lat": row["latest_lat"], "lng": row["latest_lng"]})

            else:
                # Check if it's a tracking page request: /track/<session_id>
                parts = parsed.path.strip("/").split("/")
                if parts[0] == "track" and len(parts) == 2:
                    self.handle_tracking_page(parts[1], conn)
                else:
                    self.send_error(404)
        finally:
            conn.close()

    # --- GET routes -------------------------------------------------------

    def handle_tracking_page(self, session_id, conn):
        """Serve a real-time browser tracking page for the given session."""
        row = conn.execute(
            "SELECT * FROM tracking_sessions WHERE session_id=? AND active=1",
            (session_id,)
        ).fetchone()

        if not row:
            self.send_response(404)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(b"<h2>Tracking session not found or expired.</h2>")
            return

        lat = row["latest_lat"] or 22.7196
        lng = row["latest_lng"] or 75.8577
        base_url = f"http://{self.headers.get('Host', 'localhost:8080')}"

        html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SafePath Live Tracking</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body {{ margin: 0; font-family: -apple-system, sans-serif; background: #0f172a; color: white; }}
  #map {{ width: 100vw; height: 75vh; }}
  #status {{ padding: 16px 20px; background: #1e293b; border-top: 1px solid #334155; }}
  #status h2 {{ margin: 0 0 4px; font-size: 18px; }}
  #status p {{ margin: 0; color: #94a3b8; font-size: 13px; }}
  #coords {{ font-size: 13px; color: #38bdf8; margin-top: 6px; font-weight: bold; }}
  .pulse-ring {{ border-radius: 50%; animation: pulse 2s infinite; }}
  @keyframes pulse {{ 0%,100%{{ opacity:1; }} 50%{{ opacity:0.4; }} }}
</style>
</head>
<body>
<div id="map"></div>
<div id="status">
  <h2>🛡 SafePath Live Tracking</h2>
  <p>This person is sharing their real-time location with you.</p>
  <div id="coords">📍 {lat:.5f}, {lng:.5f}</div>
</div>
<script>
var map = L.map('map').setView([{lat}, {lng}], 15);
L.tileLayer('https://{{s}}.tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png', {{
    attribution: '© OpenStreetMap'
}}).addTo(map);

var marker = L.circleMarker([{lat}, {lng}], {{
    radius: 12, color: '#ef4444', fillColor: '#ef4444',
    fillOpacity: 0.9, weight: 3
}}).addTo(map).bindPopup('Current Location').openPopup();

var SESSION_ID = '{session_id}';
var BASE = '{base_url}';

function refresh() {{
    fetch(BASE + '/api/tracking/location?session_id=' + SESSION_ID)
        .then(r => r.json())
        .then(data => {{
            if (data.lat && data.lng) {{
                var pos = [data.lat, data.lng];
                marker.setLatLng(pos);
                map.panTo(pos);
                document.getElementById('coords').textContent = '📍 ' + data.lat.toFixed(5) + ', ' + data.lng.toFixed(5);
            }}
        }}).catch(e => console.log('Update failed', e));
}}

setInterval(refresh, 5000);
</script>
</body>
</html>"""
        body = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # --- POST routes ------------------------------------------------------

    def handle_api_post(self, path):
        body = self.read_json_body()
        conn = db_connect()
        try:
            if path == "/api/tracking/start":
                try:
                    lat = float(body.get("lat", 22.7196))
                    lng = float(body.get("lng", 75.8577))
                except (TypeError, ValueError):
                    lat, lng = 22.7196, 75.8577
                contact = str(body.get("emergency_contact", ""))
                sid = uuid.uuid4().hex
                expires = datetime.now(timezone.utc) + timedelta(minutes=30)
                conn.execute(
                    "INSERT INTO tracking_sessions (session_id, emergency_contact, latest_lat, latest_lng, expires_at) VALUES (?,?,?,?,?)",
                    (sid, contact, lat, lng, expires.isoformat())
                )
                conn.commit()
                host = self.headers.get("Host", f"localhost:{PORT}")
                share_url = f"http://{host}/track/{sid}"
                # Send SMS to emergency contact
                if contact:
                    msg = f"SafePath LIVE TRACKING: Someone is sharing their live location with you. Track them here: {share_url}"
                    send_sms(contact, msg)
                self.send_json(201, {"session_id": sid, "share_url": share_url})

            elif path == "/api/tracking/update":
                sid = body.get("session_id", "")
                try:
                    lat = float(body.get("lat"))
                    lng = float(body.get("lng"))
                except (TypeError, ValueError):
                    self.send_json(400, {"error": "lat and lng required"})
                    return
                conn.execute(
                    "UPDATE tracking_sessions SET latest_lat=?, latest_lng=? WHERE session_id=? AND active=1",
                    (lat, lng, sid)
                )
                conn.commit()
                self.send_json(200, {"ok": True})

            elif path == "/api/tracking/stop":
                sid = body.get("session_id", "")
                conn.execute("UPDATE tracking_sessions SET active=0 WHERE session_id=?", (sid,))
                conn.commit()
                self.send_json(200, {"ok": True, "stopped": True})

            elif path == "/api/incidents":
                try:
                    lat = float(body.get("latitude", body.get("lat")))
                    lng = float(body.get("longitude", body.get("lng")))
                except (TypeError, ValueError):
                    self.send_json(400, {"error": "latitude and longitude required"})
                    return
                new_id = f"inc_{int(time.time())}_{uuid.uuid4().hex[:6]}"
                conn.execute(
                    "INSERT INTO incidents (id, type, description, latitude, longitude, severity, status, reported_by, created_at) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        new_id,
                        str(body.get("type", "OTHER")).upper(),
                        str(body.get("description", ""))[:500],
                        lat,
                        lng,
                        min(5, max(1, int(body.get("severity", 3)))),
                        body.get("status", "pending"),
                        body.get("reportedBy", "anonymous"),
                        datetime.now(timezone.utc).isoformat(),
                    ),
                )
                conn.commit()
                row = conn.execute("SELECT * FROM incidents WHERE id=?", (new_id,)).fetchone()
                self.send_json(201, _row_to_incident(row))

            elif path == "/api/sos":
                try:
                    lat = float(body.get("latitude", body.get("lat")))
                    lng = float(body.get("longitude", body.get("lng")))
                except (TypeError, ValueError):
                    self.send_json(400, {"error": "latitude and longitude required"})
                    return
                new_id = f"sos_{int(time.time())}_{uuid.uuid4().hex[:6]}"
                contact = str(body.get("contact", ""))
                conn.execute(
                    "INSERT INTO sos_events (id, user_id, contact, latitude, longitude, status, created_at) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (
                        new_id,
                        body.get("userId", "anonymous"),
                        contact,
                        lat,
                        lng,
                        "active",
                        datetime.now(timezone.utc).isoformat(),
                    ),
                )
                conn.commit()

                # Fan out SMS — to the user's saved contact and to a 100/108
                # advisory list. Suppressed entirely if DEMO_MODE.
                maps_link = f"https://www.google.com/maps/search/?api=1&query={lat},{lng}"
                msg = f"EMERGENCY SOS from SafePath Indore. Location: {maps_link}"
                sms_results = []
                if contact:
                    sms_results.append({"to": contact, "ok": send_sms(contact, msg)})
                # Optional fan-out list from env, comma-separated.
                extra = [n.strip() for n in os.environ.get("SOS_FANOUT_NUMBERS", "").split(",") if n.strip()]
                for number in extra:
                    sms_results.append({"to": number, "ok": send_sms(number, msg)})

                row = conn.execute("SELECT * FROM sos_events WHERE id=?", (new_id,)).fetchone()
                payload = _row_to_sos(row)
                payload["smsDispatched"] = sms_results
                payload["demoMode"] = DEMO_MODE
                self.send_json(201, payload)

            elif path == "/api/dispatch":
                if not self.require_admin(): return
                to = body.get("to", "")
                msg = body.get("message", "Emergency Alert")
                ok = send_sms(to, msg)
                self.send_json(200, {"success": ok, "demoMode": DEMO_MODE})

            elif path == "/api/hazard-zones":
                if not self.require_admin():
                    return
                lat = body.get("lat")
                lng = body.get("lng")
                radius = body.get("radius_m", 200)
                duration = body.get("duration_minutes", 30)
                note = body.get("note", "")
                risk_boost = body.get("risk_boost", 1000.0)
                
                if lat is None or lng is None:
                    self.send_json(400, {"error": "lat and lng required"})
                    return

                expires = datetime.now(timezone.utc) + timedelta(minutes=int(duration))
                cursor = conn.execute(
                    "INSERT INTO hazard_zones (latitude, longitude, radius_meters, risk_boost, expires_at, note) VALUES (?,?,?,?,?,?)",
                    (lat, lng, radius, risk_boost, expires.isoformat(), note)
                )
                conn.commit()
                new_id = cursor.lastrowid
                row = conn.execute("SELECT * FROM hazard_zones WHERE id=?", (new_id,)).fetchone()
                self.send_json(201, _row_to_hazard_zone(row))

            else:
                self.send_error(404)
        finally:
            conn.close()

    # --- PATCH routes -----------------------------------------------------

    def handle_api_patch(self, path):
        # All mutations require admin.
        if not self.require_admin():
            return
        parts = path.strip("/").split("/")  # api/incidents/ID
        if len(parts) < 3:
            self.send_error(404)
            return
        resource, resource_id = parts[1], parts[2]
        patch = self.read_json_body()
        conn = db_connect()
        try:
            if resource == "incidents":
                row = conn.execute("SELECT * FROM incidents WHERE id=?", (resource_id,)).fetchone()
                if not row:
                    self.send_json(404, {"error": "not found"})
                    return
                new_status = patch.get("status", row["status"])
                new_sev = patch.get("severity", row["severity"])
                new_sev = min(5, max(1, int(new_sev))) if new_sev is not None else row["severity"]
                conn.execute(
                    "UPDATE incidents SET status=?, severity=?, reviewed_at=? WHERE id=?",
                    (new_status, new_sev, datetime.now(timezone.utc).isoformat() + "Z", resource_id),
                )
                conn.commit()
                updated = conn.execute("SELECT * FROM incidents WHERE id=?", (resource_id,)).fetchone()
                self.send_json(200, _row_to_incident(updated))

            elif resource == "sos":
                row = conn.execute("SELECT * FROM sos_events WHERE id=?", (resource_id,)).fetchone()
                if not row:
                    self.send_json(404, {"error": "not found"})
                    return
                new_status = patch.get("status", row["status"])
                resolved_at = (
                    datetime.now(timezone.utc).isoformat() + "Z"
                    if new_status == "resolved"
                    else row["resolved_at"]
                )
                conn.execute(
                    "UPDATE sos_events SET status=?, resolved_at=? WHERE id=?",
                    (new_status, resolved_at, resource_id),
                )
                conn.commit()
                updated = conn.execute("SELECT * FROM sos_events WHERE id=?", (resource_id,)).fetchone()
                self.send_json(200, _row_to_sos(updated))
            else:
                self.send_error(404)
        finally:
            conn.close()

    def handle_api_delete(self, path):
        if not self.require_admin():
            return
        parts = path.strip("/").split("/")  # api/hazard-zones/ID
        if len(parts) < 3:
            self.send_error(404)
            return
        resource, resource_id = parts[1], parts[2]
        conn = db_connect()
        try:
            if resource == "hazard-zones":
                conn.execute("DELETE FROM hazard_zones WHERE id=?", (resource_id,))
                conn.commit()
                self.send_json(200, {"status": "deleted"})
            else:
                self.send_error(404)
        finally:
            conn.close()


# ----------------------------------------------------------------- entry ---

    def handle_api_patch(self, path):
        if not self.require_admin():
            return
        body = self.read_json_body()
        conn = db_connect()
        try:
            if path.startswith("/api/sos/"):
                sos_id = path.replace("/api/sos/", "")
                status = body.get("status", "resolved")
                conn.execute(
                    "UPDATE sos_events SET status = ?, resolved_at = ? WHERE id = ?",
                    (status, datetime.now(timezone.utc).isoformat(), sos_id)
                )
                conn.commit()
                self.send_json(200, {"success": True, "id": sos_id, "status": status})
            elif path.startswith("/api/incidents/"):
                inc_id = path.replace("/api/incidents/", "")
                status = body.get("status", "verified")
                conn.execute(
                    "UPDATE incidents SET status = ?, reviewed_at = ? WHERE id = ?",
                    (status, datetime.now(timezone.utc).isoformat(), inc_id)
                )
                conn.commit()
                self.send_json(200, {"success": True, "id": inc_id, "status": status})
            else:
                self.send_error(404, "Endpoint not found")
        except Exception as e:
            self.send_error(500, str(e))
        finally:
            conn.close()

if __name__ == "__main__":
    init_db()
    get_risk_model()  # eager-load so the first request is fast
    server = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), SafePathHandler)
    print(f"SafePath backend running at http://localhost:{PORT}")
    print(f"  (Listening on all interfaces, DEMO_MODE={DEMO_MODE})")
    print(f"Admin login: {ADMIN_USER} / {'*' * len(ADMIN_PASS)}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    server.server_close()
