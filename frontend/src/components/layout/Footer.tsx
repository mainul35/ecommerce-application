import { Link } from 'react-router-dom';

export function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="footer">
      <div className="container">
        <div className="row g-4">
          <div className="col-12 col-md-4">
            <h5 className="footer-title">E-Commerce</h5>
            <p className="text-muted">
              Your one-stop shop for all your needs. Quality products, great prices, and
              exceptional service.
            </p>
          </div>

          <div className="col-6 col-md-2">
            <h6 className="footer-title">Shop</h6>
            <ul className="list-unstyled">
              <li>
                <Link to="/products">All Products</Link>
              </li>
              <li>
                <Link to="/categories">Categories</Link>
              </li>
              <li>
                <Link to="/deals">Deals</Link>
              </li>
              <li>
                <Link to="/new-arrivals">New Arrivals</Link>
              </li>
            </ul>
          </div>

          <div className="col-6 col-md-2">
            <h6 className="footer-title">Account</h6>
            <ul className="list-unstyled">
              <li>
                <Link to="/profile">My Profile</Link>
              </li>
              <li>
                <Link to="/orders">Order History</Link>
              </li>
              <li>
                <Link to="/wishlist">Wishlist</Link>
              </li>
              <li>
                <Link to="/cart">Cart</Link>
              </li>
            </ul>
          </div>

          <div className="col-6 col-md-2">
            <h6 className="footer-title">Support</h6>
            <ul className="list-unstyled">
              <li>
                <Link to="/contact">Contact Us</Link>
              </li>
              <li>
                <Link to="/faq">FAQ</Link>
              </li>
              <li>
                <Link to="/shipping">Shipping Info</Link>
              </li>
              <li>
                <Link to="/returns">Returns</Link>
              </li>
            </ul>
          </div>

          <div className="col-6 col-md-2">
            <h6 className="footer-title">Legal</h6>
            <ul className="list-unstyled">
              <li>
                <Link to="/privacy">Privacy Policy</Link>
              </li>
              <li>
                <Link to="/terms">Terms of Service</Link>
              </li>
            </ul>
          </div>
        </div>

        <hr className="my-4 border-secondary" />

        <div className="row align-items-center">
          <div className="col-12 col-md-6 text-center text-md-start">
            <p className="mb-0 text-muted">
              &copy; {currentYear} E-Commerce. All rights reserved.
            </p>
          </div>
          <div className="col-12 col-md-6 text-center text-md-end mt-3 mt-md-0">
            <div className="d-flex justify-content-center justify-content-md-end gap-3">
              <a href="#" className="text-muted">
                <i className="bi bi-facebook"></i>
              </a>
              <a href="#" className="text-muted">
                <i className="bi bi-twitter"></i>
              </a>
              <a href="#" className="text-muted">
                <i className="bi bi-instagram"></i>
              </a>
              <a href="#" className="text-muted">
                <i className="bi bi-linkedin"></i>
              </a>
            </div>
          </div>
        </div>
      </div>
    </footer>
  );
}
