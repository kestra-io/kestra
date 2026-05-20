package io.kestra.webserver.utils.filepreview;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.serializers.FileSerde;

import lombok.Getter;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Getter
public class IonFileRender extends FileRender {
    IonFileRender(String extension, InputStream filestream, Integer maxLine) throws IOException {
        super(extension, maxLine);
        renderContent(filestream);

        this.type = Type.LIST;
    }

    private void renderContent(InputStream filestream) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(filestream, FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            this.truncated = FileSerde.read(inputStream, this.maxLine, throwConsumer(list::add));

            this.content = list;
        }
    }
}
