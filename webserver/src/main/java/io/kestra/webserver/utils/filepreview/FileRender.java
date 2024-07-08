package io.kestra.webserver.utils.filepreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public abstract class FileRender {
    protected static int MAX_LINES = 100;

    public String extension;

    public Type type;

    public Object content;

    public Integer maxLine;

    @JsonInclude
    public boolean truncated = false;

    FileRender(String extension, Integer maxLine) {
        this.maxLine = maxLine;
        this.extension = extension;
    }

    public enum Type {
        TEXT,
        LIST,
        IMAGE,
        MARKDOWN,
        PDF
    }
}
