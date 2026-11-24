package br.com.campusconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Cria contas pela API como uma pessoa faria: cadastra e confirma o e-mail com o código dos testes. */
final class ContasDeTeste {

    static final String SENHA = "senha-forte-123";

    private ContasDeTeste() {
    }

    /** @return o corpo da resposta da verificação, com {@code accessToken} e {@code usuario}, como o login */
    static String cadastrar(MockMvc mvc, String nome, String username, String email) throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "%s", "username": "%s", "email": "%s", "senha": "%s"}
                        """.formatted(nome, username, email, SENHA)))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/verificar-email").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "codigo": "%s"}
                        """.formatted(email, CodigosDeTeste.PADRAO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
