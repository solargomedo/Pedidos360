import { adminGuard } from './auth/admin.guard';
import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';
import { Orders } from './pages/orders/orders';
import { Catalog } from './pages/catalog/catalog';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard, canActivate: [MsalGuard] },
  { path: 'orders', component: Orders, canActivate: [MsalGuard] },
  { path: 'rabbitmq-monitor', loadComponent: () => import('./pages/rabbitmq-monitor/rabbitmq-monitor').then(m => m.RabbitmqMonitor), canActivate: [MsalGuard, adminGuard] },
  { path: 'catalog', component: Catalog, canActivate: [MsalGuard] }
];
