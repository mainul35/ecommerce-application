import { useEffect, useState } from 'react';
import axios from 'axios';
import type { DisputeAttachmentType } from '../../types';

/** Backend origin (without the /api suffix) for serving /uploads/** files directly. */
const BACKEND_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(
  /\/api\/?$/,
  ''
);

interface AuthMediaProps {
  /** Relative URL of the protected file, e.g. /uploads/disputes/{id}/uuid.jpg */
  url: string;
  type: DisputeAttachmentType;
  alt?: string;
  className?: string;
}

/**
 * Loads a protected /uploads/** attachment with the bearer token attached,
 * since a plain <img src> can't send the Authorization header. The file is
 * fetched as a blob, exposed via an object URL, and revoked on cleanup.
 */
export function AuthMedia({ url, type, alt, className }: AuthMediaProps) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let revoked = false;
    let created: string | null = null;
    setObjectUrl(null);
    setError(false);

    const token = localStorage.getItem('token');
    axios
      .get<Blob>(`${BACKEND_ORIGIN}${url}`, {
        responseType: 'blob',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      })
      .then((response) => {
        if (revoked) return;
        created = URL.createObjectURL(response.data);
        setObjectUrl(created);
      })
      .catch(() => {
        if (!revoked) setError(true);
      });

    return () => {
      revoked = true;
      if (created) URL.revokeObjectURL(created);
    };
  }, [url]);

  if (error) {
    return <span className="text-danger small">Attachment unavailable</span>;
  }

  if (!objectUrl) {
    return (
      <div className={`d-flex align-items-center justify-content-center bg-light ${className ?? ''}`}>
        <span className="spinner-border spinner-border-sm text-secondary" role="status">
          <span className="visually-hidden">Loading attachment…</span>
        </span>
      </div>
    );
  }

  if (type === 'VIDEO') {
    return <video src={objectUrl} controls className={className} />;
  }

  return <img src={objectUrl} alt={alt ?? 'attachment'} className={className} />;
}
