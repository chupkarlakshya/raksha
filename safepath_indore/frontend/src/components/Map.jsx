import React, { useEffect } from 'react';
import { MapContainer, TileLayer, CircleMarker, Popup, Polyline, Marker, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';

// Fix Leaflet default icon issues
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Custom Source/Dest Icons
const sourceIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const destIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-blue.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Component to handle map clicks for setting source/destination
const MapEvents = ({ onMapClick }) => {
  useMapEvents({
    click(e) {
      onMapClick(e.latlng);
    },
  });
  return null;
};

// Heatmap Layer Component
const HeatmapLayer = ({ points }) => {
  const map = useMap();

  useEffect(() => {
    if (!window.L || !window.L.heatLayer) return;

    // Convert points to [lat, lng, intensity]
    const heatPoints = points.map(p => [p.Latitude, p.Longitude, p.Risk_Weight || 1]);
    
    const layer = window.L.heatLayer(heatPoints, {
      radius: 20,
      blur: 15,
      maxZoom: 15,
      max: 5,
      gradient: { 0.4: 'blue', 0.6: 'cyan', 0.7: 'lime', 0.8: 'yellow', 1.0: 'red' }
    }).addTo(map);

    return () => {
      map.removeLayer(layer);
    };
  }, [map, points]);

  return null;
};

const getSeverityColor = (severity) => {
  if (severity >= 7) return '#ef4444'; // Red
  if (severity >= 4) return '#f59e0b'; // Orange
  return '#eab308'; // Yellow
};

const MapComponent = ({ center, crimes, mapMode, source, destination, routes, selectedRouteIndex, onMapClick }) => {
  return (
    <MapContainer 
      center={center} 
      zoom={13} 
      style={{ height: '100%', width: '100%' }}
      zoomControl={false}
    >
      {/* Dark theme tile layer */}
      <TileLayer
        url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
      />
      
      <MapEvents onMapClick={onMapClick} />

      {/* Markers Mode */}
      {mapMode === 'markers' && crimes.map((crime, idx) => (
        <CircleMarker
          key={idx}
          center={[crime.Latitude, crime.Longitude]}
          radius={5}
          pathOptions={{
            color: getSeverityColor(crime.Crime_Severity),
            fillColor: getSeverityColor(crime.Crime_Severity),
            fillOpacity: 0.7,
            weight: 1
          }}
        >
          <Popup>
            <div className="popup-title">{crime.Crime_Type}</div>
            <div className="popup-row">
              <span className="popup-label">Time:</span>
              <span>{crime.Time} ({crime.Time_of_Day})</span>
            </div>
            <div className="popup-row">
              <span className="popup-label">Severity:</span>
              <span>{crime.Crime_Severity}/10</span>
            </div>
          </Popup>
        </CircleMarker>
      ))}

      {/* Heatmap Mode */}
      {mapMode === 'heatmap' && (
        <HeatmapLayer points={crimes} />
      )}

      {/* Source & Destination Markers */}
      {source && <Marker position={source} icon={sourceIcon}><Popup>Source</Popup></Marker>}
      {destination && <Marker position={destination} icon={destIcon}><Popup>Destination</Popup></Marker>}

      {/* Render Routes */}
      {routes.map((route, idx) => {
        // Swap lng, lat to lat, lng for Leaflet Polyline
        const positions = route.geometry.coordinates.map(coord => [coord[1], coord[0]]);
        const isSelected = selectedRouteIndex === idx;
        
        let routeColor = '#94a3b8'; // default grey
        let weight = 4;
        let opacity = 0.5;
        
        if (isSelected) {
          weight = 6;
          opacity = 1.0;
          if (route.isSafest) {
            routeColor = '#10b981'; // Green for safest
          } else if (route.isFastest) {
            routeColor = '#3b82f6'; // Blue for fastest
          } else {
            routeColor = '#f59e0b'; // Yellow for others
          }
        }

        return (
          <Polyline 
            key={idx} 
            positions={positions} 
            pathOptions={{ color: routeColor, weight, opacity }} 
          />
        );
      })}

    </MapContainer>
  );
};

export default MapComponent;
