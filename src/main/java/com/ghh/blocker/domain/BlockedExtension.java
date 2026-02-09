package com.ghh.blocker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocked_extension", uniqueConstraints = {
        @UniqueConstraint(columnNames = "extension")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockedExtension extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExtensionType type;

    @Column(nullable = false)
    private boolean blocked;

    /**
     * 낙관적 락 (Optimistic Lock)
     * - 용도: 고정 확장자 토글(UPDATE) 시 동시 수정 충돌 감지
     * - 한계: INSERT에는 무의미 (비교할 이전 버전이 없으므로)
     * - INSERT 동시성은 synchronized + TransactionTemplate + DB Unique로 해결
     */
    @Version
    private Long version;

    @Builder
    public BlockedExtension(String extension, ExtensionType type, boolean blocked) {
        this.extension = extension.toLowerCase().trim();
        this.type = type;
        this.blocked = blocked;
    }

    public void updateBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}
