import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { CartState, CartItem, Product } from '../../types';

const loadCartFromStorage = (): CartItem[] => {
  try {
    const stored = localStorage.getItem('cart');
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
};

const saveCartToStorage = (items: CartItem[]): void => {
  localStorage.setItem('cart', JSON.stringify(items));
};

const initialState: CartState = {
  items: loadCartFromStorage(),
  isLoading: false,
  error: null,
};

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    addToCart: (state, action: PayloadAction<{ product: Product; quantity?: number }>) => {
      const { product, quantity = 1 } = action.payload;
      const existingItem = state.items.find((item) => item.product.id === product.id);

      if (existingItem) {
        existingItem.quantity += quantity;
      } else {
        state.items.push({
          id: `cart-${product.id}-${Date.now()}`,
          product,
          quantity,
        });
      }
      saveCartToStorage(state.items);
    },

    removeFromCart: (state, action: PayloadAction<string>) => {
      state.items = state.items.filter((item) => item.id !== action.payload);
      saveCartToStorage(state.items);
    },

    updateQuantity: (state, action: PayloadAction<{ itemId: string; quantity: number }>) => {
      const { itemId, quantity } = action.payload;
      const item = state.items.find((i) => i.id === itemId);

      if (item) {
        if (quantity <= 0) {
          state.items = state.items.filter((i) => i.id !== itemId);
        } else {
          item.quantity = quantity;
        }
      }
      saveCartToStorage(state.items);
    },

    incrementQuantity: (state, action: PayloadAction<string>) => {
      const item = state.items.find((i) => i.id === action.payload);
      if (item) {
        item.quantity += 1;
        saveCartToStorage(state.items);
      }
    },

    decrementQuantity: (state, action: PayloadAction<string>) => {
      const item = state.items.find((i) => i.id === action.payload);
      if (item) {
        if (item.quantity > 1) {
          item.quantity -= 1;
        } else {
          state.items = state.items.filter((i) => i.id !== action.payload);
        }
        saveCartToStorage(state.items);
      }
    },

    clearCart: (state) => {
      state.items = [];
      localStorage.removeItem('cart');
    },

    setCartError: (state, action: PayloadAction<string>) => {
      state.error = action.payload;
    },

    clearCartError: (state) => {
      state.error = null;
    },
  },
});

// Selectors
export const selectCartItems = (state: { cart: CartState }) => state.cart.items;

export const selectCartItemCount = (state: { cart: CartState }) =>
  state.cart.items.reduce((total, item) => total + item.quantity, 0);

export const selectCartTotal = (state: { cart: CartState }) =>
  state.cart.items.reduce((total, item) => total + item.product.price * item.quantity, 0);

export const selectCartItemById = (state: { cart: CartState }, itemId: string) =>
  state.cart.items.find((item) => item.id === itemId);

export const {
  addToCart,
  removeFromCart,
  updateQuantity,
  incrementQuantity,
  decrementQuantity,
  clearCart,
  setCartError,
  clearCartError,
} = cartSlice.actions;

export default cartSlice.reducer;
