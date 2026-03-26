package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.apache.commons.io.IOUtils;

public class Base64Render extends FileRender {

    Base64Render(String extension, InputStream inputStream, Integer maxLine) throws IOException {
        super(extension, maxLine);
        this.content = Base64.getEncoder().encodeToString(IOUtils.toByteArray(inputStream));
    }
}
