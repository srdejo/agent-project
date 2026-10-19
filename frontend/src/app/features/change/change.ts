import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { Artifact, ChangeDetail, Continuation } from '../../core/models/change';
import { AgentMonitorService } from '../../core/services/agent-monitor.service';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import { SyncHealthService } from '../../core/services/sync-health.service';
import { TASK_CLASS, TASK_LABEL } from '../../shared/labels';

type Load =
  | { state: 'loading' }
  | { state: 'missing' }
  | { state: 'ready'; change: ChangeDetail; continuation: Continuation | null };

interface ArtifactTab {
  id: string;
  label: string;
  artifact: Artifact;
}

@Component({
  selector: 'app-change',
  imports: [RouterLink, DatePipe],
  templateUrl: './change.html',
})
export class ChangePage {
  private readonly api = inject(PortfolioApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly monitor = inject(AgentMonitorService);
  private readonly sync = inject(SyncHealthService);

  readonly load = toSignal(
    combineLatest([this.route.paramMap]).pipe(
      map(([p]) => ({ id: p.get('id') ?? '', name: p.get('name') ?? '' })),
      switchMap(({ id, name }) =>
        this.api.change(id, name).pipe(
          switchMap((change) =>
            this.api.continuation(id, name).pipe(
              catchError(() => of(null)),
              map((continuation): Load => ({ state: 'ready', change, continuation })),
            ),
          ),
          catchError(() => of<Load>({ state: 'missing' })),
          startWith<Load>({ state: 'loading' }),
        ),
      ),
    ),
    { initialValue: { state: 'loading' } as Load },
  );

  readonly taskLabel = TASK_LABEL;
  readonly taskClass = TASK_CLASS;

  readonly tabs = computed<ArtifactTab[]>(() => {
    const l = this.load();
    if (l.state !== 'ready' || !l.change.artifacts) {
      return [];
    }
    const a = l.change.artifacts;
    const tabs: ArtifactTab[] = [];
    if (a.proposal) tabs.push({ id: 'proposal', label: 'proposal.md', artifact: a.proposal });
    a.specs.forEach((s) => tabs.push({ id: s.path, label: s.path, artifact: s }));
    if (a.design) tabs.push({ id: 'design', label: 'design.md', artifact: a.design });
    if (a.tasks) tabs.push({ id: 'tasks', label: 'tasks.md', artifact: a.tasks });
    if (l.change.metadata !== null) {
      tabs.push({ id: 'metadata', label: '.openspec.yaml', artifact: { content: l.change.metadata, truncated: false } });
    }
    return tabs;
  });
  readonly missing = computed(() => {
    const l = this.load();
    if (l.state !== 'ready' || !l.change.artifacts) {
      return [];
    }
    const a = l.change.artifacts;
    return [!a.proposal && 'proposal.md', !a.design && 'design.md', !a.tasks && 'tasks.md'].filter(
      (x): x is string => !!x,
    );
  });
  readonly selected = signal<string | null>(null);
  readonly current = computed(() => this.tabs().find((t) => t.id === this.selected()) ?? this.tabs()[0] ?? null);

  /** The editable instruction; reset whenever a new continuation is loaded. */
  readonly draft = signal('');
  readonly copied = signal<'idle' | 'ok' | 'error'>('idle');
  readonly agentOnline = computed(() =>
    this.monitor.connected() ? this.monitor.status() !== 'offline' : this.sync.health()?.agentConnectivity === 'online',
  );

  private readonly resetDraft = effect(() => {
    const l = this.load();
    this.draft.set(l.state === 'ready' && l.continuation ? l.continuation.text : '');
    this.copied.set('idle');
  });

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.draft());
      this.copied.set('ok');
    } catch {
      this.copied.set('error');
    }
  }
}
