import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MsalService, MsalGuard } from '@azure/msal-angular';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { RolesService } from './roles.service';
import { adminGuard } from './admin.guard';
import { routes } from '../app.routes';
import { Dashboard } from '../pages/dashboard/dashboard';

describe('Monitor Admin access', () => {
  function setup(tokenRoles: string[]) {
    const payload = btoa(JSON.stringify({roles: tokenRoles}));
    TestBed.configureTestingModule({providers:[provideRouter([]), {
      provide: MsalService, useValue: {
        instance: {getActiveAccount: () => ({username:'admin'}),getAllAccounts:()=>[]},
        acquireTokenSilent: vi.fn(() => of({accessToken:'header.'+payload+'.signature'}))
      }
    }]});
  }
  it('reads every access-token role including Admin in second position', async () => {
    setup(['Operador','Admin']);
    expect(await firstValueFrom(TestBed.inject(RolesService).getRoles())).toEqual(['Operador','Admin']);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(await firstValueFrom(result as Observable<unknown>)).toBe(true);
  });
  it('redirects non-Admin users to dashboard', async () => {
    setup(['Operador']);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(TestBed.inject(Router).serializeUrl(await firstValueFrom(result as Observable<any>))).toBe('/dashboard');
  });
  it('fails closed if role lookup fails', async () => {
    TestBed.configureTestingModule({providers:[provideRouter([]), {provide:RolesService,useValue:{
      getRoles:()=>throwError(()=>new Error('offline'))
    }}]});
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(TestBed.inject(Router).serializeUrl(await firstValueFrom(result as Observable<any>))).toBe('/dashboard');
  });
  it('registers a lazy monitor route behind both guards', () => {
    expect(routes.find(r=>r.path==='rabbitmq-monitor')?.canActivate).toEqual([MsalGuard,adminGuard]);
  });
  it.each([['Operador','Admin'],['Operador']])('shows navigation only for Admin: %s', (first: string, second?: string) => {
    const roles = [first,second].filter(Boolean);
    TestBed.configureTestingModule({imports:[Dashboard],providers:[provideRouter([]),
      {provide:RolesService,useValue:{getRoles:()=>of(roles)}},
      {provide:MsalService,useValue:{instance:{getActiveAccount:()=>({name:'Test'}),getAllAccounts:()=>[]}}}]});
    const fixture = TestBed.createComponent(Dashboard); fixture.detectChanges();
    expect(!!fixture.nativeElement.querySelector('a[href="/rabbitmq-monitor"]')).toBe(roles.includes('Admin'));
    fixture.destroy();
  });
});
