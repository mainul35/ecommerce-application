// TypeScript interfaces for E-Commerce Platform

// User Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  createdAt: string;
  updatedAt: string;
}

export type UserRole = 'CUSTOMER' | 'ADMIN' | 'VENDOR';

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface AuthResponse {
  user: User;
  token: string;
}

// Product Types
export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  originalPrice?: number;
  imageUrl: string;
  images: string[];
  category: Category;
  attributes: Record<string, unknown>;
  stock: number;
  sku: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  /** Populated when an active discount applies to this product. */
  discountedPrice?: number;
  discountPercent?: number;
  discountName?: string;
  discountEndsAt?: string;
}

// Discount Types
export type DiscountType = 'PERCENTAGE' | 'FIXED';
export type DiscountScope = 'PRODUCT' | 'CATEGORY' | 'SITEWIDE';

export interface Discount {
  id: string;
  name: string;
  type: DiscountType;
  value: number;
  scope: DiscountScope;
  scopeTargetId?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

// Discount template (reusable admin-saved blueprint, instantiated into a Discount on demand)
export interface DiscountTemplate {
  id: string;
  name: string;
  description?: string | null;
  type: DiscountType;
  value: number;
  defaultDurationDays?: number | null;
  createdAt: string;
  updatedAt: string;
}

// Coupon Types
export type CouponType = 'PERCENTAGE' | 'FIXED' | 'FREE_SHIPPING';

export interface Coupon {
  id: string;
  code: string;
  name?: string;
  type: CouponType;
  value?: number | null;
  minOrderAmount?: number | null;
  maxUses?: number | null;
  maxUsesPerUser?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CouponValidationResponse {
  valid: boolean;
  message?: string | null;
  code?: string | null;
  type?: CouponType | null;
  subtotal: number;
  discountAmount: number;
  finalAmount: number;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  description?: string;
  parentId?: string;
  imageUrl?: string;
}

export interface ProductsState {
  items: Product[];
  selectedProduct: Product | null;
  categories: Category[];
  isLoading: boolean;
  error: string | null;
  pagination: PaginationState;
  filters: ProductFilters;
}

export interface ProductFilters {
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  search?: string;
  sortBy?: 'price_asc' | 'price_desc' | 'name_asc' | 'name_desc' | 'newest';
}

// Cart Types
export interface CartItem {
  id: string;
  product: Product;
  quantity: number;
}

export interface CartState {
  items: CartItem[];
  isLoading: boolean;
  error: string | null;
}

// Order Types
export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  status: OrderStatus;
  totalAmount: number;
  shippingAddress: Address;
  billingAddress: Address;
  paymentMethod: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  productImage: string;
  quantity: number;
  price: number;
}

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface Address {
  id?: string;
  firstName: string;
  lastName: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phone: string;
}

// Pagination
export interface PaginationState {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface ApiError {
  status: number;
  message: string;
  errors?: Record<string, string[]>;
  timestamp: string;
  path: string;
}

// UI State
export interface UIState {
  isSidebarOpen: boolean;
  isCartOpen: boolean;
  notifications: Notification[];
}

export interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  duration?: number;
}
