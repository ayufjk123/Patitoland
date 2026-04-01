import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class LanguageService {
  private currentLang = new BehaviorSubject<string>('ca');
  currentLang$ = this.currentLang.asObservable();

  constructor(private translate: TranslateService) {
    const savedLang = localStorage.getItem('patitoland-lang') || 'ca';
    this.translate.addLangs(['ca', 'es', 'en']);
    this.translate.use(savedLang);
    this.currentLang.next(savedLang);
  }

  toggleLanguage(): void {
    const newLang = this.currentLang.value === 'ca' ? 'es' : 'ca';
    this.setLanguage(newLang);
  }

  setLanguage(lang: string): void {
    if (['ca', 'es', 'en'].includes(lang)) {
      this.translate.use(lang);
      localStorage.setItem('patitoland-lang', lang);
      this.currentLang.next(lang);
    }
  }

  getCurrentLang(): string {
    return this.currentLang.value;
  }
}
