package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** O "Esqueceu a senha?" ({@code /api/auth/esqueci-senha} e {@code /redefinir-senha}) e o alterar senha. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SenhaApiTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String SENHA = ContasDeTeste.SENHA;
    private static final String NOVA = "outra-senha-forte";
    private static final String COOKIE = "cc_refresh";
    private static final String ASSUNTO_AVISO = "A sua senha do CampusConnect foi alterada";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CodigosDeTeste codigos;

    @Autowired
    private CaixaDeSaidaFalsa caixa;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate usuario, bolha cascade");
        codigos.limpar();
        caixa.esvaziar();
    }

    // ------------------------------------------------------- esqueci a senha

    @Test
    void esqueciASenhaMandaOCodigoDeRedefinicao() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana Beatriz", "ana", EMAIL);
        codigos.proximos("314159");

        esqueci(EMAIL).andExpect(status().isAccepted());

        MimeMessage mensagem = caixa.esperarAssunto(EMAIL, "314159");
        assertThat(mensagem.getSubject()).isEqualTo("314159 é o seu código para redefinir a senha");
        assertThat(CaixaDeSaidaFalsa.corpo(mensagem, "text/html"))
                .contains("314159", "15 minutos", "Olá, Ana!", "redefinir a senha");
        assertThat(CaixaDeSaidaFalsa.corpo(mensagem, "text/plain")).contains("314159", "15 minutos");
        assertThat(jdbc.queryForObject("select finalidade from verificacao_email", String.class))
                .isEqualTo("REDEFINICAO_SENHA");
    }

    @Test
    void emailSemContaOuDeContaSuspensaRecebeAMesmaRespostaSemCodigo() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        jdbc.update("update usuario set status = 'SUSPENSO'");

        esqueci("ninguem@exemplo.com").andExpect(status().isAccepted());
        esqueci(EMAIL).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isZero();
    }

    @Test
    void oLimiteDeEnviosContaSeparadoDoDaVerificacao() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        // O código de verificação acabou de sair, mas o de redefinição ainda não.
        esqueci(EMAIL).andExpect(status().isAccepted());
        esqueci(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.aguardarSegundos").value(allOf(greaterThan(0), lessThanOrEqualTo(60))));
    }

    // ------------------------------------------------------------- redefinir

    @Test
    void conferirNaoGastaOCodigoERedefinirTrocaASenhaEEntra() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        esqueci(EMAIL);

        conferir(EMAIL, "123-456").andExpect(status().isNoContent());
        redefinir(EMAIL, CodigosDeTeste.PADRAO, NOVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("ana"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString(COOKIE + "="), containsString("Max-Age="))));

        entrar(EMAIL, SENHA, true).andExpect(status().isUnauthorized());
        entrar(EMAIL, NOVA, true).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isZero();
        caixa.esperarAssunto(EMAIL, ASSUNTO_AVISO);
    }

    @Test
    void oCodigoUsadoNaoRedefineDeNovo() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        esqueci(EMAIL);
        redefinir(EMAIL, CodigosDeTeste.PADRAO, NOVA).andExpect(status().isOk());

        redefinir(EMAIL, CodigosDeTeste.PADRAO, "terceira-senha-forte")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Este código não vale mais. Peça um código novo."));
    }

    @Test
    void redefinirEncerraAsSessoesAbertas() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie outroAparelho = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());
        esqueci(EMAIL);

        Cookie desteAparelho = refreshCookie(redefinir(EMAIL, CodigosDeTeste.PADRAO, NOVA).andReturn());

        renovar(outroAparelho).andExpect(status().isUnauthorized());
        renovar(desteAparelho).andExpect(status().isOk());
    }

    @Test
    void oCodigoErradoContaTentativasNaConferenciaENaRedefinicao() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        esqueci(EMAIL);

        conferir(EMAIL, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 4 tentativas."));
        redefinir(EMAIL, "000000", NOVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 3 tentativas."));

        assertThat(jdbc.queryForObject("select tentativas from verificacao_email", Integer.class)).isEqualTo(2);
        entrar(EMAIL, SENHA, true).andExpect(status().isOk());
    }

    @Test
    void umCodigoNaoValeNoLugarDoOutro() throws Exception {
        codigos.proximos("111111", "222222");
        cadastrar("Ana", "ana", EMAIL);
        esqueci(EMAIL);

        conferir(EMAIL, "111111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 4 tentativas."));
        verificar(EMAIL, "222222")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 4 tentativas."));
    }

    @Test
    void verificarOEmailNaoApagaOCodigoDeRedefinicao() throws Exception {
        codigos.proximos("111111", "222222");
        cadastrar("Ana", "ana", EMAIL);
        esqueci(EMAIL);

        verificar(EMAIL, "111111").andExpect(status().isOk());

        conferir(EMAIL, "222222").andExpect(status().isNoContent());
    }

    @Test
    void redefinirConfirmaOEmailDaContaQueAindaNaoTinhaConfirmado() throws Exception {
        cadastrar("Ana", "ana", EMAIL);
        esqueci(EMAIL);

        redefinir(EMAIL, CodigosDeTeste.PADRAO, NOVA).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select email_verificado from usuario", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isZero();
    }

    @Test
    void contaSuspensaDepoisDoPedidoNaoRedefine() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        esqueci(EMAIL);
        jdbc.update("update usuario set status = 'SUSPENSO'");

        redefinir(EMAIL, CodigosDeTeste.PADRAO, NOVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Este código não vale mais. Peça um código novo."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void aSenhaNovaSegueARegraDoCadastro() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        esqueci(EMAIL);

        redefinir(EMAIL, CodigosDeTeste.PADRAO, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.novaSenha").value("A senha precisa ter de 8 a 128 caracteres."));
        assertThat(jdbc.queryForObject("select tentativas from verificacao_email", Integer.class)).isZero();
    }

    // ---------------------------------------------------------- alterar senha

    @Test
    void alterarASenhaMantemEsteAparelhoEEncerraOsOutros() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie este = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());
        Cookie outro = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());

        MvcResult resposta = alterar(este, SENHA, NOVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=")))
                .andReturn();

        renovar(este).andExpect(status().isUnauthorized());
        renovar(outro).andExpect(status().isUnauthorized());
        renovar(refreshCookie(resposta)).andExpect(status().isOk());
        entrar(EMAIL, SENHA, true).andExpect(status().isUnauthorized());
        entrar(EMAIL, NOVA, true).andExpect(status().isOk());

        MimeMessage aviso = caixa.esperarAssunto(EMAIL, ASSUNTO_AVISO);
        assertThat(CaixaDeSaidaFalsa.corpo(aviso, "text/html")).contains("Olá, Ana!", "Esqueceu a senha?");
        assertThat(CaixaDeSaidaFalsa.corpo(aviso, "text/plain")).contains("Esqueceu a senha?");
    }

    @Test
    void semManterConectadoASessaoNovaTambemNaoMantem() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie sessao = refreshCookie(entrar(EMAIL, SENHA, false).andReturn());

        alterar(sessao, SENHA, NOVA)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString(COOKIE + "="), not(containsString("Max-Age")))));
    }

    @Test
    void aSenhaAtualErradaNaoTrocaNada() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie sessao = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());

        alterar(sessao, "senha-errada", NOVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.senhaAtual").value("A senha atual está incorreta."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        renovar(sessao).andExpect(status().isOk());
        entrar(EMAIL, SENHA, true).andExpect(status().isOk());
    }

    @Test
    void aSenhaNovaPrecisaSerDiferenteDaAtual() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie sessao = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());

        alterar(sessao, SENHA, SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.novaSenha").value("A senha nova precisa ser diferente da atual."));
        alterar(sessao, SENHA, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.novaSenha").value("A senha precisa ter de 8 a 128 caracteres."));
    }

    @Test
    void semUmaSessaoQueValhaNaoAltera() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie sessao = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());
        mvc.perform(post("/api/auth/logout").cookie(sessao)).andExpect(status().isNoContent());

        alterar(null, SENHA, NOVA).andExpect(status().isUnauthorized());
        alterar(new Cookie(COOKIE, "token-inventado"), SENHA, NOVA).andExpect(status().isUnauthorized());
        alterar(sessao, SENHA, NOVA)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        entrar(EMAIL, SENHA, true).andExpect(status().isOk());
    }

    @Test
    void contaSuspensaNaoAlteraEPerdeASessao() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        Cookie sessao = refreshCookie(entrar(EMAIL, SENHA, true).andReturn());
        jdbc.update("update usuario set status = 'SUSPENSO'");

        alterar(sessao, SENHA, NOVA).andExpect(status().isUnauthorized());

        jdbc.update("update usuario set status = 'ATIVO'");
        renovar(sessao).andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------- apoio

    private ResultActions cadastrar(String nome, String username, String email) throws Exception {
        return mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nomeCompleto": "%s", "username": "%s", "email": "%s", "senha": "%s"}
                """.formatted(nome, username, email, SENHA)));
    }

    private ResultActions verificar(String email, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/verificar-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "codigo": "%s"}
                """.formatted(email, codigo)));
    }

    private ResultActions esqueci(String email) throws Exception {
        return mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(email)));
    }

    private ResultActions conferir(String email, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha/conferir").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "codigo": "%s"}
                        """.formatted(email, codigo)));
    }

    private ResultActions redefinir(String email, String codigo, String novaSenha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "codigo": "%s", "novaSenha": "%s"}
                """.formatted(email, codigo, novaSenha)));
    }

    private ResultActions alterar(Cookie sessao, String senhaAtual, String novaSenha) throws Exception {
        var requisicao = post("/api/auth/alterar-senha").contentType(MediaType.APPLICATION_JSON).content("""
                {"senhaAtual": "%s", "novaSenha": "%s"}
                """.formatted(senhaAtual, novaSenha));
        return mvc.perform(sessao == null ? requisicao : requisicao.cookie(sessao));
    }

    private ResultActions entrar(String email, String senha, boolean manterConectado) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "senha": "%s", "manterConectado": %s}
                """.formatted(email, senha, manterConectado)));
    }

    private ResultActions renovar(Cookie refresh) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(refresh));
    }

    private static Cookie refreshCookie(MvcResult resultado) {
        Cookie cookie = resultado.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("cookie %s na resposta", COOKIE).isNotNull();
        return new Cookie(COOKIE, cookie.getValue());
    }
}
