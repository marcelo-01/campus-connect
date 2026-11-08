package br.com.campusconnect.usuario.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CodificadorSenhaTest {

    private final PasswordEncoder senhas = new SegurancaConfig().codificadorSenha();

    @Test
    void geraArgon2idComOsParametrosDaOwasp() {
        String hash = senhas.encode("senha-forte-123");

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(senhas.matches("senha-forte-123", hash)).isTrue();
        assertThat(senhas.matches("senha-errada", hash)).isFalse();
    }

    /** {@code usuario.senha_hash} é {@code VARCHAR(100)}: mudar os parâmetros não pode estourar a coluna. */
    @Test
    void oHashCabeNaColunaSenhaHash() {
        assertThat(senhas.encode("x".repeat(128))).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void doisHashesDaMesmaSenhaTemSaltsDiferentes() {
        assertThat(senhas.encode("mesma-senha")).isNotEqualTo(senhas.encode("mesma-senha"));
    }
}
