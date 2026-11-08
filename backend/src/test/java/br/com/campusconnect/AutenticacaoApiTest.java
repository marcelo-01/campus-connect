package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacaoApiTest {

    private static final String COOKIE = "cc_refresh";
    private static final String SENHA = "senha-forte-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limparContas() {
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from usuario");
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    void cadastroCriaAContaJaLogadaEGuardaASenhaComArgon2() throws Exception {
        cadastrar("Ana Beatriz", "@Ana.BC", "  Ana@Exemplo.com ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("ana.bc"))
                .andExpect(jsonPath("$.usuario.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.usuario.nomeCompleto").value("Ana Beatriz"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE + "="),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age="))));

        String hash = jdbc.queryForObject("select senha_hash from usuario where email = 'ana@exemplo.com'",
                String.class);
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(SENHA);
    }

    @Test
    void emailRepetidoComOutrasMaiusculasEhRecusado() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com").andExpect(status().isCreated());

        cadastrar("Outra Ana", "outra.ana", "ANA@Exemplo.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.email").value("Este e-mail já está em uso."));
    }

    @Test
    void usernameRepetidoEhRecusado() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com").andExpect(status().isCreated());

        cadastrar("Outra Ana", "ANA", "outra@exemplo.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.username").value("Este nome de usuário já está em uso."));
    }

    @Test
    void dadosInvalidosVoltamComOErroDeCadaCampo() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": " ", "username": "a b", "email": "sem-arroba", "senha": "curta"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nomeCompleto").exists())
                .andExpect(jsonPath("$.erros.username").exists())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.senha").exists());

        assertThat(jdbc.queryForObject("select count(*) from usuario", Long.class)).isZero();
    }

    // ------------------------------------------------------------------- login

    @Test
    void loginComSenhaCertaAbreASessao() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com");
        jdbc.update("update usuario set ultimo_acesso_em = null");

        entrar("ANA@exemplo.com", SENHA, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("ana"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=")));

        assertThat(jdbc.queryForObject("select ultimo_acesso_em is not null from usuario", Boolean.class)).isTrue();
    }

    @Test
    void semManterConectadoOCookieSomeAoFecharONavegador() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com");

        entrar("ana@exemplo.com", SENHA, false)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString(COOKIE + "="), not(containsString("Max-Age")))));
    }

    @Test
    void senhaErradaEEmailInexistenteTemAMesmaResposta() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com");

        entrar("ana@exemplo.com", "senha-errada", true)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        entrar("ninguem@exemplo.com", SENHA, true)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
    }

    @Test
    void contaSuspensaSoEhReveladaParaQuemSabeASenha() throws Exception {
        cadastrar("Ana", "ana", "ana@exemplo.com");
        jdbc.update("update usuario set status = 'SUSPENSO'");

        entrar("ana@exemplo.com", "senha-errada", true)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
        entrar("ana@exemplo.com", SENHA, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Esta conta não está ativa."));
    }

    // ----------------------------------------------------------- rota protegida

    @Test
    void rotaProtegidaExigeOJwtDeAcesso() throws Exception {
        String accessToken = JsonPath.read(corpo(cadastrar("Ana", "ana", "ana@exemplo.com")), "$.accessToken");

        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
    }

    // ----------------------------------------------------------------- refresh

    @Test
    void refreshTrocaOTokenEEmiteOutroJwt() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());

        MvcResult renovado = renovar(original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("ana"))
                .andReturn();
        Cookie novo = refreshCookie(renovado);

        assertThat(novo.getValue()).isNotEqualTo(original.getValue());
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revogado_em is null", Long.class))
                .isOne();
        assertThat(jdbc.queryForObject("select count(distinct familia_id) from refresh_token", Long.class)).isOne();
    }

    @Test
    void semCookieORefreshFalha() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        renovar(new Cookie(COOKIE, "token-inventado")).andExpect(status().isUnauthorized());
    }

    /**
     * Duas abas renovando juntas: a segunda recebe 409 e tenta de novo com o cookie novo. A resposta não
     * pode apagar o cookie, senão derrubaria a sessão que a primeira aba acabou de renovar.
     */
    @Test
    void reusoImediatoNaoDerrubaASessao() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());
        Cookie novo = refreshCookie(renovar(original).andReturn());

        renovar(original)
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        renovar(novo).andExpect(status().isOk());
    }

    /** Um token trocado há mais de 30 s que volta a ser usado indica cookie copiado: a família toda cai. */
    @Test
    void reusoDeTokenAntigoDerrubaAFamilia() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());
        Cookie novo = refreshCookie(renovar(original).andReturn());
        jdbc.update("update refresh_token set revogado_em = now() - interval '1 minute' where revogado_em is not null");

        renovar(original).andExpect(status().isUnauthorized());
        renovar(novo).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshDeContaSuspensaFalhaERevogaASessao() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());
        jdbc.update("update usuario set status = 'SUSPENSO'");

        renovar(original).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revogado_em is null", Long.class))
                .isZero();
    }

    @Test
    void refreshVencidoFalha() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());
        jdbc.update("update refresh_token set expira_em = now() - interval '1 second'");

        renovar(original).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logoutRevogaOTokenEApagaOCookie() throws Exception {
        Cookie original = refreshCookie(cadastrar("Ana", "ana", "ana@exemplo.com").andReturn());

        mvc.perform(post("/api/auth/logout").cookie(original))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        renovar(original).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSemCookieNaoEhErro() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    // ----------------------------------------------------------------- apoio

    private ResultActions cadastrar(String nome, String username, String email) throws Exception {
        return mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nomeCompleto": "%s", "username": "%s", "email": "%s", "senha": "%s"}
                """.formatted(nome, username, email, SENHA)));
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

    private static String corpo(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }
}
