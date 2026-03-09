"use client";
import { create } from "zustand";
import type { User } from "@/types";
import { tokenStorage } from "@/lib/api/client";

interface AuthState {
  user:        User | null;
  isLoading:   boolean;
  isLoggedIn:  boolean;
  setUser:     (user: User) => void;
  setTokens:   (access: string, refresh: string) => void;
  logout:      () => void;
  initFromStorage: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user:       null,
  isLoading:  true,
  isLoggedIn: false,

  setUser: (user) => {
    tokenStorage.setUser(user);
    set({ user, isLoggedIn: true, isLoading: false });
  },

  setTokens: (access, refresh) => {
    tokenStorage.set(access, refresh);
  },

  logout: () => {
    tokenStorage.clear();
    set({ user: null, isLoggedIn: false });
    window.location.href = "/auth/login";
  },

  initFromStorage: () => {
    const token = tokenStorage.get();
    const user  = tokenStorage.getUser();
    if (token && user) {
      set({ user, isLoggedIn: true, isLoading: false });
    } else {
      set({ isLoading: false });
    }
  },
}));
