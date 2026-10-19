import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { Daily, DailyItem } from '../../core/models/daily';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import { BLOCKER_LABEL } from '../../shared/labels';

type Load = { state: 'loading' } | { state: 'error' } | { state: 'ready'; daily: Daily };

@Component({
  selector: 'app-daily',
  imports: [RouterLink],
  templateUrl: './daily.html',
})
export class DailyBlock {
  private readonly api = inject(PortfolioApiService);

  readonly load = toSignal(
    this.api.daily().pipe(
      map((daily): Load => ({ state: 'ready', daily })),
      startWith<Load>({ state: 'loading' }),
      catchError(() => of<Load>({ state: 'error' })),
    ),
    { initialValue: { state: 'loading' } as Load },
  );

  readonly copied = signal<'idle' | 'ok' | 'error'>('idle');
  readonly blockerLabel: Record<string, string> = BLOCKER_LABEL;

  link(item: DailyItem): string[] {
    return item.kind === 'CHANGE' && item.changeName
      ? ['/projects', item.projectId, 'changes', item.changeName]
      : ['/projects', item.projectId];
  }

  async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set('ok');
    } catch {
      this.copied.set('error');
    }
  }
}
