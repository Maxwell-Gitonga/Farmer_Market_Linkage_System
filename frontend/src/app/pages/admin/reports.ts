import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { ReportView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Bar, BarChart, LineChart, LineSeries } from '../../shared/charts';
import { KesPipe, NumPipe, downloadBlob } from '../../shared/format';

/** "View system reports". */
@Component({
  selector: 'app-admin-reports',
  imports: [FormsModule, KesPipe, NumPipe, BarChart, LineChart],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Reports</h1><div class="sub">{{ r()?.period }}</div></div>
        <div class="row">
          <select [(ngModel)]="days" (ngModelChange)="load()">
            <option [ngValue]="30">Last 30 days</option><option [ngValue]="90">Last 90 days</option>
            <option [ngValue]="365">Last 12 months</option><option [ngValue]="1100">All time</option>
          </select>
          <button class="btn" (click)="exp('transactions')">⬇ Transactions CSV</button>
          <button class="btn" (click)="exp('listings')">⬇ Listings CSV</button>
          <button class="btn" (click)="exp('users')">⬇ Users CSV</button>
        </div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      @if (r(); as r) {
        <div class="grid-2">
          <div class="card">
            <h3>Monthly trade value (KES)</h3>
            <app-line-chart [series]="monthly()" ariaLabel="Monthly trade value" />
          </div>
          <div class="card">
            <h3>Trade value by crop</h3>
            <app-bar-chart [bars]="cropBars()" [height]="120" highlight="max" unit="KES " [showLabels]="true" />
          </div>
        </div>
        <div class="section-title">By crop</div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>Crop</th><th class="num">Transactions</th><th class="num">Quantity (kg)</th><th class="num">Value</th><th class="num">Avg price/kg</th></tr></thead>
            <tbody>
              @for (c of r.byCrop; track c.cropType) {
                <tr><td>{{ c.cropType }}</td><td class="num">{{ c.transactions | num }}</td><td class="num">{{ c.quantity | num }}</td>
                  <td class="num">{{ c.value | kes: 0 }}</td><td class="num">{{ (c.quantity ? c.value / c.quantity : 0) | kes }}</td></tr>
              }
            </tbody>
          </table>
        </div>
        <div class="grid-3 mt">
          <div class="card"><h3>Users by county</h3>
            <dl class="kv">@for (e of entries(r.usersByCounty); track e[0]) { <dt>{{ e[0] }}</dt><dd>{{ e[1] }}</dd> }</dl></div>
          <div class="card"><h3>Listings by status</h3>
            <dl class="kv">@for (e of entries(r.listingsByStatus); track e[0]) { <dt>{{ e[0] }}</dt><dd>{{ e[1] | num }}</dd> }</dl></div>
          <div class="card"><h3>Transactions by status</h3>
            <dl class="kv">@for (e of entries(r.transactionsByStatus); track e[0]) { <dt>{{ e[0] }}</dt><dd>{{ e[1] | num }}</dd> }</dl></div>
        </div>
        @if (r.modelMetrics?.price_model) {
          <div class="card mt">
            <h3>Decision-support model accuracy</h3>
            <p class="muted">Price model: <strong>{{ r.modelMetrics.price_model.selected_model }}</strong> – MAPE
              {{ pct(r.modelMetrics.price_model.comparison?.[r.modelMetrics.price_model.selected_model]?.mape) }}
              vs naive baseline {{ pct(r.modelMetrics.price_model.comparison?.naive_last_price_baseline?.mape) }}.
              Demand forecast MAPE {{ pct(r.modelMetrics.demand_model?.mean_mape) }} vs naive {{ pct(r.modelMetrics.demand_model?.mean_naive_mape) }}.</p>
          </div>
        }
      } @else if (!error()) { <div class="card"><div class="skeleton big"></div></div> }
    </div>
  `
})
export class AdminReportsPage {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  r = signal<ReportView | null>(null);
  error = signal('');
  days = 365;

  monthly = computed<LineSeries[]>(() => [{ name: 'Trade value', color: 'var(--accent)',
    points: (this.r()?.monthly || []).map(m => ({ x: m.month + '-15', y: m.value })) }]);
  cropBars = computed<Bar[]>(() => (this.r()?.byCrop || []).map(c => ({ label: c.cropType.split(' ')[0], value: c.value })));

  constructor() { this.load(); }
  load() { this.api.reports(this.days).subscribe({ next: r => this.r.set(r), error: e => this.error.set(errorMessage(e)) }); }
  entries(o: Record<string, number>) { return Object.entries(o || {}); }
  pct(v: number | undefined) { return v === undefined || v === null ? '—' : (v * 100).toFixed(1) + '%'; }
  exp(type: string) {
    this.api.exportReport(type, this.days).subscribe({
      next: b => downloadBlob(b, `fmls-${type}.csv`),
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
