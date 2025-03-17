package io.kestra.core.utils;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.NamespaceFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NamespaceFilesUtils {

    public static void loadNamespaceFiles(RunContext runContext, NamespaceFiles namespaceFiles)
        throws IllegalVariableEvaluationException, IOException {
        List<String> include = runContext.render(namespaceFiles.getInclude()).asList(String.class);
        List<String> exclude = runContext.render(namespaceFiles.getExclude()).asList(String.class);

        Map<String, NamespaceFile> namespaceFileMap = new HashMap<>();
        for (String namespace : runContext.render(namespaceFiles.getNamespaces()).asList(String.class)) {
            List<NamespaceFile> files = runContext.storage()
                .namespace(namespace)
                .findAllFilesMatching(include, exclude);
            for (NamespaceFile file : files) {
                namespaceFileMap.put(file.storagePath().toFile().getName(), file);
            }
        }

        List<NamespaceFile> matchedNamespaceFiles = new ArrayList<>(namespaceFileMap.values());
        matchedNamespaceFiles.forEach(Rethrow.throwConsumer(namespaceFile -> {
            InputStream content = runContext.storage().getFile(namespaceFile.uri());
            runContext.workingDir().putFile(Path.of(namespaceFile.path()), content);
        }));
    }
}
