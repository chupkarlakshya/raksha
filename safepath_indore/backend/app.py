import pandas as pd
import numpy as np
import os
import math
from datetime import datetime
from pathlib import Path
from fastapi import FastAPI, Body, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from typing import List, Dict, Any

# Twilio config — fill these in when ready
TWILIO_SID  = os.getenv("TWILIO_ACCOUNT_SID", "")
TWILIO_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "")
TWILIO_FROM = os.getenv("TWILIO_FROM", "")
TWILIO_TO   = os.getenv("TWILIO_TO", "")

app = FastAPI(title="SafePath Indore API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load dataset
DATA_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "archive", "data.csv"))
df = pd.read_csv(DATA_PATH)

# Recordings storage for SOS audio
RECORDINGS_DIR = Path(__file__).parent / "static" / "recordings"
RECORDINGS_DIR.mkdir(parents=True, exist_ok=True)

# Safety Scoring Logic weights
ACT_WEIGHTS = {
    "act302": 5, # Murder
    "act363": 4, # Kidnapping
    "act323": 3, # Assault
    "act379": 2, # Theft
    "act279": 1, # Rash Driving
    "act13": 1   # Gambling
}

ACT_NAMES = {
    "act302": "Murder (Act 302)",
    "act363": "Kidnapping (Act 363)",
    "act323": "Assault (Act 323)",
    "act379": "Theft (Act 379)",
    "act279": "Rash Driving (Act 279)",
    "act13": "Gambling (Act 13)"
}

# Calculate Risk_Weight and Crime_Type for each row
def compute_crime_info(row):
    max_weight = 0
    primary_crime = "Unknown"
    for act, weight in ACT_WEIGHTS.items():
        if row.get(act) == 1:
            if weight > max_weight:
                max_weight = weight
                primary_crime = ACT_NAMES[act]
    return pd.Series([max_weight, primary_crime])

df[["Risk_Weight", "Crime_Type"]] = df.apply(compute_crime_info, axis=1)
df["Crime_Severity"] = df["Risk_Weight"] * 2 # Scale to 1-10 for frontend colors

# Parse Timestamp
df["timestamp"] = pd.to_datetime(df["timestamp"], format="%d-%m-%Y %H:%M", errors='coerce')
df["Time"] = df["timestamp"].dt.strftime("%H:%M")

def get_time_of_day(dt):
    if pd.isna(dt): return "Unknown"
    hour = dt.hour
    if 6 <= hour < 12: return "Morning"
    elif 12 <= hour < 17: return "Afternoon"
    elif 17 <= hour < 21: return "Evening"
    else: return "Night"

df["Time_of_Day"] = df["timestamp"].apply(get_time_of_day)

# Rename lat/lon columns to match frontend expectations
df = df.rename(columns={"latitude": "Latitude", "longitude": "Longitude"})

@app.get("/api/crimes")
def get_crimes():
    # Only return relevant columns to save bandwidth
    crimes = df[["Latitude", "Longitude", "Crime_Type", "Time_of_Day", "Risk_Weight", "Time", "Crime_Severity"]]
    crimes_dict = crimes.to_dict(orient="records")
    
    # Strictly sanitize NaNs to None to prevent invalid JSON 'NaN' syntax errors in the frontend
    import math
    clean_crimes = [
        {k: (None if isinstance(v, float) and math.isnan(v) else v) for k, v in row.items()}
        for row in crimes_dict
    ]
    
    return {"crimes": clean_crimes}

def haversine_vectorized(lat1, lon1, lat2, lon2):
    # vectorized haversine formula
    R = 6371.0 # Earth radius in km
    dlat = np.radians(lat2 - lat1)
    dlon = np.radians(lon2 - lon1)
    a = np.sin(dlat/2)**2 + np.cos(np.radians(lat1)) * np.cos(np.radians(lat2)) * np.sin(dlon/2)**2
    c = 2 * np.arctan2(np.sqrt(a), np.sqrt(1-a))
    return R * c

