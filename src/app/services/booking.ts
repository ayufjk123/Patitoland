import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export type RoomType = 'SALA_PRIVADA' | 'ZONA_RESTAURACION';

export interface DayAvailability {
  date: string;
  status: 'available' | 'partial' | 'full' | 'closed';
  totalSlots: number;
  bookedSlots: number;
  privateRoomAvailable: boolean;
  bookedTimesByRoom: Record<RoomType, string[]>;
}

export interface MonthAvailability {
  month: string;
  days: DayAvailability[];
}

export interface SlotDetail {
  period: string;
  maxSlots: number;
  bookedSlots: number;
  remainingSlots: number;
  privateRoomAvailable: boolean;
}

export interface DateDetail {
  date: string;
  dayOfWeek: number;
  status: 'available' | 'partial' | 'full' | 'closed';
  slots: SlotDetail[];
}

/** Raw shape returned by the backend for each date key */
interface RawDayInfo {
  totalBookings: number;
  maxBookings: number;
  available: boolean;
  privateRoomBooked: boolean;
  slots: { time: string; room: string; tariff: string }[];
}

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly baseUrl = `${environment.apiUrl}/bookings`;

  constructor(private http: HttpClient) {}

  getMonthAvailability(yearMonth: string): Observable<MonthAvailability> {
    return this.http
      .get<Record<string, RawDayInfo>>(
        `${this.baseUrl}/availability?month=${yearMonth}`
      )
      .pipe(
        map((raw) => {
          const days: DayAvailability[] = Object.entries(raw ?? {}).map(
            ([date, info]) => {
              let status: DayAvailability['status'];
              if (!info.available && info.totalBookings >= info.maxBookings) {
                status = 'full';
              } else if (info.totalBookings > 0 && info.available) {
                status = 'partial';
              } else {
                status = 'available';
              }

              const slots = info.slots ?? [];
              const bookedTimesByRoom: Record<RoomType, string[]> = {
                SALA_PRIVADA: slots
                  .filter((s) => s.room === 'SALA_PRIVADA')
                  .map((s) => s.time),
                ZONA_RESTAURACION: slots
                  .filter((s) => s.room === 'ZONA_RESTAURACION')
                  .map((s) => s.time),
              };

              return {
                date,
                status,
                totalSlots: info.maxBookings,
                bookedSlots: info.totalBookings,
                privateRoomAvailable: !info.privateRoomBooked,
                bookedTimesByRoom,
              };
            }
          );
          return { month: yearMonth, days };
        })
      );
  }

  getDateDetail(date: string): Observable<DateDetail> {
    return this.http.get<DateDetail>(`${this.baseUrl}/date/${date}`);
  }

  createBooking(request: BookingRequest): Observable<{ id: number; message: string }> {
    return this.http.post<{ id: number; message: string }>(this.baseUrl, request);
  }
}

export interface BookingRequest {
  parentName: string;
  email: string;
  phone: string;
  childrenNames: string;
  childrenCount: string;
  roomPreference: RoomType;
  reservationDateTime: string;
  notes?: string;
}
