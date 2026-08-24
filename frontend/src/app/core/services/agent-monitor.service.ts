import { Injectable, signal } from '@angular/core';

export type AgentStatus = 'offline' | 'online' | 'receiving_message' | 'thinking' | 'executing_tool' | 'replying' | 'error';
export type LogLevel = 'INFO' | 'WARN' | 'ERROR';

export interface AgentLog {
  timestamp: string;
  level: LogLevel;
  type: 'INBOUND' | 'TOOL' | 'AI' | 'ERROR' | 'INFO';
  badge: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class AgentMonitorService {
  readonly status = signal<AgentStatus>('offline');
  readonly activity = signal('PC local desconectada');
  readonly logs = signal<AgentLog[]>([]);
  readonly connected = signal(false);
  private socket?: WebSocket;
  private reconnectTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.connect();
  }

  private connect(): void {
    if (typeof WebSocket === 'undefined') return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.socket = new WebSocket(`${protocol}//${window.location.host}/ws/agent`);
    this.socket.onopen = () => this.connected.set(true);
    this.socket.onclose = () => {
      this.connected.set(false);
      this.status.set('offline');
      this.activity.set('Conectando con el agente...');
      this.reconnectTimer = setTimeout(() => this.connect(), 5000);
    };
    this.socket.onerror = () => this.socket?.close();
    this.socket.onmessage = ({ data }) => this.receive(data);
  }

  private receive(raw: string): void {
    try {
      const message = JSON.parse(raw) as Record<string, string>;
      if (message['type'] === 'agent_snapshot') {
        this.status.set(message['status'] as AgentStatus);
        this.activity.set(message['activity'] ?? '');
      }
      if (message['type'] === 'log_event') {
        const parsed = this.parseLog(message['message'] ?? '');
        this.logs.update(logs => [{
          timestamp: message['timestamp'] ?? new Date().toISOString(),
          level: (message['level'] ?? 'INFO') as LogLevel,
          type: parsed.type,
          badge: parsed.badge,
          message: parsed.message,
        }, ...logs].slice(0, 200));
      }
    } catch {
      // Events are external input; a malformed event must not break the dashboard.
    }
  }

  private parseLog(message: string): Pick<AgentLog, 'type' | 'badge' | 'message'> {
    const match = message.match(/^\[([A-Z]+)]\s*(.*)$/s);
    const type = match?.[1];
    const text = match?.[2] ?? message;
    const event = {
      INBOUND: { type: 'INBOUND' as const, badge: '📥 Mensaje' },
      TOOL: { type: 'TOOL' as const, badge: '🛠️ Tool' },
      AI: { type: 'AI' as const, badge: '🧠 Pensando' },
      ERROR: { type: 'ERROR' as const, badge: '❌ Error' },
    }[type ?? ''];
    return event ? { ...event, message: text } : { type: 'INFO', badge: 'ℹ️ Info', message: text };
  }
}