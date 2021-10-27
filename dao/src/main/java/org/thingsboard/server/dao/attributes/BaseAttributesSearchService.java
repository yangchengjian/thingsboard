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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeSearchKvEntry;
import org.thingsboard.server.dao.service.Validator;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.attributes.AttributeUtils.validate;

/**
 * @author Andrew Shvayka
 */
@Service
@ConditionalOnProperty(prefix = "cache.attributes", value = "enabled", havingValue = "false", matchIfMissing = true)
@Primary
@Slf4j
public class BaseAttributesSearchService implements AttributesSearchService {
    private final AttributesSearchDao attributesSearchDao;

    public BaseAttributesSearchService(AttributesSearchDao attributesSearchDao) {
        this.attributesSearchDao = attributesSearchDao;
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndBooleanValue(String attributeKey, Boolean value) {
        return attributesSearchDao.findByKeyAndBooleanValue(attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndStringValue(String attributeKey, String value) {
        return attributesSearchDao.findByKeyAndStringValue(attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndLongValue(String attributeKey, Long value) {
        return attributesSearchDao.findByKeyAndLongValue(attributeKey, value);
    }

    @Override
    public ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndDoubleValue(String attributeKey, Double value) {
        return attributesSearchDao.findByKeyAndDoubleValue(attributeKey, value);
    }
}
