import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ProjectCard, ProjectState } from '../../core/models/project';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import { STATE_CLASS, STATE_LABEL, ageSince } from '../../shared/labels';

type SortKey = 'rank' | 'progress' | 'blockers' | 'activity';

interface Action {
  label: string;
  link: string[];
  query?: Record<string, string>;
}

const STATES: ProjectState[] = ['ACTIVE', 'NEXT', 'BLOCKED', 'MAINTENANCE', 'PAUSED', 'COMPLETED'];

@Component({
  selector: 'app-portfolio',
  imports: [RouterLink],
  templateUrl: './portfolio.html',
})
export class PortfolioPage {
  private readonly api = inject(PortfolioApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly cards = toSignal(this.api.portfolio().pipe(catchError(() => of(null))), { initialValue: undefined });
  /** First "continue" item per project, to offer "Continuar <cambio>" as the main action. */
  private readonly continueByProject = toSignal(
    this.api.work().pipe(
      map((w) => {
        const byProject = new Map<string, string>();
        w.continue.forEach((c) => byProject.has(c.projectId) || byProject.set(c.projectId, c.changeName));
        return byProject;
      }),
      catchError(() => of(new Map<string, string>())),
    ),
    { initialValue: new Map<string, string>() },
  );
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });

  readonly states = STATES;
  readonly stateLabel = STATE_LABEL;
  readonly stateClass = STATE_CLASS;
  readonly ageSince = ageSince;

  readonly state = computed(() => this.params().get('state') ?? '');
  readonly withBlockers = computed(() => this.params().get('blockers') === '1');
  readonly actionable = computed(() => this.params().get('actionable') === '1');
  readonly noObjective = computed(() => this.params().get('noObjective') === '1');
  readonly stack = computed(() => this.params().get('stack') ?? '');
  readonly sort = computed(() => (this.params().get('sort') as SortKey) ?? 'rank');

  readonly stacks = computed(() =>
    [...new Set((this.cards() ?? []).flatMap((c) => c.stack))].sort((a, b) => a.localeCompare(b)),
  );

  readonly visible = computed(() => {
    const filtered = (this.cards() ?? []).filter(
      (c) =>
        (!this.state() || c.state === this.state()) &&
        (!this.withBlockers() || c.blockerCount > 0) &&
        (!this.actionable() || c.hasActionable) &&
        (!this.noObjective() || c.objective === null) &&
        (!this.stack() || c.stack.includes(this.stack())),
    );
    return [...filtered].sort(this.comparator(this.sort()));
  });

  set(key: string, value: string | boolean): void {
    const v = value === true ? '1' : value === false || value === '' ? null : value;
    this.router.navigate([], { queryParams: { [key]: v }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  clear(): void {
    this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  action(c: ProjectCard): Action {
    if (c.objective === null) {
      return { label: 'Declarar objetivo', link: ['/projects', c.id], query: { tab: 'objective' } };
    }
    const change = this.continueByProject().get(c.id);
    if (change) {
      return { label: `Continuar ${change}`, link: ['/projects', c.id, 'changes', change] };
    }
    if (c.mainBlocker?.type === 'PRODUCT_DECISION') {
      return { label: 'Ver decisión', link: ['/projects', c.id], query: { tab: 'blockers' } };
    }
    return { label: 'Ver proyecto', link: ['/projects', c.id] };
  }

  private comparator(sort: SortKey): (a: ProjectCard, b: ProjectCard) => number {
    const rank = (c: ProjectCard) => c.priorityRank ?? Number.MAX_SAFE_INTEGER;
    const byRank = (a: ProjectCard, b: ProjectCard) => rank(a) - rank(b) || a.name.localeCompare(b.name);
    switch (sort) {
      case 'progress':
        return (a, b) => (b.objective?.percent ?? -1) - (a.objective?.percent ?? -1) || byRank(a, b);
      case 'blockers':
        return (a, b) => b.blockerCount - a.blockerCount || byRank(a, b);
      case 'activity':
        return (a, b) => (b.lastActivity ?? '').localeCompare(a.lastActivity ?? '') || byRank(a, b);
      default:
        return byRank;
    }
  }
}
