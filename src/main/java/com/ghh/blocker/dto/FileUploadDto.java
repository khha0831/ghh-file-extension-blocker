package com.ghh.blocker.dto;

import java.util.List;

public class FileUploadDto {

    private FileUploadDto() {}

    public record Response(
            int totalFiles,
            int acceptedFiles,
            List<String> acceptedFileNames
    ) {}
}
