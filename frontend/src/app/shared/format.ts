import { Pipe, PipeTransform } from '@angular/core';

/** KES currency: 1234.5 → "KES 1,234.50" (or without decimals when whole). */
@Pipe({ name: 'kes' })
export class KesPipe implements PipeTransform {
  transform(v: number | null | undefined, decimals = 2): string {
    if (v === null || v === undefined || isNaN(v as number)) return '—';
    return 'KES ' + Number(v).toLocaleString('en-KE', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
  }
}

/** Number with thousands separators. */
@Pipe({ name: 'num' })
export class NumPipe implements PipeTransform {
  transform(v: number | null | undefined, decimals = 0): string {
    if (v === null || v === undefined || isNaN(v as number)) return '—';
    return Number(v).toLocaleString('en-KE', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
  }
}

/** Relative time: "3 h ago", "2 days ago". */
@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {
  transform(v: string | null | undefined): string {
    if (!v) return '';
    const diff = (Date.now() - new Date(v).getTime()) / 1000;
    if (diff < 60) return 'just now';
    if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
    if (diff < 86400 * 30) return `${Math.floor(diff / 86400)} days ago`;
    return new Date(v).toLocaleDateString('en-KE');
  }
}

export function isoDate(d: Date): string {
  const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return z.toISOString().slice(0, 10);
}

export function shortDate(s: string | null | undefined): string {
  if (!s) return '';
  return new Date(s).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' });
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
