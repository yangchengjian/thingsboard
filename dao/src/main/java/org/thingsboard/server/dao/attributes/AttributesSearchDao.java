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

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeSearchKvEntry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author Andrew Shvayka
 */
public interface AttributesSearchDao {
    ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndBooleanValue(EntityType entityType, String attributeKey, Boolean value);

    ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndStringValue(EntityType entityType, String attributeKey, String value);

    ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndLongValue(EntityType entityType, String attributeKey, Long value);

    ListenableFuture<List<AttributeSearchKvEntry>> findByKeyAndDoubleValue(EntityType entityType, String attributeKey, Double value);
}
