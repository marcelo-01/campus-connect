package br.com.campusconnect;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    /** Os testes sabem o código de verificação de antemão (ver {@link ContasDeTeste}). */
    @Bean
    @Primary
    CodigosDeTeste codigosDeTeste() {
        return new CodigosDeTeste();
    }

    /** Nenhum e-mail sai para a rede: eles ficam guardados aqui. */
    @Bean
    @Primary
    CaixaDeSaidaFalsa caixaDeSaida() {
        return new CaixaDeSaidaFalsa();
    }

}
