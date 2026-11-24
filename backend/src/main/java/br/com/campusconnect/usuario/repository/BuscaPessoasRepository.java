package br.com.campusconnect.usuario.repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A busca de pessoas pelo nome e pelo username, sem diferenciar acento nem maiúscula.
 *
 * <p>Cada palavra buscada precisa aparecer no nome ou no username, em qualquer ordem e mesmo pela metade: "ana souza"
 * acha "Ana Beatriz Souza", e "joao" acha "João". Os resultados vêm do que casa melhor para o que casa pior: primeiro
 * o username igual ao buscado, depois o nome ou o username que começa com a busca, depois o nome com uma palavra que
 * começa com a primeira palavra buscada, e por fim o resto. No empate, pela ordem do nome.
 *
 * <p>O {@code unaccent} na consulta impede o uso de índice no nome, e o banco lê a tabela inteira. Com as pessoas de uma
 * rede universitária, isso é rápido; com muito mais, cabe um índice de trigramas sobre o nome sem acento.
 *
 * <p>O SQL é montado só com trechos fixos daqui; o que vem de fora entra sempre como parâmetro.
 */
@Repository
@RequiredArgsConstructor
public class BuscaPessoasRepository {

    /** As mesmas contas de {@code Usuario.podeEntrar()}: as que aparecem para as outras pessoas. */
    private static final String PUBLICAS = """
            u.status = 'ATIVO' and u.anonimizado_em is null and u.email_verificado
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Os ids das pessoas que casam com as palavras, na ordem do que casa melhor.
     *
     * @param palavras em minúsculas, sem o {@code @} da frente e sem repetição; pelo menos uma
     */
    public List<UUID> buscar(List<String> palavras, int deslocamento, int limite) {
        if (palavras.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("select u.id from usuario u where ").append(PUBLICAS);
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        for (int i = 0; i < palavras.size(); i++) {
            sql.append(" and unaccent(u.nome_completo || ' ' || u.username) ilike unaccent(:palavra").append(i)
                    .append(") escape '\\'");
            parametros.addValue("palavra" + i, "%" + semCuringas(palavras.get(i)) + "%");
        }
        String busca = String.join(" ", palavras);
        sql.append("""
                 order by case
                     when u.username = :exato then 0
                     when u.username like :comeco escape '\\'
                       or unaccent(lower(u.nome_completo)) like unaccent(:comeco) escape '\\' then 1
                     when unaccent(lower(' ' || u.nome_completo)) like unaccent(:palavraNoNome) escape '\\' then 2
                     else 3
                   end, unaccent(lower(u.nome_completo)), u.id
                 offset :deslocamento limit :limite
                """);
        parametros.addValue("exato", busca)
                .addValue("comeco", semCuringas(busca) + "%")
                .addValue("palavraNoNome", "% " + semCuringas(palavras.getFirst()) + "%")
                .addValue("deslocamento", deslocamento)
                .addValue("limite", limite);
        return jdbc.queryForList(sql.toString(), parametros, UUID.class);
    }

    /** {@code %} e {@code _} valem como texto, e não como curinga do {@code like}. */
    private static String semCuringas(String texto) {
        return texto.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
