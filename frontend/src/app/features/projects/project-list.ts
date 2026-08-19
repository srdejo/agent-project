import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProjectApiService } from '../../core/services/project-api.service';
import { AgentEvent, ProjectListResponse, ProjectSummary } from '../../core/models/project';
import { eventColor, lastPoint, sparkPoints, statusColor } from '../../shared/format/progress-chart';

interface ProjectRow {
  project: ProjectSummary;
  dot: string;
  spark: string;
  sparkX: number;
  sparkY: number;
}

interface GlobalEvent extends AgentEvent {
  repo: string;
  color: string;
}

const EMPTY_RESPONSE: ProjectListResponse = { projects: [], stats: { count: 0, avg: 0, blocked: 0, verified: 0 }, lastSync: null };

@Component({
  selector: 'app-project-list',
  templateUrl: './project-list.html',
})
export class ProjectList {
  private readonly api = inject(ProjectApiService);
  private readonly router = inject(Router);

  private readonly response = toSignal(this.api.list(), { initialValue: EMPTY_RESPONSE });

  readonly stats = computed(() => this.response().stats);

  readonly rows = computed<ProjectRow[]>(() =>
    this.response().projects.map((project) => {
      const spark = sparkPoints(project.series.length ? project.series : [project.progress]);
      const { x, y } = lastPoint(spark);
      return { project, dot: statusColor(project.status), spark, sparkX: x, sparkY: y };
    }),
  );

  readonly activity = computed<GlobalEvent[]>(() => {
    const events: GlobalEvent[] = [];
    for (const project of this.response().projects) {
      for (const event of project.events) {
        events.push({ ...event, repo: project.repo, color: eventColor(event.mark) });
      }
    }
    return events.sort((a, b) => (a.time < b.time ? 1 : -1)).slice(0, 8);
  });

  open(id: string): void {
    this.router.navigate(['/', id]);
  }
}
