import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { searchService } from '../../services/searchService';
import { ProductCard } from '../../components/common/ProductCard';
import type { Product } from '../../types';

export function SearchPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const query = searchParams.get('q') ?? '';

  const [inputValue, setInputValue] = useState(query);
  const [results, setResults] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const pageSize = 20;
  const totalPages = Math.ceil(total / pageSize);

  useEffect(() => {
    setInputValue(query);
    setPage(0);
  }, [query]);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setTotal(0);
      setSearched(false);
      return;
    }
    setLoading(true);
    searchService.search(query, page, pageSize)
      .then((data) => {
        setResults(data.content);
        setTotal(data.totalElements);
        setSearched(true);
      })
      .catch(() => {
        setResults([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [query, page]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim()) {
      navigate(`/search?q=${encodeURIComponent(inputValue.trim())}`);
    }
  };

  return (
    <div className="container py-4">
      {/* Search bar */}
      <form onSubmit={handleSearch} className="mb-4">
        <div className="input-group input-group-lg" style={{ maxWidth: '640px' }}>
          <input
            type="search"
            className="form-control"
            placeholder="Search products, brands, attributes…"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            aria-label="Search"
            autoFocus
          />
          <button className="btn btn-primary px-4" type="submit">
            <i className="bi bi-search me-2"></i>Search
          </button>
        </div>
        {query && (
          <p className="text-muted small mt-2 mb-0">
            {loading ? 'Searching…' : searched ? `${total.toLocaleString()} result${total !== 1 ? 's' : ''} for "${query}"` : ''}
          </p>
        )}
      </form>

      {/* Loading skeleton */}
      {loading && (
        <div className="row g-3">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="col-6 col-sm-4 col-md-3">
              <div className="card h-100 placeholder-glow">
                <div className="placeholder" style={{ height: '180px' }}></div>
                <div className="card-body">
                  <div className="placeholder col-8 mb-2"></div>
                  <div className="placeholder col-5"></div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Empty state */}
      {!loading && searched && results.length === 0 && (
        <div className="text-center py-5">
          <i className="bi bi-search text-secondary d-block mb-3" style={{ fontSize: '3rem' }}></i>
          <h5 className="fw-bold mb-2">No results for "{query}"</h5>
          <p className="text-muted mb-0">
            Try different keywords, check spelling, or use broader terms.
          </p>
        </div>
      )}

      {/* No query state */}
      {!loading && !query && (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-search d-block mb-3" style={{ fontSize: '3rem' }}></i>
          <p className="mb-0">Type something above to search across all products.</p>
        </div>
      )}

      {/* Results grid */}
      {!loading && results.length > 0 && (
        <>
          <div className="row g-3">
            {results.map((product) => (
              <div key={product.id} className="col-6 col-sm-4 col-md-3">
                <ProductCard product={product} />
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <nav className="mt-4 d-flex justify-content-center" aria-label="Search results pages">
              <ul className="pagination">
                <li className={`page-item ${page === 0 ? 'disabled' : ''}`}>
                  <button className="page-link" onClick={() => setPage(page - 1)} disabled={page === 0}>
                    <i className="bi bi-chevron-left"></i>
                  </button>
                </li>
                {Array.from({ length: totalPages }).map((_, i) => (
                  <li key={i} className={`page-item ${i === page ? 'active' : ''}`}>
                    <button className="page-link" onClick={() => setPage(i)}>{i + 1}</button>
                  </li>
                ))}
                <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
                  <button className="page-link" onClick={() => setPage(page + 1)} disabled={page >= totalPages - 1}>
                    <i className="bi bi-chevron-right"></i>
                  </button>
                </li>
              </ul>
            </nav>
          )}
        </>
      )}
    </div>
  );
}
