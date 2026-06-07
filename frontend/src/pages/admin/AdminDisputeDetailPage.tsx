import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminEscrowService } from '../../services/admin/adminEscrowService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import { AdminAuthMedia } from './AdminAuthMedia';
import type {
  Dispute,
  DisputeAuthorRole,
  DisputeMessage,
  DisputeStatus,
} from '../../types';

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

const roleChip = (role: DisputeAuthorRole): string => {
  switch (role) {
    case 'BUYER':
      return 'bg-info text-dark';
    case 'SELLER':
      return 'bg-warning text-dark';
    case 'STAFF':
      return 'bg-dark';
    default:
      return 'bg-light text-dark';
  }
};

const isActive = (status: DisputeStatus): boolean => status === 'OPEN' || status === 'ESCALATED';

export function AdminDisputeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [dispute, setDispute] = useState<Dispute | null>(null);
  const [messages, setMessages] = useState<DisputeMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Reply composer
  const [replyBody, setReplyBody] = useState('');
  const [replyFiles, setReplyFiles] = useState<File[]>([]);
  const [sending, setSending] = useState(false);

  // Resolution
  const [resolving, setResolving] = useState(false);
  const [releaseNote, setReleaseNote] = useState('');
  const [refundNote, setRefundNote] = useState('');
  const [refundAmount, setRefundAmount] = useState('');

  const load = () => {
    if (!id) return;
    setLoading(true);
    Promise.all([adminEscrowService.getDispute(id), adminEscrowService.getDisputeMessages(id)])
      .then(([d, msgs]) => {
        setDispute(d);
        setMessages(msgs);
        const max = Number(d.escrowAmount) - Number(d.escrowRefundedAmount);
        setRefundAmount(max > 0 ? max.toFixed(2) : '');
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [id]);

  const sendReply = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    if (!replyBody.trim() && replyFiles.length === 0) {
      setError('Add a message or an attachment before sending.');
      return;
    }
    setSending(true);
    setError(null);
    try {
      await adminEscrowService.postDisputeMessage(id, replyBody.trim(), replyFiles);
      setReplyBody('');
      setReplyFiles([]);
      const msgs = await adminEscrowService.getDisputeMessages(id);
      setMessages(msgs);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send reply');
    } finally {
      setSending(false);
    }
  };

  const release = async () => {
    if (!id || !dispute) return;
    if (
      !window.confirm(
        `Release the remaining escrow to ${dispute.sellerName ?? 'the seller'} and close this dispute?`
      )
    )
      return;
    setResolving(true);
    setError(null);
    try {
      await adminEscrowService.resolveDispute(id, 'RELEASE', {
        note: releaseNote.trim() || undefined,
      });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Resolution failed');
    } finally {
      setResolving(false);
    }
  };

  const refund = async () => {
    if (!id || !dispute) return;
    const max = Number(dispute.escrowAmount) - Number(dispute.escrowRefundedAmount);
    const amount = Number(refundAmount);
    if (Number.isNaN(amount) || amount <= 0 || amount > max) {
      setError(`Refund amount must be greater than 0 and at most $${max.toFixed(2)}.`);
      return;
    }
    if (
      !window.confirm(
        `Refund $${amount.toFixed(2)} to ${dispute.openedByName ?? 'the buyer'}? Any remainder is released to the seller.`
      )
    )
      return;
    setResolving(true);
    setError(null);
    try {
      await adminEscrowService.resolveDispute(id, 'REFUND', {
        refundAmount: amount,
        note: refundNote.trim() || undefined,
      });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Refund failed');
    } finally {
      setResolving(false);
    }
  };

  if (loading) return <p className="text-muted">Loading…</p>;
  if (!dispute) return <p className="text-danger">Dispute not found.</p>;

  const active = isActive(dispute.status);
  const maxRefund = Number(dispute.escrowAmount) - Number(dispute.escrowRefundedAmount);

  return (
    <>
      <PageHeader
        title={`Dispute ${dispute.id.substring(0, 8)}`}
        crumbs={[
          { label: 'Home', to: '/admin' },
          { label: 'Disputes', to: '/admin/disputes' },
          { label: dispute.id.substring(0, 8) },
        ]}
        actions={
          <button
            className="btn btn-outline-secondary"
            onClick={() => navigate('/admin/disputes')}
          >
            Back
          </button>
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-3">
        <div className="col-12 col-lg-8">
          {/* Summary */}
          <div className="card mb-3">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h3 className="card-title mb-0">Summary</h3>
              <span className={`badge ${statusBadge(dispute.status)}`}>{dispute.status}</span>
            </div>
            <div className="card-body">
              <div className="row g-3 small">
                <div className="col-12 col-sm-6">
                  <span className="text-muted d-block">Order</span>
                  <Link to={`/admin/orders/${dispute.orderId}`}>
                    <code>{dispute.orderId.substring(0, 8)}</code>
                  </Link>
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Buyer</span>
                  {dispute.openedByName ?? '—'}
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Seller</span>
                  {dispute.sellerName ?? '—'}
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Escrow amount</span>
                  ${Number(dispute.escrowAmount).toFixed(2)}
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Refunded</span>
                  ${Number(dispute.escrowRefundedAmount).toFixed(2)}
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Created</span>
                  {new Date(dispute.createdAt).toLocaleString()}
                </div>
                <div className="col-6 col-sm-3">
                  <span className="text-muted d-block">Escalated</span>
                  {dispute.escalatedAt ? new Date(dispute.escalatedAt).toLocaleString() : '—'}
                </div>
                <div className="col-12">
                  <span className="text-muted d-block">Reason</span>
                  {dispute.reason}
                </div>
              </div>

              {!active && (
                <div className="alert alert-light border mt-3 mb-0 small">
                  <strong>Resolution</strong>
                  <div className="mt-1">
                    <span className="text-muted">Resolved at:</span>{' '}
                    {dispute.resolvedAt ? new Date(dispute.resolvedAt).toLocaleString() : '—'}
                  </div>
                  {dispute.refundAmount != null && (
                    <div>
                      <span className="text-muted">Refund amount:</span> $
                      {Number(dispute.refundAmount).toFixed(2)}
                    </div>
                  )}
                  {dispute.resolutionNote && (
                    <div>
                      <span className="text-muted">Note:</span> {dispute.resolutionNote}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Conversation */}
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">Conversation</h3>
            </div>
            <div className="card-body">
              {messages.length === 0 ? (
                <p className="text-muted mb-0">No messages yet.</p>
              ) : (
                <div className="d-flex flex-column gap-3">
                  {messages.map((m) => (
                    <div key={m.id} className="border rounded p-2 p-md-3 bg-body-tertiary">
                      <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
                        <span className={`badge ${roleChip(m.authorRole)}`}>{m.authorRole}</span>
                        <span className="fw-semibold">{m.senderName ?? 'Unknown'}</span>
                        <span className="text-muted small ms-auto">
                          {new Date(m.createdAt).toLocaleString()}
                        </span>
                      </div>
                      {m.body && <p className="mb-2 text-break">{m.body}</p>}
                      {m.attachments.length > 0 && (
                        <div className="row g-2">
                          {m.attachments.map((att) => (
                            <div key={att.id} className="col-6 col-md-4">
                              <AdminAuthMedia
                                url={att.url}
                                type={att.attachmentType}
                                originalName={att.originalName}
                              />
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {active && (
                <form className="mt-4" onSubmit={sendReply}>
                  <h5 className="h6">Reply as support</h5>
                  <div className="mb-2">
                    <textarea
                      className="form-control"
                      rows={3}
                      placeholder="Type a reply to the buyer and seller…"
                      value={replyBody}
                      onChange={(e) => setReplyBody(e.target.value)}
                    />
                  </div>
                  <div className="mb-2">
                    <input
                      type="file"
                      className="form-control"
                      multiple
                      accept="image/*,video/*"
                      onChange={(e) => setReplyFiles(Array.from(e.target.files ?? []))}
                    />
                  </div>
                  <button type="submit" className="btn btn-primary" disabled={sending}>
                    {sending ? 'Sending…' : 'Send reply'}
                  </button>
                </form>
              )}
            </div>
          </div>
        </div>

        {/* Resolution panel */}
        <div className="col-12 col-lg-4">
          {active ? (
            <div className="card card-primary card-outline">
              <div className="card-header">
                <h3 className="card-title">Resolve dispute</h3>
              </div>
              <div className="card-body">
                <h5 className="h6">Release to seller</h5>
                <div className="mb-2">
                  <label className="form-label small">Note (optional)</label>
                  <textarea
                    className="form-control form-control-sm"
                    rows={2}
                    value={releaseNote}
                    onChange={(e) => setReleaseNote(e.target.value)}
                  />
                </div>
                <button
                  className="btn btn-success w-100 mb-4"
                  disabled={resolving}
                  onClick={release}
                >
                  Release to seller
                </button>

                <hr />

                <h5 className="h6">Refund buyer</h5>
                <div className="mb-2">
                  <label className="form-label small">Refund amount ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={maxRefund > 0 ? maxRefund.toFixed(2) : undefined}
                    className="form-control form-control-sm"
                    value={refundAmount}
                    onChange={(e) => setRefundAmount(e.target.value)}
                  />
                  <div className="form-text">
                    Max ${maxRefund.toFixed(2)}. A partial refund releases the remainder to the
                    seller.
                  </div>
                </div>
                <div className="mb-2">
                  <label className="form-label small">Note (optional)</label>
                  <textarea
                    className="form-control form-control-sm"
                    rows={2}
                    value={refundNote}
                    onChange={(e) => setRefundNote(e.target.value)}
                  />
                </div>
                <button
                  className="btn btn-outline-danger w-100"
                  disabled={resolving}
                  onClick={refund}
                >
                  Refund buyer
                </button>
              </div>
            </div>
          ) : (
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">Resolution</h3>
              </div>
              <div className="card-body">
                <p className="text-muted mb-0 small">
                  This dispute is closed ({dispute.status}). No further actions are available.
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
