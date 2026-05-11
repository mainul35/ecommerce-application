import { useEffect, useState } from 'react';
import {
  adminDiscountService,
  type DiscountUpsertRequest,
} from '../../services/admin/adminDiscountService';
import { adminDiscountTemplateService } from '../../services/admin/adminDiscountTemplateService';
import type { Discount, DiscountTemplate, DiscountType } from '../../types';

interface DiscountsScopePanelProps {
  scope: 'PRODUCT' | 'CATEGORY';
  targetId: string;
  /** Display name of the entity (used in confirmation prompts). */
  targetLabel: string;
}

const blankForm: Omit<DiscountUpsertRequest, 'scope' | 'scopeTargetId'> = {
  name: '',
  type: 'PERCENTAGE',
  value: 10,
  startsAt: null,
  endsAt: null,
  isActive: true,
};

const isLive = (d: Discount): boolean => {
  if (!d.isActive) return false;
  const now = Date.now();
  if (d.startsAt && new Date(d.startsAt).getTime() > now) return false;
  if (d.endsAt && new Date(d.endsAt).getTime() <= now) return false;
  return true;
};

const formatValue = (d: Discount): string =>
  d.type === 'PERCENTAGE' ? `${d.value}% off` : `$${Number(d.value).toFixed(2)} off`;

