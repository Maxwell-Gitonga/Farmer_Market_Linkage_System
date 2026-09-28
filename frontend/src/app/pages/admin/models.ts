import { Component, inject, OnDestroy, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { ToastService } from '../../core/toast.service';

/** Machine Learning and Data Analysis module: status, evaluation metrics, retraining. */
@Component({
  selector: 'app-admin-models',
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>ML models</h1><div class="sub">Price prediction (regression), demand forecasting (time series), recommendations</div></div>
        <button class="btn btn-primary" (click)="retrain()" [disabled]="s()?.retraining || s()?.status !== 'UP'">
          @if (s()?.retraining) { <span class="spinner"></span> Retraining… } @else { Retrain on database data }
        </button>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      @if (s(); as s) {
        @if (s.status !== 'UP') {
          <div class="alert error mb"><strong>ML module {{ s.status }}</strong>{{ s.message }}</div>
        }
        @if (s.lastRetrain?.status) {
          <div class="alert mb" [class.ok]="s.lastRetrain.status === 'COMPLETED'" [class.error]="s.lastRetrain.status === 'FAILED'">
            <strong>Last retraining: {{ s.lastRetrain.status }}</strong>
            {{ s.lastRetrain.modelVersion }} {{ s.lastRetrain.message }} {{ s.lastRetrain.finishedAt }}</div>
        }
        @if (pm(); as pm) {
          <div class="grid-4">
            <div class="card"><div class="stat-label">Selected price model</div><div class="stat-value small">{{ pm.selected_model?.replaceAll('_', ' ') }}</div><div class="stat-sub">{{ pm.version }}</div></div>
            <div class="card"><div class="stat-label">Price MAPE (hold-out)</div><div class="stat-value">{{ pct(pm.comparison?.[pm.selected_model]?.mape) }}</div><div class="stat-sub">naive {{ pct(pm.comparison?.naive_last_price_baseline?.mape) }} · {{ pm.improvement_over_naive_pct }}% better</div></div>
            <div class="card"><div class="stat-label">Price R²</div><div class="stat-value">{{ pm.comparison?.[pm.selected_model]?.r2 }}</div><div class="stat-sub">MAE KES {{ pm.comparison?.[pm.selected_model]?.mae }}/kg</div></div>
            <div class="card"><div class="stat-label">Demand MAPE</div><div class="stat-value">{{ pct(dm()?.mean_mape) }}</div><div class="stat-sub">naive {{ pct(dm()?.mean_naive_mape) }} · seasonal-naive {{ pct(dm()?.mean_seasonal_naive_mape) }}</div></div>
          </div>

          <div class="section-title">Price model comparison <small class="muted">(time-based hold-out: last {{ pm.holdout_days }} days, {{ pm.test_samples }} test samples)</small></div>
          <div class="table-wrap"><table>
            <thead><tr><th>Model</th><th class="num">MAE</th><th class="num">RMSE</th><th class="num">MAPE</th><th class="num">R²</th></tr></thead>
            <tbody>@for (e of entries(pm.comparison); track e[0]) {
              <tr [class.sel]="e[0] === pm.selected_model"><td>{{ e[0].replaceAll('_', ' ') }} @if (e[0] === pm.selected_model) { <span class="pill conf-high">selected</span> }</td>
                <td class="num">{{ e[1].mae }}</td><td class="num">{{ e[1].rmse }}</td><td class="num">{{ pct(e[1].mape) }}</td><td class="num">{{ e[1].r2 }}</td></tr> }</tbody>
          </table></div>

          <div class="grid-2 mt">
            <div>
              <div class="section-title">Price accuracy by crop</div>
              <div class="table-wrap"><table>
                <thead><tr><th>Crop</th><th class="num">Model MAPE</th><th class="num">Naive MAPE</th></tr></thead>
                <tbody>@for (e of entries(pm.per_crop); track e[0]) { <tr><td>{{ e[0] }}</td><td class="num">{{ pct(e[1].mape) }}</td><td class="num">{{ pct(e[1].naive_mape) }}</td></tr> }</tbody>
              </table></div>
            </div>
            <div>
              <div class="section-title">Demand forecast accuracy by crop <small class="muted">({{ dm()?.method }})</small></div>
              <div class="table-wrap"><table>
                <thead><tr><th>Crop</th><th class="num">Model</th><th class="num">Naive</th><th class="num">Seasonal naive</th></tr></thead>
                <tbody>@for (e of entries(dm()?.per_crop); track e[0]) { <tr><td>{{ e[0] }}</td><td class="num">{{ pct(e[1].mape) }}</td><td class="num">{{ pct(e[1].naive_mape) }}</td><td class="num">{{ pct(e[1].seasonal_naive_mape) }}</td></tr> }</tbody>
              </table></div>
            </div>
          </div>
          <p class="muted mt">Trained on {{ s.metrics?.last_training?.price_records }} market prices and {{ s.metrics?.last_training?.transactions }} transactions
            ({{ s.metrics?.data_source?.replaceAll('_', ' ') }}) at {{ s.metrics?.last_training?.at }}. Models are retrained automatically every Sunday at 02:00.</p>
        }
      } @else if (!error()) { <div class="card"><div class="skeleton big"></div></div> }
    </div>
  `,
  styles: [`.stat-value.small { font-size: 1.2rem; text-transform: capitalize; } tr.sel td { background: var(--surface-2); }`]
})
export class AdminModelsPage implements OnDestroy {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  s = signal<any>(null);
  error = signal('');
  private timer: any;

  pm = () => this.s()?.metrics?.price_model;
  dm = () => this.s()?.metrics?.demand_model;

  constructor() { this.load(); }
  ngOnDestroy() { clearInterval(this.timer); }

  load() {
    this.api.mlStatus().subscribe({
      next: s => {
        this.s.set(s);
        clearInterval(this.timer);
        if (s.retraining) this.timer = setInterval(() => this.load(), 5000);
      },
      error: e => this.error.set(errorMessage(e))
    });
  }
  entries(o: any): [string, any][] { return Object.entries(o || {}); }
  pct(v: number | undefined) { return v === undefined || v === null ? '—' : (v * 100).toFixed(2) + '%'; }
  retrain() {
    this.api.retrain().subscribe({
      next: r => { this.toast.info(r.message); setTimeout(() => this.load(), 1500); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
