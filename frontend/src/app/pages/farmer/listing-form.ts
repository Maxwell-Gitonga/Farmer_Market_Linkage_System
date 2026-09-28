import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription, debounceTime, switchMap, of, catchError } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { MetaView, RecommendationView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { KesPipe, isoDate } from '../../shared/format';
import { ConfidenceBadge, TrendBadge } from '../../shared/trend-badge';

/** Wireframe "Farmer — Product Listing Form" with the system-generated preview. */
@Component({
  selector: 'app-listing-form',
  imports: [FormsModule, KesPipe, TrendBadge, ConfidenceBadge],
  template: `
    <div class="page narrow">
      <div class="page-head">
        <div>
          <h1>List new produce</h1>
          <div class="sub">Enter your produce details – the system suggests a price from market data and demand.</div>
        </div>
      </div>

      <form class="card pad" (ngSubmit)="submit()" #f="ngForm">
        <div class="form-grid">
          <div class="field">
            <label for="crop">Crop type</label>
            <select id="crop" name="crop" [(ngModel)]="crop" (ngModelChange)="changed()" required>
              <option value="" disabled>Select crop</option>
              @for (c of meta()?.crops || []; track c.name) { <option [value]="c.name">{{ c.emoji }} {{ c.name }}</option> }
              <option value="__other">Other crop…</option>
            </select>
            @if (crop === '__other') {
              <input name="otherCrop" [(ngModel)]="otherCrop" (ngModelChange)="changed()" placeholder="e.g. Macadamia" required maxlength="50" />
            }
          </div>
          <div class="field">
            <label for="qty">Quantity (kg)</label>
            <input id="qty" name="qty" type="number" min="1" step="1" [(ngModel)]="quantity" (ngModelChange)="changed()" required />
          </div>
          <div class="field">
            <label for="harvest">Harvest date</label>
            <input id="harvest" name="harvest" type="date" [(ngModel)]="harvestDate" (ngModelChange)="changed()" [min]="minDate" required />
          </div>
          <div class="field">
            <label for="loc">Location</label>
            <select id="loc" name="loc" [(ngModel)]="location" (ngModelChange)="changed()" required>
              @for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }
            </select>
          </div>
          <div class="field span">
            <label for="desc">Description (optional)</label>
            <textarea id="desc" name="desc" [(ngModel)]="description" maxlength="500" placeholder="Variety, grading, packaging, delivery options…"></textarea>
          </div>
        </div>

        <div class="preview mt-lg">
          <div class="preview-head">
            <span class="muted">System-generated (preview)</span>
            @if (previewBusy()) { <span class="spinner"></span> }
          </div>
          @if (preview(); as p) {
            @if (p.sufficientData) {
              <div class="preview-grid">
                <div>
                  <div class="muted small">Recommended price</div>
                  <div class="pv">{{ p.recommendedPrice | kes }} <small class="muted">/kg</small></div>
                  <app-confidence [label]="p.confidenceLabel" />
                </div>
                <div>
                  <div class="muted small">Predicted market price at harvest</div>
                  <div class="pv">{{ p.predictedPrice | kes }}</div>
                  <small class="muted">range {{ p.lowerBound | kes: 0 }} – {{ p.upperBound | kes: 0 }}</small>
                </div>
                <div>
                  <div class="muted small">Demand trend</div>
                  <div class="pv"><app-trend [trend]="p.demand?.sufficientData ? p.demand?.trend : null" [change]="p.demand?.changePct" /></div>
                  <small class="muted">next 4 weeks</small>
                </div>
                <div>
                  <div class="muted small">Estimated value</div>
                  <div class="pv">{{ (p.recommendedPrice || 0) * (quantity || 0) | kes: 0 }}</div>
                  <small class="muted">{{ quantity || 0 }} kg</small>
                </div>
              </div>
              @if (p.message) { <p class="muted small mt">{{ p.message }}</p> }
            } @else {
              <div class="alert warn"><strong>Limited data</strong>{{ p.message }}</div>
            }
          } @else {
            <p class="muted small">Choose a crop, harvest date and location to see the estimated price and demand.</p>
          }
        </div>

        @if (error()) { <div class="alert error mt">{{ error() }}</div> }
        <div class="row mt-lg">
          <button class="btn btn-primary" type="submit" [disabled]="busy() || f.invalid">
            @if (busy()) { <span class="spinner"></span> } Submit listing
          </button>
          <span class="muted small">Next: review the price recommendation, then list at that price or set your own.</span>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .narrow { max-width: 980px; }
    .pad { padding: 26px; }
    .span { grid-column: 1 / -1; }
    .preview { border: 1px dashed var(--border-strong); border-radius: 12px; padding: 18px 20px; }
    .preview-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .preview-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
    .pv { font-size: 1.3rem; font-weight: 700; margin: 4px 0; }
    .small { font-size: .85rem; }
    @media (max-width: 760px) { .preview-grid { grid-template-columns: 1fr 1fr; } }
  `]
})
export class ListingFormPage implements OnDestroy {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);

  meta = signal<MetaView | null>(null);
  preview = signal<RecommendationView | null>(null);
  previewBusy = signal(false);
  busy = signal(false);
  error = signal('');
  crop = '';
  otherCrop = '';
  quantity: number | null = null;
  harvestDate = isoDate(new Date(Date.now() + 7 * 86400000));
  location = this.auth.user()?.location || 'Nairobi';
  description = '';
  minDate = isoDate(new Date(Date.now() - 30 * 86400000));

  private changes = new Subject<void>();
  private sub: Subscription;

  constructor() {
    this.api.meta().subscribe(m => this.meta.set(m));
    this.sub = this.changes.pipe(
      debounceTime(500),
      switchMap(() => {
        const crop = this.cropName();
        if (!crop || !this.location || !this.harvestDate) return of(null);
        this.previewBusy.set(true);
        return this.api.quickEstimate({ cropType: crop, location: this.location, harvestDate: this.harvestDate, quantity: this.quantity })
          .pipe(catchError(() => of(null)));
      })
    ).subscribe(p => { this.previewBusy.set(false); if (p) this.preview.set(p); });
  }

  ngOnDestroy() { this.sub.unsubscribe(); }

  cropName(): string {
    return this.crop === '__other' ? this.otherCrop.trim() : this.crop;
  }

  changed() { this.changes.next(); }

  submit() {
    this.error.set('');
    this.busy.set(true);
    this.api.createProduct({
      cropType: this.cropName(), quantity: this.quantity, unit: 'kg', harvestDate: this.harvestDate,
      location: this.location, description: this.description || null
    }).subscribe({
      next: r => {
        this.busy.set(false);
        this.toast.success(`Listing saved (#${r.product.id}). Review the price recommendation.`);
        this.router.navigate(['/farmer/listings', r.product.id, 'recommendation']);
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); }
    });
  }
}
