import { Component, computed, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AgentMonitorService } from '../core/services/agent-monitor.service';
import { SyncHealthService } from '../core/services/sync-health.service';
import { ageLabel } from '../shared/labels';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, UpperCasePipe],
  template: `
    <div class="min-h-screen">
      <header class="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 sm:px-6 md:px-10 py-4 border-b border-surface-border">
        <div class="flex items-baseline gap-3 min-w-0">
          <a routerLink="/" class="text-[15px] sm:text-[17px] font-bold tracking-tight hover:text-accent transition-colors">OpenClaw</a>
          <nav class="flex items-baseline gap-3 sm:gap-4 font-mono text-[11px] uppercase tracking-wide">
            <a routerLink="/" routerLinkActive="text-ink font-medium" [routerLinkActiveOptions]="{ exact: true }"
               class="text-ink-muted hover:text-ink transition-colors">Mi trabajo</a>
            <a routerLink="/portfolio" routerLinkActive="text-ink font-medium"
               class="text-ink-muted hover:text-ink transition-colors">Portafolio</a>
            <a routerLink="/agent" routerLinkActive="text-ink font-medium"
               class="text-ink-muted hover:text-ink transition-colors">Agente</a>
          </nav>
        </div>
        <div class="flex items-center gap-4 font-mono text-[10px] sm:text-[11px] text-ink-muted">
          <span class="flex items-center gap-1.5" [title]="'Datos generados: ' + (health()?.dataGeneratedAt ?? 'nunca')">
            <span class="w-1.5 h-1.5 rounded-full" [class.bg-status-ok]="!stale()" [class.bg-status-warn]="stale()"></span>
            DATOS {{ dataAge() | uppercase }}
          </span>
          <a routerLink="/agent" class="flex items-center gap-1.5 hover:text-ink transition-colors" title="Estado del agente (heartbeat)">
            <span class="w-1.5 h-1.5 rounded-full" [class.bg-status-ok]="agentOnline()" [class.bg-red-600]="!agentOnline()"></span>
            AGENTE {{ agentOnline() ? 'EN LÍNEA' : 'DESCONECTADO' }}
          </a>
        </div>
      </header>

      @if (stale()) {
        <div role="status" class="px-4 sm:px-6 md:px-10 py-2.5 bg-status-warn/10 border-b border-status-warn/30 text-[12px] text-status-warn">
          <span class="font-mono text-[10px] uppercase tracking-wide font-medium mr-2">Datos obsoletos</span>
          @if (health()?.dataAgeSeconds === null || !health()) {
            No hay ningún sync correcto todavía: lo que ves puede estar vacío o incompleto.
          } @else {
            El último sync correcto es de {{ dataAge() }}. Nada de esta página está en vivo; confírmalo en el repo antes de actuar.
          }
        </div>
      }

      <router-outlet />
    </div>
  `,
})
export class Shell {
  private readonly sync = inject(SyncHealthService);
  private readonly monitor = inject(AgentMonitorService);

  readonly health = this.sync.health;
  readonly stale = computed(() => this.health()?.stale ?? true);
  readonly dataAge = computed(() => ageLabel(this.health()?.dataAgeSeconds ?? null));
  /** The WebSocket is live; the health endpoint is the fallback when the socket is down. */
  readonly agentOnline = computed(() =>
    this.monitor.connected() ? this.monitor.status() !== 'offline' : this.health()?.agentConnectivity === 'online',
  );
}
