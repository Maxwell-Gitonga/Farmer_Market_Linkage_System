import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

interface NavItem { path: string; label: string; exact?: boolean; }

/** Authenticated layout: title bar, role-based navigation, profile menu (avatar circle in the wireframes). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="bar">
      <div class="bar-inner">
        <a class="brand" [routerLink]="auth.home()">
          <span class="logo">🌾</span>
          <span>Farmer Market Linkage</span>
        </a>
        <nav class="nav">
          @for (n of nav(); track n.path) {
            <a [routerLink]="n.path" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: !!n.exact }">{{ n.label }}</a>
          }
        </nav>
        <div class="me">
          <span class="role-tag">{{ roleLabel() }}</span>
          <button class="avatar" (click)="menu.set(!menu())" [attr.aria-expanded]="menu()" aria-label="Account menu">{{ initials() }}</button>
          @if (menu()) {
            <div class="menu" (click)="menu.set(false)">
              <div class="menu-head">
                <strong>{{ auth.user()?.name }}</strong>
                <small>{{ auth.user()?.phone || auth.user()?.email }} · {{ auth.user()?.location }}</small>
              </div>
              <a routerLink="/profile">My profile</a>
              <button (click)="auth.logout()">Log out</button>
            </div>
          }
        </div>
      </div>
    </header>
    @if (auth.user()?.status === 'PENDING') {
      <div class="pending">Your farmer account is awaiting administrator approval. You can prepare listings and view
        price recommendations; listings can be published once you are approved.</div>
    }
    <main><router-outlet /></main>
  `,
  styles: [`
    .bar { position: sticky; top: 0; z-index: 20; background: rgba(14,14,14,.92); backdrop-filter: blur(8px); border-bottom: 1px solid var(--border); }
    .bar-inner { max-width: 1180px; margin: 0 auto; padding: 12px 20px; display: flex; align-items: center; gap: 24px; }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 1.08rem; text-decoration: none; white-space: nowrap; }
    .logo { width: 34px; height: 34px; border: 1px solid var(--border-strong); border-radius: 9px; display: grid; place-items: center; font-size: 18px; }
    .nav { display: flex; gap: 4px; flex: 1; overflow-x: auto; scrollbar-width: none; }
    .nav a { padding: 8px 12px; border-radius: 8px; color: var(--muted); text-decoration: none; white-space: nowrap; font-size: .95rem; }
    .nav a:hover { color: var(--text); background: var(--surface-2); }
    .nav a.on { color: var(--text); background: var(--surface-3); }
    .me { position: relative; display: flex; align-items: center; gap: 10px; }
    .role-tag { font-size: .78rem; color: var(--muted); border: 1px solid var(--border-strong); border-radius: 999px; padding: 3px 10px; }
    .avatar { cursor: pointer; color: var(--text); font: inherit; font-weight: 700; }
    .menu { position: absolute; right: 0; top: 50px; background: var(--surface-2); border: 1px solid var(--border-strong); border-radius: 12px;
      min-width: 230px; padding: 6px; box-shadow: 0 10px 40px rgba(0,0,0,.5); display: flex; flex-direction: column; }
    .menu-head { padding: 10px 12px; border-bottom: 1px solid var(--border); margin-bottom: 4px; display: flex; flex-direction: column; }
    .menu a, .menu button { text-align: left; padding: 10px 12px; border-radius: 8px; background: none; border: 0; color: var(--text);
      font: inherit; cursor: pointer; text-decoration: none; }
    .menu a:hover, .menu button:hover { background: var(--surface-3); }
    .pending { background: rgba(227,179,90,.1); border-bottom: 1px solid rgba(227,179,90,.4); color: var(--warning); padding: 10px 20px; text-align: center; font-size: .92rem; }
    @media (max-width: 760px) {
      .bar-inner { flex-wrap: wrap; gap: 10px; }
      .nav { order: 3; flex-basis: 100%; }
      .brand span:last-child { font-size: .98rem; }
      .me { margin-left: auto; }
      .role-tag { display: none; }
    }
  `]
})
export class Shell {
  auth = inject(AuthService);
  menu = signal(false);

  nav = computed<NavItem[]>(() => {
    switch (this.auth.role()) {
      case 'FARMER': return [
        { path: '/farmer/dashboard', label: 'Dashboard' },
        { path: '/farmer/listings/new', label: 'List produce' },
        { path: '/farmer/listings', label: 'My listings', exact: true },
        { path: '/farmer/sales', label: 'Sales' },
        { path: '/insights', label: 'Market insights' },
        { path: '/messages', label: 'Messages' }];
      case 'BUYER': return [
        { path: '/buyer/browse', label: 'Browse listings' },
        { path: '/buyer/purchases', label: 'My purchases' },
        { path: '/insights', label: 'Market insights' },
        { path: '/messages', label: 'Messages' }];
      case 'ADMIN': return [
        { path: '/admin/overview', label: 'Overview' },
        { path: '/admin/users', label: 'Users' },
        { path: '/admin/listings', label: 'Listings' },
        { path: '/admin/activity', label: 'Activity' },
        { path: '/admin/reports', label: 'Reports' },
        { path: '/admin/market-data', label: 'Market data' },
        { path: '/admin/models', label: 'ML models' },
        { path: '/insights', label: 'Insights' }];
      default: return [];
    }
  });

  roleLabel = computed(() => ({ FARMER: 'Farmer', BUYER: 'Buyer', ADMIN: 'Administrator' } as any)[this.auth.role() || ''] || '');
  initials = computed(() => (this.auth.user()?.name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase());
}
