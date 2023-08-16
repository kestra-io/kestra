package io.kestra.webserver.utils.filepreview;

public abstract class FileRender {
    protected int maxLines = 100;

    public String extension;

    public TYPE type;

    public Object content;

    FileRender(String extension) {
        this.extension = extension;
    }

    public void renderContent() {
        return;
    }

    public enum TYPE {
        text,
        list,
        image,
        md
    }
}
