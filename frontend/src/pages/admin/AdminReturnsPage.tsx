import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminEscrowService } from '../../services/admin/adminEscrowService';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { ReturnRequest, ReturnStatus } from '../../types';

const STATUSES: ReturnStatus[] = ['REQUESTED', 'REFUNDED', 'REJECTED', 'CANCELLED'];

const statusBadge = (status: ReturnStatus): string => {
  switch (status) {
    case 'REQUESTED':
      return 'bg-warning text-dark';
    case 'REFUNDED':
      return 'bg-success';
    case 'REJECTED':
      return 'bg-danger';
    case 'CANCELLED':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

const truncate = (text: string, max = 60): string =>
  text.length > max ? `${text.substring(0, max)}…` : text;

export function AdminReturnsPage() {
  const [rows, setRows] = useState<ReturnRequest[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<ReturnStatus | ''>('REQUESTED');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminEscrowService
      .listReturns(statusFilter || undefined, page, 20)
      .then((paged) => {
        setRows(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, [statusFilter, page]);

  const approve = async (r: ReturnRequest) => {
    if (
      !window.confirm(
        `Approve and refund $${Number(r.refundAmount).toFixed(2)}? The refund goes to the original payment gateway, or to the buyer's wallet if the gateway charge is no longer refundable.`
      )
    )
      return;
    setBusyId(r.id);
    setError(null);
    try {
      await adminEscrowService.approveReturn(r.id);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Approval failed');
    } finally {
      setBusyId(null);
    }
  };

  const reject = async (r: ReturnRequest) => {
    const reason = window.prompt('Rejection reason (shown to the buyer)?');
    if (reason === null) return;
    if (!reason.trim()) {
      setError('A rejection reason is required.');
      return;
    }
    setBusyId(r.id);
    setError(null);
    try {
      await adminEscrowService.rejectReturn(r.id, reason.trim());
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Rejection failed');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Returns"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Returns' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <h3 className="card-title mb-0">Return requests</h3>
          <select
            className="form-select form-select-sm w-auto"
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value as ReturnStatus | '');
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
          ) : rows.length === 0 ? (
            <p className="text-muted mb-0">No return requests.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Created</th>
                    <th>Order</th>
                    <th>Product</th>
                    <th className="text-end">Qty</th>
                    <th className="text-end">Refund</th>
                    <th>Reason</th>
                    <th>Status</th>
                    <th>Destination</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.id}>
                      <td className="text-muted small">
                        {new Date(r.createdAt).toLocaleString()}
                      </td>
                      <td>
                        <Link to={`/admin/orders/${r.orderId}`}>
                          <code>{r.orderId.substring(0, 8)}</code>
                        </Link>
                      </td>
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          {r.productImage && (
                            <img
                              src={mediaUrl(r.productImage)}
                              alt={r.productName ?? 'product'}
                              className="rounded object-fit-cover"
                              width={32}
                              height={32}
                            />
                          )}
                          <span>{r.productName ?? '—'}</span>
                        </div>
                      </td>
                      <td className="text-end">{r.quantity}</td>
                      <td className="text-end">${Number(r.refundAmount).toFixed(2)}</td>
                      <td className="small">
                        {truncate(r.reason)}
                        {r.status === 'REJECTED' && r.rejectionReason && (
                          <div className="text-danger small">Rejected: {r.rejectionReason}</div>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${statusBadge(r.status)}`}>{r.status}</span>
                      </td>
                      <td>
                        {r.refundDestination ? (
                          <span
                            className={`badge ${
                              r.refundDestination === 'WALLET' ? 'bg-info text-dark' : 'bg-primary'
                            }`}
                          >
                            {r.refundDestination}
                          </span>
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td className="text-end">
                        {r.status === 'REQUESTED' && (
                          <div className="d-flex flex-wrap justify-content-end gap-2">
                            <button
                              className="btn btn-sm btn-outline-success"
                              disabled={busyId === r.id}
                              onClick={() => approve(r)}
                            >
                              Approve & refund
                            </button>
                            <button
                              className="btn btn-sm btn-outline-danger"
                              disabled={busyId === r.id}
                              onClick={() => reject(r)}
                            >
                              Reject
                            </button>
                          </div>
                        )}
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
