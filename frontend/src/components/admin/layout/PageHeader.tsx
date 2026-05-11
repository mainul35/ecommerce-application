import { Link } from 'react-router-dom';

export interface Crumb {
  label: string;
  to?: string;
}

interface PageHeaderProps {
  title: string;
  crumbs?: Crumb[];
  actions?: React.ReactNode;
}

/**
 * AdminLTE 4 page header (content-header block). Used at the top of every
 * admin page so they share a consistent title + breadcrumb pattern.
 */
export function PageHeader({ title, crumbs = [], actions }: PageHeaderProps) {
  return (
    <div className="app-content-header pb-3">
      <div className="row align-items-center">
        <div className="col-sm-7">
          <h1 className="h3 mb-0">{title}</h1>
        </div>
        <div className="col-sm-5">
          <div className="d-flex justify-content-sm-end align-items-center gap-3">
            {crumbs.length > 0 && (
              <ol className="breadcrumb mb-0">
                {crumbs.map((c, i) => {
                  const isLast = i === crumbs.length - 1;
                  return (
                    <li
                      key={`${c.label}-${i}`}
                      className={`breadcrumb-item ${isLast ? 'active' : ''}`}
                    >
                      {c.to && !isLast ? <Link to={c.to}>{c.label}</Link> : c.label}
                    </li>
                  );
                })}
              </ol>
            )}
            {actions}
          </div>
        </div>
      </div>
    </div>
  );
}
