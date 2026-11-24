package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VerificacaoEmailApiTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String SENHA = ContasDeTeste.SENHA;

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

    // ------------------------------------------------------------------ e-mail

    @Test
    void oEmailSaiDepoisDoCadastroComOCodigo() throws Exception {
        codigos.proximos("042917");
        cadastrar("<b>Ana</b> Beatriz", "ana", EMAIL).andExpect(status().isCreated());

        MimeMessage mensagem = caixa.esperar(EMAIL, 1);
        assertThat(mensagem.getSubject()).isEqualTo("042917 é o seu código do CampusConnect");
        assertThat(mensagem.getFrom()[0].toString()).contains("CampusConnect");

        String html = CaixaDeSaidaFalsa.corpo(mensagem, "text/html");
        assertThat(html).contains("042917", "15 minutos", "Olá, &lt;b&gt;Ana&lt;/b&gt;!").doesNotContain("<b>Ana");
        String texto = CaixaDeSaidaFalsa.corpo(mensagem, "text/plain");
        assertThat(texto).contains("042917", "15 minutos", "Olá, <b>Ana</b>!");
    }

    // ------------------------------------------------------------- confirmação

    @Test
    void oCodigoCertoVerificaOEmailEApagaOsCodigos() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        verificar(EMAIL, "123 456").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(jdbc.queryForObject("select email_verificado from usuario", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isZero();
    }

    @Test
    void oCodigoErradoContaAsTentativasEAQuintaMataOCodigo() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        verificar(EMAIL, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 4 tentativas."));
        assertThat(jdbc.queryForObject("select tentativas from verificacao_email", Integer.class)).isOne();
        verificar(EMAIL, "000000");
        verificar(EMAIL, "000000");
        verificar(EMAIL, "000000")
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 1 tentativa."));
        verificar(EMAIL, "000000")
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Peça um código novo."));

        verificar(EMAIL, CodigosDeTeste.PADRAO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Este código não vale mais. Peça um código novo."));
        assertThat(jdbc.queryForObject("select email_verificado from usuario", Boolean.class)).isFalse();
    }

    @Test
    void oCodigoVencidoNaoVale() throws Exception {
        cadastrar("Ana", "ana", EMAIL);
        jdbc.update("update verificacao_email set expira_em = now() - interval '1 second'");

        verificar(EMAIL, CodigosDeTeste.PADRAO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Este código não vale mais. Peça um código novo."));
    }

    @Test
    void codigoComLetrasOuTamanhoErradoEhRecusadoAntes() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        verificar(EMAIL, "12345").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("O código tem 6 números."));
        verificar(EMAIL, "12a456").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select tentativas from verificacao_email", Integer.class)).isZero();
    }

    @Test
    void emailJaVerificadoNaoEntraDeNovoPeloCodigo() throws Exception {
        cadastrar("Ana", "ana", EMAIL);
        verificar(EMAIL, CodigosDeTeste.PADRAO).andExpect(status().isOk());

        verificar(EMAIL, CodigosDeTeste.PADRAO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este e-mail já foi confirmado. Entre com a sua senha."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void emailSemContaRecebeAMesmaRespostaDeCodigoVencido() throws Exception {
        verificar("ninguem@exemplo.com", CodigosDeTeste.PADRAO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Este código não vale mais. Peça um código novo."));
    }

    // ------------------------------------------------------------------ reenvio

    @Test
    void reenviarMandaOutroCodigoEOAnteriorDeixaDeValer() throws Exception {
        codigos.proximos("111111", "222222");
        cadastrar("Ana", "ana", EMAIL);
        passarOIntervaloDeReenvio();

        reenviar(EMAIL).andExpect(status().isAccepted());

        assertThat(caixa.esperar(EMAIL, 2).getSubject()).startsWith("222222");
        verificar(EMAIL, "111111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").value("Código incorreto. Você tem mais 4 tentativas."));
        verificar(EMAIL, "222222").andExpect(status().isOk());
    }

    @Test
    void reenvioRespeitaOIntervaloEOLimitePorHora() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        reenviar(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.aguardarSegundos").value(allOf(greaterThan(0), lessThanOrEqualTo(60))))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        for (int envio = 2; envio <= 5; envio++) {
            passarOIntervaloDeReenvio();
            reenviar(EMAIL).andExpect(status().isAccepted());
        }
        passarOIntervaloDeReenvio();
        reenviar(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.aguardarSegundos").value(greaterThan(60)))
                .andExpect(jsonPath("$.detail").value(startsWith("Você pediu muitos códigos.")));
        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isEqualTo(5);
    }

    @Test
    void reenviarParaEmailSemContaOuJaVerificadoNaoFazNada() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        passarOIntervaloDeReenvio();

        reenviar("ninguem@exemplo.com").andExpect(status().isAccepted());
        reenviar(EMAIL).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isZero();
    }

    // -------------------------------------------------------------------- login

    @Test
    void loginSemVerificarRecusaEMandaOutroCodigo() throws Exception {
        codigos.proximos("111111", "222222");
        cadastrar("Ana", "ana", EMAIL);
        passarOIntervaloDeReenvio();

        entrar(EMAIL, "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isOne();

        entrar(EMAIL, SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("email-nao-verificado"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(caixa.esperar(EMAIL, 2).getSubject()).startsWith("222222");
        verificar(EMAIL, "222222").andExpect(status().isOk());
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void loginLogoDepoisDoCadastroNaoMandaOutroCodigo() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        entrar(EMAIL, SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("email-nao-verificado"));

        assertThat(jdbc.queryForObject("select count(*) from verificacao_email", Long.class)).isOne();
    }

    @Test
    void contaSuspensaSemVerificarContinuaComARespostaDeContaInativa() throws Exception {
        cadastrar("Ana", "ana", EMAIL);
        jdbc.update("update usuario set status = 'SUSPENSO'");

        entrar(EMAIL, SENHA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Esta conta não está ativa."))
                .andExpect(jsonPath("$.codigo").doesNotExist());
        verificar(EMAIL, CodigosDeTeste.PADRAO)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ------------------------------------------------------- e-mail e username

    @Test
    void novoCadastroComEmailNaoVerificadoRefazAConta() throws Exception {
        cadastrar("Ana", "ana", EMAIL);
        UUID id = jdbc.queryForObject("select id from usuario", UUID.class);

        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "Ana Beatriz", "username": "ana.b", "email": "ANA@exemplo.com",
                         "senha": "outra-senha-forte"}
                        """))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForMap("select id, username, nome_completo from usuario"))
                .containsEntry("id", id)
                .containsEntry("username", "ana.b")
                .containsEntry("nome_completo", "Ana Beatriz");
        verificar(EMAIL, CodigosDeTeste.PADRAO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.username").value("ana.b"));
        entrar(EMAIL, SENHA).andExpect(status().isUnauthorized());
        entrar(EMAIL, "outra-senha-forte").andExpect(status().isOk());
    }

    @Test
    void usernameDeContaNaoVerificadaSoSeLiberaDepoisDe24Horas() throws Exception {
        cadastrar("Ana", "ana", EMAIL);

        cadastrar("Outra Ana", "ana", "outra@exemplo.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.username").value("Este nome de usuário já está em uso."));

        jdbc.update("update usuario set criado_em = now() - interval '25 hours'");
        cadastrar("Outra Ana", "ana", "outra@exemplo.com").andExpect(status().isCreated());

        assertThat(jdbc.queryForList("select email from usuario", String.class)).containsExactly("outra@exemplo.com");
    }

    @Test
    void usernameDeContaVerificadaNuncaSeLibera() throws Exception {
        ContasDeTeste.cadastrar(mvc, "Ana", "ana", EMAIL);
        jdbc.update("update usuario set criado_em = now() - interval '25 hours'");

        cadastrar("Outra Ana", "ana", "outra@exemplo.com").andExpect(status().isConflict());
    }

    @Test
    void contaNaoVerificadaNaoApareceParaOsOutros() throws Exception {
        String token = JsonPath.read(ContasDeTeste.cadastrar(mvc, "Bia", "bia", "bia@exemplo.com"), "$.accessToken");
        cadastrar("Ana", "ana", EMAIL);

        mvc.perform(get("/api/usuarios/ana/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
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

    private ResultActions reenviar(String email) throws Exception {
        return mvc.perform(post("/api/auth/reenviar-codigo").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(email)));
    }

    private ResultActions entrar(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "senha": "%s", "manterConectado": true}
                """.formatted(email, senha)));
    }

    /** Puxa os códigos já mandados 2 minutos para trás, como se o intervalo de 60 s tivesse passado. */
    private void passarOIntervaloDeReenvio() {
        jdbc.update("update verificacao_email set criado_em = criado_em - interval '2 minutes'");
    }
}
