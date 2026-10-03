import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type CouponStatus = 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'REDEEMED';

export interface CouponResult {
  status: CouponStatus;
  code: string;
  discountType?: 'PERCENT' | 'FIXED';
  discountValue?: number;
  validUntil?: string | null;
  singleUse?: boolean;
  redeemed?: boolean;
  redeemedAt?: string | null;
  label?: string | null;
}

export interface GenerateRequest {
  count: number;
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  validUntil?: string | null;
  label?: string | null;
  singleUse: boolean;
}

@Injectable({ providedIn: 'root' })
export class CouponService {
  private readonly baseUrl = `${environment.apiUrl}/coupons`;

  constructor(private http: HttpClient) {}

  private opts(token: string) {
    return { headers: new HttpHeaders({ 'X-Coupon-Token': token }) };
  }

  checkAuth(token: string): Observable<{ ok: boolean }> {
    return this.http.get<{ ok: boolean }>(`${this.baseUrl}/auth`, this.opts(token));
  }

  validate(code: string, token: string): Observable<CouponResult> {
    return this.http.post<CouponResult>(`${this.baseUrl}/validate`, { code }, this.opts(token));
  }

  redeem(code: string, token: string): Observable<CouponResult> {
    return this.http.post<CouponResult>(`${this.baseUrl}/redeem`, { code }, this.opts(token));
  }

  generate(req: GenerateRequest, token: string): Observable<{ count: number; codes: string[] }> {
    return this.http.post<{ count: number; codes: string[] }>(`${this.baseUrl}/generate`, req, this.opts(token));
  }
}
