import { useEffect, useState } from 'react';
import {
  adminManagerService,
  type ManagerCreateRequest,
} from '../../services/admin/adminManagerService';
import { PageHeader } from '../../components/admin/layout/PageHeader';
import type { User } from '../../types';

const emptyForm: ManagerCreateRequest = {
  email: '',
  firstName: '',
  lastName: '',
  password: '',
};

export function AdminManagersPage() {
  const [managers, setManagers] = useState<User[]>([]);
  const [form, setForm] = useState<ManagerCreateRequest>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const refresh = () => {
    setLoading(true);
    adminManagerService
      .list()
      .then(setManagers)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(refresh, []);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setInfo(null);
    if (form.password.length < 6) {
      setError('Initial password must be at least 6 characters.');
      return;
    }
    setSubmitting(true);
    try {
      const created = await adminManagerService.create({
        ...form,
        email: form.email.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
      });
      setInfo(
        `Created manager "${created.firstName} ${created.lastName}". Share the initial password with them and have them change it via Settings.`,
      );
      setForm(emptyForm);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActive = async (m: User) => {
    const nextActive = !m.isActive;
    const verb = nextActive ? 'unblock' : 'block';
    if (!window.confirm(`Really ${verb} ${m.firstName} ${m.lastName}?`)) return;
    setError(null);
    setInfo(null);
    try {
      await adminManagerService.setActive(m.id, nextActive);
      refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : `${verb} failed`);
    }
  };

  return (
    <>
      <PageHeader
        title="Managers"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Managers' }]}
      />

      <p className="text-muted small">
        Managers are limited-admin accounts. They can sign in at <code>/admin/login</code> and
        manage <strong>products</strong> and <strong>categories</strong> (including hiding
        unused ones via delete). They cannot access discounts, coupons, orders, or this page.
      </p>

      {error && <div className="alert alert-danger">{error}</div>}
      {info && (
        <div className="alert alert-success d-flex justify-content-between align-items-center">
          <span>{info}</span>
          <button
            type="button"
            className="btn-close"
            aria-label="Dismiss"
            onClick={() => setInfo(null)}
          />
        </div>
      )}

      <div className="row g-4">
        <div className="col-12 col-lg-5">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">New manager</h3>
            </div>
            <div className="card-body">
              <form onSubmit={onSubmit}>
                <div className="mb-3">
                  <label className="form-label">Email or username</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="pat or pat@example.com"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    autoComplete="off"
                    required
                  />
                </div>
                <div className="row g-2 mb-3">
                  <div className="col-6">
                    <label className="form-label">First name</label>
                    <input
                      type="text"
                      className="form-control"
                      value={form.firstName}
                      onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-6">
                    <label className="form-label">Last name</label>
                    <input
                      type="text"
                      className="form-control"
                      value={form.lastName}
                      onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                      required
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label">Initial password</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="At least 6 characters"
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    autoComplete="new-password"
                    minLength={6}
                    required
                  />
                  <div className="form-text">
                    The manager can change it later via Settings.
                  </div>
                </div>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Creating…' : 'Create manager'}
                </button>
              </form>
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-7">
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">All managers</h3>
            </div>
            <div className="card-body">
              {loading ? (
                <p className="text-muted mb-0">Loading…</p>
              ) : managers.length === 0 ? (
                <p className="text-muted mb-0">No managers yet.</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Username / Email</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {managers.map((m) => (
                        <tr key={m.id}>
                          <td>
                            {m.firstName} {m.lastName}
                          </td>
                          <td>
                            <code>{m.email}</code>
                          </td>
                          <td>
                            <span className={`badge ${m.isActive ? 'bg-success' : 'bg-danger'}`}>
                              {m.isActive ? 'Active' : 'Blocked'}
                            </span>
                          </td>
                          <td className="text-muted small">
                            {new Date(m.createdAt).toLocaleDateString()}
                          </td>
                          <td className="text-end">
                            <button
                              className={`btn btn-sm ${
                                m.isActive ? 'btn-outline-danger' : 'btn-outline-success'
                              }`}
                              onClick={() => toggleActive(m)}
                            >
                              {m.isActive ? (
                                <>
                                  <i className="bi bi-slash-circle me-1"></i>Block
                                </>
                              ) : (
                                <>
                                  <i className="bi bi-check-circle me-1"></i>Unblock
                                </>
                              )}
                            </button>
                          </td>
                        </tr>
                      ))}
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
