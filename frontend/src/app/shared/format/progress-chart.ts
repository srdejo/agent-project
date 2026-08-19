export function statusColor(status: string): string {
  if (status === 'BLOCKED') return '#A8621A';
  if (status === 'STARTED') return '#3B6EA8';
  return '#2F7D5A';
}

export function verifyColor(verify: string): string {
  if (verify === 'PASSED') return '#2F7D5A';
  if (verify === 'ATTENTION') return '#A8621A';
  return '#7A756A';
}

export function eventColor(mark: string): string {
  if (mark === '⚠') return '#A8621A';
  if (mark === '✓') return '#2F7D5A';
  return '#A39C8E';
}

export function historyLine(series: number[], max = 100): string {
  const n = series.length;
  return series
    .map((v, i) => `${((i * 1000) / (n - 1)).toFixed(1)},${(240 - (v / max) * 236).toFixed(1)}`)
    .join(' ');
}
