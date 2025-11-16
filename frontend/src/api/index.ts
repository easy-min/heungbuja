const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

function getAccessToken(): string | null {
  return localStorage.getItem('userAccessToken');
}

function normalizeHeaders(h?: HeadersInit): Record<string, string> {
  if (!h) return {};
  if (h instanceof Headers) return Object.fromEntries(h.entries());
  if (Array.isArray(h)) return Object.fromEntries(h);
  return h; // already Record<string, string>
}

async function request<T>(
  path: string,
  options: RequestInit & { auth?: boolean } = {}
): Promise<T> {
  const url = path.startsWith('http') ? path : `${API_BASE}${path}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...normalizeHeaders(options.headers),
  };

  if (options.auth) {
    const token = getAccessToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    credentials: 'include',
    ...options,
    headers, // OK: Record<string,string> is a valid HeadersInit
  });

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    const message =
      (data && typeof data === 'object' && 'message' in data
        ? String((data as { message?: string }).message)
        : undefined) || `API 요청 실패 (${res.status})`;

    throw new Error(message);
  }

  return data as T;
}

export const api = {
  get: <T>(path: string, auth = false) =>
    request<T>(path, { method: 'GET', auth }),

  post: <T, B = unknown>(path: string, body?: B, auth = false) =>
    request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
      auth,
    }),

  put: <T, B = unknown>(path: string, body?: B, auth = false) =>
    request<T>(path, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
      auth,
    }),

  delete: <T>(path: string, auth = false) =>
    request<T>(path, { method: 'DELETE', auth }),
};

export default api;
