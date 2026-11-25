import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { Monitor } from '../models/rabbitmq-monitor';

@Injectable({ providedIn: 'root' })
export class RabbitmqMonitorService {
  private readonly http = inject(HttpClient);
  getMonitor() {
    return this.http.get<Monitor>(
      'https://mtdgm9u772.execute-api.us-east-1.amazonaws.com/api/bff/rabbitmq/monitor'
    ).pipe(timeout(45000));
  }
}
