package io.kestra.core.preview;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilePreview {
    private String extension;

    private Type type;

    private Object content;

    @JsonInclude
    @Builder.Default
    private boolean truncated = false;

    public enum Type {
        TEXT,
        LIST,
        IMAGE,
        MARKDOWN,
        PDF
    }
}