@app.post("/api/routes/risk")
def calculate_routes_risk(payload: Dict[str, Any] = Body(...)):
    routes = payload.get("routes", []) # list of dicts: {"coords": [...], "speeds": [...], "distances": [...]}
    time_filter = payload.get("timeFilter", "All")
    routing_mode = payload.get("routingMode", "Balanced")
    
    # Filter crimes based on time of day
    if time_filter != "All":
        filtered_df = df[df["Time_of_Day"] == time_filter]
    else:
        filtered_df = df
        
    crime_lats = filtered_df["Latitude"].values
    crime_lons = filtered_df["Longitude"].values
    crime_weights = filtered_df["Risk_Weight"].values
    
    results = []
    
    for idx, route in enumerate(routes):
        if isinstance(route, dict):
            route_coords = np.array(route.get("coords", []))
            speeds = route.get("speeds", [])
            distances = route.get("distances", [])
        else:
            route_coords = np.array(route)
            speeds = []
            distances = []
        
        if len(route_coords) == 0:
            results.append({"route_index": idx, "risk_score": 0, "road_penalty": 0, "total_cost": 0})
            continue
            
        r_lons = route_coords[:, 0]
        r_lats = route_coords[:, 1]
        
        # Sample points along the route to speed up computation
        sampled_lats = r_lats[::5]
        sampled_lons = r_lons[::5]
        
        crime_risk = 0
        counted_crime_indices = set()
        
        for p_lat, p_lon in zip(sampled_lats, sampled_lons):
            dists = haversine_vectorized(p_lat, p_lon, crime_lats, crime_lons)
            # Find crimes within 1.0 km radius of the route
            close_crimes = np.where(dists < 1.0)[0]
            for c_idx in close_crimes:
                if c_idx not in counted_crime_indices:
                    crime_risk += crime_weights[c_idx]
                    counted_crime_indices.add(c_idx)
        
        # Bonus: night mode risk multiplier
        if time_filter == "Night":
            crime_risk *= 1.5

        # Calculate road hierarchy penalty based on speed proxies
        road_penalty_sum = 0
        total_distance = sum(distances) if distances else 1
        acc_distance = 0
        
        for speed, dist in zip(speeds, distances):
            if speed >= 80: penalty = 0      # motorway
            elif speed >= 70: penalty = 1    # trunk
            elif speed >= 50: penalty = 2    # primary
            elif speed >= 40: penalty = 3    # secondary
            elif speed >= 30: penalty = 6    # tertiary
            else: penalty = 8                # residential/service
            
            # Hard constraint: Avoid roads below secondary unless near source/destination (< 500m)
            is_start_end = (acc_distance < 500) or ((total_distance - acc_distance) < 500)
            if not is_start_end and penalty >= 6:
                penalty += 15 # Massive penalty for using small roads deep in the route
                
            road_penalty_sum += (penalty * dist) / total_distance
            acc_distance += dist
            
        # Total Cost Function
        if routing_mode == "Max Safety":
            distance_weight = 1
            crime_weight = 8
            road_weight = 12
        else: # Balanced
            distance_weight = 1
            crime_weight = 5
            road_weight = 6
            
        total_cost = (distance_weight * (total_distance / 1000.0)) + (crime_weight * crime_risk) + (road_weight * road_penalty_sum)
            
        results.append({
            "route_index": idx, 
            "risk_score": float(crime_risk),
            "road_penalty": float(road_penalty_sum),
            "total_cost": float(total_cost)
        })
        
    return {"route_risks": results}

@app.post("/api/sos")
async def trigger_sos(
    lat: float = Form(0.0),
    lng: float = Form(0.0),
    audio: UploadFile = File(None)
):
    audio_url = None
    if audio and audio.filename:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"sos_{timestamp}.webm"
        content = await audio.read()
        with open(RECORDINGS_DIR / filename, "wb") as f:
            f.write(content)
        audio_url = f"/static/recordings/{filename}"

    maps_link = f"https://maps.google.com/?q={lat},{lng}"
    sms_sent = False

    if TWILIO_SID and TWILIO_TOKEN and TWILIO_FROM and TWILIO_TO:
        try:
            from twilio.rest import Client
            body = f"🆘 SOS ALERT! I need help!\nLocation: {maps_link}"
            if audio_url:
                body += f"\nAudio clip recorded — check app."
            Client(TWILIO_SID, TWILIO_TOKEN).messages.create(
                body=body, from_=TWILIO_FROM, to=TWILIO_TO
            )
            sms_sent = True
        except Exception as e:
            print(f"Twilio error: {e}")

    return {"success": True, "sms_sent": sms_sent, "maps_link": maps_link, "audio_url": audio_url}


@app.get("/api/safety-score")
def get_safety_score(lat: float, lng: float):
    crime_lats = df["Latitude"].values
    crime_lons = df["Longitude"].values
    distances = haversine_vectorized(lat, lng, crime_lats, crime_lons)
    mask = distances < 1.0
    nearby = df[mask]
    total_weight = float(df["Risk_Weight"].values[mask].sum())
    score = round(min(10.0, total_weight / 8.0), 1)
    act_cols = [c for c in df.columns if c.startswith("act")]
    breakdown = {col: int(nearby[col].sum()) for col in act_cols if col in nearby.columns}
    time_bd = nearby["Time_of_Day"].value_counts().to_dict() if "Time_of_Day" in nearby.columns else {}
    return {"score": score, "count": int(mask.sum()), "breakdown": breakdown, "time_breakdown": time_bd}


from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")

@app.get("/")
def serve_frontend():
    return FileResponse(os.path.join(os.path.dirname(__file__), "static", "index.html"))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
