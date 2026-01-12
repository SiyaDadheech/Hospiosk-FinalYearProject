import React, { useState } from 'react';
import axios from 'axios';

// Backend API base URL — resolved at runtime from a meta tag or build-time env var.
// This allows updating the backend target without rebuilding the bundle: add
// <meta name="api-base" content="https://your-backend.example.com"> to public/index.html
// If meta tag is absent, we fall back to `REACT_APP_API_BASE`.
function resolveApiBase() {
  try {
    const m = document.querySelector('meta[name="api-base"]');
    if (m && m.content) {
      const raw = m.content.trim();
      try {
        const u = new URL(raw);
        // keep only origin (protocol + host + optional port)
        return u.origin.replace(/\/+$/, '');
      } catch (err) {
        console.warn('Invalid api-base meta tag, ignoring:', raw);
        return '';
      }
    }
  } catch (e) {
    // ignore when rendering on server or during build
  }
  const env = (process.env.REACT_APP_API_BASE || '').trim();
  try {
    if (env) return new URL(env).origin.replace(/\/+$/, '');
  } catch (er) {
    console.warn('Invalid REACT_APP_API_BASE, ignoring:', env);
  }
  return '';
}
const API_BASE = resolveApiBase();
console.log('Resolved API_BASE:', API_BASE);

// Build API endpoint safely. If `API_BASE` is empty we use relative paths so
// requests go to the same origin as the static site. If it's set, we use it
// as the absolute origin.
// If the meta/api-base was set to the static site's origin (common mistake),
// treat it as unset so we don't try to call the static site for backend APIs.
const EFFECTIVE_API_BASE = (() => {
  try {
    if (!API_BASE) return '';
    // If someone accidentally set api-base to the Azure static app origin,
    // requests will hit the static site (404). Detect common static hostnames
    // and fallback to relative paths while warning the developer.
    if (API_BASE.includes('azurestaticapps.net') || API_BASE.includes('github.io')) {
      console.warn('api-base appears to be a static-site origin; ignoring to avoid requests to the static site:', API_BASE);
      return '';
    }
    return API_BASE;
  } catch (e) {
    return API_BASE;
  }
})();

function buildApi(path) {
  if (!path) return path;
  if (!path.startsWith('/')) path = '/' + path;
  if (!EFFECTIVE_API_BASE) return path; // relative
  try {
    return new URL(path, EFFECTIVE_API_BASE).toString();
  } catch (e) {
    console.warn('Failed to build API URL, falling back to relative path:', path, e);
    return path;
  }
}

