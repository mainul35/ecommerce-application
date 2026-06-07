import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminEscrowService } from '../../services/admin/adminEscrowService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { EscrowStatus, EscrowTransaction } from '../../types';

const STATUSES: EscrowStatus[] = ['HELD', 'DISPUTED', 'RELEASED', 'REFUNDED'];

const statusBadge = (status: EscrowStatus): string => {
  switch (status) {
    case 'HELD':
      return 'bg-warning text-dark';
    case 'DISPUTED':
      return 'bg-danger';
    case 'RELEASED':
      return 'bg-success';
    case 'REFUNDED':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

export function AdminEscrowPage() {
  const [rows, setRows] = useState<EscrowTransaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<EscrowStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminEscrowService
      .listEscrow(statusFilter || undefined, page, 20)
      .then((paged) => {
        setRows(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, [statusFilter, page]);

  const release = async (tx: EscrowTransaction) => {
    if (
      !window.confirm(
        `Release $${Number(tx.amount).toFixed(2)} to ${tx.sellerName ?? 'the seller'}? This cannot be undone.`
      )
    )
      return;
    setBusyId(tx.id);
    setError(null);
    try {
      await adminEscrowService.releaseEscrow(tx.id);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Release failed');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Escrow"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Escrow' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <h3 className="card-title mb-0">Escrow transactions</h3>
          <select
            className="form-select form-select-sm w-auto"
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value as EscrowStatus | '');
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
            <p className="text-muted mb-0">No escrow transactions.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Created</th>
                    <th>Order</th>
                    <th>Seller</th>
                    <th className="text-end">Amount</th>
                    <th className="text-end">Refunded</th>
                    <th>Gateway</th>
                    <th>Status</th>
                    <th>Hold until</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((tx) => (
                    <tr key={tx.id}>
                      <td className="text-muted small">
                        {new Date(tx.createdAt).toLocaleString()}
                      </td>
                      <td>
                        <Link to={`/admin/orders/${tx.orderId}`}>
                          <code>{tx.orderId.substring(0, 8)}</code>
                        </Link>
                      </td>
                      <td>{tx.sellerName ?? <span className="text-muted">—</span>}</td>
                      <td className="text-end">${Number(tx.amount).toFixed(2)}</td>
                      <td className="text-end">
                        {Number(tx.refundedAmount) > 0 ? (
                          `$${Number(tx.refundedAmount).toFixed(2)}`
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td className="text-muted small">{tx.gatewayId ?? '—'}</td>
                      <td>
                        <span className={`badge ${statusBadge(tx.status)}`}>{tx.status}</span>
                      </td>
                      <td className="text-muted small">
                        {tx.holdUntil ? new Date(tx.holdUntil).toLocaleString() : '—'}
                      </td>
                      <td className="text-end">
                        {tx.status === 'HELD' ? (
                          <button
                            className="btn btn-sm btn-outline-success"
                            disabled={busyId === tx.id}
                            onClick={() => release(tx)}
                          >
                            {busyId === tx.id ? 'Releasing…' : 'Release'}
                          </button>
                        ) : tx.status === 'DISPUTED' ? (
                          <span
                            className="text-muted small"
                            title="Resolve the dispute instead of releasing directly."
                          >
                            Resolve the dispute
                          </span>
                        ) : null}
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
