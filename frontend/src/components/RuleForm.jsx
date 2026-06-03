import { useState } from 'react';
import axios from 'axios';

export default function RuleForm() {
  const [form, setForm] = useState({
    tier: 'FREE', action: '',
    requestLimit: 100, windowSeconds: 60,
    algorithmType: 'sliding_window',
  });
  const [status,  setStatus]  = useState(null);
  const [loading, setLoading] = useState(false);

  const update = field => e => setForm(p => ({ ...p, [field]: e.target.value }));

  const submit = async e => {
    e.preventDefault();
    setLoading(true);
    setStatus(null);
    try {
      await axios.post('/rules', {
        ...form,
        action:        form.action.trim() || null,
        requestLimit:  parseInt(form.requestLimit),
        windowSeconds: parseInt(form.windowSeconds),
      });
      setStatus({ type: 'success', msg: `✓ Rule saved: ${form.tier} → ${form.requestLimit} req / ${form.windowSeconds}s` });
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? 'Failed to save rule';
      setStatus({ type: 'error', msg: `✗ ${msg}` });
    } finally {
      setLoading(false);
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <div className="form-group">
        <label className="form-label">Tier</label>
        <select className="form-select" value={form.tier} onChange={update('tier')}>
          <option>FREE</option>
          <option>PRO</option>
          <option>INTERNAL</option>
        </select>
      </div>

      <div className="form-group">
        <label className="form-label">Action (blank = all actions)</label>
        <input className="form-input" value={form.action} onChange={update('action')}
               placeholder="e.g. api:export" />
      </div>

      <div className="form-group">
        <label className="form-label">Request Limit</label>
        <input className="form-input" type="number" min="1"
               value={form.requestLimit} onChange={update('requestLimit')} />
      </div>

      <div className="form-group">
        <label className="form-label">Window (seconds)</label>
        <input className="form-input" type="number" min="1"
               value={form.windowSeconds} onChange={update('windowSeconds')} />
      </div>

      <div className="form-group">
        <label className="form-label">Algorithm</label>
        <select className="form-select" value={form.algorithmType} onChange={update('algorithmType')}>
          <option value="sliding_window">Sliding Window</option>
          <option value="token_bucket">Token Bucket</option>
        </select>
      </div>

      <button className="btn btn-primary" type="submit" disabled={loading}>
        {loading ? 'Saving…' : 'Create Rule'}
      </button>

      {status && <div className={`status-msg ${status.type}`}>{status.msg}</div>}
    </form>
  );
}
