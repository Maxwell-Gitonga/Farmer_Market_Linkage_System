import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { MarketOption, ProductDetailView, RecommendationView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Bar, BarChart } from '../../shared/charts';
import { KesPipe, NumPipe, shortDate } from '../../shared/format';
import { ConfidenceBadge, StatusBadge, TrendBadge } from '../../shared/trend-badge';

/**
 * Wireframe "Price Recommendation Detail View" – the decision points of the activity diagram:
 * sufficient data? → demand rising? → farmer accepts the recommended price or sets a custom one.
 */
@Component({
  selector: 'app-recommendation',
  imports: [FormsModule, RouterLink, KesPipe, NumPipe, BarChart, ConfidenceBadge, TrendBadge, StatusBadge],
  template: `
    <div class="page narrow">
      <div class="card view">
        <div class="head">
          <span class="check">✓</span>
          <h2>Price recommendation</h2>
          <button class="btn btn-sm refresh" (click)="load(true)" [disabled]="loading()" title="Recalculate with the latest market data">↻ Refresh</button>
        </div>

        @if (error()) { <div class="alert error">{{ error() }}</div> }

        @if (detail(); as d) {
          <div class="product">
            <div class="thumb lg">{{ d.product.emoji }}</div>
            <div>
              <div class="ptitle">{{ d.product.cropType }} · {{ d.product.quantity | num }} {{ d.product.unit }}</div>
              <div class="muted">{{ d.product.location }} · harvest {{ fmt(d.product.harvestDate) }} · <app-status [status]="d.product.status" /></div>
            </div>
          </div>
        }

        @if (rec(); as r) {
          @if (r.sufficientData) {
            <div class="price-box">
              <div class="muted">Recommended price per kg</div>
              <div class="big-price">{{ r.recommendedPrice | kes }}</div>
              <app-confidence [label]="r.confidenceLabel" [value]="r.confidence" />
              <div class="muted small mt">
                Predicted market price at harvest {{ r.predictedPrice | kes }} (likely range {{ r.lowerBound | kes: 0 }} – {{ r.upperBound | kes: 0 }})
                @if (r.adjustmentPct) { · demand adjustment {{ r.adjustmentPct! > 0 ? '+' : '' }}{{ r.adjustmentPct }}% }
              </div>
            </div>
          } @else {
            <div class="alert warn big-alert">
              <strong>Limited data</strong>
              {{ r.message || 'There is not enough historical market data to recommend a price for this crop.' }}
            </div>
          }

          <div class="label">30-day price trend <small class="muted">({{ r.priceTrendBasis === 'national' ? 'national average' : r.county }})</small></div>
          <div class="trend-box">
            <app-bar-chart [bars]="trendBars()" [height]="90" unit="KES " [showLabels]="true" ariaLabel="30-day price trend" />
          </div>

          <div class="demand-box">
            <div>
              <div class="muted">Demand forecast</div>
              <div class="dv"><app-trend [trend]="r.demand?.sufficientData ? r.demand?.trend : null" [change]="r.demand?.changePct" /></div>
              @if (r.demand?.sufficientData) {
                <small class="muted">{{ r.demand?.forecastWeeklyAvg | num }} kg/week expected ({{ r.demand?.forecastPeriod }})</small>
              } @else { <small class="muted">{{ r.demand?.message }}</small> }
            </div>
            <div class="right">
              <div class="muted">Based on</div>
              <div class="dv">{{ r.basedOnRecords | num }} market records</div>
              <small class="muted">{{ r.recentRecords }} recent in {{ r.county }} · {{ r.algorithm?.replace('_', ' ') }}</small>
            </div>
          </div>

          @if (r.advice.length) {
            <div class="advice">
              <div class="label">Advice</div>
              <ul>@for (a of r.advice; track a) { <li>{{ a }}</li> }</ul>
            </div>
          }

          @if (r.sellWindow.length && r.sufficientData) {
            <div class="label">Best time to sell <small class="muted">(within the crop's shelf life)</small></div>
            <div class="window">
              @for (w of r.sellWindow; track w.date) {
                <div class="wcell" [class.best]="w.date === r.bestSellDate">
                  <small class="muted">{{ fmt(w.date) }}</small>
                  <strong>{{ w.price | kes: 0 }}</strong>
                </div>
              }
            </div>
          }

          @if (markets().length) {
            <div class="label">Best markets for your {{ r.cropType }} <small class="muted">(predicted price minus transport from {{ r.county }})</small></div>
            <div class="table-wrap">
              <table>
                <thead><tr><th>#</th><th>Market</th><th class="num">Predicted</th><th class="num">Distance</th><th class="num">Transport/kg</th><th class="num">Net/kg</th></tr></thead>
                <tbody>
                  @for (m of markets().slice(0, 5); track m.market) {
                    <tr><td>{{ m.rank }}</td><td>{{ m.market }}</td><td class="num">{{ m.predictedPrice | kes: 0 }}</td>
                      <td class="num">{{ m.distanceKm === null ? '—' : (m.distanceKm | num) + ' km' }}</td>
                      <td class="num">{{ m.transportCostPerKg | kes }}</td><td class="num"><strong>{{ m.netPricePerKg | kes }}</strong></td></tr>
                  }
                </tbody>
              </table>
            </div>
          }

          @if (r.fallback) {
            <div class="alert warn mt">The ML decision-support service is offline – figures are a simple moving-average estimate.</div>
          }

          @if (detail()?.product?.status === 'DRAFT' || detail()?.product?.status === 'AVAILABLE') {
            @if (custom()) {
              <div class="custom mt-lg">
                <div class="field">
                  <label for="own">Your price per kg (KES)</label>
                  <input id="own" type="number" min="1" step="0.5" [(ngModel)]="ownPrice" />
                  @if (r.predictedPrice && ownPrice) {
                    <span class="hint">{{ diffText() }}</span>
                  }
                </div>
                <div class="actions">
                  <button class="btn btn-primary" (click)="publish(ownPrice!, false)" [disabled]="busy() || !ownPrice || ownPrice <= 0">List at my price</button>
                  <button class="btn" (click)="custom.set(false)">Back</button>
                </div>
              </div>
            } @else {
              <div class="actions mt-lg">
                <button class="btn btn-primary" (click)="publish(r.recommendedPrice!, true)" [disabled]="busy() || !r.sufficientData">
                  @if (busy()) { <span class="spinner"></span> } List at this price
                </button>
                <button class="btn" (click)="startCustom()" [disabled]="busy()">Set own price</button>
              </div>
            }
          } @else if (detail()) {
            <div class="actions mt-lg"><a class="btn" routerLink="/farmer/listings">Back to my listings</a></div>
          }
        } @else if (loading()) {
          <div class="stack mt"><div class="skeleton big"></div><div class="skeleton"></div><div class="skeleton" style="width:70%"></div>
            <p class="muted center">Analysing historical market data…</p></div>
        }
      </div>
    </div>
  `,
  styles: [`
    .narrow { max-width: 900px; }
    .view { padding: 28px; }
    .head { display: flex; align-items: center; gap: 14px; border-bottom: 1px solid var(--text); padding-bottom: 14px; margin-bottom: 20px; }
    .head h2 { margin: 0; flex: 1; }
    .check { width: 30px; height: 30px; border: 1px solid var(--border-strong); border-radius: 7px; display: grid; place-items: center; color: var(--accent); }
    .product { display: flex; gap: 16px; align-items: center; margin-bottom: 20px; }
    .ptitle { font-weight: 650; font-size: 1.1rem; }
    .price-box { border: 1px solid var(--border-strong); border-radius: 12px; padding: 24px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; }
    .big-alert { padding: 20px; }
    .label { color: var(--muted); margin: 22px 0 8px; }
    .trend-box { border: 1px solid var(--border-strong); border-radius: 12px; padding: 16px 16px 10px; }
    .demand-box { border: 1px dashed var(--border-strong); border-radius: 12px; padding: 16px 18px; display: flex; justify-content: space-between; gap: 20px; margin-top: 22px; flex-wrap: wrap; }
    .demand-box .right { text-align: right; }
    .dv { font-weight: 650; margin: 4px 0; }
    .advice ul { margin: 0; padding-left: 20px; color: var(--text); }
    .advice li { margin-bottom: 6px; }
    .window { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 10px; }
    .wcell { border: 1px solid var(--border); border-radius: 10px; padding: 10px; display: flex; flex-direction: column; align-items: center; }
    .wcell.best { border-color: var(--accent); background: rgba(124,196,127,.08); }
    .actions { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .actions .btn { padding: 15px; font-size: 1.02rem; }
    .custom .actions { margin-top: 14px; }
    .small { font-size: .88rem; }
    @media (max-width: 560px) { .view { padding: 18px; } .actions { grid-template-columns: 1fr; } .demand-box .right { text-align: left; } }
  `]
})
export class RecommendationPage implements OnInit {
  id = input.required<string>();
  private api = inject(ApiService);
  private router = inject(Router);
  private toast = inject(ToastService);

