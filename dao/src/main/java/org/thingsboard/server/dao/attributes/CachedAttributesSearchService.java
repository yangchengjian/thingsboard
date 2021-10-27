/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.attributes;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeSearchKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.stats.DefaultCounter;
import org.thingsboard.server.common.stats.StatsFactory;
import org.thingsboard.server.dao.service.Validator;

import java.util.*;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.attributes.AttributeUtils.validate;

@Service
@ConditionalOnProperty(prefix = "cache.attributes", value = "enabled", havingValue = "true")
@Primary
@Slf4j
public class CachedAttributesSearchService implements AttributesSearchService {
    private static final String STATS_NAME = "attributes.cache";

    private final AttributesSearchDao attributesSearchDao;
    private final AttributesCacheWrapper cacheWrapper;
    private final DefaultCounter hitCounter;
    private final DefaultCounter missCounter;

    public CachedAttributesSearchService(AttributesSearchDao attributesSearchDao,
                                         AttributesCacheWrapper cacheWrapper,
                                         StatsFactory statsFactory) {
        this.attributesSearchDao = attributesSearchDao;
        this.cacheWrapper = cacheWrapper;

        this.hitCounter = statsFactory.createDefaultCounter(STATS_NAME, "result", "hit");
        this.missCounter = statsFactory.createDefaultCounter(STATS_NAME, "result", "miss");
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndBooleanValue(EntityType entityType, String attributeKey, Boolean value) {
        return attributesSearchDao.findByKeyAndBooleanValue(entityType, attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndStringValue(EntityType entityType, String attributeKey, String value) {
        return attributesSearchDao.findByKeyAndStringValue(entityType, attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndLongValue(EntityType entityType, String attributeKey, Long value) {
        return attributesSearchDao.findByKeyAndLongValue(entityType, attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndDoubleValue(EntityType entityType, String attributeKey, Double value) {
        return attributesSearchDao.findByKeyAndDoubleValue(entityType, attributeKey, value);
    }
}
