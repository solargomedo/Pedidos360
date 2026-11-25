import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MsalService } from '@azure/msal-angular';
import { RolesService } from '../../auth/roles.service';
import { AccountInfo } from '@azure/msal-browser';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {

  private readonly rolesService = inject(RolesService);
  protected readonly isAdmin = signal(false);

  private readonly msalService = inject(MsalService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly redirecting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly role = signal('Sin rol');

  protected get account(): AccountInfo | null {
    return this.msalService.instance.getActiveAccount()
      ?? this.msalService.instance.getAllAccounts()[0]
      ?? null;
  }

  ngOnInit(): void {
    this.detectRole();
  }

  private detectRole(): void {

    const account = this.account;

    if (!account) {
      this.errorMessage.set('No se encontró una cuenta autenticada.');
      return;
    }

    this.rolesService.getRoles().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: roles => {
        this.role.set(roles.length ? roles.join(', ') : 'Sin rol');
        this.isAdmin.set(roles.includes('Admin'));
      },
      error: () => this.errorMessage.set('No se pudieron consultar los permisos del usuario.')
    });
  }

  logout(): void {

    if (this.redirecting()) return;

    this.errorMessage.set('');
    this.redirecting.set(true);

    this.msalService.logoutRedirect({
      account: this.account,
      postLogoutRedirectUri: window.location.origin + (window.location.hostname === 'localhost' ? '' : '/Pedidos360/')
    }).subscribe({
      error: () => {
        this.redirecting.set(false);
        this.errorMessage.set(
          'No se pudo cerrar sesión. Inténtalo nuevamente.'
        );
      }
    });
  }
}