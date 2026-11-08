import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { ToastService } from './shared/toast.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
  });

  it('mostra o aviso do ToastService com a classe .toast do MVP', async () => {
    const fixture = TestBed.createComponent(App);
    TestBed.inject(ToastService).mostrar('Entrar com Google — em breve');
    await fixture.whenStable();

    const toast = (fixture.nativeElement as HTMLElement).querySelector('.toast');
    expect(toast?.textContent).toContain('Entrar com Google — em breve');
  });
});
