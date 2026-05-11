import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  adminDiscountTemplateService,
  type DiscountTemplateUpsertRequest,
} from '../../services/admin/adminDiscountTemplateService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { DiscountTemplate } from '../../types';

const emptyForm: DiscountTemplateUpsertRequest = {
  name: '',
  description: '',
  type: 'PERCENTAGE',
  value: 10,
  defaultDurationDays: null,
};

const formatValue = (t: DiscountTemplate): string =>
  t.type === 'PERCENTAGE' ? `${t.value}% off` : `$${Number(t.value).toFixed(2)} off`;

export function AdminDiscountTemplatesPage() {
  const [templates, setTemplates] = useState<DiscountTemplate[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<DiscountTemplateUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminDiscountTemplateService
      .list()
      .then(setTemplates)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, []);

  const startEdit = (t: DiscountTemplate) => {
    setEditingId(t.id);
    setForm({
      name: t.name,
      description: t.description ?? '',
      type: t.type,
      value: Number(t.value),
      defaultDurationDays: t.defaultDurationDays ?? null,
    });
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (form.type === 'PERCENTAGE' && (form.value <= 0 || form.value > 100)) {
      setError('Percentage value must be between 0 and 100.');
      return;
    }
    setSubmitting(true);
    try {
      const payload: DiscountTemplateUpsertRequest = {
        ...form,
        description: form.description?.trim() || null,
        defaultDurationDays: form.defaultDurationDays ?? null,
      };
      if (editingId) await adminDiscountTemplateService.update(editingId, payload);
      else await adminDiscountTemplateService.create(payload);
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this template? Discounts already created from it are unaffected.')) return;
    setError(null);
    try {
      await adminDiscountTemplateService.delete(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Discount Templates"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Discount Templates' }]}
      />

      <p className="text-muted small">
        Templates are reusable blueprints. Create one once (e.g. <em>"Holiday 25% off"</em>) and
        re-apply it to different products, categories, or sitewide whenever you run a campaign.
        Click <strong>Use</strong> to start a new discount pre-filled from a template.
      </p>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">{editingId ? 'Edit template' : 'New template'}</h3>
            </div>
            <div className="card-body">
              <form onSubmit={submit}>
                <div className="mb-3">
                  <label className="form-label">Name</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Holiday 25% Off"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div className="mb-3">
                  <label className="form-label">Description (optional)</label>
                  <textarea
                    rows={2}
                    className="form-control"
                    placeholder="Recurring seasonal promo"
                    value={form.description ?? ''}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">Type</label>
                    <select
                      className="form-select"
                      value={form.type}
                      onChange={(e) => setForm({ ...form, type: e.target.value as DiscountTemplateUpsertRequest['type'] })}
                    >
                      <option value="PERCENTAGE">% off</option>
                      <option value="FIXED">$ off</option>
                    </select>
                  </div>
                  <div className="col-6">
                    <label className="form-label">Value</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      max={form.type === 'PERCENTAGE' ? '100' : undefined}
                      className="form-control"
                      value={form.value}
                      onChange={(e) => setForm({ ...form, value: Number(e.target.value) })}
                      required
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Default duration (days)</label>
                  <input
                    type="number"
                    min="1"
                    className="form-control"
                    placeholder="No default"
                    value={form.defaultDurationDays ?? ''}
                    onChange={(e) => setForm({
                      ...form,
                      defaultDurationDays: e.target.value === '' ? null : Number(e.target.value),
                    })}
                  />
                  <div className="form-text">
                    When set, applying the template pre-fills end date = today + N days.
                  </div>
                </div>
                <div className="d-flex gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingId ? 'Update' : 'Save template'}
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
              <h3 className="card-title">Saved templates</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : templates.length === 0 ? (
                <p className="text-muted mb-0">
                  No templates yet. Save one on the left, then come back here to re-apply it later.
                </p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Discount</th>
                        <th>Default duration</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {templates.map((t) => (
                        <tr key={t.id}>
                          <td>
                            <div className="fw-semibold">{t.name}</div>
                            {t.description && (
                              <div className="text-muted small">{t.description}</div>
                            )}
                          </td>
                          <td>
                            <strong>{formatValue(t)}</strong>
                          </td>
                          <td className="small text-muted">
                            {t.defaultDurationDays ? `${t.defaultDurationDays} days` : '—'}
                          </td>
                          <td className="text-end">
                            <Link
                              to={`/admin/discounts?template=${t.id}`}
                              className="btn btn-sm btn-success me-2"
                              title="Apply this template to products / category / sitewide"
                            >
                              <i className="bi bi-magic me-1"></i>Use
                            </Link>
                            <button
                              className="btn btn-sm btn-outline-primary me-2"
                              onClick={() => startEdit(t)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-sm btn-outline-danger"
                              onClick={() => onDelete(t.id)}
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