export default function KioskFrontend() {
  const [step, setStep] = useState(1); // 1: Aadhar, 2: Details, 3: Doctor, 4: Receipt
  const [aadhar, setAadhar] = useState('');
  const [patient, setPatient] = useState({ name: '', age: '' });
  const [doctor, setDoctor] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState('card');
  const doctors = [
    { id: 1, name: 'Dr. Vijay Pathak (Cardiologist)', fee: 200, experience: '8 years' },
    { id: 2, name: 'Dr. Sanjay Saran (Endocrinologist)', fee: 500, experience: '15 years' },
    { id: 3, name: 'Dr. Pankaj Beniwal (Nephrologist)', fee: 450, experience: '10 years' },
    { id: 4, name: 'Dr. Smriti Bhargava (Obstetrician)', fee: 300, experience: '7 years' },
    { id: 5, name: 'Dr. Pawan Singhal (ENT Surgeon)', fee: 250, experience: '12 years' },
  ];

  // Step 1: Fetch from UIDAI (Backend API)
  const handleAadharSubmit = async () => {
    // validate input (strip spaces)
    const cleaned = (aadhar || '').replace(/\s+/g, '');
    if (!/^\d{12}$/.test(cleaned)) {
      alert('Please enter a valid 12-digit Aadhaar number');
      return;
    }
    try {
      const res = await axios.get(buildApi(`/api/fetch-aadhar/${cleaned}`));
      setPatient(res.data);
      setStep(2);
    } catch (e) {
      console.error('Aadhaar fetch error:', e);
      const resp = e && e.response ? e.response : null;
      const details = resp ? `HTTP ${resp.status} ${resp.statusText} - ${JSON.stringify(resp.data)}` : (e.message || String(e));
      // Fallback: create a local mock patient so kiosk can proceed offline
      const suffix = cleaned.substring(Math.max(0, cleaned.length - 4));
      const mock = { name: `Patient ${suffix}`, age: 30 };
      alert(`Aadhaar lookup failed — using offline fallback.\n\nDetails: ${details}`);
      setPatient(mock);
      setStep(2);
    }
  };

  const handleBiometric = async () => {
    try {
      // In real flow the scanner SDK will provide a template; here we use a mock template
      const template = String(Date.now());
      const res = await axios.post(buildApi('/api/biometric/authenticate'), { template, mode: 'mock' });
      setPatient(res.data);
      setStep(2);
    } catch (e) {
      console.error('Biometric error:', e);
      alert('Biometric authentication failed (mock). You can still use Aadhaar.');
    }
  };

  // Step 2 & 3: Finalize Appointment
  const handleFinalSubmit = async () => {
    if (!doctor) { alert('Please select a doctor'); return; }
    try {
      // include selected doctor and payment method in the booking payload
      const payload = { ...patient, doctor, paymentMethod };
      await axios.post(buildApi('/api/add-patient'), payload);
      setStep(4);
    } catch (e) {
      console.error('Booking error:', e);
      const msg = e.response && e.response.data ? JSON.stringify(e.response.data) : 'Database Error';
      alert(msg);
    }
  };




  //  const handlePayment = () => {
  //   if (!doctor) {
  //     alert('Please select a doctor first!');
  //     return;
  //   }

  //   const options = {
  //     key: 'rzp_test_51N0XXXXXXxXxXx', // Replace with your test key
  //     amount: doctor.fee * 100, // ₹ to paise
  //     currency: 'INR',
  //     name: 'City Hospital',
  //     description: `Consultation fee for ${doctor.name}`,
  //     handler: async function (response) {
  //       alert(`Payment successful! Payment ID: ${response.razorpay_payment_id}`);
  //       await handleFinalSubmit(); // Call your backend booking after payment
  //     },
  //     prefill: {
  //       name: patient.name,
  //     },
  //     theme: { color: '#007bff' },
  //   };

  //   const rzp = new window.Razorpay(options);
  //   rzp.open();
  // };

const handlePayment = async () => {
  if (!doctor) {
    alert('Please select a doctor first!');
    return;
  }

  // Simulate payment processing for demo. Replace with gateway integration.
  try {
    if (paymentMethod === 'cash') {
      await handleFinalSubmit();
      return;
    }

    // For online methods call backend to create an order (Razorpay) or payment link
    if (paymentMethod === 'upi') {
      // Create a payment link and show it to the user to scan
      const linkResp = await axios.post(buildApi('/api/razorpay/create-payment-link'), {
        amount: doctor.fee,
        customer: { name: patient.name }
      });
      const data = linkResp.data;
      // If response is string (JSON) parse it
      const json = typeof data === 'string' ? JSON.parse(data) : data;
      const shortUrl = json.short_url || json.shortUrl || json.shorturl || json.shortLink || json.short_url;
      if (shortUrl) {
        window.open(shortUrl, '_blank');
        alert('Opened payment link; after payment complete the booking.');
        return;
      }
    }

    // Create order (Razorpay Orders API) and open Checkout
    const ordResp = await axios.post(buildApi('/api/razorpay/create-order'), { amount: doctor.fee });
    const ordData = typeof ordResp.data === 'string' ? JSON.parse(ordResp.data) : ordResp.data;
    const orderId = ordData.id || ordData.order_id || ordData.orderId || ordData.id;
    let key = ordData.key || ordData.key_id || ordData.key;
    if (!key) {
      try {
        const pk = await axios.get(buildApi('/api/razorpay/public-key'));
        const pkd = typeof pk.data === 'string' ? JSON.parse(pk.data) : pk.data;
        key = pkd.key || pkd.key_id || '';
      } catch (er) {
        console.warn('Could not fetch public key from backend', er);
      }
    }

    // Load Razorpay Checkout script dynamically
    await new Promise((resolve, reject) => {
      if (window.Razorpay) return resolve(true);
      const s = document.createElement('script');
      s.src = 'https://checkout.razorpay.com/v1/checkout.js';
      s.onload = () => resolve(true);
      s.onerror = () => reject(new Error('Failed to load Razorpay script'));
      document.body.appendChild(s);
    });

    const options = {
      key: key || 'rzp_test_S28kQARurj88gk',
      amount: doctor.fee * 100,
      currency: 'INR',
      name: 'City Hospital',
      description: `Consultation fee for ${doctor.name}`,
      order_id: orderId,
      handler: async function (response) {
        try {
          // Verify payment server-side
          await axios.post(buildApi('/api/razorpay/verify'), response);
          alert('Payment successful!');
          await handleFinalSubmit();
        } catch (err) {
          console.error('Payment/booking error:', err);
          const resp = err && err.response ? err.response : null;
          const msg = resp ? `HTTP ${resp.status} ${resp.statusText} - ${JSON.stringify(resp.data)}` : (err.message || 'Payment/Booking Error');
          alert(`Payment/booking failed:\n\n${msg}`);
        }
      },
      prefill: { name: patient.name },
      theme: { color: '#007bff' },
    };

    const rzp = new window.Razorpay(options);
    rzp.open();
  } catch (e) {
    console.error('Payment/booking error:', e);
    const msg = e.response && e.response.data ? JSON.stringify(e.response.data) : 'Payment/Booking Error';
    alert(msg);
  }
};


  return (
    <div style={uiStyles.screen}>
      <div style={uiStyles.card}>
        {step === 1 && (
          <div>
            <h2>Welcome</h2>
            <p>Please enter your 12-digit Aadhar Number</p>
            <input 
              style={uiStyles.input} 
              placeholder="0000 0000 0000"
              value={aadhar}
              onChange={(e) => setAadhar(e.target.value)}
            />
            <div style={{display: 'flex', gap: 8}}>
              <button style={uiStyles.button} onClick={handleAadharSubmit}>Verify</button>
              <button style={{...uiStyles.button, backgroundColor:'#28a745'}} onClick={handleBiometric}>Use Biometric</button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div>
            <h2>Verify Identity</h2>
            <p><strong>Name:</strong> {patient.name}</p>
            <p><strong>Age:</strong> {patient.age}</p>
            <button style={uiStyles.button} onClick={() => setStep(3)}>Details are Correct</button>
          </div>
        )}

        {step === 3 && (
          <div>
            <h2>Select Doctor</h2>
            <select style={uiStyles.input} value={doctor ? doctor.id : ''} onChange={(e) => {
              const val = e.target.value;
              if (!val) { setDoctor(null); return; }
              const id = Number(val);
              const sel = doctors.find(d => d.id === id) || null;
              setDoctor(sel);
            }}>
              <option value="">Select...</option>
              {doctors.map(d => (
                <option key={d.id} value={d.id}>{d.name} — Fee: ₹{d.fee} — {d.experience}</option>
              ))}
            </select>

            <div style={{ textAlign: 'left', marginTop: 12 }}>
              <p style={{ marginBottom: 6 }}><strong>Choose Payment Method:</strong></p>
              <label style={{ display: 'block', marginBottom: 6 }}>
                <input type="radio" name="pay" value="card" checked={paymentMethod === 'card'} onChange={() => setPaymentMethod('card')} /> Card
              </label>
              <label style={{ display: 'block', marginBottom: 6 }}>
                <input type="radio" name="pay" value="upi" checked={paymentMethod === 'upi'} onChange={() => setPaymentMethod('upi')} /> UPI
              </label>
              <label style={{ display: 'block', marginBottom: 6 }}>
                <input type="radio" name="pay" value="netbanking" checked={paymentMethod === 'netbanking'} onChange={() => setPaymentMethod('netbanking')} /> Netbanking
              </label>
              <label style={{ display: 'block', marginBottom: 6 }}>
                <input type="radio" name="pay" value="cash" checked={paymentMethod === 'cash'} onChange={() => setPaymentMethod('cash')} /> Pay at Hospital (Cash)
              </label>
            </div>

            <button style={uiStyles.button} onClick={handlePayment}>Confirm & Pay</button>
          </div>
        )}

        {step === 4 && (
          <div id="receipt">
            <h2 style={{color: 'green'}}>Success!</h2>
            <div style={uiStyles.receiptBox}>
              <h3>CITY HOSPITAL</h3>
              <p>Patient: {patient.name}</p>
              <p>Doctor: {doctor?.name}</p>
              <p>Fee: ₹{doctor?.fee} | Experience: {doctor?.experience}</p>
              <p>Payment Method: {paymentMethod}</p>
              <p>Date: {new Date().toLocaleDateString()}</p>
            </div>
            <button style={uiStyles.button} onClick={() => window.print()}>Print Receipt</button>
            <button onClick={() => window.location.reload()}>Finish</button>
          </div>
        )}
      </div>
    </div>
  );
}

const uiStyles = {
  screen: { backgroundColor: '#e9ecef', height: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center' },
  card: { backgroundColor: 'white', padding: '40px', borderRadius: '12px', textAlign: 'center', width: '400px', boxShadow: '0 10px 25px rgba(0,0,0,0.1)' },
  input: { width: '100%', padding: '12px', margin: '15px 0', fontSize: '18px', borderRadius: '5px', border: '1px solid #ccc' },
  button: { width: '100%', padding: '15px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold' },
  receiptBox: { border: '2px dashed #444', padding: '20px', margin: '20px 0', textAlign: 'left' }
};