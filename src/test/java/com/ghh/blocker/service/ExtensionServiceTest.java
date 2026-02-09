package com.ghh.blocker.service;

import com.ghh.blocker.domain.ExtensionType;
import com.ghh.blocker.dto.ExtensionDto;
import com.ghh.blocker.exception.BlockedExtensionException;
import com.ghh.blocker.repository.BlockedExtensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
public class ExtensionServiceTest {

    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private BlockedExtensionRepository repository;

    @BeforeEach
    void setUp() {
        // 커스텀 확장자 초기화, 고정 확장자 전체 해제
        extensionService.resetAll();
    }

    // ===== 고정 확장자 =====

    @Nested
    @DisplayName("고정 확장자")
    class FixedExtensionTest {

        @Test
        @DisplayName("초기화 시 7개 고정 확장자가 등록된다")
        void init_creates_7_fixed_extensions() {
            List<ExtensionDto.FixedResponse> result = extensionService.getFixedExtensions();

            assertThat(result).hasSize(7);
            assertThat(result).extracting("extension")
                    .containsExactlyInAnyOrder("bat", "cmd", "com", "cpl", "exe", "scr", "js");
        }

        @Test
        @DisplayName("고정 확장자 초기값은 모두 blocked=false이다")
        void init_all_fixed_unblocked() {
            List<ExtensionDto.FixedResponse> result = extensionService.getFixedExtensions();

            assertThat(result).allMatch(r -> !r.blocked());
        }

        @Test
        @DisplayName("고정 확장자를 차단할 수 있다")
        void toggle_fixed_to_blocked() {
            ExtensionDto.FixedResponse result = extensionService.updateFixedExtension("exe", true);

            assertThat(result.extension()).isEqualTo("exe");
            assertThat(result.blocked()).isTrue();
        }

        @Test
        @DisplayName("고정 확장자를 해제할 수 있다")
        void toggle_fixed_to_unblocked() {
            extensionService.updateFixedExtension("exe", true);
            ExtensionDto.FixedResponse result = extensionService.updateFixedExtension("exe", false);

            assertThat(result.blocked()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 확장자를 토글하면 예외가 발생한다")
        void toggle_nonexistent_throws() {
            assertThatThrownBy(() -> extensionService.updateFixedExtension("xyz", true))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("존재하지 않는");
        }

        @Test
        @DisplayName("전체 선택하면 7개 모두 blocked=true가 된다")
        void bulk_update_all_blocked() {
            int count = extensionService.bulkUpdateFixed(true);

            assertThat(count).isEqualTo(7);

            Set<String> blockedSet = extensionService.getBlockedExtensionSet();
            assertThat(blockedSet).containsExactlyInAnyOrder("bat", "cmd", "com", "cpl", "exe", "scr", "js");
        }

        @Test
        @DisplayName("전체 해제하면 차단 목록이 비어있다")
        void bulk_update_all_unblocked() {
            extensionService.bulkUpdateFixed(true);
            extensionService.bulkUpdateFixed(false);

            Set<String> blockedSet = extensionService.getBlockedExtensionSet();
            assertThat(blockedSet).isEmpty();
        }
    }

    // ===== 커스텀 확장자 =====

    @Nested
    @DisplayName("커스텀 확장자 추가")
    class CustomAddTest {

        @Test
        @DisplayName("커스텀 확장자 1개를 추가할 수 있다")
        void add_single_custom() {
            List<ExtensionDto.CustomResponse> result = extensionService.addCustomExtensions("py");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).extension()).isEqualTo("py");
        }

        @Test
        @DisplayName("쉼표로 구분하여 여러 개를 동시에 추가할 수 있다")
        void add_multiple_custom() {
            List<ExtensionDto.CustomResponse> result = extensionService.addCustomExtensions("py, java, cpp");

            assertThat(result).hasSize(3);
            assertThat(result).extracting("extension")
                    .containsExactlyInAnyOrder("py", "java", "cpp");
        }

        @Test
        @DisplayName("대문자 입력은 소문자로 변환된다")
        void add_uppercase_converts_to_lowercase() {
            List<ExtensionDto.CustomResponse> result = extensionService.addCustomExtensions("PY");

            assertThat(result.get(0).extension()).isEqualTo("py");
        }

        @Test
        @DisplayName("입력 내 중복은 자동 제거된다")
        void add_duplicate_input_deduplicated() {
            List<ExtensionDto.CustomResponse> result = extensionService.addCustomExtensions("py, py, py");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("추가된 커스텀 확장자는 blocked=true이다")
        void added_custom_is_blocked() {
            extensionService.addCustomExtensions("py");

            Set<String> blockedSet = extensionService.getBlockedExtensionSet();
            assertThat(blockedSet).contains("py");
        }

        @Test
        @DisplayName("빈 입력이면 예외가 발생한다")
        void add_empty_throws() {
            assertThatThrownBy(() -> extensionService.addCustomExtensions("   "))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("추가할 확장자를 입력");
        }

        @Test
        @DisplayName("이미 등록된 확장자를 추가하면 예외가 발생한다")
        void add_duplicate_throws() {
            extensionService.addCustomExtensions("py");

            assertThatThrownBy(() -> extensionService.addCustomExtensions("py"))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("이미 등록된");
        }

        @Test
        @DisplayName("고정 확장자와 같은 이름은 추가할 수 없다")
        void add_fixed_name_throws() {
            assertThatThrownBy(() -> extensionService.addCustomExtensions("exe"))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("고정 확장자");
        }

        @Test
        @DisplayName("20자를 초과하는 확장자는 추가할 수 없다")
        void add_too_long_throws() {
            String longExt = "a".repeat(21);

            assertThatThrownBy(() -> extensionService.addCustomExtensions(longExt))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("최대 20자");
        }

        @Test
        @DisplayName("특수문자가 포함된 확장자는 추가할 수 없다")
        void add_special_char_throws() {
            assertThatThrownBy(() -> extensionService.addCustomExtensions("test!"))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("영문 소문자와 숫자만");
        }
    }

