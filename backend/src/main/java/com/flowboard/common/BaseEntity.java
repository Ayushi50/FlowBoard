package com.flowboard.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Persistable;

/**
 * Application-assigned UUID identity with id-based equality.
 * <p>
 * Implements {@link Persistable} so Spring Data calls {@code persist} (not {@code merge}) for new instances:
 * with a pre-assigned id it could otherwise not tell new from detached, and merge would return a different
 * managed copy and issue an extra SELECT.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean fresh = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        fresh = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return Objects.equals(id, ((BaseEntity) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
