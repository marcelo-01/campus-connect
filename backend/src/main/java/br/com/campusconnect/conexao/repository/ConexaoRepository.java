package br.com.campusconnect.conexao.repository;

import br.com.campusconnect.conexao.model.Conexao;
import br.com.campusconnect.conexao.model.StatusConexao;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConexaoRepository extends JpaRepository<Conexao, UUID> {

    /** O usuário pode estar em qualquer um dos dois lados do par. */
    @Query("""
            select count(c) from Conexao c
            where c.status = :status and (c.usuarioMenorId = :usuarioId or c.usuarioMaiorId = :usuarioId)
            """)
    long contarDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("status") StatusConexao status);
}
