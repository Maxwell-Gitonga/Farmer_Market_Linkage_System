import { Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/guards';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/auth/login').then(m => m.LoginPage) },
  {
    path: '', component: Shell, canActivate: [roleGuard()],
    children: [
      // Farmer
      { path: 'farmer/dashboard', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/farmer/dashboard').then(m => m.FarmerDashboardPage) },
      { path: 'farmer/listings/new', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/farmer/listing-form').then(m => m.ListingFormPage) },
      { path: 'farmer/listings/:id/recommendation', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/farmer/recommendation').then(m => m.RecommendationPage) },
      { path: 'farmer/listings/:id/matches', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/farmer/matched-buyers').then(m => m.MatchedBuyersPage) },
      { path: 'farmer/listings', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/farmer/my-listings').then(m => m.MyListingsPage) },
      { path: 'farmer/sales', canActivate: [roleGuard('FARMER')], loadComponent: () => import('./pages/common/transactions').then(m => m.TransactionsPage) },
      // Buyer
      { path: 'buyer/browse', canActivate: [roleGuard('BUYER')], loadComponent: () => import('./pages/buyer/browse').then(m => m.BrowsePage) },
      { path: 'buyer/product/:id', canActivate: [roleGuard('BUYER')], loadComponent: () => import('./pages/buyer/product-detail').then(m => m.ProductDetailPage) },
      { path: 'buyer/purchases', canActivate: [roleGuard('BUYER')], loadComponent: () => import('./pages/common/transactions').then(m => m.TransactionsPage) },
      // Admin
      { path: 'admin/overview', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/overview').then(m => m.AdminOverviewPage) },
      { path: 'admin/users', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/users').then(m => m.AdminUsersPage) },
      { path: 'admin/listings', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/listings').then(m => m.AdminListingsPage) },
      { path: 'admin/activity', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/activity').then(m => m.AdminActivityPage) },
      { path: 'admin/reports', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/reports').then(m => m.AdminReportsPage) },
      { path: 'admin/market-data', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/market-data').then(m => m.AdminMarketDataPage) },
      { path: 'admin/models', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/models').then(m => m.AdminModelsPage) },
      // Shared
      { path: 'insights', loadComponent: () => import('./pages/common/insights').then(m => m.InsightsPage) },
      { path: 'messages', canActivate: [roleGuard('FARMER', 'BUYER')], loadComponent: () => import('./pages/common/messages').then(m => m.MessagesPage) },
      { path: 'profile', loadComponent: () => import('./pages/common/profile').then(m => m.ProfilePage) },
      { path: '', pathMatch: 'full', redirectTo: 'login' },
    ]
  },
  { path: '**', redirectTo: 'login' }
];
