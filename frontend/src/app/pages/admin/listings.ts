import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { ProductStatus, ProductView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AgoPipe, KesPipe, NumPipe } from '../../shared/format';
import { StatusBadge } from '../../shared/trend-badge';

/** Listing control: remove unwanted product listings. */
@Component({
  selector: 'app-admin-listings',
  imports: [FormsModule, KesPipe, NumPipe, AgoPipe, StatusBadge],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Listings</h1><div class="sub">Monitor and remove unwanted listings</div></div>
        <select [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">Open & removed</option><option value="AVAILABLE">Available</option><option value="DRAFT">Draft</option><option value="REMOVED">Removed</option><option value="SOLD">Sold</option>
        </select>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="table-wrap">
        <table>
          <thead><tr><th>#</th><th>Produce</th><th class="num">Quantity</th><th class="num">Price</th><th class="num">vs market</th><th>Farmer</th><th>County</th><th>Status</th><th>Listed</th><th></th></tr></thead>
          <tbody>
            @for (p of items(); track p.id) {
              <tr>
                <td class="faint">{{ p.id }}</td><td>{{ p.emoji }} {{ p.cropType }}</td>
                <td class="num">{{ p.quantity | num }} {{ p.unit }}</td><td class="num">{{ p.pricePerUnit | kes }}</td>
                <td class="num" [class.down]="(p.priceVsMarketPct ?? 0) > 25">{{ p.priceVsMarketPct === null ? '—' : p.priceVsMarketPct + '%' }}</td>
                <td>{{ p.farmerName }}</td><td>{{ p.location }}</td><td><app-status [status]="p.status" /></td>
                <td class="muted">{{ p.createdAt | ago }}</td>
                <td>@if (p.status === 'AVAILABLE' || p.status === 'DRAFT') { <button class="btn btn-sm btn-danger" (click)="remove(p)">Remove</button> }</td>
              </tr>
            } @empty { <tr><td colspan="10" class="muted center">No listings</td></tr> }
          </tbody>
        </table>
      </div>
      <p class="muted mt">Prices far above the local market (highlighted) may indicate a mistaken or unwanted listing.</p>
    </div>
  `
})
export class AdminListingsPage {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  items = signal<ProductView[]>([]);
  error = signal('');
  status: ProductStatus | '' = '';
  constructor() { this.load(); }
  load() { this.api.adminListings(this.status).subscribe({ next: l => this.items.set(l), error: e => this.error.set(errorMessage(e)) }); }
  remove(p: ProductView) {
    const reason = prompt(`Remove ${p.cropType} listing #${p.id} by ${p.farmerName}? Reason:`, '');
    if (reason === null) return;
    this.api.removeListing(p.id, reason).subscribe({
      next: () => { this.toast.success('Listing removed'); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
