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
package org.thingsboard.server.dao.sql.attributes;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeSearchKvEntry;
import org.thingsboard.server.common.stats.StatsFactory;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.attributes.AttributesSearchDao;
import org.thingsboard.server.dao.model.sql.AttributeKvCompositeKey;
import org.thingsboard.server.dao.model.sql.AttributeSearchKvEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.server.dao.sql.ScheduledLogExecutorComponent;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueParams;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueWrapper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JpaAttributeSearchDao extends JpaAbstractDaoListeningExecutorService implements AttributesSearchDao {

    @Autowired
    ScheduledLogExecutorComponent logExecutor;

    @Autowired
    private AttributeSearchKvRepository attributeSearchKvRepository;

    @Autowired
    private AttributeKvInsertRepository attributeKvInsertRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Value("${sql.attributes.batch_size:1000}")
    private int batchSize;

    @Value("${sql.attributes.batch_max_delay:100}")
    private long maxDelay;

    @Value("${sql.attributes.stats_print_interval_ms:1000}")
    private long statsPrintIntervalMs;

    @Value("${sql.attributes.batch_threads:4}")
    private int batchThreads;

    @Value("${sql.batch_sort:false}")
    private boolean batchSortEnabled;

    private TbSqlBlockingQueueWrapper<AttributeSearchKvEntity> queue;

    @PostConstruct
    private void init() {
    }

    @PreDestroy
    private void destroy() {
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndBooleanValue(EntityType entityType, String attributeKey, Boolean value) {
        return Futures.immediateFuture(
                DaoUtil.convertDataList(Lists.newArrayList(
                        attributeSearchKvRepository.findAttributesByKeyAndBooleanValue(
                                entityType,
                                attributeKey,
                                value))));
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndStringValue(EntityType entityType, String attributeKey, String value) {
        return Futures.immediateFuture(
                DaoUtil.convertDataList(Lists.newArrayList(
                        attributeSearchKvRepository.findAttributesByKeyAndStringValue(
                                entityType,
                                attributeKey,
                                value))));
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndLongValue(EntityType entityType, String attributeKey, Long value) {
        return Futures.immediateFuture(
                DaoUtil.convertDataList(Lists.newArrayList(
                        attributeSearchKvRepository.findAttributesByKeyAndLongValue(
                                entityType,
                                attributeKey,
                                value))));
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndDoubleValue(EntityType entityType, String attributeKey, Double value) {
        return Futures.immediateFuture(
                DaoUtil.convertDataList(Lists.newArrayList(
                        attributeSearchKvRepository.findAttributesByKeyAndDoubleValue(
                                entityType,
                                attributeKey,
                                value))));
    }
}
