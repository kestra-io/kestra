package io.kestra.assets.assets;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
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

        assertThat(asset).isInstanceOf(CustomAsset.class);
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
        String type = "EXTERNAL";
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

        assertThat(asset).isInstanceOf(ExternalAsset.class);
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
            DatasetAsset.ASSET_TYPE,
            displayName,
            description,
            system,
            location,
            format,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(DatasetAsset.class);
        DatasetAsset datasetAsset = (DatasetAsset) asset;
        assertThat(datasetAsset.getNamespace()).isEqualTo(namespace);
        assertThat(datasetAsset.getId()).isEqualTo(id);
        assertThat(datasetAsset.getDisplayName()).isEqualTo(displayName);
        assertThat(datasetAsset.getDescription()).isEqualTo(description);
        assertThat(datasetAsset.getSystem()).isEqualTo(system);
        assertThat(datasetAsset.getLocation()).isEqualTo(location);
        assertThat(datasetAsset.getFormat()).isEqualTo(format);
        assertThat(datasetAsset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
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
            FileAsset.ASSET_TYPE,
            displayName,
            description,
            system,
            path,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(FileAsset.class);
        FileAsset fileAsset = (FileAsset) asset;
        assertThat(fileAsset.getNamespace()).isEqualTo(namespace);
        assertThat(fileAsset.getId()).isEqualTo(id);
        assertThat(fileAsset.getDisplayName()).isEqualTo(displayName);
        assertThat(fileAsset.getDescription()).isEqualTo(description);
        assertThat(fileAsset.getSystem()).isEqualTo(system);
        assertThat(fileAsset.getPath()).isEqualTo(path);
        assertThat(fileAsset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
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
            TableAsset.ASSET_TYPE,
            displayName,
            description,
            system,
            database,
            schema,
            name,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(TableAsset.class);
        TableAsset tableAsset = (TableAsset) asset;
        assertThat(tableAsset.getNamespace()).isEqualTo(namespace);
        assertThat(tableAsset.getId()).isEqualTo(id);
        assertThat(tableAsset.getDisplayName()).isEqualTo(displayName);
        assertThat(tableAsset.getDescription()).isEqualTo(description);
        assertThat(tableAsset.getSystem()).isEqualTo(system);
        assertThat(tableAsset.getDatabase()).isEqualTo(database);
        assertThat(tableAsset.getSchema()).isEqualTo(schema);
        assertThat(tableAsset.getName()).isEqualTo(name);
        assertThat(tableAsset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
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
            VmAsset.ASSET_TYPE,
            displayName,
            description,
            provider,
            region,
            state,
            metadataKey,
            metadataValue
        ), Asset.class);

        assertThat(asset).isInstanceOf(VmAsset.class);
        VmAsset vmAsset = (VmAsset) asset;
        assertThat(vmAsset.getNamespace()).isEqualTo(namespace);
        assertThat(vmAsset.getId()).isEqualTo(id);
        assertThat(vmAsset.getDisplayName()).isEqualTo(displayName);
        assertThat(vmAsset.getDescription()).isEqualTo(description);
        assertThat(vmAsset.getProvider()).isEqualTo(provider);
        assertThat(vmAsset.getRegion()).isEqualTo(region);
        assertThat(vmAsset.getState()).isEqualTo(state);
        assertThat(vmAsset.getMetadata().get(metadataKey)).isEqualTo(metadataValue);
    }
}
