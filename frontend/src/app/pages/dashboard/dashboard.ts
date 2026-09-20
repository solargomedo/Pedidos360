import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {
  private readonly msalService = inject(MsalService);
  protected readonly redirecting = signal(false);
  protected readonly errorMessage = signal('');

  protected get account(): AccountInfo | null {
    return this.msalService.instance.getActiveAccount()
      ?? this.msalService.instance.getAllAccounts()[0]
      ?? null;
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
        this.errorMessage.set('No se pudo cerrar sesión. Inténtalo nuevamente.');
      }
    });
  }
}
