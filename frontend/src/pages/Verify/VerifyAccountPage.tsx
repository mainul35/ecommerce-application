import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchCurrentUser } from '../../store/slices/authSlice';
import { verificationService } from '../../services/verificationService';
import { Loading } from '../../components/common';
import type { VerificationStatus } from '../../types';

/**
 * Mandatory account verification page: confirm email (link sent to inbox) and
 * verify a phone number (dummy OTP for now). Both must pass before checkout
 * or starting seller verification - the backend enforces it; this page is how
 * the user clears the gate.
 */
export const VerifyAccountPage = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { isAuthenticated } = useAppSelector((s) => s.auth);

  const [status, setStatus] = useState<VerificationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [phone, setPhone] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () =>
    verificationService.getStatus().then((s) => {
      setStatus(s);
      setPhone((prev) => prev || (s.phone ?? ''));
      // Keep the Redux user (banner, gates) in sync with the latest flags.
      dispatch(fetchCurrentUser());
      return s;
    });

  useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false);
      return;
    }
    refresh()
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return (
      <div className="container py-5 text-center">
        <h4 className="fw-bold mb-3">Please sign in</h4>
        <Link to="/login?redirect=/verify" className="btn btn-primary">Sign in</Link>
      </div>
    );
  }

  if (loading) return <Loading />;

  const resendEmail = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await verificationService.resendEmail();
      setNotice('Verification email sent. Check your inbox.');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const sendCode = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await verificationService.sendPhoneCode(phone);
      setCodeSent(true);
      setNotice('Verification code sent to your phone.');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const verifyCode = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await verificationService.verifyPhone(code);
      setCode('');
      setCodeSent(false);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const fullyVerified = status?.fullyVerified ?? false;

  return (
    <div className="container py-4">
      <div className="row justify-content-center">
        <div className="col-12 col-lg-7">
          <h1 className="h3 mb-1">Verify your account</h1>
          <p className="text-muted">
            Confirm your email and phone number to place orders and sell on the marketplace.
          </p>

          {error && <div className="alert alert-danger">{error}</div>}
          {notice && <div className="alert alert-info">{notice}</div>}

          {fullyVerified && (
            <div className="alert alert-success d-flex align-items-center justify-content-between flex-wrap gap-2">
              <span>
                <i className="bi bi-check-circle-fill me-2"></i>
                Your account is fully verified.
              </span>
              <button className="btn btn-success btn-sm" onClick={() => navigate('/')}>
                Continue shopping
              </button>
            </div>
          )}

          {/* Email */}
          <div className="card shadow-sm mb-3">
            <div className="card-body">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <h2 className="h6 mb-0">
                  <i className="bi bi-envelope me-2"></i>Email address
                </h2>
                {status?.emailVerified ? (
                  <span className="badge bg-success">Verified</span>
                ) : (
                  <span className="badge bg-warning text-dark">Pending</span>
                )}
              </div>
              <p className="small text-muted mb-3">{status?.email}</p>
              {status?.emailVerified ? (
                <p className="small mb-0 text-success">Your email is confirmed.</p>
              ) : (
                <>
                  <p className="small mb-3">
                    We sent a verification link to your email. Open it to confirm.
                    Didn’t get it?
                  </p>
                  <button className="btn btn-outline-primary btn-sm" disabled={busy} onClick={resendEmail}>
                    Resend email
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Phone */}
          <div className="card shadow-sm mb-3">
            <div className="card-body">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <h2 className="h6 mb-0">
                  <i className="bi bi-phone me-2"></i>Phone number
                </h2>
                {status?.phoneVerified ? (
                  <span className="badge bg-success">Verified</span>
                ) : (
                  <span className="badge bg-warning text-dark">Pending</span>
                )}
              </div>

              {status?.phoneVerified ? (
                <p className="small mb-0 text-success">{status.phone} is confirmed.</p>
              ) : (
                <>
                  <div className="mb-3">
                    <label className="form-label small fw-semibold">Phone number</label>
                    <input
                      type="tel"
                      className="form-control"
                      value={phone}
                      placeholder="+8801XXXXXXXXX"
                      onChange={(e) => setPhone(e.target.value)}
                      disabled={busy}
                    />
                  </div>
                  {!codeSent ? (
                    <button className="btn btn-outline-primary btn-sm" disabled={busy || !phone} onClick={sendCode}>
                      Send code
                    </button>
                  ) : (
                    <div className="row g-2 align-items-end">
                      <div className="col-12 col-sm-6">
                        <label className="form-label small fw-semibold">Enter the code</label>
                        <input
                          type="text"
                          inputMode="numeric"
                          className="form-control"
                          value={code}
                          placeholder="6-digit code"
                          onChange={(e) => setCode(e.target.value)}
                          disabled={busy}
                        />
                      </div>
                      <div className="col-12 col-sm-6 d-flex gap-2">
                        <button className="btn btn-primary btn-sm" disabled={busy || !code} onClick={verifyCode}>
                          Verify
                        </button>
                        <button className="btn btn-link btn-sm" disabled={busy} onClick={sendCode}>
                          Resend
                        </button>
                      </div>
                    </div>
                  )}
                  <p className="small text-muted mt-2 mb-0">
                    SMS delivery is in test mode — check the server log for your code.
                  </p>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
