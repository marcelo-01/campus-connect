package br.com.campusconnect.usuario.seguranca;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API sem estado: cada requisição autenticada traz o JWT de acesso no header {@code Authorization}.
 *
 * <p>O CSRF fica desligado porque nenhuma rota autenticada depende de cookie. As únicas que leem
 * cookie são {@code /api/auth/refresh} e {@code /api/auth/logout}, e o cookie delas é
 * {@code SameSite=Strict}: o navegador não o envia em requisições vindas de outro site.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PropriedadesSeguranca.class)
class SegurancaConfig {

    private static final int BYTES_MINIMOS_CHAVE = 32;

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        // A tag <img> não manda o JWT; o nome aleatório do arquivo é a proteção.
                        .requestMatchers(HttpMethod.GET, "/api/arquivos/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor.jwt(jwt -> { }))
                .build();
    }

    /**
     * Argon2id com os parâmetros mínimos da OWASP: 19 MiB de memória, 2 iterações, paralelismo 1,
     * salt de 16 bytes e hash de 32 bytes.
     *
     * <p>O texto gerado tem 97 caracteres e cabe em {@code usuario.senha_hash VARCHAR(100)}. Por isso o
     * encoder é usado direto, e não pelo {@code DelegatingPasswordEncoder}: o prefixo
     * {@code {argon2@SpringSecurity_v5_8}} que ele acrescenta passaria do tamanho da coluna.
     */
    @Bean
    PasswordEncoder codificadorSenha() {
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    /**
     * Confere a situação da conta só depois da senha. Na ordem padrão do Spring, uma senha errada em
     * conta suspensa responderia "conta bloqueada", e qualquer um descobriria a situação da conta.
     */
    @Bean
    AuthenticationManager gerenciadorAutenticacao(UserDetailsService contas, PasswordEncoder senhas) {
        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(contas);
        provedor.setPasswordEncoder(senhas);
        provedor.setPreAuthenticationChecks(conta -> { });
        provedor.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provedor);
    }

    @Bean
    SecretKey chaveJwt(PropriedadesSeguranca propriedades) {
        String segredo = propriedades.jwtSegredo();
        byte[] bytes;
        if (segredo == null || segredo.isBlank()) {
            log.warn("JWT_SECRET não definido: usando uma chave aleatória. Os JWTs emitidos deixam de valer "
                    + "quando a aplicação reinicia, e o frontend renova a sessão pelo refresh token.");
            bytes = new byte[BYTES_MINIMOS_CHAVE];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = Base64.getDecoder().decode(segredo.strip());
            if (bytes.length < BYTES_MINIMOS_CHAVE) {
                throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 256 bits (32 bytes em Base64)");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder codificadorJwt(SecretKey chaveJwt) {
        return NimbusJwtEncoder.withSecretKey(chaveJwt).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder decodificadorJwt(SecretKey chaveJwt) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(chaveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenAcessoService.EMISSOR));
        return decodificador;
    }
}