const toLocalDateTimeInput = (date: Date): string => {
  // datetime-local needs "yyyy-MM-ddTHH:mm" without timezone suffix
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`;
};

/**
 * Embedded inline panel that lists, creates, edits and deletes discounts
 * tied to a specific product or category. Used inside the product/category
 * edit forms so admins don't have to context-switch to /admin/discounts.
 *
 * Also surfaces saved Discount Templates: pick one and the form pre-fills
 * with the template's name/type/value (and ends_at, if defaultDurationDays
 * is set), leaving the admin to confirm dates and save.
 */
export function DiscountsScopePanel({
  scope,
  targetId,
  targetLabel,
}: Readonly<DiscountsScopePanelProps>) {
  const [discounts, setDiscounts] = useState<Discount[]>([]);
  const [templates, setTemplates] = useState<DiscountTemplate[]>([]);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState({ ...blankForm });
  const [appliedTemplateName, setAppliedTemplateName] = useState<string | null>(null);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminDiscountService
      .listForScope(scope, targetId)
      .then(setDiscounts)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    adminDiscountTemplateService
      .list()
      .then(setTemplates)
      .catch((e: Error) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope, targetId]);

  const startCreate = () => {
    setEditingId(null);
    setForm({ ...blankForm });
    setAppliedTemplateName(null);
    setInfo(null);
    setShowForm(true);
  };

  const startEdit = (d: Discount) => {
    setEditingId(d.id);
    setForm({
      name: d.name,
      type: d.type,
      value: Number(d.value),
      startsAt: d.startsAt ?? null,
      endsAt: d.endsAt ?? null,
      isActive: d.isActive,
    });
    setAppliedTemplateName(null);
    setInfo(null);
    setShowForm(true);
  };

  const reset = () => {
    setShowForm(false);
    setEditingId(null);
    setForm({ ...blankForm });
    setAppliedTemplateName(null);
    setInfo(null);
  };

  const applyTemplate = (templateId: string) => {
    const t = templates.find((x) => x.id === templateId);
    if (!t) return;
    const ends = t.defaultDurationDays
      ? toLocalDateTimeInput(new Date(Date.now() + t.defaultDurationDays * 86_400_000))
      : null;
    setEditingId(null);
    setForm({
      name: t.name,
      type: t.type,
      value: Number(t.value),
      startsAt: null,
      endsAt: ends,
      isActive: true,
    });
    setAppliedTemplateName(t.name);
    setInfo(`Pre-filled from template "${t.name}". Confirm dates and Save.`);
    setShowForm(true);
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
      const payload: DiscountUpsertRequest = {
        name: form.name,
        type: form.type as DiscountType,
        value: form.value,
        scope,
        scopeTargetId: targetId,
        startsAt: form.startsAt || null,
        endsAt: form.endsAt || null,
        isActive: form.isActive,
      };
      if (editingId) await adminDiscountService.update(editingId, payload);
      else await adminDiscountService.create(payload);
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm(`Delete this discount on ${targetLabel}? This cannot be undone.`)) return;
    setError(null);
    try {
      await adminDiscountService.delete(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <div className="border rounded p-3 bg-light">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <label className="form-label fw-semibold mb-0">
          <i className="bi bi-tag-fill me-1 text-danger"></i>
          Discount on this {scope.toLowerCase()}
        </label>
        {/* Only one discount per product/category. Hide add controls once one exists. */}
        {!showForm && discounts.length === 0 && (
          <div className="d-flex gap-2 align-items-center">
            {templates.length > 0 && (
              <select
                className="form-select form-select-sm"
                style={{ width: 'auto' }}
                defaultValue=""
                onChange={(e) => {
                  if (e.target.value) {
                    applyTemplate(e.target.value);
                    e.target.value = '';
                  }
                }}
                title="Apply a saved template"
              >
                <option value="" disabled>
                  📑 Apply template…
                </option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} — {t.type === 'PERCENTAGE' ? `${t.value}%` : `$${t.value}`}
                  </option>
                ))}
              </select>
            )}
            <button type="button" className="btn btn-sm btn-primary" onClick={startCreate}>
              <i className="bi bi-plus-lg me-1"></i>Add discount
            </button>
          </div>
        )}
        {!showForm && discounts.length > 0 && (
          <span className="text-muted small">
            <i className="bi bi-info-circle me-1"></i>
            Edit or delete the existing one to switch promotions.
          </span>
        )}
      </div>

      {error && <div className="alert alert-danger py-2 mb-2 small">{error}</div>}
      {info && (
        <div className="alert alert-info py-2 mb-2 small d-flex justify-content-between align-items-center">
          <span>{info}</span>
          <button
            type="button"
            className="btn-close btn-close-sm"
            aria-label="Dismiss"
            onClick={() => setInfo(null)}
          />
        </div>
      )}

      {loading ? (
        <p className="text-muted small mb-0">Loading…</p>
      ) : discounts.length === 0 && !showForm ? (
        <p className="text-muted small mb-0">
          No discounts on this {scope.toLowerCase()} yet.
        </p>
      ) : (
        <div className="table-responsive mb-2">
          <table className="table table-sm align-middle mb-0">
            <thead>
              <tr>
                <th>Name</th>
                <th>Discount</th>
                <th>Window</th>
                <th>Status</th>
                <th className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {discounts.map((d) => (
                <tr key={d.id}>
                  <td>{d.name}</td>
                  <td>
                    <strong>{formatValue(d)}</strong>
                  </td>
                  <td className="small text-muted">
                    {d.startsAt ? new Date(d.startsAt).toLocaleDateString() : '—'}
                    {' → '}
                    {d.endsAt ? new Date(d.endsAt).toLocaleDateString() : 'no end'}
                  </td>
                  <td>
                    <span className={`badge ${isLive(d) ? 'bg-success' : 'bg-secondary'}`}>
                      {isLive(d) ? 'Live' : 'Inactive'}
                    </span>
                  </td>
                  <td className="text-end">
                    <button
                      className="btn btn-sm btn-outline-primary me-1"
                      onClick={() => startEdit(d)}
                    >
                      Edit
                    </button>
                    <button
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => onDelete(d.id)}
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

      {showForm && (
        <form onSubmit={submit} className="border-top pt-3 mt-2">
          <div className="d-flex justify-content-between align-items-center mb-2">
            <h6 className="mb-0">
              {editingId ? 'Edit discount' : 'New discount'}
              {appliedTemplateName && (
                <span className="badge bg-info-subtle text-info ms-2">
                  <i className="bi bi-bookmark-fill me-1"></i>
                  from "{appliedTemplateName}"
                </span>
              )}
            </h6>
          </div>
          <div className="row g-2 mb-2">
            <div className="col-12 col-md-6">
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="Name (e.g. Holiday Sale)"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </div>
            <div className="col-6 col-md-3">
              <select
                className="form-select form-select-sm"
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value as DiscountType })}
              >
                <option value="PERCENTAGE">% off</option>
                <option value="FIXED">$ off</option>
              </select>
            </div>
            <div className="col-6 col-md-3">
              <input
                type="number"
                step="0.01"
                min="0.01"
                max={form.type === 'PERCENTAGE' ? '100' : undefined}
                className="form-control form-control-sm"
                placeholder="Value"
                value={form.value}
                onChange={(e) => setForm({ ...form, value: Number(e.target.value) })}
                required
              />
            </div>
          </div>
          <div className="row g-2 mb-2">
            <div className="col-12 col-md-6">
              <label className="form-label small mb-0 text-muted">Starts (optional)</label>
              <input
                type="datetime-local"
                className="form-control form-control-sm"
                value={form.startsAt ?? ''}
                onChange={(e) => setForm({ ...form, startsAt: e.target.value || null })}
              />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label small mb-0 text-muted">Ends (optional)</label>
              <input
                type="datetime-local"
                className="form-control form-control-sm"
                value={form.endsAt ?? ''}
                onChange={(e) => setForm({ ...form, endsAt: e.target.value || null })}
              />
            </div>
          </div>
          <div className="d-flex justify-content-between align-items-center">
            <div className="form-check">
              <input
                type="checkbox"
                className="form-check-input"
                id={`active-${scope}-${targetId}`}
                checked={form.isActive}
                onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
              />
              <label className="form-check-label small" htmlFor={`active-${scope}-${targetId}`}>
                Active
              </label>
            </div>
            <div className="d-flex gap-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={reset}
              >
                Cancel
              </button>
              <button type="submit" className="btn btn-sm btn-primary" disabled={submitting}>
                {submitting ? 'Saving…' : editingId ? 'Update' : 'Save discount'}
              </button>
            </div>
          </div>
        </form>
      )}
    </div>
  );
}
