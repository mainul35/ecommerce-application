import { useEffect, useState } from 'react';
import {
  adminCouponService,
  type CouponUpsertRequest,
} from '../../services/admin/adminCouponService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Coupon } from '../../types';

const emptyForm: CouponUpsertRequest = {
  code: '',
  name: '',
  type: 'PERCENTAGE',
  value: 10,
  minOrderAmount: null,
  maxUses: null,
  maxUsesPerUser: null,
  validFrom: null,
  validUntil: null,
  isActive: true,
};

const formatDiscount = (c: Coupon): string => {
  if (c.type === 'PERCENTAGE') return `${c.value}% off`;
  if (c.type === 'FIXED') return `$${Number(c.value).toFixed(2)} off`;
  return 'Free shipping';
};

const isLive = (c: Coupon): boolean => {
  if (!c.isActive) return false;
  const now = Date.now();
  if (c.validFrom && new Date(c.validFrom).getTime() > now) return false;
  if (c.validUntil && new Date(c.validUntil).getTime() <= now) return false;
  return true;
};

export function AdminCouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CouponUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminCouponService
      .list()
      .then(setCoupons)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, []);

  const startEdit = (c: Coupon) => {
    setEditingId(c.id);
    setForm({
      code: c.code,
      name: c.name ?? '',
      type: c.type,
      value: c.value ?? null,
      minOrderAmount: c.minOrderAmount ?? null,
      maxUses: c.maxUses ?? null,
      maxUsesPerUser: c.maxUsesPerUser ?? null,
      validFrom: c.validFrom ?? null,
      validUntil: c.validUntil ?? null,
      isActive: c.isActive,
    });
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
  };

  const onTypeChange = (type: CouponUpsertRequest['type']) => {
    setForm({ ...form, type, value: type === 'FREE_SHIPPING' ? null : (form.value ?? 10) });
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (form.type !== 'FREE_SHIPPING' && (form.value === null || form.value === undefined || form.value <= 0)) {
      setError('Value must be positive for percentage / fixed coupons.');
      return;
    }
    if (form.type === 'PERCENTAGE' && form.value !== null && (form.value as number) > 100) {
      setError('Percentage value must be at most 100.');
      return;
    }
    setSubmitting(true);
    try {
      const payload: CouponUpsertRequest = {
        ...form,
        code: form.code.trim().toUpperCase(),
        name: form.name?.trim() || undefined,
        value: form.type === 'FREE_SHIPPING' ? null : form.value,
        minOrderAmount: form.minOrderAmount ?? null,
        maxUses: form.maxUses ?? null,
        maxUsesPerUser: form.maxUsesPerUser ?? null,
        validFrom: form.validFrom || null,
        validUntil: form.validUntil || null,
      };
      if (editingId) await adminCouponService.update(editingId, payload);
      else await adminCouponService.create(payload);
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this coupon permanently? Existing redemptions will also be removed.')) return;
    setError(null);
    try {
      await adminCouponService.delete(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Coupons"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Coupons' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">{editingId ? 'Edit coupon' : 'New coupon'}</h3>
            </div>
            <div className="card-body">
              <form onSubmit={submit}>
                <div className="row g-2 mb-3">
                  <div className="col-7">
                    <label className="form-label">Code</label>
                    <input
                      type="text"
                      className="form-control text-uppercase"
                      placeholder="BLACKFRIDAY"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-5">
                    <label className="form-label">Type</label>
                    <select
                      className="form-select"
                      value={form.type}
                      onChange={(e) => onTypeChange(e.target.value as CouponUpsertRequest['type'])}
                    >
                      <option value="PERCENTAGE">% off</option>
                      <option value="FIXED">$ off</option>
                      <option value="FREE_SHIPPING">Free shipping</option>
                    </select>
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Display name (optional)</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Black Friday Special"
                    value={form.name ?? ''}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                  />
                </div>
                {form.type !== 'FREE_SHIPPING' && (
                  <div className="mb-3">
                    <label className="form-label">
                      Value {form.type === 'PERCENTAGE' ? '(%)' : '($)'}
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      max={form.type === 'PERCENTAGE' ? '100' : undefined}
                      className="form-control"
                      value={form.value ?? ''}
                      onChange={(e) => setForm({ ...form, value: e.target.value === '' ? null : Number(e.target.value) })}
                      required
                    />
                  </div>
                )}
                <div className="mb-3">
                  <label className="form-label">Minimum order amount ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="form-control"
                    placeholder="No minimum"
                    value={form.minOrderAmount ?? ''}
                    onChange={(e) => setForm({ ...form, minOrderAmount: e.target.value === '' ? null : Number(e.target.value) })}
                  />
                </div>
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">Total uses (cap)</label>
                    <input
                      type="number"
                      min="1"
                      className="form-control"
                      placeholder="Unlimited"
                      value={form.maxUses ?? ''}
                      onChange={(e) => setForm({ ...form, maxUses: e.target.value === '' ? null : Number(e.target.value) })}
                    />
                  </div>
                  <div className="col-6">
                    <label className="form-label">Per-user cap</label>
                    <input
                      type="number"
                      min="1"
                      className="form-control"
                      placeholder="Unlimited"
                      value={form.maxUsesPerUser ?? ''}
                      onChange={(e) => setForm({ ...form, maxUsesPerUser: e.target.value === '' ? null : Number(e.target.value) })}
                    />
                  </div>
                </div>
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">Valid from</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.validFrom ?? ''}
                      onChange={(e) => setForm({ ...form, validFrom: e.target.value || null })}
                    />
                  </div>
                  <div className="col-6">
                    <label className="form-label">Valid until</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      value={form.validUntil ?? ''}
                      onChange={(e) => setForm({ ...form, validUntil: e.target.value || null })}
                    />
                  </div>
                </div>
                <div className="form-check mb-3">
                  <input
                    type="checkbox"
                    className="form-check-input"
                    id="couponActive"
                    checked={form.isActive ?? true}
                    onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                  />
                  <label className="form-check-label" htmlFor="couponActive">Active</label>
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
              <h3 className="card-title">All coupons</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : coupons.length === 0 ? (
                <p className="text-muted mb-0">No coupons yet.</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Discount</th>
                        <th>Min order</th>
                        <th>Limits</th>
                        <th>Status</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {coupons.map((c) => (
                        <tr key={c.id}>
                          <td>
                            <code className="fw-bold">{c.code}</code>
                            {c.name && <div className="text-muted small">{c.name}</div>}
                          </td>
                          <td>
                            <strong>{formatDiscount(c)}</strong>
                          </td>
                          <td className="small text-muted">
                            {c.minOrderAmount ? `$${Number(c.minOrderAmount).toFixed(2)}` : '—'}
                          </td>
                          <td className="small text-muted">
                            {c.maxUses ? `${c.maxUses} total` : '∞'}
                            {c.maxUsesPerUser && (
                              <>
                                <br />
                                <span>{c.maxUsesPerUser}/user</span>
                              </>
                            )}
                          </td>
                          <td>
                            <span className={`badge ${isLive(c) ? 'bg-success' : 'bg-secondary'}`}>
                              {isLive(c) ? 'Live' : 'Inactive'}
                            </span>
                          </td>
                          <td className="text-end">
                            <button
                              className="btn btn-sm btn-outline-primary me-2"
                              onClick={() => startEdit(c)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-sm btn-outline-danger"
                              onClick={() => onDelete(c.id)}
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
