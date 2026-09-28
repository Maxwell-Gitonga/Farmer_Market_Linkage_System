import { Component, computed, input } from '@angular/core';

export interface Bar { label: string; value: number; }

/**
 * Bar chart (wireframe "30-day price trend": one highlighted bar = latest value).
 * Pure SVG – no external chart library.
 */
@Component({
  selector: 'app-bar-chart',
  template: `
    @if (bars().length) {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + height()" class="chart" preserveAspectRatio="none" role="img"
           [attr.aria-label]="ariaLabel()">
        @for (b of geometry(); track $index) {
          <g>
            <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="3"
                  [class.hl]="b.highlight" class="bar"><title>{{ b.title }}</title></rect>
          </g>
        }
      </svg>
      @if (showLabels()) {
        <div class="bar-labels">
          @for (b of geometry(); track $index) { <span [style.width.%]="100 / geometry().length">{{ b.short }}</span> }
        </div>
      }
    } @else {
      <div class="empty small">No data yet</div>
    }
  `,
  styles: [`
    :host { display: block; }
    .chart { width: 100%; height: auto; display: block; }
    .bar { fill: var(--bar); transition: fill .15s; }
    .bar:hover { fill: var(--bar-hover); }
    .bar.hl { fill: var(--bar-hl); }
    .bar-labels { display: flex; font-size: 11px; color: var(--muted); margin-top: 6px; }
    .bar-labels span { text-align: center; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  `]
})
export class BarChart {
  bars = input<Bar[]>([]);
  height = input(90);
  highlight = input<'last' | 'max' | 'none'>('last');
  unit = input('');
  showLabels = input(false);
  ariaLabel = input('Bar chart');
  readonly W = 600;

  geometry = computed(() => {
    const bars = this.bars();
    const h = this.height();
    if (!bars.length) return [];
    const max = Math.max(...bars.map(b => b.value));
    const min = Math.min(...bars.map(b => b.value));
    const floor = min > 0 && (max - min) / max < 0.5 ? min * 0.85 : 0;   // zoom in when values are close
    const gap = 8;
    const w = (this.W - gap * (bars.length - 1)) / bars.length;
    const hi = this.highlight() === 'last' ? bars.length - 1
      : this.highlight() === 'max' ? bars.findIndex(b => b.value === max) : -1;
    return bars.map((b, i) => {
      const bh = Math.max(4, ((b.value - floor) / (max - floor || 1)) * (h - 6));
      return {
        x: i * (w + gap), y: h - bh, w, h: bh, highlight: i === hi,
        title: `${b.label}: ${this.unit()}${b.value.toLocaleString('en-KE', { maximumFractionDigits: 2 })}`,
        short: b.label
      };
    });
  });
}

export interface LinePoint { x: string; y: number; }
export interface LineSeries { name: string; points: LinePoint[]; color?: string; dashed?: boolean; }
export interface Band { x: string; lo: number; hi: number; }

/** Line chart with optional prediction-interval band (market insights, demand forecast, reports). */
@Component({
  selector: 'app-line-chart',
  template: `
    @if (hasData()) {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" class="chart" role="img" [attr.aria-label]="ariaLabel()">
        @for (t of g().yTicks; track $index) {
          <line [attr.x1]="PL" [attr.x2]="W - PR" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
          <text [attr.x]="PL - 6" [attr.y]="t.y + 4" class="tick" text-anchor="end">{{ t.label }}</text>
        }
        @for (t of g().xTicks; track $index) {
          <text [attr.x]="t.x" [attr.y]="H - 6" class="tick" text-anchor="middle">{{ t.label }}</text>
        }
        @if (g().band) { <path [attr.d]="g().band" class="band" /> }
        @for (s of g().paths; track s.name) {
          <path [attr.d]="s.d" class="line" [attr.stroke]="s.color" [attr.stroke-dasharray]="s.dashed ? '6 5' : null" />
          @for (p of s.dots; track $index) {
            <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3" [attr.fill]="s.color"><title>{{ p.title }}</title></circle>
          }
        }
      </svg>
      @if (series().length > 1 || band().length) {
        <div class="legend">
          @for (s of series(); track s.name) {
            <span><i [style.background]="s.color || 'var(--accent)'" [class.dashed]="s.dashed"></i>{{ s.name }}</span>
          }
          @if (band().length) { <span><i class="bandkey"></i>{{ bandLabel() }}</span> }
        </div>
      }
    } @else {
      <div class="empty small">No data yet</div>
    }
  `,
  styles: [`
    :host { display: block; }
    .chart { width: 100%; height: auto; display: block; overflow: visible; }
    .grid { stroke: var(--border); stroke-width: 1; }
    .tick { fill: var(--muted); font-size: 11px; }
    .line { fill: none; stroke-width: 2.2; stroke-linejoin: round; stroke-linecap: round; }
    .band { fill: var(--band); }
    .legend { display: flex; gap: 16px; flex-wrap: wrap; font-size: 12px; color: var(--muted); margin-top: 6px; }
    .legend i { display: inline-block; width: 14px; height: 3px; margin-right: 6px; vertical-align: middle; border-radius: 2px; }
    .legend i.dashed { background: repeating-linear-gradient(90deg, currentColor 0 4px, transparent 4px 7px) !important; }
    .legend i.bandkey { height: 10px; background: var(--band); }
  `]
})
export class LineChart {
  series = input<LineSeries[]>([]);
  band = input<Band[]>([]);
  bandLabel = input('Prediction range');
  yPrefix = input('');
  ariaLabel = input('Line chart');
  readonly W = 640;
  readonly H = 230;
  readonly PL = 58;
  readonly PR = 14;
  readonly PT = 12;
  readonly PB = 26;

