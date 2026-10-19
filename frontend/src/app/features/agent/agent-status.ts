import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AgentMonitorService, AgentStatus as Status } from '../../core/services/agent-monitor.service';

const STATUS_LABEL: Record<Status, string> = {
  offline: 'Desconectado',
  online: 'En línea',
  receiving_message: 'Recibiendo mensaje',
  thinking: 'Pensando',
  executing_tool: 'Ejecutando herramienta',
  replying: 'Respondiendo',
  error: 'Error',
};

@Component({
  selector: 'app-agent-status',
  imports: [DatePipe],
  template: `
    <main class="px-4 sm:px-6 md:px-10 pt-8 pb-16">
      <div class="pb-6 border-b border-surface-border">
        <h1 class="text-2xl sm:text-3xl font-semibold tracking-tight leading-none">Agente</h1>
        <p class="text-[13px] text-ink-muted mt-2.5 max-w-[68ch] leading-relaxed">
          Estado en vivo del agente local (OpenClaw): qué está haciendo y los eventos que envía. Solo eventos reales: nada simulado.
        </p>
      </div>

      <section aria-labelledby="h-state" class="mt-8 border border-surface-border bg-surface-card">
        <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-4 border-b border-surface-border">
          <h2 id="h-state" class="flex items-center gap-2.5 text-[15px] font-semibold tracking-tight">
            <span class="w-2 h-2 rounded-full"
                  [class.bg-status-ok]="monitor.status() === 'online'"
                  [class.bg-status-warn]="monitor.status() !== 'online'"></span>
            {{ label[monitor.status()] }}
          </h2>
          <span class="font-mono text-[11px] text-ink-muted">
            Último heartbeat:
            @if (monitor.lastSeen(); as seen) { {{ seen | date: 'dd/MM HH:mm:ss' }} } @else { nunca desde que arrancó el backend }
          </span>
        </div>

        @if (!monitor.connected()) {
          <p class="text-[13px] text-status-warn px-4 py-4 border-b border-surface-border">
            El navegador no está conectado al backend (WebSocket <span class="font-mono">/ws/agent</span>). Reintentando cada 5 s.
            Si no se recupera, revisa que nginx tenga la ruta <span class="font-mono">/ws/</span>: ver <span class="font-mono">docs/AGENT_MONITOR.md</span>.
          </p>
        } @else if (monitor.status() === 'offline') {
          <p class="text-[13px] text-ink-muted px-4 py-4 border-b border-surface-border">
            El navegador sí está conectado, pero el daemon local no está enviando heartbeats. Para conectarlo
            (token <span class="font-mono">AGENT_WS_TOKEN</span> y daemon de OpenClaw), sigue <span class="font-mono">docs/AGENT_MONITOR.md</span>.
          </p>
        }

        <div class="px-4 py-4">
          <h3 class="font-mono text-[11px] uppercase tracking-wider text-ink-muted">Actividad actual</h3>
          <p class="text-[15px] font-semibold mt-1 break-words">{{ monitor.activity() || 'Sin actividad' }}</p>
        </div>
      </section>

      <section aria-labelledby="h-events" class="mt-8">
        <h2 id="h-events" class="font-mono text-[11px] uppercase tracking-wider text-ink-muted mb-3">
          Eventos <span class="normal-case tracking-normal">(los últimos 200, solo de esta sesión del navegador)</span>
        </h2>
        <div class="border border-surface-border bg-surface-card" aria-live="polite">
          @for (log of monitor.logs(); track log.timestamp + log.message) {
            <div class="grid grid-cols-[64px_minmax(0,1fr)] sm:grid-cols-[64px_120px_minmax(0,1fr)] gap-x-3 gap-y-0.5 items-baseline px-4 py-2 border-b border-surface-divider last:border-b-0 font-mono text-[11px]">
              <span class="text-ink-muted tabular-nums">{{ log.timestamp | date: 'HH:mm:ss' }}</span>
              <span [class.text-status-warn]="log.type === 'ERROR'" [class.text-ink-muted]="log.type !== 'ERROR'">{{ log.badge }}</span>
              <span class="col-span-2 sm:col-span-1 min-w-0 break-words text-ink">{{ log.message }}</span>
            </div>
          } @empty {
            <p class="text-[13px] text-ink-muted px-4 py-4">Todavía no llegan eventos.</p>
          }
        </div>
      </section>
    </main>
  `,
})
export class AgentStatus {
  readonly monitor = inject(AgentMonitorService);
  readonly label = STATUS_LABEL;
}
