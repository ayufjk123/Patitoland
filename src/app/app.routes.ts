import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Gallery } from './pages/gallery/gallery';
import { Pricing } from './pages/pricing/pricing';
import { Birthday } from './pages/birthday/birthday';
import { Contact } from './pages/contact/contact';
import { Rules } from './pages/rules/rules';
import { Promotions } from './pages/promotions/promotions';
import { Coupons } from './pages/coupons/coupons';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'galeria', component: Gallery },
  { path: 'tarifas', component: Pricing },
  { path: 'cumpleanos', component: Birthday },
  { path: 'promociones', component: Promotions },
  { path: 'normas', component: Rules },
  { path: 'contacto', component: Contact },
  // Staff-only coupon validation tool (not linked in the navbar)
  { path: 'cupones', component: Coupons },
  { path: '**', redirectTo: '' }
];
