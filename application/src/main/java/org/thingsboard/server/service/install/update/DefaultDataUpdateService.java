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
package org.thingsboard.server.service.install.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNode;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNodeConfiguration;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.oauth2.OAuth2Info;
import org.thingsboard.server.common.data.oauth2.deprecated.OAuth2ClientsParams;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.dao.alarm.AlarmDao;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.oauth2.OAuth2Service;
import org.thingsboard.server.dao.oauth2.OAuth2Utils;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.install.InstallScripts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@Profile("install")
@Slf4j
public class DefaultDataUpdateService implements DataUpdateService {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private InstallScripts installScripts;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private TimeseriesService tsService;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private EntityService entityService;

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private OAuth2Service oAuth2Service;

    @Override
    public void updateData(String fromVersion) throws Exception {
        switch (fromVersion) {
            case "1.4.0":
                log.info("Updating data from version 1.4.0 to 2.0.0 ...");
                tenantsDefaultRuleChainUpdater.updateEntities(null);
                break;
            case "3.0.1":
                log.info("Updating data from version 3.0.1 to 3.1.0 ...");
                tenantsEntityViewsUpdater.updateEntities(null);
                break;
            case "3.1.1":
                log.info("Updating data from version 3.1.1 to 3.2.0 ...");
                tenantsRootRuleChainUpdater.updateEntities(null);
                break;
            case "3.2.2":
                log.info("Updating data from version 3.2.2 to 3.3.0 ...");
                tenantsDefaultEdgeRuleChainUpdater.updateEntities(null);
                tenantsAlarmsCustomerUpdater.updateEntities(null);
                updateOAuth2Params();
                break;
            default:
                throw new RuntimeException("Unable to update data, unsupported fromVersion: " + fromVersion);
        }
    }

    private final PaginatedUpdater<String, Tenant> tenantsDefaultRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants default rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsDefaultEdgeRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants default edge rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain defaultEdgeRuleChain = ruleChainService.getEdgeTemplateRootRuleChain(tenant.getId());
                        if (defaultEdgeRuleChain == null) {
                            installScripts.createDefaultEdgeRuleChains(tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsRootRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants root rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        } else {
                            RuleChainMetaData md = ruleChainService.loadRuleChainMetaData(tenant.getId(), ruleChain.getId());
                            int oldIdx = md.getFirstNodeIndex();
                            int newIdx = md.getNodes().size();

                            if (md.getNodes().size() < oldIdx) {
                                // Skip invalid rule chains
                                return;
                            }

                            RuleNode oldFirstNode = md.getNodes().get(oldIdx);
                            if (oldFirstNode.getType().equals(TbDeviceProfileNode.class.getName())) {
                                // No need to update the rule node twice.
                                return;
                            }

                            RuleNode ruleNode = new RuleNode();
                            ruleNode.setRuleChainId(ruleChain.getId());
                            ruleNode.setName("Device Profile Node");
                            ruleNode.setType(TbDeviceProfileNode.class.getName());
                            ruleNode.setDebugMode(false);
                            TbDeviceProfileNodeConfiguration ruleNodeConfiguration = new TbDeviceProfileNodeConfiguration().defaultConfiguration();
                            ruleNode.setConfiguration(JacksonUtil.valueToTree(ruleNodeConfiguration));
                            ObjectNode additionalInfo = JacksonUtil.newObjectNode();
                            additionalInfo.put("description", "Process incoming messages from devices with the alarm rules defined in the device profile. Dispatch all incoming messages with \"Success\" relation type.");
                            additionalInfo.put("layoutX", 204);
                            additionalInfo.put("layoutY", 240);
                            ruleNode.setAdditionalInfo(additionalInfo);

                            md.getNodes().add(ruleNode);
                            md.setFirstNodeIndex(newIdx);
                            md.addConnectionInfo(newIdx, oldIdx, "Success");
                            ruleChainService.saveRuleChainMetaData(tenant.getId(), md);
                        }
                    } catch (Exception e) {
                        log.error("[{}] Unable to update Tenant: {}", tenant.getId(), tenant.getName(), e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsEntityViewsUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants entity views updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    updateTenantEntityViews(tenant.getId());
                }
            };

