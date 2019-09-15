import { Component, OnInit } from '@angular/core';
import { MenuService } from './services/menu.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  showHeader: boolean;

  constructor(
    private menuService: MenuService
    ) {
  }

  ngOnInit() {
    this.showHeader = false;
    this.menuService.menuShowHeader.subscribe(showHeader => {
      setTimeout(() => {
        this.showHeader = showHeader;
      }, 0);
    });
  }
}
