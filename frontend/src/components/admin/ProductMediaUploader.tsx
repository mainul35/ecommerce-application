import { useEffect, useRef, useState } from 'react';
import type { ProductMedia } from '../../types';
import {
  adminProductMediaService,
  mediaUrl,
} from '../../services/admin/adminProductMediaService';

interface Props {
  productId: string;
}

const ACCEPTED = 'image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime';

export function ProductMediaUploader({ productId }: Props) {
  const [items, setItems] = useState<ProductMedia[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const load = () => {
    adminProductMediaService
      .list(productId)
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  };

  useEffect(() => {
    load();
  }, [productId]);

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    setError(null);
    const fileArray = Array.from(files);
    try {
      for (let i = 0; i < fileArray.length; i++) {
        setUploadProgress(`Uploading ${i + 1} of ${fileArray.length}…`);
        await adminProductMediaService.upload(productId, fileArray[i]);
      }
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setUploading(false);
      setUploadProgress(null);
      // Reset input so the same file can be re-uploaded after deletion
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const handleDelete = async (item: ProductMedia) => {
    if (!window.confirm(`Remove "${item.originalName}"?`)) return;
    setError(null);
    try {
      await adminProductMediaService.delete(productId, item.id);
      setItems((prev) => prev.filter((m) => m.id !== item.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed');
    }
  };

  return (
    <div>
      <div className="d-flex align-items-center justify-content-between mb-2">
        <h6 className="mb-0">
          <i className="bi bi-images me-1"></i>Photos &amp; Videos
        </h6>
        <label className={`btn btn-sm btn-outline-primary mb-0${uploading ? ' disabled' : ''}`}>
          <i className="bi bi-cloud-upload me-1"></i>
          {uploading ? uploadProgress : 'Upload files'}
          <input
            ref={inputRef}
            type="file"
            multiple
            accept={ACCEPTED}
            className="d-none"
            disabled={uploading}
            onChange={(e) => handleFiles(e.target.files)}
          />
        </label>
      </div>

      <div className="form-text mb-2">
        Accepted: JPEG, PNG, WEBP, GIF (max 10 MB) · MP4, WEBM, MOV (max 100 MB).
        Multiple files can be selected at once.
      </div>

      {error && (
        <div className="alert alert-danger py-2 small mb-2">{error}</div>
      )}

      {items.length === 0 ? (
        <p className="text-muted small mb-0">No media uploaded yet.</p>
      ) : (
        <div className="row g-2">
          {items.map((item) => (
            <div key={item.id} className="col-6 col-sm-4 col-md-3">
              <div className="position-relative border rounded overflow-hidden">
                <div className="ratio ratio-1x1 bg-light">
                  {item.mediaType === 'IMAGE' ? (
                    <img
                      src={mediaUrl(item.url)}
                      alt={item.originalName}
                      className="w-100 h-100 object-fit-cover"
                    />
                  ) : (
                    <video
                      src={mediaUrl(item.url)}
                      className="w-100 h-100 object-fit-cover"
                      preload="metadata"
                    />
                  )}
                </div>

                {item.mediaType === 'VIDEO' && (
                  <span className="badge bg-dark position-absolute bottom-0 start-0 m-1">
                    <i className="bi bi-play-fill me-1"></i>Video
                  </span>
                )}

                <button
                  type="button"
                  className="btn btn-danger btn-sm position-absolute top-0 end-0 m-1 p-0 lh-1"
                  style={{ width: '22px', height: '22px' }}
                  title={`Remove ${item.originalName}`}
                  onClick={() => handleDelete(item)}
                >
                  <i className="bi bi-x"></i>
                </button>
              </div>

              <p className="text-muted small mt-1 mb-0 text-truncate" title={item.originalName}>
                {item.originalName}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
