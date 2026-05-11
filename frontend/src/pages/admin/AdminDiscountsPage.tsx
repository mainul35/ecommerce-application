import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  adminDiscountService,
  type DiscountUpsertRequest,
} from '../../services/admin/adminDiscountService';
import { adminCategoryService } from '../../services/admin/adminCategoryService';
import { adminProductService } from '../../services/admin/adminProductService';
import { adminDiscountTemplateService } from '../../services/admin/adminDiscountTemplateService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Category, Discount, Product } from '../../types';

const emptyForm: DiscountUpsertRequest = {
  name: '',
  type: 'PERCENTAGE',
  value: 10,
  scope: 'PRODUCT',
  scopeTargetId: null,
  startsAt: null,
  endsAt: null,
  isActive: true,
};

const formatValue = (d: Discount): string =>
  d.type === 'PERCENTAGE' ? `${d.value}% off` : `$${Number(d.value).toFixed(2)} off`;

const formatScope = (
  d: Discount,
  products: Product[],
  categories: Category[]
): string => {
  if (d.scope === 'SITEWIDE') return 'Sitewide';
  if (d.scope === 'PRODUCT') {
    const p = products.find((x) => x.id === d.scopeTargetId);
    return `Product · ${p?.name ?? d.scopeTargetId}`;
  }
  const c = categories.find((x) => x.id === d.scopeTargetId);
  return `Category · ${c?.name ?? d.scopeTargetId}`;
};

const isLive = (d: Discount): boolean => {
  if (!d.isActive) return false;
  const now = Date.now();
  if (d.startsAt && new Date(d.startsAt).getTime() > now) return false;
  if (d.endsAt && new Date(d.endsAt).getTime() <= now) return false;
  return true;
};

