package io.kestra.repository.elasticsearch;

import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.reactivestreams.Publisher;

import java.io.IOException;
import javax.inject.Singleton;

import static io.micronaut.health.HealthStatus.DOWN;
import static io.micronaut.health.HealthStatus.UP;
import static java.util.Collections.emptyMap;
import static org.elasticsearch.cluster.health.ClusterHealthStatus.GREEN;
import static org.elasticsearch.cluster.health.ClusterHealthStatus.YELLOW;

/**
 * A {@link HealthIndicator} for Elasticsearch High Level REST client.
 * Mostly a copy from the micronaut one, since it don't work with yellow cluster for example
 */
@Requires(beans = HealthEndpoint.class)
@ElasticSearchRepositoryEnabled
@Singleton
@Slf4j
public class ElasticsearchHealthIndicator implements HealthIndicator {
    public static final String NAME = "elasticsearch";
    private final RestHighLevelClient esClient;

    /**
     * Constructor.
     *
     * @param esClient The Elasticsearch high level REST client.
     */
    public ElasticsearchHealthIndicator(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return (subscriber -> {
            try {
                esClient.cluster()
                    .healthAsync(
                        new ClusterHealthRequest(),
                        RequestOptions.DEFAULT,
                        new ActionListener<>() {
                            private final HealthResult.Builder resultBuilder = HealthResult.builder(NAME);

                            @Override
                            public void onResponse(ClusterHealthResponse response) {

                                HealthResult result;

                                try {
                                    result = resultBuilder
                                        .status(healthResultStatus(response))
                                        .details(healthResultDetails(response))
                                        .build();
                                } catch (IOException e) {
                                    result = resultBuilder.status(DOWN).exception(e).build();
                                }

                                subscriber.onNext(result);
                                subscriber.onComplete();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                subscriber.onNext(resultBuilder.status(DOWN).exception(e).build());
                                subscriber.onComplete();
                            }
                        }
                    );
            } catch (Exception e) {
                HealthResult.Builder resultBuilder = HealthResult.builder(NAME);
                subscriber.onNext(resultBuilder.status(DOWN).exception(e).build());
                subscriber.onComplete();
            }
        });
    }

    private Object healthResultDetails(ClusterHealthResponse response) throws IOException {
        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        response.toXContent(xContentBuilder, new ToXContent.MapParams(emptyMap()));
        return JacksonMapper.toMap(Strings.toString(xContentBuilder));
    }

    private HealthStatus healthResultStatus(ClusterHealthResponse response) {
        return response.getStatus() == GREEN || response.getStatus() == YELLOW ? UP : DOWN;
    }
}
