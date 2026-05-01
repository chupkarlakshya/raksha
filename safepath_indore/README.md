# SafePath Indore

SafePath Indore is a full-stack interactive web application that visualizes crime data, displays density heatmaps, and provides AI-powered safe route recommendations using real-time routing and safety scoring logic.

## Project Structure
- **/backend**: FastAPI Python backend for data processing, safety scoring, and filtering.
- **/frontend**: React + Vite frontend using React-Leaflet for mapping and OSRM for dynamic routing.

## Setup Instructions

### 1. Backend Setup
1. Open a new terminal and navigate to the backend directory:
   ```bash
   cd "safepath_indore/backend"
   ```
2. (Optional) Create a virtual environment:
   ```bash
   python -m venv venv
   .\venv\Scripts\activate
   ```
3. Install the required dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Start the backend server:
   ```bash
   uvicorn app:app --reload --port 8000
   ```
   *The backend will run on http://localhost:8000*

### 2. Frontend Setup
1. Open a **second** terminal and navigate to the frontend directory:
   ```bash
   cd "safepath_indore/frontend"
   ```
2. Install the Node modules:
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
   *The frontend will run on http://localhost:5173 (or similar)*

## Features Included
1. **Interactive Map**: Built with React-Leaflet and premium dark mode styling (CARTO Dark Matter).
2. **Dynamic Heatmap**: Toggle between markers and density heatmaps natively.
3. **Safety Scoring**: Backend assigns risk weights to specific crime types (Assault=5, Harassment=4, etc.).
4. **Time of Day Filter**: Live-filter map data and route calculations based on the time.
5. **Bonus Night-Mode Multiplier**: Automatically applies a 1.5x risk penalty when routing during the night.
6. **Smart Routing**: 
   - Click "Set Source" and tap on the map.
   - Click "Set Destination" and tap on the map.
   - Computes multiple possible paths using OSRM, calculates cumulative risk intersecting with crime radiuses, and flags the **Safest** (Green) and **Fastest** (Blue) routes!
