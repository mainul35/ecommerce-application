import { useEffect, useState } from 'react';
import { adminKycService } from '../../services/admin/adminKycService';

interface KycEvidenceImageProps {
  caseId: string;
  documentId: string;
  /** Human label shown beneath the image, e.g. "ID front". */
  label: string;
}

/**
 * Loads a single KYC evidence image through the party-checked blob endpoint,
 * rendering a spinner while loading and a muted box on error. The object URL
 * is revoked on unmount / dependency change to avoid leaking blob memory.
 */
export function KycEvidenceImage({ caseId, documentId, label }: KycEvidenceImageProps) {
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    setLoading(true);
    setError(false);
    setUrl(null);

    adminKycService
      .fetchDocumentObjectUrl(caseId, documentId)
      .then((created) => {
        objectUrl = created;
        if (active) {
          setUrl(created);
        } else {
          URL.revokeObjectURL(created);
        }
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [caseId, documentId]);

  return (
    <figure className="mb-0">
      {loading ? (
        <div className="d-flex align-items-center justify-content-center border rounded bg-body-tertiary py-5">
          <div className="spinner-border text-secondary" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      ) : error || !url ? (
        <div className="d-flex align-items-center justify-content-center border rounded bg-body-tertiary text-muted small py-5">
          Image unavailable
        </div>
      ) : (
        <img src={url} alt={label} className="img-fluid rounded border" />
      )}
      <figcaption className="text-muted small mt-1">{label}</figcaption>
    </figure>
  );
}
