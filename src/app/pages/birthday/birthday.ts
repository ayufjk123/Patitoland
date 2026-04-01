import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { CalendarComponent } from '../../components/calendar/calendar';

@Component({
  selector: 'app-birthday',
  imports: [TranslateModule, CalendarComponent],
  templateUrl: './birthday.html',
  styleUrl: './birthday.scss'
})
export class Birthday {}
