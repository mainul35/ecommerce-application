import { useEffect, useRef, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchProductById } from '../../store/slices/productsSlice';
import { addToCart, selectCartItems } from '../../store/slices/cartSlice';
import { addNotification } from '../../store/slices/uiSlice';
import { Loading, EmptyState } from '../../components/common';
import { useCurrency } from '../../storefront/CurrencyContext';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import { productService } from '../../services/productService';
import { productReviewService } from '../../services/productReviewService';
import { ProductCard } from '../../components/common/ProductCard';
import type { Product, ProductReview } from '../../types';

// ── Helpers ───────────────────────────────────────────────────────────────────

function Stars({ value, max = 5, size = 16 }: { value: number; max?: number; size?: number }) {
  return (
    <span style={{ fontSize: size }} aria-label={`${value.toFixed(1)} out of ${max}`}>
      {Array.from({ length: max }, (_, i) => (
        <i
          key={i}
          className={`bi ${i < Math.floor(value) ? 'bi-star-fill' : i < value ? 'bi-star-half' : 'bi-star'} text-warning`}
        />
      ))}
    </span>
  );
}

function avatarInitials(name: string) {
  const parts = name.trim().split(' ');
  return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
}

// ── Reviews tab ───────────────────────────────────────────────────────────────

function ReviewsSection({ productId }: { productId: string }) {
  const { isAuthenticated } = useAppSelector((s) => s.auth);
  const [reviews, setReviews] = useState<ProductReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [form, setForm] = useState({ rating: 5, title: '', body: '' });

  useEffect(() => {
    setLoading(true);
    productReviewService.list(productId).then(setReviews).catch(() => {}).finally(() => setLoading(false));
  }, [productId]);

  const avg = reviews.length ? reviews.reduce((s, r) => s + r.rating, 0) / reviews.length : 0;
  const counts = [5, 4, 3, 2, 1].map((s) => ({ s, n: reviews.filter((r) => r.rating === s).length }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await productReviewService.create(productId, form);
      setReviews((prev) => [created, ...prev]);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      {/* Summary */}
      {reviews.length > 0 && (
        <div className="row g-4 align-items-center mb-4 pb-4 border-bottom">
          <div className="col-12 col-sm-auto text-center">
            <div style={{ fontSize: '3.5rem', fontWeight: 800, lineHeight: 1, color: '#f59e0b' }}>{avg.toFixed(1)}</div>
            <Stars value={avg} size={20} />
            <div className="text-muted mt-1" style={{ fontSize: '0.8rem' }}>
              {reviews.length} review{reviews.length !== 1 ? 's' : ''}
            </div>
          </div>
          <div className="col">
            {counts.map(({ s, n }) => (
              <div key={s} className="d-flex align-items-center gap-2 mb-1">
                <span className="text-muted" style={{ fontSize: '0.8rem', width: '24px', textAlign: 'right' }}>{s}</span>
                <i className="bi bi-star-fill text-warning" style={{ fontSize: '0.7rem' }}></i>
                <div className="pdp-rating-bar flex-grow-1">
                  <div className="pdp-rating-fill" style={{ width: reviews.length ? `${(n / reviews.length) * 100}%` : '0%' }} />
                </div>
                <span className="text-muted" style={{ fontSize: '0.8rem', width: '20px' }}>{n}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Write a review */}
      <div className="mb-4">
        {!isAuthenticated ? (
          <div className="pdp-write-review text-center">
            <i className="bi bi-pencil-square text-primary mb-2 d-block" style={{ fontSize: '1.75rem' }}></i>
            <p className="mb-2 fw-semibold">Share your experience</p>
            <p className="text-muted small mb-3">Have you bought this product? <Link to="/login">Log in</Link> to leave a review.</p>
          </div>
        ) : done ? (
          <div className="alert alert-success d-flex align-items-center gap-2 rounded-3">
            <i className="bi bi-check-circle-fill fs-5"></i>
            <span>Review submitted — thank you!</span>
            <button type="button" className="btn btn-sm btn-link ms-auto p-0" onClick={() => setDone(false)}>
              Write another
            </button>
          </div>
        ) : (
          <div className="pdp-write-review">
            <p className="fw-semibold mb-3">
              <i className="bi bi-pencil-square me-2 text-primary"></i>Write a Review
            </p>
            {error && <div className="alert alert-danger py-2 small rounded-3">{error}</div>}
            <form onSubmit={submit}>
              <div className="mb-3">
                <label className="form-label small fw-semibold text-muted text-uppercase" style={{ letterSpacing: '0.05em' }}>
                  Your Rating
                </label>
                <div className="pdp-star-input d-flex gap-1">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button key={star} type="button" onClick={() => setForm({ ...form, rating: star })}>
                      <i className={`bi ${star <= form.rating ? 'bi-star-fill text-warning' : 'bi-star text-muted'}`} />
                    </button>
                  ))}
                </div>
              </div>
              <div className="row g-3">
                <div className="col-12">
                  <input
                    type="text" className="form-control" placeholder="Review title (optional)"
                    maxLength={200} value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                  />
                </div>
                <div className="col-12">
                  <textarea
                    className="form-control" rows={3} placeholder="Tell others what you think…"
                    maxLength={2000} value={form.body}
                    onChange={(e) => setForm({ ...form, body: e.target.value })}
                  />
                </div>
                <div className="col-12">
                  <button type="submit" className="btn btn-primary px-4" disabled={submitting}>
                    {submitting ? 'Submitting…' : 'Submit Review'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        )}
      </div>

      {/* Review list */}
      {loading ? (
        <p className="text-muted text-center py-3">Loading reviews…</p>
      ) : reviews.length === 0 ? (
        <div className="text-center py-4 text-muted">
          <i className="bi bi-chat-heart d-block mb-2" style={{ fontSize: '2rem' }}></i>
          No reviews yet — be the first!
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {reviews.map((r) => (
            <div key={r.id} className="pdp-review-card">
              <div className="d-flex gap-3 align-items-start">
                <div className="pdp-reviewer-avatar">{avatarInitials(r.reviewerName)}</div>
                <div className="flex-grow-1 min-w-0">
                  <div className="d-flex align-items-center gap-2 flex-wrap mb-1">
                    <span className="fw-semibold small">{r.reviewerName}</span>
                    <Stars value={r.rating} size={13} />
                    <span className="pdp-review-meta ms-auto">
                      {new Date(r.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}
                    </span>
                  </div>
                  {r.title && <div className="pdp-review-title mb-1">{r.title}</div>}
                  {r.body && <p className="pdp-review-body mb-0">{r.body}</p>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Similar products ──────────────────────────────────────────────────────────

function SimilarProducts({ categoryId, excludeId }: { categoryId: string; excludeId: string }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    productService
      .getProductsByCategory(categoryId, 0, 8)
      .then((paged) => setProducts(paged.content.filter((p) => p.id !== excludeId).slice(0, 6)))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [categoryId, excludeId]);

  if (loading || products.length === 0) return null;

  return (
    <section className="mt-5">
      <div className="pdp-section-header"><h5>You May Also Like</h5></div>
      <div className="row row-cols-2 row-cols-sm-3 row-cols-md-4 row-cols-lg-6 g-3">
        {products.map((p) => (
          <div key={p.id} className="col"><ProductCard product={p} /></div>
        ))}
      </div>
    </section>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { selectedProduct: product, isLoading, error } = useAppSelector((s) => s.products);
  const { format } = useCurrency();

  const cartItems = useAppSelector(selectCartItems);

  const [quantity, setQuantity] = useState(1);
  const [selectedImage, setSelectedImage] = useState(0);
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const [loved, setLoved] = useState(false);
  const [activeTab, setActiveTab] = useState<'details' | 'reviews' | 'qa'>('details');
  const [showBuyNowModal, setShowBuyNowModal] = useState(false);

  const dragStartXRef = useRef<number | null>(null);
  const hasDraggedRef = useRef(false);

  useEffect(() => {
    if (id) { dispatch(fetchProductById(id)); setSelectedImage(0); }
  }, [dispatch, id]);

  const allMedia: Array<{ type: 'IMAGE' | 'VIDEO'; src: string }> = product
    ? (product.media ?? []).length > 0
      ? (product.media ?? []).map((m) => ({ type: m.mediaType as 'IMAGE' | 'VIDEO', src: mediaUrl(m.url) }))
      : (product.images?.length > 0 ? product.images : [product.imageUrl].filter(Boolean) as string[])
          .map((url) => ({ type: 'IMAGE' as const, src: url }))
    : [];

  const mediaCount = allMedia.length;

  useEffect(() => {
    if (!isLightboxOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsLightboxOpen(false);
      if (e.key === 'ArrowRight') setSelectedImage((i) => (i + 1) % mediaCount);
      if (e.key === 'ArrowLeft') setSelectedImage((i) => (i - 1 + mediaCount) % mediaCount);
    };
    const onMove = (e: MouseEvent) => {
      if (dragStartXRef.current !== null && Math.abs(e.clientX - dragStartXRef.current) > 5)
        hasDraggedRef.current = true;
    };
    const onUp = (e: MouseEvent) => {
      if (dragStartXRef.current === null) return;
      const d = e.clientX - dragStartXRef.current;
      if (Math.abs(d) > 60) setSelectedImage((i) => (d < 0 ? (i + 1) % mediaCount : (i - 1 + mediaCount) % mediaCount));
      dragStartXRef.current = null;
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => { window.removeEventListener('keydown', onKey); window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
  }, [isLightboxOpen, mediaCount]);

  const nav = (dir: 1 | -1) => setSelectedImage((i) => (i + dir + mediaCount) % mediaCount);

  const addAndNotify = () => {
    dispatch(addToCart({ product: product!, quantity }));
    dispatch(addNotification({ type: 'success', message: `${product!.name} added to cart`, duration: 3000 }));
  };

  const buyNow = () => {
    if (cartItems.length === 0) {
      dispatch(addToCart({ product: product!, quantity }));
      navigate('/cart');
    } else {
      setShowBuyNowModal(true);
    }
  };

  const buyNowOnly = () => {
    setShowBuyNowModal(false);
    navigate('/cart', { state: { buyNow: { product: product!, quantity } } });
  };

  const buyNowWithCart = () => {
    setShowBuyNowModal(false);
    dispatch(addToCart({ product: product!, quantity }));
    navigate('/cart');
  };

  if (isLoading) return <Loading fullScreen />;
  if (error || !product) {
    return (
      <div className="container py-5">
        <EmptyState icon="bi-exclamation-circle" title="Product not found"
          description={error || 'The product you are looking for does not exist'}
          actionLabel="Back to Products" actionLink="/products" />
      </div>
    );
  }

  const hasDiscount = typeof product.discountedPrice === 'number';
  const effectivePrice = hasDiscount ? product.discountedPrice! : product.price;
  const compareAtPrice = hasDiscount ? product.price : null;
  const discountPct = hasDiscount ? Math.round(Number(product.discountPercent ?? 0)) : null;
  const cur = allMedia[selectedImage];

  const TABS = [
    { key: 'details', label: 'Product Details' },
    { key: 'reviews', label: 'Ratings & Reviews' },
    { key: 'qa', label: 'Questions & Answers' },
  ] as const;

  return (
    <>
      <div className="container py-4">
        {/* Breadcrumb */}
        <nav aria-label="breadcrumb" className="mb-4">
          <ol className="breadcrumb small">
            <li className="breadcrumb-item"><Link to="/">Home</Link></li>
            <li className="breadcrumb-item"><Link to="/products">Products</Link></li>
            <li className="breadcrumb-item">
              <Link to={`/products?category=${product.category.id}`}>{product.category.name}</Link>
            </li>
            <li className="breadcrumb-item active">{product.name}</li>
          </ol>
        </nav>

        {/* ── Two-column hero ─────────────────────────────────────────────── */}
        <div className="row g-4 mb-4">

          {/* Gallery */}
          <div className="col-12 col-lg-5">
            <div className="position-relative mb-2">
              <div className="ratio ratio-4x3 bg-light rounded-3 overflow-hidden shadow-sm">
                {mediaCount === 0 ? (
                  <img src="/placeholder.png" alt={product.name} className="object-fit-contain w-100 h-100" />
                ) : cur.type === 'VIDEO' ? (
                  <video key={cur.src} src={cur.src} controls className="object-fit-contain w-100 h-100" />
                ) : (
                  <button type="button" className="border-0 p-0 bg-transparent w-100 h-100"
                    title="Click to zoom" onClick={() => setIsLightboxOpen(true)}>
                    <img src={cur.src} alt={product.name} className="object-fit-contain w-100 h-100"
                      style={{ cursor: 'zoom-in' }} />
                  </button>
                )}
              </div>
              {mediaCount > 1 && (
                <>
                  <button type="button" aria-label="Previous"
                    className="btn btn-dark btn-sm opacity-75 position-absolute top-50 start-0 translate-middle-y ms-2"
                    onClick={() => nav(-1)}><i className="bi bi-chevron-left"></i></button>
                  <button type="button" aria-label="Next"
                    className="btn btn-dark btn-sm opacity-75 position-absolute top-50 end-0 translate-middle-y me-2"
                    onClick={() => nav(1)}><i className="bi bi-chevron-right"></i></button>
                  <span className="badge bg-dark bg-opacity-75 position-absolute bottom-0 end-0 m-2">
                    {selectedImage + 1} / {mediaCount}
                  </span>
                </>
              )}
            </div>
            {mediaCount > 1 && (
              <div className="d-flex gap-2 overflow-auto py-1">
                {allMedia.map((item, i) => (
                  <button key={i} type="button" aria-label={`Media ${i + 1}`}
                    className={`flex-shrink-0 rounded-2 overflow-hidden p-0 border-2${selectedImage === i ? ' border-primary' : ' border border-transparent'}`}
                    style={{ width: '60px', height: '60px', outline: selectedImage === i ? '2px solid #2563eb' : 'none' }}
                    onClick={() => setSelectedImage(i)}>
                    {item.type === 'VIDEO' ? (
                      <div className="w-100 h-100 d-flex align-items-center justify-content-center bg-light">
                        <i className="bi bi-play-circle-fill text-secondary fs-5"></i>
                      </div>
                    ) : (
                      <img src={item.src} alt="" className="w-100 h-100 object-fit-cover" />
                    )}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Info panel */}
          <div className="col-12 col-lg-7">
            {/* Category + title */}
            <Link to={`/products?category=${product.category.id}`}
              className="text-decoration-none text-primary small fw-semibold mb-1 d-block">
              {product.category.name}
            </Link>
            <h1 className="h3 fw-bold lh-sm mb-1">{product.name}</h1>
            <p className="text-muted small mb-3">SKU: <code className="text-muted">{product.sku}</code></p>

            {/* Discount label */}
            {hasDiscount && product.discountName && (
              <div className="mb-3 d-flex align-items-center gap-2 flex-wrap">
                <span className="badge bg-danger-subtle text-danger px-3 py-2" style={{ fontSize: '0.8rem' }}>
                  <i className="bi bi-tag-fill me-1"></i>{product.discountName}
                </span>
                {product.discountEndsAt && (
                  <span className="text-muted small">
                    <i className="bi bi-clock me-1"></i>
                    ends {new Date(product.discountEndsAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            )}

            {/* Price */}
            <div className="pdp-price-block d-flex align-items-baseline gap-3 flex-wrap mb-3">
              <span className="pdp-price">{format(effectivePrice)}</span>
              {compareAtPrice && compareAtPrice > effectivePrice && (
                <>
                  <span className="pdp-original-price">{format(compareAtPrice)}</span>
                  {discountPct && <span className="pdp-discount-badge">-{discountPct}%</span>}
                </>
              )}
            </div>

            {/* Shipping info */}
            <div className="pdp-shipping-row mb-3">
              <span className="pdp-shipping-item">
                <i className="bi bi-truck-front-fill"></i> Free shipping over ¥5,000
              </span>
              <span className="pdp-shipping-item">
                <i className="bi bi-arrow-repeat"></i> 15-day returns
              </span>
              <span className="pdp-shipping-item">
                <i className="bi bi-patch-check-fill"></i> Authenticity guaranteed
              </span>
            </div>

            <hr className="my-3" />

            {/* Stock */}
            <div className="mb-3">
              {product.stock > 0 ? (
                <span className="badge bg-success-subtle text-success px-3 py-2">
                  <i className="bi bi-check-circle me-1"></i>{product.stock} in stock
                </span>
              ) : (
                <span className="badge bg-danger-subtle text-danger px-3 py-2">
                  <i className="bi bi-x-circle me-1"></i>Out of stock
                </span>
              )}
            </div>

            {/* Quantity row */}
            <div className="d-flex align-items-center gap-3 mb-4 flex-wrap">
              <span className="text-muted small fw-semibold">Qty</span>
              <div className="input-group" style={{ width: '110px' }}>
                <button className="btn btn-outline-secondary btn-sm" type="button"
                  disabled={quantity <= 1} onClick={() => setQuantity((q) => Math.max(1, q - 1))}>−</button>
                <input type="text" className="form-control form-control-sm text-center fw-semibold"
                  value={quantity} readOnly />
                <button className="btn btn-outline-secondary btn-sm" type="button"
                  disabled={!product.stock || quantity >= product.stock}
                  onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))}>+</button>
              </div>

              {/* Love */}
              <button type="button"
                className={`btn btn-sm ms-auto d-flex align-items-center gap-1 ${loved ? 'btn-danger' : 'btn-outline-danger'}`}
                onClick={() => setLoved((v) => !v)}>
                <i className={`bi ${loved ? 'bi-heart-fill' : 'bi-heart'}`}></i>
                {loved ? 'Loved' : 'Love'}
              </button>
            </div>

            {/* CTAs */}
            <div className="d-flex gap-2 flex-wrap">
              <button className="btn btn-warning fw-bold px-4 py-2" disabled={product.stock === 0}
                onClick={addAndNotify}>
                <i className="bi bi-cart-plus me-2"></i>Add to Cart
              </button>
              <button className="btn btn-danger fw-bold px-4 py-2" disabled={product.stock === 0}
                onClick={buyNow}>
                <i className="bi bi-lightning-fill me-1"></i>Buy Now
              </button>
            </div>
          </div>
        </div>

        {/* ── Tabs ─────────────────────────────────────────────────────────── */}
        <div className="card border-0 shadow-sm rounded-3 mb-4">
          <div className="card-header bg-white border-bottom-0 px-3 pt-3 pb-0">
            <ul className="nav pdp-tabs">
              {TABS.map(({ key, label }) => (
                <li key={key} className="nav-item">
                  <button type="button"
                    className={`nav-link${activeTab === key ? ' active' : ''}`}
                    onClick={() => setActiveTab(key)}>
                    {label}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <div className="card-body px-4">
            <div className="pdp-tab-pane">
              {/* ── Details ── */}
              {activeTab === 'details' && (
                <div className="row g-5">
                  <div className="col-12 col-md-6">
                    <div className="pdp-section-header"><h5>Specifications</h5></div>
                    <div className="pdp-spec-list">
                      {[
                        ['SKU', product.sku],
                        ['Category', product.category.name],
                        ...Object.entries(product.attributes).map(([k, v]) => [k, String(v)]),
                      ].map(([key, val]) => (
                        <div key={key} className="pdp-spec-row">
                          <span className="pdp-spec-key">{key}</span>
                          <span className="pdp-spec-val">{val}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {product.description && (
                    <div className="col-12 col-md-6">
                      <div className="pdp-section-header"><h5>Description</h5></div>
                      <p className="pdp-description">{product.description}</p>
                    </div>
                  )}
                </div>
              )}

              {/* ── Reviews ── */}
              {activeTab === 'reviews' && <ReviewsSection productId={product.id} />}

              {/* ── Q&A ── */}
              {activeTab === 'qa' && (
                <div className="text-center py-5">
                  <i className="bi bi-chat-dots-fill text-primary opacity-25 d-block mb-3"
                    style={{ fontSize: '3rem' }}></i>
                  <h6 className="fw-semibold">No questions yet</h6>
                  <p className="text-muted small mb-3">Be the first to ask about this product.</p>
                  <button type="button" className="btn btn-outline-primary" disabled>
                    Ask a Question
                    <span className="badge bg-secondary-subtle text-secondary ms-2 fw-normal">Coming soon</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* ── Similar products ─────────────────────────────────────────────── */}
        <SimilarProducts categoryId={product.category.id} excludeId={product.id} />
      </div>

      {/* ── Buy Now modal ────────────────────────────────────────────────── */}
      {showBuyNowModal && product && (() => {
        const sameItem = cartItems.find((it) => it.product.id === product.id);
        return (
          <div className="modal d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,0.55)', zIndex: 1070 }}
            onClick={(e) => { if (e.target === e.currentTarget) setShowBuyNowModal(false); }}>
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content shadow-lg rounded-4">
                <div className="modal-header border-0 pb-0">
                  <h5 className="modal-title fw-bold">
                    <i className="bi bi-lightning-fill text-danger me-2"></i>Buy Now
                  </h5>
                  <button type="button" className="btn-close" onClick={() => setShowBuyNowModal(false)} />
                </div>
                <div className="modal-body pt-2">
                  {sameItem ? (
                    <>
                      <p className="mb-1">
                        <strong>{product.name}</strong> is already in your cart
                        ({sameItem.quantity} {sameItem.quantity === 1 ? 'item' : 'items'}).
                      </p>
                      <p className="text-muted small mb-0">
                        Proceed with the cart quantity, or checkout only your selected
                        quantity ({quantity}) without changing your cart.
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="mb-1">
                        You have {cartItems.length} {cartItems.length === 1 ? 'item' : 'items'} in your cart.
                      </p>
                      <p className="text-muted small mb-0">
                        Checkout only this item and leave your cart untouched, or add it to
                        your cart and checkout everything together.
                      </p>
                    </>
                  )}
                </div>
                <div className="modal-footer border-0 pt-0 gap-2 flex-column flex-sm-row">
                  <button type="button" className="btn btn-danger fw-semibold flex-fill" onClick={buyNowOnly}>
                    <i className="bi bi-lightning-fill me-1"></i>
                    {sameItem ? `Checkout selected (×${quantity})` : 'Checkout this item only'}
                  </button>
                  {sameItem ? (
                    <button type="button" className="btn btn-outline-primary flex-fill" onClick={() => { setShowBuyNowModal(false); navigate('/cart'); }}>
                      <i className="bi bi-cart me-1"></i>Use cart quantity (×{sameItem.quantity})
                    </button>
                  ) : (
                    <button type="button" className="btn btn-outline-primary flex-fill" onClick={buyNowWithCart}>
                      <i className="bi bi-cart-plus me-1"></i>Add to cart & checkout all
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ── Lightbox ─────────────────────────────────────────────────────── */}
      {isLightboxOpen && cur?.type === 'IMAGE' && (
        <div className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ backgroundColor: 'rgba(0,0,0,0.92)', zIndex: 1060, cursor: 'grab' }}
          onMouseDown={(e) => { dragStartXRef.current = e.clientX; hasDraggedRef.current = false; }}
          onClick={() => { if (!hasDraggedRef.current) setIsLightboxOpen(false); }}
          onTouchStart={(e) => { dragStartXRef.current = e.touches[0].clientX; hasDraggedRef.current = false; }}
          onTouchMove={(e) => {
            if (dragStartXRef.current !== null && Math.abs(e.touches[0].clientX - dragStartXRef.current) > 5)
              hasDraggedRef.current = true;
          }}
          onTouchEnd={(e) => {
            if (dragStartXRef.current === null) return;
            const d = e.changedTouches[0].clientX - dragStartXRef.current;
            if (Math.abs(d) > 60) nav(d < 0 ? 1 : -1);
            dragStartXRef.current = null;
          }}>
          <button type="button" aria-label="Close"
            className="btn btn-dark opacity-75 position-absolute top-0 end-0 m-3"
            style={{ zIndex: 1 }}
            onClick={(e) => { e.stopPropagation(); setIsLightboxOpen(false); }}>
            <i className="bi bi-x-lg"></i>
          </button>
          <img src={cur.src} alt={product.name} draggable={false}
            style={{ maxHeight: '90vh', maxWidth: '90vw', objectFit: 'contain', userSelect: 'none' }}
            onClick={(e) => e.stopPropagation()} />
          {mediaCount > 1 && (
            <>
              <button type="button" aria-label="Previous"
                className="btn btn-dark opacity-75 position-absolute top-50 start-0 translate-middle-y ms-3"
                style={{ zIndex: 1 }}
                onClick={(e) => { e.stopPropagation(); nav(-1); }}>
                <i className="bi bi-chevron-left fs-4"></i>
              </button>
              <button type="button" aria-label="Next"
                className="btn btn-dark opacity-75 position-absolute top-50 end-0 translate-middle-y me-3"
                style={{ zIndex: 1 }}
                onClick={(e) => { e.stopPropagation(); nav(1); }}>
                <i className="bi bi-chevron-right fs-4"></i>
              </button>
              <span className="badge bg-dark bg-opacity-75 position-absolute bottom-0 end-0 m-3">
                {selectedImage + 1} / {mediaCount}
              </span>
            </>
          )}
        </div>
      )}
    </>
  );
}
