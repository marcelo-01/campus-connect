import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './shared/toast.service';
import { Visualizador } from './shared/visualizador/visualizador';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Visualizador],
  templateUrl: './app.html',
})
export class App {
  protected readonly toast = inject(ToastService);
}
