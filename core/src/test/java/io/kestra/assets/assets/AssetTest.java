package io.kestra.assets.assets;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.assets.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class AssetTest {
    @Test
    void custom() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String type = "MY_OWN_ASSET_TYPE";
        String displayName = "My own asset";
        String description = "This is my asset";
        String metadataKey = "owner";
        String metadataValue = "data-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            type,
            displayName,
            description,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(Custom.class);
        assertThat(asset.getNamespace()).isEqualTo(namespace);
        assertThat(asset.getId()).isEqualTo(id);
        assertThat(asset.getType()).isEqualTo(type);
        assertThat(asset.getDisplayName()).isEqualTo(displayName);
        assertThat(asset.getDescription()).isEqualTo(description);
        assertThat(asset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }

    @Test
    void external() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String type = External.ASSET_TYPE;
        String displayName = "External asset";
        String description = "This is an external asset";
        String metadataKey = "owner";
        String metadataValue = "external-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            type,
            displayName,
            description,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(External.class);
        assertThat(asset.getNamespace()).isEqualTo(namespace);
        assertThat(asset.getId()).isEqualTo(id);
        assertThat(asset.getType()).isEqualTo(type);
        assertThat(asset.getDisplayName()).isEqualTo(displayName);
        assertThat(asset.getDescription()).isEqualTo(description);
        assertThat(asset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }

    @Test
    void dataset() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String displayName = "My Dataset";
        String description = "This is my dataset";
        String system = "S3";
        String location = "s3://my-bucket/my-dataset";
        String format = "parquet";
        String metadataKey = "owner";
        String metadataValue = "data-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            system: %s
            location: %s
            format: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            Dataset.ASSET_TYPE,
            displayName,
            description,
            system,
            location,
            format,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(Dataset.class);
        Dataset dataset = (Dataset) asset;
        assertThat(dataset.getNamespace()).isEqualTo(namespace);
        assertThat(dataset.getId()).isEqualTo(id);
        assertThat(dataset.getDisplayName()).isEqualTo(displayName);
        assertThat(dataset.getDescription()).isEqualTo(description);
        assertThat(dataset.getSystem()).isEqualTo(system);
        assertThat(dataset.getLocation()).isEqualTo(location);
        assertThat(dataset.getFormat()).isEqualTo(format);
        assertThat(dataset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }

    @Test
    void file() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String displayName = "My File";
        String description = "This is my file";
        String system = "local";
        String path = "/data/my-file.txt";
        String metadataKey = "owner";
        String metadataValue = "file-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            system: %s
            path: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            File.ASSET_TYPE,
            displayName,
            description,
            system,
            path,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(File.class);
        File file = (File) asset;
        assertThat(file.getNamespace()).isEqualTo(namespace);
        assertThat(file.getId()).isEqualTo(id);
        assertThat(file.getDisplayName()).isEqualTo(displayName);
        assertThat(file.getDescription()).isEqualTo(description);
        assertThat(file.getSystem()).isEqualTo(system);
        assertThat(file.getPath()).isEqualTo(path);
        assertThat(file.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }

    @Test
    void table() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String displayName = "My Table";
        String description = "This is my table";
        String system = "postgres";
        String database = "mydb";
        String schema = "my_schema";
        String name = "mytable";
        String metadataKey = "owner";
        String metadataValue = "table-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            system: %s
            database: %s
            schema: %s
            name: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            Table.ASSET_TYPE,
            displayName,
            description,
            system,
            database,
            schema,
            name,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(Table.class);
        Table table = (Table) asset;
        assertThat(table.getNamespace()).isEqualTo(namespace);
        assertThat(table.getId()).isEqualTo(id);
        assertThat(table.getDisplayName()).isEqualTo(displayName);
        assertThat(table.getDescription()).isEqualTo(description);
        assertThat(table.getSystem()).isEqualTo(system);
        assertThat(table.getDatabase()).isEqualTo(database);
        assertThat(table.getSchema()).isEqualTo(schema);
        assertThat(table.getName()).isEqualTo(name);
        assertThat(table.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }

    @Test
    void vm() throws JsonProcessingException {
        String namespace = TestsUtils.randomNamespace();
        String id = TestsUtils.randomString();
        String displayName = "My VM";
        String description = "This is my vm";
        String provider = "aws";
        String region = "us-east-1";
        String state = "running";
        String metadataKey = "owner";
        String metadataValue = "vm-team";
        Asset asset = JacksonMapper.ofYaml().readValue("""
            namespace: %s
            id: %s
            type: %s
            displayName: %s
            description: %s
            provider: %s
            region: %s
            state: %s
            metadata:
                %s: %s""".formatted(
            namespace,
            id,
            VM.ASSET_TYPE,
            displayName,
            description,
            provider,
            region,
            state,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(VM.class);
        VM VM = (VM) asset;
        assertThat(VM.getNamespace()).isEqualTo(namespace);
        assertThat(VM.getId()).isEqualTo(id);
        assertThat(VM.getDisplayName()).isEqualTo(displayName);
        assertThat(VM.getDescription()).isEqualTo(description);
        assertThat(VM.getProvider()).isEqualTo(provider);
        assertThat(VM.getRegion()).isEqualTo(region);
        assertThat(VM.getState()).isEqualTo(state);
        assertThat(VM.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }
}
