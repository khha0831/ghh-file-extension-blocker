package com.ghh.blocker.repository;

import com.ghh.blocker.domain.BlockedExtension;
import com.ghh.blocker.domain.ExtensionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockedExtensionRepository extends JpaRepository<BlockedExtension, Long> {

    Optional<BlockedExtension> findByExtension(String extension);

    boolean existsByExtension(String extension);

    List<BlockedExtension> findByTypeOrderByCreatedAtDesc(ExtensionType type);

    List<BlockedExtension> findByBlockedTrue();

    long countByType(ExtensionType type);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE BlockedExtension b SET b.blocked = :blocked, b.version = b.version + 1 WHERE b.type = :type")
    int bulkUpdateBlockedByType(@Param("type") ExtensionType type, @Param("blocked") boolean blocked);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BlockedExtension b WHERE b.type = :type")
    int deleteAllByType(@Param("type") ExtensionType type);
}
