package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.campusconnect.bolha.model.Bolha;
import br.com.campusconnect.bolha.model.MembroBolha;
import br.com.campusconnect.bolha.model.MembroBolhaId;
import br.com.campusconnect.bolha.model.PapelMembro;
import br.com.campusconnect.bolha.model.TipoBolha;
import br.com.campusconnect.conexao.model.Conexao;
import br.com.campusconnect.conexao.model.StatusConexao;
import br.com.campusconnect.conversa.model.Conversa;
import br.com.campusconnect.conversa.model.Mensagem;
import br.com.campusconnect.conversa.model.ParticipanteConversa;
import br.com.campusconnect.conversa.model.ParticipanteConversaId;
import br.com.campusconnect.conversa.model.TipoConversa;
import br.com.campusconnect.instituicao.model.Campus;
import br.com.campusconnect.instituicao.model.Curso;
import br.com.campusconnect.instituicao.model.GrauCurso;
import br.com.campusconnect.instituicao.model.Instituicao;
import br.com.campusconnect.marketplace.model.Anuncio;
import br.com.campusconnect.marketplace.model.AnuncioBolha;
import br.com.campusconnect.marketplace.model.AnuncioBolhaId;
import br.com.campusconnect.marketplace.model.AvaliacaoAnuncio;
import br.com.campusconnect.marketplace.model.CategoriaAnuncio;
import br.com.campusconnect.marketplace.model.FavoritoAnuncio;
import br.com.campusconnect.marketplace.model.FavoritoAnuncioId;
import br.com.campusconnect.marketplace.model.ImagemAnuncio;
import br.com.campusconnect.marketplace.model.Impulsionamento;
import br.com.campusconnect.marketplace.model.ModalidadePreco;
import br.com.campusconnect.marketplace.model.StatusAnuncio;
import br.com.campusconnect.marketplace.model.StatusImpulsionamento;
import br.com.campusconnect.marketplace.model.TipoAnuncio;
import br.com.campusconnect.moderacao.model.Denuncia;
import br.com.campusconnect.moderacao.model.MotivoDenuncia;
import br.com.campusconnect.moderacao.model.StatusDenuncia;
import br.com.campusconnect.notificacao.model.Notificacao;
import br.com.campusconnect.notificacao.model.TipoNotificacao;
import br.com.campusconnect.publicacao.model.Comentario;
import br.com.campusconnect.publicacao.model.MidiaPublicacao;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.Reacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import br.com.campusconnect.publicacao.model.TipoMidia;
import br.com.campusconnect.publicacao.model.TipoReacao;
import br.com.campusconnect.usuario.model.RefreshToken;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.TipoVinculo;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.model.VinculoInstitucional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MapeamentoEntidadesTest {

    // Em signed long, f000... é negativo e vem antes de 0000...; em bytes, vem depois.
    private static final UUID ID_MENOR_NO_POSTGRES = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ID_MAIOR_NO_POSTGRES = UUID.fromString("f0000000-0000-4000-8000-000000000002");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void gravaELeUmaInstanciaDeCadaTabela() {
        Instituicao ifba = new Instituicao("Instituto Federal da Bahia");
        ifba.setSigla("IFBA");
        em.persist(ifba);

        Campus eunapolis = new Campus(ifba, "Eunápolis");
        eunapolis.setCidade("Eunápolis");
        eunapolis.setUf("BA");
        em.persist(eunapolis);

        Curso ads = new Curso(eunapolis, "Análise e Desenvolvimento de Sistemas");
        ads.setGrau(GrauCurso.TECNOLOGO);
        em.persist(ads);

        Usuario ana = usuario("ana");
        Usuario joao = usuario("joao");

        VinculoInstitucional vinculo = new VinculoInstitucional(ana, ifba.getId(), TipoVinculo.ALUNO);
        vinculo.setCampusId(eunapolis.getId());
        vinculo.setCursoId(ads.getId());
        vinculo.setMatricula("2026001");
        em.persist(vinculo);

        RefreshToken refreshToken = em.persist(new RefreshToken(ana, UUID.randomUUID(), "a".repeat(64), true,
                Instant.parse("2026-12-01T03:00:00Z")));

        Conexao conexao = em.persist(Conexao.solicitar(ana.getId(), joao.getId()));

        Notificacao notificacao = new Notificacao(joao.getId(), TipoNotificacao.SOLICITACAO_CONEXAO);
        notificacao.setOrigemId(ana.getId());
        notificacao.setReferenciaId(conexao.getId());
        em.persist(notificacao);

        Bolha bolhaCampus = new Bolha(TipoBolha.CAMPUS, "Campus Eunápolis", "ifba-eunapolis");
        bolhaCampus.setInstituicaoId(ifba.getId());
        bolhaCampus.setCampusId(eunapolis.getId());
        em.persist(bolhaCampus);

        Bolha bolhaCurso = new Bolha(TipoBolha.CURSO, "ADS", "ifba-eunapolis-ads");
        bolhaCurso.setBolhaPai(bolhaCampus);
        bolhaCurso.setInstituicaoId(ifba.getId());
        bolhaCurso.setCampusId(eunapolis.getId());
        bolhaCurso.setCursoId(ads.getId());
        bolhaCurso.setEntradaAutomatica(true);
        em.persist(bolhaCurso);

        em.persist(new MembroBolha(bolhaCurso, ana.getId(), PapelMembro.MODERADOR));

        CategoriaAnuncio servicos = em.persist(new CategoriaAnuncio("Serviços", "servicos"));
        CategoriaAnuncio aulas = new CategoriaAnuncio("Aulas", "aulas");
        aulas.setCategoriaPai(servicos);
        em.persist(aulas);

        Anuncio anuncio = new Anuncio(ana.getId(), aulas, TipoAnuncio.SERVICO, "Aulas de Java",
                StatusAnuncio.PUBLICADO);
        anuncio.setDescricao("Monitoria de programação");
        anuncio.setPreco(new BigDecimal("49.90"));
        anuncio.setModalidadePreco(ModalidadePreco.POR_HORA);
        anuncio.adicionarImagem("https://exemplo.com/1.png");
        anuncio.adicionarImagem("https://exemplo.com/2.png");
        em.persist(anuncio);

        AvaliacaoAnuncio avaliacao = em.persist(new AvaliacaoAnuncio(anuncio, joao.getId(), (short) 5));
        em.persist(new FavoritoAnuncio(joao.getId(), anuncio));
        em.persist(new AnuncioBolha(anuncio, bolhaCurso.getId()));

        Instant inicio = Instant.parse("2026-11-01T03:00:00Z");
        Instant fim = inicio.plus(7, ChronoUnit.DAYS);
        Impulsionamento impulsionamento = em.persist(new Impulsionamento(anuncio, bolhaCurso.getId(), inicio,
                fim, new BigDecimal("19.90"), StatusImpulsionamento.ATIVO));

        Publicacao publicacao = new Publicacao(ana.getId(), bolhaCurso.getId(), "Bem-vindos!",
                StatusPublicacao.PUBLICADA);
        publicacao.adicionarMidia(TipoMidia.IMAGEM, "https://exemplo.com/foto.png").setTamanhoBytes(5_000_000_000L);
        publicacao.adicionarMidia(TipoMidia.LINK, "https://exemplo.com");
        em.persist(publicacao);

        Comentario comentario = em.persist(new Comentario(publicacao, joao.getId(), "Valeu!"));
        Comentario resposta = new Comentario(publicacao, ana.getId(), "De nada");
        resposta.setComentarioPai(comentario);
        em.persist(resposta);

        Reacao reacaoEmPublicacao = new Reacao(joao.getId(), TipoReacao.CURTIDA);
        reacaoEmPublicacao.setPublicacao(publicacao);
        em.persist(reacaoEmPublicacao);

        Reacao reacaoEmComentario = new Reacao(ana.getId(), TipoReacao.APOIO);
        reacaoEmComentario.setComentario(comentario);
        em.persist(reacaoEmComentario);

        Conversa direta = em.persist(Conversa.direta(ana.getId(), joao.getId()));
        Conversa grupo = em.persist(Conversa.grupo("Turma de ADS"));
        em.persist(new ParticipanteConversa(direta, ana.getId()));

        Mensagem mensagem = new Mensagem(direta, ana.getId());
        mensagem.setConteudo("Oi!");
        em.persist(mensagem);

        Denuncia denuncia = new Denuncia(joao.getId(), MotivoDenuncia.SPAM, StatusDenuncia.ABERTA);
        denuncia.setAnuncioId(anuncio.getId());
        em.persist(denuncia);

        em.flush();
        em.clear();

        // Toda tabela do schema recebeu ao menos uma linha: nenhuma entidade ficou de fora.
        List<String> tabelas = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tabelas).hasSize(25).allSatisfy(tabela -> assertThat(
                jdbc.queryForObject("select count(*) from " + tabela, Long.class)).as(tabela).isPositive());

        Campus campusLido = em.find(Campus.class, eunapolis.getId());
        assertThat(campusLido.getUf()).isEqualTo("BA");
        assertThat(campusLido.getInstituicao().getSigla()).isEqualTo("IFBA");
        assertThat(em.find(Curso.class, ads.getId()).getGrau()).isEqualTo(GrauCurso.TECNOLOGO);

        Usuario anaLida = em.find(Usuario.class, ana.getId());
        assertThat(anaLida.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(anaLida.isEmailVerificado()).isFalse();
        assertThat(em.find(VinculoInstitucional.class, vinculo.getId()).getCursoId()).isEqualTo(ads.getId());

        RefreshToken refreshTokenLido = em.find(RefreshToken.class, refreshToken.getId());
        assertThat(refreshTokenLido.getUsuario().getId()).isEqualTo(ana.getId());
        assertThat(refreshTokenLido.isPersistente()).isTrue();
        assertThat(refreshTokenLido.isRevogado()).isFalse();
        assertThat(refreshTokenLido.getCriadoEm()).isNotNull();

        assertThat(em.find(Conexao.class, conexao.getId()).getStatus()).isEqualTo(StatusConexao.PENDENTE);
        assertThat(em.find(Notificacao.class, notificacao.getId()).getReferenciaId()).isEqualTo(conexao.getId());

        Bolha bolhaLida = em.find(Bolha.class, bolhaCurso.getId());
        assertThat(bolhaLida.getBolhaPai().getSlug()).isEqualTo("ifba-eunapolis");
        assertThat(bolhaLida.isEntradaAutomatica()).isTrue();
        assertThat(bolhaLida.isSomenteLeitura()).isFalse();

        MembroBolha membroLido = em.find(MembroBolha.class, new MembroBolhaId(bolhaCurso.getId(), ana.getId()));
        assertThat(membroLido.getPapel()).isEqualTo(PapelMembro.MODERADOR);
        assertThat(membroLido.isNotificacoesAtivas()).isTrue();
        assertThat(membroLido.getEntrouEm()).isNotNull();

        assertThat(em.find(CategoriaAnuncio.class, aulas.getId()).getCategoriaPai().isAtiva()).isTrue();

        Anuncio anuncioLido = em.find(Anuncio.class, anuncio.getId());
        assertThat(anuncioLido.getPreco()).isEqualByComparingTo("49.90");
        assertThat(anuncioLido.getModalidadePreco()).isEqualTo(ModalidadePreco.POR_HORA);
        assertThat(anuncioLido.getImagens()).extracting(ImagemAnuncio::getOrdem).containsExactly((short) 0, (short) 1);

        assertThat(em.find(AvaliacaoAnuncio.class, avaliacao.getId()).getNota()).isEqualTo((short) 5);
        assertThat(em.find(FavoritoAnuncio.class, new FavoritoAnuncioId(joao.getId(), anuncio.getId()))).isNotNull();
        assertThat(em.find(AnuncioBolha.class, new AnuncioBolhaId(anuncio.getId(), bolhaCurso.getId()))
                .getVinculadoEm()).isNotNull();

        Impulsionamento impulsionamentoLido = em.find(Impulsionamento.class, impulsionamento.getId());
        assertThat(impulsionamentoLido.getValorPago()).isEqualByComparingTo("19.90");
        assertThat(impulsionamentoLido.getPeriodoFim()).isEqualTo(fim);

        Publicacao publicacaoLida = em.find(Publicacao.class, publicacao.getId());
        assertThat(publicacaoLida.isFixada()).isFalse();
        assertThat(publicacaoLida.getMidias()).extracting(MidiaPublicacao::getTipo)
                .containsExactly(TipoMidia.IMAGEM, TipoMidia.LINK);
        assertThat(publicacaoLida.getMidias().getFirst().getTamanhoBytes()).isEqualTo(5_000_000_000L);

        assertThat(em.find(Comentario.class, resposta.getId()).getComentarioPai().getConteudo()).isEqualTo("Valeu!");

        Reacao reacaoLida = em.find(Reacao.class, reacaoEmComentario.getId());
        assertThat(reacaoLida.getPublicacao()).isNull();
        assertThat(reacaoLida.getComentario().getId()).isEqualTo(comentario.getId());

        assertThat(em.find(Conversa.class, direta.getId()).getTipo()).isEqualTo(TipoConversa.DIRETA);
        Conversa grupoLido = em.find(Conversa.class, grupo.getId());
        assertThat(grupoLido.getTipo()).isEqualTo(TipoConversa.GRUPO);
        assertThat(grupoLido.getUsuarioMenorId()).isNull();

        assertThat(em.find(ParticipanteConversa.class, new ParticipanteConversaId(direta.getId(), ana.getId()))
                .isArquivada()).isFalse();
        assertThat(em.find(Mensagem.class, mensagem.getId()).getEnviadoEm()).isNotNull();

        Denuncia denunciaLida = em.find(Denuncia.class, denuncia.getId());
        assertThat(denunciaLida.getMotivo()).isEqualTo(MotivoDenuncia.SPAM);
        assertThat(denunciaLida.getAnuncioId()).isEqualTo(anuncio.getId());
    }

    @Test
    void ordenaOParDeUuidsComoOPostgres() {
        assertThat(ID_MAIOR_NO_POSTGRES).isLessThan(ID_MENOR_NO_POSTGRES); // a ordem do Java é a oposta

        usuarioComId(ID_MENOR_NO_POSTGRES, "menor");
        usuarioComId(ID_MAIOR_NO_POSTGRES, "maior");

        Conexao conexao = em.persistAndFlush(Conexao.solicitar(ID_MAIOR_NO_POSTGRES, ID_MENOR_NO_POSTGRES));
        assertThat(conexao.getUsuarioMenorId()).isEqualTo(ID_MENOR_NO_POSTGRES);
        assertThat(conexao.getUsuarioMaiorId()).isEqualTo(ID_MAIOR_NO_POSTGRES);
        assertThat(conexao.getSolicitanteId()).isEqualTo(ID_MAIOR_NO_POSTGRES);

        Conversa conversa = em.persistAndFlush(Conversa.direta(ID_MAIOR_NO_POSTGRES, ID_MENOR_NO_POSTGRES));
        assertThat(conversa.getUsuarioMenorId()).isEqualTo(ID_MENOR_NO_POSTGRES);
        assertThat(conversa.getUsuarioMaiorId()).isEqualTo(ID_MAIOR_NO_POSTGRES);
    }

    @Test
    void oBancoRejeitaOParNaOrdemDoCompareTo() {
        usuarioComId(ID_MENOR_NO_POSTGRES, "menor");
        usuarioComId(ID_MAIOR_NO_POSTGRES, "maior");

        assertThatThrownBy(() -> jdbc.update("""
                insert into conexao (usuario_menor_id, usuario_maior_id, solicitante_id, status)
                values (?, ?, ?, 'PENDENTE')
                """, ID_MAIOR_NO_POSTGRES, ID_MENOR_NO_POSTGRES, ID_MENOR_NO_POSTGRES))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_conexao_par_ordenado");
    }

    @Test
    void recusaParSemDoisUsuariosDiferentes() {
        UUID ana = usuario("ana").getId();

        assertThatThrownBy(() -> Conexao.solicitar(ana, ana)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversa.direta(ana, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oBancoRejeitaCampusDeOutraInstituicao() {
        Instituicao ifba = em.persist(new Instituicao("IFBA"));
        Instituicao outra = em.persist(new Instituicao("Outra"));
        Campus campusDaOutra = em.persist(new Campus(outra, "Campus da outra"));

        VinculoInstitucional vinculo = new VinculoInstitucional(usuario("ana"), ifba.getId(), TipoVinculo.ALUNO);
        vinculo.setCampusId(campusDaOutra.getId());

        assertThatThrownBy(() -> em.persistAndFlush(vinculo))
                .rootCause()
                .hasMessageContaining("fk_vinculo_campus");
    }

    @Test
    void preencheAtualizadoEmSomenteNaAlteracao() {
        Instituicao instituicao = em.persistAndFlush(new Instituicao("IFBA"));
        assertThat(instituicao.getCriadoEm()).isNotNull();
        assertThat(instituicao.getAtualizadoEm()).isNull();

        instituicao.setSigla("IFBA");
        em.flush();
        assertThat(instituicao.getAtualizadoEm()).isNotNull();
    }

    @Test
    void normalizaOEmailNaCriacaoENaAlteracao() {
        Usuario usuario = new Usuario("ana", "Ana", "  Ana@Exemplo.COM ", "hash", StatusUsuario.ATIVO);
        assertThat(usuario.getEmail()).isEqualTo("ana@exemplo.com");

        usuario.setEmail("ANA.SILVA@exemplo.com");
        assertThat(usuario.getEmail()).isEqualTo("ana.silva@exemplo.com");
    }

    @Test
    void normalizaOUsernameNaCriacaoENaAlteracao() {
        Usuario usuario = new Usuario("  @Ana.BC ", "Ana", "ana@exemplo.com", "hash", StatusUsuario.ATIVO);
        assertThat(usuario.getUsername()).isEqualTo("ana.bc");

        usuario.setUsername("ANA_silva");
        assertThat(usuario.getUsername()).isEqualTo("ana_silva");
    }

    @Test
    void usernamesQueSoDiferemEmMaiusculasColidem() {
        usuario("ana");
        Usuario outraConta = new Usuario("ANA", "Ana", "outra@exemplo.com", "hash", StatusUsuario.ATIVO);

        assertThatThrownBy(() -> em.persistAndFlush(outraConta))
                .rootCause()
                .hasMessageContaining("usuario_username_key");
    }

    @Test
    void oBancoRejeitaUsernameNaoNormalizado() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into usuario (username, nome_completo, email, senha_hash, status)
                values ('Ana', 'Ana', 'ana@exemplo.com', 'hash', 'ATIVO')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_username_normalizado");
    }

    @Test
    void emailsQueSoDiferemEmMaiusculasColidem() {
        usuario("ana");
        Usuario outraConta = new Usuario("ana2", "Ana", "ANA@exemplo.com", "hash", StatusUsuario.ATIVO);

        assertThatThrownBy(() -> em.persistAndFlush(outraConta))
                .rootCause()
                .hasMessageContaining("usuario_email_key");
    }

    @Test
    void oBancoRejeitaEmailNaoNormalizado() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into usuario (username, nome_completo, email, senha_hash, status)
                values ('ana', 'Ana', 'Ana@exemplo.com', 'hash', 'ATIVO')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_email_normalizado");
    }

    @Test
    void equalsEHashCodeNaoInicializamOProxy() {
        Usuario ana = usuario("ana");
        em.flush();
        em.clear();

        Usuario proxy = em.getEntityManager().getReference(Usuario.class, ana.getId());

        assertThat(proxy.equals(ana)).isTrue();
        assertThat(ana.equals(proxy)).isTrue();
        assertThat(proxy.hashCode()).isEqualTo(ana.hashCode());
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
    }

    private Usuario usuario(String nome) {
        return em.persist(new Usuario(nome, nome, nome + "@exemplo.com", "hash", StatusUsuario.ATIVO));
    }

    /** O Hibernate gera o id no persist, então um id escolhido precisa entrar por SQL. */
    private void usuarioComId(UUID id, String nome) {
        jdbc.update("""
                insert into usuario (id, username, nome_completo, email, senha_hash, status)
                values (?, ?, ?, ?, 'hash', 'ATIVO')
                """, id, nome, nome, nome + "@exemplo.com");
    }
}