  rec = signal<RecommendationView | null>(null);
  detail = signal<ProductDetailView | null>(null);
  markets = signal<MarketOption[]>([]);
  loading = signal(true);
  busy = signal(false);
  error = signal('');
  custom = signal(false);
  ownPrice: number | null = null;
  fmt = shortDate;

  trendBars = computed<Bar[]>(() => (this.rec()?.priceTrend || []).map(p => ({ label: shortDate(p.date), value: p.price })));

  ngOnInit() { this.load(false); }

  load(refresh: boolean) {
    const id = Number(this.id());
    this.loading.set(true);
    this.error.set('');
    this.api.product(id).subscribe({ next: d => this.detail.set(d), error: e => this.error.set(errorMessage(e)) });
    this.api.recommendation(id, refresh).subscribe({
      next: r => {
        this.rec.set(r);
        this.loading.set(false);
        if (!r.sufficientData) this.custom.set(true);   // displayAlert('limited data') → farmer sets own price
        if (r.sufficientData) {
          this.api.bestMarkets(r.cropType, r.county, r.quantity, r.harvestDate).subscribe({ next: m => this.markets.set(m), error: () => {} });
        }
        if (refresh) this.toast.info('Recommendation recalculated with the latest market data');
      },
      error: e => { this.loading.set(false); this.error.set(errorMessage(e)); }
    });
  }

  startCustom() {
    this.ownPrice = this.rec()?.recommendedPrice ?? null;
    this.custom.set(true);
  }

  diffText(): string {
    const p = this.rec()?.predictedPrice;
    if (!p || !this.ownPrice) return '';
    const d = (100 * (this.ownPrice / p - 1));
    return `${d >= 0 ? '+' : ''}${d.toFixed(1)}% compared with the predicted market price`;
  }

  publish(price: number, accepted: boolean) {
    this.busy.set(true);
    this.api.publish(Number(this.id()), price, accepted).subscribe({
      next: r => {
        this.busy.set(false);
        this.toast.success(`Listed at KES ${r.product.pricePerUnit}/kg – ${r.matchedBuyers.length} matched buyer(s)`);
        this.router.navigate(['/farmer/listings', r.product.id, 'matches']);
      },
      error: e => { this.busy.set(false); this.toast.error(errorMessage(e)); }
    });
  }
}
