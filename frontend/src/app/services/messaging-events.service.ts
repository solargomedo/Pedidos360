import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { timeout } from 'rxjs';
import { MessagingEventsPage } from '../models/messaging-events';
@Injectable({ providedIn: 'root' })
export class MessagingEventsService {
  private readonly http = inject(HttpClient);
  getEvents(after: number, pedidoId: number | null) {
    let params = new HttpParams().set('after', after).set('limit', 1000);
    if (pedidoId !== null) params = params.set('pedidoId', pedidoId);
    return this.http.get<MessagingEventsPage>(
      'https://mtdgm9u772.execute-api.us-east-1.amazonaws.com/api/bff/rabbitmq/events', { params }
    ).pipe(timeout(10000));
  }
}
