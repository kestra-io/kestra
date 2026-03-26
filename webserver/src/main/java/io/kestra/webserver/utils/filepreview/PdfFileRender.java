package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;

@Getter
public class PdfFileRender extends Base64Render {
    PdfFileRender(String extension, InputStream inputStream, Integer maxLine) throws IOException {
        super(extension, inputStream, maxLine);
        this.type = Type.PDF;
    }
}
