import { useEffect, useState } from 'react';
import {
  adminProductService,
  type ProductUpsertRequest,
} from '../../services/admin/adminProductService';
import { adminCategoryService } from '../../services/admin/adminCategoryService';
import { AttributeEditor } from '../../components/admin/AttributeEditor';
import { DiscountsScopePanel } from '../../components/admin/DiscountsScopePanel';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { Category, Product } from '../../types';

const emptyForm: ProductUpsertRequest = {
  name: '',
  description: '',
  price: 0,
  originalPrice: undefined,
  imageUrl: '',
  categoryId: '',
  attributes: {},
  stock: 0,
  sku: '',
};

export function AdminProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<ProductUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [showForm, setShowForm] = useState(false);

  const loadProducts = (p: number) => {
    setLoading(true);
    adminProductService
      .list(p, 10, 'newest')
      .then((paged) => {
        setProducts(paged.content);
        setTotalPages(paged.totalPages);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    adminCategoryService
      .list()
      .then(setCategories)
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    loadProducts(page);
  }, [page]);

  const startCreate = () => {
    setEditingId(null);
    setForm({ ...emptyForm, categoryId: categories[0]?.id ?? '' });
    setShowForm(true);
  };

  const startEdit = (p: Product) => {
    setEditingId(p.id);
    setForm({
      name: p.name,
      description: p.description ?? '',
      price: p.price,
      originalPrice: p.originalPrice,
      imageUrl: p.imageUrl ?? '',
      categoryId: p.category?.id ?? '',
      attributes: p.attributes ?? {},
      stock: p.stock,
      sku: p.sku,
    });
    setShowForm(true);
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
    setShowForm(false);
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (editingId) {
        await adminProductService.update(editingId, form);
      } else {
        await adminProductService.create(form);
      }
      reset();
      loadProducts(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this product? This is a soft-delete.')) return;
    setError(null);
    try {
      await adminProductService.delete(id);
      loadProducts(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Products"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Products' }]}
        actions={
          !showForm && (
            <button className="btn btn-primary" onClick={startCreate}>
              <i className="bi bi-plus-lg me-1"></i>New Product
            </button>
          )
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}

      {showForm && (
        <div className="card card-primary card-outline mb-4">
          <div className="card-header">
            <h3 className="card-title">{editingId ? 'Edit product' : 'New product'}</h3>
          </div>
          <div className="card-body">
            <form onSubmit={onSubmit}>
              <div className="row g-3">
                <div className="col-12 col-md-6">
                  <label className="form-label">Name</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label">SKU</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.sku}
                    onChange={(e) => setForm({ ...form, sku: e.target.value })}
                    required
                    disabled={!!editingId}
                  />
                </div>
                <div className="col-12">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-control"
                    rows={2}
                    value={form.description ?? ''}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <div className="col-6 col-md-3">
                  <label className="form-label">Price</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="form-control"
                    value={form.price}
                    onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
                    required
                  />
                </div>
                <div className="col-6 col-md-3">
                  <label className="form-label">Original price</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="form-control"
                    value={form.originalPrice ?? ''}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        originalPrice: e.target.value === '' ? undefined : Number(e.target.value),
                      })
                    }
                  />
                </div>
                <div className="col-6 col-md-3">
                  <label className="form-label">Stock</label>
                  <input
                    type="number"
                    min="0"
                    className="form-control"
                    value={form.stock}
                    onChange={(e) => setForm({ ...form, stock: Number(e.target.value) })}
                    required
                  />
                </div>
                <div className="col-6 col-md-3">
                  <label className="form-label">Category</label>
                  <select
                    className="form-select"
                    value={form.categoryId}
                    onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                    required
                  >
                    <option value="">— select —</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="col-12">
                  <label className="form-label">Image URL</label>
                  <input
                    type="url"
                    className="form-control"
                    value={form.imageUrl ?? ''}
                    onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                  />
                </div>
                <div className="col-12">
                  <AttributeEditor
                    value={form.attributes ?? {}}
                    onChange={(next) => setForm({ ...form, attributes: next })}
                  />
                </div>
                <div className="col-12 d-flex gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingId ? 'Update' : 'Create'}
                  </button>
                  <button type="button" className="btn btn-outline-secondary" onClick={reset}>
                    Cancel
                  </button>
                </div>
              </div>
            </form>

            {/* Discounts can only be attached to a product after it exists (needs an id). */}
            {editingId && (
              <div className="mt-4">
                <DiscountsScopePanel
                  scope="PRODUCT"
                  targetId={editingId}
                  targetLabel={form.name || 'this product'}
                />
              </div>
            )}
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h3 className="card-title">All products</h3>
        </div>
        <div className="card-body">
          {loading ? (
            <p className="text-muted mb-0">Loading…</p>
          ) : products.length === 0 ? (
            <p className="text-muted mb-0">No products yet.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>SKU</th>
                    <th>Category</th>
                    <th className="text-end">Price</th>
                    <th className="text-end">Stock</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p) => (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>
                        <code>{p.sku}</code>
                      </td>
                      <td className="text-muted small">{p.category?.name ?? '—'}</td>
                      <td className="text-end">${Number(p.price).toFixed(2)}</td>
                      <td className="text-end">{p.stock}</td>
                      <td className="text-end">
                        <button
                          className="btn btn-sm btn-outline-primary me-2"
                          onClick={() => startEdit(p)}
                        >
                          Edit
                        </button>
                        <button
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => onDelete(p.id)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="d-flex justify-content-between align-items-center mt-3">
              <button
                className="btn btn-sm btn-outline-secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span className="text-muted small">
                Page {page + 1} of {totalPages}
              </span>
              <button
                className="btn btn-sm btn-outline-secondary"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
