import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { DemandView, LatestPriceView, MarketOption, MetaView, PriceForecastView } from '../../core/models';
import { Band, LineChart, LineSeries } from '../../shared/charts';
import { KesPipe, NumPipe } from '../../shared/format';
import { TrendBadge } from '../../shared/trend-badge';

/** Market insights (Insights & Reports dashboard): price trends, price forecast, demand forecast, best markets. */
@Component({
  selector: 'app-insights',
  imports: [FormsModule, KesPipe, NumPipe, LineChart, TrendBadge],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Market insights</h1><div class="sub">Price trends, predictions and demand forecasts from historical market data</div></div>
        <div class="row">
          <select [(ngModel)]="crop" (ngModelChange)="load()" aria-label="Crop">
            @for (c of meta()?.crops || []; track c.name) { <option [value]="c.name">{{ c.emoji }} {{ c.name }}</option> }
          </select>
          <select [(ngModel)]="county" (ngModelChange)="load()" aria-label="County">
            @for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }
          </select>
        </div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }

      <div class="grid-4">
        <div class="card"><div class="stat-label">Latest price · {{ county }}</div>
          <div class="stat-value">{{ latestHere()?.price | kes }}</div>
          <div class="stat-sub">@if (latestHere()?.change7dPct !== null && latestHere()) {
            <span [class.up]="latestHere()!.change7dPct! > 0" [class.down]="latestHere()!.change7dPct! < 0">{{ latestHere()!.change7dPct! > 0 ? '+' : '' }}{{ latestHere()!.change7dPct }}%</span> vs last week }</div></div>
        <div class="card"><div class="stat-label">Predicted in 4 weeks</div>
          <div class="stat-value">{{ last()?.price | kes }}</div>
          <div class="stat-sub">{{ last() ? 'confidence ' + last()!.confidenceLabel : (forecast()?.message || '') }}</div></div>
        <div class="card"><div class="stat-label">Demand trend (4 weeks)</div>
          <div class="stat-value"><app-trend [trend]="demand()?.trend" [change]="demand()?.changePct" /></div>
          <div class="stat-sub">{{ demand()?.forecastWeeklyAvg | num }} kg/week forecast</div></div>
        <div class="card"><div class="stat-label">Best market to sell</div>
          <div class="stat-value">{{ markets()[0]?.market || '—' }}</div>
          <div class="stat-sub">{{ markets()[0] ? (markets()[0].netPricePerKg | kes) + '/kg net from ' + county : '' }}</div></div>
      </div>

      <div class="grid-2 mt">
        <div class="card">
          <h3>Price: last 60 days and 4-week forecast</h3>
          <app-line-chart [series]="priceSeries()" [band]="priceBand()" yPrefix="" ariaLabel="Price history and forecast" />
          <p class="muted small">Regression model prediction with its likely range (shaded). KES per kg.</p>
        </div>
        <div class="card">
          <h3>Weekly demand and forecast</h3>
          <app-line-chart [series]="demandSeries()" ariaLabel="Demand history and forecast" />
          <p class="muted small">{{ demand()?.model?.replaceAll('_', ' ') }} · national platform demand (kg per week).
            @if (demand()?.message) { {{ demand()?.message }} }</p>
        </div>
      </div>

      <div class="grid-2 mt">
        <div>
          <div class="section-title">Best markets from {{ county }}</div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Market</th><th class="num">Predicted</th><th class="num">Transport/kg</th><th class="num">Net/kg</th></tr></thead>
              <tbody>
                @for (m of markets(); track m.market) {
                  <tr><td>{{ m.rank }}</td><td>{{ m.market }}</td><td class="num">{{ m.predictedPrice | kes }}</td>
                    <td class="num">{{ m.transportCostPerKg | kes }}</td><td class="num"><strong>{{ m.netPricePerKg | kes }}</strong></td></tr>
                } @empty { <tr><td colspan="5" class="muted">No data</td></tr> }
              </tbody>
            </table>
          </div>
        </div>
        <div>
          <div class="section-title">Latest {{ crop }} prices by county</div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>County</th><th class="num">Price (KES/kg)</th><th class="num">7-day change</th><th>Date</th></tr></thead>
              <tbody>
                @for (p of latest(); track p.market) {
                  <tr [class.here]="p.market === county"><td>{{ p.market }}</td><td class="num">{{ p.price | kes }}</td>
                    <td class="num" [class.up]="(p.change7dPct ?? 0) > 0" [class.down]="(p.change7dPct ?? 0) < 0">{{ p.change7dPct === null ? '—' : (p.change7dPct > 0 ? '+' : '') + p.change7dPct + '%' }}</td>
                    <td class="muted">{{ p.date }}</td></tr>
                } @empty { <tr><td colspan="4" class="muted">No data</td></tr> }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`.small { font-size: .85rem; margin-top: 8px; } tr.here td { background: var(--surface-2); }`]
})
export class InsightsPage {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  meta = signal<MetaView | null>(null);
  forecast = signal<PriceForecastView | null>(null);
  demand = signal<DemandView | null>(null);
  markets = signal<MarketOption[]>([]);
  latest = signal<LatestPriceView[]>([]);
  error = signal('');
  crop = 'Maize';
  county = this.auth.user()?.location || 'Nairobi';

  latestHere = computed(() => this.latest().find(p => p.market === this.county) || null);
  last = computed(() => { const f = this.forecast()?.forecast || []; return f.length ? f[f.length - 1] : null; });

  priceSeries = computed<LineSeries[]>(() => {
    const f = this.forecast();
    if (!f) return [];
    const hist = f.history.map(p => ({ x: p.date, y: p.price }));
    const fc = f.forecast.map(p => ({ x: p.date, y: p.price }));
    const bridge = hist.length && fc.length ? [hist[hist.length - 1], ...fc] : fc;
    return [{ name: 'Market price', points: hist, color: 'var(--accent)' },
            { name: 'Predicted', points: bridge, color: 'var(--accent-2)', dashed: true }];
  });
  priceBand = computed<Band[]>(() => (this.forecast()?.forecast || []).filter(p => p.lower && p.upper)
    .map(p => ({ x: p.date, lo: p.lower!, hi: p.upper! })));
  demandSeries = computed<LineSeries[]>(() => {
    const d = this.demand();
    if (!d) return [];
    const hist = d.history.map(p => ({ x: p.weekStart, y: p.quantity }));
    const fc = d.forecast.map(p => ({ x: p.weekStart, y: p.quantity }));
    const bridge = hist.length && fc.length ? [hist[hist.length - 1], ...fc] : fc;
    return [{ name: 'Weekly demand', points: hist, color: 'var(--accent-3)' },
            { name: 'Forecast', points: bridge, color: 'var(--accent-2)', dashed: true }];
  });

  constructor() {
    this.api.meta().subscribe(m => this.meta.set(m));
    this.load();
  }

  load() {
    this.error.set('');
    this.api.priceForecast(this.crop, this.county, 4).subscribe({ next: f => this.forecast.set(f), error: e => this.error.set(errorMessage(e)) });
    this.api.demand(this.crop).subscribe({ next: d => this.demand.set(d), error: () => this.demand.set(null) });
    this.api.bestMarkets(this.crop, this.county).subscribe({ next: m => this.markets.set(m), error: () => this.markets.set([]) });
    this.api.latestPrices(this.crop).subscribe({ next: l => this.latest.set(l), error: () => this.latest.set([]) });
  }
}
