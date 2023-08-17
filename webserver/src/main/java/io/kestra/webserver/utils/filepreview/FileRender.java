package io.kestra.webserver.utils.filepreview;

public abstract class FileRender {
    protected static int MAX_LINES = 100;

    public String extension;

    public Type type;

    public Object content;

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
