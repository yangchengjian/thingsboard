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
package org.thingsboard.server.transport.lwm2m;

import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.client.object.Security;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.device.credentials.lwm2m.X509ClientCredentials;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.SingleEntityFilter;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.transport.util.SslUtil;
import org.thingsboard.server.service.telemetry.cmd.TelemetryPluginCmdsWrapper;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityDataCmd;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityDataUpdate;
import org.thingsboard.server.service.telemetry.cmd.v2.LatestValueCmd;
import org.thingsboard.server.transport.lwm2m.client.LwM2MTestClient;
import org.thingsboard.server.transport.lwm2m.secure.credentials.LwM2MCredentials;

import java.util.Collections;
import java.util.List;

import static org.eclipse.leshan.client.object.Security.x509;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class X509LwM2MIntegrationTest extends AbstractLwM2MIntegrationTest {

    private final int port = 5686;
    private final NetworkConfig coapConfig = new NetworkConfig().setString("COAP_SECURE_PORT", Integer.toString(port));
    private final String endpoint = "deviceAEndpoint";
    private final String serverUri = "coaps://localhost:" + port;

    private Device createDevice(X509ClientCredentials clientCredentials) throws Exception {
        Device device = new Device();
        device.setName("Device A");
        device.setDeviceProfileId(deviceProfile.getId());
        device.setTenantId(tenantId);
        device = doPost("/api/device", device, Device.class);
        Assert.assertNotNull(device);

        DeviceCredentials deviceCredentials =
                doGet("/api/device/" + device.getId().getId().toString() + "/credentials", DeviceCredentials.class);
        Assert.assertEquals(device.getId(), deviceCredentials.getDeviceId());
        deviceCredentials.setCredentialsType(DeviceCredentialsType.LWM2M_CREDENTIALS);

        LwM2MCredentials credentials = new LwM2MCredentials();

        credentials.setClient(clientCredentials);

        deviceCredentials.setCredentialsValue(JacksonUtil.toString(credentials));
        doPost("/api/device/credentials", deviceCredentials).andExpect(status().isOk());
        return device;
    }

    //TODO: use different endpoints to isolate tests.
    @Ignore()
    @Test
    public void testConnectAndObserveTelemetry() throws Exception {
        createDeviceProfile(TRANSPORT_CONFIGURATION);
        X509ClientCredentials credentials = new X509ClientCredentials();
        credentials.setEndpoint(endpoint+1);
        Device device = createDevice(credentials);

        SingleEntityFilter sef = new SingleEntityFilter();
        sef.setSingleEntity(device.getId());
        LatestValueCmd latestCmd = new LatestValueCmd();
        latestCmd.setKeys(Collections.singletonList(new EntityKey(EntityKeyType.TIME_SERIES, "batteryLevel")));
        EntityDataQuery edq = new EntityDataQuery(sef, new EntityDataPageLink(1, 0, null, null),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        EntityDataCmd cmd = new EntityDataCmd(1, edq, null, latestCmd, null);
        TelemetryPluginCmdsWrapper wrapper = new TelemetryPluginCmdsWrapper();
        wrapper.setEntityDataCmds(Collections.singletonList(cmd));

        wsClient.send(mapper.writeValueAsString(wrapper));
        wsClient.waitForReply();

        wsClient.registerWaitForUpdate();
        LwM2MTestClient client = new LwM2MTestClient(executor, endpoint+1);
        Security security = x509(serverUri, 123, clientX509Cert.getEncoded(), clientPrivateKeyFromCert.getEncoded(), serverX509Cert.getEncoded());
        client.init(security, coapConfig);
        String msg = wsClient.waitForUpdate();

        EntityDataUpdate update = mapper.readValue(msg, EntityDataUpdate.class);
        Assert.assertEquals(1, update.getCmdId());
        List<EntityData> eData = update.getUpdate();
        Assert.assertNotNull(eData);
        Assert.assertEquals(1, eData.size());
        Assert.assertEquals(device.getId(), eData.get(0).getEntityId());
        Assert.assertNotNull(eData.get(0).getLatest().get(EntityKeyType.TIME_SERIES));
        var tsValue = eData.get(0).getLatest().get(EntityKeyType.TIME_SERIES).get("batteryLevel");
        Assert.assertEquals(42, Long.parseLong(tsValue.getValue()));
        client.destroy();
    }

    @Test
    public void testConnectWithCertAndObserveTelemetry() throws Exception {
        createDeviceProfile(TRANSPORT_CONFIGURATION);
        X509ClientCredentials credentials = new X509ClientCredentials();
        credentials.setEndpoint(endpoint);
        credentials.setCert(SslUtil.getCertificateString(clientX509CertNotTrusted));
        Device device = createDevice(credentials);

        SingleEntityFilter sef = new SingleEntityFilter();
        sef.setSingleEntity(device.getId());
        LatestValueCmd latestCmd = new LatestValueCmd();
        latestCmd.setKeys(Collections.singletonList(new EntityKey(EntityKeyType.TIME_SERIES, "batteryLevel")));
        EntityDataQuery edq = new EntityDataQuery(sef, new EntityDataPageLink(1, 0, null, null),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        EntityDataCmd cmd = new EntityDataCmd(1, edq, null, latestCmd, null);
        TelemetryPluginCmdsWrapper wrapper = new TelemetryPluginCmdsWrapper();
        wrapper.setEntityDataCmds(Collections.singletonList(cmd));

        wsClient.send(mapper.writeValueAsString(wrapper));
        wsClient.waitForReply();

        wsClient.registerWaitForUpdate();
        LwM2MTestClient client = new LwM2MTestClient(executor, endpoint);

        Security security = x509(serverUri, 123, clientX509CertNotTrusted.getEncoded(), clientPrivateKeyFromCert.getEncoded(), serverX509Cert.getEncoded());

        client.init(security, coapConfig);
        String msg = wsClient.waitForUpdate();

        EntityDataUpdate update = mapper.readValue(msg, EntityDataUpdate.class);
        Assert.assertEquals(1, update.getCmdId());
        List<EntityData> eData = update.getUpdate();
        Assert.assertNotNull(eData);
        Assert.assertEquals(1, eData.size());
        Assert.assertEquals(device.getId(), eData.get(0).getEntityId());
        Assert.assertNotNull(eData.get(0).getLatest().get(EntityKeyType.TIME_SERIES));
        var tsValue = eData.get(0).getLatest().get(EntityKeyType.TIME_SERIES).get("batteryLevel");
        Assert.assertEquals(42, Long.parseLong(tsValue.getValue()));
        client.destroy();
    }

}
