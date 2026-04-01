import { Component } from '@angular/core';
import { PricingTabs } from '../../components/pricing-tabs/pricing-tabs';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-pricing',
  imports: [PricingTabs, TranslateModule],
  templateUrl: './pricing.html',
  styleUrl: './pricing.scss'
})
export class Pricing {}
