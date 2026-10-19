import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/work/work').then((m) => m.WorkPage),
  },
  {
    path: 'portfolio',
    loadComponent: () => import('./features/portfolio/portfolio').then((m) => m.PortfolioPage),
  },
  {
    path: 'projects/:id',
    loadComponent: () => import('./features/project/project').then((m) => m.ProjectPage),
  },
  {
    path: 'projects/:id/changes/:name',
    loadComponent: () => import('./features/change/change').then((m) => m.ChangePage),
  },
  {
    path: 'agent',
    loadComponent: () => import('./features/agent/agent-status').then((m) => m.AgentStatus),
  },
  { path: '**', redirectTo: '' },
];
