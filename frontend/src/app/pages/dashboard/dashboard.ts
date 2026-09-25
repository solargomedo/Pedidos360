import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {

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
              character => character.charCodeAt(0)
            );

            const payload: unknown = JSON.parse(
              new TextDecoder('utf-8', { fatal: true }).decode(bytes)
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

            if (Array.isArray(roles) && roles.length > 0) {
              this.role.set(String(roles[0]));
            }

          } catch {
            this.errorMessage.set(
              'No se pudo identificar el rol del usuario.'
            );
          }
        },

        error: () => {
          this.errorMessage.set(
            'No se pudieron consultar los permisos del usuario.'
          );
        }
      });
  }

  logout(): void {

    if (this.redirecting()) return;

    this.errorMessage.set('');
    this.redirecting.set(true);

    this.msalService.logoutRedirect({
      account: this.account,
      postLogoutRedirectUri: 'http://localhost:4200'
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