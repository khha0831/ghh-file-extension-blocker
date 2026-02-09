package com.ghh.blocker.controller;

import com.ghh.blocker.repository.BlockedExtensionRepository;
import com.ghh.blocker.service.ExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ExtensionApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private BlockedExtensionRepository repository;

    @BeforeEach
    void setUp() {
        extensionService.resetAll();
    }

    // ===== 고정 확장자 API =====

    @Nested
    @DisplayName("GET /api/extensions/fixed")
    class GetFixed {

        @Test
        @DisplayName("고정 확장자 7개를 조회한다")
        void get_fixed() throws Exception {
            mockMvc.perform(get("/api/extensions/fixed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(7));
        }
    }

    @Nested
    @DisplayName("PATCH /api/extensions/fixed")
    class UpdateFixed {

        @Test
        @DisplayName("고정 확장자를 차단한다")
        void update_fixed_blocked() throws Exception {
            mockMvc.perform(patch("/api/extensions/fixed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extension\": \"exe\", \"blocked\": true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.extension").value("exe"))
                    .andExpect(jsonPath("$.data.blocked").value(true));
        }

        @Test
        @DisplayName("존재하지 않는 확장자는 400 에러를 반환한다")
        void update_nonexistent_returns_400() throws Exception {
            mockMvc.perform(patch("/api/extensions/fixed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extension\": \"xyz\", \"blocked\": true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("빈 확장자는 validation 에러를 반환한다")
        void update_empty_returns_400() throws Exception {
            mockMvc.perform(patch("/api/extensions/fixed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extension\": \"\", \"blocked\": true}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /api/extensions/fixed/bulk")
    class BulkUpdateFixed {

        @Test
        @DisplayName("전체 선택하면 7개가 업데이트된다")
        void bulk_update_all() throws Exception {
            mockMvc.perform(patch("/api/extensions/fixed/bulk?blocked=true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(7));
        }
    }

    // ===== 커스텀 확장자 API =====

    @Nested
    @DisplayName("POST /api/extensions/custom")
    class AddCustom {

        @Test
        @DisplayName("커스텀 확장자를 추가한다")
        void add_custom() throws Exception {
            mockMvc.perform(post("/api/extensions/custom")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensions\": \"py\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].extension").value("py"));
        }

        @Test
        @DisplayName("여러 확장자를 쉼표로 구분하여 추가한다")
        void add_multiple_custom() throws Exception {
            mockMvc.perform(post("/api/extensions/custom")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensions\": \"py, java, cpp\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3));
        }

        @Test
        @DisplayName("이미 등록된 확장자는 400 에러를 반환한다")
        void add_duplicate_returns_400() throws Exception {
            extensionService.addCustomExtensions("py");

            mockMvc.perform(post("/api/extensions/custom")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensions\": \"py\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("이미 등록된 확장자입니다: py"));
        }

        @Test
        @DisplayName("고정 확장자 이름은 400 에러를 반환한다")
        void add_fixed_name_returns_400() throws Exception {
            mockMvc.perform(post("/api/extensions/custom")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensions\": \"exe\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("고정 확장자")));
        }
    }

    @Nested
    @DisplayName("GET /api/extensions/custom")
    class GetCustom {

        @Test
        @DisplayName("커스텀 확장자가 없으면 빈 리스트를 반환한다")
        void get_empty_custom() throws Exception {
            mockMvc.perform(get("/api/extensions/custom"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("추가한 커스텀 확장자가 조회된다")
        void get_custom_after_add() throws Exception {
            extensionService.addCustomExtensions("py, java");

            mockMvc.perform(get("/api/extensions/custom"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    @Nested
    @DisplayName("DELETE /api/extensions/custom")
    class DeleteCustom {

        @Test
        @DisplayName("커스텀 확장자를 개별 삭제한다")
        void delete_single() throws Exception {
            extensionService.addCustomExtensions("py");
            Long id = repository.findByExtension("py").get().getId();

            mockMvc.perform(delete("/api/extensions/custom/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("커스텀 확장자를 전체 삭제한다")
        void delete_all() throws Exception {
            extensionService.addCustomExtensions("py, java, cpp");

            mockMvc.perform(delete("/api/extensions/custom"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(3));
        }
    }

    // ===== 파일 업로드 API =====

    @Nested
    @DisplayName("POST /api/extensions/upload")
    class Upload {

        @Test
        @DisplayName("차단되지 않은 파일은 업로드 성공한다")
        void upload_allowed_file() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "document.pdf", "application/pdf", "dummy".getBytes());

            mockMvc.perform(multipart("/api/extensions/upload").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.acceptedFiles").value(1));
        }

        @Test
        @DisplayName("차단된 확장자 파일은 업로드 거부된다")
        void upload_blocked_file() throws Exception {
            extensionService.updateFixedExtension("exe", true);

            MockMultipartFile file = new MockMultipartFile(
                    "files", "malware.exe", "application/octet-stream", "dummy".getBytes());

            mockMvc.perform(multipart("/api/extensions/upload").file(file))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("차단")));
        }

        @Test
        @DisplayName("여러 파일 중 하나라도 차단이면 전체 거부된다")
        void upload_partial_blocked_rejects_all() throws Exception {
            extensionService.updateFixedExtension("exe", true);

            MockMultipartFile good = new MockMultipartFile(
                    "files", "doc.pdf", "application/pdf", "dummy".getBytes());
            MockMultipartFile bad = new MockMultipartFile(
                    "files", "virus.exe", "application/octet-stream", "dummy".getBytes());

            mockMvc.perform(multipart("/api/extensions/upload").file(good).file(bad))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("커스텀 차단 확장자도 거부된다")
        void upload_custom_blocked_file() throws Exception {
            extensionService.addCustomExtensions("py");

            MockMultipartFile file = new MockMultipartFile(
                    "files", "script.py", "text/plain", "print('hello')".getBytes());

            mockMvc.perform(multipart("/api/extensions/upload").file(file))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== 초기화 / 테스트 데이터 API =====

    @Nested
    @DisplayName("기타 API")
    class EtcApi {

        @Test
        @DisplayName("설정 초기화가 정상 동작한다")
        void reset_all() throws Exception {
            extensionService.addCustomExtensions("py");
            extensionService.updateFixedExtension("exe", true);

            mockMvc.perform(post("/api/extensions/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("테스트 데이터를 생성한다")
        void generate_test_data() throws Exception {
            mockMvc.perform(post("/api/extensions/test-data"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(200));
        }
    }
}
