import { DatePipe } from '@angular/common';
import { Component, computed, effect, ElementRef, inject, ViewChild } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProjectApiService } from './core/services/project-api.service';
import { AgentMonitorService } from './core/services/agent-monitor.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, DatePipe],
  templateUrl: './app.html',
})
export class App {
  @ViewChild('logsContainer') private logsContainer?: ElementRef<HTMLElement>;
  private readonly api = inject(ProjectApiService);
  readonly monitor = inject(AgentMonitorService);
  private readonly scrollLogs = effect(() => {
    this.monitor.logs();
    queueMicrotask(() => {
      const element = this.logsContainer?.nativeElement;
      if (element) element.scrollTop = element.scrollHeight;
    });
  });

  private readonly response = toSignal(this.api.list(), { initialValue: null });

  readonly lastSync = computed(() => this.response()?.lastSync ?? null);
}
