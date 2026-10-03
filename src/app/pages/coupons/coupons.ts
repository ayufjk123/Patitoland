import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CouponService, CouponResult } from '../../services/coupon';

interface GeneratedBatch {
  codes: string[];
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  freeEntry: boolean;
  validUntil: string;
  label: string;
  singleUse: boolean;
}

@Component({
  selector: 'app-coupons',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './coupons.html',
  styleUrl: './coupons.scss',
})
export class Coupons {
  private readonly STORAGE_KEY = 'coupon_token';

  token = signal<string>(sessionStorage.getItem(this.STORAGE_KEY) || '');
  authed = signal<boolean>(false);
  authError = signal<boolean>(false);
  passwordInput = signal('');

  code = signal('');
  result = signal<CouponResult | null>(null);
  checking = signal(false);
  redeeming = signal(false);

  showGenerate = signal(false);
  genCount = signal(10);
  genType = signal<'PERCENT' | 'FIXED' | 'FREE'>('FIXED');
  genValue = signal(10);
  genValidUntil = signal(this.oneMonthFromToday());
  genLabel = signal('');
  genSingleUse = signal(true);
  genCodes = signal<string[]>([]);
  generatedBatch = signal<GeneratedBatch | null>(null);
  generating = signal(false);
  pdfExporting = signal(false);
  generateError = signal('');
  pdfError = signal('');
  copyDone = signal(false);
  readonly today = this.localDate(new Date());

  // In-app camera scanning (uses the native BarcodeDetector API; unsupported on iOS Safari).
  scanSupported = typeof (globalThis as any).BarcodeDetector !== 'undefined';
  scanning = signal(false);
  scanError = signal(false);
  private stream?: MediaStream;
  private rafId = 0;

  constructor(private couponService: CouponService, private route: ActivatedRoute) {
    // A coupon's QR encodes /cupones?code=XXX, so scanning it with a phone camera lands here.
    const qp = this.route.snapshot.queryParamMap.get('code');
    if (qp) this.code.set(qp.trim().toUpperCase());

    const t = this.token();
    if (t) {
      this.couponService.checkAuth(t).subscribe({
        next: () => {
          this.authed.set(true);
          if (this.code()) this.validate();
        },
        error: () => {
          sessionStorage.removeItem(this.STORAGE_KEY);
          this.token.set('');
        },
      });
    }
  }

  login(): void {
    const pwd = this.passwordInput().trim();
    if (!pwd) return;
    this.authError.set(false);
    this.couponService.checkAuth(pwd).subscribe({
      next: () => {
        this.token.set(pwd);
        sessionStorage.setItem(this.STORAGE_KEY, pwd);
        this.authed.set(true);
        this.passwordInput.set('');
        if (this.code()) this.validate();
      },
      error: () => this.authError.set(true),
    });
  }

  logout(): void {
    sessionStorage.removeItem(this.STORAGE_KEY);
    this.token.set('');
    this.authed.set(false);
    this.result.set(null);
    this.code.set('');
  }

