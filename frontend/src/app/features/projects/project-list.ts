import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProjectApiService } from '../../core/services/project-api.service';
import { AgentEvent, ProjectListResponse, ProjectSummary } from '../../core/models/project';
import { eventColor, statusColor } from '../../shared/format/progress-chart';

interface ProjectRow {
  project: ProjectSummary;
  dot: string;
  tasksRatio: string;
}

interface GlobalEvent extends AgentEvent {
  repo: string;
  color: string;
}

type SortKey = 'name' | 'progress';
type SortDir = 'asc' | 'desc';

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

  readonly sortKey = signal<SortKey | null>('progress');
  readonly sortDir = signal<SortDir>('desc');

  readonly rows = computed<ProjectRow[]>(() => {
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    const projects = [...this.response().projects];

    if (key === 'name') {
      projects.sort((a, b) => a.name.localeCompare(b.name) * dir);
    } else if (key === 'progress') {
      projects.sort((a, b) => (a.progress - b.progress) * dir);
    }

    return projects.map((project) => ({
      project,
      dot: statusColor(project.status),
      tasksRatio: `${project.tasksDone}/${project.tasksTotal}`,
    }));
  });

  sortBy(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set(key === 'progress' ? 'desc' : 'asc');
    }
  }

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
