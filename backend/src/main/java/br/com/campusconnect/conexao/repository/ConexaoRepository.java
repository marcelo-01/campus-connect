package br.com.campusconnect.conexao.repository;

import br.com.campusconnect.conexao.model.Conexao;
import br.com.campusconnect.conexao.model.StatusConexao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * O par chega sempre ordenado ({@code menor < maior}, na ordem do PostgreSQL).
 *
 * <p>Cada escrita é uma instrução só, com a condição no {@code WHERE}. Quando duas requisições disputam a mesma
 * linha, a segunda espera o lock e confere a condição de novo sobre a versão nova: por isso cancelar um pedido que
 * acabou de ser aceito não desfaz a conexão. As datas vêm do relógio do banco, como o {@code solicitado_em}.
 */
public interface ConexaoRepository extends JpaRepository<Conexao, UUID> {

    /** Os registros em que o usuário está de qualquer um dos dois lados. */
    @Query("select c from Conexao c where c.usuarioMenorId = :usuarioId or c.usuarioMaiorId = :usuarioId")
    List<Conexao> todasDoUsuario(@Param("usuarioId") UUID usuarioId);

    /** Só as colunas, sem a entidade: depois de uma escrita em massa, uma entidade já carregada estaria velha. */
    @Query("""
            select c.status as status, c.solicitanteId as solicitanteId from Conexao c
            where c.usuarioMenorId = :menor and c.usuarioMaiorId = :maior
            """)
    Optional<EstadoDoPar> estadoDoPar(@Param("menor") UUID menor, @Param("maior") UUID maior);

    /** Cria o pedido. Devolve 0, sem fazer nada, se o par já tem um registro. */
    @Modifying
    @Query(value = """
            insert into conexao (usuario_menor_id, usuario_maior_id, solicitante_id, status)
            values (:menor, :maior, :solicitante, 'PENDENTE')
            on conflict (usuario_menor_id, usuario_maior_id) do nothing
            """, nativeQuery = true)
    int pedir(@Param("menor") UUID menor, @Param("maior") UUID maior, @Param("solicitante") UUID solicitante);

    /** O pedido do {@code solicitante}, pendente ou recusado em silêncio, vira conexão. */
    @Modifying
    @Query(value = """
            update conexao set status = 'ACEITA', respondido_em = now()
            where usuario_menor_id = :menor and usuario_maior_id = :maior
              and solicitante_id = :solicitante and status in ('PENDENTE', 'RECUSADA')
            """, nativeQuery = true)
    int aceitarPedidoDe(@Param("menor") UUID menor, @Param("maior") UUID maior,
            @Param("solicitante") UUID solicitante);

    @Modifying
    @Query(value = """
            update conexao set status = 'RECUSADA', respondido_em = now()
            where usuario_menor_id = :menor and usuario_maior_id = :maior
              and solicitante_id = :solicitante and status = 'PENDENTE'
            """, nativeQuery = true)
    int recusarPedidoDe(@Param("menor") UUID menor, @Param("maior") UUID maior,
            @Param("solicitante") UUID solicitante);

    /** Retira o pedido, mesmo o recusado em silêncio. Uma conexão já aceita fica. */
    @Modifying
    @Query(value = """
            delete from conexao
            where usuario_menor_id = :menor and usuario_maior_id = :maior
              and solicitante_id = :solicitante and status in ('PENDENTE', 'RECUSADA')
            """, nativeQuery = true)
    int cancelarPedidoDe(@Param("menor") UUID menor, @Param("maior") UUID maior,
            @Param("solicitante") UUID solicitante);

    /** Desfaz só a conexão aceita: um pedido no meio do caminho fica. */
    @Modifying
    @Query(value = """
            delete from conexao
            where usuario_menor_id = :menor and usuario_maior_id = :maior and status = 'ACEITA'
            """, nativeQuery = true)
    int desfazer(@Param("menor") UUID menor, @Param("maior") UUID maior);

    interface EstadoDoPar {

        StatusConexao getStatus();

        UUID getSolicitanteId();
    }
}
