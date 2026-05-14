import { useEffect, useState } from 'react';
import {
  adminCurrencyService,
  type CurrencyUpsertRequest,
} from '../../services/admin/adminCurrencyService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Currency } from '../../types';

const emptyForm: CurrencyUpsertRequest = {
  code: '',
  name: '',
  symbol: '',
  exchangeRate: 1,
  isActive: true,
};

export function AdminCurrenciesPage() {
  const [currencies, setCurrencies] = useState<Currency[]>([]);
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [form, setForm] = useState<CurrencyUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminCurrencyService
      .list()
      .then(setCurrencies)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, []);

  const startEdit = (c: Currency) => {
    setEditingCode(c.code);
    setForm({
      code: c.code,
      name: c.name,
      symbol: c.symbol,
      exchangeRate: Number(c.exchangeRate),
      isActive: c.isActive,
    });
  };

  const reset = () => {
    setEditingCode(null);
    setForm(emptyForm);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      const payload: CurrencyUpsertRequest = {
        ...form,
        code: form.code.trim().toUpperCase(),
        symbol: form.symbol.trim(),
        name: form.name.trim(),
      };
      if (editingCode) await adminCurrencyService.update(editingCode, payload);
      else await adminCurrencyService.create(payload);
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onSetBase = async (code: string) => {
    if (!window.confirm(`Make ${code} the base currency? This sets its rate to 1.0 and clears the previous base.`))
      return;
    try {
      await adminCurrencyService.setBase(code);
      setInfo(`Base currency switched to ${code}.`);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Set-base failed');
    }
  };

  const onDelete = async (c: Currency) => {
    if (c.isBase) return;
    if (!window.confirm(`Delete currency ${c.code}? Regions using it will need to be reassigned.`)) return;
    try {
      await adminCurrencyService.delete(c.code);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Currencies"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Currencies' }]}
      />

      <p className="text-muted small">
        Product prices are stored in the <strong>base currency</strong>. Each enabled currency
        carries an <code>exchange_rate</code> (multiplier from the base) used by the storefront
        to display converted prices. Customers' selected viewing currency does not change what
        the order is settled in.
      </p>

      {error && <div className="alert alert-danger">{error}</div>}
      {info && (
        <div className="alert alert-success d-flex justify-content-between align-items-center">
          <span>{info}</span>
          <button type="button" className="btn-close" aria-label="Dismiss" onClick={() => setInfo(null)} />
        </div>
      )}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">{editingCode ? `Edit ${editingCode}` : 'New currency'}</h3>
            </div>
            <div className="card-body">
              <form onSubmit={submit}>
                <div className="row g-2 mb-3">
                  <div className="col-4">
                    <label className="form-label">Code</label>
                    <input
                      type="text"
                      maxLength={3}
                      className="form-control text-uppercase"
                      placeholder="USD"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value })}
                      required
                      disabled={!!editingCode}
                    />
                  </div>
                  <div className="col-4">
                    <label className="form-label">Symbol</label>
                    <input
                      type="text"
                      maxLength={10}
                      className="form-control"
                      placeholder="$"
                      value={form.symbol}
                      onChange={(e) => setForm({ ...form, symbol: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-4">
                    <label className="form-label">Rate (per base)</label>
                    <input
                      type="number"
                      step="0.00000001"
                      min="0.00000001"
                      className="form-control"
                      value={form.exchangeRate}
                      onChange={(e) => setForm({ ...form, exchangeRate: Number(e.target.value) })}
                      required
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Display name</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="US Dollar"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div className="form-check mb-3">
                  <input
                    type="checkbox"
                    className="form-check-input"
                    id="currencyActive"
                    checked={form.isActive ?? true}
                    onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                  />
                  <label className="form-check-label" htmlFor="currencyActive">
                    Active (enabled in customer picker)
                  </label>
                </div>
                <div className="d-flex gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingCode ? 'Update' : 'Create'}
                  </button>
                  {editingCode && (
                    <button type="button" className="btn btn-outline-secondary" onClick={reset}>
                      Cancel
                    </button>
                  )}
                </div>
              </form>
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-7">
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">All currencies</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Name</th>
                        <th className="text-end">Rate</th>
                        <th>Status</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {currencies.map((c) => (
                        <tr key={c.code}>
                          <td>
                            <code className="fw-bold">{c.code}</code>{' '}
                            <span className="text-muted">{c.symbol}</span>
                          </td>
                          <td>{c.name}</td>
                          <td className="text-end">{Number(c.exchangeRate).toFixed(4)}</td>
                          <td>
                            {c.isBase && <span className="badge bg-info-subtle text-info me-1">Base</span>}
                            <span className={`badge ${c.isActive ? 'bg-success' : 'bg-secondary'}`}>
                              {c.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                          <td className="text-end">
                            {!c.isBase && (
                              <button
                                className="btn btn-sm btn-outline-info me-1"
                                onClick={() => onSetBase(c.code)}
                                title="Make this the base currency"
                              >
                                Set base
                              </button>
                            )}
                            <button
                              className="btn btn-sm btn-outline-primary me-1"
                              onClick={() => startEdit(c)}
                            >
                              Edit
                            </button>
                            {!c.isBase && (
                              <button
                                className="btn btn-sm btn-outline-danger"
                                onClick={() => onDelete(c)}
                              >
                                Delete
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
