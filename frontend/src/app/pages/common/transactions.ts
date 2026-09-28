import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { TransactionStatus, TransactionView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { KesPipe, NumPipe } from '../../shared/format';
import { StatusBadge } from '../../shared/trend-badge';

/** Farmer "Sales" and buyer "My purchases". */
@Component({
  selector: 'app-transactions',
  imports: [KesPipe, NumPipe, StatusBadge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>{{ isFarmer() ? 'Sales' : 'My purchases' }}</h1>
          <div class="sub">{{ isFarmer() ? 'Orders from buyers – mark them completed after delivery' : 'Produce you have bought from farmers' }}</div>
        </div>
      </div>
      <div class="grid-3 mb">
        <div class="card"><div class="stat-label">Open orders</div><div class="stat-value">{{ open() }}</div></div>
        <div class="card"><div class="stat-label">Completed (listed period)</div><div class="stat-value">{{ completed() }}</div></div>
        <div class="card"><div class="stat-label">Total value</div><div class="stat-value">{{ total() | kes: 0 }}</div></div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      @if (items().length) {
        <div class="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Date</th><th>Produce</th><th class="num">Quantity</th><th class="num">Price</th><th class="num">Total</th>
              <th>{{ isFarmer() ? 'Buyer' : 'Farmer' }}</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (t of items(); track t.id) {
                <tr>
                  <td class="faint">{{ t.id }}</td>
                  <td>{{ date(t.transactionDate) }}</td>
                  <td>{{ t.cropType }}</td>
                  <td class="num">{{ t.quantity | num }} {{ t.unit }}</td>
                  <td class="num">{{ t.agreedPrice | kes }}</td>
                  <td class="num"><strong>{{ t.total | kes: 0 }}</strong></td>
                  <td>{{ isFarmer() ? t.buyerName : t.farmerName }}<br><small class="muted">{{ isFarmer() ? t.buyerPhone : t.farmerPhone }}</small></td>
                  <td><app-status [status]="t.status" /></td>
                  <td class="actions">
                    @if (t.status === 'CONFIRMED') {
                      @if (isFarmer()) { <button class="btn btn-sm btn-primary" (click)="set(t, 'COMPLETED')">Mark completed</button> }
                      <button class="btn btn-sm btn-danger" (click)="set(t, 'CANCELLED')">Cancel</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading()) {
        <div class="empty">No transactions yet.</div>
      }
    </div>
  `,
  styles: [`.actions { white-space: nowrap; display: flex; gap: 6px; }`]
})
export class TransactionsPage {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  items = signal<TransactionView[]>([]);
  loading = signal(true);
  error = signal('');
  isFarmer = computed(() => this.auth.role() === 'FARMER');
  open = computed(() => this.items().filter(t => t.status === 'CONFIRMED').length);
  completed = computed(() => this.items().filter(t => t.status === 'COMPLETED').length);
  total = computed(() => this.items().filter(t => t.status !== 'CANCELLED').reduce((s, t) => s + t.total, 0));

  constructor() { this.load(); }

  load() {
    this.api.myTransactions().subscribe({
      next: t => { this.items.set(t); this.loading.set(false); },
      error: e => { this.loading.set(false); this.error.set(errorMessage(e)); }
    });
  }

  date(s: string) { return new Date(s).toLocaleDateString('en-KE', { day: 'numeric', month: 'short', year: 'numeric' }); }

  set(t: TransactionView, status: TransactionStatus) {
    if (status === 'CANCELLED' && !confirm(`Cancel transaction #${t.id}? The quantity is returned to the listing.`)) return;
    this.api.setTransactionStatus(t.id, status).subscribe({
      next: () => { this.toast.success(status === 'COMPLETED' ? 'Sale completed – recorded as market data' : 'Transaction cancelled'); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
