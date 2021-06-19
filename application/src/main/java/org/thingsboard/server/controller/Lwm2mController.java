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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.rule.engine.api.msg.DeviceNameOrTypeUpdateMsg;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.lwm2m.ServerSecurityConfig;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.Map;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class Lwm2mController extends BaseController {

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/deviceProfile/bootstrap/{isBootstrapServer}", method = RequestMethod.GET)
    @ResponseBody
    public ServerSecurityConfig getLwm2mBootstrapSecurityInfo(@PathVariable("isBootstrapServer") boolean bootstrapServer) throws ThingsboardException {
        try {
            return lwM2MServerSecurityInfoRepository.getServerSecurityInfo(bootstrapServer);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/lwm2m/device-credentials", method = RequestMethod.POST)
    @ResponseBody
    public Device saveDeviceWithCredentials(@RequestBody (required=false) Map<Class<?>, Object> deviceWithDeviceCredentials) throws ThingsboardException {
        ObjectMapper mapper = new ObjectMapper();
        Device device = checkNotNull(mapper.convertValue(deviceWithDeviceCredentials.get(Device.class), Device.class));
        DeviceCredentials credentials = checkNotNull(mapper.convertValue( deviceWithDeviceCredentials.get(DeviceCredentials.class), DeviceCredentials.class));
        try {
            device.setTenantId(getCurrentUser().getTenantId());
            checkEntity(device.getId(), device, Resource.DEVICE);
            Device savedDevice = deviceService.saveDeviceWithCredentials(device, credentials);
            checkNotNull(savedDevice);

            tbClusterService.onDeviceChange(savedDevice, null);
            tbClusterService.pushMsgToCore(new DeviceNameOrTypeUpdateMsg(savedDevice.getTenantId(),
                    savedDevice.getId(), savedDevice.getName(), savedDevice.getType()), null);
            tbClusterService.onEntityStateChange(savedDevice.getTenantId(), savedDevice.getId(),
                    device.getId() == null ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);

            logEntityAction(savedDevice.getId(), savedDevice,
                    savedDevice.getCustomerId(),
                    device.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            if (device.getId() == null) {
                deviceStateService.onDeviceAdded(savedDevice);
            } else {
                deviceStateService.onDeviceUpdated(savedDevice);
            }
            return savedDevice;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.DEVICE), device,
                    null, device.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }
}
