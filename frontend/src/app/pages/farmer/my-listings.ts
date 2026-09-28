import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { ProductView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { KesPipe, NumPipe, shortDate } from '../../shared/format';
import { StatusBadge } from '../../shared/trend-badge';

/** Product Listing and Management: view, modify and delete listings. */
@Component({
  selector: 'app-my-listings',
  imports: [FormsModule, RouterLink, KesPipe, NumPipe, StatusBadge],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>My listings</h1><div class="sub">Drafts, live listings and recent sales</div></div>
        <a class="btn btn-primary" routerLink="/farmer/listings/new">+ List new produce</a>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="tabs">
        @for (t of tabs; track t.key) {
          <button [class.on]="tab() === t.key" (click)="tab.set(t.key)">{{ t.label }} ({{ count(t.key) }})</button>
        }
      </div>
      @if (filtered().length) {
        <div class="list">
          @for (p of filtered(); track p.id) {
            <div class="list-row">
              <div class="thumb">{{ p.emoji }}</div>
              <div class="main">
                <div class="title">{{ p.cropType }} · {{ p.quantity | num: 0 }} {{ p.unit }} <span class="faint">#{{ p.id }}</span></div>
                <div class="meta">{{ p.pricePerUnit !== null ? (p.pricePerUnit | kes) + '/' + p.unit : 'No price yet' }}
                  @if (p.recommendedPrice) { · recommended {{ p.recommendedPrice | kes }} }
                  · {{ p.location }} · harvest {{ fmt(p.harvestDate) }}</div>
              </div>
              <app-status [status]="p.status" />
              <div class="row actions">
                @if (p.status === 'DRAFT') {
                  <a class="btn btn-sm btn-primary" [routerLink]="['/farmer/listings', p.id, 'recommendation']">Set price</a>
                }
                @if (p.status === 'AVAILABLE') {
                  <a class="btn btn-sm" [routerLink]="['/farmer/listings', p.id, 'matches']">Buyers</a>
                  <a class="btn btn-sm" [routerLink]="['/farmer/listings', p.id, 'recommendation']">Pricing</a>
                }
                @if (p.status === 'DRAFT' || p.status === 'AVAILABLE') {
                  <button class="btn btn-sm" (click)="edit(p)">Edit</button>
                  <button class="btn btn-sm btn-danger" (click)="remove(p)">Delete</button>
                }
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="empty">Nothing here yet.</div>
      }
    </div>

    @if (editing(); as e) {
      <div class="modal-back" (click)="editing.set(null)">
        <div class="modal" (click)="$event.stopPropagation()">
          <h3>Edit {{ e.cropType }} #{{ e.id }}</h3>
          <div class="stack mt">
            <div class="field"><label>Quantity (kg)</label><input type="number" min="1" [(ngModel)]="form.quantity" /></div>
            <div class="field"><label>Harvest date</label><input type="date" [(ngModel)]="form.harvestDate" /></div>
            @if (e.status === 'AVAILABLE') {
              <div class="field"><label>Price per kg (KES)</label><input type="number" min="1" step="0.5" [(ngModel)]="form.pricePerUnit" /></div>
            }
            <div class="field"><label>Description</label><textarea [(ngModel)]="form.description" maxlength="500"></textarea></div>
          </div>
          <div class="row mt">
            <button class="btn btn-primary" (click)="save(e)">Save changes</button>
            <button class="btn" (click)="editing.set(null)">Cancel</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`.actions { gap: 6px; justify-content: flex-end; } @media (max-width: 700px) { .list-row { flex-wrap: wrap; } .actions { width: 100%; justify-content: flex-start; } }`]
})
export class MyListingsPage {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private router = inject(Router);
  listings = signal<ProductView[]>([]);
  error = signal('');
  tab = signal<'open' | 'DRAFT' | 'AVAILABLE' | 'SOLD'>('open');
  editing = signal<ProductView | null>(null);
  form: any = {};
  fmt = shortDate;
  tabs = [
    { key: 'open' as const, label: 'All open' }, { key: 'AVAILABLE' as const, label: 'Live' },
    { key: 'DRAFT' as const, label: 'Drafts' }, { key: 'SOLD' as const, label: 'Sold' }
  ];

  constructor() { this.load(); }

  load() {
    this.api.myProducts().subscribe({ next: l => this.listings.set(l), error: e => this.error.set(errorMessage(e)) });
  }

  count(k: string) { return this.listings().filter(p => this.match(p, k)).length; }
  filtered() { return this.listings().filter(p => this.match(p, this.tab())); }
  private match(p: ProductView, k: string) { return k === 'open' ? (p.status === 'DRAFT' || p.status === 'AVAILABLE') : p.status === k; }

  edit(p: ProductView) {
    this.form = { quantity: p.quantity, harvestDate: p.harvestDate, pricePerUnit: p.pricePerUnit, description: p.description };
    this.editing.set(p);
  }

  save(p: ProductView) {
    const body: any = { quantity: this.form.quantity, harvestDate: this.form.harvestDate, description: this.form.description };
    if (p.status === 'AVAILABLE') body.pricePerUnit = this.form.pricePerUnit;
    this.api.updateProduct(p.id, body).subscribe({
      next: () => { this.toast.success('Listing updated'); this.editing.set(null); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }

  remove(p: ProductView) {
    if (!confirm(`Delete the ${p.cropType} listing #${p.id}?`)) return;
    this.api.deleteProduct(p.id).subscribe({
      next: () => { this.toast.success('Listing deleted'); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
