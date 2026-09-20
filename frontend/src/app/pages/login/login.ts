import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  private readonly msalService = inject(MsalService);
  protected readonly redirecting = signal(false);
  protected readonly errorMessage = signal('');

  protected get account(): AccountInfo | null {
    return this.msalService.instance.getActiveAccount()
      ?? this.msalService.instance.getAllAccounts()[0]
      ?? null;
  }

  login(): void {
    if (this.redirecting()) return;
    this.errorMessage.set('');
    this.redirecting.set(true);
    this.msalService.loginRedirect({
      scopes: ['api://6faf8448-cd68-430c-a4f6-4ac7726e5faa/access_as_user']
    }).subscribe({
      error: () => {
        this.redirecting.set(false);
        this.errorMessage.set('No se pudo iniciar sesión. Inténtalo nuevamente.');
      }
    });
  }
}
