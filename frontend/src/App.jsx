import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import LiveChart from './components/LiveChart';
import StatsCards from './components/StatsCards';
import RuleForm from './components/RuleForm';
import LoadTestButton from './components/LoadTestButton';
import './App.css';

// ── Metrics polling hook ──────────────────────────────
function useMetrics() {
  const [history, setHistory] = useState([]);
  const [totals, setTotals]   = useState({ totalAllowed: 0, totalDenied: 0 });
  const prevRef               = useRef(null);

  useEffect(() => {
    const poll = async () => {
      try {
        const { data } = await axios.get('/dashboard/metrics');
        const { totalAllowed, totalDenied, timestamp } = data;

        const allowedRate = prevRef.current
          ? Math.max(0, totalAllowed - prevRef.current.totalAllowed) : 0;
        const deniedRate = prevRef.current
          ? Math.max(0, totalDenied  - prevRef.current.totalDenied)  : 0;

        prevRef.current = data;
        setTotals({ totalAllowed, totalDenied });

        const time = new Date(timestamp).toLocaleTimeString();
        setHistory(prev => [...prev, { time, allowedRate, deniedRate }].slice(-30));
      } catch {
        // Spring Boot not running — silently hold last known state
      }
    };

    poll();
    const id = setInterval(poll, 1000);
    return () => clearInterval(id);
  }, []);

  return { history, totals };
}

// ── App ───────────────────────────────────────────────
export default function App() {
  const { history, totals } = useMetrics();
  const total       = totals.totalAllowed + totals.totalDenied;
  const denialPct   = total > 0 ? ((totals.totalDenied / total) * 100).toFixed(1) : '0.0';
  const last        = history[history.length - 1];
  const currentRate = last ? last.allowedRate + last.deniedRate : 0;

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1 className="title">Rate Limiter Dashboard</h1>
          <p className="subtitle">Live traffic · Rule management · Load testing</p>
        </div>
        <div className="live-badge">● LIVE</div>
      </header>

      <main className="main">
        <StatsCards
          totalAllowed={totals.totalAllowed}
          totalDenied={totals.totalDenied}
          denialPct={denialPct}
          currentRate={currentRate}
        />

        <div className="grid">
          <div className="card">
            <h2 className="card-title">Live Request Rate</h2>
            <LiveChart data={history} />
          </div>
          <div className="card">
            <h2 className="card-title">Create / Update Rule</h2>
            <RuleForm />
          </div>
        </div>

        <div className="card">
          <LoadTestButton />
        </div>
      </main>
    </div>
  );
}
