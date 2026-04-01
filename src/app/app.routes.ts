import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Gallery } from './pages/gallery/gallery';
import { Pricing } from './pages/pricing/pricing';
import { Birthday } from './pages/birthday/birthday';
import { Contact } from './pages/contact/contact';
import { Rules } from './pages/rules/rules';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'galeria', component: Gallery },
  { path: 'tarifas', component: Pricing },
  { path: 'cumpleanos', component: Birthday },
  { path: 'normas', component: Rules },
  { path: 'contacto', component: Contact },
  { path: '**', redirectTo: '' }
];
