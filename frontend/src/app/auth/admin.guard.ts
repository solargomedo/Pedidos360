import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { RolesService } from './roles.service';

export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(RolesService).getRoles().pipe(
    map(roles => roles.includes('Admin') ? true : router.createUrlTree(['/dashboard'])),
    catchError(() => of(router.createUrlTree(['/dashboard'])))
  );
};
