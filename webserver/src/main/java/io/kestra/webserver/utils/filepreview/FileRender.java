package io.kestra.webserver.utils.filepreview;

import com.fasterxml.jackson.annotation.JsonInclude;

public abstract class FileRender {
    protected static int MAX_LINES = 100;

    public String extension;

    public Type type;

    public Object content;

    @JsonInclude
    public boolean truncated = false;

    FileRender(String extension) {
        this.extension = extension;
    }

    public enum Type {
        TEXT,
        LIST,
        IMAGE,
        MARKDOWN
    }
}
