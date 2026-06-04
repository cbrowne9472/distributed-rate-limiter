import { useState } from 'react';
import axios from 'axios';

const TOTAL = 200;

/**
 * Fires 200 concurrent POST /check requests as "demo-user" on the FREE tier.
 * The FREE limit is 100 req/min, so roughly the first 100 are allowed and the
 * remaining 100 come back as 429 — the spike shows up on the live chart in real time.
 */
export default function LoadTestButton() {
  const [running,  setRunning]  = useState(false);
  const [progress, setProgress] = useState(null);   // { allowed, denied, done, elapsed?, finished? }

  const fire = async () => {
    setRunning(true);
    setProgress({ allowed: 0, denied: 0, done: 0 });
    const start = Date.now();

    await Promise.all(
      Array.from({ length: TOTAL }, () =>
        axios.post(
          '/check',
          { userId: 'demo-user', tier: 'FREE', action: 'api:demo' },
          { validateStatus: s => s === 200 || s === 429 }
        )
        .then(res => {
          setProgress(p => ({
            ...p,
            allowed: p.allowed + (res.status === 200 ? 1 : 0),
            denied:  p.denied  + (res.status === 429 ? 1 : 0),
            done:    p.done + 1,
          }));
        })
        .catch(() => {
          // network error — still count it as done
          setProgress(p => ({ ...p, done: p.done + 1 }));
        })
      )
    );

    const elapsed = ((Date.now() - start) / 1000).toFixed(2);
    setProgress(p => ({ ...p, elapsed, finished: true }));
    setRunning(false);
  };

  const reset = () => { setProgress(null); };

  return (
    <div className="load-test">
      <div className="load-test-header">
        <div>
          <h2 className="card-title" style={{ marginBottom: 4 }}>Demo Load Test</h2>
          <p className="load-test-desc">
            Fires {TOTAL} concurrent requests as{' '}
            <strong style={{ color: '#d1d5db' }}>demo-user (FREE tier, 100 req/min limit)</strong>.
            The first ~100 are allowed; the rest return 429 — watch the denial spike on the chart.
          </p>
        </div>
        {progress?.finished && (
          <button
            className="btn btn-primary"
            style={{ width: 'auto', whiteSpace: 'nowrap' }}
            onClick={reset}
          >
            Reset
          </button>
        )}
      </div>

      <button className="btn btn-danger" onClick={fire} disabled={running}>
        {running
          ? `Running… ${progress?.done ?? 0} / ${TOTAL}`
          : '🚀 Fire 200 Requests'}
      </button>

      {progress && (
        <div className="load-test-results">
          <div className="result-item">
            <div className="result-value" style={{ color: '#60a5fa' }}>
              {progress.done}
            </div>
            <div className="result-label">Sent</div>
          </div>
          <div className="result-item">
            <div className="result-value" style={{ color: '#4ade80' }}>
              {progress.allowed}
            </div>
            <div className="result-label">Allowed</div>
          </div>
          <div className="result-item">
            <div className="result-value" style={{ color: '#f87171' }}>
              {progress.denied}
            </div>
            <div className="result-label">Denied (429)</div>
          </div>

          {progress.finished && (
            <div style={{
              gridColumn: 'span 3',
              textAlign: 'center',
              marginTop: 8,
              paddingTop: 12,
              borderTop: '1px solid #1f2937',
              fontSize: 13,
              color: '#6b7280',
            }}>
              Completed in {progress.elapsed}s
              {progress.denied > 0 && (
                <span style={{ color: '#f87171', marginLeft: 12 }}>
                  · {((progress.denied / progress.done) * 100).toFixed(0)}% rate-limited
                </span>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
