import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import type {
  ProductsState,
  Product,
  Category,
  ProductFilters,
  PagedResponse,
} from '../../types';
import { productService } from '../../services/productService';

const initialState: ProductsState = {
  items: [],
  selectedProduct: null,
  categories: [],
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 12,
    totalElements: 0,
    totalPages: 0,
  },
  filters: {},
};

export const fetchProducts = createAsyncThunk<
  PagedResponse<Product>,
  { page?: number; size?: number; filters?: ProductFilters }
>('products/fetchProducts', async ({ page = 0, size = 12, filters = {} }, { rejectWithValue }) => {
  try {
    const response = await productService.getProducts(page, size, filters);
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Failed to fetch products';
    return rejectWithValue(message);
  }
});

export const fetchProductById = createAsyncThunk<Product, string>(
  'products/fetchProductById',
  async (productId, { rejectWithValue }) => {
    try {
      const product = await productService.getProductById(productId);
      return product;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch product';
      return rejectWithValue(message);
    }
  }
);

export const fetchCategories = createAsyncThunk<Category[]>(
  'products/fetchCategories',
  async (_, { rejectWithValue }) => {
    try {
      const categories = await productService.getCategories();
      return categories;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch categories';
      return rejectWithValue(message);
    }
  }
);

const productsSlice = createSlice({
  name: 'products',
  initialState,
  reducers: {
    setFilters: (state, action: PayloadAction<ProductFilters>) => {
      state.filters = action.payload;
    },
    clearFilters: (state) => {
      state.filters = {};
    },
    setSelectedProduct: (state, action: PayloadAction<Product | null>) => {
      state.selectedProduct = action.payload;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch Products
      .addCase(fetchProducts.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchProducts.fulfilled, (state, action) => {
        state.isLoading = false;
        state.items = action.payload.content;
        state.pagination = {
          page: action.payload.page,
          size: action.payload.size,
          totalElements: action.payload.totalElements,
          totalPages: action.payload.totalPages,
        };
      })
      .addCase(fetchProducts.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      })
      // Fetch Product by ID
      .addCase(fetchProductById.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchProductById.fulfilled, (state, action) => {
        state.isLoading = false;
        state.selectedProduct = action.payload;
      })
      .addCase(fetchProductById.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      })
      // Fetch Categories
      .addCase(fetchCategories.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(fetchCategories.fulfilled, (state, action) => {
        state.isLoading = false;
        state.categories = action.payload;
      })
      .addCase(fetchCategories.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });
  },
});

export const { setFilters, clearFilters, setSelectedProduct, clearError } = productsSlice.actions;
export default productsSlice.reducer;
