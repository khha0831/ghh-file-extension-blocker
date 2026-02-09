package com.ghh.blocker.service;

import com.ghh.blocker.domain.ExtensionType;
import com.ghh.blocker.exception.BlockedExtensionException;
import com.ghh.blocker.repository.BlockedExtensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("동시성 테스트")
class ConcurrencyTest {
    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private BlockedExtensionRepository repository;

    @BeforeEach
    void setUp() {
        extensionService.resetAll();
    }

    @Test
    @DisplayName("10개 스레드가 동시에 같은 확장자를 추가해도 1개만 등록된다")
    void concurrent_add_same_extension() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    extensionService.addCustomExtensions("py");
                    successCount.incrementAndGet();
                } catch (BlockedExtensionException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 정확히 1개만 성공, 나머지는 "이미 등록된" 예외
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        assertThat(repository.countByType(ExtensionType.CUSTOM)).isEqualTo(1);
    }

    @Test
    @DisplayName("10개 스레드가 동시에 서로 다른 확장자를 추가해도 200개를 초과하지 않는다")
    void concurrent_add_respects_200_limit() throws InterruptedException {
        // 먼저 195개 채우기
        extensionService.generateTestData();
        for (int i = 200; i > 195; i--) {
            Long id = repository.findByExtension("test" + i).get().getId();
            extensionService.deleteCustomExtension(id);
        }
        // 현재 195개

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 10개 스레드가 각각 다른 확장자 1개씩 추가 시도
        for (int i = 0; i < threadCount; i++) {
            final String ext = "ext" + i;
            executor.submit(() -> {
                try {
                    extensionService.addCustomExtensions(ext);
                    successCount.incrementAndGet();
                } catch (BlockedExtensionException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long totalCustom = repository.countByType(ExtensionType.CUSTOM);

        // 200개를 절대 초과하지 않는다
        assertThat(totalCustom).isLessThanOrEqualTo(200);
        // 5개만 성공해야 함 (195 + 5 = 200)
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("추가와 삭제가 동시에 실행되어도 데이터 정합성이 유지된다")
    void concurrent_add_and_delete() throws InterruptedException {
        extensionService.addCustomExtensions("target");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 5개 스레드: 추가, 5개 스레드: 전체 삭제
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    if (idx % 2 == 0) {
                        extensionService.addCustomExtensions("add" + idx);
                    } else {
                        extensionService.deleteAllCustomExtensions();
                    }
                } catch (Exception e) {
                    // 예외 무시 (동시성으로 인한 정상 예외)
                }finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // count 조회 결과와 실제 DB 데이터가 일치해야 한다
        long countByQuery = repository.countByType(ExtensionType.CUSTOM);
        long countByList = repository.findByTypeOrderByCreatedAtDesc(ExtensionType.CUSTOM).size();

        assertThat(countByQuery).isEqualTo(countByList);
    }

    @Test
    @DisplayName("동시에 테스트 데이터를 생성해도 200개를 초과하지 않는다")
    void concurrent_generate_test_data() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    extensionService.generateTestData();
                } catch (Exception e) {
                    // 이미 최대 개수 도달 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long totalCustom = repository.countByType(ExtensionType.CUSTOM);
        assertThat(totalCustom).isLessThanOrEqualTo(200);
    }

}
