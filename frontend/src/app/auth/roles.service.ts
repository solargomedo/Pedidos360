import { inject, Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { map, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class RolesService {
  private readonly msal = inject(MsalService);
  getRoles() {
    const account = this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0];
    if (!account) return of<string[]>([]);
    return this.msal.acquireTokenSilent({
      account, scopes: ['api://6faf8448-cd68-430c-a4f6-4ac7726e5faa/access_as_user']
    }).pipe(map(result => {
      // UI permissions only. The backend validates the JWT and enforces roles.
      const part = result.accessToken.split('.')[1];
      if (!part) throw new Error('Invalid token');
      const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(new TextDecoder().decode(Uint8Array.from(
        atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), c => c.charCodeAt(0))));
      return Array.isArray(payload?.roles)
        ? payload.roles.filter((role: unknown): role is string => typeof role === 'string') as string[] : [];
    }));
  }
}
