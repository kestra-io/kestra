package io.kestra.repository.elasticsearch;

import io.kestra.core.repositories.AbstracSettingRepositoryTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

class ElasticSearchSettingRepositoryTest extends AbstracSettingRepositoryTest {
    @Inject
    ElasticsearchSettingRepository elasticsearchSettingRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticsearchSettingRepository.initMapping();
    }
}
