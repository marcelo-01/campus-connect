package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArquiteturaModularTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(CampusConnectApplication.class);

    @Test
    void reconheceOsDezModulos() {
        assertThat(MODULOS.stream().map(modulo -> modulo.getIdentifier().toString())).containsExactlyInAnyOrder(
                "arquivo", "bolha", "comum", "conexao", "conversa", "marketplace", "moderacao", "notificacao",
                "publicacao", "usuario");
    }

    /** Falha se um módulo usar algo além do {@code service} de outro ou sair do que declarou no package-info. */
    @Test
    void cadaModuloRespeitaSuasFronteiras() {
        MODULOS.verify();
    }
}
