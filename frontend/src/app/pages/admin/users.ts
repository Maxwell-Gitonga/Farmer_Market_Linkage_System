import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/auth.interceptor';
import { AccountStatus, AdminUserView, Role } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AgoPipe } from '../../shared/format';
import { StatusBadge } from '../../shared/trend-badge';

/** Manage users: approve farmer accounts, suspend, ban, reactivate. */
@Component({
  selector: 'app-admin-users',
  imports: [FormsModule, StatusBadge, AgoPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Users</h1><div class="sub">Approve farmers, suspend or ban accounts</div></div>
        <div class="row">
          <select [(ngModel)]="role" (ngModelChange)="load()"><option value="">All roles</option><option value="FARMER">Farmers</option><option value="BUYER">Buyers</option><option value="ADMIN">Admins</option></select>
          <select [(ngModel)]="status" (ngModelChange)="load()"><option value="">Any status</option><option value="PENDING">Pending</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="BANNED">Banned</option></select>
          <input placeholder="Search name or phone" [(ngModel)]="q" style="width:220px" />
        </div>
      </div>
      @if (error()) { <div class="alert error mb">{{ error() }}</div> }
      <div class="table-wrap">
        <table>
          <thead><tr><th>Name</th><th>Role</th><th>Phone / email</th><th>County</th><th>Status</th><th>Joined</th><th>Last login</th><th></th></tr></thead>
          <tbody>
            @for (u of filtered(); track u.role + u.id) {
              <tr>
                <td><strong>{{ u.name }}</strong>@if (u.preferences) {<br><small class="muted">buys: {{ u.preferences }}</small>}</td>
                <td>{{ u.role.toLowerCase() }}</td>
                <td>{{ u.phone }}<br><small class="muted">{{ u.email }}</small></td>
                <td>{{ u.location }}</td>
                <td><app-status [status]="u.status" /></td>
                <td class="muted">{{ u.createdAt | ago }}</td>
                <td class="muted">{{ u.lastLoginAt ? (u.lastLoginAt | ago) : 'never' }}</td>
                <td class="actions">
                  @if (u.role !== 'ADMIN') {
                    @if (u.status === 'PENDING') { <button class="btn btn-sm btn-primary" (click)="set(u, 'ACTIVE')">Approve</button> }
                    @if (u.status === 'ACTIVE') { <button class="btn btn-sm" (click)="set(u, 'SUSPENDED')">Suspend</button> }
                    @if (u.status === 'SUSPENDED' || u.status === 'BANNED') { <button class="btn btn-sm" (click)="set(u, 'ACTIVE')">Reactivate</button> }
                    @if (u.status !== 'BANNED') { <button class="btn btn-sm btn-danger" (click)="set(u, 'BANNED')">Ban</button> }
                  }
                </td>
              </tr>
            } @empty { <tr><td colspan="8" class="muted center">No users found</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`.actions { white-space: nowrap; } .actions .btn { margin-left: 4px; }`]
})
export class AdminUsersPage {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  users = signal<AdminUserView[]>([]);
  error = signal('');
  role: Role | '' = '';
  status: AccountStatus | '' = '';
  q = '';

  constructor(route: ActivatedRoute) {
    this.status = (route.snapshot.queryParamMap.get('status') as AccountStatus) || '';
    this.load();
  }

  load() {
    this.api.adminUsers(this.role, this.status).subscribe({ next: u => this.users.set(u), error: e => this.error.set(errorMessage(e)) });
  }

  filtered() {
    const q = this.q.trim().toLowerCase();
    return q ? this.users().filter(u => (u.name + ' ' + (u.phone || '') + ' ' + (u.email || '')).toLowerCase().includes(q)) : this.users();
  }

  set(u: AdminUserView, status: AccountStatus) {
    let reason = '';
    if (status === 'SUSPENDED' || status === 'BANNED') {
      const r = prompt(`Reason for ${status === 'BANNED' ? 'banning' : 'suspending'} ${u.name}?`, '');
      if (r === null) return;
      reason = r;
    }
    this.api.setUserStatus(u.role, u.id, status, reason).subscribe({
      next: () => { this.toast.success(`${u.name}: ${status === 'ACTIVE' && u.status === 'PENDING' ? 'approved' : status.toLowerCase()}`); this.load(); },
      error: e => this.toast.error(errorMessage(e))
    });
  }
}
