package io.kestra.webserver.utils.filepreview;

import io.kestra.core.serializers.FileSerde;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Getter
public class IonFileRender extends FileRender {
    IonFileRender(String extension, InputStream filestream, Integer maxLine) throws IOException {
        super(extension, maxLine);
        renderContent(filestream);

        this.type = Type.LIST;
    }

    private void renderContent(InputStream filestream) throws IOException {
        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(filestream))) {
            List<Object> list = new ArrayList<>();
            this.truncated = FileSerde.reader(inputStream, this.maxLine, throwConsumer(list::add));

            this.content = list;
        }
    }
}
