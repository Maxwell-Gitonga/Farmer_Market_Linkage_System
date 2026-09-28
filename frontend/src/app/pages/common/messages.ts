import { Component, ElementRef, inject, OnDestroy, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AuthService } from '../../core/auth.service';
import { MessageView, ThreadView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AgoPipe } from '../../shared/format';

/** Direct communication between buyers and farmers ("Contact seller"). */
@Component({
  selector: 'app-messages',
  imports: [FormsModule, AgoPipe],
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Messages</h1><div class="sub">Talk directly with {{ auth.role() === 'FARMER' ? 'buyers' : 'farmers' }}</div></div></div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="layout">
        <div class="threads list">
          @for (t of threads(); track t.counterpartId) {
            <div class="list-row clickable" [class.sel]="t.counterpartId === active()?.id" (click)="open(t.counterpartId, t.counterpartName, t.counterpartPhone)">
              <div class="avatar">{{ initials(t.counterpartName) }}</div>
              <div class="main">
                <div class="title">{{ t.counterpartName }} @if (t.unread) { <span class="badge">{{ t.unread }}</span> }</div>
                <div class="meta ellipsis">{{ t.lastMessage }}</div>
              </div>
              <small class="muted">{{ t.lastAt | ago }}</small>
            </div>
          } @empty {
            <div class="empty small">No conversations yet.</div>
          }
        </div>
        <div class="conv card">
          @if (active(); as a) {
            <div class="conv-head">
              <strong>{{ a.name }}</strong>
              @if (a.phone) { <a class="btn btn-sm" [href]="'tel:' + a.phone">📞 {{ a.phone }}</a> }
            </div>
            <div class="msgs" #scroller>
              @for (m of messages(); track m.id) {
                <div class="msg" [class.mine]="m.mine">
                  @if (m.productLabel) { <div class="about">Re: {{ m.productLabel }}</div> }
                  <div>{{ m.content }}</div>
                  <small>{{ m.createdAt | ago }}</small>
                </div>
              } @empty { <div class="muted center mt">Start the conversation.</div> }
            </div>
            <form class="compose" (ngSubmit)="send()">
              <input name="text" [(ngModel)]="text" placeholder="Type a message…" maxlength="1000" autocomplete="off" />
              <button class="btn btn-primary" [disabled]="!text.trim() || sending()">Send</button>
            </form>
          } @else {
            <div class="empty">Select a conversation.</div>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: 330px 1fr; gap: 16px; min-height: 520px; }
    .threads { max-height: 640px; overflow: auto; }
    .list-row.sel { background: var(--surface-3); }
    .ellipsis { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .badge { background: var(--accent); color: #111; border-radius: 999px; font-size: .72rem; padding: 1px 7px; margin-left: 6px; }
    .conv { display: flex; flex-direction: column; padding: 0; overflow: hidden; }
    .conv-head { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; border-bottom: 1px solid var(--border); }
    .msgs { flex: 1; overflow: auto; padding: 18px; display: flex; flex-direction: column; gap: 10px; max-height: 520px; min-height: 340px; }
    .msg { max-width: 75%; background: var(--surface-3); border-radius: 14px 14px 14px 4px; padding: 10px 14px; align-self: flex-start; }
    .msg.mine { align-self: flex-end; background: #2c3a2d; border-radius: 14px 14px 4px 14px; }
    .msg small { color: var(--muted); font-size: .72rem; display: block; margin-top: 4px; }
    .about { font-size: .75rem; color: var(--accent-2); margin-bottom: 4px; }
    .compose { display: flex; gap: 10px; padding: 12px; border-top: 1px solid var(--border); }
    @media (max-width: 760px) { .layout { grid-template-columns: 1fr; } }
  `]
})
export class MessagesPage implements OnDestroy {
  auth = inject(AuthService);
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  scroller = viewChild<ElementRef<HTMLDivElement>>('scroller');

  threads = signal<ThreadView[]>([]);
  messages = signal<MessageView[]>([]);
  active = signal<{ id: number; name: string; phone: string | null } | null>(null);
  error = signal('');
  sending = signal(false);
  text = '';
  productId: number | null = null;
  private timer: any;

  constructor() {
    const q = this.route.snapshot.queryParamMap;
    const to = q.get('to');
    if (q.get('product')) this.productId = Number(q.get('product'));
    this.loadThreads(() => {
      if (to) {
        const t = this.threads().find(x => x.counterpartId === Number(to));
        this.open(Number(to), t?.counterpartName || q.get('name') || 'Conversation', t?.counterpartPhone || null);
      } else if (this.threads().length) {
        const t = this.threads()[0];
        this.open(t.counterpartId, t.counterpartName, t.counterpartPhone);
      }
    });
    this.timer = setInterval(() => { if (this.active()) this.refresh(); }, 15000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  loadThreads(then?: () => void) {
    this.api.threads().subscribe({ next: t => { this.threads.set(t); then?.(); }, error: e => this.error.set(errorMessage(e)) });
  }

  open(id: number, name: string, phone: string | null) {
    this.active.set({ id, name, phone });
    this.refresh();
  }

  refresh() {
    const a = this.active();
    if (!a) return;
    this.api.thread(a.id).subscribe({
      next: m => {
        this.messages.set(m);
        this.threads.update(ts => ts.map(t => t.counterpartId === a.id ? { ...t, unread: 0 } : t));
        setTimeout(() => { const el = this.scroller()?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
      },
      error: e => this.error.set(errorMessage(e))
    });
  }

  send() {
    const a = this.active();
    if (!a || !this.text.trim()) return;
    this.sending.set(true);
    this.api.sendMessage(a.id, this.text.trim(), this.productId).subscribe({
      next: () => { this.sending.set(false); this.text = ''; this.productId = null; this.refresh(); this.loadThreads(); },
      error: e => { this.sending.set(false); this.toast.error(errorMessage(e)); }
    });
  }

  initials(n: string) { return n.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase(); }
}
