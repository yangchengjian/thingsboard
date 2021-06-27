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
package org.thingsboard.server.transport.coap;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.coapserver.CoapServerService;
import org.thingsboard.server.coapserver.TbCoapServerComponent;
import org.thingsboard.server.common.data.TbTransportService;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.transport.coap.efento.CoapEfentoTransportResource;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.UnknownHostException;

@Service("CoapTransportService")
@TbCoapServerComponent
@Slf4j
public class CoapTransportService implements TbTransportService {

    private static final String V1 = "v1";
    private static final String API = "api";
    private static final String EFENTO = "efento";
    private static final String MEASUREMENTS = "m";

    @Autowired
    private CoapServerService coapServerService;

    @Autowired
    private CoapTransportContext coapTransportContext;

    private CoapServer coapServer;

    @PostConstruct
    public void init() throws UnknownHostException {
        log.info("Starting CoAP transport...");
        coapServer = coapServerService.getCoapServer();
        CoapResource api = new CoapResource(API);
        api.add(new CoapTransportResource(coapTransportContext, coapServerService, V1));

        CoapResource efento = new CoapResource(EFENTO);
        CoapEfentoTransportResource efentoMeasurementsTransportResource = new CoapEfentoTransportResource(coapTransportContext, MEASUREMENTS);
        efento.add(efentoMeasurementsTransportResource);
        coapServer.add(api);
        coapServer.add(efento);
        coapServer.add(new OtaPackageTransportResource(coapTransportContext, OtaPackageType.FIRMWARE));
        coapServer.add(new OtaPackageTransportResource(coapTransportContext, OtaPackageType.SOFTWARE));
        log.info("CoAP transport started!");
    }

    @PreDestroy
    public void shutdown() {
        log.info("CoAP transport stopped!");
    }

    @Override
    public String getName() {
        return "COAP";
    }
}
