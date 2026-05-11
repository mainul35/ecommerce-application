import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminOrderService } from '../../services/admin/adminOrderService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Order, OrderStatus } from '../../types';

const STATUSES: OrderStatus[] = [
  'PENDING',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'REFUNDED',
];

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

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    adminOrderService
      .list(statusFilter || undefined, page, 20)
      .then((paged) => {
        setOrders(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [statusFilter, page]);

  return (
    <>
      <PageHeader
        title="Orders"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Orders' }]}
        actions={
          <Link to="/admin/orders/new" className="btn btn-primary">
            <i className="bi bi-plus-lg me-1"></i>New Order
          </Link>
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <h3 className="card-title mb-0">All orders</h3>
          <select
            className="form-select form-select-sm"
            style={{ width: 'auto' }}
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value as OrderStatus | '');
            }}
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="card-body">
          {loading ? (
            <p className="text-muted mb-0">Loading…</p>
          ) : orders.length === 0 ? (
            <p className="text-muted mb-0">No orders.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Customer</th>
                    <th>Status</th>
                    <th className="text-end">Amount</th>
                    <th>Created</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((o) => (
                    <tr key={o.id}>
                      <td>
                        <code>{o.id.substring(0, 8)}</code>
                      </td>
                      <td className="text-muted small">{o.userId.substring(0, 8)}</td>
                      <td>
                        <span className={`badge ${statusBadge(o.status)}`}>{o.status}</span>
                      </td>
                      <td className="text-end">${Number(o.totalAmount).toFixed(2)}</td>
                      <td className="text-muted small">
                        {new Date(o.createdAt).toLocaleString()}
                      </td>
                      <td className="text-end">
                        <Link
                          to={`/admin/orders/${o.id}`}
                          className="btn btn-sm btn-outline-primary"
                        >
                          Open
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="d-flex justify-content-between align-items-center mt-3">
              <button
                className="btn btn-sm btn-outline-secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span className="text-muted small">
                Page {page + 1} of {totalPages}
              </span>
              <button
                className="btn btn-sm btn-outline-secondary"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
