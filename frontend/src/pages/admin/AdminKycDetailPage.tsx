import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { adminKycService } from '../../services/admin/adminKycService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import { KycEvidenceImage } from './KycEvidenceImage';
import type { FaceVerdict, KycCase, KycDocType, KycStatus } from '../../types';

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

const DOC_LABELS: Record<KycDocType, string> = {
  ID_FRONT: 'ID front',
  ID_BACK: 'ID back',
  SELFIE_FRONT: 'Selfie front',
  SELFIE_LEFT: 'Selfie left',
  SELFIE_RIGHT: 'Selfie right',
  UTILITY_BILL: 'Utility bill',
};

/** Order documents render in, regardless of API ordering. */
const DOC_ORDER: KycDocType[] = [
  'ID_FRONT',
  'ID_BACK',
  'SELFIE_FRONT',
  'SELFIE_LEFT',
  'SELFIE_RIGHT',
  'UTILITY_BILL',
];

const BoolRow = ({ label, value }: { label: string; value?: boolean | null }) => {
  const cls = value == null ? 'bg-secondary' : value ? 'bg-success' : 'bg-danger';
  const text = value == null ? 'OCR unavailable' : value ? 'OK' : 'Failed';
  return (
    <div className="d-flex justify-content-between align-items-center">
      <span className="text-muted">{label}</span>
      <span className={`badge ${cls}`}>{text}</span>
    </div>
  );
};

const ScoreRow = ({ label, value }: { label: string; value?: number | null }) => {
  const pct = value == null ? null : Math.round(value * 100);
  const cls =
    pct == null
      ? 'bg-secondary'
      : pct >= 80
        ? 'bg-success'
        : pct >= 50
          ? 'bg-warning text-dark'
          : 'bg-danger';
  return (
    <div className="d-flex justify-content-between align-items-center">
      <span className="text-muted">{label}</span>
      <span className={`badge ${cls}`}>{pct == null ? 'n/a' : `${pct}%`}</span>
    </div>
  );
};

const hoursUntil = (iso?: string | null): number | null => {
  if (!iso) return null;
  return Math.round((new Date(iso).getTime() - Date.now()) / 3_600_000);
};

const isDecidable = (status: KycStatus): boolean =>
  status === 'IN_REVIEW' || status === 'SUBMITTED' || status === 'CHECKING';

