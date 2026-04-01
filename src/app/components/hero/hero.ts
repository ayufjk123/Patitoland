import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-hero',
  imports: [RouterLink, TranslateModule],
  templateUrl: './hero.html',
  styleUrl: './hero.scss'
})
export class Hero {}
