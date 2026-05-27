import { useState, useEffect } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { selectCartItems } from '../../store/slices/cartSlice';
import { clearCart } from '../../store/slices/cartSlice';
import { useCurrency } from '../../storefront/CurrencyContext';
import { orderService } from '../../services/orderService';
import { checkoutService } from '../../services/checkoutService';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import { paymentGatewayService, type PaymentGatewayInfo } from '../../services/paymentGatewayService';
import type { Address, Product } from '../../types';

interface BuyNowState {
  product: Product;
  quantity: number;
}

const EMPTY_ADDRESS: Address = {
  firstName: '', lastName: '', addressLine1: '', addressLine2: '',
  city: '', state: '', postalCode: '', country: '', phone: '',
};

function AddressForm({
  prefix,
  value,
  onChange,
}: {
  prefix: string;
  value: Address;
  onChange: (a: Address) => void;
}) {
  const field = (key: keyof Address, label: string, required = true, type = 'text') => (
    <div className="col-12 col-sm-6">
      <label className="form-label small fw-semibold">{label}{required && <span className="text-danger ms-1">*</span>}</label>
      <input
        id={`${prefix}-${key}`}
        type={type}
        className="form-control"
        value={value[key] ?? ''}
        required={required}
        onChange={(e) => onChange({ ...value, [key]: e.target.value })}
      />
    </div>
  );

  return (
    <div className="row g-3">
      {field('firstName', 'First name')}
      {field('lastName', 'Last name')}
      {field('phone', 'Phone', true, 'tel')}
      <div className="col-12">
        <label className="form-label small fw-semibold">Address line 1<span className="text-danger ms-1">*</span></label>
        <input type="text" className="form-control" required
          value={value.addressLine1}
          onChange={(e) => onChange({ ...value, addressLine1: e.target.value })} />
      </div>
      <div className="col-12">
        <label className="form-label small fw-semibold">Address line 2 <span className="text-muted fw-normal">(optional)</span></label>
        <input type="text" className="form-control"
          value={value.addressLine2 ?? ''}
          onChange={(e) => onChange({ ...value, addressLine2: e.target.value })} />
      </div>
      {field('city', 'City')}
      {field('state', 'State / Province')}
      {field('postalCode', 'Postal code')}
      {field('country', 'Country')}
    </div>
  );
}

