import {
  LineChart, Line, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';

export default function LiveChart({ data }) {
  if (data.length === 0) {
    return (
      <div className="chart-placeholder">
        Waiting for traffic — start the app and hit /check to see data
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis
          dataKey="time"
          tick={{ fill: '#6b7280', fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          interval="preserveStartEnd"
        />
        <YAxis
          tick={{ fill: '#6b7280', fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          width={36}
        />
        <Tooltip
          contentStyle={{ background: '#1f2937', border: '1px solid #374151', borderRadius: 8, fontSize: 13 }}
          labelStyle={{ color: '#9ca3af' }}
        />
        <Legend wrapperStyle={{ color: '#9ca3af', fontSize: 12 }} />
        <Line
          type="monotone" dataKey="allowedRate"
          stroke="#4ade80" strokeWidth={2} dot={false}
          name="Allowed / sec" activeDot={{ r: 4 }}
        />
        <Line
          type="monotone" dataKey="deniedRate"
          stroke="#f87171" strokeWidth={2} dot={false}
          name="Denied / sec"  activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
