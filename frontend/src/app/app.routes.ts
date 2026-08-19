import { Routes } from '@angular/router';

export const routes: Routes = [
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
