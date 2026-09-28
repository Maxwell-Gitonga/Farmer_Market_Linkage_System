import { Component, input } from '@angular/core';

/** Demand trend pill: ▲ Rising / ▬ Stable / ▼ Falling. */
@Component({
  selector: 'app-trend',
  template: `
    @if (trend()) {
      <span class="pill" [class]="'pill trend-' + trend()!.toLowerCase()">
        {{ trend() === 'RISING' ? '▲' : trend() === 'FALLING' ? '▼' : '▬' }}
        {{ trend() === 'RISING' ? 'Rising' : trend() === 'FALLING' ? 'Falling' : 'Stable' }}
        @if (change() !== null && change() !== undefined) { <small>({{ change()! > 0 ? '+' : '' }}{{ change() }}%)</small> }
      </span>
    } @else {
      <span class="pill muted">No data</span>
    }
  `
})
export class TrendBadge {
  trend = input<string | null | undefined>(null);
  change = input<number | null | undefined>(null);
}

/** Confidence pill: high / medium / low. */
@Component({
  selector: 'app-confidence',
  template: `<span class="pill" [class]="'pill conf-' + (label() || 'low')">Confidence: {{ label() || 'low' }}@if (value() !== null && value() !== undefined) { <small>&nbsp;({{ (value()! * 100).toFixed(0) }}%)</small> }</span>`
})
export class ConfidenceBadge {
  label = input<string | null | undefined>(null);
  value = input<number | null | undefined>(null);
}

/** Status pill for listings, accounts and transactions. */
@Component({
  selector: 'app-status',
  template: `<span class="pill" [class]="'pill status-' + (status() || '').toLowerCase()">{{ status() }}</span>`
})
export class StatusBadge {
  status = input<string | null | undefined>('');
}
