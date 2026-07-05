import { useEffect, useState } from 'react';
import axios from 'axios';
import type { DisputeAttachmentType } from '../../types';

/** Backend origin (API base URL minus the trailing /api), used for /uploads/** paths. */
const BACKEND_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(
  /\/api\/?$/,
  ''
);

interface AdminAuthMediaProps {
  /** Party-checked media path served by the backend, e.g. /api/disputes/{id}/attachments/{id}/file */
  url: string;
  type: DisputeAttachmentType;
  originalName?: string;
}

/**
 * Renders a protected /uploads/** attachment that requires the Authorization header.
 * A plain <img src> would get a 401, so we fetch the bytes with the Bearer token and
 * render an object URL instead.
 */
export function AdminAuthMedia({ url, type, originalName }: AdminAuthMediaProps) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let revoked = false;
    let createdUrl: string | null = null;
    setError(null);
    setObjectUrl(null);

    const token = localStorage.getItem('token');
    axios
      .get<Blob>(`${BACKEND_ORIGIN}${url}`, {
        responseType: 'blob',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      })
      .then((res) => {
        if (revoked) return;
        createdUrl = URL.createObjectURL(res.data);
        setObjectUrl(createdUrl);
      })
      .catch(() => {
        if (!revoked) setError('Failed to load attachment');
      });

    return () => {
      revoked = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [url]);

  if (error) {
    return <div className="text-danger small">{error}</div>;
  }

  if (!objectUrl) {
    return <div className="text-muted small">Loading attachment…</div>;
  }

  if (type === 'VIDEO') {
    return <video controls src={objectUrl} className="img-fluid rounded" />;
  }

  return <img src={objectUrl} alt={originalName ?? 'attachment'} className="img-fluid rounded" />;
}
