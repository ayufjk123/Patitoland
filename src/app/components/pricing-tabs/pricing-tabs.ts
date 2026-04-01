import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-pricing-tabs',
  imports: [TranslateModule],
  templateUrl: './pricing-tabs.html',
  styleUrl: './pricing-tabs.scss'
})
export class PricingTabs {
  activeTab = 'general';

  setTab(tab: string): void {
    this.activeTab = tab;
  }
}
