package com.ghh.blocker.service;

import com.ghh.blocker.dto.FileUploadDto;
import com.ghh.blocker.exception.FileBlockedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final ExtensionService extensionService;
    private final Tika tika = new Tika();

    /**
     * 위험한 MIME Type → 확장자 매핑
     * .exe를 .jpg로 이름을 변경해도 MIME Type으로 탐지
     */
    private static final Map<String, Set<String>> DANGEROUS_MIME_TO_EXT = new HashMap<>();

    static {
        DANGEROUS_MIME_TO_EXT.put("application/x-msdownload", Set.of("exe", "com", "scr"));
        DANGEROUS_MIME_TO_EXT.put("application/x-dosexec", Set.of("exe", "com", "scr"));
        DANGEROUS_MIME_TO_EXT.put("application/x-executable", Set.of("exe"));
        DANGEROUS_MIME_TO_EXT.put("application/x-msdos-program", Set.of("exe", "com", "bat", "cmd"));
        DANGEROUS_MIME_TO_EXT.put("application/x-bat", Set.of("bat"));
        DANGEROUS_MIME_TO_EXT.put("application/x-msdos-batch", Set.of("bat", "cmd"));
        DANGEROUS_MIME_TO_EXT.put("application/x-cpl", Set.of("cpl"));
        DANGEROUS_MIME_TO_EXT.put("text/javascript", Set.of("js"));
        DANGEROUS_MIME_TO_EXT.put("application/javascript", Set.of("js"));
    }

    /**
     * 다중 파일 업로드 - All or Nothing 트랜잭션
     */
    public FileUploadDto.Response uploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new FileBlockedException("업로드할 파일이 없습니다.");
        }

        Set<String> blockedSet = extensionService.getBlockedExtensionSet();
        List<String> blockedFileNames = new ArrayList<>();

        // 1단계: 전체 파일 검증 (All or Nothing)
        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;

            String extension = extractExtension(originalName);

            // 1차 검증: 확장자 문자열 비교
            if (extension != null && blockedSet.contains(extension.toLowerCase())) {
                blockedFileNames.add(originalName + " (확장자 차단: ." + extension + ")");
                continue;
            }

            // 2차 검증: Apache Tika MIME Type 검사
            try {
                String mimeBlockResult = checkMimeType(file, blockedSet);
                if (mimeBlockResult != null) {
                    blockedFileNames.add(originalName + " (MIME 위변조 탐지: " + mimeBlockResult + ")");
                }
            } catch (IOException e) {
                log.error("MIME Type 검사 실패: {}", originalName, e);
                blockedFileNames.add(originalName + " (파일 검사 오류)");
            }
        }

        // 차단된 파일이 하나라도 있으면 전체 거부
        if (!blockedFileNames.isEmpty()) {
            String detail = String.join("\n", blockedFileNames);
            throw new FileBlockedException(
                    "차단된 파일이 포함되어 전체 업로드가 거부되었습니다.\n\n" + detail);
        }

        // 2단계: 모든 파일 통과 시 성공 처리
        List<String> acceptedNames = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        log.info("파일 업로드 성공: {}개 파일", acceptedNames.size());

        return new FileUploadDto.Response(files.size(), acceptedNames.size(), acceptedNames);
    }

    /**
     * Apache Tika로 MIME Type 검사
     * 파일의 실제 바이너리(Magic Number)를 분석하여 위변조 탐지
     */
    private String checkMimeType(MultipartFile file, Set<String> blockedSet) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String detectedMime = tika.detect(is, file.getOriginalFilename());
            log.debug("파일 [{}] 감지된 MIME: {}", file.getOriginalFilename(), detectedMime);

            // MIME Type이 위험한 확장자에 매핑되는지 확인
            Set<String> mappedExtensions = DANGEROUS_MIME_TO_EXT.get(detectedMime);
            if (mappedExtensions != null) {
                for (String ext : mappedExtensions) {
                    if (blockedSet.contains(ext)) {
                        return "실제 타입: " + detectedMime + " → 차단 확장자: ." + ext;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 파일명에서 확장자 추출
     */
    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }
}
