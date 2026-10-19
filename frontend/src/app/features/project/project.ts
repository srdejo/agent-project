import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ChangeList } from '../../core/models/change';
import { Blocker, BlockerType, ProjectDetail } from '../../core/models/project';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import {
  BLOCKER_LABEL,
  BLOCKER_ORDER,
  STATE_CLASS,
  STATE_LABEL,
  TASK_CLASS,
  TASK_LABEL,
  ageSince,
} from '../../shared/labels';

type Tab = 'summary' | 'objective' | 'changes' | 'blockers' | 'activity';
type Load =
  | { state: 'loading' }
  | { state: 'missing' }
  | { state: 'ready'; project: ProjectDetail; changes: ChangeList | null };

const TABS: { id: Tab; label: string }[] = [
  { id: 'summary', label: 'Resumen' },
  { id: 'objective', label: 'Objetivo y tareas' },
  { id: 'changes', label: 'Cambios OpenSpec' },
  { id: 'blockers', label: 'Bloqueos y decisiones' },
  { id: 'activity', label: 'Actividad' },
];

const OBJECTIVE_TEMPLATE = `## Objetivo actual: <nombre>

**Criterio de cierre**: <qué tiene que ser cierto para darlo por cumplido>
**Alcance**: Etapa 2, Etapa 3, openspec:<cambio>
**Fecha objetivo**: AAAA-MM-DD`;

@Component({
  selector: 'app-project',
  imports: [RouterLink, DatePipe],
  templateUrl: './project.html',
})
export class ProjectPage {
  private readonly api = inject(PortfolioApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly load = toSignal(
    this.route.paramMap.pipe(
      map((p) => p.get('id') ?? ''),
      switchMap((id) =>
        this.api.project(id).pipe(
          switchMap((project) =>
            this.api.changes(id).pipe(
              catchError(() => of(null)),
              map((changes): Load => ({ state: 'ready', project, changes })),
            ),
          ),
          catchError(() => of<Load>({ state: 'missing' })),
          startWith<Load>({ state: 'loading' }),
        ),
      ),
    ),
    { initialValue: { state: 'loading' } as Load },
  );
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });

  readonly tabs = TABS;
  readonly tab = computed<Tab>(() => (this.query().get('tab') as Tab) || 'summary');
  readonly stateLabel = STATE_LABEL;
  readonly stateClass = STATE_CLASS;
  readonly taskLabel = TASK_LABEL;
  readonly taskClass = TASK_CLASS;
  readonly blockerLabel = BLOCKER_LABEL;
  readonly objectiveTemplate = OBJECTIVE_TEMPLATE;
  readonly ageSince = ageSince;

  select(tab: Tab): void {
    this.router.navigate([], { queryParams: { tab: tab === 'summary' ? null : tab }, queryParamsHandling: 'merge' });
  }

  blockersByType(blockers: Blocker[]): { type: BlockerType; items: Blocker[] }[] {
    return BLOCKER_ORDER.map((type) => ({ type, items: blockers.filter((b) => b.type === type) })).filter(
      (g) => g.items.length > 0,
    );
  }

  /** Objective progress over time, as an SVG polyline in a 300x80 box. */
  chart(project: ProjectDetail): { points: string; last: number } | null {
    const pts = project.history.filter((h) => h.total > 0);
    if (pts.length === 0) {
      return null;
    }
    const values = pts.map((h) => (100 * h.done) / h.total);
    // A single point still draws a flat line, so the chart never looks empty.
    const series = values.length === 1 ? [values[0], values[0]] : values;
    const step = 300 / (series.length - 1);
    const points = series.map((v, i) => `${(i * step).toFixed(1)},${(78 - v * 0.76).toFixed(1)}`).join(' ');
    return { points, last: values[values.length - 1] };
  }

  stagesOutsideObjective(project: ProjectDetail) {
    return project.stages.filter((s) => !s.backlog && s.tasks.every((t) => !t.inObjective));
  }
}
