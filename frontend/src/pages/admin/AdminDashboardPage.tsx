import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminCategoryService } from '../../services/admin/adminCategoryService';
import { adminProductService } from '../../services/admin/adminProductService';
import { PageHeader } from '../../components/admin/layout/PageHeader';

interface SmallBoxProps {
  label: string;
  value: string | number | null;
  icon: string;
  variant: 'primary' | 'success' | 'warning' | 'info';
  to: string;
}

function SmallBox({ label, value, icon, variant, to }: Readonly<SmallBoxProps>) {
  return (
    <div className={`small-box text-bg-${variant}`}>
      <div className="inner">
        <h3>{value ?? '—'}</h3>
        <p className="mb-0">{label}</p>
      </div>
      <span className="icon">
        <i className={`bi ${icon}`}></i>
      </span>
      <Link to={to} className="small-box-footer">
        Manage <i className="bi bi-arrow-right ms-1"></i>
      </Link>
    </div>
  );
}

export function AdminDashboardPage() {
  const [categoryCount, setCategoryCount] = useState<number | null>(null);
  const [productCount, setProductCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([adminCategoryService.list(), adminProductService.list(0, 1)])
      .then(([cats, prods]) => {
        setCategoryCount(cats.length);
        setProductCount(prods.totalElements);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <>
      <PageHeader
        title="Dashboard"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Dashboard' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-3">
        <div className="col-12 col-md-6 col-lg-3">
          <SmallBox
            label="Categories"
            value={categoryCount}
            icon="bi-tags"
            variant="primary"
            to="/admin/categories"
          />
        </div>
        <div className="col-12 col-md-6 col-lg-3">
          <SmallBox
            label="Products"
            value={productCount}
            icon="bi-box-seam"
            variant="success"
            to="/admin/products"
          />
        </div>
      </div>

      <div className="card mt-4">
        <div className="card-header">
          <h3 className="card-title">Quick actions</h3>
        </div>
        <div className="card-body d-flex flex-wrap gap-2">
          <Link to="/admin/categories" className="btn btn-outline-primary">
            <i className="bi bi-tags me-2"></i>Manage Categories
          </Link>
          <Link to="/admin/products" className="btn btn-outline-success">
            <i className="bi bi-box-seam me-2"></i>Manage Products
          </Link>
          <Link to="/admin/settings" className="btn btn-outline-secondary">
            <i className="bi bi-gear me-2"></i>Account Settings
          </Link>
        </div>
      </div>
    </>
  );
}
