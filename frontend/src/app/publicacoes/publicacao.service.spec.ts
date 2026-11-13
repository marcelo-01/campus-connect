import {
  comentariosPorExtenso,
  dominio,
  enderecoDasNovas,
  enderecoDoFeed,
  formularioDaPublicacao,
  problemaNaImagemDoPost,
  problemaNoVideo,
  rotuloDasNovas,
} from './publicacao.service';

describe('funções das publicações', () => {
  it('monta o endereço de cada feed, com filtro, ordem e cursor só quando precisam', () => {
    const inicio = enderecoDoFeed({ tipo: 'inicio', bolhaId: null }, 'recentes', null);
    expect(inicio.url).toBe('/api/publicacoes/feed');
    expect(inicio.parametros.keys()).toEqual([]);

    const filtrado = enderecoDoFeed({ tipo: 'inicio', bolhaId: 'b1' }, 'alta', 'c1');
    expect(filtrado.parametros.get('bolhaId')).toBe('b1');
    expect(filtrado.parametros.get('ordem')).toBe('alta');
    expect(filtrado.parametros.get('cursor')).toBe('c1');

    expect(enderecoDoFeed({ tipo: 'bolha', bolhaId: 'b2' }, 'midia', null).url).toBe(
      '/api/publicacoes/bolha/b2',
    );
    const perfil = enderecoDoFeed({ tipo: 'usuario', usuarioId: 'u1' }, 'alta', null);
    expect(perfil.url).toBe('/api/publicacoes/usuario/u1');
    expect(perfil.parametros.has('ordem')).toBe(false);
    expect(enderecoDoFeed({ tipo: 'salvas' }, 'recentes', null).url).toBe('/api/publicacoes/salvas');
  });

  it('pergunta pelos posts novos ao lado do feed, com o mesmo filtro e o mesmo chip', () => {
    const inicio = enderecoDasNovas({ tipo: 'inicio', bolhaId: 'b1' }, 'midia', 'm1');
    expect(inicio.url).toBe('/api/publicacoes/feed/novas');
    expect(inicio.parametros.get('bolhaId')).toBe('b1');
    expect(inicio.parametros.get('ordem')).toBe('midia');
    expect(inicio.parametros.get('desde')).toBe('m1');

    const bolha = enderecoDasNovas({ tipo: 'bolha', bolhaId: 'b2' }, 'recentes', 'm2');
    expect(bolha.url).toBe('/api/publicacoes/bolha/b2/novas');
    expect(bolha.parametros.keys()).toEqual(['desde']);
  });

  it('escreve o aviso de posts novos, com "20+" quando passa do limite', () => {
    const aviso = (total: number, mais = false) => rotuloDasNovas({ total, mais, autores: [] });
    expect(aviso(1)).toBe('1 nova publicação');
    expect(aviso(3)).toBe('3 novas publicações');
    expect(aviso(20, true)).toBe('20+ novas publicações');
  });

  it('manda os dados como JSON e cada arquivo na sua parte', async () => {
    const foto = new File(['x'], 'foto.png', { type: 'image/png' });
    const formulario = formularioDaPublicacao({
      bolhaId: 'b1',
      conteudo: 'Oi',
      link: '',
      imagens: [foto, foto],
      video: null,
    });

    const dados = formulario.get('dados') as Blob;
    expect(dados.type).toBe('application/json');
    expect(JSON.parse(await dados.text())).toEqual({ bolhaId: 'b1', conteudo: 'Oi', link: null });
    expect(formulario.getAll('imagens')).toHaveLength(2);
    expect(formulario.has('video')).toBe(false);
  });

  it('confere imagens e vídeos antes de anexar', () => {
    const png = new File(['x'], 'foto.png', { type: 'image/png' });
    const gif = new File(['x'], 'foto.gif', { type: 'image/gif' });
    const mp4 = new File(['x'], 'aula.mp4', { type: 'video/mp4' });
    const mov = new File(['x'], 'aula.mov', { type: 'video/quicktime' });

    expect(problemaNaImagemDoPost(png)).toBeNull();
    expect(problemaNaImagemDoPost(gif)).toBe('Escolha imagens JPG ou PNG.');
    expect(problemaNoVideo(mp4)).toBeNull();
    expect(problemaNoVideo(mov)).toBe('Escolha um vídeo MP4 ou WebM.');
  });

  it('mostra só o domínio de um link', () => {
    expect(dominio('https://www.ifba.edu.br/eventos?x=1')).toBe('ifba.edu.br');
    expect(dominio('github.com/ana')).toBe('github.com');
  });

  it('escreve a contagem de comentários por extenso', () => {
    expect(comentariosPorExtenso(1)).toBe('1 comentário');
    expect(comentariosPorExtenso(0)).toBe('0 comentários');
  });
});
