import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { FarmerDashboard, ProductView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AgoPipe, KesPipe, NumPipe, shortDate } from '../../shared/format';
import { StatusBadge, TrendBadge } from '../../shared/trend-badge';

/** Wireframe "Farmer — Dashboard": recommended price, demand trend, active listings, your listings, matched buyers. */
@Component({
  selector: 'app-farmer-dashboard',
  imports: [RouterLink, KesPipe, NumPipe, AgoPipe, TrendBadge, StatusBadge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Dashboard</h1>
          <div class="sub">Your produce, price guidance and interested buyers</div>
        </div>
        <a class="btn btn-primary" routerLink="/farmer/listings/new">+ List new produce</a>
      </div>

      @if (error()) { <div class="alert error mb">{{ error() }}</div> }

      <div class="grid-3">
        <div class="card">
          <div class="stat-label">Recommended price</div>
          @if (d(); as d) {
            <div class="stat-value">{{ d.recommendedPrice !== null ? (d.recommendedPrice | kes) : '—' }}<small class="unit">/kg</small></div>
            <div class="stat-sub">{{ d.recommendedPriceCrop ? 'Latest: ' + d.recommendedPriceCrop : 'List produce to get a recommendation' }}
              @if (d.recommendedConfidence) { · {{ d.recommendedConfidence }} confidence }</div>
          } @else { <div class="skeleton big mt"></div> }
        </div>
        <div class="card">
          <div class="stat-label">Demand trend</div>
          @if (d(); as d) {
            <div class="stat-value"><app-trend [trend]="d.demandTrend" [change]="d.demandChangePct" /></div>
            <div class="stat-sub">{{ d.demandCrop ? d.demandCrop + ' · next 4 weeks' : 'No crop selected yet' }}</div>
          } @else { <div class="skeleton big mt"></div> }
        </div>
        <div class="card">
          <div class="stat-label">Active listings</div>
          @if (d(); as d) {
            <div class="stat-value">{{ d.activeListings }}</div>
            <div class="stat-sub">{{ d.draftListings }} draft · {{ d.salesLast30Days | kes: 0 }} sold in 30 days</div>
          } @else { <div class="skeleton big mt"></div> }
        </div>
      </div>

      @if (d()?.pendingOrders) {
        <div class="alert warn mt">
          <strong>{{ d()!.pendingOrders }} order(s) awaiting delivery</strong>
          Mark them as completed once delivered. <a routerLink="/farmer/sales">View sales →</a>
        </div>
      }

      <div class="section-title">Your listings</div>
      @if (d(); as d) {
        @if (openListings().length) {
          <div class="list">
            @for (p of openListings(); track p.id) {
              <div class="list-row clickable" (click)="open(p)">
                <div class="thumb">{{ p.emoji }}</div>
                <div class="main">
                  <div class="title">{{ p.cropType }} · {{ p.quantity | num: 0 }} {{ p.unit }}</div>
                  <div class="meta">
                    {{ p.pricePerUnit !== null ? (p.pricePerUnit | kes) + '/' + p.unit : 'Price not set' }} · {{ p.location }}
                    · harvest {{ fmt(p.harvestDate) }}
                    @if (p.priceVsMarketPct !== null && p.status === 'AVAILABLE') {
                      · <span class="muted">{{ p.priceVsMarketPct! > 0 ? '+' : '' }}{{ p.priceVsMarketPct }}% vs market</span>
                    }
                  </div>
                </div>
                <app-status [status]="p.status" />
                <button class="btn btn-sm" (click)="$event.stopPropagation(); open(p)">{{ p.status === 'DRAFT' ? 'Set price' : 'Buyers' }}</button>
              </div>
            }
          </div>
        } @else {
          <div class="empty">No active listings. <a routerLink="/farmer/listings/new">List your first produce</a> to get a price recommendation.</div>
        }
      } @else {
        <div class="list"><div class="list-row"><div class="thumb"></div><div class="main"><div class="skeleton" style="width:40%"></div></div></div></div>
      }

      <div class="section-title">Matched buyers</div>
      @if (d()?.matchedBuyers?.length) {
        <div class="grid-2">
          @for (m of d()!.matchedBuyers; track m.buyerId) {
            <div class="card buyer">
              <div class="avatar">{{ initials(m.buyerName) }}</div>
              <div class="main">
                <div class="title">{{ m.buyerName }}</div>
                <div class="meta">{{ m.buyerLocation }} · for your {{ m.cropType }} · match {{ (m.score * 100).toFixed(0) }}%</div>
                <div class="reasons">@for (r of m.reasons; track r) { <span class="pill">{{ r }}</span> }</div>
              </div>
              <a class="btn btn-sm" routerLink="/messages" [queryParams]="{ to: m.buyerId, name: m.buyerName }">Message</a>
            </div>
          }
        </div>
      } @else {
        <div class="empty">Buyers are matched when you publish a listing.</div>
      }

      @if (d()?.recentSales?.length) {
        <div class="section-title">Recent sales</div>
        <div class="list">
          @for (t of d()!.recentSales; track t.id) {
            <div class="list-row">
              <div class="main">
                <div class="title">{{ t.cropType }} · {{ t.quantity | num: 0 }} {{ t.unit }} to {{ t.buyerName }}</div>
                <div class="meta">{{ t.agreedPrice | kes }}/{{ t.unit }} · total {{ t.total | kes: 0 }} · {{ t.transactionDate | ago }}</div>
              </div>
              <app-status [status]="t.status" />
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .unit { font-size: .9rem; color: var(--muted); font-weight: 500; margin-left: 2px; }
    .buyer { display: flex; gap: 14px; align-items: flex-start; }
    .buyer .main { flex: 1; min-width: 0; }
    .buyer .title { font-weight: 600; }
    .buyer .meta { color: var(--muted); font-size: .88rem; }
    .reasons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
  `]
})
export class FarmerDashboardPage {
  private api = inject(ApiService);
  private router = inject(Router);
  private toast = inject(ToastService);
  d = signal<FarmerDashboard | null>(null);
  error = signal('');
  fmt = shortDate;

  constructor() {
    this.api.farmerDashboard().subscribe({
      next: d => this.d.set(d),
      error: e => this.error.set(errorMessage(e))
    });
  }

  openListings(): ProductView[] {
    return (this.d()?.listings || []).filter(p => p.status === 'AVAILABLE' || p.status === 'DRAFT');
  }

  open(p: ProductView) {
    if (p.status === 'DRAFT') this.router.navigate(['/farmer/listings', p.id, 'recommendation']);
    else this.router.navigate(['/farmer/listings', p.id, 'matches']);
  }

  initials(n: string) {
    return n.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }
}
