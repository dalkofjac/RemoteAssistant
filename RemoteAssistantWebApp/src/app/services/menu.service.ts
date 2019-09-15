import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class MenuService {

  private showHeader = new Subject<boolean>();
  menuShowHeader = this.showHeader.asObservable();

  constructor() { }

  showHeaderArea(doShow: boolean) {
    this.showHeader.next(doShow);
  }
}