  hasData = computed(() => this.series().some(s => s.points.length > 0));

  g = computed(() => {
    const series = this.series().filter(s => s.points.length);
    const band = this.band();
    const xs = [...series.flatMap(s => s.points.map(p => +new Date(p.x))), ...band.map(b => +new Date(b.x))];
    const ys = [...series.flatMap(s => s.points.map(p => p.y)), ...band.flatMap(b => [b.lo, b.hi])];
    const x0 = Math.min(...xs), x1 = Math.max(...xs);
    let y0 = Math.min(...ys), y1 = Math.max(...ys);
    const pad = (y1 - y0) * 0.1 || Math.abs(y1) * 0.1 || 1;
    y0 = Math.max(0, y0 - pad);
    y1 = y1 + pad;
    const sx = (x: number) => this.PL + ((x - x0) / (x1 - x0 || 1)) * (this.W - this.PL - this.PR);
    const sy = (y: number) => this.PT + (1 - (y - y0) / (y1 - y0 || 1)) * (this.H - this.PT - this.PB);
    const fmt = (v: number) => this.yPrefix() + (v >= 1e6 ? (v / 1e6).toFixed(1) + 'M' : v >= 1000 ? (v / 1000).toFixed(v >= 10000 ? 0 : 1) + 'k' : v.toFixed(0));
    const yTicks = [0, 1, 2, 3, 4].map(i => {
      const v = y0 + ((y1 - y0) * i) / 4;
      return { y: sy(v), label: fmt(v) };
    });
    const nX = 5;
    const xTicks = Array.from({ length: nX }, (_, i) => {
      const t = x0 + ((x1 - x0) * i) / (nX - 1);
      return { x: sx(t), label: new Date(t).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' }) };
    });
    const palette = ['var(--accent)', 'var(--accent-2)', 'var(--accent-3)', 'var(--muted)'];
    const paths = series.map((s, i) => {
      const pts = [...s.points].sort((a, b) => +new Date(a.x) - +new Date(b.x));
      const d = pts.map((p, j) => `${j ? 'L' : 'M'}${sx(+new Date(p.x)).toFixed(1)},${sy(p.y).toFixed(1)}`).join(' ');
      const dots = pts.length <= 40 ? pts.map(p => ({
        x: sx(+new Date(p.x)), y: sy(p.y),
        title: `${new Date(p.x).toLocaleDateString('en-KE')}: ${this.yPrefix()}${p.y.toLocaleString('en-KE', { maximumFractionDigits: 1 })}`
      })) : [];
      return { name: s.name, d, dots, color: s.color || palette[i % palette.length], dashed: !!s.dashed };
    });
    let bandPath: string | null = null;
    if (band.length > 1) {
      const b = [...band].sort((a, c) => +new Date(a.x) - +new Date(c.x));
      bandPath = b.map((p, j) => `${j ? 'L' : 'M'}${sx(+new Date(p.x)).toFixed(1)},${sy(p.hi).toFixed(1)}`).join(' ')
        + ' ' + [...b].reverse().map(p => `L${sx(+new Date(p.x)).toFixed(1)},${sy(p.lo).toFixed(1)}`).join(' ') + ' Z';
    }
    return { yTicks, xTicks, paths, band: bandPath };
  });
}
