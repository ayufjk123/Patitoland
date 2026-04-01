import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-features',
  imports: [RouterLink, TranslateModule],
  templateUrl: './features.html',
  styleUrl: './features.scss'
})
export class Features {}
