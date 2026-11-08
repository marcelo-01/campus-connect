package br.com.campusconnect.conexao.service;

import br.com.campusconnect.conexao.model.StatusConexao;
import br.com.campusconnect.conexao.repository.ConexaoRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConexaoService {

    private final ConexaoRepository conexoes;

    /** Só conta conexões aceitas; pedidos pendentes, recusados e bloqueios ficam de fora. */
    @Transactional(readOnly = true)
    public long contarConexoes(UUID usuarioId) {
        return conexoes.contarDoUsuario(usuarioId, StatusConexao.ACEITA);
    }
}
