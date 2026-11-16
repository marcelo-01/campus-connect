import {
  DadosAnuncio,
  FiltroVitrine,
  anunciosPorExtenso,
  avaliacoesPorExtenso,
  enderecoDosAnuncios,
  formularioDoAnuncio,
  problemaNaFoto,
  rotuloDoTipoDeAnuncio,
} from './anuncio.service';

function filtro(campos: Partial<FiltroVitrine> = {}): FiltroVitrine {
  return {
    escopo: 'bolhas',
    bolhaId: null,
    tipo: null,
    categoria: null,
    busca: '',
    ordem: 'recentes',
    ...campos,
  };
}

const dados: DadosAnuncio = {
  tipo: 'SERVICO',
  categoriaId: 'c1',
  titulo: 'Site em React',
  descricao: 'Landing page',
  modalidadePreco: 'FIXO',
  preco: 120,
  bolhaIds: ['b1', 'b2'],
};

describe('funções do marketplace', () => {
  it('monta o endereço da vitrine só com os filtros que fogem do padrão', () => {
    const padrao = enderecoDosAnuncios({ tipo: 'vitrine', filtro: filtro() }, null);
    expect(padrao.url).toBe('/api/anuncios');
    expect(padrao.parametros.keys()).toEqual(['escopo']);
    expect(padrao.parametros.get('escopo')).toBe('bolhas');

    const filtrado = enderecoDosAnuncios(
      {
        tipo: 'vitrine',
        filtro: filtro({
          escopo: 'rede',
          bolhaId: 'b1',
          tipo: 'PRODUTO',
          categoria: 'eletronicos',
          busca: '  notebook ',
          ordem: 'preco-asc',
        }),
      },
      'c1',
    );
    expect(filtrado.parametros.get('escopo')).toBe('rede');
    expect(filtrado.parametros.get('bolhaId')).toBe('b1');
    expect(filtrado.parametros.get('tipo')).toBe('PRODUTO');
    expect(filtrado.parametros.get('categoria')).toBe('eletronicos');
    expect(filtrado.parametros.get('q')).toBe('notebook');
    expect(filtrado.parametros.get('ordem')).toBe('preco-asc');
    expect(filtrado.parametros.get('cursor')).toBe('c1');
  });

  it('busca os anúncios de uma pessoa sem os filtros da vitrine', () => {
    const perfil = enderecoDosAnuncios({ tipo: 'usuario', usuarioId: 'u1' }, 'c2');
    expect(perfil.url).toBe('/api/anuncios/usuario/u1');
    expect(perfil.parametros.keys()).toEqual(['cursor']);
  });

  it('manda os dados como JSON e cada foto na parte imagens', async () => {
    const foto = new File(['x'], 'foto.png', { type: 'image/png' });
    const formulario = formularioDoAnuncio(dados, [foto, foto]);

    const parte = formulario.get('dados') as Blob;
    expect(parte.type).toBe('application/json');
    expect(JSON.parse(await parte.text())).toEqual(dados);
    expect(formulario.getAll('imagens')).toHaveLength(2);
  });

  it('na edição, diz quais fotos atuais ficam', async () => {
    const formulario = formularioDoAnuncio(dados, [], ['/api/arquivos/a.jpg']);

    const json = JSON.parse(await (formulario.get('dados') as Blob).text());
    expect(json.imagensMantidas).toEqual(['/api/arquivos/a.jpg']);
    expect(formulario.has('imagens')).toBe(false);
  });

  it('confere as fotos antes de anexar', () => {
    expect(problemaNaFoto(new File(['x'], 'foto.png', { type: 'image/png' }))).toBeNull();
    expect(problemaNaFoto(new File(['x'], 'foto.jpg', { type: 'image/jpeg' }))).toBeNull();
    expect(problemaNaFoto(new File(['x'], 'foto.gif', { type: 'image/gif' }))).toBe('Escolha fotos JPG ou PNG.');
    const grande = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.png', { type: 'image/png' });
    expect(problemaNaFoto(grande)).toBe('grande.png passa de 5 MB.');
  });

  it('escreve tipos e contagens por extenso', () => {
    expect(rotuloDoTipoDeAnuncio('SERVICO')).toBe('Serviço');
    expect(rotuloDoTipoDeAnuncio('PRODUTO')).toBe('Produto');
    expect(anunciosPorExtenso(1)).toBe('1 anúncio');
    expect(anunciosPorExtenso(12)).toBe('12 anúncios');
    expect(avaliacoesPorExtenso(1)).toBe('1 avaliação');
    expect(avaliacoesPorExtenso(38)).toBe('38 avaliações');
  });
});
