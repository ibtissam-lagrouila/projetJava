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
    const headers = this.getHeaders();
    if (labId) {
      headers['X-Lab-Id'] = labId;
    }
    const res = await fetch('/api/lab/dashboard', {
      headers,
      credentials: 'include',
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      if (data && (data.status === 'PENDING' || data.status === 'REJECTED')) {
        return data as any;
      }
      if (data && (data.error || data.message)) {
        throw new Error(data.error || data.message);
      }
      throw new Error(`HTTP ${res.status}: Failed to load laboratory dashboard`);
    }
    return data;
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

  async createLabOffer(payload: {
    title: string;
    domain: string;
    durationMonths?: number;
    duration?: string;
    startDate?: string;
    location: string;
    compensationType: string;
    compensationAmount?: string;
    description: string;
    tasks: string[];
    skills: string[];
    isDraft?: boolean;
  }, labId?: string): Promise<{ id: string; message: string }> {
    const headers = this.getHeaders();
    if (labId) {
      headers['X-Lab-Id'] = labId;
    }
    const res = await fetch('/api/lab/offers', {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const msg = data?.error || data?.message || `HTTP ${res.status}: Failed to publish offer`;
      throw new Error(msg);
    }
    return data;
  }

  async getLabOffers(labId?: string): Promise<any[]> {
    try {
      const headers = this.getHeaders();
      if (labId) {
        headers['X-Lab-Id'] = labId;
      }
      const res = await fetch('/api/lab/offers', {
        headers,
        credentials: 'include',
      });
      if (!res.ok) return [];
      return await res.json();
    } catch {
      return [];
    }
  }

  async getCandidates(offerId?: string, labId?: string): Promise<any> {
    const headers = this.getHeaders();
    if (labId) {
      headers['X-Lab-Id'] = labId;
    }
    const query = offerId ? `?offerId=${encodeURIComponent(offerId)}` : '';
    const res = await fetch(`/api/lab/candidates${query}`, {
      headers,
      credentials: 'include',
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const msg = data?.error || data?.message || `HTTP ${res.status}: Failed to load candidates`;
      throw new Error(msg);
    }
    return data;
  }

  async updateApplicationStatus(applicationId: string, status: string, labId?: string): Promise<any> {
    const headers = this.getHeaders();
    if (labId) {
      headers['X-Lab-Id'] = labId;
    }
    const res = await fetch(`/api/lab/applications/${encodeURIComponent(applicationId)}/status`, {
      method: 'PUT',
      headers,
      credentials: 'include',
      body: JSON.stringify({ status }),
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const msg = data?.error || data?.message || `HTTP ${res.status}: Failed to update candidate status`;
      throw new Error(msg);
    }
    return data;
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

