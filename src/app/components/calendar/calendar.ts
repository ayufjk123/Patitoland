import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import {
  BookingService,
  DayAvailability,
  DateDetail,
  SlotDetail,
  RoomType,
} from '../../services/booking';

interface CalendarDay {
  date: Date | null;
  day: number;
  status: 'available' | 'partial' | 'full' | 'closed' | 'past' | 'empty';
  isToday: boolean;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
})
export class CalendarComponent implements OnInit {
  currentDate = signal(new Date());
  selectedDate = signal<string | null>(null);
  selectedTime = signal<string | null>(null);
  selectedRoom = signal<RoomType | null>(null);
  dateDetail = signal<DateDetail | null>(null);
  loadingMonth = signal(false);
  loadingDetail = signal(false);
  errorMonth = signal(false);
  errorDetail = signal(false);

  private availabilityMap = signal<Map<string, DayAvailability>>(new Map());

  /** Holidays list (national + Catalonia) */
  private readonly HOLIDAYS = new Set([
    '2025-01-01','2025-01-06','2025-04-18','2025-04-21','2025-05-01',
    '2025-06-24','2025-08-15','2025-09-11','2025-10-12','2025-11-01',
    '2025-12-06','2025-12-08','2025-12-25','2025-12-26',
    '2026-01-01','2026-01-06','2026-04-03','2026-04-06','2026-05-01',
    '2026-06-24','2026-08-15','2026-09-11','2026-10-12','2026-11-01',
    '2026-12-06','2026-12-08','2026-12-25','2026-12-26',
  ]);

  availableTimes = computed(() => {
    const dateStr = this.selectedDate();
    if (!dateStr) return [];
    const date = new Date(dateStr + 'T00:00:00');
    const isWeekend = this.isWeekendOrHoliday(date);
    const startHour = isWeekend ? 11 : 17;
    const endHour = 21;
    return this.generateTimeSlots(startHour, endHour);
  });

  morningTimes = computed(() => {
    return this.availableTimes().filter(t => {
      const h = parseInt(t.split(':')[0], 10);
      return h < 13;
    });
  });

  afternoonTimes = computed(() => {
    return this.availableTimes().filter(t => {
      const h = parseInt(t.split(':')[0], 10);
      return h >= 13;
    });
  });

  formattedSelectedDate = computed(() => {
    const dStr = this.selectedDate();
    if (!dStr) return '';
    const d = new Date(dStr + 'T00:00:00');
    // We can use the current lang from TranslateService theoretically,
    // but default to es-ES for now. 
    return d.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  });

  showForm = computed(() => {
    return !!this.selectedDate() && !!this.selectedRoom() && !!this.selectedTime();
  });

  private readonly BLOCK_MINUTES = 180; // 3 hours
  private readonly ROOM_MAX: Record<string, number> = {
    SALA_PRIVADA: 1,
    ZONA_RESTAURACION: 2,
  };

  /** Set of blocked time slots based on 3-hour window rule */
  bookedTimesSet = computed(() => {
    const dateStr = this.selectedDate();
    const room = this.selectedRoom();
    if (!dateStr || !room) return new Set<string>();

    const avail = this.availabilityMap().get(dateStr);
    const bookedTimes = avail?.bookedTimesByRoom[room] ?? [];
    if (bookedTimes.length === 0) return new Set<string>();

    const bookedMinutes = bookedTimes.map((t) => this.timeToMinutes(t));
    const maxAllowed = this.ROOM_MAX[room] ?? 1;
    const blocked = new Set<string>();

    for (const slot of this.availableTimes()) {
      const slotMin = this.timeToMinutes(slot);
      const nearby = bookedMinutes.filter(
        (bm) => Math.abs(bm - slotMin) < this.BLOCK_MINUTES
      );
      if (nearby.length >= maxAllowed) {
        blocked.add(slot);
      }
    }
    return blocked;
  });

  currentYear = computed(() => this.currentDate().getFullYear());
  currentMonth = computed(() => this.currentDate().getMonth());

  calendarDays = computed(() => this.buildCalendarGrid());

  weekDayKeys = [
    'CALENDAR.MON',
    'CALENDAR.TUE',
    'CALENDAR.WED',
    'CALENDAR.THU',
    'CALENDAR.FRI',
    'CALENDAR.SAT',
    'CALENDAR.SUN',
  ];

  // Form fields
  parentName = signal('');
  email = signal('');
  phone = signal('');
  childrenNames = signal('');
  childrenCount = signal('');
  notes = signal('');

  // Submission state
  submitting = signal(false);
  submitSuccess = signal(false);
  submitError = signal<string | null>(null);

  constructor(private bookingService: BookingService) {}

  ngOnInit(): void {
    this.loadMonth();
  }

  get monthLabel(): string {
    const d = this.currentDate();
    return d.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  }

  get canGoPrev(): boolean {
    const now = new Date();
    const cur = this.currentDate();
    return (
      cur.getFullYear() > now.getFullYear() ||
      (cur.getFullYear() === now.getFullYear() &&
        cur.getMonth() > now.getMonth())
    );
  }

