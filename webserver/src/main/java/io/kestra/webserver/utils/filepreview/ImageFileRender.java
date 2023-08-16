package io.kestra.webserver.utils.filepreview;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class ImageFileRender extends FileRender {
    public String content;

    ImageFileRender(String extension, InputStream filestream) throws IOException {
        super(extension);
        renderContent(filestream);

        this.type = TYPE.image;
    }

    public void renderContent(InputStream fileStream) throws IOException {
        byte[] imageBytes = IOUtils.toByteArray(fileStream);
        this.content =  Base64.getEncoder().encodeToString(imageBytes);
    }
}
