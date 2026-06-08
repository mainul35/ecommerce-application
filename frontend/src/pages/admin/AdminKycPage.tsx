import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { adminKycService } from '../../services/admin/adminKycService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { FaceVerdict, KycCase, KycStatus } from '../../types';

const STATUSES: KycStatus[] = [
  'SUBMITTED',
  'CHECKING',
  'IN_REVIEW',
  'APPROVED',
  'REJECTED',
  'EXPIRED',
];

const statusBadge = (status: KycStatus): string => {
  switch (status) {
    case 'APPROVED':
      return 'bg-success';
    case 'REJECTED':
      return 'bg-danger';
    case 'IN_REVIEW':
      return 'bg-warning text-dark';
    case 'CHECKING':
      return 'bg-info text-dark';
    case 'SUBMITTED':
      return 'bg-primary';
    case 'EXPIRED':
      return 'bg-dark';
    case 'DRAFT':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

const faceBadge = (verdict: FaceVerdict | undefined): string => {
  switch (verdict) {
    case 'MATCH':
      return 'bg-success';
    case 'NO_MATCH':
      return 'bg-danger';
    case 'UNKNOWN':
    default:
      return 'bg-secondary';
  }
};

/** Boolean signal as ✓ / ✗ / ? (null/undefined = OCR unavailable). */
const BoolChip = ({ label, value }: { label: string; value?: boolean | null }) => {
  const cls = value == null ? 'bg-secondary' : value ? 'bg-success' : 'bg-danger';
  const mark = value == null ? '?' : value ? '✓' : '✗';
  return (
    <span className={`badge ${cls}`} title={`${label}: ${mark}`}>
      {label} {mark}
    </span>
  );
};

const ScoreChip = ({ label, value }: { label: string; value?: number | null }) => {
  if (value == null) {
    return (
      <span className="badge bg-secondary" title={`${label}: n/a`}>
        {label} —
      </span>
    );
  }
  const pct = Math.round(value * 100);
  const cls = pct >= 80 ? 'bg-success' : pct >= 50 ? 'bg-warning text-dark' : 'bg-danger';
  return (
    <span className={`badge ${cls}`} title={`${label}: ${pct}%`}>
      {label} {pct}%
    </span>
  );
};

/** Hours until the 72h purge deadline; null when no deadline / already past. */
const hoursUntil = (iso?: string | null): number | null => {
  if (!iso) return null;
  const diffMs = new Date(iso).getTime() - Date.now();
  return Math.round(diffMs / 3_600_000);
};

export function AdminKycPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<KycCase[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<KycStatus | ''>('IN_REVIEW');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    adminKycService
      .list(statusFilter || undefined, page, 20)
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
        title="Seller KYC"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'KYC' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <h3 className="card-title mb-0">Review queue</h3>
          <select
            className="form-select form-select-sm w-auto"
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value as KycStatus | '');
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
            <p className="text-muted mb-0">No KYC cases.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle table-hover">
                <thead>
                  <tr>
                    <th>Submitted</th>
                    <th>User</th>
                    <th>Signals</th>
                    <th>Status</th>
                    <th>Retention</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((c) => {
                    const hrs = hoursUntil(c.expiresAt);
                    return (
                      <tr
                        key={c.id}
                        role="button"
                        onClick={() => navigate(`/admin/kyc/${c.id}`)}
                      >
                        <td className="text-muted small">
                          {c.submittedAt
                            ? new Date(c.submittedAt).toLocaleString()
                            : '—'}
                        </td>
                        <td>
                          <code>{c.userId.substring(0, 8)}</code>
                        </td>
                        <td>
                          <div className="d-flex flex-wrap gap-1">
                            <BoolChip label="ID" value={c.idDocumentOk} />
                            <BoolChip label="Bill" value={c.billDocumentOk} />
                            <ScoreChip label="Name" value={c.nameMatchScore} />
                            <ScoreChip label="Addr" value={c.addressMatchScore} />
                            <span
                              className={`badge ${faceBadge(c.faceVerdict)}`}
                              title={`Face: ${c.faceVerdict ?? 'UNKNOWN'} (advisory)`}
                            >
                              Face {c.faceVerdict ?? 'UNKNOWN'}
                            </span>
                          </div>
                        </td>
                        <td>
                          <span className={`badge ${statusBadge(c.status)}`}>
                            {c.status}
                          </span>
                          {c.status === 'APPROVED' && c.autoDecided && (
                            <span className="badge bg-secondary ms-1" title="Auto-approved by automation">
                              auto
                            </span>
                          )}
                        </td>
                        <td className="small">
                          {c.documentsPurgedAt ? (
                            <span className="text-muted">purged</span>
                          ) : hrs == null ? (
                            <span className="text-muted">—</span>
                          ) : hrs <= 0 ? (
                            <span className="text-danger">due</span>
                          ) : (
                            <span className={hrs <= 12 ? 'text-danger' : 'text-muted'}>
                              purges in {hrs}h
                            </span>
                          )}
                        </td>
                        <td className="text-end">
                          <Link
                            to={`/admin/kyc/${c.id}`}
                            className="btn btn-sm btn-outline-primary"
                            onClick={(e) => e.stopPropagation()}
                          >
                            Review
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
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
