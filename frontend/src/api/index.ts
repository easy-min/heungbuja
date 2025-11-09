const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

function getAccessToken(): string | null {
  return localStorage.getItem('accessToken');
}

async function request<T>(
  path: string,
  options: RequestInit & { auth?: boolean } = {}
): Promise<T> {
  const url = path.startsWith('http') ? path : `${API_BASE}${path}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  if (options.auth) {
    const token = getAccessToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    credentials: 'include',
    ...options,
    headers,
  });

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    const message =
      (data && data.message) || `API 요청 실패 (${res.status})`;
    throw new Error(message);
  }

  return data as T;
}

export const api = {
  get:  <T>(path: string, auth = false) => request<T>(path, { method: 'GET', auth }),
  post: <T>(path: string, body?: any, auth = false) =>
    request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
      auth,
    }),
  put:  <T>(path: string, body?: any, auth = false) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body), auth }),
  delete: <T>(path: string, auth = false) =>
    request<T>(path, { method: 'DELETE', auth }),
};

export default api;
