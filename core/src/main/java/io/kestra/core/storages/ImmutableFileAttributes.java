package io.kestra.core.storages;

public record ImmutableFileAttributes(String fileName, long fileSize) implements FileAttributes {
    @Override
    public String getFileName() {
        return fileName();
    }

    @Override
    public long getLastModifiedTime() {
        return 0;
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public FileType getType() {
        return FileType.File;
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
