package br.com.campusconnect.instituicao.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "instituicao")
public class Instituicao extends EntidadeAuditada {

    @NonNull
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "sigla", length = 20)
    private String sigla;
}