  validate(): void {
    const c = this.code().trim();
    if (!c) return;
    this.checking.set(true);
    this.result.set(null);
    this.couponService.validate(c, this.token()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.checking.set(false);
      },
      error: () => this.checking.set(false),
    });
  }

  redeem(): void {
    const r = this.result();
    if (!r) return;
    this.redeeming.set(true);
    this.couponService.redeem(r.code, this.token()).subscribe({
      next: (res) => {
        this.result.set(res);
        this.redeeming.set(false);
      },
      error: () => this.redeeming.set(false),
    });
  }

  newCheck(): void {
    this.code.set('');
    this.result.set(null);
  }

  generate(): void {
    if (!this.canGenerate()) return;
    const selectedType = this.genType();
    const freeEntry = selectedType === 'FREE';
    const discountType = freeEntry ? 'PERCENT' : selectedType;
    const discountValue = freeEntry ? 100 : this.genValue();
    const request = {
      count: this.genCount(),
      discountType,
      discountValue,
      validUntil: this.genValidUntil() || null,
      label: this.genLabel().trim() || (freeEntry ? 'Entrada gratis' : null),
      singleUse: this.genSingleUse(),
    };
    this.generating.set(true);
    this.genCodes.set([]);
    this.generatedBatch.set(null);
    this.generateError.set('');
    this.pdfError.set('');
    this.copyDone.set(false);
    this.couponService
      .generate(request, this.token())
      .subscribe({
        next: (res) => {
          this.genCodes.set(res.codes);
          this.generatedBatch.set({
            codes: res.codes,
            discountType: request.discountType,
            discountValue: request.discountValue,
            freeEntry,
            validUntil: request.validUntil || '',
            label: request.label || '',
            singleUse: request.singleUse,
          });
          this.generating.set(false);
        },
        error: (err) => {
          this.generateError.set(this.apiError(err));
          this.generating.set(false);
        },
      });
  }

  canGenerate(): boolean {
    const count = this.genCount();
    const value = this.genValue();
    const freeEntry = this.genType() === 'FREE';
    return Number.isInteger(count)
      && count >= 1
      && count <= 100
      && (freeEntry || (Number.isFinite(value)
        && value > 0
        && (this.genType() !== 'PERCENT' || value <= 100)))
      && (!this.genValidUntil() || this.genValidUntil() >= this.today)
      && this.genLabel().trim().length <= 100;
  }

  async copyCodes(): Promise<void> {
    if (!this.genCodes().length || !navigator.clipboard) return;
    await navigator.clipboard.writeText(this.genCodes().join('\n'));
    this.copyDone.set(true);
  }

  downloadCsv(): void {
    const batch = this.generatedBatch();
    if (!batch) return;

    const header = ['codigo', 'tipo_descuento', 'valor', 'valido_hasta', 'un_solo_uso', 'etiqueta', 'url_validacion'];
    const rows = batch.codes.map((couponCode) => [
      couponCode,
      batch.discountType,
      String(batch.discountValue),
      batch.validUntil,
      batch.singleUse ? 'si' : 'no',
      batch.label,
      `${window.location.origin}/cupones?code=${encodeURIComponent(couponCode)}`,
    ]);
    const csv = [header, ...rows]
      .map((row) => row.map((cell) => this.csvCell(cell)).join(';'))
      .join('\r\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `cupones-${this.today}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  canDownloadDesignedPdf(): boolean {
    const batch = this.generatedBatch();
    return !!batch
      && batch.codes.length <= 100;
  }

  async downloadDesignedPdf(): Promise<void> {
    const batch = this.generatedBatch();
    if (!batch || !this.canDownloadDesignedPdf()) return;

    this.pdfExporting.set(true);
    this.pdfError.set('');
    try {
      const [{ jsPDF }, qrCodeModule, template] = await Promise.all([
        import('jspdf'),
        import('qrcode'),
        this.loadImage('/img/coupon-template-10-euros.png'),
        this.loadCouponFonts(),
      ]);
      const qrCode = qrCodeModule.default ?? qrCodeModule;
      if (typeof qrCode.toDataURL !== 'function') {
        throw new Error('QR code renderer unavailable');
      }
      const pageWidth = 150;
      const pageHeight = 100;
      const pdf = new jsPDF({
        orientation: 'landscape',
        unit: 'mm',
        format: [pageWidth, pageHeight],
        compress: true,
      });

      for (let index = 0; index < batch.codes.length; index++) {
        const couponCode = batch.codes[index];
        if (index > 0) pdf.addPage([pageWidth, pageHeight], 'landscape');
        const coupon = await this.renderDesignedCoupon(template, couponCode, batch, qrCode.toDataURL);
        pdf.addImage(coupon, 'JPEG', 0, 0, pageWidth, pageHeight, undefined, 'FAST');
      }

      const value = this.formatDiscountValue(batch.discountValue);
      const suffix = batch.freeEntry
        ? 'entrada-gratis'
        : batch.discountType === 'PERCENT' ? `${value}-por-ciento` : `${value}-euros`;
      this.downloadBlob(pdf.output('blob'), `cupones-${suffix}-${this.today}.pdf`);
    } catch (error) {
      console.error('Failed to create coupon PDF', error);
      this.pdfError.set('No se pudo crear el PDF. Inténtalo de nuevo.');
    } finally {
      this.pdfExporting.set(false);
    }
  }

  async startScan(): Promise<void> {
    if (!this.scanSupported) return;
    this.scanError.set(false);
    this.scanning.set(true);
    this.result.set(null);
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
      });
      // The <video> element only exists once scanning() is true; wait a tick for it to render.
      setTimeout(() => this.runDetectionLoop(), 0);
    } catch {
      this.scanError.set(true);
      this.scanning.set(false);
    }
  }

  private async runDetectionLoop(): Promise<void> {
    const video = document.getElementById('scan-video') as HTMLVideoElement | null;
    if (!video || !this.stream) {
      this.stopScan();
      return;
    }
    video.srcObject = this.stream;
    try {
      await video.play();
    } catch {
      /* autoplay may need user gesture; the click already provided it */
    }
    const detector = new (globalThis as any).BarcodeDetector({ formats: ['qr_code'] });
    const tick = async () => {
      if (!this.scanning()) return;
      try {
        const found = await detector.detect(video);
        if (found && found.length) {
          this.applyScanned(found[0].rawValue ?? '');
          return;
        }
      } catch {
        /* transient detect errors are ignored */
      }
      this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  private applyScanned(raw: string): void {
    let code = (raw || '').trim();
    const m = code.match(/[?&]code=([^&]+)/i);
    if (m) code = decodeURIComponent(m[1]);
    this.stopScan();
    if (code) {
      this.code.set(code.toUpperCase());
      this.validate();
    }
  }

  stopScan(): void {
    this.scanning.set(false);
    cancelAnimationFrame(this.rafId);
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = undefined;
  }

  discountText(r: CouponResult): string {
    if (r.discountType === 'PERCENT' && r.discountValue === 100) return 'Entrada gratis (100 %)';
    if (r.discountType === 'PERCENT') return `${r.discountValue}%`;
    if (r.discountType === 'FIXED') return `${r.discountValue} €`;
    return '';
  }

  private csvCell(value: string): string {
    return `"${value.replaceAll('"', '""')}"`;
  }

  private async renderDesignedCoupon(
    template: HTMLImageElement,
    couponCode: string,
    batch: GeneratedBatch,
    toDataURL: typeof import('qrcode').toDataURL
  ): Promise<HTMLCanvasElement> {
    const canvas = document.createElement('canvas');
    canvas.width = 1536;
    canvas.height = 1024;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Canvas unavailable');
    context.drawImage(template, 0, 0, canvas.width, canvas.height);

    if (batch.discountType !== 'FIXED' || batch.discountValue !== 10) {
      this.drawDynamicDiscount(context, batch);
    }

    const validationUrl = `${window.location.origin}/cupones?code=${encodeURIComponent(couponCode)}`;
    const qrDataUrl = await toDataURL(validationUrl, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 420,
      color: { dark: '#003d5f', light: '#ffffff' },
    });
    const qr = await this.loadImage(qrDataUrl);

    context.save();
    context.shadowColor = 'rgba(74, 39, 10, 0.22)';
    context.shadowBlur = 18;
    context.fillStyle = '#fffaf0';
    context.beginPath();
    context.roundRect(1265, 95, 190, 220, 22);
    context.fill();
    context.restore();

    context.drawImage(qr, 1280, 110, 160, 160);
    context.fillStyle = '#003d5f';
    context.font = '600 22px "Patitoland IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, monospace';
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(couponCode, 1360, 292);

    return canvas;
  }

  private drawDynamicDiscount(context: CanvasRenderingContext2D, batch: GeneratedBatch): void {
    context.save();

    const background = context.createRadialGradient(1045, 365, 20, 1045, 365, 470);
    background.addColorStop(0, '#fffaf0');
    background.addColorStop(1, '#fff0d7');
    context.fillStyle = background;
    context.beginPath();
    context.roundRect(690, 200, 710, 390, 38);
    context.fill();

    const amount = batch.freeEntry
      ? 'ENTRADA'
      : `${this.formatDiscountValue(batch.discountValue)}${batch.discountType === 'PERCENT' ? '%' : '€'}`;
    const amountSize = batch.freeEntry ? 150 : amount.length <= 3 ? 250 : amount.length <= 4 ? 220 : 185;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.font = `700 ${amountSize}px "Patitoland Fredoka", Arial Rounded MT Bold, Arial, sans-serif`;
    context.lineJoin = 'round';
    context.lineWidth = 18;
    context.strokeStyle = '#fff8e8';
    context.shadowColor = 'rgba(115, 54, 0, 0.18)';
    context.shadowBlur = 10;
    context.shadowOffsetY = 8;
    context.strokeText(amount, 1045, 345);
    context.fillStyle = '#f26b00';
    context.fillText(amount, 1045, 345);

    context.shadowColor = 'transparent';
    context.fillStyle = '#087ba7';
    context.beginPath();
    context.roundRect(745, 465, 600, 105, 18);
    context.fill();
    context.strokeStyle = '#e6f7ff';
    context.lineWidth = 3;
    context.setLineDash([12, 10]);
    context.strokeRect(758, 478, 574, 79);
    context.setLineDash([]);

    context.fillStyle = '#ffffff';
    context.font = '700 66px "Patitoland Fredoka", Arial Rounded MT Bold, Arial, sans-serif';
    context.shadowColor = 'rgba(0, 48, 75, 0.35)';
    context.shadowBlur = 6;
    context.shadowOffsetY = 5;
    context.fillText(
      batch.freeEntry ? 'GRATIS' : batch.discountType === 'PERCENT' ? 'DESCUENTO' : 'EUROS',
      1045,
      518
    );
    context.restore();
  }

  private formatDiscountValue(value: number): string {
    return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2)));
  }

  private loadImage(source: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error(`Could not load image: ${source}`));
      image.src = source;
    });
  }

  private async loadCouponFonts(): Promise<void> {
    const fontSet = document.fonts;
    if (!fontSet || typeof fontSet.load !== 'function') return;

    try {
      await Promise.all([
        fontSet.load('700 16px "Patitoland Fredoka"', '0123456789%€DESCUENTO'),
        fontSet.load('600 16px "Patitoland IBM Plex Mono"', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'),
      ]);
    } catch {
      // Font loading must never block PDF creation. Canvas uses the declared
      // system fallbacks if a browser cannot load the bundled web fonts.
    }
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private localDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private oneMonthFromToday(): string {
    const now = new Date();
    const targetMonth = now.getMonth() + 1;
    const target = new Date(now.getFullYear(), targetMonth, 1);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    target.setDate(Math.min(now.getDate(), lastDay));
    return this.localDate(target);
  }

  private apiError(err: any): string {
    const message = err?.error?.error;
    if (typeof message === 'string' && message) return message;
    if (err?.status === 401) return 'La sesión ha caducado. Sal y vuelve a entrar.';
    return 'No se pudieron generar los cupones. Inténtalo de nuevo.';
  }
}