export function AdminKycDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [kyc, setKyc] = useState<KycCase | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [rejectReason, setRejectReason] = useState('');
  const [deciding, setDeciding] = useState(false);

  const load = () => {
    if (!id) return;
    setLoading(true);
    adminKycService
      .getCase(id)
      .then(setKyc)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [id]);

  const approve = async () => {
    if (!id) return;
    if (
      !window.confirm(
        'Marks the account as ID-verified and permanently deletes the evidence. Continue?'
      )
    )
      return;
    setDeciding(true);
    setError(null);
    try {
      await adminKycService.approve(id);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approval failed');
    } finally {
      setDeciding(false);
    }
  };

  const reject = async () => {
    if (!id) return;
    const reason = rejectReason.trim();
    if (!reason) {
      setError('A rejection reason is required.');
      return;
    }
    if (
      !window.confirm(
        'Rejects this submission and permanently deletes the evidence. The user can redo KYC with a fresh submission. Continue?'
      )
    )
      return;
    setDeciding(true);
    setError(null);
    try {
      await adminKycService.reject(id, reason);
      setRejectReason('');
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Rejection failed');
    } finally {
      setDeciding(false);
    }
  };

  if (loading) return <p className="text-muted">Loading…</p>;
  if (!kyc) return <p className="text-danger">KYC case not found.</p>;

  const purged = !!kyc.documentsPurgedAt;
  const decidable = isDecidable(kyc.status);
  const hrs = hoursUntil(kyc.expiresAt);

  const orderedDocs = [...kyc.documents].sort(
    (a, b) => DOC_ORDER.indexOf(a.docType) - DOC_ORDER.indexOf(b.docType)
  );

  return (
    <>
      <PageHeader
        title={`KYC ${kyc.id.substring(0, 8)}`}
        crumbs={[
          { label: 'Home', to: '/admin' },
          { label: 'KYC', to: '/admin/kyc' },
          { label: kyc.id.substring(0, 8) },
        ]}
        actions={
          <button className="btn btn-outline-secondary" onClick={() => navigate('/admin/kyc')}>
            Back
          </button>
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-3">
        {/* Evidence */}
        <div className="col-12 col-lg-7">
          <div className="card">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h3 className="card-title mb-0">Evidence</h3>
              <span className={`badge ${statusBadge(kyc.status)}`}>{kyc.status}</span>
            </div>
            <div className="card-body">
              {purged ? (
                <div className="alert alert-info mb-0">
                  Evidence was purged (privacy policy: max 72h retention).
                </div>
              ) : orderedDocs.length === 0 ? (
                <p className="text-muted mb-0">No documents on this case.</p>
              ) : (
                <div className="row g-3">
                  {orderedDocs.map((doc) => (
                    <div key={doc.id} className="col-12 col-sm-6">
                      <KycEvidenceImage
                        caseId={kyc.id}
                        documentId={doc.id}
                        label={DOC_LABELS[doc.docType] ?? doc.docType}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Signals + decision */}
        <div className="col-12 col-lg-5">
          {/* Signals */}
          <div className="card mb-3">
            <div className="card-header">
              <h3 className="card-title mb-0">Automated signals</h3>
            </div>
            <div className="card-body">
              <div className="d-flex flex-column gap-2 small">
                <BoolRow label="ID document" value={kyc.idDocumentOk} />
                <BoolRow label="Utility bill" value={kyc.billDocumentOk} />
                <ScoreRow label="Name match" value={kyc.nameMatchScore} />
                <ScoreRow label="Address match" value={kyc.addressMatchScore} />
                <div className="d-flex justify-content-between align-items-center">
                  <span className="text-muted">
                    Face verdict <em className="text-muted">(advisory)</em>
                  </span>
                  <span className={`badge ${faceBadge(kyc.faceVerdict)}`}>
                    {kyc.faceVerdict ?? 'UNKNOWN'}
                  </span>
                </div>
              </div>

              <hr />

              <div className="row g-2 small">
                <div className="col-6">
                  <span className="text-muted d-block">Submitted</span>
                  {kyc.submittedAt ? new Date(kyc.submittedAt).toLocaleString() : '—'}
                </div>
                <div className="col-6">
                  <span className="text-muted d-block">Retention</span>
                  {purged ? (
                    <span className="text-muted">purged</span>
                  ) : hrs == null ? (
                    '—'
                  ) : hrs <= 0 ? (
                    <span className="text-danger">due</span>
                  ) : (
                    <span className={hrs <= 12 ? 'text-danger' : ''}>purges in {hrs}h</span>
                  )}
                </div>
              </div>

              {/* OCR extracts */}
              {!purged && (kyc.extractedIdText || kyc.extractedBillText || kyc.faceNote) && (
                <div className="mt-3">
                  {kyc.extractedIdText && (
                    <details className="mb-2">
                      <summary className="small fw-semibold">ID OCR text</summary>
                      <pre className="small text-body-secondary bg-body-tertiary border rounded p-2 mt-1 mb-0 text-wrap">
                        {kyc.extractedIdText}
                      </pre>
                    </details>
                  )}
                  {kyc.extractedBillText && (
                    <details className="mb-2">
                      <summary className="small fw-semibold">Utility bill OCR text</summary>
                      <pre className="small text-body-secondary bg-body-tertiary border rounded p-2 mt-1 mb-0 text-wrap">
                        {kyc.extractedBillText}
                      </pre>
                    </details>
                  )}
                  {kyc.faceNote && (
                    <blockquote className="blockquote small text-muted border-start ps-2 mb-0">
                      <em>“{kyc.faceNote}”</em>
                    </blockquote>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Decision */}
          {decidable ? (
            <div className="card card-primary card-outline">
              <div className="card-header">
                <h3 className="card-title mb-0">Decision</h3>
              </div>
              <div className="card-body">
                <button
                  className="btn btn-success w-100 mb-4"
                  disabled={deciding}
                  onClick={approve}
                >
                  Approve &amp; verify
                </button>

                <hr />

                <h5 className="h6">Reject</h5>
                <div className="mb-2">
                  <label className="form-label small">Reason (shown to the user)</label>
                  <textarea
                    className="form-control form-control-sm"
                    rows={3}
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Explain what was wrong so the seller can redo KYC…"
                  />
                </div>
                <button
                  className="btn btn-outline-danger w-100"
                  disabled={deciding || !rejectReason.trim()}
                  onClick={reject}
                >
                  Reject submission
                </button>
              </div>
            </div>
          ) : (
            <div className="card">
              <div className="card-header">
                <h3 className="card-title mb-0">Outcome</h3>
              </div>
              <div className="card-body">
                <div className="d-flex flex-column gap-2 small">
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted">Status</span>
                    <span>
                      <span className={`badge ${statusBadge(kyc.status)}`}>{kyc.status}</span>
                      {kyc.autoDecided && (
                        <span className="badge bg-secondary ms-1">auto</span>
                      )}
                    </span>
                  </div>
                  <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted">Decided at</span>
                    <span>
                      {kyc.decidedAt ? new Date(kyc.decidedAt).toLocaleString() : '—'}
                    </span>
                  </div>
                  {kyc.rejectionReason && (
                    <div>
                      <span className="text-muted d-block">Rejection reason</span>
                      <span className="text-break">{kyc.rejectionReason}</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
