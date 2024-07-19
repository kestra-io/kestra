package io.kestra.webserver.utils.filepreview;

import io.github.pixee.security.BoundedLineReader;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Getter
public class DefaultFileRender extends FileRender {
    DefaultFileRender(String extension, InputStream filestream, Charset charset, Integer maxLine) throws IOException {
        super(extension, maxLine);
        renderContent(filestream, charset);

        this.type = Type.TEXT;
    }

    DefaultFileRender(String extension, InputStream filestream, Charset charset, Type type, Integer maxLine) throws IOException {
        super(extension, maxLine);
        renderContent(filestream, charset);

        this.type = type;
    }

    private void renderContent(InputStream fileStream, Charset charset) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, charset));
        String line = BoundedLineReader.readLine(reader, 5_000_000);
        int lineCount = 0;

        StringBuilder contentBuilder = new StringBuilder();

        while (line != null && lineCount < this.maxLine) {
            contentBuilder.append(line);
            lineCount++;
            if ((line = BoundedLineReader.readLine(reader, 5_000_000)) != null) {
                contentBuilder.append("\n");

                if(lineCount == this.maxLine) {
                    truncated = true;
                }
            }
        }

        this.content = truncateStringSize(contentBuilder.toString());
    }

    private String truncateStringSize(String content) {
        // Equivalent to 2MB
        int maxSizeInBytes = 2097152;
        byte[] inputBytes = content.getBytes();

        if (inputBytes.length <= maxSizeInBytes) {

            return content;
        }
        truncated = true;
        byte[] truncatedBytes = new byte[maxSizeInBytes];
        System.arraycopy(inputBytes, 0, truncatedBytes, 0, maxSizeInBytes);

        return new String(truncatedBytes);
    }

}
