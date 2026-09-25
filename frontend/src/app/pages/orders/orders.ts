import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule, NgForm } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';

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
  private readonly msalService = inject(MsalService);

  // Roles del usuario autenticado
  protected readonly isAdmin = signal(false);
  protected readonly isOperador = signal(false);
  protected readonly isCliente = signal(false);

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly saving = signal(false);

  protected readonly estados = [
    'CREADO',
    'ACEPTADO',
    'EN_PREPARACION',
    'DESPACHADO',
    'ENTREGADO',
    'CANCELADO'
  ];

  protected productoId: number | null = null;
  protected cantidad: number | null = null;

  protected estadosSeleccionados: Record<number, string> = {};

  readonly ordersUrl =
    'https://o1kt0r3yj9.execute-api.us-east-1.amazonaws.com/api/bff/orders';

  ngOnInit(): void {
    this.detectRole();
  }

  /**
   * Obtiene el Access Token desde MSAL y lee el claim "roles".
   *
   * Esto se utiliza para adaptar la interfaz.
   * La seguridad real sigue estando en el BFF.
   */
  private detectRole(): void {

    const account =
      this.msalService.instance.getActiveAccount()
      ?? this.msalService.instance.getAllAccounts()[0];

    if (!account) {
      this.errorMessage.set('No se encontró una cuenta autenticada.');
      this.loading.set(false);
      return;
    }

    this.msalService.acquireTokenSilent({
      account,
      scopes: [
        'api://6faf8448-cd68-430c-a4f6-4ac7726e5faa/access_as_user'
      ]
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: (result) => {

          try {

            const parts = result.accessToken.split('.');

            if (parts.length !== 3 || !parts[1]) {
              throw new Error('JWT inválido');
            }

            const base64 = parts[1]
              .replace(/-/g, '+')
              .replace(/_/g, '/');

            const bytes = Uint8Array.from(
              atob(
                base64.padEnd(
                  Math.ceil(base64.length / 4) * 4,
                  '='
                )
              ),
              (character) => character.charCodeAt(0)
            );

            const payload: unknown = JSON.parse(
              new TextDecoder(
                'utf-8',
                { fatal: true }
              ).decode(bytes)
            );

            if (
              !payload ||
              typeof payload !== 'object' ||
              Array.isArray(payload)
            ) {
              throw new Error('Payload inválido');
            }

            const roles =
              (payload as Record<string, unknown>)['roles'];

            const roleList =
              Array.isArray(roles)
                ? roles
                : [];

            this.isAdmin.set(
              roleList.includes('Admin')
            );

            this.isOperador.set(
              roleList.includes('Operador')
            );

            this.isCliente.set(
              roleList.includes('Cliente')
            );

            /*
             * Admin y Operador pueden consultar
             * actualmente el listado completo.
             *
             * Cliente no ejecuta este GET porque
             * todavía no tenemos implementado
             * "mis propios pedidos".
             */
            if (
              this.isAdmin() ||
              this.isOperador()
            ) {
              this.loadOrders();
            } else {
              this.loading.set(false);
            }

          } catch {

            this.errorMessage.set(
              'No se pudieron leer los permisos del usuario.'
            );

            this.loading.set(false);
          }
        },

        error: () => {

          this.errorMessage.set(
            'No se pudieron consultar los permisos.'
          );

          this.loading.set(false);
        }
      });
  }

  /**
   * Obtiene el listado de pedidos.
   */
  private loadOrders(): void {

    this.loading.set(true);
    this.errorMessage.set('');

    this.http
      .get<Pedido[]>(this.ordersUrl)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: (pedidos) => {

          this.pedidos.set(pedidos);

          this.estadosSeleccionados =
            Object.fromEntries(
              pedidos.map(
                (pedido) => [
                  pedido.id,
                  pedido.estado
                ]
              )
            );

          this.loading.set(false);
        },

        error: (error: unknown) => {

          this.loading.set(false);

          this.errorMessage.set(
            error instanceof HttpErrorResponse
              ? `HTTP ${error.status}: No se pudieron cargar los pedidos.`
              : 'Código HTTP no disponible: No se pudo completar la solicitud de pedidos.'
          );
        }
      });
  }

  /**
   * Crea un nuevo pedido.
   */
  protected createOrder(form: NgForm): void {

    if (
      this.saving() ||
      this.loading() ||
      form.invalid
    ) {
      return;
    }

    this.beginSave();

    this.http.post(
      this.ordersUrl,
      {
        productoId: this.productoId,
        cantidad: this.cantidad
      },
      {
        responseType: 'text'
      }
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: () => {

          form.resetForm();

          this.finishSave(
            'Pedido creado correctamente.'
          );
        },

        error: (error: unknown) =>
          this.failSave(
            error,
            'No se pudo crear el pedido.'
          )
      });
  }

  /**
   * Cambia el estado de un pedido.
   */
  protected changeState(pedido: Pedido): void {

    // Cliente no puede cambiar estados desde la interfaz.
    if (
      this.isCliente() ||
      this.saving() ||
      this.loading()
    ) {
      return;
    }

    this.beginSave();

    this.http.patch(
      `${this.ordersUrl}/${pedido.id}/estado`,
      null,
      {
        params: {
          estado:
            this.estadosSeleccionados[pedido.id]
        },
        responseType: 'text'
      }
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({

        next: () =>
          this.finishSave(
            'Estado del pedido actualizado correctamente.'
          ),

        error: (error: unknown) =>
          this.failSave(
            error,
            'No se pudo cambiar el estado del pedido.'
          )
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

    /*
     * Solo Admin y Operador pueden consultar
     * actualmente todos los pedidos.
     */
    if (
      this.isAdmin() ||
      this.isOperador()
    ) {
      this.loadOrders();
    }
  }

  private failSave(
    error: unknown,
    message: string
  ): void {

    this.saving.set(false);

    this.errorMessage.set(
      error instanceof HttpErrorResponse
        ? `HTTP ${error.status}: ${message}`
        : `Código HTTP no disponible: ${message}`
    );
  }
}