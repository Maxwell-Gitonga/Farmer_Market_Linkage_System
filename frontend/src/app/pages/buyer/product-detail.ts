import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { DemandView, ProductDetailView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Bar, BarChart } from '../../shared/charts';
import { KesPipe, NumPipe, shortDate } from '../../shared/format';
import { StatusBadge, TrendBadge } from '../../shared/trend-badge';

/** Listing detail for buyers: pricing insight, farmer contact, purchase (purchaseProduct → createTransaction). */
@Component({
  selector: 'app-product-detail',
  imports: [FormsModule, RouterLink, KesPipe, NumPipe, BarChart, StatusBadge, TrendBadge],
  template: `
    <div class="page narrow">
      <a class="link" routerLink="/buyer/browse">← Back to listings</a>
      @if (error()) { <div class="alert error mt">{{ error() }}</div> }
      @if (d(); as d) {
        <div class="grid-main mt">
          <div class="card">
            <div class="row">
              <div class="thumb lg">{{ d.product.emoji }}</div>
              <div>
                <h1>{{ d.product.cropType }}</h1>
                <div class="muted">{{ d.product.quantity | num }} {{ d.product.unit }} available · {{ d.product.location }} ·
                  harvest {{ fmt(d.product.harvestDate) }} · <app-status [status]="d.product.status" /></div>
              </div>
            </div>
            @if (d.product.description) { <p class="mt">{{ d.product.description }}</p> }

            <div class="label">Pricing insight</div>
            <div class="grid-3">
              <div class="mini"><small class="muted">Asking price</small><strong>{{ d.product.pricePerUnit | kes }}/{{ d.product.unit }}</strong></div>
              <div class="mini"><small class="muted">Local market price</small><strong>{{ d.product.marketPrice | kes }}</strong></div>
              <div class="mini"><small class="muted">Compared with market</small>
                <strong [class.up]="(d.product.priceVsMarketPct ?? 0) <= 0" [class.down]="(d.product.priceVsMarketPct ?? 0) > 0">
                  {{ d.product.priceVsMarketPct === null ? '—' : (d.product.priceVsMarketPct! > 0 ? '+' : '') + d.product.priceVsMarketPct + '%' }}</strong></div>
            </div>
            <div class="label">30-day price trend <small class="muted">({{ d.trendBasis === 'national' ? 'national' : d.product.location }})</small></div>
            <div class="trend-box"><app-bar-chart [bars]="bars()" unit="KES " [showLabels]="true" /></div>
            @if (demand(); as dm) {
              <div class="row mt"><span class="muted">Demand for {{ d.product.cropType }} over the next 4 weeks:</span>
                <app-trend [trend]="dm.trend" [change]="dm.changePct" /></div>
            }
          </div>

          <div class="stack">
            <div class="card">
              <h3>Seller</h3>
              <div class="row">
                <div class="avatar">{{ initials(d.product.farmerName) }}</div>
                <div><strong>{{ d.product.farmerName }}</strong><div class="muted">{{ d.product.farmerLocation }}</div></div>
              </div>
              @if (d.product.farmerPhone) { <a class="btn w-100 mt" [href]="'tel:' + d.product.farmerPhone">📞 {{ d.product.farmerPhone }}</a> }
              <a class="btn w-100 mt" routerLink="/messages" [queryParams]="{ to: d.product.farmerId, name: d.product.farmerName, product: d.product.id }">Message seller</a>
            </div>

            @if (d.product.status === 'AVAILABLE') {
              <div class="card">
                <h3>Buy</h3>
                <div class="field">
                  <label for="q">Quantity ({{ d.product.unit }})</label>
                  <input id="q" type="number" [min]="1" [max]="d.product.quantity" [(ngModel)]="qty" />
                  <span class="hint">Max {{ d.product.quantity | num }} {{ d.product.unit }}</span>
                </div>
                <div class="row between mt"><span class="muted">Total</span><strong>{{ (qty || 0) * (d.product.pricePerUnit || 0) | kes }}</strong></div>
                <button class="btn btn-primary w-100 mt" (click)="buy()" [disabled]="busy() || !qty || qty <= 0 || qty > d.product.quantity">
                  @if (busy()) { <span class="spinner"></span> } Purchase
                </button>
                <p class="muted small mt">Payment and delivery are arranged directly with the farmer.</p>
              </div>
            } @else {
              <div class="alert">This produce is no longer available.</div>
            }
          </div>
        </div>
      } @else if (!error()) {
        <div class="card mt"><div class="skeleton big"></div></div>
      }
    </div>
  `,
  styles: [`
    .narrow { max-width: 1060px; }
    .grid-main { display: grid; grid-template-columns: 1fr 320px; gap: 18px; align-items: start; }
    .label { color: var(--muted); margin: 20px 0 8px; }
    .mini { border: 1px solid var(--border); border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
    .trend-box { border: 1px solid var(--border-strong); border-radius: 12px; padding: 14px 14px 8px; }
    .small { font-size: .85rem; }
    @media (max-width: 860px) { .grid-main { grid-template-columns: 1fr; } }
  `]
})
export class ProductDetailPage implements OnInit {
  id = input.required<string>();
  private api = inject(ApiService);
  private router = inject(Router);
  private toast = inject(ToastService);
  d = signal<ProductDetailView | null>(null);
  demand = signal<DemandView | null>(null);
  error = signal('');
  busy = signal(false);
  qty: number | null = null;
  fmt = shortDate;
  bars = computed<Bar[]>(() => (this.d()?.priceTrend || []).map(p => ({ label: shortDate(p.date), value: p.price })));

  ngOnInit() {
    this.api.product(Number(this.id())).subscribe({
      next: d => {
        this.d.set(d);
        this.qty = d.product.quantity;
        this.api.demand(d.product.cropType).subscribe({ next: x => this.demand.set(x), error: () => {} });
      },
      error: e => this.error.set(errorMessage(e))
    });
  }

  initials(n: string) { return n.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase(); }

  buy() {
    const p = this.d()!.product;
    if (!confirm(`Buy ${this.qty} ${p.unit} of ${p.cropType} from ${p.farmerName} for KES ${((this.qty || 0) * (p.pricePerUnit || 0)).toLocaleString()}?`)) return;
    this.busy.set(true);
    this.api.purchase(p.id, this.qty!).subscribe({
      next: r => {
        this.busy.set(false);
        this.toast.success(`${r.message} – transaction #${r.transactionId}`);   // displayAlert('Purchase confirmed')
        this.router.navigate(['/buyer/purchases']);
      },
      error: e => { this.busy.set(false); this.toast.error(errorMessage(e)); }
    });
  }
}
