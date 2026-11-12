package br.com.campusconnect.publicacao.repository;

import br.com.campusconnect.publicacao.model.Comentario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {

    /** Comentários de primeiro nível, do mais antigo para o mais novo. */
    @Query("""
            select c from Comentario c
            where c.publicacao.id = :publicacaoId and c.comentarioPai is null
            order by c.criadoEm, c.id
            """)
    List<Comentario> principais(@Param("publicacaoId") UUID publicacaoId);

    /** Respostas aos comentários dados, do mais antigo para o mais novo. */
    @Query("select c from Comentario c where c.comentarioPai.id in :pais order by c.criadoEm, c.id")
    List<Comentario> respostasA(@Param("pais") Collection<UUID> pais);
}
