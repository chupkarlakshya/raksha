import React from 'react';
import { Phone, User, Shield, Ambulance, Truck } from 'lucide-react';

const DirectoryView = () => {
  const contacts = [
    { name: 'Police Emergency', phone: '100', icon: <Shield size={20}/>, type: 'Official' },
    { name: 'Ambulance', phone: '102', icon: <Ambulance size={20}/>, type: 'Medical' },
    { name: 'Fire Station', phone: '101', icon: <Truck size={20}/>, type: 'Emergency' },
    { name: 'Women Helpline', phone: '1091', icon: <User size={20}/>, type: 'Helpline' },
    { name: 'Indore Police HQ', phone: '0731-2522222', icon: <Shield size={20}/>, type: 'Local' },
    { name: 'Child Helpline', phone: '1098', icon: <User size={20}/>, type: 'Helpline' },
  ];

  return (
    <div className="view-container" style={{padding: '20px'}}>
      <div className="glass-card">
        <h2 style={{margin: '0 0 15px 0', fontSize: '20px'}}>Quick Dial Directory</h2>
        <p style={{fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px'}}>
          Tap any contact to initiate an immediate call.
        </p>

        <div style={{display: 'flex', flexDirection: 'column'}}>
          {contacts.map((contact, i) => (
            <div key={i} className="dir-item">
              <div style={{display: 'flex', alignItems: 'center', gap: '15px'}}>
                <div style={{
                  width: '40px', height: '40px', borderRadius: '12px', 
                  background: 'rgba(255,255,255,0.05)', display: 'flex', 
                  alignItems: 'center', justifyContent: 'center', color: 'var(--accent)'
                }}>
                  {contact.icon}
                </div>
                <div>
                  <div style={{fontSize: '15px', fontWeight: '600'}}>{contact.name}</div>
                  <div style={{fontSize: '12px', color: 'var(--text-muted)'}}>{contact.type} • {contact.phone}</div>
                </div>
              </div>
              <a href={`tel:${contact.phone}`} className="call-btn">
                <Phone size={18} fill="currentColor" />
              </a>
            </div>
          ))}
        </div>
      </div>

      <div className="glass-card" style={{marginTop: '15px', padding: '15px', background: 'rgba(56, 189, 248, 0.05)'}}>
        <div style={{display: 'flex', gap: '10px', alignItems: 'center'}}>
          <Info size={16} color="var(--accent)"/>
          <span style={{fontSize: '12px'}}>Personal emergency contacts can be added in Settings.</span>
        </div>
      </div>
    </div>
  );
};

export default DirectoryView;
