import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store';
import { fetchProducts, fetchCategories, setFilters } from '../../store/slices/productsSlice';
import { ProductCard, Loading, EmptyState, Pagination } from '../../components/common';
import type { ProductFilters } from '../../types';

export function ProductsPage() {
  const dispatch = useAppDispatch();
  const [searchParams, setSearchParams] = useSearchParams();
  const { items: products, categories, isLoading, pagination, filters } = useAppSelector(
    (state) => state.products
  );

  const currentPage = parseInt(searchParams.get('page') || '0', 10);
  const categoryId = searchParams.get('category') || undefined;

  useEffect(() => {
    dispatch(fetchCategories());
  }, [dispatch]);

  useEffect(() => {
    const newFilters: ProductFilters = {
      categoryId,
      search: searchParams.get('search') || undefined,
      sortBy: (searchParams.get('sort') as ProductFilters['sortBy']) || undefined,
    };
    dispatch(setFilters(newFilters));
    dispatch(fetchProducts({ page: currentPage, size: 12, filters: newFilters }));
  }, [dispatch, currentPage, categoryId, searchParams]);

  const handlePageChange = (page: number) => {
    searchParams.set('page', page.toString());
    setSearchParams(searchParams);
  };

  const handleCategoryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    if (value) {
      searchParams.set('category', value);
    } else {
      searchParams.delete('category');
    }
    searchParams.set('page', '0');
    setSearchParams(searchParams);
  };

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    if (value) {
      searchParams.set('sort', value);
    } else {
      searchParams.delete('sort');
    }
    setSearchParams(searchParams);
  };

  return (
    <div className="container py-5">
      <div className="row">
        <div className="col-12 col-md-3 mb-4 mb-md-0">
          <div className="card">
            <div className="card-body">
              <h5 className="card-title mb-4">Filters</h5>

              <div className="mb-4">
                <label className="form-label">Category</label>
                <select
                  className="form-select"
                  value={filters.categoryId || ''}
                  onChange={handleCategoryChange}
                >
                  <option value="">All Categories</option>
                  {categories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="mb-4">
                <label className="form-label">Sort By</label>
                <select
                  className="form-select"
                  value={filters.sortBy || ''}
                  onChange={handleSortChange}
                >
                  <option value="">Default</option>
                  <option value="price_asc">Price: Low to High</option>
                  <option value="price_desc">Price: High to Low</option>
                  <option value="name_asc">Name: A to Z</option>
                  <option value="name_desc">Name: Z to A</option>
                  <option value="newest">Newest First</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <div className="col-12 col-md-9">
          <div className="d-flex justify-content-between align-items-center mb-4">
            <h2 className="mb-0">Products</h2>
            <span className="text-muted">
              {pagination.totalElements} products found
            </span>
          </div>

          {isLoading ? (
            <Loading />
          ) : products.length === 0 ? (
            <EmptyState
              icon="bi-search"
              title="No products found"
              description="Try adjusting your filters or search terms"
              actionLabel="Clear Filters"
              onAction={() => setSearchParams({})}
            />
          ) : (
            <>
              <div className="row g-4">
                {products.map((product) => (
                  <div key={product.id} className="col-6 col-lg-4">
                    <ProductCard product={product} />
                  </div>
                ))}
              </div>

              <div className="mt-5">
                <Pagination
                  currentPage={pagination.page}
                  totalPages={pagination.totalPages}
                  onPageChange={handlePageChange}
                />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
