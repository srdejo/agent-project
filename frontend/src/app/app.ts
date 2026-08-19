import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProjectApiService } from './core/services/project-api.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, DatePipe],
  templateUrl: './app.html',
})
export class App {
  private readonly api = inject(ProjectApiService);

  private readonly response = toSignal(this.api.list(), { initialValue: null });

  readonly lastSync = computed(() => this.response()?.lastSync ?? null);
}
