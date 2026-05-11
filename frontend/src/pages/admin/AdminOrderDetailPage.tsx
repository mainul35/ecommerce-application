import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { adminOrderService } from '../../services/admin/adminOrderService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Order, OrderStatus } from '../../types';

const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING: ['CANCELLED'],
  CONFIRMED: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: ['REFUNDED'],
};

const statusBadge = (status: OrderStatus): string => {
  switch (status) {
    case 'PENDING':
      return 'bg-warning text-dark';
    case 'CONFIRMED':
      return 'bg-info text-dark';
    case 'PROCESSING':
      return 'bg-primary';
    case 'SHIPPED':
      return 'bg-secondary';
    case 'DELIVERED':
      return 'bg-success';
    case 'CANCELLED':
      return 'bg-danger';
    case 'REFUNDED':
      return 'bg-dark';
    default:
      return 'bg-light text-dark';
  }
};

export function AdminOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => {
    if (!id) return;
    setLoading(true);
    adminOrderService
      .getById(id)
      .then(setOrder)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, [id]);

  const transition = async (next: OrderStatus) => {
    if (!order) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await adminOrderService.transitionStatus(order.id, next);
      setOrder(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Status change failed');
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!order) return;
    const reason = window.prompt('Cancellation reason?', 'Customer request');
    if (reason === null) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await adminOrderService.cancel(order.id, reason);
      setOrder(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Cancel failed');
    } finally {
      setBusy(false);
    }
  };

  const markPaid = async () => {
    if (!order) return;
    if (!window.confirm('Mark this order as paid (offline payment / manual confirmation)?')) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await adminOrderService.markPaid(order.id);
      setOrder(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Mark-paid failed');
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <p className="text-muted">Loading…</p>;
  if (!order) return <p className="text-danger">Order not found.</p>;

  const allowed = NEXT_STATUS[order.status] ?? [];
  const advanceTargets = allowed.filter((s) => s !== 'CANCELLED');
  const canCancel = allowed.includes('CANCELLED');
  const canMarkPaid = order.status === 'PENDING';

  return (
    <>
      <PageHeader
        title={`Order ${order.id.substring(0, 8)}`}
        crumbs={[
          { label: 'Home', to: '/admin' },
          { label: 'Orders', to: '/admin/orders' },
          { label: order.id.substring(0, 8) },
        ]}
        actions={
          <button className="btn btn-outline-secondary" onClick={() => navigate('/admin/orders')}>
            Back
          </button>
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-3">
        <div className="col-12 col-lg-8">
          <div className="card">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h3 className="card-title mb-0">Items</h3>
              <span className={`badge ${statusBadge(order.status)}`}>{order.status}</span>
            </div>
            <div className="card-body">
              <div className="table-responsive">
                <table className="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th className="text-end">Qty</th>
                      <th className="text-end">Unit price</th>
                      <th className="text-end">Subtotal</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items.map((item) => (
                      <tr key={item.id}>
                        <td>{item.productName}</td>
                        <td className="text-end">{item.quantity}</td>
                        <td className="text-end">${Number(item.price).toFixed(2)}</td>
                        <td className="text-end">
                          ${(Number(item.price) * item.quantity).toFixed(2)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colSpan={3} className="text-end fw-semibold">
                        Total
                      </td>
                      <td className="text-end fw-semibold">
                        ${Number(order.totalAmount).toFixed(2)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-4">
          <div className="card mb-3">
            <div className="card-header">
              <h3 className="card-title">Actions</h3>
            </div>
            <div className="card-body d-grid gap-2">
              {canMarkPaid && (
                <button
                  className="btn btn-success"
                  disabled={busy}
                  onClick={markPaid}
                  title="Marks the order as paid without using Stripe (offline payment / dev testing)"
                >
                  <i className="bi bi-cash-coin me-2"></i>Mark as paid
                </button>
              )}
              {advanceTargets.map((next) => (
                <button
                  key={next}
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={() => transition(next)}
                >
                  Advance to {next}
                </button>
              ))}
              {canCancel && (
                <button className="btn btn-outline-danger" disabled={busy} onClick={cancel}>
                  <i className="bi bi-x-circle me-2"></i>Cancel order
                </button>
              )}
              {!canMarkPaid && advanceTargets.length === 0 && !canCancel && (
                <p className="text-muted small mb-0">No further actions for this status.</p>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <h3 className="card-title">Meta</h3>
            </div>
            <div className="card-body small">
              <div className="mb-2">
                <span className="text-muted">Created:</span>{' '}
                {new Date(order.createdAt).toLocaleString()}
              </div>
              <div className="mb-2">
                <span className="text-muted">Updated:</span>{' '}
                {new Date(order.updatedAt).toLocaleString()}
              </div>
              <div className="mb-2">
                <span className="text-muted">Customer ID:</span>{' '}
                <code>{order.userId.substring(0, 8)}</code>
              </div>
              <div className="mb-2">
                <span className="text-muted">Payment method:</span>{' '}
                {order.paymentMethod ?? '—'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
