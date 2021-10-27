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

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.dao.model.sql.AttributeKvCompositeKey;
import org.thingsboard.server.dao.model.sql.AttributeSearchKvEntity;

import java.util.List;
import java.util.UUID;

public interface AttributeSearchKvRepository extends CrudRepository<AttributeSearchKvEntity, AttributeKvCompositeKey> {

    @Query("SELECT a FROM AttributeSearchKvEntity a WHERE a.id.entityType = :entityType " +
            "AND a.id.attributeKey = :attributeKey " +
            "AND a.booleanValue = :value")
    List<AttributeSearchKvEntity> findAttributesByKeyAndBooleanValue(@Param("entityType") EntityType entityType,
                                                                     @Param("attributeKey") String attributeKey,
                                                                     @Param("value") Boolean value);

    @Query("SELECT a FROM AttributeSearchKvEntity a WHERE a.id.entityType = :entityType " +
            "AND a.id.attributeKey = :attributeKey " +
            "AND a.strValue = :value")
    List<AttributeSearchKvEntity> findAttributesByKeyAndStringValue(@Param("entityType") EntityType entityType,
                                                                    @Param("attributeKey") String attributeKey,
                                                                    @Param("value") String value);

    @Query("SELECT a FROM AttributeSearchKvEntity a WHERE a.id.entityType = :entityType " +
            "AND a.id.attributeKey = :attributeKey " +
            "AND a.longValue = :value")
    List<AttributeSearchKvEntity> findAttributesByKeyAndLongValue(@Param("entityType") EntityType entityType,
                                                                  @Param("attributeKey") String attributeKey,
                                                                  @Param("value") Long value);

    @Query("SELECT a FROM AttributeSearchKvEntity a WHERE a.id.entityType = :entityType " +
            "AND a.id.attributeKey = :attributeKey " +
            "AND a.doubleValue = :value")
    List<AttributeSearchKvEntity> findAttributesByKeyAndDoubleValue(@Param("entityType") EntityType entityType,
                                                                    @Param("attributeKey") String attributeKey,
                                                                    @Param("value") Double value);
}

