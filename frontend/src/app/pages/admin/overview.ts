import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AdminOverview, LogView } from '../../core/models';
import { AgoPipe, KesPipe, NumPipe } from '../../shared/format';

@Component({
  selector: 'app-admin-overview',
  imports: [RouterLink, KesPipe, NumPipe, AgoPipe],
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Administration</h1><div class="sub">Users, listings, activity and system health</div></div></div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      @if (o(); as o) {
        @if (o.pendingFarmers) {
          <div class="alert warn mb"><strong>{{ o.pendingFarmers }} farmer account(s) awaiting approval</strong>
            <a routerLink="/admin/users" [queryParams]="{ status: 'PENDING' }">Review now →</a></div>
        }
        <div class="grid-4">
          <div class="card"><div class="stat-label">Farmers</div><div class="stat-value">{{ o.farmers | num }}</div><div class="stat-sub">{{ o.pendingFarmers }} pending approval</div></div>
          <div class="card"><div class="stat-label">Buyers</div><div class="stat-value">{{ o.buyers | num }}</div></div>
          <div class="card"><div class="stat-label">Active listings</div><div class="stat-value">{{ o.activeListings | num }}</div><div class="stat-sub">{{ o.soldListings | num }} sold · {{ o.removedListings }} removed</div></div>
          <div class="card"><div class="stat-label">Transactions (30 days)</div><div class="stat-value">{{ o.transactions30d | num }}</div><div class="stat-sub">{{ o.volume30dKes | kes: 0 }}</div></div>
          <div class="card"><div class="stat-label">Price predictions (30 days)</div><div class="stat-value">{{ o.predictions30d | num }}</div></div>
          <div class="card"><div class="stat-label">Market price records</div><div class="stat-value">{{ o.marketPriceRecords | num }}</div><div class="stat-sub">latest {{ o.latestMarketDate }}</div></div>
          <div class="card"><div class="stat-label">ML module</div><div class="stat-value" [class.up]="o.ml.status === 'UP'" [class.down]="o.ml.status !== 'UP'">{{ o.ml.status }}</div>
            <div class="stat-sub">{{ o.ml.retraining ? 'retraining…' : '' }} <a routerLink="/admin/models">details</a></div></div>
          <div class="card"><div class="stat-label">Activity (24 h)</div><div class="stat-value">{{ o.logs24h | num }}</div><div class="stat-sub"><a routerLink="/admin/activity">view log</a></div></div>
        </div>
      } @else { <div class="grid-4"><div class="card"><div class="skeleton big"></div></div></div> }

      <div class="section-title">Recent activity</div>
      <div class="list">
        @for (l of logs(); track l.id) {
          <div class="list-row">
            <span class="pill">{{ l.action }}</span>
            <div class="main"><div>{{ l.details }}</div><div class="meta">{{ l.actorName }} {{ l.actorRole ? '(' + l.actorRole.toLowerCase() + ')' : '' }}</div></div>
            <small class="muted">{{ l.createdAt | ago }}</small>
          </div>
        } @empty { <div class="empty small">No activity yet.</div> }
      </div>
    </div>
  `
})
export class AdminOverviewPage {
  private api = inject(ApiService);
  o = signal<AdminOverview | null>(null);
  logs = signal<LogView[]>([]);
  error = signal('');
  constructor() {
    this.api.adminOverview().subscribe({ next: o => this.o.set(o), error: e => this.error.set(errorMessage(e)) });
    this.api.logs().subscribe({ next: l => this.logs.set(l.slice(0, 10)), error: () => {} });
  }
}
