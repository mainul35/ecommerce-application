// TypeScript interfaces for E-Commerce Platform

// User Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  isActive?: boolean;
  createdAt: string;
  updatedAt: string;
}

export type UserRole = 'CUSTOMER' | 'ADMIN' | 'MANAGER' | 'VENDOR';

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
  /** Region restriction (admin-only field; empty/undefined = available globally). */
  regionIds?: string[];
  /** All media items for this product, ordered by sort_order. */
  media?: ProductMedia[];
}

// Product Media
export interface ProductMedia {
  id: string;
  productId: string;
  mediaType: 'IMAGE' | 'VIDEO';
  /** Relative path served by the backend, e.g. /uploads/products/{id}/uuid.jpg */
  url: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
  createdAt: string;
}

// Product Review
export interface ProductReview {
  id: string;
  productId: string;
  reviewerName: string;
  rating: number;
  title?: string;
  body?: string;
  createdAt: string;
}

// Currency / Region Types
export interface Currency {
  code: string;
  name: string;
  symbol: string;
  exchangeRate: number;
  isBase: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Region {
  id: string;
  name: string;
  countryCode: string;
  currencyCode: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
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
  paymentStatus?: PaymentStatus;
  subtotalAmount?: number;
  couponCode?: string | null;
  couponDiscountAmount?: number | null;
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
  /** Seller snapshot; null/undefined = platform-owned item. */
  sellerId?: string | null;
  /** Units already refunded via approved returns. */
  returnedQuantity?: number;
}

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export type PaymentStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

// ---- Marketplace escrow / buyer protection ----

export type EscrowStatus = 'HELD' | 'RELEASED' | 'DISPUTED' | 'REFUNDED';

export interface EscrowTransaction {
  id: string;
  orderId: string;
  sellerId?: string | null;
  sellerName?: string;
  amount: number;
  refundedAmount: number;
  currencyCode: string;
  status: EscrowStatus;
  gatewayId?: string | null;
  /** Auto-release deadline; set once the order is DELIVERED. */
  holdUntil?: string | null;
  releasedAt?: string | null;
  createdAt: string;
}

export type DisputeStatus =
  | 'OPEN'
  | 'ESCALATED'
  | 'RESOLVED_RELEASED'
  | 'RESOLVED_REFUNDED'
  | 'WITHDRAWN';

export interface Dispute {
  id: string;
  escrowTransactionId: string;
  orderId: string;
  orderItemId?: string | null;
  openedByUserId: string;
  openedByName?: string;
  sellerId?: string | null;
  sellerName?: string;
  escrowAmount: number;
  escrowRefundedAmount: number;
  reason: string;
  status: DisputeStatus;
  escalatedAt?: string | null;
  resolvedByUserId?: string | null;
  resolutionNote?: string | null;
  refundAmount?: number | null;
  resolvedAt?: string | null;
  createdAt: string;
}

export type DisputeAuthorRole = 'BUYER' | 'SELLER' | 'STAFF';
export type DisputeAttachmentType = 'IMAGE' | 'VIDEO';

export interface DisputeAttachment {
  id: string;
  url: string;
  originalName?: string;
  contentType?: string;
  attachmentType: DisputeAttachmentType;
  sizeBytes?: number;
}

export interface DisputeMessage {
  id: string;
  disputeId: string;
  senderUserId: string;
  senderName?: string;
  authorRole: DisputeAuthorRole;
  body?: string | null;
  attachments: DisputeAttachment[];
  createdAt: string;
}

// ---- Wallet ----

export type WalletTransactionType = 'CREDIT' | 'DEBIT';
export type WalletReferenceType =
  | 'ESCROW_RELEASE'
  | 'DISPUTE_REFUND'
  | 'RETURN_REFUND'
  | 'ADJUSTMENT'
  | 'WITHDRAWAL';

export interface Wallet {
  id: string;
  userId: string;
  balance: number;
  currencyCode: string;
  updatedAt?: string;
}

export interface WalletTransaction {
  id: string;
  type: WalletTransactionType;
  amount: number;
  balanceAfter: number;
  referenceType: WalletReferenceType;
  referenceId?: string | null;
  description?: string;
  createdAt: string;
}

// ---- Returns ----

export type ReturnStatus = 'REQUESTED' | 'REFUNDED' | 'REJECTED' | 'CANCELLED';
export type RefundDestination = 'GATEWAY' | 'WALLET';

export interface ReturnRequest {
  id: string;
  orderId: string;
  orderItemId: string;
  escrowTransactionId: string;
  productName?: string;
  productImage?: string;
  quantity: number;
  reason: string;
  status: ReturnStatus;
  refundAmount: number;
  refundDestination?: RefundDestination | null;
  rejectionReason?: string | null;
  resolvedAt?: string | null;
  createdAt: string;
}

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
