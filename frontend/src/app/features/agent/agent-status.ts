import { DatePipe, UpperCasePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AgentMonitorService } from '../../core/services/agent-monitor.service';

@Component({
  selector: 'app-agent-status',
  imports: [DatePipe, RouterLink, UpperCasePipe],
  template: `
    <main class="px-6 md:px-10 pt-8 pb-16">
      <div class="flex items-center justify-between gap-4 mb-6">
        <div>
          <div class="font-mono text-[10px] tracking-wider text-ink-muted">AGENT STATUS</div>
          <h1 class="text-2xl font-semibold tracking-tight mt-1">Agent connection</h1>
        </div>
        <a routerLink="/" class="font-mono text-[10px] text-ink-muted hover:text-ink transition-colors">BACK TO PROJECTS</a>
      </div>

      <section class="max-w-2xl border border-surface-border bg-surface-card">
        <div class="flex items-center justify-between gap-4 px-4 py-4 border-b border-surface-border">
          <div class="flex items-center gap-2.5">
            <span class="w-2 h-2 rounded-full" [class.bg-status-ok]="monitor.status() === 'online'" [class.bg-red-600]="monitor.status() === 'offline'" [class.bg-status-warn]="monitor.status() !== 'online' && monitor.status() !== 'offline'"></span>
            <span class="font-mono text-[11px]">{{ monitor.status() | uppercase }}</span>
          </div>
          <span class="font-mono text-[10px] text-ink-muted">{{ monitor.connected() ? 'SOCKET CONNECTED' : 'SOCKET DISCONNECTED' }}</span>
        </div>
        <div class="px-4 py-5">
          <div class="font-mono text-[10px] tracking-wider text-ink-muted">CURRENT ACTIVITY</div>
          <div class="text-lg font-semibold mt-1">{{ monitor.activity() }}</div>
        </div>
        <div class="bg-[#17191c] text-[#d8ddd5] px-4 py-3 font-mono text-[11px] min-h-24 max-h-80 overflow-y-auto" aria-live="polite">
          @if (monitor.logs().length === 0) {
            <div class="text-[#7f8980]">Waiting for agent events...</div>
          }
          @for (log of monitor.logs(); track $index) {
            <div class="leading-5"><span class="text-[#7f8980]">{{ log.timestamp | date:'HH:mm:ss' }}</span> {{ log.level }} {{ log.message }}</div>
          }
        </div>
      </section>
    </main>
  `,
})
export class AgentStatus {
  readonly monitor = inject(AgentMonitorService);
}
