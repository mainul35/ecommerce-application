import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  adminOrderService,
  type OrderCreateRequest,
} from '../../services/admin/adminOrderService';
import { adminProductService } from '../../services/admin/adminProductService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Address, Product, User } from '../../types';

interface CartLine {
  product: Product;
  quantity: number;
}

const emptyAddress: Address = {
  firstName: '',
  lastName: '',
  addressLine1: '',
  city: '',
  state: '',
  postalCode: '',
  country: '',
  phone: '',
};

export function AdminNewOrderPage() {
  const navigate = useNavigate();

  const [customerQuery, setCustomerQuery] = useState('');
  const [customerResults, setCustomerResults] = useState<User[]>([]);
  const [customer, setCustomer] = useState<User | null>(null);

  const [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);

  const [shippingAddress, setShippingAddress] = useState<Address>(emptyAddress);
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true);
  const [billingAddress, setBillingAddress] = useState<Address>(emptyAddress);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminProductService
      .list(0, 50, 'newest')
      .then((paged) => setProducts(paged.content))
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    if (customerQuery.length < 2) {
      setCustomerResults([]);
      return;
    }
    const t = setTimeout(() => {
      adminOrderService
        .searchCustomers(customerQuery)
        .then(setCustomerResults)
        .catch((e: Error) => setError(e.message));
    }, 250);
    return () => clearTimeout(t);
  }, [customerQuery]);

  const addToCart = (product: Product) => {
    setCart((c) => {
      const existing = c.find((l) => l.product.id === product.id);
      if (existing) {
        return c.map((l) =>
          l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l
        );
      }
      return [...c, { product, quantity: 1 }];
    });
  };

  const updateQty = (productId: string, qty: number) => {
    setCart((c) =>
      c.map((l) =>
        l.product.id === productId ? { ...l, quantity: Math.max(1, qty) } : l
      )
    );
  };

  const removeFromCart = (productId: string) => {
    setCart((c) => c.filter((l) => l.product.id !== productId));
  };

  const total = cart.reduce(
    (sum, l) => sum + Number(l.product.price) * l.quantity,
    0
  );

  const canSubmit =
    customer !== null &&
    cart.length > 0 &&
    shippingAddress.firstName.trim() !== '' &&
    shippingAddress.addressLine1.trim() !== '' &&
    shippingAddress.city.trim() !== '';

  const submit = async () => {
    if (!customer || cart.length === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      const payload: OrderCreateRequest = {
        items: cart.map((l) => ({
          productId: l.product.id,
          quantity: l.quantity,
        })),
        shippingAddress,
        billingAddress: billingSameAsShipping ? shippingAddress : billingAddress,
        paymentMethod: 'admin-offline',
      };
      const order = await adminOrderService.createForCustomer(customer.id, payload);
      navigate(`/admin/orders/${order.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Order creation failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="New Order"
        crumbs={[
          { label: 'Home', to: '/admin' },
          { label: 'Orders', to: '/admin/orders' },
          { label: 'New' },
        ]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-3">
        <div className="col-12 col-lg-7">
          <div className="card mb-3">
            <div className="card-header">
              <h3 className="card-title">1. Customer</h3>
            </div>
            <div className="card-body">
              {customer ? (
                <div className="d-flex justify-content-between align-items-center">
                  <div>
                    <strong>
                      {customer.firstName} {customer.lastName}
                    </strong>
                    <div className="text-muted small">{customer.email}</div>
                  </div>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={() => {
                      setCustomer(null);
                      setCustomerQuery('');
                      setCustomerResults([]);
                    }}
                  >
                    Change
                  </button>
                </div>
              ) : (
                <>
                  <input
                    type="search"
                    className="form-control"
                    placeholder="Search by email, first name, or last name…"
                    value={customerQuery}
                    onChange={(e) => setCustomerQuery(e.target.value)}
                    autoFocus
                  />
                  {customerResults.length > 0 && (
                    <ul className="list-group mt-2">
                      {customerResults.map((u) => (
                        <li
                          key={u.id}
                          className="list-group-item list-group-item-action cursor-pointer"
                          onClick={() => {
                            setCustomer(u);
                            setShippingAddress((a) => ({
                              ...a,
                              firstName: u.firstName,
                              lastName: u.lastName,
                            }));
                          }}
                        >
                          <strong>{u.firstName} {u.lastName}</strong>{' '}
                          <span className="text-muted small">— {u.email}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                  {customerQuery.length >= 2 && customerResults.length === 0 && (
                    <p className="text-muted small mt-2 mb-0">No matches.</p>
                  )}
                </>
              )}
            </div>
          </div>

          <div className="card mb-3">
            <div className="card-header">
              <h3 className="card-title">2. Products</h3>
            </div>
            <div className="card-body">
              <div className="list-group" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {products.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                    onClick={() => addToCart(p)}
                  >
                    <div>
                      <div className="fw-semibold">{p.name}</div>
                      <small className="text-muted">
                        SKU {p.sku} · stock {p.stock}
                      </small>
                    </div>
                    <span className="badge bg-primary">${Number(p.price).toFixed(2)}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <h3 className="card-title">3. Shipping address</h3>
            </div>
            <div className="card-body">
              <AddressForm value={shippingAddress} onChange={setShippingAddress} />
              <hr />
              <div className="form-check mb-3">
                <input
                  type="checkbox"
                  className="form-check-input"
                  id="billingSame"
                  checked={billingSameAsShipping}
                  onChange={(e) => setBillingSameAsShipping(e.target.checked)}
                />
                <label className="form-check-label" htmlFor="billingSame">
                  Billing address same as shipping
                </label>
              </div>
              {!billingSameAsShipping && (
                <>
                  <h5 className="mb-3">Billing address</h5>
                  <AddressForm value={billingAddress} onChange={setBillingAddress} />
                </>
              )}
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-5">
          <div className="card sticky-top" style={{ top: '1rem' }}>
            <div className="card-header">
              <h3 className="card-title">Cart</h3>
            </div>
            <div className="card-body">
              {cart.length === 0 ? (
                <p className="text-muted mb-0">No items yet.</p>
              ) : (
                <table className="table table-sm align-middle mb-3">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th style={{ width: '90px' }}>Qty</th>
                      <th className="text-end">Subtotal</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {cart.map((l) => (
                      <tr key={l.product.id}>
                        <td>
                          <div className="small">{l.product.name}</div>
                          <small className="text-muted">${Number(l.product.price).toFixed(2)}</small>
                        </td>
                        <td>
                          <input
                            type="number"
                            min="1"
                            className="form-control form-control-sm"
                            value={l.quantity}
                            onChange={(e) => updateQty(l.product.id, Number(e.target.value))}
                          />
                        </td>
                        <td className="text-end">
                          ${(Number(l.product.price) * l.quantity).toFixed(2)}
                        </td>
                        <td className="text-end">
                          <button
                            type="button"
                            className="btn btn-sm btn-link text-danger"
                            onClick={() => removeFromCart(l.product.id)}
                            aria-label="Remove"
                          >
                            &times;
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colSpan={2} className="fw-semibold">
                        Total
                      </td>
                      <td className="text-end fw-semibold">${total.toFixed(2)}</td>
                      <td></td>
                    </tr>
                  </tfoot>
                </table>
              )}
              <button
                type="button"
                className="btn btn-primary w-100"
                disabled={!canSubmit || submitting}
                onClick={submit}
              >
                {submitting ? 'Creating…' : 'Create order'}
              </button>
              <p className="text-muted small mt-2 mb-0">
                Order will be created in PENDING status. Use the order detail page to mark it paid
                (offline) or send a Stripe Checkout link to the customer.
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

interface AddressFormProps {
  value: Address;
  onChange: (next: Address) => void;
}

function AddressForm({ value, onChange }: Readonly<AddressFormProps>) {
  const set = (patch: Partial<Address>) => onChange({ ...value, ...patch });
  return (
    <div className="row g-2">
      <div className="col-12 col-md-6">
        <input
          type="text"
          className="form-control"
          placeholder="First name"
          value={value.firstName}
          onChange={(e) => set({ firstName: e.target.value })}
        />
      </div>
      <div className="col-12 col-md-6">
        <input
          type="text"
          className="form-control"
          placeholder="Last name"
          value={value.lastName}
          onChange={(e) => set({ lastName: e.target.value })}
        />
      </div>
      <div className="col-12">
        <input
          type="text"
          className="form-control"
          placeholder="Address line 1"
          value={value.addressLine1}
          onChange={(e) => set({ addressLine1: e.target.value })}
        />
      </div>
      <div className="col-12">
        <input
          type="text"
          className="form-control"
          placeholder="Address line 2 (optional)"
          value={value.addressLine2 ?? ''}
          onChange={(e) => set({ addressLine2: e.target.value })}
        />
      </div>
      <div className="col-12 col-md-6">
        <input
          type="text"
          className="form-control"
          placeholder="City"
          value={value.city}
          onChange={(e) => set({ city: e.target.value })}
        />
      </div>
      <div className="col-6 col-md-3">
        <input
          type="text"
          className="form-control"
          placeholder="State"
          value={value.state}
          onChange={(e) => set({ state: e.target.value })}
        />
      </div>
      <div className="col-6 col-md-3">
        <input
          type="text"
          className="form-control"
          placeholder="Postal code"
          value={value.postalCode}
          onChange={(e) => set({ postalCode: e.target.value })}
        />
      </div>
      <div className="col-12 col-md-6">
        <input
          type="text"
          className="form-control"
          placeholder="Country"
          value={value.country}
          onChange={(e) => set({ country: e.target.value })}
        />
      </div>
      <div className="col-12 col-md-6">
        <input
          type="tel"
          className="form-control"
          placeholder="Phone"
          value={value.phone}
          onChange={(e) => set({ phone: e.target.value })}
        />
      </div>
    </div>
  );
}
