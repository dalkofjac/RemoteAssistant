import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MenuService } from 'src/app/services/menu.service';
import { SendAudioVariables } from 'src/app/shared/enums/send-audio-variables';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  chosenRoomName: string;
  sendAudio: boolean = false;

  constructor(
    private router: Router,
    private menuService: MenuService
  ) { }

  ngOnInit() {
    this.menuService.showHeaderArea(false);
  }

  startConferenceCall() {
    sessionStorage.setItem(
      SendAudioVariables.AllowAudio, this.sendAudio ? SendAudioVariables.Allow : SendAudioVariables.DontAllow
    );

    this.router.navigate(['conference-call/' + this.chosenRoomName]);
  }

}

