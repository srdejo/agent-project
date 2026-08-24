import { Injectable, signal } from '@angular/core';

export type AgentStatus = 'offline' | 'online' | 'receiving_message' | 'thinking' | 'executing_tool' | 'replying' | 'error';
export type LogLevel = 'INFO' | 'WARN' | 'ERROR';

export interface AgentLog {
  timestamp: string;
  level: LogLevel;
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
        this.logs.update(logs => [...logs, {
          timestamp: message['timestamp'] ?? new Date().toISOString(),
          level: (message['level'] ?? 'INFO') as LogLevel,
          message: message['message'] ?? '',
        }].slice(-200));
      }
    } catch {
      // Events are external input; a malformed event must not break the dashboard.
    }
  }
}