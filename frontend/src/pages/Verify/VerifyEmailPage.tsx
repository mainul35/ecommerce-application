import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { verificationService } from '../../services/verificationService';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchCurrentUser } from '../../store/slices/authSlice';

type State = 'verifying' | 'success' | 'error';

/**
 * Landing page for the email verification link (/verify-email?token=...).
 * Public - works even without a session, since the token authenticates it.
 */
export const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const dispatch = useAppDispatch();
  const { isAuthenticated } = useAppSelector((s) => s.auth);
  const token = searchParams.get('token');

  const [state, setState] = useState<State>('verifying');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setState('error');
      setMessage('This link is missing its verification token.');
      return;
    }
    verificationService
      .verifyEmail(token)
      .then(() => {
        setState('success');
        // If the user is logged in here, refresh flags so the banner clears.
        if (isAuthenticated) dispatch(fetchCurrentUser());
      })
      .catch((e: Error) => {
        setState('error');
        setMessage(e.message);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-12 col-md-6 text-center">
          {state === 'verifying' && (
            <>
              <div className="spinner-border text-primary mb-3" role="status">
                <span className="visually-hidden">Verifying…</span>
              </div>
              <p>Verifying your email…</p>
            </>
          )}
          {state === 'success' && (
            <>
              <i className="bi bi-check-circle-fill text-success d-block mb-3 display-4"></i>
              <h4 className="fw-bold mb-2">Email verified</h4>
              <p className="text-muted mb-4">
                Thanks! Next, verify your phone number to finish setting up your account.
              </p>
              <Link to="/verify" className="btn btn-primary px-4">Continue</Link>
            </>
          )}
          {state === 'error' && (
            <>
              <i className="bi bi-x-circle-fill text-danger d-block mb-3 display-4"></i>
              <h4 className="fw-bold mb-2">Verification failed</h4>
              <p className="text-muted mb-4">{message}</p>
              <Link to="/verify" className="btn btn-outline-primary px-4">
                Back to verification
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
