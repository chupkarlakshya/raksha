import React, { useState } from 'react';
import axios from 'axios';
import { Mic, MicOff, Send, MapPin, ShieldAlert } from 'lucide-react';

const SOSView = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [status, setStatus] = useState('Idle');
  const [mediaRecorder, setMediaRecorder] = useState(null);

  const startSOS = async () => {
    setStatus('Sending Alert...');
    
    // 1. Get current location
    navigator.geolocation.getCurrentPosition(async (pos) => {
      const { latitude, longitude } = pos.coords;
      
      try {
        // 2. Start recording audio
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const recorder = new MediaRecorder(stream);
        const chunks = [];
        
        recorder.ondataavailable = (e) => chunks.push(e.data);
        recorder.onstop = async () => {
          const blob = new Blob(chunks, { type: 'audio/webm' });
          const formData = new FormData();
          formData.append('lat', latitude);
          formData.append('lng', longitude);
          formData.append('audio', blob, 'sos_audio.webm');
          
          await axios.post('http://localhost:8000/api/sos', formData);
          setStatus('SOS SENT! Help is on the way.');
          setIsRecording(false);
        };
        
        setMediaRecorder(recorder);
        recorder.start();
        setIsRecording(true);
        setStatus('RECORDING AUDIO...');
        
        // Stop after 10 seconds automatically
        setTimeout(() => {
          if (recorder.state === 'recording') recorder.stop();
        }, 10000);
        
      } catch (err) {
        console.error(err);
        // Fallback: Send SOS without audio
        const formData = new FormData();
        formData.append('lat', latitude);
        formData.append('lng', longitude);
        await axios.post('http://localhost:8000/api/sos', formData);
        setStatus('SOS SENT (No Audio)!');
      }
    }, () => {
      setStatus('Error: Location required for SOS');
    });
  };

  return (
    <div className="view-container" style={{display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center'}}>
      <div className="glass-card" style={{width: '90%', maxWidth: '400px'}}>
        <h2 style={{color: 'var(--danger)', marginBottom: '10px'}}>Emergency SOS</h2>
        <p style={{color: 'var(--text-muted)', fontSize: '14px', marginBottom: '30px'}}>
          Pressing the button below will send your live location and a 10s audio clip to emergency contacts.
        </p>
        
        <button className="sos-btn-large" onClick={startSOS} disabled={isRecording}>
          {isRecording ? <MicOff size={40} /> : <ShieldAlert size={40} />}
          <span style={{marginTop: '8px'}}>{isRecording ? 'RECORDING' : 'SOS'}</span>
        </button>
        
        <div style={{marginTop: '20px', padding: '15px', borderRadius: '12px', background: 'rgba(0,0,0,0.2)', fontSize: '14px'}}>
          <div style={{display:'flex', alignItems:'center', justifyContent:'center', gap:'8px', color: status.includes('SENT') ? 'var(--success)' : 'var(--accent)'}}>
             {status.includes('SENT') ? <Send size={16}/> : <MapPin size={16}/>}
             <b>Status: {status}</b>
          </div>
        </div>
      </div>
      
      <div className="glass-card" style={{width: '90%', maxWidth: '400px', marginTop: '10px'}}>
        <h3 style={{fontSize: '16px', margin: '0 0 10px 0'}}>Current Contacts</h3>
        <div style={{fontSize: '13px', color: 'var(--text-muted)'}}>
          Police (100) • Ambulance (102) • Twilio Linked Number
        </div>
      </div>
    </div>
  );
};

export default SOSView;
