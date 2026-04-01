import { Component, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ContactService, ContactRequest } from '../../services/contact';

@Component({
  selector: 'app-contact',
  imports: [FormsModule, TranslateModule],
  templateUrl: './contact.html',
  styleUrl: './contact.scss'
})
export class Contact {
  @ViewChild('contactForm') contactForm!: NgForm;

  formData: ContactRequest = { name: '', email: '', message: '' };
  submitted = false;
  error = false;

  constructor(private contactService: ContactService) {}

  onSubmit(): void {
    this.contactService.sendMessage(this.formData).subscribe({
      next: () => {
        this.submitted = true;
        this.error = false;
        if (this.contactForm) {
          this.contactForm.resetForm();
        }
        this.formData = { name: '', email: '', message: '' };
        
        // Hide success message after 5 seconds
        setTimeout(() => {
          this.submitted = false;
        }, 5000);
      },
      error: () => {
        this.error = true;
      }
    });
  }
}
