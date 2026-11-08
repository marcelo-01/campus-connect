package br.com.campusconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Monólito modular: cada pacote direto daqui é um módulo, dividido em {@code api} (HTTP),
 * {@code service} (regras de negócio), {@code repository} (banco) e {@code model} (entidades e
 * enums). O {@code ArquiteturaModularTest} cobra as regras abaixo.
 *
 * <ul>
 *   <li>Só o {@code service} é visível para os outros módulos; o resto é privado.
 *   <li>Entre módulos a referência é pelo id, nunca por associação JPA. As FKs continuam no banco.
 *   <li>O {@code package-info.java} de cada módulo declara de quais outros ele pode depender.
 * </ul>
 */
@Modulithic(sharedModules = "comum")
@SpringBootApplication
public class CampusConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusConnectApplication.class, args);
    }

}
