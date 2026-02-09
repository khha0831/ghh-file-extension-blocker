package com.ghh.blocker.service;

import com.ghh.blocker.domain.BlockedExtension;
import com.ghh.blocker.domain.ExtensionType;
import com.ghh.blocker.dto.ExtensionDto;
import com.ghh.blocker.exception.BlockedExtensionException;
import com.ghh.blocker.repository.BlockedExtensionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionService {

    private final BlockedExtensionRepository repository;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.custom-extension-limit:200}")
    private int customExtensionLimit;

    private static final List<String> FIXED_EXTENSIONS =
            List.of("bat", "cmd", "com", "cpl", "exe", "scr", "js");

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]+$");

    // ===== 초기화 =====

    @PostConstruct
    public void init() {
        transactionTemplate.execute(status -> {
            for (String ext : FIXED_EXTENSIONS) {
                if (!repository.existsByExtension(ext)) {
                    repository.save(BlockedExtension.builder()
                            .extension(ext)
                            .type(ExtensionType.FIXED)
                            .blocked(false)
                            .build());
                    log.info("고정 확장자 초기화: {}", ext);
                }
            }
            return null;
        });
    }

    // ===== 고정 확장자 (읽기: 락 불필요) =====

    @Transactional(readOnly = true)
    public List<ExtensionDto.FixedResponse> getFixedExtensions() {
        return repository.findByTypeOrderByCreatedAtDesc(ExtensionType.FIXED).stream()
                .map(ExtensionDto.FixedResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 고정 확장자 토글 (UPDATE)
     * - @Version(낙관적 락)이 Entity에 있으므로 동시 UPDATE 충돌 감지
     * - UPDATE는 synchronized 없이 @Version만으로 충분
     */
    @Transactional
    public ExtensionDto.FixedResponse updateFixedExtension(String extension, boolean blocked) {
        BlockedExtension entity = repository.findByExtension(extension.toLowerCase())
                .orElseThrow(() -> new BlockedExtensionException("존재하지 않는 고정 확장자입니다: " + extension));

        if (entity.getType() != ExtensionType.FIXED) {
            throw new BlockedExtensionException("고정 확장자가 아닙니다: " + extension);
        }

        entity.updateBlocked(blocked);
        return ExtensionDto.FixedResponse.from(entity);
    }

    @Transactional
    public int bulkUpdateFixed(boolean blocked) {
        return repository.bulkUpdateBlockedByType(ExtensionType.FIXED, blocked);
    }

    // ===== 커스텀 확장자 (쓰기: synchronized + TransactionTemplate) =====

    @Transactional(readOnly = true)
    public List<ExtensionDto.CustomResponse> getCustomExtensions() {
        return repository.findByTypeOrderByCreatedAtDesc(ExtensionType.CUSTOM).stream()
                .map(ExtensionDto.CustomResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 커스텀 확장자 추가
     *
     * 동시성 흐름:
     *   Thread A: synchronized 진입 → TX 시작 → count=199 → save → TX 커밋(200) → synchronized 해제
     *   Thread B: ⏳ 대기...         → 진입   → TX 시작 → count=200 → 거부 ✅
     *
     * 방어 레이어:
     *   1층: synchronized → 단일 JVM 내 직렬화 (count 조회 ~ 커밋 원자적)
     *   2층: TransactionTemplate → 커밋 시점을 락 내부로 강제
     *   3층: DB Unique Constraint → 최종 방어선
     */
    public synchronized List<ExtensionDto.CustomResponse> addCustomExtensions(String extensionsInput) {
        return transactionTemplate.execute(status -> {
            String[] parts = extensionsInput.split(",");
            List<String> toAdd = Arrays.stream(parts)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (toAdd.isEmpty()) {
                throw new BlockedExtensionException("추가할 확장자를 입력해주세요.");
            }

            long currentCount = repository.countByType(ExtensionType.CUSTOM);
            if (currentCount + toAdd.size() > customExtensionLimit) {
                throw new BlockedExtensionException(
                        String.format("커스텀 확장자는 최대 %d개까지 등록 가능합니다. (현재: %d개, 추가 요청: %d개)",
                                customExtensionLimit, currentCount, toAdd.size()));
            }

            List<ExtensionDto.CustomResponse> results = new ArrayList<>();

            for (String ext : toAdd) {
                validateExtension(ext);

                if (FIXED_EXTENSIONS.contains(ext)) {
                    throw new BlockedExtensionException(
                            "'" + ext + "'는 고정 확장자입니다. 상단의 체크박스를 이용하세요.");
                }

                if (repository.existsByExtension(ext)) {
                    throw new BlockedExtensionException("이미 등록된 확장자입니다: " + ext);
                }

                try {
                    BlockedExtension entity = repository.saveAndFlush(BlockedExtension.builder()
                            .extension(ext)
                            .type(ExtensionType.CUSTOM)
                            .blocked(true)
                            .build());
                    results.add(ExtensionDto.CustomResponse.from(entity));
                } catch (DataIntegrityViolationException e) {
                    log.warn("DB Unique 제약 위반: {}", ext);
                    throw new BlockedExtensionException("이미 등록된 확장자입니다: " + ext);
                }
            }

            log.info("커스텀 확장자 {}개 추가됨: {}", results.size(), toAdd);
            return results;
        });
    }

    public synchronized void deleteCustomExtension(Long id) {
        transactionTemplate.execute(status -> {
            BlockedExtension entity = repository.findById(id)
                    .orElseThrow(() -> new BlockedExtensionException("존재하지 않는 확장자입니다."));

            if (entity.getType() != ExtensionType.CUSTOM) {
                throw new BlockedExtensionException("커스텀 확장자만 삭제할 수 있습니다.");
            }

            repository.delete(entity);
            log.info("커스텀 확장자 삭제: {}", entity.getExtension());
            return null;
        });
    }

    public synchronized int deleteAllCustomExtensions() {
        return transactionTemplate.execute(status ->
                repository.deleteAllByType(ExtensionType.CUSTOM));
    }

    // ===== 초기화 / 테스트 =====

    public synchronized void resetAll() {
        transactionTemplate.execute(status -> {
            repository.deleteAllByType(ExtensionType.CUSTOM);
            repository.bulkUpdateBlockedByType(ExtensionType.FIXED, false);
            log.info("전체 설정 초기화 완료");
            return null;
        });
    }

    public synchronized int generateTestData() {
        return transactionTemplate.execute(status -> {
            long currentCount = repository.countByType(ExtensionType.CUSTOM);
            int toGenerate = (int) (customExtensionLimit - currentCount);

            if (toGenerate <= 0) {
                throw new BlockedExtensionException("이미 최대 개수에 도달했습니다.");
            }

            int generated = 0;
            for (int i = 1; i <= 200 && generated < toGenerate; i++) {
                String ext = "test" + i;
                if (!repository.existsByExtension(ext)) {
                    try {
                        repository.saveAndFlush(BlockedExtension.builder()
                                .extension(ext)
                                .type(ExtensionType.CUSTOM)
                                .blocked(true)
                                .build());
                        generated++;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("테스트 데이터 중복: {}", ext);
                    }
                }
            }

            log.info("테스트 데이터 {}개 생성 완료", generated);
            return generated;
        });
    }

    // ===== 조회 (락 불필요) =====

    @Transactional(readOnly = true)
    public Set<String> getBlockedExtensionSet() {
        return repository.findByBlockedTrue().stream()
                .map(BlockedExtension::getExtension)
                .collect(Collectors.toSet());
    }

    // ===== Private =====

    private void validateExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BlockedExtensionException("확장자를 입력해주세요.");
        }
        if (extension.length() > 20) {
            throw new BlockedExtensionException("확장자는 최대 20자까지 입력 가능합니다: " + extension);
        }
        if (!EXTENSION_PATTERN.matcher(extension).matches()) {
            throw new BlockedExtensionException("확장자는 영문 소문자와 숫자만 허용됩니다: " + extension);
        }
    }

    public static List<String> getFixedExtensionList() {
        return FIXED_EXTENSIONS;
    }
}
