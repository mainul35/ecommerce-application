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

export const isVendor = (user: User | null): boolean => hasRole(user, 'VENDOR');

export const canAccessAdmin = (user: User | null): boolean => isAdmin(user);

export const canManageProducts = (user: User | null): boolean =>
  isAdmin(user) || isVendor(user);

export const canManageCategories = (user: User | null): boolean => isAdmin(user);
