import { BlockerType, ProjectState, TaskStatus } from '../core/models/project';

export const STATE_LABEL: Record<ProjectState, string> = {
  ACTIVE: 'Activo',
  NEXT: 'Siguiente',
  PAUSED: 'Pausado',
  BLOCKED: 'Bloqueado',
  MAINTENANCE: 'Mantenimiento',
  COMPLETED: 'Completado',
  ARCHIVED: 'Archivado',
};

export const STATE_CLASS: Record<ProjectState, string> = {
  ACTIVE: 'bg-status-ok/10 text-status-ok',
  NEXT: 'bg-accent/10 text-accent',
  PAUSED: 'bg-surface-divider text-ink-subtle',
  BLOCKED: 'bg-status-warn/10 text-status-warn',
  MAINTENANCE: 'bg-surface-divider text-ink-muted',
  COMPLETED: 'bg-status-ok/10 text-status-ok',
  ARCHIVED: 'bg-surface-divider text-ink-subtle',
};

/** DONE is "completed according to the repo": its own convention says it is only checked after verifying. */
export const TASK_LABEL: Record<TaskStatus, string> = {
  OPEN: 'Pendiente',
  IN_PROGRESS: 'En curso',
  BLOCKED: 'Bloqueada',
  DONE: 'Completada',
  CANCELLED: 'Cancelada',
};

export const TASK_CLASS: Record<TaskStatus, string> = {
  OPEN: 'text-ink-muted',
  IN_PROGRESS: 'text-accent',
  BLOCKED: 'text-status-warn',
  DONE: 'text-status-ok',
  CANCELLED: 'text-ink-subtle line-through',
};

export const BLOCKER_LABEL: Record<BlockerType, string> = {
  PRODUCT_DECISION: 'Decisión de producto',
  EXTERNAL_DEPENDENCY: 'Dependencia externa',
  TECHNICAL: 'Técnico',
  ENVIRONMENT: 'Entorno',
  SECURITY: 'Seguridad',
  UNKNOWN: 'Sin clasificar',
};

export const BLOCKER_ORDER: BlockerType[] = [
  'PRODUCT_DECISION',
  'EXTERNAL_DEPENDENCY',
  'TECHNICAL',
  'ENVIRONMENT',
  'SECURITY',
  'UNKNOWN',
];

/** "hace 3 h", "hace 2 días"… for data ages; null input means there is no data at all. */
export function ageLabel(seconds: number | null): string {
  if (seconds === null) {
    return 'sin datos';
  }
  if (seconds < 3600) {
    return `hace ${Math.max(1, Math.round(seconds / 60))} min`;
  }
  if (seconds < 48 * 3600) {
    return `hace ${Math.round(seconds / 3600)} h`;
  }
  return `hace ${Math.round(seconds / 86400)} días`;
}

export function ageSince(iso: string | null, now = Date.now()): string {
  return iso ? ageLabel(Math.max(0, (now - Date.parse(iso)) / 1000)) : 'sin datos';
}