  prevMonth(): void {
    if (!this.canGoPrev) return;
    const d = new Date(this.currentDate());
    d.setMonth(d.getMonth() - 1);
    this.currentDate.set(d);
    this.selectedDate.set(null);
    this.selectedTime.set(null);
    this.selectedRoom.set(null);
    this.loadMonth();
  }

  nextMonth(): void {
    const d = new Date(this.currentDate());
    d.setMonth(d.getMonth() + 1);
    this.currentDate.set(d);
    this.selectedDate.set(null);
    this.selectedTime.set(null);
    this.selectedRoom.set(null);
    this.loadMonth();
  }

  selectDay(day: CalendarDay): void {
    if (!day.date || day.status === 'empty' || day.status === 'past' || day.status === 'closed') {
      return;
    }
    const dateStr = this.formatDate(day.date);
    this.selectedDate.set(dateStr);
    this.selectedTime.set(null);
    this.selectedRoom.set(null);
  }

  isDaySelected(day: CalendarDay): boolean {
    if (!day.date) return false;
    return this.formatDate(day.date) === this.selectedDate();
  }

  private loadMonth(): void {
    const yearMonth = this.formatYearMonth(this.currentDate());
    this.loadingMonth.set(true);
    this.errorMonth.set(false);

    this.bookingService.getMonthAvailability(yearMonth).subscribe({
      next: (data) => {
        const map = new Map<string, DayAvailability>();
        (data.days ?? []).forEach((d) => map.set(d.date, d));
        this.availabilityMap.set(map);
        this.loadingMonth.set(false);
      },
      error: () => {
        this.availabilityMap.set(new Map());
        this.loadingMonth.set(false);
        this.errorMonth.set(true);
      },
    });
  }

  private buildCalendarGrid(): CalendarDay[] {
    const year = this.currentYear();
    const month = this.currentMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Monday = 0, Sunday = 6
    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    const days: CalendarDay[] = [];

    // Empty cells before first day
    for (let i = 0; i < startDow; i++) {
      days.push({ date: null, day: 0, status: 'empty', isToday: false });
    }

    for (let d = 1; d <= lastDay.getDate(); d++) {
      const date = new Date(year, month, d);
      const dateStr = this.formatDate(date);
      const isToday =
        date.getFullYear() === today.getFullYear() &&
        date.getMonth() === today.getMonth() &&
        date.getDate() === today.getDate();
      const isPast = date < today && !isToday;

      let status: CalendarDay['status'];
      if (isPast) {
        status = 'past';
      } else {
        const avail = this.availabilityMap().get(dateStr);
        status = avail ? avail.status : 'available';
      }

      days.push({ date, day: d, status, isToday });
    }

    return days;
  }

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private formatYearMonth(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    return `${y}-${m}`;
  }

  getSlotPeriodKey(slot: SlotDetail): string {
    if (slot.period === 'morning') return 'CALENDAR.MORNING';
    if (slot.period === 'afternoon') return 'CALENDAR.AFTERNOON';
    return slot.period;
  }

  selectTime(time: string): void {
    if (this.isTimeBooked(time)) return;
    this.selectedTime.set(this.selectedTime() === time ? null : time);
  }

  isTimeSelected(time: string): boolean {
    return this.selectedTime() === time;
  }

  isTimeBooked(time: string): boolean {
    return this.bookedTimesSet().has(time);
  }

  selectRoom(room: RoomType): void {
    this.selectedRoom.set(this.selectedRoom() === room ? null : room);
    this.selectedTime.set(null);
  }

  isRoomSelected(room: RoomType): boolean {
    return this.selectedRoom() === room;
  }

  private timeToMinutes(time: string): number {
    const [h, m] = time.split(':').map(Number);
    return h * 60 + m;
  }

  private isWeekendOrHoliday(date: Date): boolean {
    const dow = date.getDay();
    const dateStr = this.formatDate(date);
    return dow === 0 || dow === 6 || this.HOLIDAYS.has(dateStr);
  }

  private generateTimeSlots(startHour: number, endHour: number): string[] {
    const slots: string[] = [];
    for (let h = startHour; h < endHour; h++) {
      for (let m = 0; m < 60; m += 15) {
        slots.push(
          `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
        );
      }
    }
    return slots;
  }

  submitBooking(): void {
    const date = this.selectedDate();
    const time = this.selectedTime();
    const room = this.selectedRoom();
    if (!date || !time || !room) return;

    this.submitting.set(true);
    this.submitError.set(null);
    this.submitSuccess.set(false);

    const reservationDateTime = `${date}T${time}:00`;

    this.bookingService.createBooking({
      parentName: this.parentName(),
      email: this.email(),
      phone: this.phone(),
      childrenNames: this.childrenNames(),
      childrenCount: this.childrenCount(),
      roomPreference: room,
      reservationDateTime,
      notes: this.notes() || undefined,
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitSuccess.set(true);
        this.resetForm();
        this.loadMonth();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = err?.error?.error || 'CALENDAR.SUBMIT_ERROR';
        this.submitError.set(msg);
      },
    });
  }

  private resetForm(): void {
    this.parentName.set('');
    this.email.set('');
    this.phone.set('');
    this.childrenNames.set('');
    this.childrenCount.set('');
    this.notes.set('');
    this.selectedTime.set(null);
    this.selectedRoom.set(null);
  }
}
