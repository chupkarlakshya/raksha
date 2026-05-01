import React from 'react';
import { BarChart3, TrendingUp, AlertTriangle, ShieldCheck } from 'lucide-react';

const SafetyDashboard = ({ crimes }) => {
  // Simple stats calculation
  const totalCrimes = crimes.length;
  const highSeverity = crimes.filter(c => c.Crime_Severity >= 7).length;
  const mediumSeverity = crimes.filter(c => c.Crime_Severity >= 4 && c.Crime_Severity < 7).length;
  const lowSeverity = crimes.filter(c => c.Crime_Severity < 4).length;

  const typeCounts = crimes.reduce((acc, c) => {
    acc[c.Crime_Type] = (acc[c.Crime_Type] || 0) + 1;
    return acc;
  }, {});

  const sortedTypes = Object.entries(typeCounts).sort((a, b) => b[1] - a[1]).slice(0, 5);

  return (
    <div className="view-container" style={{padding: '20px'}}>
      <div className="glass-card">
        <h2 style={{margin: '0 0 20px 0', display: 'flex', alignItems: 'center', gap: '8px'}}>
          <BarChart3 size={24} color="var(--accent)"/> Safety Insights
        </h2>

        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '20px'}}>
          <div className="glass-card" style={{margin: '0', padding: '15px', background: 'rgba(255,255,255,0.03)'}}>
            <div style={{fontSize: '24px', fontWeight: '700'}}>{totalCrimes}</div>
            <div style={{fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase'}}>Total Incidents</div>
          </div>
          <div className="glass-card" style={{margin: '0', padding: '15px', background: 'rgba(239, 68, 68, 0.05)'}}>
            <div style={{fontSize: '24px', fontWeight: '700', color: 'var(--danger)'}}>{highSeverity}</div>
            <div style={{fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase'}}>High Criticality</div>
          </div>
        </div>

        <div className="section" style={{marginBottom: '20px'}}>
          <h3 style={{fontSize: '14px', color: 'var(--text-muted)', marginBottom: '15px'}}>INCIDENT SEVERITY</h3>
          <div style={{height: '10px', width: '100%', display: 'flex', borderRadius: '5px', overflow: 'hidden', background: '#334155'}}>
            <div style={{width: `${(highSeverity/totalCrimes)*100}%`, background: 'var(--danger)'}}></div>
            <div style={{width: `${(mediumSeverity/totalCrimes)*100}%`, background: 'var(--warning)'}}></div>
            <div style={{width: `${(lowSeverity/totalCrimes)*100}%`, background: 'var(--success)'}}></div>
          </div>
          <div style={{display: 'flex', justifyContent: 'space-between', marginTop: '8px', fontSize: '11px'}}>
            <span>High: {highSeverity}</span>
            <span>Med: {mediumSeverity}</span>
            <span>Low: {lowSeverity}</span>
          </div>
        </div>

        <div className="section">
          <h3 style={{fontSize: '14px', color: 'var(--text-muted)', marginBottom: '15px'}}>TOP CRIME TYPES</h3>
          {sortedTypes.map(([type, count], i) => (
            <div key={i} style={{marginBottom: '10px'}}>
              <div style={{display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px'}}>
                <span>{type}</span>
                <span style={{fontWeight: '600'}}>{count}</span>
              </div>
              <div style={{height: '4px', width: '100%', background: '#334155', borderRadius: '2px'}}>
                <div style={{
                  height: '100%', 
                  width: `${(count/sortedTypes[0][1])*100}%`, 
                  background: 'var(--accent)',
                  borderRadius: '2px'
                }}></div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="glass-card" style={{marginTop: '15px'}}>
        <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
          <ShieldCheck size={32} color="var(--success)"/>
          <div>
            <div style={{fontSize: '15px', fontWeight: '600'}}>System Verified</div>
            <div style={{fontSize: '12px', color: 'var(--text-muted)'}}>Data last updated: {new Date().toLocaleDateString()}</div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SafetyDashboard;
