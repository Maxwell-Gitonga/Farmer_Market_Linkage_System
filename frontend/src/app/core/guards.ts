import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Role-based route protection. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.loggedIn()) return router.parseUrl('/login');
    if (roles.length && !roles.includes(auth.role()!)) return router.parseUrl(auth.home());
    return true;
  };
}

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.loggedIn() ? inject(Router).parseUrl(auth.home()) : true;
};
