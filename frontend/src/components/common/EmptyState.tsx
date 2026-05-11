import { Link } from 'react-router-dom';

interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
  actionLabel?: string;
  actionLink?: string;
  onAction?: () => void;
}

export function EmptyState({
  icon = 'bi-inbox',
  title,
  description,
  actionLabel,
  actionLink,
  onAction,
}: EmptyStateProps) {
  return (
    <div className="empty-state">
      <i className={`bi ${icon} empty-state-icon`}></i>
      <h4 className="empty-state-title">{title}</h4>
      {description && <p className="empty-state-text">{description}</p>}
      {actionLabel && (
        actionLink ? (
          <Link to={actionLink} className="btn btn-primary">
            {actionLabel}
          </Link>
        ) : (
          <button className="btn btn-primary" onClick={onAction}>
            {actionLabel}
          </button>
        )
      )}
    </div>
  );
}