export function AdminDiscountsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const templateIdParam = searchParams.get('template');

  const [discounts, setDiscounts] = useState<Discount[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [appliedTemplateName, setAppliedTemplateName] = useState<string | null>(null);
  const [form, setForm] = useState<DiscountUpsertRequest>(emptyForm);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminDiscountService
      .list()
      .then(setDiscounts)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    adminCategoryService.list().then(setCategories).catch((e: Error) => setError(e.message));
    adminProductService.list(0, 100, 'newest')
      .then((p) => setProducts(p.content))
      .catch((e: Error) => setError(e.message));
  }, []);

  // Pre-fill the form from a saved template (?template=<id>) and clear the param
  // so a refresh doesn't re-apply.
  useEffect(() => {
    if (!templateIdParam) return;
    adminDiscountTemplateService
      .getById(templateIdParam)
      .then((t) => {
        const ends = t.defaultDurationDays
          ? new Date(Date.now() + t.defaultDurationDays * 86_400_000)
              .toISOString()
              .slice(0, 16)
          : null;
        setEditingId(null);
        setAppliedTemplateName(t.name);
        setForm({
          name: t.name,
          type: t.type,
          value: Number(t.value),
          scope: 'PRODUCT',
          scopeTargetId: null,
          startsAt: null,
          endsAt: ends,
          isActive: true,
        });
        setInfo(`Form pre-filled from template "${t.name}". Pick a target and Create to apply.`);
        const next = new URLSearchParams(searchParams);
        next.delete('template');
        setSearchParams(next, { replace: true });
      })
      .catch((e: Error) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templateIdParam]);

  const startEdit = (d: Discount) => {
    setEditingId(d.id);
    setForm({
      name: d.name,
      type: d.type,
      value: Number(d.value),
      scope: d.scope,
      scopeTargetId: d.scopeTargetId ?? null,
      startsAt: d.startsAt ?? null,
      endsAt: d.endsAt ?? null,
      isActive: d.isActive,
    });
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
    setAppliedTemplateName(null);
    setInfo(null);
  };

  const saveAsTemplate = async () => {
    setError(null);
    setInfo(null);
    if (!form.name.trim()) {
      setError('Enter a name before saving as a template.');
      return;
    }
    if (form.type === 'PERCENTAGE' && (form.value <= 0 || form.value > 100)) {
      setError('Percentage value must be between 0 and 100.');
      return;
    }
    setSavingTemplate(true);
    try {
      const t = await adminDiscountTemplateService.create({
        name: form.name.trim(),
        type: form.type,
        value: form.value,
      });
      setInfo(`Saved template "${t.name}". You can re-apply it from /admin/discount-templates.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save template failed');
    } finally {
      setSavingTemplate(false);
    }
  };

  const onScopeChange = (scope: DiscountUpsertRequest['scope']) => {
    setForm({ ...form, scope, scopeTargetId: null });
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (form.scope !== 'SITEWIDE' && !form.scopeTargetId) {
      setError('Pick a target product or category for non-sitewide discounts.');
      return;
    }
    if (form.type === 'PERCENTAGE' && (form.value <= 0 || form.value > 100)) {
      setError('Percentage value must be between 0 and 100.');
      return;
    }

    setSubmitting(true);
    try {
      const payload: DiscountUpsertRequest = {
        ...form,
        scopeTargetId: form.scope === 'SITEWIDE' ? null : form.scopeTargetId,
        startsAt: form.startsAt || null,
        endsAt: form.endsAt || null,
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
    if (!window.confirm('Delete this discount permanently?')) return;
    setError(null);
    try {
      await adminDiscountService.delete(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Discounts"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Discounts' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}
      {info && (
        <div className="alert alert-info d-flex justify-content-between align-items-center">
          <span>{info}</span>
          <button
            type="button"
            className="btn-close"
            aria-label="Dismiss"
            onClick={() => setInfo(null)}
          />
        </div>
      )}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h3 className="card-title mb-0">{editingId ? 'Edit discount' : 'New discount'}</h3>
              {appliedTemplateName && (
                <span className="badge bg-info-subtle text-info">
                  <i className="bi bi-magic me-1"></i>from "{appliedTemplateName}"
                </span>
              )}
            </div>
            <div className="card-body">
              <form onSubmit={submit}>
                <div className="mb-3">
                  <label className="form-label">Name</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Black Friday Sale"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">Type</label>
                    <select
                      className="form-select"
                      value={form.type}
                      onChange={(e) => setForm({ ...form, type: e.target.value as DiscountUpsertRequest['type'] })}
                    >
                      <option value="PERCENTAGE">Percentage (%)</option>
                      <option value="FIXED">Fixed amount ($)</option>
                    </select>
                  </div>
                  <div className="col-6">
                    <label className="form-label">Value</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="form-control"
                      value={form.value}
                      onChange={(e) => setForm({ ...form, value: Number(e.target.value) })}
                      required
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Scope</label>
                  <select
                    className="form-select"
                    value={form.scope}
                    onChange={(e) =>
                      onScopeChange(e.target.value as DiscountUpsertRequest['scope'])
                    }
                  >
                    <option value="PRODUCT">Single product</option>
                    <option value="CATEGORY">Whole category</option>
                    <option value="SITEWIDE">Sitewide (all products)</option>
                  </select>
                </div>
                {form.scope === 'PRODUCT' && (
                  <div className="mb-3">
                    <label className="form-label">Target product</label>
                    <select
                      className="form-select"
                      value={form.scopeTargetId ?? ''}
                      onChange={(e) => setForm({ ...form, scopeTargetId: e.target.value || null })}
                      required
                    >
                      <option value="">— select a product —</option>
                      {products.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} ({p.sku})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
                {form.scope === 'CATEGORY' && (
                  <div className="mb-3">
                    <label className="form-label">Target category</label>
                    <select
                      className="form-select"
                      value={form.scopeTargetId ?? ''}
                      onChange={(e) => setForm({ ...form, scopeTargetId: e.target.value || null })}
                      required
                    >
                      <option value="">— select a category —</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">Starts (optional)</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.startsAt ?? ''}
                      onChange={(e) => setForm({ ...form, startsAt: e.target.value || null })}
                    />
                  </div>
                  <div className="col-6">
                    <label className="form-label">Ends (optional)</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.endsAt ?? ''}
                      onChange={(e) => setForm({ ...form, endsAt: e.target.value || null })}
                    />
                  </div>
                </div>
                <div className="form-check mb-3">
                  <input
                    type="checkbox"
                    className="form-check-input"
                    id="discountActive"
                    checked={form.isActive ?? true}
                    onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                  />
                  <label className="form-check-label" htmlFor="discountActive">
                    Active
                  </label>
                </div>
                <div className="d-flex flex-wrap gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingId ? 'Update' : 'Create'}
                  </button>
                  {!editingId && (
                    <button
                      type="button"
                      className="btn btn-outline-info"
                      onClick={saveAsTemplate}
                      disabled={savingTemplate}
                      title="Save name/type/value as a reusable template (does not create an active discount)"
                    >
                      <i className="bi bi-bookmark-plus me-1"></i>
                      {savingTemplate ? 'Saving…' : 'Save as template'}
                    </button>
                  )}
                  {(editingId || appliedTemplateName) && (
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
              <h3 className="card-title">All discounts</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : discounts.length === 0 ? (
                <p className="text-muted mb-0">No discounts yet.</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Discount</th>
                        <th>Scope</th>
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
                          <td className="text-muted small">
                            {formatScope(d, products, categories)}
                          </td>
                          <td>
                            <span className={`badge ${isLive(d) ? 'bg-success' : 'bg-secondary'}`}>
                              {isLive(d) ? 'Live' : 'Inactive'}
                            </span>
                          </td>
                          <td className="text-end">
                            <button
                              className="btn btn-sm btn-outline-primary me-2"
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
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
