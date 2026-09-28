import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; text: string; kind: 'success' | 'error' | 'info'; }

/** Small notification banners (e.g. "Purchase confirmed"). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private next = 1;

  show(text: string, kind: Toast['kind'] = 'info', ms = 4000) {
    const t = { id: this.next++, text, kind };
    this.toasts.update(l => [...l, t]);
    setTimeout(() => this.dismiss(t.id), ms);
  }
  success(text: string) { this.show(text, 'success'); }
  error(text: string) { this.show(text, 'error', 6000); }
  info(text: string) { this.show(text, 'info'); }
  dismiss(id: number) { this.toasts.update(l => l.filter(t => t.id !== id)); }
}
