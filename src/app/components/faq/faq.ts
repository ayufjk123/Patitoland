import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-faq',
  imports: [TranslateModule],
  templateUrl: './faq.html',
  styleUrl: './faq.scss'
})
export class Faq {
  openIndex: number | null = null;

  toggle(index: number): void {
    this.openIndex = this.openIndex === index ? null : index;
  }
}
