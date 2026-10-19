import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangeDetail, ChangeList, Continuation } from '../models/change';
import { ProjectCard, ProjectDetail } from '../models/project';
import { Work } from '../models/work';

@Injectable({ providedIn: 'root' })
export class PortfolioApiService {
  private readonly http = inject(HttpClient);

  portfolio(includeArchived = false): Observable<ProjectCard[]> {
    const params = new HttpParams().set('includeArchived', includeArchived);
    return this.http.get<ProjectCard[]>('/api/portfolio', { params });
  }

  project(id: string): Observable<ProjectDetail> {
    return this.http.get<ProjectDetail>(`/api/projects/${encodeURIComponent(id)}`);
  }

  changes(id: string): Observable<ChangeList> {
    return this.http.get<ChangeList>(`/api/projects/${encodeURIComponent(id)}/changes`);
  }

  change(id: string, name: string): Observable<ChangeDetail> {
    return this.http.get<ChangeDetail>(
      `/api/projects/${encodeURIComponent(id)}/changes/${encodeURIComponent(name)}`,
    );
  }

  continuation(id: string, name: string): Observable<Continuation> {
    return this.http.get<Continuation>(
      `/api/projects/${encodeURIComponent(id)}/changes/${encodeURIComponent(name)}/continuation`,
    );
  }

  work(): Observable<Work> {
    return this.http.get<Work>('/api/work');
  }
}
