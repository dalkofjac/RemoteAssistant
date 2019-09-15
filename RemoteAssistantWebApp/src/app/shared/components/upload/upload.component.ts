import {
  Component, OnInit, OnDestroy, Input, ElementRef, HostBinding, HostListener, Renderer2,
  Optional, Self, ViewChild, forwardRef, EventEmitter, Output, ChangeDetectorRef
} from '@angular/core';
import { NgControl, ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { MatFormFieldControl, MatIconRegistry } from '@angular/material';
import { FocusMonitor } from '@angular/cdk/a11y';
import { coerceBooleanProperty } from '@angular/cdk/coercion';

import { Subject } from 'rxjs';
import { DomSanitizer } from '@angular/platform-browser';


@Component({
  selector: 'app-upload',
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.sass'],
  providers: [
    { provide: MatFormFieldControl, useExisting: UploadComponent }
  ]
})
export class UploadComponent implements OnInit, OnDestroy, ControlValueAccessor, MatFormFieldControl<File> {

  static nextId = 0;

  files: File[];
  error: string;
  result = '';
  focused = false;
  dragged = false;
  percentage = -1;
  controlType = 'file-input';
  stateChanges = new Subject<void>();
  @Output() change = new EventEmitter<File>();
  @Output() multiple = new EventEmitter<File[]>();
  @HostBinding() id = `adm-upload-${UploadComponent.nextId++}`;
  @HostBinding('attr.aria-describedby') describedBy = '';
  @ViewChild('input', null) input: ElementRef;
  @Input() accept: string;
  @Input() width: number = null;
  @Input() height: number = null;
  @Input() maxWidth: number = null;
  @Input() maxHeight: number = null;
  @Input() minWidth: number = null;
  @Input() minHeight: number = null;
  @Input() message: string;

  private variables = { placeholder: '', error: null, required: false, disabled: false, maxsize: -1, shouldLabelFloat: false };
  private events = { changed: e => { }, touched: () => { } };


  @Input() get value(): File | null {
    return this.empty ? null : <File>this.element.nativeElement.value;
  }

  set value(file: File | null) {
    this.writeValue(file ? file.name : null);
    this.stateChanges.next();
    if (this.files && this.files.length <= 1) {
      this.change.emit(file);
      this.multiple.emit(this.files);
    } else {
      this.multiple.emit(this.files);
    }
  }

  @Input() get placeholder() {
    return this.variables.placeholder;
  }

  set placeholder(value: string) {
    this.variables.placeholder = value;
    this.stateChanges.next();
  }

  @Input() get required() {
    return this.variables.required;
  }

  set required(value: boolean) {
    this.variables.required = coerceBooleanProperty(value);
    this.stateChanges.next();
  }

  @HostBinding('class.disabled') @Input() get disabled() {
    return this.variables.disabled;
  }

  set disabled(value: boolean) {
    this.variables.disabled = coerceBooleanProperty(value);
    this.renderer.setProperty(this.element.nativeElement, 'disabled', this.variables.disabled);
    this.stateChanges.next();
  }

  @Input() get shouldLabelFloat() {
    return this.variables.shouldLabelFloat;
  }

  set shouldLabelFloat(value: boolean) {
    this.variables.shouldLabelFloat = coerceBooleanProperty(value);
    this.stateChanges.next();
  }

  @Input() get errorState(): boolean {
    return !!this.variables.error || (this.required && (this.empty || this.uploading));
  }

  get empty(): boolean {
    return !this.element.nativeElement.value || this.element.nativeElement.value.length === 0;
  }

  @HostBinding('class.mat-form-field-should-float') get shouldPlaceholderFloat() {
    return this.focused || !this.empty;
  }

  @Input() set progress(perc: number | null) {
    if (!isNaN(perc)) {
      this.percentage = perc;
      this.stateChanges.next();
    }
  }

  @HostBinding('class.uploading') get uploading(): boolean {
    return this.percentage >= 0 && this.percentage < 100;
  }

  @Output() get errorMessage(): string {
    return this.variables.error;
  }

  set errorMessage(value: string) {
    this.variables.error = value;
    this.stateChanges.next();
  }

  @Input() get maxSize(): string {
    if (this.variables.maxsize !== -1) {
      const s = ['', 'KB', 'MB', 'GB'];
      const k = 1024;
      const i = Math.floor(Math.log(this.variables.maxsize) / Math.log(k));

      return (this.variables.maxsize / Math.pow(k, i)) + s[i];
    }

    return '';
  }

  set maxSize(value: string) {
    if (value) {
      const s = ['', 'KB', 'MB', 'GB'];
      const r = /(\w{2})$/.exec(value) && RegExp.$1;
      const n = parseInt(value.replace(r, ''), 10);

      this.variables.maxsize = n * (s.indexOf((r || '').toUpperCase()) * 1024);
    } else {
      this.variables.maxsize = -1;
    }

    this.stateChanges.next();
  }


  constructor(
    @Optional() @Self() public ngControl: NgControl,
    private detector: ChangeDetectorRef,
    private element: ElementRef,
    private monitor: FocusMonitor,
    private renderer: Renderer2,
    private matIconRegistry: MatIconRegistry,
    private domSanitizer: DomSanitizer
  ) {
    this.matIconRegistry.addSvgIcon('upload', this.domSanitizer.bypassSecurityTrustResourceUrl('../assets/svgs/upload_icon.svg'));

    if (this.ngControl !== null) {
      this.ngControl.valueAccessor = this;
    }

    this.monitor.monitor(this.element.nativeElement, true).subscribe(origin => {
      this.focused = !!origin;
      this.stateChanges.next();
    });
  }


  ngOnInit() {
  }

  ngOnDestroy() {
    this.monitor.stopMonitoring(this.element.nativeElement);
    this.stateChanges.complete();
  }


  onChange(event) {
    const files = event.target.files;

    this.changed(files);
    this.prevent(event);
  }

  onClear() {
    this.changed(null, true);
    this.detector.detectChanges();
  }


  @HostListener('focusout')
  onBlur() {
    this.focused = false;
    this.events.touched();
  }

  @HostListener('dragover', ['$event'])
  onDragOver(event) {
    if (!this.uploading && !this.variables.disabled) {
      this.dragged = true;
      this.prevent(event);
    }
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave(event) {
    if (!this.uploading && !this.variables.disabled) {
      this.dragged = false;
      this.prevent(event);
    }
  }

  @HostListener('drop', ['$event'])
  onDrop(event) {
    if (!this.uploading && !this.variables.disabled) {
      const files = event.dataTransfer.files;

      this.dragged = false;

      this.changed(files, true);
      this.prevent(event);
    }
  }


  setDescribedByIds(ids: string[]) {
    this.describedBy = ids.join(' ');
  }

  onContainerClick(event: MouseEvent) {
    const tag = (event.target as Element).tagName.toLowerCase();

    if (!this.uploading && !this.variables.disabled && ['input', 'button', 'mat-icon'].indexOf(tag) === -1) {
      const input = this.input.nativeElement;

      this.focused = true;

      input.focus();
      input.click();
    }
  }

  writeValue(name: string) {
    this.result = name || '';
    this.renderer.setProperty(this.element.nativeElement, 'value', name || null);
  }

  registerOnChange(fn: (v) => void) {
    this.events.changed = fn;
  }

  registerOnTouched(fn: () => void) {
    this.events.touched = fn;
  }

  setDisabledState?(isDisabled: boolean) {
    this.variables.disabled = isDisabled;
    this.renderer.setProperty(this.element.nativeElement, 'disabled', isDisabled);
  }


  private changed(list: FileList, resetInput: boolean = false) {
    let file: File;

    if (list && list.length > 0) {
      [file] = Array.from<File>(list);

      this.files = Array.from<File>(list);
      if (this.files.length > 1) {
        this.files.forEach(_ => {
          const extension = /\.([a-z0-9]+)$/i.exec(_.name) && RegExp.$1;
          if (!(!this.accept || this.accept.split(',').indexOf(`.${extension}`) !== -1)) {
            this.errorMessage = `The file extension ${extension} is not valid!`;
          }
        });
      }
    }

    if (file) {
      const extension = /\.([a-z0-9]+)$/i.exec(file.name) && RegExp.$1;

      if (!this.accept || this.accept.split(',').indexOf(`.${extension}`) !== -1) {
        if (this.variables.maxsize === -1 || file.size <= this.variables.maxsize) {
          // check image size if it is specified - jquery way
          if (this.width || this.height || this.minWidth || this.minHeight || this.maxHeight || this.maxWidth) {
            const img = new Image();
            const self = this;
            const URL = window.URL;

            img.onload = function () {
              if (self.height && img.height !== self.height) {
                self.errorMessage = `Not a valid height: ${img.height}`;
              } else if (self.width && img.width !== self.width) {
                self.errorMessage = `Not a valid width: ${img.width}`;
              } else if (self.maxHeight && img.width > self.maxHeight) {
                self.errorMessage = `Not a valid height: ${img.height}`;
              } else if (self.maxWidth && img.width > self.maxWidth) {
                self.errorMessage = `Not a valid width: ${img.width}`;
              } else if (self.minHeight && img.width < self.minHeight) {
                self.errorMessage = `Not a valid height: ${img.height}`;
              } else if (self.minWidth && img.width < self.minWidth) {
                self.errorMessage = `Not a valid width: ${img.width}`;
              } else {
                self.value = file;
                self.errorMessage = null;
                self.events.changed(file.name);
              }
            };

            img.onerror = function () {
              self.errorMessage = `Not a valid file: ${file.type}`;
            };

            img.src = URL.createObjectURL(file);
          } else {
            this.value = file;
            this.errorMessage = null;
            this.events.changed(file.name);
          }
        } else {
          this.errorMessage = `The file is bigger than ${this.maxSize}`;
        }
      } else {
        this.errorMessage = `Chosen file extension is not valid!`;
      }

      if (resetInput) {
        this.percentage = -1;
        this.renderer.setProperty(this.input.nativeElement, 'value', null);
      } else {
        this.percentage = -1;
      }
    } else if (resetInput) {
      this.percentage = -1;
      this.renderer.setProperty(this.input.nativeElement, 'value', null);
      this.writeValue(null);
    }
  }

  private prevent(event: Event) {
    event.preventDefault();
    event.stopPropagation();
  }

}
