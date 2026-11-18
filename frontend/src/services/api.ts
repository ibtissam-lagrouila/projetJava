import { User, Badges } from '../types';

class ApiService {
  private getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
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

  async getLabDashboard(labId?: string): Promise<import('../types').LabDashboardData | null> {
    try {
      const headers = this.getHeaders();
      if (labId) {
        headers['X-Lab-Id'] = labId;
      }
      const res = await fetch('/api/lab/dashboard', {
        headers,
        credentials: 'include',
      });
      if (!res.ok) {
        if (res.status === 403) {
          const body = await res.json().catch(() => ({}));
          return body as any;
        }
        return null;
      }
      return await res.json();
    } catch {
      return null;
    }
  }

  async shortlistCandidates(applicationIds: string[], confirmed: boolean, labId?: string): Promise<{ message: string; shortlistedCount: number } | null> {
    try {
      const headers = this.getHeaders();
      if (labId) {
        headers['X-Lab-Id'] = labId;
      }
      const res = await fetch('/api/lab/shortlist', {
        method: 'POST',
        headers,
        credentials: 'include',
        body: JSON.stringify({ applicationIds, confirmed }),
      });
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
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

