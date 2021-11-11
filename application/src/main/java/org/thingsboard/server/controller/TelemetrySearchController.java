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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.rule.engine.api.msg.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.dao.attributes.AttributesSearchService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.AccessValidator;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.telemetry.AttributeData;
import org.thingsboard.server.service.telemetry.TsData;
import org.thingsboard.server.service.telemetry.exception.InvalidParametersException;
import org.thingsboard.server.service.telemetry.exception.UncheckedApiException;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by ashvayka on 22.03.18.
 */
@RestController
@TbCoreComponent
@RequestMapping(TbUrlConstants.TELEMETRY_URL_PREFIX)
@Slf4j
public class TelemetrySearchController extends BaseController {

    @Autowired
    private TimeseriesService tsService;

    @Autowired
    protected AttributesSearchService attributesSearchService;

    @Autowired
    private AccessValidator accessValidator;

    @Value("${transport.json.max_string_value_length:0}")
    private int maxStringValueLength;

    private ExecutorService executor;

    private static final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void initExecutor() {
        executor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("telemetry-search-controller"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/attributes", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getAttributesByKeyAndValue(
            @RequestParam(required = true) String entityType,
            @RequestParam(required = true) String attributeKey,
            @RequestParam(required = true) String attributeValue
    ) throws ThingsboardException {
        log.info("getAttributesByKeyAndValue entityType: [{}]", entityType);
        log.info("getAttributesByKeyAndValue attributeKey: [{}]", attributeKey);
        log.info("getAttributesByKeyAndValue attributeValue: [{}]", attributeValue);
        SecurityUser user = getCurrentUser();
        TenantId tenantId = user.getTenantId();
        String entityTypeStr = tenantId.getEntityType().toString();
        String entityIdStr = tenantId.getId().toString();
        return accessValidator.validateEntityAndCallback(getCurrentUser(), Operation.READ_ATTRIBUTES, entityTypeStr, entityIdStr,
                (result, t, e) -> getAttributesByKeyAndValueCallback(result, entityType, attributeKey, attributeValue));
    }

    private void getAttributesByKeyAndValueCallback(@Nullable DeferredResult<ResponseEntity> result, String entityTypeStr, String attributeKey, String attributeValue) {
        EntityType entityType = EntityType.valueOf(entityTypeStr);
        if (isBoolean(attributeValue)) {
            Futures.addCallback(attributesSearchService.findByKeyAndBooleanValue(entityType, attributeKey, new Boolean(attributeValue)), getAttributesToResponseCallback(result), MoreExecutors.directExecutor());
        } else if (isLong(attributeValue)) {
            Futures.addCallback(attributesSearchService.findByKeyAndLongValue(entityType, attributeKey, new Long(attributeValue)), getAttributesToResponseCallback(result), MoreExecutors.directExecutor());
        } else if (isDouble(attributeValue)) {
            Futures.addCallback(attributesSearchService.findByKeyAndDoubleValue(entityType, attributeKey, new Double(attributeValue)), getAttributesToResponseCallback(result), MoreExecutors.directExecutor());
        } else {
            Futures.addCallback(attributesSearchService.findByKeyAndStringValue(entityType, attributeKey, attributeValue), getAttributesToResponseCallback(result), MoreExecutors.directExecutor());
        }
    }

    private FutureCallback<List<AttributeSearchKvEntry>> getAttributesToResponseCallback(final DeferredResult<ResponseEntity> response) {
        return new FutureCallback<List<AttributeSearchKvEntry>>() {
            @Override
            public void onSuccess(List<AttributeSearchKvEntry> attributes) {
                log.info("getAttributesToResponseCallback attributes: [{}]", attributes);
                response.setResult(new ResponseEntity<>(attributes, HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable e) {
                log.error("Failed to fetch attributes", e);
                AccessValidator.handleError(e, response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private boolean isBoolean(String str) {
        return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
    }

    private boolean isLong (String str) {
        Pattern pattern = Pattern.compile("^[-\\+]?[0-9]*");
        return pattern.matcher(str).matches();
    }

    private boolean isDouble(String str) {
        Pattern pattern = Pattern.compile("^[-\\+]?[.\\d]*$");
        return pattern.matcher(str).matches();
    }
}
