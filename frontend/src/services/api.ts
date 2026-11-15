import { User, Badges } from '../types';

class ApiService {
  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    const token = localStorage.getItem('fitwell_token');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  async getCurrentUser(): Promise<User | null> {
    try {
      const res = await fetch('/api/auth/me', {
        headers: this.getHeaders(),
        credentials: 'include',
      });
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
    }
  }

  async getBadges(): Promise<Badges> {
    try {
      const res = await fetch('/api/badges', {
        headers: this.getHeaders(),
        credentials: 'include',
      });
      if (!res.ok) return {};
      return await res.json();
    } catch {
      return {};
    }
  }

  async logout(): Promise<void> {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: this.getHeaders(),
        credentials: 'include',
      });
    } finally {
      localStorage.removeItem('fitwell_token');
    }
  }
}

export const api = new ApiService();

