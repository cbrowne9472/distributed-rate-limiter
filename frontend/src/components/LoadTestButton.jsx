// Wired in Day 9 — fires 200 rapid /check requests so denials appear live on the chart
export default function LoadTestButton({ onFire }) {
  return (
    <div className="load-test">
      <div className="load-test-header">
        <div>
          <h2 className="card-title" style={{ marginBottom: 4 }}>Demo Load Test</h2>
          <p className="load-test-desc">
            Fire 200 rapid requests and watch rate limiting kick in live on the chart above.
          </p>
        </div>
      </div>
      <button className="btn btn-danger" onClick={onFire} disabled={!onFire}>
        🚀 Fire 200 Requests
      </button>
      {!onFire && (
        <p style={{ fontSize: 12, color: '#4b5563', textAlign: 'center' }}>
          Logic wired in Day 9
        </p>
      )}
    </div>
  );
}
