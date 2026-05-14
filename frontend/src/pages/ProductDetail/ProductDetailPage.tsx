import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchProductById } from '../../store/slices/productsSlice';
import { addToCart } from '../../store/slices/cartSlice';
import { addNotification } from '../../store/slices/uiSlice';
import { Loading, EmptyState } from '../../components/common';
import { useCurrency } from '../../storefront/CurrencyContext';

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const dispatch = useAppDispatch();
  const { selectedProduct: product, isLoading, error } = useAppSelector((state) => state.products);
  const { format } = useCurrency();
  const [quantity, setQuantity] = useState(1);
  const [selectedImage, setSelectedImage] = useState(0);

  useEffect(() => {
    if (id) {
      dispatch(fetchProductById(id));
    }
  }, [dispatch, id]);

  const handleAddToCart = () => {
    if (product) {
      dispatch(addToCart({ product, quantity }));
      dispatch(
        addNotification({
          type: 'success',
          message: `${product.name} added to cart`,
          duration: 3000,
        })
      );
    }
  };

  const handleQuantityChange = (delta: number) => {
    const newQuantity = quantity + delta;
    if (newQuantity >= 1 && newQuantity <= (product?.stock || 1)) {
      setQuantity(newQuantity);
    }
  };

  if (isLoading) {
    return <Loading fullScreen />;
  }

  if (error || !product) {
    return (
      <div className="container py-5">
        <EmptyState
          icon="bi-exclamation-circle"
          title="Product not found"
          description={error || 'The product you are looking for does not exist'}
          actionLabel="Back to Products"
          actionLink="/products"
        />
      </div>
    );
  }

  const images = product.images.length > 0 ? product.images : [product.imageUrl];
  const hasDiscount = typeof product.discountedPrice === 'number';
  const effectivePrice = hasDiscount ? product.discountedPrice! : product.price;
  const compareAtPrice = hasDiscount ? product.price : null;
  const discountPercentage = hasDiscount
    ? Math.round(Number(product.discountPercent ?? 0))
    : null;

  return (
    <div className="container py-5">
      <nav aria-label="breadcrumb" className="mb-4">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/">Home</Link>
          </li>
          <li className="breadcrumb-item">
            <Link to="/products">Products</Link>
          </li>
          <li className="breadcrumb-item">
            <Link to={`/products?category=${product.category.id}`}>{product.category.name}</Link>
          </li>
          <li className="breadcrumb-item active" aria-current="page">
            {product.name}
          </li>
        </ol>
      </nav>

      <div className="row g-5">
        <div className="col-12 col-lg-6">
          <div className="mb-3">
            <img
              src={images[selectedImage] || '/placeholder.png'}
              alt={product.name}
              className="img-fluid rounded"
            />
          </div>
          {images.length > 1 && (
            <div className="row g-2">
              {images.map((image, index) => (
                <div key={index} className="col-3">
                  <img
                    src={image}
                    alt={`${product.name} ${index + 1}`}
                    className={`img-fluid rounded cursor-pointer ${selectedImage === index ? 'border border-primary border-2' : ''}`}
                    onClick={() => setSelectedImage(index)}
                  />
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="col-12 col-lg-6">
          <span className="badge bg-secondary mb-2">{product.category.name}</span>
          <h1 className="mb-3">{product.name}</h1>

          {hasDiscount && product.discountName && (
            <p className="mb-2">
              <span className="badge bg-danger-subtle text-danger fs-6">
                <i className="bi bi-tag-fill me-1"></i>
                {product.discountName}
              </span>
              {product.discountEndsAt && (
                <span className="text-muted small ms-2">
                  ends {new Date(product.discountEndsAt).toLocaleDateString()}
                </span>
              )}
            </p>
          )}

          <div className="d-flex align-items-center gap-3 mb-4">
            <span className="fs-2 fw-bold text-primary">{format(effectivePrice)}</span>
            {compareAtPrice && compareAtPrice > effectivePrice && (
              <>
                <span className="fs-4 text-muted text-decoration-line-through">
                  {format(compareAtPrice)}
                </span>
                {discountPercentage && (
                  <span className="badge bg-danger">-{discountPercentage}%</span>
                )}
              </>
            )}
          </div>

          <p className="text-muted mb-4">{product.description}</p>

          <div className="mb-4">
            <span
              className={`badge ${product.stock > 0 ? 'bg-success' : 'bg-danger'}`}
            >
              {product.stock > 0 ? `In Stock (${product.stock} available)` : 'Out of Stock'}
            </span>
          </div>

          {product.stock > 0 && (
            <div className="d-flex align-items-center gap-3 mb-4">
              <div className="input-group" style={{ width: '140px' }}>
                <button
                  className="btn btn-outline-secondary"
                  type="button"
                  onClick={() => handleQuantityChange(-1)}
                  disabled={quantity <= 1}
                >
                  -
                </button>
                <input
                  type="text"
                  className="form-control text-center"
                  value={quantity}
                  readOnly
                />
                <button
                  className="btn btn-outline-secondary"
                  type="button"
                  onClick={() => handleQuantityChange(1)}
                  disabled={quantity >= product.stock}
                >
                  +
                </button>
              </div>
              <button className="btn btn-primary btn-lg" onClick={handleAddToCart}>
                Add to Cart
              </button>
            </div>
          )}

          <div className="card mt-4">
            <div className="card-body">
              <h5 className="card-title">Product Details</h5>
              <table className="table table-sm mb-0">
                <tbody>
                  <tr>
                    <td className="text-muted">SKU</td>
                    <td>{product.sku}</td>
                  </tr>
                  <tr>
                    <td className="text-muted">Category</td>
                    <td>{product.category.name}</td>
                  </tr>
                  {Object.entries(product.attributes).map(([key, value]) => (
                    <tr key={key}>
                      <td className="text-muted text-capitalize">{key}</td>
                      <td>{String(value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
