interface LoadingProps {
  fullScreen?: boolean;
  message?: string;
}

export function Loading({ fullScreen = false, message = 'Loading...' }: LoadingProps) {
  const containerClass = fullScreen
    ? 'loading-spinner vh-100'
    : 'loading-spinner';

  return (
    <div className={containerClass}>
      <div className="text-center">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">{message}</span>
        </div>
        <p className="mt-3 text-muted">{message}</p>
      </div>
    </div>
  );
}
