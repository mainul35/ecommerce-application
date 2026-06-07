import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { orderService } from '../../services/orderService';
import { escrowService } from '../../services/escrowService';
import { disputeService } from '../../services/disputeService';
import { Loading } from '../../components/common';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import { formatMoney } from './format';
import type {
  Order,
  OrderItem,
  OrderStatus,
  EscrowTransaction,
  EscrowStatus,
} from '../../types';

const orderStatusBadge = (status: OrderStatus): string => {
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

const escrowStatusBadge = (status: EscrowStatus): string => {
  switch (status) {
    case 'HELD':
      return 'bg-warning text-dark';
    case 'RELEASED':
      return 'bg-success';
    case 'DISPUTED':
      return 'bg-danger';
    case 'REFUNDED':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

const returnableQty = (item: OrderItem): number =>
  item.quantity - (item.returnedQuantity ?? 0);

const itemImageSrc = (item: OrderItem): string =>
  item.productImage
    ? item.productImage.startsWith('/uploads/')
      ? mediaUrl(item.productImage)
      : item.productImage
    : '/placeholder.png';

export const OrderDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [escrows, setEscrows] = useState<EscrowTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Return modal state
  const [returnItem, setReturnItem] = useState<OrderItem | null>(null);
  const [returnQty, setReturnQty] = useState(1);
  const [returnReason, setReturnReason] = useState('');
  const [returnSubmitting, setReturnSubmitting] = useState(false);
  const [returnError, setReturnError] = useState<string | null>(null);

  // Dispute modal state
  const [disputeEscrow, setDisputeEscrow] = useState<EscrowTransaction | null>(null);
  const [disputeReason, setDisputeReason] = useState('');
  const [disputeSubmitting, setDisputeSubmitting] = useState(false);
  const [disputeError, setDisputeError] = useState<string | null>(null);

  const loadAll = (orderId: string) => {
    setIsLoading(true);
    setError(null);
    Promise.all([orderService.getOrderById(orderId), escrowService.getOrderEscrow(orderId)])
      .then(([o, esc]) => {
        setOrder(o);
        setEscrows(esc);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setIsLoading(false));
  };

  useEffect(() => {
    if (id) loadAll(id);
  }, [id]);

  const openReturnModal = (item: OrderItem) => {
    setReturnItem(item);
    setReturnQty(1);
    setReturnReason('');
    setReturnError(null);
  };

  const submitReturn = async () => {
    if (!id || !returnItem) return;
    if (!returnReason.trim()) {
      setReturnError('Please give a reason for the return.');
      return;
    }
    setReturnSubmitting(true);
    setReturnError(null);
    try {
      await escrowService.requestReturn(id, returnItem.id, returnQty, returnReason.trim());
      setReturnItem(null);
      setActionMessage('Return requested. We will review it shortly.');
      loadAll(id);
    } catch (e) {
      setReturnError(e instanceof Error ? e.message : 'Failed to request return');
    } finally {
      setReturnSubmitting(false);
    }
  };

  const handleConfirmReceipt = async () => {
    if (!id) return;
    if (
      !window.confirm(
        'Confirm receipt? This releases the protected payment from escrow to the sellers and cannot be undone.'
      )
    ) {
      return;
    }
    setBusy(true);
    setActionError(null);
    setActionMessage(null);
    try {
      const updated = await escrowService.confirmReceipt(id);
      setEscrows(updated);
      setActionMessage('Payment released to the sellers. Thank you!');
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to confirm receipt');
    } finally {
      setBusy(false);
    }
  };

  const openDisputeModal = (escrow: EscrowTransaction) => {
    setDisputeEscrow(escrow);
    setDisputeReason('');
    setDisputeError(null);
  };

  const submitDispute = async () => {
    if (!disputeEscrow) return;
    if (!disputeReason.trim()) {
      setDisputeError('Please describe the problem.');
      return;
    }
    setDisputeSubmitting(true);
    setDisputeError(null);
    try {
      const dispute = await disputeService.openDispute(disputeEscrow.id, disputeReason.trim());
      setDisputeEscrow(null);
      navigate(`/account/disputes/${dispute.id}`);
    } catch (e) {
      setDisputeError(e instanceof Error ? e.message : 'Failed to open dispute');
    } finally {
      setDisputeSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="container py-5">
        <Loading message="Loading order…" />
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="container py-5">
        <div className="alert alert-danger">{error ?? 'Order not found.'}</div>
        <Link to="/account/orders" className="btn btn-outline-secondary">
          Back to orders
        </Link>
      </div>
    );
  }

  const canReturn = (item: OrderItem): boolean =>
    order.status === 'DELIVERED' && returnableQty(item) > 0;

  const hasHeldEscrow = escrows.some((e) => e.status === 'HELD');
  const showConfirmReceipt = order.status === 'DELIVERED' && hasHeldEscrow;

  return (
    <div className="container py-5">
      <div className="mb-3">
        <Link to="/account/orders" className="text-decoration-none small">
          <i className="bi bi-arrow-left me-1"></i>Back to orders
        </Link>
      </div>

      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-4">
        <div>
          <h1 className="h4 mb-1">
            Order <code>{order.id.substring(0, 8)}</code>
          </h1>
          <p className="text-muted small mb-0">
            Placed {new Date(order.createdAt).toLocaleString()}
          </p>
        </div>
        <span className={`badge ${orderStatusBadge(order.status)} align-self-center`}>
          {order.status}
        </span>
      </div>

      {actionMessage && <div className="alert alert-success">{actionMessage}</div>}
      {actionError && <div className="alert alert-danger">{actionError}</div>}

      <div className="row g-4">
        <div className="col-12 col-lg-8">
          {/* Items */}
          <div className="card mb-4">
            <div className="card-header">
              <h5 className="card-title mb-0">Items</h5>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table align-middle mb-0">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th className="text-center">Qty</th>
                      <th className="text-end">Price</th>
                      <th className="text-end"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <div className="d-flex align-items-center gap-2">
                            <img
                              src={itemImageSrc(item)}
                              alt={item.productName}
                              className="order-item-thumb rounded"
                            />
                            <div>
                              <div className="fw-semibold">{item.productName}</div>
                              {(item.returnedQuantity ?? 0) > 0 && (
                                <div className="text-muted small">
                                  {item.returnedQuantity} returned
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="text-center">{item.quantity}</td>
                        <td className="text-end">{formatMoney(item.price)}</td>
                        <td className="text-end">
                          {canReturn(item) && (
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-secondary"
                              onClick={() => openReturnModal(item)}
                            >
                              Return
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Escrow / buyer protection */}
          <div className="card mb-4">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h5 className="card-title mb-0">Buyer protection</h5>
              {showConfirmReceipt && (
                <button
                  type="button"
                  className="btn btn-sm btn-success"
                  onClick={handleConfirmReceipt}
                  disabled={busy}
                >
                  Confirm receipt
                </button>
              )}
            </div>
            <div className="card-body">
              {showConfirmReceipt && (
                <p className="text-muted small">
                  Confirming receipt releases the protected payment from escrow to your sellers.
                  Only do this once you've received and checked everything.
                </p>
              )}
              {escrows.length === 0 ? (
                <p className="text-muted mb-0">No protected payments on this order.</p>
              ) : (
                <div className="row g-3">
                  {escrows.map((esc) => (
                    <div key={esc.id} className="col-12 col-md-6">
                      <div className="border rounded p-3 h-100">
                        <div className="d-flex justify-content-between align-items-start mb-2">
                          <span className="fw-semibold">
                            {esc.sellerName ?? 'Marketplace'}
                          </span>
                          <span className={`badge ${escrowStatusBadge(esc.status)}`}>
                            {esc.status}
                          </span>
                        </div>
                        <div className="d-flex justify-content-between small mb-1">
                          <span className="text-muted">Amount</span>
                          <span>{formatMoney(esc.amount, esc.currencyCode)}</span>
                        </div>
                        {esc.refundedAmount > 0 && (
                          <div className="d-flex justify-content-between small mb-1">
                            <span className="text-muted">Refunded</span>
                            <span>{formatMoney(esc.refundedAmount, esc.currencyCode)}</span>
                          </div>
                        )}
                        {esc.status === 'HELD' && esc.holdUntil && (
                          <p className="text-muted small mb-2">
                            Payment protected until{' '}
                            {new Date(esc.holdUntil).toLocaleDateString()}
                          </p>
                        )}
                        {esc.status === 'HELD' && (
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-danger w-100"
                            onClick={() => openDisputeModal(esc)}
                          >
                            Report a problem
                          </button>
                        )}
                        {esc.status === 'DISPUTED' && (
                          <Link
                            to="/account/disputes"
                            className="btn btn-sm btn-outline-primary w-100"
                          >
                            View dispute
                          </Link>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Summary */}
        <div className="col-12 col-lg-4">
          <div className="card">
            <div className="card-body">
              <h5 className="card-title mb-4">Order Summary</h5>
              {typeof order.subtotalAmount === 'number' && (
                <div className="d-flex justify-content-between mb-2">
                  <span>Subtotal</span>
                  <span>{formatMoney(order.subtotalAmount)}</span>
                </div>
              )}
              {order.couponCode && (
                <div className="d-flex justify-content-between mb-2 text-success">
                  <span>
                    Coupon <span className="badge bg-success">{order.couponCode}</span>
                  </span>
                  <span>
                    {order.couponDiscountAmount
                      ? `-${formatMoney(order.couponDiscountAmount)}`
                      : '—'}
                  </span>
                </div>
              )}
              <hr />
              <div className="d-flex justify-content-between mb-2">
                <strong>Total</strong>
                <strong>{formatMoney(order.totalAmount)}</strong>
              </div>
              {order.paymentStatus && (
                <p className="text-muted small mb-0">Payment: {order.paymentStatus}</p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Return modal */}
      {returnItem && (
        <>
          <div className="modal d-block" tabIndex={-1} role="dialog">
            <div className="modal-dialog modal-dialog-centered" role="document">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">Return item</h5>
                  <button
                    type="button"
                    className="btn-close"
                    aria-label="Close"
                    onClick={() => setReturnItem(null)}
                  ></button>
                </div>
                <div className="modal-body">
                  <p className="fw-semibold mb-3">{returnItem.productName}</p>
                  {returnError && <div className="alert alert-danger">{returnError}</div>}
                  <div className="mb-3">
                    <label className="form-label">Quantity to return</label>
                    <input
                      type="number"
                      className="form-control"
                      min={1}
                      max={returnableQty(returnItem)}
                      value={returnQty}
                      onChange={(e) => {
                        const max = returnableQty(returnItem);
                        const val = Number(e.target.value);
                        setReturnQty(Math.min(Math.max(1, val), max));
                      }}
                    />
                    <div className="form-text">
                      Up to {returnableQty(returnItem)} returnable.
                    </div>
                  </div>
                  <div className="mb-2">
                    <label className="form-label">Reason</label>
                    <textarea
                      className="form-control"
                      rows={3}
                      value={returnReason}
                      onChange={(e) => setReturnReason(e.target.value)}
                      placeholder="Tell us what's wrong…"
                    />
                  </div>
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setReturnItem(null)}
                    disabled={returnSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={submitReturn}
                    disabled={returnSubmitting}
                  >
                    {returnSubmitting ? 'Submitting…' : 'Request return'}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop fade show"></div>
        </>
      )}

      {/* Dispute modal */}
      {disputeEscrow && (
        <>
          <div className="modal d-block" tabIndex={-1} role="dialog">
            <div className="modal-dialog modal-dialog-centered" role="document">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">Report a problem</h5>
                  <button
                    type="button"
                    className="btn-close"
                    aria-label="Close"
                    onClick={() => setDisputeEscrow(null)}
                  ></button>
                </div>
                <div className="modal-body">
                  <p className="text-muted small">
                    Opening a dispute pauses the payment to{' '}
                    {disputeEscrow.sellerName ?? 'the seller'} while you work it out. You can add
                    photos or videos as evidence in the next step.
                  </p>
                  {disputeError && <div className="alert alert-danger">{disputeError}</div>}
                  <div className="mb-2">
                    <label className="form-label">What went wrong?</label>
                    <textarea
                      className="form-control"
                      rows={4}
                      value={disputeReason}
                      onChange={(e) => setDisputeReason(e.target.value)}
                      placeholder="Describe the problem…"
                    />
                  </div>
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setDisputeEscrow(null)}
                    disabled={disputeSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={submitDispute}
                    disabled={disputeSubmitting}
                  >
                    {disputeSubmitting ? 'Opening…' : 'Open dispute'}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop fade show"></div>
        </>
      )}
    </div>
  );
};
