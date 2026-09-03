import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  BlockedTask,
  ProjectListResponse,
  ProjectPriority,
  ProjectSummary,
} from '../../core/models/project';
import { ProjectApiService } from '../../core/services/project-api.service';

/**
 * Una fila del mapa. El orden lo manda `priorityRank` (1 = primero); `priority`
 * es el grupo — en que estado de decision esta — y se muestra como etiqueta.
 * Son dos ejes distintos a proposito: agrupar Y ordenar a la vez se peleaba.
 */
interface ProjectRow {
  project: ProjectSummary;
  rank: number | null;
  grupo: { label: string; clase: string };
  blocked: number;
  blockedNames: string[];
  donePct: number;
  blockedPct: number;
  restPct: number;
}

interface OpenQuestion {
  projectId: string;
  projectName: string;
  question: string;
  blocked: number;
}

const EMPTY_RESPONSE: ProjectListResponse = {
  projects: [],
  stats: { count: 0, avg: 0, blocked: 0, verified: 0 },
  lastSync: null,
};

const GRUPOS: Record<ProjectPriority, { label: string; clase: string }> = {
  NOW: { label: 'Avanzar ahora', clase: 'bg-status-ok/10 text-status-ok' },
  NEXT: { label: 'Después', clase: 'bg-accent/10 text-accent' },
  DECIDE: { label: 'Falta decidir', clase: 'bg-status-warn/10 text-status-warn' },
  ON_TRACK: { label: 'Al día', clase: 'bg-surface-divider text-ink-muted' },
  FROZEN: { label: 'Congelado', clase: 'bg-surface-divider text-ink-subtle' },
};

const SIN_GRUPO = { label: 'Sin clasificar', clase: 'bg-surface-divider text-ink-subtle' };

@Component({
  selector: 'app-portfolio-map',
  templateUrl: './portfolio-map.html',
  imports: [RouterLink],
})
export class PortfolioMap {
  private readonly api = inject(ProjectApiService);
  private readonly router = inject(Router);

  private readonly response = toSignal(this.api.list(), { initialValue: EMPTY_RESPONSE });
  private readonly blocked = toSignal(this.api.blockedTasks(), { initialValue: [] as BlockedTask[] });

  readonly loaded = computed(() => this.response().projects.length > 0);

  /** Tareas bloqueadas por proyecto, para no pedir el detalle de cada uno. */
  private readonly blockedByProject = computed<Map<string, string[]>>(() => {
    const map = new Map<string, string[]>();
    for (const task of this.blocked()) {
      const list = map.get(task.projectId) ?? [];
      list.push(task.taskName);
      map.set(task.projectId, list);
    }
    return map;
  });

  readonly rows = computed<ProjectRow[]>(() => {
    const blocked = this.blockedByProject();

    return this.response()
      .projects.map<ProjectRow>((project) => {
        const names = blocked.get(project.id) ?? [];
        const total = project.tasksTotal || 0;
        const done = project.tasksDone || 0;
        const blk = Math.min(names.length, Math.max(total - done, 0));

        return {
          project,
          rank: project.priorityRank ?? null,
          grupo: project.priority ? GRUPOS[project.priority] : SIN_GRUPO,
          blocked: names.length,
          blockedNames: names,
          donePct: total ? (done / total) * 100 : 0,
          blockedPct: total ? (blk / total) * 100 : 0,
          restPct: total ? ((total - done - blk) / total) * 100 : 100,
        };
      })
      // Sin rango van al final, no primero: un proyecto sin clasificar no es urgente.
      .sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999));
  });

  readonly sinClasificar = computed(() => this.rows().filter((r) => r.rank === null).length);

  readonly totals = computed(() => {
    const projects = this.response().projects;
    const total = projects.reduce((acc, p) => acc + (p.tasksTotal || 0), 0);
    const done = projects.reduce((acc, p) => acc + (p.tasksDone || 0), 0);
    return {
      projects: projects.length,
      tasks: total,
      done,
      pct: total ? Math.round((done / total) * 100) : 0,
      blocked: this.blocked().length,
    };
  });

  /** Las preguntas abiertas, ordenadas por cuanto trabajo desbloquean. */
  readonly questions = computed<OpenQuestion[]>(() => {
    const blocked = this.blockedByProject();
    return this.response()
      .projects.filter((p) => !!p.openQuestion)
      .map((p) => ({
        projectId: p.id,
        projectName: p.name,
        question: p.openQuestion as string,
        blocked: (blocked.get(p.id) ?? []).length,
      }))
      .sort((a, b) => b.blocked - a.blocked);
  });

  open(id: string): void {
    this.router.navigate(['/', id]);
  }
}
