import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { SyncHealth } from '../models/health';

/** Data freshness shared by every view. Refreshed every minute so the "stale" warning never lags far behind. */
@Injectable({ providedIn: 'root' })
export class SyncHealthService {
  private readonly http = inject(HttpClient);

  readonly health = toSignal(
    timer(0, 60_000).pipe(
      switchMap(() => this.http.get<SyncHealth>('/api/health/sync').pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );
}
