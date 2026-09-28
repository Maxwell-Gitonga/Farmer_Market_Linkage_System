import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <router-outlet />
    <div class="toasts" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast ' + t.kind" (click)="toast.dismiss(t.id)">{{ t.text }}</div>
      }
    </div>
  `,
  styles: [`
    .toasts { position: fixed; right: 18px; bottom: 18px; display: flex; flex-direction: column; gap: 10px; z-index: 100; max-width: min(420px, calc(100vw - 36px)); }
    .toast { background: var(--surface-3); border: 1px solid var(--border-strong); border-left-width: 4px; border-radius: 10px;
      padding: 12px 16px; cursor: pointer; box-shadow: 0 8px 30px rgba(0,0,0,.4); animation: rise .2s ease-out; }
    .toast.success { border-left-color: var(--accent); }
    .toast.error { border-left-color: var(--danger); }
    .toast.info { border-left-color: var(--accent-3); }
    @keyframes rise { from { transform: translateY(8px); opacity: 0; } to { transform: none; opacity: 1; } }
  `]
})
export class App {
  toast = inject(ToastService);
}
