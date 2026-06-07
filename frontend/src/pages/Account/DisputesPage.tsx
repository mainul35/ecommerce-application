import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { disputeService } from '../../services/disputeService';
import { Loading, EmptyState } from '../../components/common';
import { formatMoney } from './format';
import type { Dispute, DisputeStatus } from '../../types';

const statusBadge: Record<DisputeStatus, string> = {
  OPEN: 'bg-info',
  ESCALATED: 'bg-warning text-dark',
  RESOLVED_RELEASED: 'bg-success',
  RESOLVED_REFUNDED: 'bg-success',
  WITHDRAWN: 'bg-secondary',
};

const statusLabel: Record<DisputeStatus, string> = {
  OPEN: 'Open',
  ESCALATED: 'With support',
  RESOLVED_RELEASED: 'Resolved — released',
  RESOLVED_REFUNDED: 'Resolved — refunded',
  WITHDRAWN: 'Withdrawn',
};

/** All disputes the user participates in, as buyer or seller. */
export const DisputesPage = () => {
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    disputeService
      .getMyDisputes()
      .then((data) => {
        if (!cancelled) setDisputes(data);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (isLoading) return <Loading />;

  return (
    <div className="container py-4">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 mb-0">My Disputes</h1>
        <Link to="/account/orders" className="btn btn-outline-secondary btn-sm">
          <i className="bi bi-arrow-left me-1"></i>My Orders
        </Link>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {disputes.length === 0 ? (
        <EmptyState
          icon="bi-shield-check"
          title="No disputes"
          description="You have no payment disputes. Open one from an order's escrow panel if something goes wrong."
          actionLabel="My Orders"
          actionLink="/account/orders"
        />
      ) : (
        <div className="card shadow-sm">
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th>Opened</th>
                  <th>Order</th>
                  <th className="d-none d-md-table-cell">Seller</th>
                  <th>Amount</th>
                  <th className="d-none d-md-table-cell">Reason</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {disputes.map((d) => (
                  <tr key={d.id}>
                    <td>{new Date(d.createdAt).toLocaleDateString()}</td>
                    <td>
                      <Link to={`/account/orders/${d.orderId}`}>
                        {d.orderId.slice(0, 8)}…
                      </Link>
                    </td>
                    <td className="d-none d-md-table-cell">{d.sellerName ?? '—'}</td>
                    <td>{formatMoney(d.escrowAmount)}</td>
                    <td className="d-none d-md-table-cell text-truncate">
                      {d.reason.length > 60 ? `${d.reason.slice(0, 60)}…` : d.reason}
                    </td>
                    <td>
                      <span className={`badge ${statusBadge[d.status]}`}>
                        {statusLabel[d.status]}
                      </span>
                    </td>
                    <td className="text-end">
                      <Link to={`/account/disputes/${d.id}`} className="btn btn-sm btn-outline-primary">
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
