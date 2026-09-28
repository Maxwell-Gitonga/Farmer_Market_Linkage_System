import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { MetaView, ProductView, RecommendedProductView } from '../../core/models';
import { KesPipe, NumPipe, shortDate } from '../../shared/format';

/** Wireframe "Buyer Dashboard – Browse listings": search, filters, Recommended for you, All listings. */
@Component({
  selector: 'app-browse',
  imports: [FormsModule, KesPipe, NumPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Browse listings</h1><div class="sub">Buy directly from farmers – no middlemen</div></div>
      </div>

      <div class="filters">
        <input class="search" placeholder="Search produce, e.g. maize, tomatoes…" [(ngModel)]="q" (ngModelChange)="changed()" aria-label="Search listings" />
        <select [(ngModel)]="county" (ngModelChange)="load()" aria-label="County">
          <option value="">All counties</option>
          @for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }
        </select>
        <select [(ngModel)]="sort" (ngModelChange)="load()" aria-label="Sort">
          <option value="newest">Newest</option>
          <option value="price_asc">Price: low → high</option>
          <option value="price_desc">Price: high → low</option>
          <option value="quantity">Largest quantity</option>
          <option value="harvest">Harvest date</option>
        </select>
      </div>

      @if (!q && !county) {
        <div class="section-title muted-title">Recommended for you</div>
        @if (recs().length) {
          <div class="grid-2">
            @for (r of recs().slice(0, 4); track r.product.id) {
              <div class="pcard" (click)="open(r.product)">
                <div class="img">{{ r.product.emoji }}</div>
                <div class="body">
                  <strong>{{ r.product.cropType }} · {{ r.product.quantity | num }} {{ r.product.unit }}</strong>
                  <span class="muted">{{ r.product.farmerName }} · {{ r.product.location }}</span>
                  <div class="row between">
                    <span class="price">{{ r.product.pricePerUnit | kes }}/{{ r.product.unit }}</span>
                    <span class="pill conf-high">{{ (r.score * 100).toFixed(0) }}% match</span>
                  </div>
                  <div class="reasons">@for (x of r.reasons.slice(0, 3); track x) { <span class="pill">{{ x }}</span> }</div>
                </div>
              </div>
            }
          </div>
        } @else if (recsLoading()) {
          <div class="grid-2"><div class="pcard"><div class="img"></div><div class="body"><div class="skeleton"></div></div></div>
            <div class="pcard"><div class="img"></div><div class="body"><div class="skeleton"></div></div></div></div>
        } @else {
          <div class="empty small">Add the produce you buy to your profile to get recommendations.</div>
        }
      }

      <div class="section-title muted-title">All listings <small class="muted">({{ listings().length }})</small></div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      @if (listings().length) {
        <div class="list">
          @for (p of listings(); track p.id) {
            <div class="list-row clickable" (click)="open(p)">
              <div class="thumb">{{ p.emoji }}</div>
              <div class="main">
                <div class="title">{{ p.cropType }} · {{ p.quantity | num }} {{ p.unit }}</div>
                <div class="meta">{{ p.farmerName }} · {{ p.location }} · harvest {{ fmt(p.harvestDate) }}
                  @if (p.priceVsMarketPct !== null) {
                    · <span [class.up]="p.priceVsMarketPct! < 0" [class.down]="p.priceVsMarketPct! > 0">
                      {{ p.priceVsMarketPct! >= 0.5 ? p.priceVsMarketPct + '% above market' : p.priceVsMarketPct! <= -0.5 ? -p.priceVsMarketPct! + '% below market' : 'at market price' }}</span>
                  }
                </div>
              </div>
              <div class="price">{{ p.pricePerUnit | kes }}<small class="muted">/{{ p.unit }}</small></div>
              <button class="btn btn-sm" (click)="$event.stopPropagation(); open(p)">View</button>
            </div>
          }
        </div>
      } @else if (!loading()) {
        <div class="empty">No listings match your search.</div>
      }
    </div>
  `,
  styles: [`
    .filters { display: grid; grid-template-columns: 1fr 190px 190px; gap: 14px; }
    .muted-title { color: var(--muted); font-weight: 500; }
    .reasons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .price { font-weight: 700; white-space: nowrap; }
    @media (max-width: 700px) { .filters { grid-template-columns: 1fr 1fr; } .search { grid-column: 1 / -1; } }
  `]
})
export class BrowsePage implements OnDestroy {
  private api = inject(ApiService);
  private router = inject(Router);
  meta = signal<MetaView | null>(null);
  listings = signal<ProductView[]>([]);
  recs = signal<RecommendedProductView[]>([]);
  recsLoading = signal(true);
  loading = signal(true);
  error = signal('');
  q = '';
  county = '';
  sort = 'newest';
  fmt = shortDate;
  private typing = new Subject<void>();
  private sub: Subscription;

  constructor() {
    this.api.meta().subscribe(m => this.meta.set(m));
    this.api.buyerRecommendations(6).subscribe({
      next: r => { this.recs.set(r); this.recsLoading.set(false); },
      error: () => this.recsLoading.set(false)
    });
    this.sub = this.typing.pipe(debounceTime(300)).subscribe(() => this.load());
    this.load();
  }

  ngOnDestroy() { this.sub.unsubscribe(); }

  changed() { this.typing.next(); }

  /** searchListings(cropType) → getAvailableListings(cropType). */
  load() {
    this.loading.set(true);
    this.api.searchProducts({ crop: this.q.trim(), county: this.county, sort: this.sort }).subscribe({
      next: l => { this.listings.set(l); this.loading.set(false); this.error.set(''); },
      error: e => { this.loading.set(false); this.error.set(errorMessage(e)); }
    });
  }

  open(p: ProductView) { this.router.navigate(['/buyer/product', p.id]); }
}
