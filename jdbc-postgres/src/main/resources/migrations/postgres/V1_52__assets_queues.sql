ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.ee.assets.AssetLineageEvent';
ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.ee.assets.AssetUpsertCommand';
ALTER TYPE queue_type ADD VALUE IF NOT EXISTS 'io.kestra.ee.assets.AssetStateEvent';
