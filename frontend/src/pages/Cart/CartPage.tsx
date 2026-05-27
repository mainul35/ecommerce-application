import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import {
  removeFromCart,
  incrementQuantity,
  decrementQuantity,
  clearCart,
  selectCartItems,
  selectCartTotal,
} from '../../store/slices/cartSlice';
import { EmptyState } from '../../components/common';
import { couponService } from '../../services/couponService';
import { useCurrency } from '../../storefront/CurrencyContext';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import type { CouponValidationResponse, Product } from '../../types';

const effectiveUnitPrice = (price: number, discountedPrice?: number): number =>
  typeof discountedPrice === 'number' ? discountedPrice : price;

interface BuyNowState {
  product: Product;
  quantity: number;
}

export function CartPage() {
  const dispatch = useAppDispatch();
  const { format } = useCurrency();
  const location = useLocation();
  const buyNow = (location.state as { buyNow?: BuyNowState } | null)?.buyNow ?? null;
  const cartItems = useAppSelector(selectCartItems);
  // Note: store-level cartTotal still uses sticker price; we recompute with item discounts below.
  const stickerTotal = useAppSelector(selectCartTotal);

  const [couponCode, setCouponCode] = useState('');
  const [coupon, setCoupon] = useState<CouponValidationResponse | null>(null);
  const [applying, setApplying] = useState(false);
  const [couponError, setCouponError] = useState<string | null>(null);

  const handleRemove = (itemId: string) => dispatch(removeFromCart(itemId));
  const handleIncrement = (itemId: string) => dispatch(incrementQuantity(itemId));
  const handleDecrement = (itemId: string) => dispatch(decrementQuantity(itemId));
  const handleClearCart = () => dispatch(clearCart());

  // In buy-now mode we compute totals from the single temporary item, not the cart.
  const displayItems = buyNow
    ? [{ id: 'buynow', product: buyNow.product, quantity: buyNow.quantity }]
    : cartItems;

  const itemDiscountedSubtotal = displayItems.reduce(
    (sum, it) => sum + effectiveUnitPrice(it.product.price, it.product.discountedPrice) * it.quantity,
    0
  );
  const buyNowStickerTotal = buyNow
    ? buyNow.product.price * buyNow.quantity
    : stickerTotal;
  const itemDiscountSavings = buyNowStickerTotal - itemDiscountedSubtotal;

  const couponDiscount = coupon?.valid ? coupon.discountAmount : 0;
  const subtotalAfterEverything = Math.max(0, itemDiscountedSubtotal - couponDiscount);
  const shipping = subtotalAfterEverything >= 50 ? 0 : 5;
  const grandTotal = subtotalAfterEverything + shipping;

  const applyCoupon = async () => {
    setCouponError(null);
    if (!couponCode.trim()) {
      setCouponError('Enter a code first.');
      return;
    }
    setApplying(true);
    try {
      const result = await couponService.validate(
        couponCode.trim(),
        displayItems.map((it) => ({ productId: it.product.id, quantity: it.quantity }))
      );
      if (!result.valid) {
        setCouponError(result.message ?? 'Coupon could not be applied.');
        setCoupon(null);
      } else {
        setCoupon(result);
      }
    } catch (e) {
      setCouponError(e instanceof Error ? e.message : 'Coupon validation failed');
      setCoupon(null);
    } finally {
      setApplying(false);
    }
  };

  const removeCoupon = () => {
    setCoupon(null);
    setCouponCode('');
    setCouponError(null);
  };

  if (!buyNow && cartItems.length === 0) {
    return (
      <div className="container py-5">
        <EmptyState
          icon="bi-cart-x"
          title="Your cart is empty"
          description="Looks like you haven't added any products to your cart yet"
          actionLabel="Start Shopping"
          actionLink="/products"
        />
      </div>
    );
  }

  return (
    <div className="container py-5">
      <div className="d-flex justify-content-between align-items-center mb-4">
        {buyNow ? (
          <div>
            <h1 className="mb-0">Quick Checkout</h1>
            <p className="text-muted small mb-0 mt-1">
              Checking out this item only — your cart is untouched.{' '}
              <Link to="/cart" replace className="text-decoration-none">Back to cart</Link>
            </p>
          </div>
        ) : (
          <h1>Shopping Cart</h1>
        )}
        {!buyNow && (
          <button className="btn btn-outline-danger" onClick={handleClearCart}>
            Clear Cart
          </button>
        )}
      </div>

      <div className="row g-4">
        <div className="col-12 col-lg-8">
          <div className="card">
            <div className="card-body p-0">
              {displayItems.map((item) => {
                const unitPrice = effectiveUnitPrice(item.product.price, item.product.discountedPrice);
                const onSale = unitPrice < item.product.price;
                const isBuyNowItem = buyNow !== null;
                return (
                  <div key={item.id} className="cart-item d-flex gap-3">
                    <img
                      src={(() => {
                        const first = item.product.media?.find((m) => m.mediaType === 'IMAGE');
                        return first ? mediaUrl(first.url) : (item.product.imageUrl || '/placeholder.png');
                      })()}
                      alt={item.product.name}
                      className="cart-item-image"
                    />
                    <div className="flex-grow-1">
                      <div className="d-flex justify-content-between">
                        <div>
                          <Link
                            to={`/products/${item.product.id}`}
                            className="text-decoration-none"
                          >
                            <h6 className="mb-1">{item.product.name}</h6>
                          </Link>
                          <p className="text-muted small mb-2">
                            {item.product.category.name}
                          </p>
                          {onSale && (
                            <p className="small mb-2">
                              <span className="badge bg-danger-subtle text-danger">
                                {format(unitPrice)} each
                              </span>{' '}
                              <span className="text-muted text-decoration-line-through small">
                                {format(item.product.price)}
                              </span>
                            </p>
                          )}
                        </div>
                        {!isBuyNowItem && (
                          <button
                            className="btn btn-link text-danger p-0"
                            onClick={() => handleRemove(item.id)}
                          >
                            <i className="bi bi-trash"></i>
                          </button>
                        )}
                      </div>
                      <div className="d-flex justify-content-between align-items-center">
                        <div className="quantity-control">
                          {!isBuyNowItem && (
                            <button
                              className="btn btn-outline-secondary btn-sm"
                              onClick={() => handleDecrement(item.id)}
                            >
                              -
                            </button>
                          )}
                          <span className={isBuyNowItem ? '' : 'mx-2'}>{item.quantity}</span>
                          {!isBuyNowItem && (
                            <button
                              className="btn btn-outline-secondary btn-sm"
                              onClick={() => handleIncrement(item.id)}
                              disabled={item.quantity >= item.product.stock}
                            >
                              +
                            </button>
                          )}
                        </div>
                        <span className="fw-bold">
                          {format(unitPrice * item.quantity)}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-4">
          <div className="card">
            <div className="card-body">
              <h5 className="card-title mb-4">Order Summary</h5>

              <div className="d-flex justify-content-between mb-2">
                <span>Subtotal</span>
                <span>{format(stickerTotal)}</span>
              </div>
              {itemDiscountSavings > 0 && (
                <div className="d-flex justify-content-between mb-2 text-success">
                  <span>Item discounts</span>
                  <span>-{format(itemDiscountSavings)}</span>
                </div>
              )}

              <hr />

              <label className="form-label small text-muted mb-1">Promo code</label>
              {coupon?.valid ? (
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <div>
                    <span className="badge bg-success">{coupon.code}</span>
                    <span className="text-success ms-2">-{format(coupon.discountAmount)}</span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-link text-danger btn-sm p-0"
                    onClick={removeCoupon}
                  >
                    Remove
                  </button>
                </div>
              ) : (
                <div className="input-group input-group-sm mb-2">
                  <input
                    type="text"
                    className="form-control text-uppercase"
                    placeholder="e.g. WELCOME10"
                    value={couponCode}
                    onChange={(e) => setCouponCode(e.target.value)}
                    disabled={applying}
                  />
                  <button
                    type="button"
                    className="btn btn-outline-primary"
                    onClick={applyCoupon}
                    disabled={applying}
                  >
                    {applying ? '…' : 'Apply'}
                  </button>
                </div>
              )}
              {couponError && <p className="small text-danger mb-2">{couponError}</p>}

              <div className="d-flex justify-content-between mb-2">
                <span>Shipping</span>
                <span>{shipping === 0 ? 'Free' : format(shipping)}</span>
              </div>
              <hr />
              <div className="d-flex justify-content-between mb-4">
                <strong>Total</strong>
                <strong>{format(grandTotal)}</strong>
              </div>
              <Link
                to={coupon?.valid ? `/checkout?coupon=${encodeURIComponent(coupon.code ?? '')}` : '/checkout'}
                state={buyNow ? { buyNow } : undefined}
                className="btn btn-primary w-100 mb-2"
              >
                Proceed to Checkout
              </Link>
              <Link to="/products" className="btn btn-outline-secondary w-100">
                Continue Shopping
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
