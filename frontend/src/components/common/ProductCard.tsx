import { Link } from 'react-router-dom';
import { useAppDispatch } from '../../store';
import { addToCart } from '../../store/slices/cartSlice';
import { addNotification } from '../../store/slices/uiSlice';
import { useCurrency } from '../../storefront/CurrencyContext';
import { mediaUrl } from '../../services/admin/adminProductMediaService';
import type { Product } from '../../types';

interface ProductCardProps {
  product: Product;
}

export function ProductCard({ product }: ProductCardProps) {
  const dispatch = useAppDispatch();
  const { format } = useCurrency();

  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    dispatch(addToCart({ product, quantity: 1 }));
    dispatch(
      addNotification({
        type: 'success',
        message: `${product.name} added to cart`,
        duration: 3000,
      })
    );
  };

  const firstImage = product.media?.find((m) => m.mediaType === 'IMAGE');
  const cardImage = firstImage ? mediaUrl(firstImage.url) : (product.imageUrl || '/placeholder.png');

  // Sale-price display is driven entirely by active discount campaigns.
  const hasDiscount = typeof product.discountedPrice === 'number';
  const effectivePrice = hasDiscount ? product.discountedPrice! : product.price;
  const compareAtPrice = hasDiscount ? product.price : null;
  const discountPercentage = hasDiscount
    ? Math.round(Number(product.discountPercent ?? 0))
    : null;

  return (
    <div className="card product-card h-100">
      <Link to={`/products/${product.id}`} className="text-decoration-none">
        <div className="position-relative">
          <img
            src={cardImage}
            className="card-img-top product-image"
            alt={product.name}
          />
          {discountPercentage && discountPercentage > 0 && (
            <span className="badge-discount">-{discountPercentage}%</span>
          )}
        </div>
        <div className="card-body d-flex flex-column">
          <h6 className="product-title text-dark">{product.name}</h6>
          <p className="text-muted small mb-2">{product.category.name}</p>
          {hasDiscount && product.discountName && (
            <p className="small mb-2">
              <span className="badge bg-danger-subtle text-danger">{product.discountName}</span>
            </p>
          )}
          <div className="mt-auto">
            <div className="d-flex align-items-center gap-2 mb-3">
              <span className="product-price">{format(effectivePrice)}</span>
              {compareAtPrice && compareAtPrice > effectivePrice && (
                <span className="product-original-price">{format(compareAtPrice)}</span>
              )}
            </div>
            <button
              className="btn btn-primary w-100"
              onClick={handleAddToCart}
              disabled={product.stock === 0}
            >
              {product.stock === 0 ? 'Out of Stock' : 'Add to Cart'}
            </button>
          </div>
        </div>
      </Link>
    </div>
  );
}
