import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type NomeIcone =
  | 'inicio'
  | 'bolhas'
  | 'marketplace'
  | 'mensagens'
  | 'notificacoes'
  | 'salvos'
  | 'perfil'
  | 'busca'
  | 'mais'
  | 'editar'
  | 'imagem'
  | 'link'
  | 'voltar'
  | 'sair'
  | 'globo'
  | 'local'
  | 'lixeira'
  | 'check'
  | 'conexoes'
  | 'coracao'
  | 'coracao-cheio'
  | 'comentario'
  | 'republicar'
  | 'compartilhar'
  | 'salvo'
  | 'opcoes'
  | 'video'
  | 'fechar'
  | 'subir';

/**
 * Ícones do `components.jsx` do MVP (objeto `Icon`), com os mesmos traços. `voltar` vem do `BackBar` do
 * MVP; `globo` é o `Icon.Globe` do chip "Toda a rede"; `coracao`, `coracao-cheio`, `comentario`,
 * `republicar`, `compartilhar` e `opcoes` são os do card do post (`Heart`, `HeartFill`, `Comment`, `Repost`,
 * `Share`, `More`). `sair`, `local`, `lixeira`, `check`, `conexoes` (o `Icon.User` do MVP com uma segunda
 * pessoa atrás), `salvo` (o marcador preenchido), `video`, `fechar` e `subir` (a seta do aviso de posts
 * novos) não existiam lá e seguem o mesmo estilo. O elemento do componente não cria caixa
 * (`display: contents`), então o CSS do MVP que dimensiona o `<svg>` continua valendo.
 */
@Component({
  selector: 'app-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    @switch (nome()) {
      @case ('inicio') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5L12 3l9 7.5"/><path d="M5 9.5V20h14V9.5"/></svg>
      }
      @case ('bolhas') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="10" r="6"/><circle cx="17" cy="16" r="4"/></svg>
      }
      @case ('marketplace') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7h18l-1.5 11a2 2 0 0 1-2 1.7H6.5a2 2 0 0 1-2-1.7L3 7z"/><path d="M8 7V5a4 4 0 0 1 8 0v2"/></svg>
      }
      @case ('mensagens') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.5 8.5 0 0 1-12.6 7.4L3 20.5l1.6-5.4A8.5 8.5 0 1 1 21 11.5z"/></svg>
      }
      @case ('notificacoes') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10 21a2 2 0 0 0 4 0"/></svg>
      }
      @case ('salvos') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12v18l-6-4-6 4z"/></svg>
      }
      @case ('perfil') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-7 8-7s8 3 8 7"/></svg>
      }
      @case ('busca') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/></svg>
      }
      @case ('mais') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
      }
      @case ('editar') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>
      }
      @case ('imagem') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2.5"/><circle cx="9" cy="9" r="2"/><path d="M21 16l-5-5L4 21"/></svg>
      }
      @case ('link') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/></svg>
      }
      @case ('voltar') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
      }
      @case ('sair') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>
      }
      @case ('globo') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/></svg>
      }
      @case ('local') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>
      }
      @case ('lixeira') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16"/><path d="M10 11v6M14 11v6"/><path d="M6 7l1 13h10l1-13"/><path d="M9 7V4h6v3"/></svg>
      }
      @case ('check') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7"/></svg>
      }
      @case ('conexoes') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/><path d="M15.5 4.7a3.5 3.5 0 0 1 0 6.6"/><path d="M17.5 14.4c2.3.7 4 2.7 4 5.6"/></svg>
      }
      @case ('coracao') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20s-7-4.5-9.3-9A5 5 0 0 1 12 6a5 5 0 0 1 9.3 5C19 15.5 12 20 12 20z"/></svg>
      }
      @case ('coracao-cheio') {
        <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 20s-7-4.5-9.3-9A5 5 0 0 1 12 6a5 5 0 0 1 9.3 5C19 15.5 12 20 12 20z"/></svg>
      }
      @case ('comentario') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.8 7L4 20.5 5.5 16A8 8 0 1 1 21 12z"/></svg>
      }
      @case ('republicar') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
      }
      @case ('compartilhar') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><path d="M16 6l-4-4-4 4"/><path d="M12 2v13"/></svg>
      }
      @case ('salvo') {
        <svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12v18l-6-4-6 4z"/></svg>
      }
      @case ('opcoes') {
        <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="19" cy="12" r="1.7"/></svg>
      }
      @case ('video') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6" width="13" height="12" rx="2.5"/><path d="M16 10.5l5-3v9l-5-3"/></svg>
      }
      @case ('fechar') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
      }
      @case ('subir') {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"/><path d="M5 12l7-7 7 7"/></svg>
      }
    }
  `,
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
}
