import { useEffect, useState } from 'react';
import {
  adminRegionService,
  type RegionUpsertRequest,
} from '../../services/admin/adminRegionService';
import { adminCurrencyService } from '../../services/admin/adminCurrencyService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Currency, Region } from '../../types';

const emptyForm: RegionUpsertRequest = {
  name: '',
  countryCode: '',
  currencyCode: '',
  isActive: true,
};

export function AdminRegionsPage() {
  const [regions, setRegions] = useState<Region[]>([]);
  const [currencies, setCurrencies] = useState<Currency[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<RegionUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminRegionService
      .list()
      .then(setRegions)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    adminCurrencyService.list().then(setCurrencies).catch((e: Error) => setError(e.message));
  }, []);

  const startEdit = (r: Region) => {
    setEditingId(r.id);
    setForm({
      name: r.name,
      countryCode: r.countryCode,
      currencyCode: r.currencyCode,
      isActive: r.isActive,
    });
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload: RegionUpsertRequest = {
        ...form,
        name: form.name.trim(),
        countryCode: form.countryCode.trim().toUpperCase(),
        currencyCode: form.currencyCode.trim().toUpperCase(),
      };
      if (editingId) await adminRegionService.update(editingId, payload);
      else await adminRegionService.create(payload);
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (r: Region) => {
    if (!window.confirm(`Delete region ${r.name} (${r.countryCode})?`)) return;
    try {
      await adminRegionService.delete(r.id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Regions"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Regions' }]}
      />

      <p className="text-muted small">
        A region maps an ISO country code to a default currency. The storefront uses the
        visitor's IP-detected country to pick the matching region (and its currency) on first
        load. Products can be restricted to specific regions on the product edit page.
      </p>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">{editingId ? 'Edit region' : 'New region'}</h3>
            </div>
            <div className="card-body">
              <form onSubmit={submit}>
                <div className="row g-2 mb-3">
                  <div className="col-8">
                    <label className="form-label">Name</label>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="United States"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-4">
                    <label className="form-label">Country (ISO)</label>
                    <input
                      type="text"
                      maxLength={2}
                      className="form-control text-uppercase"
                      placeholder="US"
                      value={form.countryCode}
                      onChange={(e) => setForm({ ...form, countryCode: e.target.value })}
                      required
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Default currency</label>
                  <select
                    className="form-select"
                    value={form.currencyCode}
                    onChange={(e) => setForm({ ...form, currencyCode: e.target.value })}
                    required
                  >
                    <option value="">— select a currency —</option>
                    {currencies.filter((c) => c.isActive).map((c) => (
                      <option key={c.code} value={c.code}>
                        {c.code} ({c.symbol}) — {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-check mb-3">
                  <input
                    type="checkbox"
                    className="form-check-input"
                    id="regionActive"
                    checked={form.isActive ?? true}
                    onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                  />
                  <label className="form-check-label" htmlFor="regionActive">
                    Active
                  </label>
                </div>
                <div className="d-flex gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingId ? 'Update' : 'Create'}
                  </button>
                  {editingId && (
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
              <h3 className="card-title">All regions</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Country</th>
                        <th>Region</th>
                        <th>Currency</th>
                        <th>Status</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {regions.map((r) => (
                        <tr key={r.id}>
                          <td>
                            <code className="fw-bold">{r.countryCode}</code>
                          </td>
                          <td>{r.name}</td>
                          <td>
                            <code>{r.currencyCode}</code>
                          </td>
                          <td>
                            <span className={`badge ${r.isActive ? 'bg-success' : 'bg-secondary'}`}>
                              {r.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                          <td className="text-end">
                            <button
                              className="btn btn-sm btn-outline-primary me-1"
                              onClick={() => startEdit(r)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-sm btn-outline-danger"
                              onClick={() => onDelete(r)}
                            >
                              Delete
                            </button>
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
