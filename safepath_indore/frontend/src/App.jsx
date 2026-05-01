import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Map as MapIcon, ShieldAlert, Clock, Phone, Info } from 'lucide-react';
import MapComponent from './components/Map';
import SOSView from './components/SOSView';
import TimerView from './components/TimerView';
import DirectoryView from './components/DirectoryView';
import SafetyDashboard from './components/SafetyDashboard';

const INDORE_CENTER = [22.7196, 75.8577];

function App() {
  const [activeTab, setActiveTab] = useState('map');
  const [crimes, setCrimes] = useState([]);
  const [filteredCrimes, setFilteredCrimes] = useState([]);
  const [timeFilter, setTimeFilter] = useState('All');
  const [mapMode, setMapMode] = useState('markers');
  
  // Routing states
  const [source, setSource] = useState(null);
  const [destination, setDestination] = useState(null);
  const [isSelecting, setIsSelecting] = useState(null);
  const [routes, setRoutes] = useState([]);
  const [selectedRouteIndex, setSelectedRouteIndex] = useState(null);
  const [isLoadingRoutes, setIsLoadingRoutes] = useState(false);

  // Fetch crime data
  useEffect(() => {
    const fetchCrimes = async () => {
      try {
        console.log("Fetching crime data from backend...");
        const response = await axios.get('http://localhost:8000/api/crimes');
        setCrimes(response.data.crimes);
        setFilteredCrimes(response.data.crimes);
      } catch (error) {
        console.error("Error fetching crimes:", error);
      }
    };
    fetchCrimes();
  }, []);

  useEffect(() => {
    if (timeFilter === 'All') setFilteredCrimes(crimes);
    else setFilteredCrimes(crimes.filter(c => c.Time_of_Day === timeFilter));
  }, [timeFilter, crimes]);

  const calculateRoutes = async () => {
    if (!source || !destination) return;
    setIsLoadingRoutes(true);
    setRoutes([]);
    try {
      const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${source.lng},${source.lat};${destination.lng},${destination.lat}?overview=full&geometries=geojson&alternatives=true`;
      const osrmRes = await axios.get(osrmUrl);
      
      const fetchedRoutes = osrmRes.data.routes.map(r => ({
        geometry: r.geometry,
        distance: r.distance,
        duration: r.duration,
        speeds: r.legs?.[0]?.annotation?.speed || [],
        distances: r.legs?.[0]?.annotation?.distance || [],
      }));

      const riskRes = await axios.post('http://localhost:8000/api/routes/risk', {
        routes: fetchedRoutes.map(r => ({ coords: r.geometry.coordinates, speeds: r.speeds, distances: r.distances })),
        timeFilter,
        routingMode: 'Balanced',
      });

      const completeRoutes = fetchedRoutes.map((r, idx) => ({
        ...r,
        riskScore: riskRes.data.route_risks[idx]?.risk_score || 0,
        totalCost: riskRes.data.route_risks[idx]?.total_cost || 0,
      }));

      let safestIdx = 0;
      completeRoutes.forEach((r, i) => { if (r.totalCost < completeRoutes[safestIdx].totalCost) safestIdx = i; });
      completeRoutes[safestIdx].isSafest = true;

      setRoutes(completeRoutes);
      setSelectedRouteIndex(safestIdx);
    } catch (err) {
      console.error(err);
      alert("Failed to fetch routes. Ensure backend is running on port 8000.");
    } finally {
      setIsLoadingRoutes(false);
    }
  };

  const renderActiveTab = () => {
    switch (activeTab) {
      case 'map':
        return (
          <div className="map-view">
            <div className="map-overlay-top">
              <div className="glass-card" style={{padding: '10px 15px', margin: '0', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <select 
                  style={{background: 'none', border: 'none', color: 'white', fontWeight: '600', fontSize: '14px', outline: 'none', width: 'auto'}}
                  value={timeFilter}
                  onChange={(e) => setTimeFilter(e.target.value)}
                >
                  <option value="All">All Times</option>
                  <option value="Morning">Morning</option>
                  <option value="Afternoon">Afternoon</option>
                  <option value="Evening">Evening</option>
                  <option value="Night">Night Mode ⚠️</option>
                </select>
                <div style={{display:'flex', gap: '8px'}}>
                   <button className={`nav-btn-sm ${mapMode === 'markers' ? 'active' : ''}`} onClick={() => setMapMode('markers')} style={{padding: '4px', background: 'none', border: 'none', color: mapMode === 'markers' ? 'var(--accent)' : 'white'}}>📍</button>
                   <button className={`nav-btn-sm ${mapMode === 'heatmap' ? 'active' : ''}`} onClick={() => setMapMode('heatmap')} style={{padding: '4px', background: 'none', border: 'none', color: mapMode === 'heatmap' ? 'var(--accent)' : 'white'}}>🔥</button>
                </div>
              </div>
              
              {(!routes.length) && (
                <div className="glass-card" style={{marginTop: '10px', padding: '12px'}}>
                  <p style={{margin: '0 0 10px 0', fontSize: '13px', color: 'var(--text-muted)'}}>
                    {isSelecting ? `Tap map to set ${isSelecting}` : (source && destination ? 'Ready to calculate' : 'Plan a safe route')}
                  </p>
                  <div style={{display:'flex', gap: '8px'}}>
                    <button className="btn secondary" onClick={() => setIsSelecting('source')} style={{fontSize: '11px', height: '32px', borderColor: isSelecting === 'source' ? 'var(--accent)' : ''}}>
                      {source ? 'Source ✅' : 'Set Source'}
                    </button>
                    <button className="btn secondary" onClick={() => setIsSelecting('destination')} style={{fontSize: '11px', height: '32px', borderColor: isSelecting === 'destination' ? 'var(--accent)' : ''}}>
                      {destination ? 'Dest ✅' : 'Set Dest'}
                    </button>
                  </div>
                  {source && destination && (
                    <button className="btn" onClick={calculateRoutes} style={{marginTop: '10px', height: '36px', fontSize: '13px'}}>
                      {isLoadingRoutes ? <div className="loader" style={{width: '16px', height: '16px'}}></div> : 'Find Safest Path'}
                    </button>
                  )}
                </div>
              )}

              {routes.length > 0 && (
                <div className="glass-card" style={{marginTop: '10px', padding: '12px', maxHeight: '180px', overflowY: 'auto'}}>
                  <div style={{fontWeight:'700', fontSize:'12px', marginBottom:'8px', color:'var(--accent)'}}>RECOMMENDED ROUTES</div>
                  {routes.map((r, i) => (
                    <div 
                      key={i} 
                      onClick={() => setSelectedRouteIndex(i)}
                      style={{
                        padding: '10px', 
                        borderRadius: '10px', 
                        background: selectedRouteIndex === i ? 'rgba(56, 189, 248, 0.15)' : 'rgba(255,255,255,0.03)',
                        border: `1px solid ${selectedRouteIndex === i ? 'var(--accent)' : 'var(--glass-border)'}`,
                        marginBottom: '6px',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{display:'flex', justifyContent: 'space-between', fontSize: '13px', fontWeight: '600'}}>
                        <span>Route {i+1} {r.isSafest && '🛡️'}</span>
                        <span>{Math.round(r.duration/60)} min</span>
                      </div>
                      <div style={{fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px'}}>
                        {Math.round(r.distance/1000)} km • Risk Score: {Math.round(r.riskScore)}
                      </div>
                    </div>
                  ))}
                  <button className="btn danger" onClick={() => {setSource(null); setDestination(null); setRoutes([]);}} style={{marginTop: '4px', height: '28px', fontSize: '11px'}}>Reset Map</button>
                </div>
              )}
            </div>
            
            <MapComponent 
              center={INDORE_CENTER}
              crimes={filteredCrimes}
              mapMode={mapMode}
              source={source}
              destination={destination}
              routes={routes}
              selectedRouteIndex={selectedRouteIndex}
              onMapClick={(latlng) => {
                if (isSelecting === 'source') { setSource(latlng); setIsSelecting(null); }
                else if (isSelecting === 'destination') { setDestination(latlng); setIsSelecting(null); }
              }}
            />
          </div>
        );
      case 'sos':
        return <SOSView />;
      case 'timer':
        return <TimerView />;
      case 'directory':
        return <DirectoryView />;
      case 'info':
        return <SafetyDashboard crimes={crimes} />;
      default:
        return null;
    }
  };

  return (
    <div className="mobile-container">
      <main className="view-container">
        {renderActiveTab()}
      </main>

      <nav className="bottom-nav">
        <button className={`nav-item ${activeTab === 'map' ? 'active' : ''}`} onClick={() => setActiveTab('map')}>
          <MapIcon size={24} />
          <span>Map</span>
        </button>
        <button className={`nav-item ${activeTab === 'info' ? 'active' : ''}`} onClick={() => setActiveTab('info')}>
          <Info size={24} />
          <span>Insights</span>
        </button>
        <button className={`nav-item ${activeTab === 'sos' ? 'active' : ''}`} onClick={() => setActiveTab('sos')}>
          <ShieldAlert size={28} color={activeTab === 'sos' ? '#ef4444' : '#94a3b8'} />
          <span>SOS</span>
        </button>
        <button className={`nav-item ${activeTab === 'timer' ? 'active' : ''}`} onClick={() => setActiveTab('timer')}>
          <Clock size={24} />
          <span>Timer</span>
        </button>
        <button className={`nav-item ${activeTab === 'directory' ? 'active' : ''}`} onClick={() => setActiveTab('directory')}>
          <Phone size={24} />
          <span>Contacts</span>
        </button>
      </nav>
    </div>
  );
}

export default App;
