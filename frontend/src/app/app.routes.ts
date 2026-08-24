import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'agent',
    loadComponent: () => import('./features/agent/agent-status').then((m) => m.AgentStatus),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/projects/project-list').then((m) => m.ProjectList),
  },
  {
    path: ':id',
    loadComponent: () => import('./features/projects/project-detail').then((m) => m.ProjectDetail),
  },
];
