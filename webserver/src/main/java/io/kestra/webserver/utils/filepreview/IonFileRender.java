package io.kestra.webserver.utils.filepreview;

import io.kestra.core.serializers.FileSerde;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public class IonFileRender extends FileRender {
    public List<Object> content;

    IonFileRender(String extension, InputStream filestream) throws IOException {
        super(extension);
        renderContent(filestream);

        this.type = Type.LIST;
    }

    private void renderContent(InputStream filestream) throws IOException {
        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(filestream))) {
            List<Object> list = new ArrayList<>();
            FileSerde.reader(inputStream, MAX_LINES, throwConsumer(row -> list.add(row)));

            this.content = list;
        }
    }
}
