import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule, NgForm } from '@angular/forms';

export interface Pedido {
  id: number;
  productoId: number;
  cantidad: number;
  estado: string;
}

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './orders.html',
  styleUrl: './orders.css'
})
export class Orders implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly saving = signal(false);
  protected readonly estados = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO', 'CANCELADO'];
  protected productoId: number | null = null;
  protected cantidad: number | null = null;
  protected estadosSeleccionados: Record<number, string> = {};
  private readonly ordersUrl = 'http://localhost:8080/api/bff/orders';

  ngOnInit(): void {
    this.loadOrders();
  }

  private loadOrders(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.http.get<Pedido[]>(this.ordersUrl)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pedidos) => {
          this.pedidos.set(pedidos);
          this.estadosSeleccionados = Object.fromEntries(pedidos.map((pedido) => [pedido.id, pedido.estado]));
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(error instanceof HttpErrorResponse
            ? `HTTP ${error.status}: No se pudieron cargar los pedidos.`
            : 'Código HTTP no disponible: No se pudo completar la solicitud de pedidos.');
        }
      });
  }

  protected createOrder(form: NgForm): void {
    if (this.saving() || this.loading() || form.invalid) return;
    this.beginSave();
    this.http.post(this.ordersUrl, { productoId: this.productoId, cantidad: this.cantidad }, { responseType: 'text' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          form.resetForm();
          this.finishSave('Pedido creado correctamente.');
        },
        error: (error: unknown) => this.failSave(error, 'No se pudo crear el pedido.')
      });
  }

  protected changeState(pedido: Pedido): void {
    if (this.saving() || this.loading()) return;
    this.beginSave();
    this.http.patch(`${this.ordersUrl}/${pedido.id}/estado`, null, {
      params: { estado: this.estadosSeleccionados[pedido.id] },
      responseType: 'text'
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.finishSave('Estado del pedido actualizado correctamente.'),
      error: (error: unknown) => this.failSave(error, 'No se pudo cambiar el estado del pedido.')
    });
  }

  private beginSave(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private finishSave(message: string): void {
    this.saving.set(false);
    this.successMessage.set(message);
    this.loadOrders();
  }

  private failSave(error: unknown, message: string): void {
    this.saving.set(false);
    this.errorMessage.set(error instanceof HttpErrorResponse
      ? `HTTP ${error.status}: ${message}`
      : `Código HTTP no disponible: ${message}`);
  }
}
