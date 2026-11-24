import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { MetaView, Role } from '../../core/models';

/** Login screen: one sign-in for everyone (the role comes from the account). The Farmer | Buyer choice appears only when registering. */
@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="wrap">
      <div class="panel">
        <div class="logo">🌾</div>
        <h1 class="center">Farmer Market Linkage</h1>
        <p class="center muted tagline">Sell directly to buyers · price recommendations · demand forecasts</p>

        <div class="seg mt-lg">
          <button type="button" [class.on]="mode() === 'login'" (click)="mode.set('login'); error.set('')">Login</button>
          <button type="button" [class.on]="mode() === 'register'" (click)="mode.set('register'); error.set('')">Register</button>
        </div>

        @if (mode() === 'register') {
          <div class="choice mt">
            <button type="button" [class.on]="role() === 'FARMER'" (click)="role.set('FARMER')">Farmer</button>
            <button type="button" [class.on]="role() === 'BUYER'" (click)="role.set('BUYER')">Buyer</button>
          </div>
        }

        <form class="stack mt" (ngSubmit)="submit()" #f="ngForm">
          @if (mode() === 'register') {
            <div class="field">
              <label for="name">{{ role() === 'BUYER' ? 'Name or business name' : 'Full name' }}</label>
              <input id="name" name="name" [(ngModel)]="name" required maxlength="100" autocomplete="name" />
            </div>
          }
          <div class="field">
            <label for="phone">Phone number{{ mode() === 'login' ? ' (administrators: email)' : '' }}</label>
            <input id="phone" name="phone" [(ngModel)]="phone" required [attr.inputmode]="mode() === 'register' ? 'tel' : null"
                   placeholder="07XX XXX XXX" autocomplete="username" />
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input id="password" name="password" type="password" [(ngModel)]="password" required minlength="6"
                   [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" />
            @if (mode() === 'register') { <span class="hint">At least 6 characters</span> }
          </div>
          @if (mode() === 'register') {
            <div class="field">
              <label for="location">County</label>
              <select id="location" name="location" [(ngModel)]="location" required>
                <option value="" disabled>Select your county</option>
                @for (c of meta()?.counties || []; track c) { <option [value]="c">{{ c }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="email">Email (optional)</label>
              <input id="email" name="email" type="email" [(ngModel)]="email" autocomplete="email" />
            </div>
            @if (role() === 'BUYER') {
              <div class="field">
                <label>Produce you usually buy (used to recommend listings)</label>
                <div class="chips">
                  @for (c of meta()?.crops || []; track c.name) {
                    <button type="button" class="chip" [class.on]="prefs().includes(c.name)" (click)="togglePref(c.name)">{{ c.emoji }} {{ c.name }}</button>
                  }
                </div>
              </div>
            } @else {
              <p class="hint muted small-note">New farmer accounts are reviewed by an administrator before listings go live.</p>
            }
          }
          @if (error()) { <div class="alert error">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" type="submit" [disabled]="busy() || f.invalid">
            @if (busy()) { <span class="spinner"></span> } {{ mode() === 'login' ? 'Log in' : 'Create account' }}
          </button>
        </form>

        @if (mode() === 'login') {
          <details class="demo mt">
            <summary>Demo accounts</summary>
            <div class="demo-grid">
              <button type="button" class="link" (click)="fill('0711000001', 'Farmer@123', 'FARMER')">Farmer · 0711000001 / Farmer&#64;123</button>
              <button type="button" class="link" (click)="fill('0722000001', 'Buyer@123', 'BUYER')">Buyer · 0722000001 / Buyer&#64;123</button>
              <button type="button" class="link" (click)="fill('admin@fmls.co.ke', 'Admin@123', 'FARMER')">Admin · admin&#64;fmls.co.ke / Admin&#64;123</button>
              <button type="button" class="link" (click)="fill('0711000099', 'Farmer@123', 'FARMER')">Pending farmer · 0711000099 / Farmer&#64;123</button>
            </div>
          </details>
        }
        <p class="foot">Web-Based Farmer Market Linkage System with Integrated Decision Support for Price Optimization and Demand Analysis</p>
      </div>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px;
      background: radial-gradient(ellipse at top, #1b1b1b, #0e0e0e 60%); }
    .panel { width: min(460px, 100%); background: var(--surface); border: 1px solid var(--border-strong); border-radius: 22px; padding: 34px 32px; }
    .logo { width: 64px; height: 64px; margin: 0 auto 18px; border: 1px solid var(--border-strong); border-radius: 16px; display: grid; place-items: center; font-size: 32px; }
    h1 { font-size: 1.5rem; margin-bottom: 4px; }
    .tagline { font-size: .9rem; }
    .demo { border: 1px dashed var(--border-strong); border-radius: 10px; padding: 10px 14px; color: var(--muted); font-size: .88rem; }
    .demo summary { cursor: pointer; }
    .demo-grid { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; align-items: flex-start; }
    .foot { color: var(--faint); font-size: .76rem; text-align: center; margin: 22px 0 0; }
    .small-note { font-size: .85rem; margin: 0; }
    @media (max-width: 480px) { .panel { padding: 26px 18px; } }
  `]
})
export class LoginPage {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private router = inject(Router);

  mode = signal<'login' | 'register'>('login');
  role = signal<Role>('FARMER');
  meta = signal<MetaView | null>(null);
  prefs = signal<string[]>([]);
  busy = signal(false);
  error = signal('');
  name = '';
  phone = '';
  password = '';
  location = '';
  email = '';

  constructor() {
    this.api.meta().subscribe({ next: m => this.meta.set(m), error: () => {} });
  }

  togglePref(c: string) {
    this.prefs.update(p => p.includes(c) ? p.filter(x => x !== c) : [...p, c]);
  }

  fill(phone: string, password: string, role: Role) {
    this.phone = phone;
    this.password = password;
    this.role.set(role);
  }

  submit() {
    this.error.set('');
    this.busy.set(true);
    const done = {
      next: () => { this.busy.set(false); this.router.navigateByUrl(this.auth.home()); },
      error: (e: any) => { this.busy.set(false); this.error.set(errorMessage(e)); }
    };
    if (this.mode() === 'login') {
      // No role is sent: the backend works out who is logging in from the phone number or email.
      this.auth.login(this.phone.trim(), this.password).subscribe(done);
    } else {
      this.auth.register({
        role: this.role(), name: this.name.trim(), phone: this.phone.trim(), password: this.password,
        location: this.location, email: this.email.trim() || null,
        preferences: this.role() === 'BUYER' ? this.prefs().join(',') : null
      }).subscribe(done);
    }
  }
}