    private void updateTenantEntityViews(TenantId tenantId) {
        PageLink pageLink = new PageLink(100);
        PageData<EntityView> pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
        boolean hasNext = true;
        while (hasNext) {
            List<ListenableFuture<List<Void>>> updateFutures = new ArrayList<>();
            for (EntityView entityView : pageData.getData()) {
                updateFutures.add(updateEntityViewLatestTelemetry(entityView));
            }

            try {
                Futures.allAsList(updateFutures).get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to copy latest telemetry to entity view", e);
            }

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
            } else {
                hasNext = false;
            }
        }
    }

    private ListenableFuture<List<Void>> updateEntityViewLatestTelemetry(EntityView entityView) {
        EntityViewId entityId = entityView.getId();
        List<String> keys = entityView.getKeys() != null && entityView.getKeys().getTimeseries() != null ?
                entityView.getKeys().getTimeseries() : Collections.emptyList();
        long startTs = entityView.getStartTimeMs();
        long endTs = entityView.getEndTimeMs() == 0 ? Long.MAX_VALUE : entityView.getEndTimeMs();
        ListenableFuture<List<String>> keysFuture;
        if (keys.isEmpty()) {
            keysFuture = Futures.transform(tsService.findAllLatest(TenantId.SYS_TENANT_ID,
                    entityView.getEntityId()), latest -> latest.stream().map(TsKvEntry::getKey).collect(Collectors.toList()), MoreExecutors.directExecutor());
        } else {
            keysFuture = Futures.immediateFuture(keys);
        }
        ListenableFuture<List<TsKvEntry>> latestFuture = Futures.transformAsync(keysFuture, fetchKeys -> {
            List<ReadTsKvQuery> queries = fetchKeys.stream().filter(key -> !isBlank(key)).map(key -> new BaseReadTsKvQuery(key, startTs, endTs, 1, "DESC")).collect(Collectors.toList());
            if (!queries.isEmpty()) {
                return tsService.findAll(TenantId.SYS_TENANT_ID, entityView.getEntityId(), queries);
            } else {
                return Futures.immediateFuture(null);
            }
        }, MoreExecutors.directExecutor());
        return Futures.transformAsync(latestFuture, latestValues -> {
            if (latestValues != null && !latestValues.isEmpty()) {
                ListenableFuture<List<Void>> saveFuture = tsService.saveLatest(TenantId.SYS_TENANT_ID, entityId, latestValues);
                return saveFuture;
            }
            return Futures.immediateFuture(null);
        }, MoreExecutors.directExecutor());
    }

    private final PaginatedUpdater<String, Tenant> tenantsAlarmsCustomerUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants alarms customer updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    updateTenantAlarmsCustomer(tenant.getId());
                }
            };

    private void updateTenantAlarmsCustomer(TenantId tenantId) {
        AlarmQuery alarmQuery = new AlarmQuery(null, new TimePageLink(100), null, null, false);
        PageData<AlarmInfo> alarms = alarmDao.findAlarms(tenantId, alarmQuery);
        boolean hasNext = true;
        while (hasNext) {
            for (Alarm alarm : alarms.getData()) {
                if (alarm.getCustomerId() == null && alarm.getOriginator() != null) {
                    alarm.setCustomerId(entityService.fetchEntityCustomerId(tenantId, alarm.getOriginator()));
                    alarmDao.save(tenantId, alarm);
                }
            }
            if (alarms.hasNext()) {
                alarmQuery.setPageLink(alarmQuery.getPageLink().nextPageLink());
                alarms = alarmDao.findAlarms(tenantId, alarmQuery);
            } else {
                hasNext = false;
            }
        }
    }

    private void updateOAuth2Params() {
        try {
            OAuth2ClientsParams oauth2ClientsParams = oAuth2Service.findOAuth2Params();
            if (!oauth2ClientsParams.getDomainsParams().isEmpty()) {
                log.info("Updating OAuth2 parameters ...");
                OAuth2Info oAuth2Info = OAuth2Utils.clientParamsToOAuth2Info(oauth2ClientsParams);
                oAuth2Service.saveOAuth2Info(oAuth2Info);
                oAuth2Service.saveOAuth2Params(new OAuth2ClientsParams(false, Collections.emptyList()));
                log.info("Successfully updated OAuth2 parameters!");
            }
        }
        catch (Exception e) {
           log.error("Failed to update OAuth2 parameters", e);
        }
    }

}
