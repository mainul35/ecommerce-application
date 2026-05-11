import type { User, UserRole } from '../types';

/**
 * Single source of truth for role/permission checks on the frontend.
 *
 * Pages and components MUST NOT inline `user.role === 'ADMIN'` style checks.
 * Use the helpers below so future permission edits (renaming roles, adding
 * fine-grained capabilities, role hierarchies) happen in this one file.
 *
 * Mirrors the backend's authorization model in
 * com.ecommerce.security.AccessRules - keep them in sync.
 */

export const hasRole = (user: User | null, role: UserRole): boolean =>
  !!user && user.role === role;

export const isAdmin = (user: User | null): boolean => hasRole(user, 'ADMIN');

export const isManager = (user: User | null): boolean => hasRole(user, 'MANAGER');

export const isVendor = (user: User | null): boolean => hasRole(user, 'VENDOR');

/** Any staff role - can sign in to the admin console. */
export const canAccessAdmin = (user: User | null): boolean =>
  isAdmin(user) || isManager(user);

// ---------- Capability checks (admin console actions) ----------

/** Products: admin and manager can both manage. */
export const canManageProducts = (user: User | null): boolean =>
  isAdmin(user) || isManager(user);

/** Categories: admin and manager can both manage. */
export const canManageCategories = (user: User | null): boolean =>
  isAdmin(user) || isManager(user);

/** Discounts / coupons / templates / orders / customers: admin only. */
export const canManagePromotions = (user: User | null): boolean => isAdmin(user);
export const canManageOrders = (user: User | null): boolean => isAdmin(user);

/** Provisioning new managers, blocking them: admin only. */
export const canManageManagers = (user: User | null): boolean => isAdmin(user);
