import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { MatchView, ProductDetailView } from '../../core/models';
import { KesPipe, NumPipe, shortDate } from '../../shared/format';
import { StatusBadge } from '../../shared/trend-badge';

/** "View matched buyers" – end of the activity diagram flow. */
@Component({
  selector: 'app-matched-buyers',
  imports: [RouterLink, KesPipe, NumPipe, StatusBadge],
  template: `
    <div class="page narrow">
      <div class="page-head">
        <div>
          <h1>Matched buyers</h1>
          @if (detail(); as d) {
            <div class="sub">{{ d.product.emoji }} {{ d.product.cropType }} · {{ d.product.quantity | num }} {{ d.product.unit }} at
              {{ d.product.pricePerUnit | kes }}/{{ d.product.unit }} · {{ d.product.location }} · <app-status [status]="d.product.status" /></div>
          }
        </div>
        <div class="row">
          <a class="btn" [routerLink]="['/farmer/listings', id(), 'recommendation']">Price recommendation</a>
          <a class="btn btn-primary" routerLink="/farmer/dashboard">Done</a>
        </div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <p class="muted">Buyers ranked by the recommendation engine: interest in this crop, distance, your price compared with
        their local market, and their purchase history.</p>
      @if (loading()) {
        <div class="list"><div class="list-row"><div class="avatar"></div><div class="main"><div class="skeleton" style="width:50%"></div></div></div></div>
      } @else if (matches().length) {
        <div class="list">
          @for (m of matches(); track m.buyerId; let i = $index) {
            <div class="list-row">
              <div class="rank">{{ i + 1 }}</div>
              <div class="avatar">{{ initials(m.buyerName) }}</div>
              <div class="main">
                <div class="title">{{ m.buyerName }}</div>
                <div class="meta">{{ m.buyerLocation }} · {{ m.buyerPhone }}</div>
                <div class="reasons">@for (r of m.reasons; track r) { <span class="pill">{{ r }}</span> }</div>
              </div>
              <div class="score"><div class="bar"><i [style.width.%]="m.score * 100"></i></div><small>{{ (m.score * 100).toFixed(0) }}% match</small></div>
              <a class="btn btn-sm" routerLink="/messages" [queryParams]="{ to: m.buyerId, name: m.buyerName, product: id() }">Message</a>
            </div>
          }
        </div>
      } @else {
        <div class="empty">No matched buyers yet{{ detail()?.product?.status !== 'AVAILABLE' ? ' – publish the listing first' : '' }}.</div>
      }
    </div>
  `,
  styles: [`
    .narrow { max-width: 980px; }
    .rank { width: 22px; color: var(--muted); font-weight: 700; }
    .reasons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .score { width: 120px; text-align: right; }
    .score .bar { height: 6px; background: var(--surface-3); border-radius: 4px; overflow: hidden; margin-bottom: 4px; }
    .score .bar i { display: block; height: 100%; background: var(--accent); }
    @media (max-width: 620px) { .score { display: none; } }
  `]
})
export class MatchedBuyersPage implements OnInit {
  id = input.required<string>();
  private api = inject(ApiService);
  matches = signal<MatchView[]>([]);
  detail = signal<ProductDetailView | null>(null);
  loading = signal(true);
  error = signal('');
  fmt = shortDate;

  ngOnInit() {
    const id = Number(this.id());
    this.api.product(id).subscribe({ next: d => this.detail.set(d), error: e => this.error.set(errorMessage(e)) });
    this.api.matches(id).subscribe({
      next: m => { this.matches.set(m); this.loading.set(false); },
      error: e => { this.loading.set(false); this.error.set(errorMessage(e)); }
    });
  }

  initials(n: string) { return n.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase(); }
}
