import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchProducts, fetchCategories } from '../../store/slices/productsSlice';
import { ProductCard, Loading } from '../../components/common';

export function HomePage() {
  const dispatch = useAppDispatch();
  const { items: products, categories, isLoading } = useAppSelector((state) => state.products);

  useEffect(() => {
    dispatch(fetchProducts({ page: 0, size: 8 }));
    dispatch(fetchCategories());
  }, [dispatch]);

  return (
    <>
      <section className="hero-section">
        <div className="container">
          <div className="row align-items-center">
            <div className="col-12 col-lg-6">
              <h1 className="hero-title display-4 mb-4">
                Discover Amazing Products at Great Prices
              </h1>
              <p className="hero-subtitle mb-4">
                Shop the latest trends with confidence. Quality products, fast delivery, and
                exceptional customer service.
              </p>
              <div className="d-flex gap-3">
                <Link to="/products" className="btn btn-light btn-lg">
                  Shop Now
                </Link>
                <Link to="/categories" className="btn btn-outline-light btn-lg">
                  Browse Categories
                </Link>
              </div>
            </div>
            <div className="col-12 col-lg-6 d-none d-lg-block">
              <img
                src="/hero-image.png"
                alt="Hero"
                className="img-fluid"
              />
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="d-flex justify-content-between align-items-center mb-4">
            <h2 className="mb-0">Featured Categories</h2>
            <Link to="/categories" className="btn btn-outline-primary">
              View All
            </Link>
          </div>
          <div className="row g-4">
            {categories.slice(0, 4).map((category) => (
              <div key={category.id} className="col-6 col-md-3">
                <Link
                  to={`/products?category=${category.id}`}
                  className="card h-100 text-decoration-none"
                >
                  <div className="card-body text-center">
                    <img
                      src={category.imageUrl || '/category-placeholder.png'}
                      alt={category.name}
                      className="img-fluid mb-3 category-image"
                    />
                    <h5 className="card-title">{category.name}</h5>
                  </div>
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section bg-light">
        <div className="container">
          <div className="d-flex justify-content-between align-items-center mb-4">
            <h2 className="mb-0">Featured Products</h2>
            <Link to="/products" className="btn btn-outline-primary">
              View All
            </Link>
          </div>

          {isLoading ? (
            <Loading />
          ) : (
            <div className="row g-4">
              {products.slice(0, 8).map((product) => (
                <div key={product.id} className="col-6 col-md-4 col-lg-3">
                  <ProductCard product={product} />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="row g-4 text-center">
            <div className="col-6 col-md-3">
              <div className="p-4">
                <i className="bi bi-truck fs-1 text-primary mb-3 d-block"></i>
                <h5>Free Shipping</h5>
                <p className="text-muted small mb-0">On orders over $50</p>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-4">
                <i className="bi bi-shield-check fs-1 text-primary mb-3 d-block"></i>
                <h5>Secure Payment</h5>
                <p className="text-muted small mb-0">100% secure checkout</p>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-4">
                <i className="bi bi-arrow-repeat fs-1 text-primary mb-3 d-block"></i>
                <h5>Easy Returns</h5>
                <p className="text-muted small mb-0">30-day return policy</p>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="p-4">
                <i className="bi bi-headset fs-1 text-primary mb-3 d-block"></i>
                <h5>24/7 Support</h5>
                <p className="text-muted small mb-0">Dedicated support team</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
