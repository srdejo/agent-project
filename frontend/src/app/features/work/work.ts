import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { Work } from '../../core/models/work';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import { DailyBlock } from './daily';

type Load = { state: 'loading' } | { state: 'error' } | { state: 'ready'; work: Work };

const REVIEW_LABEL: Record<string, string> = {
  CHANGE_COMPLETE: 'Listo para verificar y archivar',
  OBJECTIVE_ACHIEVED: 'Objetivo cumplido',
  NO_OBJECTIVE: 'Sin objetivo declarado',
  READ_ERROR: 'Error de lectura',
  MISSING: 'No reportado',
  CHANGE_PARSE_ERROR: 'Cambio ilegible',
};

@Component({
  selector: 'app-work',
  imports: [RouterLink, DatePipe, DailyBlock],
  templateUrl: './work.html',
})
export class WorkPage {
  private readonly api = inject(PortfolioApiService);

  readonly load = toSignal(
    this.api.work().pipe(
      map((work): Load => ({ state: 'ready', work })),
      startWith<Load>({ state: 'loading' }),
      catchError(() => of<Load>({ state: 'error' })),
    ),
    { initialValue: { state: 'loading' } as Load },
  );

  /** Long task texts are clamped to two lines; these keys are the expanded ones. */
  readonly expanded = signal<ReadonlySet<string>>(new Set());
  readonly reviewLabel = REVIEW_LABEL;

  toggle(key: string): void {
    this.expanded.update((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  isExpanded(key: string): boolean {
    return this.expanded().has(key);
  }
}
