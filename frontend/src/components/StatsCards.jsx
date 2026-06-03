export default function StatsCards({ totalAllowed, totalDenied, denialPct, currentRate }) {
  const fmt = n => n.toLocaleString();
  return (
    <div className="stats-row">
      <div className="stat-card">
        <div className="stat-label">Total Allowed</div>
        <div className="stat-value green">{fmt(totalAllowed)}</div>
      </div>
      <div className="stat-card">
        <div className="stat-label">Total Denied</div>
        <div className="stat-value red">{fmt(totalDenied)}</div>
      </div>
      <div className="stat-card">
        <div className="stat-label">Denial Rate</div>
        <div className="stat-value yellow">{denialPct}%</div>
      </div>
      <div className="stat-card">
        <div className="stat-label">Current Rate</div>
        <div className="stat-value blue">{currentRate} / s</div>
      </div>
    </div>
  );
}
