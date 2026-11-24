import { abaDaUrl, enderecoDaBusca, parametrosDaBusca } from './busca.service';
import { ehMac } from './busca-topo';

describe('funções da busca', () => {
  it('manda o termo sem espaços nas pontas, e o cursor e o limite só quando vêm', () => {
    const primeira = parametrosDaBusca('  ana souza ', null, null);
    expect(primeira.keys()).toEqual(['q']);
    expect(primeira.get('q')).toBe('ana souza');

    const seguinte = parametrosDaBusca('ana', '20', 5);
    expect(seguinte.get('cursor')).toBe('20');
    expect(seguinte.get('limite')).toBe('5');
  });

  it('monta o endereço da página de busca, sem a aba Posts e sem termo vazio', () => {
    expect(enderecoDaBusca(' bolsa ')).toEqual({ caminho: ['/busca'], queryParams: { q: 'bolsa', aba: null } });
    expect(enderecoDaBusca('ana', 'pessoas').queryParams).toEqual({ q: 'ana', aba: 'pessoas' });
    expect(enderecoDaBusca('   ', 'bolhas').queryParams).toEqual({ q: null, aba: 'bolhas' });
  });

  it('lê a aba da URL e cai em Posts quando ela falta ou não existe', () => {
    expect(abaDaUrl('pessoas')).toBe('pessoas');
    expect(abaDaUrl('bolhas')).toBe('bolhas');
    expect(abaDaUrl(null)).toBe('posts');
    expect(abaDaUrl('anuncios')).toBe('posts');
  });

  it('mostra o atalho como ⌘K só nos aparelhos da Apple', () => {
    expect(ehMac({ platform: 'MacIntel', userAgent: '' } as Navigator)).toBe(true);
    expect(ehMac({ platform: '', userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)' } as Navigator)).toBe(
      true,
    );
    expect(ehMac({ platform: 'Win32', userAgent: 'Mozilla/5.0 (Windows NT 10.0)' } as Navigator)).toBe(false);
    expect(ehMac(undefined)).toBe(false);
  });
});
