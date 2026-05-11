package com.ecommerce.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Persistable contract: an entity is considered new (INSERT) when it has
     * no createdAt timestamp. Loaded rows always have createdAt set by the
     * database, so re-saving them issues an UPDATE.
     */
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
