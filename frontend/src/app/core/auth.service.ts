import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, Role, UserView } from './models';

const TOKEN_KEY = 'fmls.token';
const USER_KEY = 'fmls.user';

/** Single sign-in: after login the role returned by the backend decides which part of the app is shown. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);

  readonly user = signal<UserView | null>(this.read());
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly loggedIn = computed(() => !!this.user() && !!this.token());

  token(): string | null {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  }

  private read(): UserView | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as UserView) : null;
    } catch { return null; }
  }

  private store(res: AuthResponse) {
    try {
      localStorage.setItem(TOKEN_KEY, res.token);
      localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    } catch { /* storage unavailable */ }
    this.user.set(res.user);
  }

  setUser(u: UserView) {
    try { localStorage.setItem(USER_KEY, JSON.stringify(u)); } catch { }
    this.user.set(u);
  }

  login(phone: string, password: string, role?: Role) {
    return this.api.login(phone, password, role).pipe(tap(r => this.store(r)));
  }

  register(body: any) {
    return this.api.register(body).pipe(tap(r => this.store(r)));
  }

  refresh() {
    return this.api.me().pipe(tap(u => this.setUser(u)));
  }

  logout(redirect = true) {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch { }
    this.user.set(null);
    if (redirect) this.router.navigate(['/login']);
  }

  home(role: Role | null = this.role()): string {
    switch (role) {
      case 'FARMER': return '/farmer/dashboard';
      case 'BUYER': return '/buyer/browse';
      case 'ADMIN': return '/admin/overview';
      default: return '/login';
    }
  }
}
