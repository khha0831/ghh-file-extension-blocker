package com.ghh.blocker.controller;

import com.ghh.blocker.dto.ApiResponse;
import com.ghh.blocker.dto.ExtensionDto;
import com.ghh.blocker.dto.FileUploadDto;
import com.ghh.blocker.service.ExtensionService;
import com.ghh.blocker.service.FileUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/extensions")
@RequiredArgsConstructor
public class ExtensionApiController {

    private final ExtensionService extensionService;
    private final FileUploadService fileUploadService;

    // ===== 고정 확장자 =====

    @GetMapping("/fixed")
    public ResponseEntity<ApiResponse<List<ExtensionDto.FixedResponse>>> getFixedExtensions() {
        return ResponseEntity.ok(ApiResponse.ok("조회 성공", extensionService.getFixedExtensions()));
    }

    @PatchMapping("/fixed")
    public ResponseEntity<ApiResponse<ExtensionDto.FixedResponse>> updateFixed(
            @Valid @RequestBody ExtensionDto.FixedUpdateRequest request) {
        var result = extensionService.updateFixedExtension(request.extension(), request.blocked());
        return ResponseEntity.ok(ApiResponse.ok("업데이트 성공", result));
    }

    @PatchMapping("/fixed/bulk")
    public ResponseEntity<ApiResponse<Integer>> bulkUpdateFixed(@RequestParam boolean blocked) {
        int count = extensionService.bulkUpdateFixed(blocked);
        String msg = blocked ? "전체 선택 완료 (" + count + "개)" : "전체 해제 완료 (" + count + "개)";
        return ResponseEntity.ok(ApiResponse.ok(msg, count));
    }

    // ===== 커스텀 확장자 =====

    @GetMapping("/custom")
    public ResponseEntity<ApiResponse<List<ExtensionDto.CustomResponse>>> getCustomExtensions() {
        return ResponseEntity.ok(ApiResponse.ok("조회 성공", extensionService.getCustomExtensions()));
    }

    @PostMapping("/custom")
    public ResponseEntity<ApiResponse<List<ExtensionDto.CustomResponse>>> addCustomExtensions(
            @Valid @RequestBody ExtensionDto.CustomAddRequest request) {
        var result = extensionService.addCustomExtensions(request.extensions());
        return ResponseEntity.ok(ApiResponse.ok(result.size() + "개 확장자가 추가되었습니다.", result));
    }

    @DeleteMapping("/custom/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomExtension(@PathVariable Long id) {
        extensionService.deleteCustomExtension(id);
        return ResponseEntity.ok(ApiResponse.ok("삭제 완료"));
    }

    @DeleteMapping("/custom")
    public ResponseEntity<ApiResponse<Integer>> deleteAllCustom() {
        int count = extensionService.deleteAllCustomExtensions();
        return ResponseEntity.ok(ApiResponse.ok("커스텀 확장자 " + count + "개 삭제 완료", count));
    }

    // ===== 초기화 =====

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetAll() {
        extensionService.resetAll();
        return ResponseEntity.ok(ApiResponse.ok("전체 설정이 초기화되었습니다."));
    }

    // ===== 테스트 데이터 =====

    @PostMapping("/test-data")
    public ResponseEntity<ApiResponse<Integer>> generateTestData() {
        int count = extensionService.generateTestData();
        return ResponseEntity.ok(ApiResponse.ok("테스트 데이터 " + count + "개 생성 완료", count));
    }

    // ===== 파일 업로드 =====

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileUploadDto.Response>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files) {
        var result = fileUploadService.uploadFiles(files);
        return ResponseEntity.ok(ApiResponse.ok("파일 업로드 성공!", result));
    }
}
