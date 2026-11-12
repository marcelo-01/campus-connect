import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Um pedaço do texto de um post ou comentário. */
export type Trecho =
  | { tipo: 'texto'; texto: string }
  | { tipo: 'link'; texto: string; url: string }
  | { tipo: 'mencao'; texto: string; username: string }
  | { tipo: 'hashtag'; texto: string };

/**
 * Endereços (com `http(s)://` ou começando por `www.`), `@menções` no formato de username do cadastro
 * (`[a-z0-9._]{3,30}`) e `#hashtags`. Menção e hashtag só contam no começo de uma palavra, então
 * `ana@exemplo.com` e `a#b` ficam como texto.
 */
const PADRAO =
  /(?<url>\bhttps?:\/\/[^\s<>"]+|\bwww\.[^\s<>"]+)|(?<![\p{L}\p{N}_@.])@(?<usuario>[a-z0-9._]{3,30})|(?<![\p{L}\p{N}_#&])#(?<tag>[\p{L}\p{N}_]+)/giu;

/** Pontuação colada no fim de um endereço costuma ser da frase, não do endereço. */
const PONTUACAO_FINAL = '.,;:!?\'"';

/**
 * Divide o texto em trechos para o template desenhar cada um com segurança, sem `innerHTML`: o texto da
 * pessoa nunca vira HTML.
 */
export function trechos(texto: string): Trecho[] {
  const resultado: Trecho[] = [];
  const somarTexto = (pedaco: string) => {
    if (!pedaco) {
      return;
    }
    const ultimo = resultado.at(-1);
    if (ultimo?.tipo === 'texto') {
      ultimo.texto += pedaco;
    } else {
      resultado.push({ tipo: 'texto', texto: pedaco });
    }
  };

  let inicio = 0;
  for (const achado of texto.matchAll(PADRAO)) {
    const posicao = achado.index ?? 0;
    somarTexto(texto.slice(inicio, posicao));
    const { url, usuario, tag } = achado.groups ?? {};
    if (url) {
      const limpo = semPontuacaoFinal(url);
      resultado.push({
        tipo: 'link',
        texto: limpo,
        url: /^https?:\/\//i.test(limpo) ? limpo : `https://${limpo}`,
      });
      somarTexto(url.slice(limpo.length));
    } else if (usuario) {
      // "@bia." no fim da frase: o ponto é da frase.
      const nome = usuario.replace(/\.+$/, '');
      if (nome.length >= 3) {
        resultado.push({ tipo: 'mencao', texto: `@${nome}`, username: nome.toLowerCase() });
        somarTexto(usuario.slice(nome.length));
      } else {
        somarTexto(achado[0]);
      }
    } else if (tag) {
      resultado.push({ tipo: 'hashtag', texto: `#${tag}` });
    }
    inicio = posicao + achado[0].length;
  }
  somarTexto(texto.slice(inicio));
  return resultado;
}

function semPontuacaoFinal(url: string): string {
  let fim = url.length;
  while (fim > 0) {
    const ultimo = url[fim - 1];
    const parenteseSobrando =
      ultimo === ')' && contar(url.slice(0, fim), '(') < contar(url.slice(0, fim), ')');
    if (!PONTUACAO_FINAL.includes(ultimo) && !parenteseSobrando) {
      break;
    }
    fim--;
  }
  return url.slice(0, fim);
}

function contar(texto: string, caractere: string): number {
  return texto.split(caractere).length - 1;
}

/**
 * O texto de um post ou comentário, com links, menções (que levam ao perfil) e hashtags. Os cliques nos
 * links não abrem a publicação que está em volta. O template fica numa linha só: o `.post-body` respeita
 * as quebras de linha do texto (`white-space: pre-wrap`), e qualquer espaço a mais apareceria.
 */
@Component({
  selector: 'app-texto-publicacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styles: ':host { display: contents; }',
  template: `@for (trecho of partes(); track $index) {@switch (trecho.tipo) {@case ('link') {<a [href]="$any(trecho).url" target="_blank" rel="noopener noreferrer nofollow" (click)="$event.stopPropagation()">{{ trecho.texto }}</a>}@case ('mencao') {<a class="mention" [routerLink]="['/perfil', $any(trecho).username]" (click)="$event.stopPropagation()">{{ trecho.texto }}</a>}@case ('hashtag') {<span class="hashtag">{{ trecho.texto }}</span>}@default {<ng-container>{{ trecho.texto }}</ng-container>}}}`,
})
export class TextoPublicacao {
  readonly texto = input.required<string>();

  protected readonly partes = computed(() => trechos(this.texto()));
}
