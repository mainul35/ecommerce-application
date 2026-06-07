import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { disputeService } from '../../services/disputeService';
import { useAppSelector } from '../../store';
import { Loading } from '../../components/common';
import { AuthMedia } from './AuthMedia';
import { formatMoney } from './format';
import type { Dispute, DisputeMessage, DisputeStatus, DisputeAuthorRole } from '../../types';

const disputeStatusBadge = (status: DisputeStatus): string => {
  switch (status) {
    case 'OPEN':
      return 'bg-info text-dark';
    case 'ESCALATED':
      return 'bg-warning text-dark';
    case 'RESOLVED_RELEASED':
      return 'bg-success';
    case 'RESOLVED_REFUNDED':
      return 'bg-success';
    case 'WITHDRAWN':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

const roleChip = (role: DisputeAuthorRole): string => {
  switch (role) {
    case 'BUYER':
      return 'bg-primary-subtle text-primary';
    case 'SELLER':
      return 'bg-warning-subtle text-warning-emphasis';
    case 'STAFF':
      return 'bg-dark-subtle text-dark';
    default:
      return 'bg-light text-dark';
  }
};

const isActiveStatus = (status: DisputeStatus): boolean =>
  status === 'OPEN' || status === 'ESCALATED';

export const DisputeDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const currentUser = useAppSelector((state) => state.auth.user);

  const [dispute, setDispute] = useState<Dispute | null>(null);
  const [messages, setMessages] = useState<DisputeMessage[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [body, setBody] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadAll = (disputeId: string) => {
    setIsLoading(true);
    setError(null);
    Promise.all([disputeService.getDispute(disputeId), disputeService.getMessages(disputeId)])
      .then(([d, msgs]) => {
        setDispute(d);
        setMessages(msgs);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setIsLoading(false));
  };

  useEffect(() => {
    if (id) loadAll(id);
  }, [id]);

  const refreshThread = () => {
    if (!id) return;
    Promise.all([disputeService.getDispute(id), disputeService.getMessages(id)])
      .then(([d, msgs]) => {
        setDispute(d);
        setMessages(msgs);
      })
      .catch((e: Error) => setActionError(e.message));
  };

  const handleSend = async () => {
    if (!id) return;
    if (!body.trim() && files.length === 0) {
      setActionError('Type a message or attach a file first.');
      return;
    }
    setSending(true);
    setActionError(null);
    try {
      await disputeService.postMessage(id, body.trim(), files);
      setBody('');
      setFiles([]);
      refreshThread();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to send message');
    } finally {
      setSending(false);
    }
  };

  const handleEscalate = async () => {
    if (!id) return;
    if (
      !window.confirm(
        'Escalate to support? The full conversation will be forwarded to our support team for review.'
      )
    ) {
      return;
    }
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await disputeService.escalate(id);
      setDispute(updated);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to escalate');
    } finally {
      setActionBusy(false);
    }
  };

  const handleWithdraw = async () => {
    if (!id) return;
    if (!window.confirm('Withdraw this dispute? This cannot be undone.')) {
      return;
    }
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await disputeService.withdraw(id);
      setDispute(updated);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to withdraw');
    } finally {
      setActionBusy(false);
    }
  };

  if (isLoading) {
    return (
      <div className="container py-5">
        <Loading message="Loading dispute…" />
      </div>
    );
  }

  if (error || !dispute) {
    return (
      <div className="container py-5">
        <div className="alert alert-danger">{error ?? 'Dispute not found.'}</div>
        <Link to="/account/disputes" className="btn btn-outline-secondary">
          Back to disputes
        </Link>
      </div>
    );
  }

  const resolved =
    dispute.status === 'RESOLVED_RELEASED' || dispute.status === 'RESOLVED_REFUNDED';
  const isOpener = !!currentUser && currentUser.id === dispute.openedByUserId;

  return (
    <div className="container py-5">
      <div className="mb-3">
        <Link to={`/account/orders/${dispute.orderId}`} className="text-decoration-none small">
          <i className="bi bi-arrow-left me-1"></i>Back to order
        </Link>
      </div>

      <div className="card mb-4">
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
            <div>
              <h1 className="h4 mb-2">Dispute</h1>
              <span className={`badge ${disputeStatusBadge(dispute.status)}`}>
                {dispute.status.replace('_', ' ')}
              </span>
            </div>
            <div className="text-end">
              <div className="text-muted small">Escrow amount</div>
              <div className="fw-bold fs-5">{formatMoney(dispute.escrowAmount)}</div>
              {dispute.sellerName && (
                <div className="text-muted small">Seller: {dispute.sellerName}</div>
              )}
            </div>
          </div>

          <p className="mb-2">
            <span className="text-muted small d-block">Reason</span>
            {dispute.reason}
          </p>

          {resolved && (
            <div className="alert alert-success mb-0 mt-3">
              <div className="fw-semibold mb-1">
                {dispute.status === 'RESOLVED_REFUNDED' ? 'Resolved — refunded' : 'Resolved — released'}
              </div>
              {dispute.resolutionNote && <p className="mb-1">{dispute.resolutionNote}</p>}
              {typeof dispute.refundAmount === 'number' && dispute.refundAmount > 0 && (
                <p className="mb-0">Refund amount: {formatMoney(dispute.refundAmount)}</p>
              )}
            </div>
          )}
        </div>
      </div>

      {actionError && <div className="alert alert-danger">{actionError}</div>}

      <div className="card mb-4">
        <div className="card-header">
          <h5 className="card-title mb-0">Conversation</h5>
        </div>
        <div className="card-body">
          {messages.length === 0 ? (
            <p className="text-muted mb-0">No messages yet.</p>
          ) : (
            <div className="d-flex flex-column gap-3">
              {messages.map((msg) => {
                const mine = !!currentUser && currentUser.id === msg.senderUserId;
                return (
                  <div
                    key={msg.id}
                    className={`d-flex flex-column ${mine ? 'align-items-end' : 'align-items-start'}`}
                  >
                    <div
                      className={`p-3 rounded-3 ${
                        mine ? 'bg-primary-subtle' : 'bg-light'
                      }`}
                    >
                      <div className="d-flex align-items-center gap-2 mb-1">
                        <span className="fw-semibold small">
                          {msg.senderName ?? (mine ? 'You' : 'User')}
                        </span>
                        <span className={`badge ${roleChip(msg.authorRole)}`}>
                          {msg.authorRole}
                        </span>
                        <span className="text-muted small">
                          {new Date(msg.createdAt).toLocaleString()}
                        </span>
                      </div>
                      {msg.body && <p className="mb-0">{msg.body}</p>}
                      {msg.attachments.length > 0 && (
                        <div className="d-flex flex-wrap gap-2 mt-2">
                          {msg.attachments.map((att) => (
                            <AuthMedia
                              key={att.id}
                              url={att.url}
                              type={att.attachmentType}
                              alt={att.originalName}
                              className="dispute-attachment rounded"
                            />
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {isActiveStatus(dispute.status) && (
            <div className="mt-4 border-top pt-3">
              <label className="form-label small text-muted">Reply</label>
              <textarea
                className="form-control mb-2"
                rows={3}
                placeholder="Add a message…"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                disabled={sending}
              />
              <div className="d-flex flex-wrap gap-2 align-items-center">
                <input
                  type="file"
                  className="form-control form-control-sm"
                  accept="image/*,video/*"
                  multiple
                  onChange={(e) => setFiles(e.target.files ? Array.from(e.target.files) : [])}
                  disabled={sending}
                />
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleSend}
                  disabled={sending}
                >
                  {sending ? 'Sending…' : 'Send'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {isActiveStatus(dispute.status) && (
        <div className="d-flex flex-wrap gap-2">
          {dispute.status === 'OPEN' && (
            <button
              type="button"
              className="btn btn-outline-warning"
              onClick={handleEscalate}
              disabled={actionBusy}
            >
              Escalate to support
            </button>
          )}
          {isOpener && (
            <button
              type="button"
              className="btn btn-outline-danger"
              onClick={handleWithdraw}
              disabled={actionBusy}
            >
              Withdraw dispute
            </button>
          )}
        </div>
      )}
      {dispute.status === 'OPEN' && (
        <p className="text-muted small mt-2">
          Escalating forwards the full conversation to our support team, who will review the
          evidence and decide the outcome.
        </p>
      )}
    </div>
  );
};
