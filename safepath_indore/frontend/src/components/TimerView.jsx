import React, { useState, useEffect } from 'react';
import { Play, Square, AlertCircle, CheckCircle2, Clock } from 'lucide-react';
import axios from 'axios';

const TimerView = () => {
  const [timeLeft, setTimeLeft] = useState(0);
  const [isActive, setIsActive] = useState(false);
  const [inputMinutes, setInputMinutes] = useState(15);
  const [isTriggered, setIsTriggered] = useState(false);

  useEffect(() => {
    let interval = null;
    if (isActive && timeLeft > 0) {
      interval = setInterval(() => {
        setTimeLeft((prev) => prev - 1);
      }, 1000);
    } else if (isActive && timeLeft === 0) {
      triggerSOS();
      setIsActive(false);
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [isActive, timeLeft]);

  const triggerSOS = async () => {
    setIsTriggered(true);
    navigator.geolocation.getCurrentPosition(async (pos) => {
      const formData = new FormData();
      formData.append('lat', pos.coords.latitude);
      formData.append('lng', pos.coords.longitude);
      await axios.post('http://localhost:8000/api/sos', formData);
    });
  };

  const startTimer = () => {
    setTimeLeft(inputMinutes * 60);
    setIsActive(true);
    setIsTriggered(false);
  };

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="view-container" style={{padding: '20px'}}>
      <div className="glass-card" style={{textAlign: 'center'}}>
        <h2 style={{display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px'}}>
          <Clock size={24} color="var(--accent)"/> Safe Arrival
        </h2>
        <p style={{fontSize: '14px', color: 'var(--text-muted)'}}>
          If you don't stop this timer before it ends, an emergency alert will be sent automatically.
        </p>

        {!isActive && !isTriggered && (
          <div style={{margin: '30px 0'}}>
            <label style={{display: 'block', marginBottom: '10px', fontSize: '14px'}}>Duration (minutes)</label>
            <input 
              type="number" 
              value={inputMinutes}
              onChange={(e) => setInputMinutes(e.target.value)}
              className="glass-card"
              style={{width: '80px', textAlign: 'center', fontSize: '20px', padding: '10px', background: 'rgba(255,255,255,0.05)'}}
            />
            <button className="btn" onClick={startTimer} style={{marginTop: '20px'}}>
              <Play size={18} /> Start Safe Journey
            </button>
          </div>
        )}

        {isActive && (
          <div style={{margin: '30px 0'}}>
            <div className="timer-display">{formatTime(timeLeft)}</div>
            <button className="btn danger" onClick={() => setIsActive(false)}>
              <Square size={18} /> I've Arrived Safely
            </button>
          </div>
        )}

        {isTriggered && (
          <div className="glass-card" style={{background: 'rgba(239, 68, 68, 0.2)', borderColor: 'var(--danger)'}}>
            <AlertCircle size={40} color="var(--danger)" style={{margin: '10px auto'}}/>
            <h3 style={{color: 'var(--danger)'}}>SOS Triggered!</h3>
            <p style={{fontSize: '13px'}}>Timer expired. Emergency services have been notified of your last location.</p>
            <button className="btn secondary" onClick={() => setIsTriggered(false)}>Reset</button>
          </div>
        )}

        {!isActive && !isTriggered && timeLeft === 0 && (
          <div style={{marginTop: '20px', display:'flex', alignItems:'center', justifyContent:'center', gap:'8px', color: 'var(--success)'}}>
             <CheckCircle2 size={16}/> <span>Last journey completed safely.</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default TimerView;