    // ===== 커스텀 확장자 200개 제한 =====

    @Nested
    @DisplayName("커스텀 확장자 200개 제한")
    class CustomLimitTest {

        @Test
        @DisplayName("200개까지 등록할 수 있다")
        void add_up_to_200() {
            int count = extensionService.generateTestData();

            assertThat(count).isEqualTo(200);
            assertThat(repository.countByType(ExtensionType.CUSTOM)).isEqualTo(200);
        }

        @Test
        @DisplayName("200개 초과 시 예외가 발생한다")
        void add_over_200_throws() {
            extensionService.generateTestData();

            assertThatThrownBy(() -> extensionService.addCustomExtensions("overflow"))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("최대 200개");
        }

        @Test
        @DisplayName("현재 199개일 때 2개를 추가하면 예외가 발생한다")
        void add_exceeding_remaining_throws() {
            extensionService.generateTestData();
            extensionService.deleteCustomExtension(
                    repository.findByExtension("test1").get().getId());
            // 현재 199개

            assertThatThrownBy(() -> extensionService.addCustomExtensions("aaa, bbb"))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("최대 200개");
        }
    }

    // ===== 커스텀 확장자 삭제 =====

    @Nested
    @DisplayName("커스텀 확장자 삭제")
    class CustomDeleteTest {

        @Test
        @DisplayName("커스텀 확장자를 삭제할 수 있다")
        void delete_custom() {
            extensionService.addCustomExtensions("py");
            Long id = repository.findByExtension("py").get().getId();

            extensionService.deleteCustomExtension(id);

            assertThat(repository.existsByExtension("py")).isFalse();
        }

        @Test
        @DisplayName("고정 확장자는 삭제할 수 없다")
        void delete_fixed_throws() {
            Long exeId = repository.findByExtension("exe").get().getId();

            assertThatThrownBy(() -> extensionService.deleteCustomExtension(exeId))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("커스텀 확장자만");
        }

        @Test
        @DisplayName("존재하지 않는 ID 삭제 시 예외가 발생한다")
        void delete_nonexistent_throws() {
            assertThatThrownBy(() -> extensionService.deleteCustomExtension(99999L))
                    .isInstanceOf(BlockedExtensionException.class)
                    .hasMessageContaining("존재하지 않는");
        }

        @Test
        @DisplayName("전체 삭제하면 커스텀 확장자가 모두 삭제된다")
        void delete_all_custom() {
            extensionService.addCustomExtensions("py, java, cpp");

            int count = extensionService.deleteAllCustomExtensions();

            assertThat(count).isEqualTo(3);
            assertThat(repository.countByType(ExtensionType.CUSTOM)).isZero();
        }

        @Test
        @DisplayName("전체 삭제해도 고정 확장자는 유지된다")
        void delete_all_keeps_fixed() {
            extensionService.addCustomExtensions("py");
            extensionService.deleteAllCustomExtensions();

            assertThat(repository.countByType(ExtensionType.FIXED)).isEqualTo(7);
        }
    }

    // ===== 초기화 =====

    @Nested
    @DisplayName("전체 초기화")
    class ResetTest {

        @Test
        @DisplayName("초기화하면 커스텀 전체 삭제 + 고정 전체 해제된다")
        void reset_clears_all() {
            extensionService.addCustomExtensions("py, java");
            extensionService.updateFixedExtension("exe", true);

            extensionService.resetAll();

            assertThat(repository.countByType(ExtensionType.CUSTOM)).isZero();
            assertThat(extensionService.getBlockedExtensionSet()).isEmpty();
        }
    }

    // ===== 차단 목록 조회 =====

    @Nested
    @DisplayName("차단 목록 조회")
    class BlockedSetTest {

        @Test
        @DisplayName("차단된 고정 + 커스텀 확장자가 모두 조회된다")
        void blocked_set_includes_both_types() {
            extensionService.updateFixedExtension("exe", true);
            extensionService.addCustomExtensions("py");

            Set<String> blockedSet = extensionService.getBlockedExtensionSet();

            assertThat(blockedSet).contains("exe", "py");
        }

        @Test
        @DisplayName("해제된 확장자는 조회되지 않는다")
        void blocked_set_excludes_unblocked() {
            extensionService.updateFixedExtension("exe", true);
            extensionService.updateFixedExtension("exe", false);

            Set<String> blockedSet = extensionService.getBlockedExtensionSet();

            assertThat(blockedSet).doesNotContain("exe");
        }
    }
}
