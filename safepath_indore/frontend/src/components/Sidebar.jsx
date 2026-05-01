import React from 'react';
import { Shield, Clock, MapPin, Navigation, Trash2, CheckCircle2, Zap } from 'lucide-react';

const Sidebar = ({ 
  timeFilter, setTimeFilter, 
  isSelecting, setIsSelecting, 
  source, destination, 
  calculateRoutes, clearRoutes, 
  isLoadingRoutes, routes, 
  selectedRouteIndex, setSelectedRouteIndex 
}) => {

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60);
    return `${mins} min`;
  };

  const formatDistance = (meters) => {
    return `${(meters / 1000).toFixed(1)} km`;
  };

  const getRiskLabel = (score) => {
    if (score < 50) return <span className="risk-low">Low Risk</span>;
    if (score < 150) return <span className="risk-medium">Medium Risk</span>;
    return <span className="risk-high">High Risk</span>;
  };

  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <h1>SafePath Indore</h1>
        <p>AI-Powered Safe Route Recommendation</p>
      </div>

      <div className="sidebar-content">
        <div className="control-group">
          <label><Clock size={14} style={{display:'inline', marginRight:'4px'}}/> Time of Day Filter</label>
          <select 
            className="select-box"
            value={timeFilter} 
            onChange={(e) => setTimeFilter(e.target.value)}
          >
            <option value="All">All Times</option>
            <option value="Morning">Morning (6 AM - 12 PM)</option>
            <option value="Afternoon">Afternoon (12 PM - 5 PM)</option>
            <option value="Evening">Evening (5 PM - 9 PM)</option>
            <option value="Night">Night Mode (9 PM - 6 AM) ⚠️</option>
          </select>
        </div>

        <div className="control-group">
          <label><Navigation size={14} style={{display:'inline', marginRight:'4px'}}/> Route Planner</label>
          
          <div style={{display:'flex', gap:'8px', marginBottom:'12px'}}>
            <button 
              className={`btn btn-secondary ${isSelecting === 'source' ? 'active' : ''}`}
              style={{borderColor: isSelecting === 'source' ? 'var(--accent)' : ''}}
              onClick={() => setIsSelecting('source')}
            >
              <MapPin size={16} /> 
              {source ? 'Source Set' : 'Set Source'}
            </button>
            <button 
              className={`btn btn-secondary ${isSelecting === 'destination' ? 'active' : ''}`}
              style={{borderColor: isSelecting === 'destination' ? 'var(--accent)' : ''}}
              onClick={() => setIsSelecting('destination')}
            >
              <MapPin size={16} /> 
              {destination ? 'Dest Set' : 'Set Dest'}
            </button>
          </div>

          <button 
            className="btn" 
            onClick={calculateRoutes}
            disabled={!source || !destination || isLoadingRoutes}
          >
            {isLoadingRoutes ? <div className="loader"></div> : 'Find Safe Routes'}
          </button>
          
          {(source || destination || routes.length > 0) && (
            <button 
              className="btn btn-secondary" 
              onClick={clearRoutes}
              style={{marginTop: '8px', color: 'var(--danger)', borderColor: 'var(--danger)'}}
            >
              <Trash2 size={16} /> Clear
            </button>
          )}
        </div>

        {routes.length > 0 && (
          <div className="control-group">
            <label>Recommended Routes</label>
            {routes.map((route, idx) => (
              <div 
                key={idx} 
                className={`route-card ${selectedRouteIndex === idx ? 'selected' : ''}`}
                onClick={() => setSelectedRouteIndex(idx)}
              >
                <div className="route-header">
                  <span className="route-title">Route {idx + 1}</span>
                  <div style={{display:'flex', gap:'4px'}}>
                    {route.isSafest && <span className="route-badge badge-safest"><Shield size={10} style={{display:'inline'}}/> Safest</span>}
                    {route.isFastest && <span className="route-badge badge-fastest"><Zap size={10} style={{display:'inline'}}/> Fastest</span>}
                  </div>
                </div>
                <div className="route-stats">
                  <span className="stat-item">⏱️ {formatDuration(route.duration)}</span>
                  <span className="stat-item">📏 {formatDistance(route.distance)}</span>
                </div>
                <div style={{marginTop: '8px', fontSize: '13px'}}>
                  Risk Score: <b>{Math.round(route.riskScore)}</b> ({getRiskLabel(route.riskScore)})
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="legend">
          <label style={{display:'block', marginBottom:'12px', fontSize:'14px', fontWeight:'600', color:'var(--text-muted)'}}>
            Severity Legend
          </label>
          <div className="legend-item">
            <div className="legend-color" style={{backgroundColor: '#ef4444'}}></div>
            <span>High (Murder, Assault)</span>
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{backgroundColor: '#f59e0b'}}></div>
            <span>Medium (Robbery, Burglary)</span>
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{backgroundColor: '#eab308'}}></div>
            <span>Low (Theft, Fraud)</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
