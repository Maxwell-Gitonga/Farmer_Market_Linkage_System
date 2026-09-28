import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { MetaView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { StatusBadge } from '../../shared/trend-badge';

@Component({
  selector: 'app-profile',
  imports: [FormsModule, StatusBadge],
  template: `
    <div class="page narrow">
      <div class="page-head"><div><h1>My profile</h1><div class="sub">{{ u()?.role }} · <app-status [status]="u()?.status" /></div></div></div>
      <div class="card">
        <div class="form-grid">
          <div class="field"><label>Name</label><input [(ngModel)]="name" maxlength="100" /></div>
          <div class="field"><label>Phone (used to log in)</label><input [value]="u()?.phone || ''" disabled /></div>
          <div class="field"><label>Email</label><input type="email" [(ngModel)]="email" /></div>
          <div class="field"><label>County</label>
            <select [(ngModel)]="location">@for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }</select></div>
        </div>
        @if (u()?.role === 'BUYER') {
          <div class="field mt"><label>Produce you buy (improves recommendations)</label>
            <div class="chips">@for (c of meta()?.crops || []; track c.name) {
              <button type="button" class="chip" [class.on]="prefs().includes(c.name)" (click)="toggle(c.name)">{{ c.emoji }} {{ c.name }}</button> }</div>
          </div>
        }
        <button class="btn btn-primary mt-lg" (click)="save()" [disabled]="busy()">Save changes</button>
      </div>
    </div>
  `,
  styles: [`.narrow { max-width: 820px; }`]
})
export class ProfilePage {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  u = this.auth.user;
  meta = signal<MetaView | null>(null);
  prefs = signal<string[]>([]);
  busy = signal(false);
  name = '';
  email = '';
  location = '';

  constructor() {
    this.api.meta().subscribe(m => this.meta.set(m));
    this.auth.refresh().subscribe({ next: u => this.fill(), error: () => this.fill() });
    this.fill();
  }

  fill() {
    const u = this.auth.user();
    this.name = u?.name || '';
    this.email = u?.email || '';
    this.location = u?.location || '';
    this.prefs.set((u?.preferences || '').split(',').map(s => s.trim()).filter(Boolean));
  }

  toggle(c: string) { this.prefs.update(p => p.includes(c) ? p.filter(x => x !== c) : [...p, c]); }

  save() {
    this.busy.set(true);
    this.api.updateProfile({ name: this.name, email: this.email, location: this.location,
      preferences: this.u()?.role === 'BUYER' ? this.prefs().join(',') : null }).subscribe({
      next: u => { this.busy.set(false); this.auth.setUser(u); this.toast.success('Profile updated'); },
      error: e => { this.busy.set(false); this.toast.error(errorMessage(e)); }
    });
  }
}
