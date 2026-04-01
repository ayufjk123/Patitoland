import { Component } from '@angular/core';
import { Hero } from '../../components/hero/hero';
import { Features } from '../../components/features/features';
import { PricingTabs } from '../../components/pricing-tabs/pricing-tabs';
import { Marquee } from '../../components/marquee/marquee';
import { Faq } from '../../components/faq/faq';

@Component({
  selector: 'app-home',
  imports: [Hero, Features, PricingTabs, Marquee, Faq],
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home {}
