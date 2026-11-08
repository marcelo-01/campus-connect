package br.com.campusconnect.comum;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

/**
 * Base das entidades com chave UUID gerada.
 *
 * <p>{@code equals} e {@code hashCode} são {@code final} de propósito: assim o proxy do Hibernate
 * não os intercepta, e comparar uma associação lazy não dispara SELECT nem falha fora da sessão.
 * Pelo mesmo motivo eles usam {@link #getId()} e nunca o campo diretamente.
 */
@Getter
@MappedSuperclass
public abstract class EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Override
    public final boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (outro == null || classeReal(this) != classeReal(outro)) {
            return false;
        }
        return getId() != null && getId().equals(((EntidadeBase) outro).getId());
    }

    @Override
    public final int hashCode() {
        // Constante por classe: o id só existe depois do persist e o hash não pode mudar.
        return classeReal(this).hashCode();
    }

    private static Class<?> classeReal(Object entidade) {
        LazyInitializer proxy = HibernateProxy.extractLazyInitializer(entidade);
        return proxy != null ? proxy.getPersistentClass() : entidade.getClass();
    }
}
