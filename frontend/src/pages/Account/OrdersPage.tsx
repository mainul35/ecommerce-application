import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { orderService } from '../../services/orderService';
import { Loading, EmptyState, Pagination } from '../../components/common';
import { formatMoney } from './format';
import type { Order, OrderStatus, PaymentStatus } from '../../types';

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

const paymentStatusBadge = (status: PaymentStatus): string => {
  switch (status) {
    case 'PENDING':
      return 'bg-warning text-dark';
    case 'COMPLETED':
      return 'bg-success';
    case 'FAILED':
      return 'bg-danger';
    case 'REFUNDED':
      return 'bg-dark';
    case 'PARTIALLY_REFUNDED':
      return 'bg-secondary';
    default:
      return 'bg-light text-dark';
  }
};

const PAGE_SIZE = 10;

export const OrdersPage = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    orderService
      .getOrders(page, PAGE_SIZE)
      .then((paged) => {
        setOrders(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setIsLoading(false));
  }, [page]);

  if (isLoading) {
    return (
      <div className="container py-5">
        <Loading message="Loading your orders…" />
      </div>
    );
  }

  return (
    <div className="container py-5">
      <h1 className="mb-4">My Orders</h1>

      {error && <div className="alert alert-danger">{error}</div>}

      {!error && orders.length === 0 ? (
        <EmptyState
          icon="bi-bag"
          title="No orders yet"
          description="When you place an order it will show up here."
          actionLabel="Start Shopping"
          actionLink="/products"
        />
      ) : (
        <>
          <div className="card">
            <div className="table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Placed</th>
                    <th>Status</th>
                    <th>Payment</th>
                    <th className="text-center">Items</th>
                    <th className="text-end">Total</th>
                    <th className="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order) => {
                    const itemCount = order.items.reduce((sum, it) => sum + it.quantity, 0);
                    return (
                      <tr key={order.id}>
                        <td>
                          <code>{order.id.substring(0, 8)}</code>
                        </td>
                        <td className="text-muted small">
                          {new Date(order.createdAt).toLocaleDateString()}
                        </td>
                        <td>
                          <span className={`badge ${orderStatusBadge(order.status)}`}>
                            {order.status}
                          </span>
                        </td>
                        <td>
                          {order.paymentStatus ? (
                            <span className={`badge ${paymentStatusBadge(order.paymentStatus)}`}>
                              {order.paymentStatus}
                            </span>
                          ) : (
                            <span className="text-muted small">—</span>
                          )}
                        </td>
                        <td className="text-center">{itemCount}</td>
                        <td className="text-end fw-semibold">
                          {formatMoney(order.totalAmount)}
                        </td>
                        <td className="text-end">
                          <Link
                            to={`/account/orders/${order.id}`}
                            className="btn btn-sm btn-outline-primary"
                          >
                            View
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="mt-4">
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              onPageChange={setPage}
            />
          </div>
        </>
      )}
    </div>
  );
};
