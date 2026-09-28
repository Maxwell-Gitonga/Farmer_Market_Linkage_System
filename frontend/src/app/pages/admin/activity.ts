import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { LogView } from '../../core/models';

/** Monitor system activity. */
@Component({
  selector: 'app-admin-activity',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>System activity</h1><div class="sub">Latest 300 events</div></div>
        <div class="row">
          <select [(ngModel)]="action" (ngModelChange)="load()">
            <option value="">All events</option>
            @for (a of actions; track a) { <option [value]="a">{{ a }}</option> }
          </select>
          <button class="btn" (click)="load()">↻ Refresh</button>
        </div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="table-wrap">
        <table>
          <thead><tr><th>Time</th><th>Event</th><th>User</th><th>Details</th></tr></thead>
          <tbody>
            @for (l of logs(); track l.id) {
              <tr><td class="muted nowrap">{{ time(l.createdAt) }}</td><td><span class="pill">{{ l.action }}</span></td>
                <td>{{ l.actorName }}<br><small class="muted">{{ l.actorRole?.toLowerCase() }}</small></td><td>{{ l.details }}</td></tr>
            } @empty { <tr><td colspan="4" class="muted center">No events</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`.nowrap { white-space: nowrap; }`]
})
export class AdminActivityPage {
  private api = inject(ApiService);
  logs = signal<LogView[]>([]);
  error = signal('');
  action = '';
  actions = ['LOGIN', 'REGISTER', 'LISTING_CREATED', 'LISTING_PUBLISHED', 'LISTING_UPDATED', 'LISTING_DELETED', 'PURCHASE',
    'TRANSACTION_COMPLETED', 'TRANSACTION_CANCELLED', 'MESSAGE_SENT', 'FARMER_APPROVED', 'USER_STATUS_CHANGED',
    'LISTING_REMOVED_BY_ADMIN', 'MARKET_FEED', 'MARKET_PRICE_ADDED', 'MARKET_PRICES_IMPORTED', 'ML_RETRAINED', 'REPORT_EXPORTED'];
  constructor() { this.load(); }
  load() { this.api.logs(this.action).subscribe({ next: l => this.logs.set(l), error: e => this.error.set(errorMessage(e)) }); }
  time(s: string) { return new Date(s).toLocaleString('en-KE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }); }
}
