import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, switchMap } from 'rxjs';
import { ProjectApiService } from '../../core/services/project-api.service';
import { eventColor, historyLine, statusColor, verifyColor } from '../../shared/format/progress-chart';

@Component({
  selector: 'app-project-detail',
  templateUrl: './project-detail.html',
})
export class ProjectDetail {
  private readonly api = inject(ProjectApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly project = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id')!),
      switchMap((id) => this.api.get(id)),
    ),
  );

  readonly dot = computed(() => statusColor(this.project()!.status));
  readonly verifyColor = computed(() => verifyColor(this.project()!.verify));
  readonly checks = computed(() =>
    this.project()!.checks.map((c) => ({
      ...c,
      mark: c.ok ? '✓' : '⚠',
      color: c.ok ? '#2F7D5A' : '#A8621A',
    })),
  );
  readonly events = computed(() =>
    this.project()!.events.map((e) => ({ ...e, color: eventColor(e.mark) })),
  );
  readonly series = computed(() => {
    const history = this.project()!.history;
    return history.length ? history.map((h) => h.progress) : [this.project()!.progress];
  });
  readonly line = computed(() => historyLine(this.series()));
  readonly area = computed(() => `0,240 ${this.line()} 1000,240`);

  back(): void {
    this.router.navigate(['/']);
  }
}
