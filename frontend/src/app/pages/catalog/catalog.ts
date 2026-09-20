import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule, NgForm } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';

export interface Producto {
  id: number;
  nombre: string;
  precio: number;
  stock: number;
}

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './catalog.html',
  styleUrl: './catalog.css'
})
export class Catalog implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly msalService = inject(MsalService);
  private readonly catalogUrl = 'http://localhost:8080/api/bff/catalog';
  readonly isAdmin = signal(false);
  protected readonly saving = signal(false);
  protected readonly successMessage = signal('');
  protected readonly roleError = signal('');
  protected editingId: number | null = null;
  protected nombre = '';
  protected precio: number | null = null;
  protected stock: number | null = null;
  private readonly currencyFormatter = new Intl.NumberFormat('es-CL', {
    style: 'currency',
    currency: 'CLP',
    maximumFractionDigits: 0
  });

  protected readonly productos = signal<Producto[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal('');

  ngOnInit(): void {
    this.detectAdmin();
    this.loadCatalog();
  }

  private detectAdmin(): void {
    const account = this.msalService.instance.getActiveAccount()
      ?? this.msalService.instance.getAllAccounts()[0];
    if (!account) {
      this.roleError.set('No se encontró una cuenta para consultar los permisos.');
      return;
    }
    this.msalService.acquireTokenSilent({
      account,
      scopes: ['api://6faf8448-cd68-430c-a4f6-4ac7726e5faa/access_as_user']
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        try {
          // Se inspecciona solo el token devuelto por MSAL, nunca el almacenamiento.
          const parts = result.accessToken.split('.');
          if (parts.length !== 3 || !parts[1]) throw new Error('JWT inválido');
          const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
          const bytes = Uint8Array.from(
            atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')),
            (character) => character.charCodeAt(0)
          );
          const payload: unknown = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
          if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('Payload inválido');
          const roles = (payload as Record<string, unknown>)['roles'];
          // Esto controla la interfaz; el BFF sigue autorizando cada operación.
          this.isAdmin.set(Array.isArray(roles) && roles.includes('Admin'));
        } catch {
          this.roleError.set('No se pudieron leer los permisos del access token.');
        }
      },
      error: () => this.roleError.set('No se pudieron consultar los permisos. Intenta iniciar sesión nuevamente.')
    });
  }

  private loadCatalog(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.http.get<Producto[]>(this.catalogUrl)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (productos) => {
          this.productos.set(productos);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(error instanceof HttpErrorResponse
            ? `HTTP ${error.status}: No se pudo cargar el catálogo.`
            : 'Código HTTP no disponible: No se pudo completar la solicitud del catálogo.');
        }
      });
  }

  protected editProduct(producto: Producto): void {
    if (!this.isAdmin() || this.saving() || this.loading()) return;
    this.editingId = producto.id;
    this.nombre = producto.nombre;
    this.precio = producto.precio;
    this.stock = producto.stock;
    this.successMessage.set('');
  }

  protected resetForm(form: NgForm): void {
    this.editingId = null;
    form.resetForm({ nombre: '', precio: null, stock: null });
  }

  protected saveProduct(form: NgForm): void {
    if (!this.isAdmin() || this.saving() || this.loading() || form.invalid) return;
    const body = { nombre: this.nombre, precio: this.precio, stock: this.stock };
    const editing = this.editingId !== null;
    const request = editing
      ? this.http.put(`${this.catalogUrl}/${this.editingId}`, body, { responseType: 'text' })
      : this.http.post(this.catalogUrl, body, { responseType: 'text' });
    this.beginSave();
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.resetForm(form);
        this.finishSave(editing ? 'Producto actualizado correctamente.' : 'Producto creado correctamente.');
      },
      error: (error: unknown) => this.failSave(error, 'No se pudo guardar el producto.')
    });
  }

  protected deleteProduct(producto: Producto): void {
    if (!this.isAdmin() || this.saving() || this.loading()) return;
    if (!window.confirm(`¿Eliminar el producto "${producto.nombre}"?`)) return;
    this.beginSave();
    this.http.delete(`${this.catalogUrl}/${producto.id}`, { responseType: 'text' })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          if (this.editingId === producto.id) {
            this.editingId = null;
            this.nombre = '';
            this.precio = null;
            this.stock = null;
          }
          this.finishSave('Producto eliminado correctamente.');
        },
        error: (error: unknown) => this.failSave(error, 'No se pudo eliminar el producto.')
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
    this.loadCatalog();
  }

  private failSave(error: unknown, message: string): void {
    this.saving.set(false);
    this.errorMessage.set(error instanceof HttpErrorResponse
      ? `HTTP ${error.status}: ${message}`
      : `Código HTTP no disponible: ${message}`);
  }

  protected formatPrecio(precio: number): string {
    return this.currencyFormatter.format(precio);
  }
}
