import { useEffect, useRef, useState } from 'react';

interface CameraCaptureProps {
  label: string;
  instruction: string;
  onCapture: (blob: Blob) => void;
  /** Object URL of the last captured frame, owned/revoked by the parent. */
  capturedPreviewUrl?: string | null;
}

const MIN_WIDTH = 640;
const MIN_HEIGHT = 480;

/**
 * In-browser selfie capture. Camera-only by deliberate policy — there is no
 * file-input fallback. Draws the live frame to a canvas and emits a JPEG Blob.
 */
export function CameraCapture({
  label,
  instruction,
  onCapture,
  capturedPreviewUrl,
}: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const [isStarting, setIsStarting] = useState(false);
  const [isLive, setIsLive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stopStream = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsLive(false);
  };

  const startCamera = async () => {
    setError(null);
    setIsStarting(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1280 } },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setIsLive(true);
    } catch (e) {
      const name = e instanceof DOMException ? e.name : '';
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        setError(
          'Camera access was blocked. Please allow camera permission in your browser and try again.'
        );
      } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
        setError('No camera was found on this device. A camera is required to continue.');
      } else {
        setError(
          e instanceof Error ? e.message : 'Could not start the camera. Please try again.'
        );
      }
      stopStream();
    } finally {
      setIsStarting(false);
    }
  };

  const handleCapture = () => {
    const video = videoRef.current;
    if (!video) return;

    const width = video.videoWidth;
    const height = video.videoHeight;
    if (width < MIN_WIDTH || height < MIN_HEIGHT) {
      setError(
        `The captured image is too small (${width}×${height}). It must be at least ${MIN_WIDTH}×${MIN_HEIGHT}. Please move closer to a better camera and retry.`
      );
      return;
    }

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      setError('Could not process the captured image. Please try again.');
      return;
    }
    ctx.drawImage(video, 0, 0, width, height);
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          setError('Could not process the captured image. Please try again.');
          return;
        }
        setError(null);
        stopStream();
        onCapture(blob);
      },
      'image/jpeg',
      0.9
    );
  };

  // Stop the camera when the component unmounts.
  useEffect(() => {
    return () => {
      stopStream();
    };
  }, []);

  return (
    <div className="card h-100">
      <div className="card-body d-flex flex-column">
        <h6 className="card-title mb-1">{label}</h6>
        <p className="text-muted small mb-3">{instruction}</p>

        {error && <div className="alert alert-danger py-2 small">{error}</div>}

        <div className="ratio ratio-4x3 bg-dark rounded mb-3 overflow-hidden">
          {capturedPreviewUrl && !isLive ? (
            <img
              src={capturedPreviewUrl}
              alt={`${label} preview`}
              className="w-100 h-100 object-fit-cover"
            />
          ) : (
            <video
              ref={videoRef}
              className="w-100 h-100 object-fit-cover"
              playsInline
              muted
            />
          )}
        </div>

        <div className="mt-auto d-grid gap-2">
          {isLive ? (
            <button type="button" className="btn btn-primary" onClick={handleCapture}>
              <i className="bi bi-camera me-1"></i>Capture
            </button>
          ) : (
            <button
              type="button"
              className="btn btn-outline-primary"
              onClick={startCamera}
              disabled={isStarting}
            >
              {isStarting
                ? 'Starting camera…'
                : capturedPreviewUrl
                  ? 'Retake'
                  : 'Start camera'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
