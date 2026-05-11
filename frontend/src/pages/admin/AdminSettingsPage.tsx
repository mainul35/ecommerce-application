import { useEffect, useState } from 'react';
import {
  adminMeService,
  type AdminProfileUpdateRequest,
} from '../../services/admin/adminMeService';
import { useAppDispatch } from '../../store';
import { setUser } from '../../store/slices/authSlice';
import { PageHeader } from '../../components/admin/layout/PageHeader';

interface FormState {
  email: string;
  firstName: string;
  lastName: string;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const empty: FormState = {
  email: '',
  firstName: '',
  lastName: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
};

export function AdminSettingsPage() {
  const dispatch = useAppDispatch();
  const [form, setForm] = useState<FormState>(empty);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    adminMeService
      .get()
      .then((u) =>
        setForm((prev) => ({
          ...prev,
          email: u.email,
          firstName: u.firstName,
          lastName: u.lastName,
        }))
      )
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (form.newPassword && form.newPassword !== form.confirmPassword) {
      setError('New password and confirmation do not match');
      return;
    }
    if (form.newPassword && !form.currentPassword) {
      setError('Enter your current password to set a new one');
      return;
    }

    setSubmitting(true);
    try {
      const payload: AdminProfileUpdateRequest = {
        email: form.email,
        firstName: form.firstName,
        lastName: form.lastName,
        currentPassword: form.currentPassword || undefined,
        newPassword: form.newPassword || undefined,
      };
      const updated = await adminMeService.update(payload);
      dispatch(setUser(updated));
      setForm({ ...form, currentPassword: '', newPassword: '', confirmPassword: '' });
      setSuccess('Profile updated.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Update failed');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <p className="text-muted">Loading…</p>;

  return (
    <>
      <PageHeader
        title="Account Settings"
        crumbs={[{ label: 'Home', to: '/admin' }, { label: 'Settings' }]}
      />

      {error && <div className="alert alert-danger">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      <div className="row">
        <div className="col-12 col-lg-7">
          <div className="card card-primary card-outline">
            <div className="card-header">
              <h3 className="card-title">Profile &amp; password</h3>
            </div>
            <div className="card-body">
              <form onSubmit={onSubmit}>
                <h5 className="mb-3">Profile</h5>
                <div className="mb-3">
                  <label className="form-label">Username / Email</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    required
                  />
                  <div className="form-text">
                    Used to log in. Can be a plain username (e.g. <code>admin</code>) or an email.
                  </div>
                </div>
                <div className="row">
                  <div className="col-12 col-md-6 mb-3">
                    <label className="form-label">First name</label>
                    <input
                      type="text"
                      className="form-control"
                      value={form.firstName}
                      onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-12 col-md-6 mb-3">
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

                <hr className="my-4" />

                <h5 className="mb-2">Change password</h5>
                <p className="text-muted small">
                  Leave the new-password fields empty if you only want to update your profile.
                </p>
                <div className="mb-3">
                  <label className="form-label">Current password</label>
                  <input
                    type="password"
                    className="form-control"
                    value={form.currentPassword}
                    onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
                    autoComplete="current-password"
                  />
                </div>
                <div className="row">
                  <div className="col-12 col-md-6 mb-3">
                    <label className="form-label">New password</label>
                    <input
                      type="password"
                      className="form-control"
                      value={form.newPassword}
                      onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
                      autoComplete="new-password"
                      minLength={6}
                    />
                  </div>
                  <div className="col-12 col-md-6 mb-3">
                    <label className="form-label">Confirm new password</label>
                    <input
                      type="password"
                      className="form-control"
                      value={form.confirmPassword}
                      onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                      autoComplete="new-password"
                    />
                  </div>
                </div>

                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Saving…' : 'Save changes'}
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
