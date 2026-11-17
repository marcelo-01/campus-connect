package br.com.campusconnect.conversa.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.conversa} do {@code application.yml}: o limite contra spam.
 *
 * @param mensagensPorMinuto quantas mensagens uma pessoa manda por minuto, somando todas as conversas
 * @param conversasNovasPorDia quantas conversas uma pessoa começa por dia; conta a conversa em que a primeira
 *     mensagem é dela
 */
@ConfigurationProperties("campusconnect.conversa")
record PropriedadesConversa(
        @DefaultValue("30") int mensagensPorMinuto,
        @DefaultValue("20") int conversasNovasPorDia) {
}
