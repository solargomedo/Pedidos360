import { MessageActivity } from './message-activity';
import { Component, ViewChild, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DOCUMENT } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, defer, EMPTY, exhaustMap, filter, finalize, fromEvent, merge, Subject, tap, timer } from 'rxjs';
import { RabbitmqMonitorService } from '../../services/rabbitmq-monitor.service';
import { Monitor } from '../../models/rabbitmq-monitor';

@Component({
  selector: 'app-rabbitmq-monitor', standalone: true, imports: [DatePipe, RouterLink, MessageActivity],
  templateUrl: './rabbitmq-monitor.html', styleUrl: './rabbitmq-monitor.css'
})
export class RabbitmqMonitor implements OnInit {
  @ViewChild(MessageActivity) private activity?: MessageActivity;
  private readonly api = inject(RabbitmqMonitorService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly manual = new Subject<void>();
  private failures = 0;
  private nextAttempt = 0;
  private blocked = false;
  protected readonly data = signal<Monitor | null>(null);
  protected readonly loading = signal(false);
  protected readonly attempted = signal(false);
  protected readonly error = signal('');
  protected readonly stale = signal(false);
  protected readonly hidden = signal(this.document.hidden);
  protected readonly receivedAt = signal<Date | null>(null);

  ngOnInit(): void {
    const visibility = fromEvent(this.document, 'visibilitychange').pipe(
      tap(() => this.hidden.set(this.document.hidden)), filter(() => !this.document.hidden));
    merge(timer(0, 5000), visibility, this.manual).pipe(
      filter(() => !this.document.hidden && !this.blocked && Date.now() >= this.nextAttempt),
      exhaustMap(() => defer(() => {
        this.loading.set(true);
        return this.api.getMonitor().pipe(
          tap(result => this.accept(result)),
          catchError((error: unknown) => {
            this.failures++;
            this.nextAttempt = Date.now() + Math.min(60000, 5000 * 2 ** Math.min(this.failures, 4));
            this.stale.set(true);
            if (error instanceof HttpErrorResponse && error.status === 503
                && error.error?.status === 'unavailable') this.data.set(error.error as Monitor);
            if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
              this.blocked = true;
              this.error.set(error.status === 403 ? 'Acceso denegado: se requiere rol Admin.' : 'La sesi?n no est? autorizada. Vuelve a iniciar sesi?n.');
            } else {
              this.error.set('No se pudo actualizar el monitor. El servicio puede estar apagado o inaccesible. Se reintentar? con espera progresiva.');
            }
            return EMPTY;
          }),
          finalize(() => { this.loading.set(false); this.attempted.set(true); })
        );
      })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe();
  }

  private accept(result: Monitor): void {
    this.data.set(result);
    this.receivedAt.set(new Date());
    this.error.set('');
    this.stale.set(false);
    this.failures = 0;
    this.nextAttempt = 0;
  }

  protected refresh(): void {
    if (this.loading() || this.document.hidden) return;
    this.activity?.refresh();
    this.nextAttempt = 0;
    this.blocked = false;
    this.manual.next();
  }

  protected state(value: boolean | null | undefined): string {
    return value == null ? 'Desconocido' : value ? 'Disponible' : 'No disponible';
  }
  protected alarm(value: boolean | null | undefined): string {
    return value == null ? '?' : value ? 'Activa' : 'Sin alarma';
  }
}
