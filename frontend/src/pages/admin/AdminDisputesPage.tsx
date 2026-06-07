import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { adminEscrowService } from '../../services/admin/adminEscrowService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Dispute, DisputeStatus } from '../../types';

const STATUSES: DisputeStatus[] = [
  'OPEN',
  'ESCALATED',
  'RESOLVED_RELEASED',
  'RESOLVED_REFUNDED',
  'WITHDRAWN',
];

const statusBadge = (status: DisputeStatus): string => {
  switch (status) {
    case 'OPEN':
      return 'bg-warning text-dark';
    case 'ESCALATED':
      return 'bg-danger';
    case 'RESOLVED_RELEASED':
      return 'bg-success';
    case 'RESOLVED_REFUNDED':
      return 'bg-secondary';
    case 'WITHDRAWN':
      return 'bg-dark';
    default:
      return 'bg-light text-dark';
  }
};

const truncate = (text: string, max = 60): string =>
  text.length > max ? `${text.substring(0, max)}…` : text;

export function AdminDisputesPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<Dispute[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<DisputeStatus | ''>('ESCALATED');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    adminEscrowService
      .listDisputes(statusFilter || undefined, page, 20)
      .then((paged) => {
        setRows(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [statusFilter, page]);

  return (
    <>
      <PageHeader
        title="Disputes"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Disputes' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <h3 className="card-title mb-0">Dispute queue</h3>
          <select
            className="form-select form-select-sm w-auto"
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value as DisputeStatus | '');
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
            <p className="text-muted mb-0">No disputes.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle table-hover">
                <thead>
                  <tr>
                    <th>Created</th>
                    <th>Order</th>
                    <th>Buyer</th>
                    <th>Seller</th>
                    <th className="text-end">Escrow</th>
                    <th>Reason</th>
                    <th>Status</th>
                    <th>Escalated</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((d) => (
                    <tr
                      key={d.id}
                      role="button"
                      onClick={() => navigate(`/admin/disputes/${d.id}`)}
                    >
                      <td className="text-muted small">
                        {new Date(d.createdAt).toLocaleString()}
                      </td>
                      <td>
                        <code>{d.orderId.substring(0, 8)}</code>
                      </td>
                      <td>{d.openedByName ?? <span className="text-muted">—</span>}</td>
                      <td>{d.sellerName ?? <span className="text-muted">—</span>}</td>
                      <td className="text-end">${Number(d.escrowAmount).toFixed(2)}</td>
                      <td className="small">{truncate(d.reason)}</td>
                      <td>
                        <span className={`badge ${statusBadge(d.status)}`}>{d.status}</span>
                      </td>
                      <td className="text-muted small">
                        {d.escalatedAt ? new Date(d.escalatedAt).toLocaleString() : '—'}
                      </td>
                      <td className="text-end">
                        <Link
                          to={`/admin/disputes/${d.id}`}
                          className="btn btn-sm btn-outline-primary"
                          onClick={(e) => e.stopPropagation()}
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
