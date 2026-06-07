import { useEffect, useState } from 'react';
import { walletService } from '../../services/walletService';
import { Loading } from '../../components/common';
import { Pagination } from '../../components/common';
import { formatMoney } from './format';
import type {
  Wallet,
  WalletTransaction,
  WalletReferenceType,
  WalletTransactionType,
} from '../../types';

const REFERENCE_LABELS: Record<WalletReferenceType, string> = {
  ESCROW_RELEASE: 'Escrow release',
  DISPUTE_REFUND: 'Dispute refund',
  RETURN_REFUND: 'Return refund',
  ADJUSTMENT: 'Adjustment',
  WITHDRAWAL: 'Withdrawal',
};

const humanizeReference = (type: WalletReferenceType): string =>
  REFERENCE_LABELS[type] ?? type;

const typeBadge = (type: WalletTransactionType): string =>
  type === 'CREDIT' ? 'bg-success' : 'bg-danger';

const PAGE_SIZE = 20;

export const WalletPage = () => {
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [transactions, setTransactions] = useState<WalletTransaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setError(null);
    Promise.all([walletService.getWallet(), walletService.getTransactions(page, PAGE_SIZE)])
      .then(([w, paged]) => {
        if (!active) return;
        setWallet(w);
        setTransactions(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page]);

  if (isLoading) {
    return (
      <div className="container py-5">
        <Loading message="Loading your wallet…" />
      </div>
    );
  }

  const currency = wallet?.currencyCode || 'USD';

  return (
    <div className="container py-5">
      <h1 className="mb-4">My Wallet</h1>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-4">
        <div className="col-12 col-md-5 col-lg-4">
          <div className="card h-100">
            <div className="card-body">
              <h6 className="text-muted text-uppercase small mb-2">Balance</h6>
              <p className="display-6 fw-bold mb-1">
                {formatMoney(wallet?.balance ?? 0, currency)}
              </p>
              <p className="text-muted small mb-0">{currency}</p>
              <hr />
              <p className="text-muted small mb-0">
                Refunds land here when your original payment method can't receive them; sellers
                receive escrow payouts here.
              </p>
            </div>
          </div>
        </div>

        <div className="col-12 col-md-7 col-lg-8">
          <div className="card">
            <div className="card-header">
              <h5 className="card-title mb-0">Transactions</h5>
            </div>
            <div className="card-body p-0">
              {transactions.length === 0 ? (
                <p className="text-muted mb-0 p-4">No wallet activity yet.</p>
              ) : (
                <div className="table-responsive">
                  <table className="table align-middle mb-0">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Type</th>
                        <th>Reference</th>
                        <th>Description</th>
                        <th className="text-end">Amount</th>
                        <th className="text-end">Balance</th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactions.map((tx) => {
                        const signed =
                          tx.type === 'CREDIT'
                            ? `+${formatMoney(tx.amount, currency)}`
                            : `-${formatMoney(tx.amount, currency)}`;
                        return (
                          <tr key={tx.id}>
                            <td className="text-muted small">
                              {new Date(tx.createdAt).toLocaleString()}
                            </td>
                            <td>
                              <span className={`badge ${typeBadge(tx.type)}`}>{tx.type}</span>
                            </td>
                            <td className="small">{humanizeReference(tx.referenceType)}</td>
                            <td className="text-muted small">{tx.description ?? '—'}</td>
                            <td
                              className={`text-end fw-semibold ${
                                tx.type === 'CREDIT' ? 'text-success' : 'text-danger'
                              }`}
                            >
                              {signed}
                            </td>
                            <td className="text-end">{formatMoney(tx.balanceAfter, currency)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          <div className="mt-4">
            <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
          </div>
        </div>
      </div>
    </div>
  );
};
