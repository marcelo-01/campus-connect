package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CampusConnectApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    /** Subir o contexto já roda o Flyway e o {@code ddl-auto=validate} sobre todas as entidades. */
    @Test
    void aplicaAsMigrationsEValidaAsEntidades() {
        assertThat(contar("select count(*) from flyway_schema_history where success")).isEqualTo(15);
        assertThat(contar("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """)).isEqualTo(26);
    }

    @Test
    void nenhumaDataFicaSemFuso() {
        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'public' and data_type = 'timestamp without time zone'
                  and table_name <> 'flyway_schema_history'
                """, String.class)).isEmpty();
    }

    @Test
    void todaChaveEstrangeiraTemIndice() {
        assertThat(jdbc.queryForList("""
                select fk.conname from pg_constraint fk
                where fk.contype = 'f' and fk.connamespace = 'public'::regnamespace
                  and not exists (
                      select 1 from pg_index i
                      where i.indrelid = fk.conrelid and i.indkey[0] = fk.conkey[1]
                  )
                """, String.class)).isEmpty();
    }

    private Long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
