import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Adds the login token to every API call; an expired session sends the user back to the login page. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const authReq = token && req.url.startsWith('/api') ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.includes('/auth/login') || req.url.includes('/auth/register');
      if ((err.status === 401 || (err.status === 403 && /your account is (suspended|banned)/i.test(err.error?.message ?? ''))) && !isAuthCall) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};

/** Human-readable message from an API error. */
export function errorMessage(err: any): string {
  if (err?.status === 0) return 'Cannot reach the server. Make sure the backend is running on port 8080.';
  return err?.error?.message || err?.message || 'Something went wrong';
}
