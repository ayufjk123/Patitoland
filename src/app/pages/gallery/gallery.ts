import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-gallery',
  imports: [TranslateModule],
  templateUrl: './gallery.html',
  styleUrl: './gallery.scss'
})
export class Gallery {
  images = [
    { src: 'img/IMG_1656.jpg', alt: 'PatitoLand Parc 1' },
    { src: 'img/IMG_1606.jpg', alt: 'PatitoLand Parc 2' },
    { src: 'img/IMG_1608.jpg', alt: 'PatitoLand Parc 3' },
    { src: 'img/IMG_1611.jpg', alt: 'PatitoLand Parc 4' },
    { src: 'img/IMG_1613.jpg', alt: 'PatitoLand Parc 5' },
    { src: 'img/IMG_1614.jpg', alt: 'PatitoLand Parc 6' },
    { src: 'img/IMG_1626.jpg', alt: 'PatitoLand Parc 7' },
    { src: 'img/IMG_1628.jpg', alt: 'PatitoLand Parc 8' },
    { src: 'img/IMG_1631.jpg', alt: 'PatitoLand Parc 9' },
    { src: 'img/IMG_1632.jpg', alt: 'PatitoLand Parc 10' },
    { src: 'img/IMG_1635.jpg', alt: 'PatitoLand Parc 11' },
    { src: 'img/IMG_1637.jpg', alt: 'PatitoLand Parc 12' },
    { src: 'img/IMG_1638.jpg', alt: 'PatitoLand Parc 13' },
    { src: 'img/IMG_1652.jpg', alt: 'PatitoLand Parc 14' }
  ];

  selectedImage: string | null = null;

  openLightbox(src: string): void {
    this.selectedImage = src;
  }

  closeLightbox(): void {
    this.selectedImage = null;
  }
}