export function CheckoutPage() {
  const dispatch = useAppDispatch();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { format, region } = useCurrency();

  const { isAuthenticated } = useAppSelector((s) => s.auth);
  const cartItems = useAppSelector(selectCartItems);
  const buyNow = (location.state as { buyNow?: BuyNowState } | null)?.buyNow ?? null;
  const couponCode = searchParams.get('coupon') ?? null;

  const items = buyNow
    ? [{ id: 'buynow', product: buyNow.product, quantity: buyNow.quantity }]
    : cartItems;

  const [shipping, setShipping] = useState<Address>({ ...EMPTY_ADDRESS });
  const [sameAsShipping, setSameAsShipping] = useState(true);
  const [billing, setBilling] = useState<Address>({ ...EMPTY_ADDRESS });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [gateways, setGateways] = useState<PaymentGatewayInfo[]>([]);
  const [gatewaysLoading, setGatewaysLoading] = useState(true);
  const [selectedGateway, setSelectedGateway] = useState<string>('');

  const countryCode = region?.countryCode ?? '';

  useEffect(() => {
    setGatewaysLoading(true);
    paymentGatewayService.getForCountry(countryCode).then((list) => {
      setGateways(list);
      setGatewaysLoading(false);
      setSelectedGateway((prev) => {
        if (prev && list.some((g) => g.id === prev && g.configured !== false)) return prev;
        const first = list.find((g) => g.configured !== false) ?? list[0];
        return first?.id ?? '';
      });
    }).catch(() => setGatewaysLoading(false));
  }, [countryCode]);

  if (!isAuthenticated) {
    return (
      <div className="container py-5">
        <div className="text-center">
          <i className="bi bi-lock-fill text-secondary d-block mb-3" style={{ fontSize: '3rem' }}></i>
          <h4 className="fw-bold mb-2">Sign in to checkout</h4>
          <p className="text-muted mb-4">You need to be logged in to place an order.</p>
          <Link
            to={`/login?redirect=${encodeURIComponent('/checkout')}`}
            className="btn btn-primary px-4"
          >
            Sign in
          </Link>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="container py-5 text-center">
        <i className="bi bi-cart-x text-secondary d-block mb-3" style={{ fontSize: '3rem' }}></i>
        <h4 className="fw-bold mb-2">Nothing to checkout</h4>
        <Link to="/products" className="btn btn-primary px-4 mt-2">Browse products</Link>
      </div>
    );
  }

  const effectivePrice = (p: Product) =>
    typeof p.discountedPrice === 'number' ? p.discountedPrice : p.price;

  const subtotal = items.reduce((s, it) => s + effectivePrice(it.product) * it.quantity, 0);
  const shippingCost = subtotal >= 50 ? 0 : 5;
  const total = subtotal + shippingCost;

  const selectedGw = gateways.find((g) => g.id === selectedGateway);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selectedGateway || selectedGw?.configured === false) {
      setError('Please select a payment method before placing your order.');
      return;
    }

    setSubmitting(true);
    try {
      const billingAddress = sameAsShipping ? shipping : billing;
      const order = await orderService.createOrder({
        items: items.map((it) => ({ productId: it.product.id, quantity: it.quantity })),
        shippingAddress: shipping,
        billingAddress,
        paymentMethod: 'card',
      });

      if (!buyNow) dispatch(clearCart());

      const checkoutUrl = await checkoutService.createSession(order.id, selectedGateway);
      window.location.href = checkoutUrl;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="container py-4">
      <nav aria-label="breadcrumb" className="mb-4">
        <ol className="breadcrumb small">
          <li className="breadcrumb-item"><Link to="/">Home</Link></li>
          <li className="breadcrumb-item"><Link to="/cart">Cart</Link></li>
          <li className="breadcrumb-item active">Checkout</li>
        </ol>
      </nav>

      {buyNow && (
        <div className="alert alert-info d-flex align-items-center gap-2 py-2 mb-4 rounded-3">
          <i className="bi bi-lightning-fill"></i>
          <span className="small">Quick checkout — only the selected item will be ordered. Your cart is untouched.</span>
          <Link to="/cart" className="ms-auto small text-decoration-none">Back to cart</Link>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="row g-4">

          {/* ── Left column: address ──────────────────────────────────────── */}
          <div className="col-12 col-lg-7">

            <div className="card border-0 shadow-sm rounded-3 mb-4">
              <div className="card-body p-4">
                <h5 className="fw-bold mb-4">
                  <i className="bi bi-geo-alt-fill text-primary me-2"></i>Shipping address
                </h5>
                <AddressForm prefix="ship" value={shipping} onChange={setShipping} />
              </div>
            </div>

            <div className="card border-0 shadow-sm rounded-3">
              <div className="card-body p-4">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <h5 className="fw-bold mb-0">
                    <i className="bi bi-credit-card-fill text-primary me-2"></i>Billing address
                  </h5>
                  <div className="form-check mb-0">
                    <input className="form-check-input" type="checkbox" id="same-as-shipping"
                      checked={sameAsShipping}
                      onChange={(e) => setSameAsShipping(e.target.checked)} />
                    <label className="form-check-label small" htmlFor="same-as-shipping">
                      Same as shipping
                    </label>
                  </div>
                </div>
                {!sameAsShipping && (
                  <AddressForm prefix="bill" value={billing} onChange={setBilling} />
                )}
                {sameAsShipping && (
                  <p className="text-muted small mb-0">Using the same address as shipping.</p>
                )}
              </div>
            </div>
          </div>

          {/* ── Right column: order summary + payment ─────────────────────── */}
          <div className="col-12 col-lg-5">
            <div className="card border-0 shadow-sm rounded-3 sticky-top" style={{ top: '1rem' }}>
              <div className="card-body p-4">
                <h5 className="fw-bold mb-4">Order summary</h5>

                {/* Items */}
                <div className="d-flex flex-column gap-3 mb-3">
                  {items.map((it) => {
                    const price = effectivePrice(it.product);
                    const thumb = it.product.media?.find((m) => m.mediaType === 'IMAGE');
                    return (
                      <div key={it.id} className="d-flex gap-3 align-items-center">
                        <div className="flex-shrink-0 rounded-2 overflow-hidden bg-light"
                          style={{ width: '52px', height: '52px' }}>
                          <img
                            src={thumb ? mediaUrl(thumb.url) : (it.product.imageUrl || '/placeholder.png')}
                            alt={it.product.name}
                            className="w-100 h-100 object-fit-cover"
                          />
                        </div>
                        <div className="flex-grow-1 min-w-0">
                          <div className="fw-semibold small text-truncate">{it.product.name}</div>
                          <div className="text-muted" style={{ fontSize: '0.8rem' }}>
                            {format(price)} × {it.quantity}
                          </div>
                        </div>
                        <div className="fw-bold small">{format(price * it.quantity)}</div>
                      </div>
                    );
                  })}
                </div>

                <hr />

                <div className="d-flex justify-content-between small mb-2">
                  <span className="text-muted">Subtotal</span>
                  <span>{format(subtotal)}</span>
                </div>
                <div className="d-flex justify-content-between small mb-2">
                  <span className="text-muted">Shipping</span>
                  <span>{shippingCost === 0 ? <span className="text-success">Free</span> : format(shippingCost)}</span>
                </div>
                {couponCode && (
                  <div className="d-flex justify-content-between small mb-2 text-success">
                    <span><i className="bi bi-tag-fill me-1"></i>Coupon: {couponCode}</span>
                  </div>
                )}
                <hr />
                <div className="d-flex justify-content-between fw-bold mb-4">
                  <span>Total</span>
                  <span className="text-danger" style={{ fontSize: '1.1rem' }}>{format(total)}</span>
                </div>

                {/* ── Payment method (inline, always visible) ── */}
                <h6 className="fw-bold mb-2">
                  <i className="bi bi-lock-fill text-primary me-2"></i>Payment method
                </h6>

                {gatewaysLoading ? (
                  <div className="d-flex align-items-center gap-2 p-3 bg-light rounded-3 mb-3">
                    <span className="spinner-border spinner-border-sm text-secondary flex-shrink-0"></span>
                    <span className="small text-muted">Loading payment options…</span>
                  </div>
                ) : gateways.length === 0 ? (
                  <div className="d-flex align-items-center gap-2 p-3 bg-light rounded-3 mb-3">
                    <i className="bi bi-exclamation-circle text-warning flex-shrink-0"></i>
                    <span className="small text-muted">No payment options available for your region.</span>
                  </div>
                ) : (
                  <div className="d-flex flex-column gap-2 mb-3">
                    {gateways.map((gw) => {
                      const active = selectedGateway === gw.id;
                      return (
                        <label key={gw.id}
                          className={`d-flex align-items-center gap-3 p-3 rounded-3 border ${
                            gw.configured === false
                              ? 'bg-light opacity-75'
                              : active
                              ? 'border-primary bg-primary bg-opacity-10'
                              : 'bg-white'
                          }`}
                          style={{ cursor: gw.configured !== false ? 'pointer' : 'not-allowed', transition: 'border-color 0.15s' }}>
                          <input
                            type="radio"
                            className="form-check-input flex-shrink-0 mt-0"
                            name="gateway"
                            value={gw.id}
                            checked={active}
                            disabled={gw.configured === false}
                            onChange={() => gw.configured !== false && setSelectedGateway(gw.id)}
                          />
                          <i className={`bi ${gw.iconClass} fs-5 flex-shrink-0 ${active ? 'text-primary' : 'text-secondary'}`}></i>
                          <div className="flex-grow-1 min-w-0">
                            <div className={`fw-semibold small d-flex align-items-center gap-2 flex-wrap ${active ? 'text-primary' : ''}`}>
                              {gw.displayName}
                              {gw.configured === false && (
                                <span className="badge bg-secondary-subtle text-secondary fw-normal" style={{ fontSize: '0.65rem' }}>
                                  <i className="bi bi-lock-fill me-1"></i>Requires setup
                                </span>
                              )}
                            </div>
                            <div className="text-muted text-truncate" style={{ fontSize: '0.75rem' }}>{gw.description}</div>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}

                {error && (
                  <div className="alert alert-danger py-2 small rounded-3 mb-3">
                    <i className="bi bi-exclamation-triangle-fill me-2"></i>{error}
                  </div>
                )}

                <button
                  type="submit"
                  className="btn btn-danger w-100 fw-bold py-2"
                  disabled={submitting || gatewaysLoading || !selectedGateway || selectedGw?.configured === false}
                >
                  {submitting
                    ? <><span className="spinner-border spinner-border-sm me-2"></span>Placing order…</>
                    : selectedGw
                    ? <><i className={`bi ${selectedGw.iconClass} me-2`}></i>Pay with {selectedGw.displayName}</>
                    : <><i className="bi bi-lock-fill me-2"></i>Place order & pay</>}
                </button>

                <p className="text-muted text-center mt-2 mb-0" style={{ fontSize: '0.72rem' }}>
                  <i className="bi bi-shield-check me-1"></i>
                  Secure checkout — your payment is handled by {selectedGw?.displayName ?? 'a trusted provider'}.
                </p>
              </div>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}
