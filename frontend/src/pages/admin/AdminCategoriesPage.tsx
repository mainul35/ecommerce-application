import { useEffect, useState } from 'react';
import {
  adminCategoryService,
  type CategoryUpsertRequest,
} from '../../services/admin/adminCategoryService';
import type { Category } from '../../types';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import { DiscountsScopePanel } from '../../components/admin/DiscountsScopePanel';

const emptyForm: CategoryUpsertRequest = {
  name: '',
  slug: '',
  description: '',
  parentId: null,
  imageUrl: '',
};

const slugify = (value: string) =>
  value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

export function AdminCategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CategoryUpsertRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);

  const refresh = () => {
    setLoading(true);
    adminCategoryService
      .list()
      .then((data) => setCategories(data))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
  }, []);

  const startEdit = (cat: Category) => {
    setEditingId(cat.id);
    setForm({
      name: cat.name,
      slug: cat.slug,
      description: cat.description ?? '',
      parentId: cat.parentId ?? null,
      imageUrl: cat.imageUrl ?? '',
    });
  };

  const reset = () => {
    setEditingId(null);
    setForm(emptyForm);
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const payload: CategoryUpsertRequest = {
        ...form,
        slug: form.slug || slugify(form.name),
        parentId: form.parentId || null,
      };
      if (editingId) {
        await adminCategoryService.update(editingId, payload);
      } else {
        await adminCategoryService.create(payload);
      }
      reset();
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this category? This is a soft-delete.')) return;
    setError(null);
    try {
      await adminCategoryService.delete(id);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  return (
    <>
      <PageHeader
        title="Categories"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Categories' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">{editingId ? 'Edit category' : 'New category'}</h3>
            </div>
            <div className="card-body">
              <form onSubmit={onSubmit}>
                <div className="mb-3">
                  <label className="form-label">Name</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div className="mb-3">
                  <label className="form-label">Slug</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="auto-generated from name if empty"
                    value={form.slug}
                    onChange={(e) => setForm({ ...form, slug: e.target.value })}
                  />
                </div>
                <div className="mb-3">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-control"
                    rows={2}
                    value={form.description ?? ''}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <div className="mb-3">
                  <label className="form-label">Parent category</label>
                  <select
                    className="form-select"
                    value={form.parentId ?? ''}
                    onChange={(e) =>
                      setForm({ ...form, parentId: e.target.value || null })
                    }
                  >
                    <option value="">— none (root) —</option>
                    {categories
                      .filter((c) => c.id !== editingId)
                      .map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Image URL</label>
                  <input
                    type="url"
                    className="form-control"
                    value={form.imageUrl ?? ''}
                    onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                  />
                </div>
                <div className="d-flex gap-2">
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving…' : editingId ? 'Update' : 'Create'}
                  </button>
                  {editingId && (
                    <button type="button" className="btn btn-outline-secondary" onClick={reset}>
                      Cancel
                    </button>
                  )}
                </div>
              </form>

              {/* Discounts can only be attached to a category after it exists. */}
              {editingId && (
                <div className="mt-4">
                  <DiscountsScopePanel
                    scope="CATEGORY"
                    targetId={editingId}
                    targetLabel={form.name || 'this category'}
                  />
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-7">
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">All categories</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : categories.length === 0 ? (
                <p className="text-muted mb-0">No categories yet.</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Slug</th>
                        <th>Parent</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {categories.map((c) => {
                        const parent = categories.find((p) => p.id === c.parentId);
                        return (
                          <tr key={c.id}>
                            <td>{c.name}</td>
                            <td>
                              <code>{c.slug}</code>
                            </td>
                            <td className="text-muted small">{parent?.name ?? '—'}</td>
                            <td className="text-end">
                              <button
                                className="btn btn-sm btn-outline-primary me-2"
                                onClick={() => startEdit(c)}
                              >
                                Edit
                              </button>
                              <button
                                className="btn btn-sm btn-outline-danger"
                                onClick={() => onDelete(c.id)}
                              >
                                Delete
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
