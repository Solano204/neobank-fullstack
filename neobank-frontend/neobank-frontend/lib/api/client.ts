import axios, { AxiosInstance, AxiosRequestConfig, AxiosError, InternalAxiosRequestConfig } from "axios";

// ─── Token storage ────────────────────────────────────────────────────────────
export const tokenStorage = {
  get:        () => (typeof window !== "undefined" ? localStorage.getItem("access_token")  : null),
  getRefresh: () => (typeof window !== "undefined" ? localStorage.getItem("refresh_token") : null),
  set: (access: string, refresh: string) => {
    localStorage.setItem("access_token",  access);
    localStorage.setItem("refresh_token", refresh);
  },
  clear: () => {
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("user");
  },
  setUser: (user: object) => localStorage.setItem("user", JSON.stringify(user)),
  getUser: () => {
    const u = localStorage.getItem("user");
    return u ? JSON.parse(u) : null;
  },
};

// ─── Factory: create an Axios instance with interceptors ─────────────────────
function createInstance(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
    headers: { "Content-Type": "application/json" },
    timeout: 30000,
  });

  // REQUEST: attach Bearer token
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.get();
    if (token && config.headers) {
      config.headers["Authorization"] = `Bearer ${token}`;
    }
    return config;
  });

  // RESPONSE: auto-refresh on 401
  let isRefreshing = false;
  let queue: Array<{ resolve: (t: string) => void; reject: (e: unknown) => void }> = [];

  const processQueue = (error: unknown, token: string | null) => {
    queue.forEach(p => (error ? p.reject(error) : p.resolve(token!)));
    queue = [];
  };

  instance.interceptors.response.use(
    res => res,
    async (error: AxiosError) => {
      const original = error.config as AxiosRequestConfig & { _retry?: boolean };

      if (error.response?.status === 401 && !original._retry) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            queue.push({ resolve, reject });
          })
            .then(token => {
              if (original.headers) original.headers["Authorization"] = `Bearer ${token}`;
              return instance(original);
            })
            .catch(e => Promise.reject(e));
        }

        original._retry  = true;
        isRefreshing     = true;

        const refreshToken = tokenStorage.getRefresh();

        if (!refreshToken) {
          tokenStorage.clear();
          if (typeof window !== "undefined") window.location.href = "/auth/login";
          return Promise.reject(error);
        }

        try {
          const { data } = await axios.post(`${baseURL}/api/auth/refresh-token`, { refreshToken });
          const newAccess   = data.data.accessToken;
          const newRefresh  = data.data.refreshToken || refreshToken;
          tokenStorage.set(newAccess, newRefresh);
          instance.defaults.headers.common["Authorization"] = `Bearer ${newAccess}`;
          processQueue(null, newAccess);
          if (original.headers) original.headers["Authorization"] = `Bearer ${newAccess}`;
          return instance(original);
        } catch (refreshError) {
          processQueue(refreshError, null);
          tokenStorage.clear();
          if (typeof window !== "undefined") window.location.href = "/auth/login";
          return Promise.reject(refreshError);
        } finally {
          isRefreshing = false;
        }
      }

      return Promise.reject(error);
    }
  );

  return instance;
}

// ─── Two instances: Spring Boot EC2 + Lambda API Gateway ─────────────────────
const API_URL    = process.env.NEXT_PUBLIC_API_URL    || "http://localhost:8080";
const LAMBDA_URL = process.env.NEXT_PUBLIC_LAMBDA_URL || "";

export const apiClient    = createInstance(API_URL);
export const lambdaClient = createInstance(LAMBDA_URL);

// ─── Convenience helpers ──────────────────────────────────────────────────────
export const api = {
  // Spring Boot (EC2)
  get:    <T>(path: string, config?: AxiosRequestConfig)              => apiClient.get<T>(path, config).then(r => r.data),
  post:   <T>(path: string, data?: unknown, config?: AxiosRequestConfig) => apiClient.post<T>(path, data, config).then(r => r.data),
  put:    <T>(path: string, data?: unknown, config?: AxiosRequestConfig) => apiClient.put<T>(path, data, config).then(r => r.data),
  patch:  <T>(path: string, data?: unknown, config?: AxiosRequestConfig) => apiClient.patch<T>(path, data, config).then(r => r.data),
  delete: <T>(path: string, config?: AxiosRequestConfig)              => apiClient.delete<T>(path, config).then(r => r.data),

  // Lambda (API Gateway)
  lambda: {
    get:    <T>(path: string, config?: AxiosRequestConfig)               => lambdaClient.get<T>(path, config).then(r => r.data),
    post:   <T>(path: string, data?: unknown, config?: AxiosRequestConfig) => lambdaClient.post<T>(path, data, config).then(r => r.data),
    put:    <T>(path: string, data?: unknown, config?: AxiosRequestConfig) => lambdaClient.put<T>(path, data, config).then(r => r.data),
    delete: <T>(path: string, config?: AxiosRequestConfig)               => lambdaClient.delete<T>(path, config).then(r => r.data),
  },
};

// ─── Error helper ─────────────────────────────────────────────────────────────
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || error.message || "Error desconocido";
  }
  if (error instanceof Error) return error.message;
  return "Error desconocido";
}
