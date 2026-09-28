import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { ImportResult, LatestPriceView, MetaView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { KesPipe, isoDate } from '../../shared/format';

/** Market data management: manual entries, CSV import, latest prices. */
@Component({
  selector: 'app-admin-market-data',
  imports: [FormsModule, KesPipe],
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Market data</h1><div class="sub">Historical prices used by the price-prediction model</div></div></div>
      <div class="grid-2">
        <div class="card">
          <h3>Record a market price</h3>
          <div class="form-grid">
            <div class="field"><label>Crop</label><select [(ngModel)]="crop">@for (c of meta()?.crops || []; track c.name) { <option [value]="c.name">{{ c.name }}</option> }</select></div>
            <div class="field"><label>Market (county)</label><select [(ngModel)]="market">@for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }</select></div>
            <div class="field"><label>Price (KES/kg)</label><input type="number" min="1" step="0.5" [(ngModel)]="price" /></div>
            <div class="field"><label>Date</label><input type="date" [(ngModel)]="date" /></div>
          </div>
          <button class="btn btn-primary mt" (click)="add()" [disabled]="!price || price <= 0">Save price</button>
        </div>
        <div class="card">
          <h3>Import CSV</h3>
          <p class="muted">Columns: <code>crop_type, market_location, price, recorded_date</code> (date as YYYY-MM-DD).</p>
          <input type="file" accept=".csv,text/csv" (change)="pick($event)" />
          <button class="btn btn-primary mt" (click)="upload()" [disabled]="!file">Import</button>
          @if (result(); as r) {
            <div class="alert mt" [class.ok]="!r.skipped" [class.warn]="r.skipped">
              <strong>{{ r.imported }} imported, {{ r.skipped }} skipped</strong>
              @for (e of r.errors; track e) { <div class="muted">{{ e }}</div> }
            </div>
          }
          <p class="muted mt">Tip: retrain the models afterwards (ML models page) so predictions use the new data.</p>
        </div>
      </div>
      <div class="section-title row between">Latest prices
        <select [(ngModel)]="filter" (ngModelChange)="load()" style="width:200px"><option value="">All crops</option>
          @for (c of meta()?.crops || []; track c.name) { <option [value]="c.name">{{ c.name }}</option> }</select></div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="table-wrap">
        <table>
          <thead><tr><th>Crop</th><th>Market</th><th class="num">Price</th><th class="num">7-day change</th><th>Date</th></tr></thead>
          <tbody>
            @for (p of latest(); track p.cropType + p.market) {
              <tr><td>{{ p.cropType }}</td><td>{{ p.market }}</td><td class="num">{{ p.price | kes }}</td>
                <td class="num" [class.up]="(p.change7dPct ?? 0) > 0" [class.down]="(p.change7dPct ?? 0) < 0">{{ p.change7dPct === null ? '—' : p.change7dPct + '%' }}</td><td class="muted">{{ p.date }}</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AdminMarketDataPage {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  meta = signal<MetaView | null>(null);
  latest = signal<LatestPriceView[]>([]);
  result = signal<ImportResult | null>(null);
  error = signal('');
  crop = 'Maize';
  market = 'Nairobi';
  price: number | null = null;
  date = isoDate(new Date());
  filter = '';
  file: File | null = null;

  constructor() {
    this.api.meta().subscribe(m => this.meta.set(m));
    this.load();
  }
  load() { this.api.latestPrices(this.filter || undefined).subscribe({ next: l => this.latest.set(l), error: e => this.error.set(errorMessage(e)) }); }
  add() {
    this.api.addMarketPrice({ cropType: this.crop, marketLocation: this.market, price: this.price, recordedDate: this.date }).subscribe({
      next: () => { this.toast.success('Market price saved'); this.price = null; this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
  pick(ev: Event) { this.file = (ev.target as HTMLInputElement).files?.[0] || null; }
  upload() {
    if (!this.file) return;
    this.api.importMarketPrices(this.file).subscribe({
      next: r => { this.result.set(r); this.toast.success(`${r.imported} prices imported`); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
