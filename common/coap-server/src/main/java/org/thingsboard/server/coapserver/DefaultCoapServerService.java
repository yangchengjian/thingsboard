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
package org.thingsboard.server.coapserver;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.eclipse.californium.core.network.config.NetworkConfigDefaults.DEFAULT_BLOCKWISE_STATUS_LIFETIME;

@Slf4j
@Component
@TbCoapServerComponent
public class DefaultCoapServerService implements CoapServerService {

    @Autowired
    private CoapServerContext coapServerContext;

    private CoapServer server;

    private TbCoapDtlsCertificateVerifier tbDtlsCertificateVerifier;

    private ScheduledExecutorService dtlsSessionsExecutor;

    @PostConstruct
    public void init() throws UnknownHostException {
        createCoapServer();
    }

    @PreDestroy
    public void shutdown() {
        if (dtlsSessionsExecutor != null) {
            dtlsSessionsExecutor.shutdownNow();
        }
        log.info("Stopping CoAP server!");
        server.destroy();
        log.info("CoAP server stopped!");
    }

    @Override
    public CoapServer getCoapServer() throws UnknownHostException {
        if (server != null) {
            return server;
        } else {
            return createCoapServer();
        }
    }

    @Override
    public ConcurrentMap<String, TbCoapDtlsSessionInfo> getDtlsSessionsMap() {
        return tbDtlsCertificateVerifier != null ? tbDtlsCertificateVerifier.getTbCoapDtlsSessionIdsMap() : null;
    }

    @Override
    public long getTimeout() {
        return coapServerContext.getTimeout();
    }

    private CoapServer createCoapServer() throws UnknownHostException {
        server = new CoapServer();

        CoapEndpoint.Builder noSecCoapEndpointBuilder = new CoapEndpoint.Builder();
        InetAddress addr = InetAddress.getByName(coapServerContext.getHost());
        InetSocketAddress sockAddr = new InetSocketAddress(addr, coapServerContext.getPort());
        noSecCoapEndpointBuilder.setInetSocketAddress(sockAddr);
        NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setBoolean(NetworkConfig.Keys.BLOCKWISE_STRICT_BLOCK2_OPTION, true);
        networkConfig.setBoolean(NetworkConfig.Keys.BLOCKWISE_ENTITY_TOO_LARGE_AUTO_FAILOVER, true);
        networkConfig.setLong(NetworkConfig.Keys.BLOCKWISE_STATUS_LIFETIME, DEFAULT_BLOCKWISE_STATUS_LIFETIME);
        networkConfig.setInt(NetworkConfig.Keys.MAX_RESOURCE_BODY_SIZE, 256 * 1024 * 1024);
        networkConfig.setString(NetworkConfig.Keys.RESPONSE_MATCHING, "RELAXED");
        networkConfig.setInt(NetworkConfig.Keys.PREFERRED_BLOCK_SIZE, 1024);
        networkConfig.setInt(NetworkConfig.Keys.MAX_MESSAGE_SIZE, 1024);
        networkConfig.setInt(NetworkConfig.Keys.MAX_RETRANSMIT, 4);
        noSecCoapEndpointBuilder.setNetworkConfig(networkConfig);
        CoapEndpoint noSecCoapEndpoint = noSecCoapEndpointBuilder.build();
        server.addEndpoint(noSecCoapEndpoint);

        if (isDtlsEnabled()) {
            CoapEndpoint.Builder dtlsCoapEndpointBuilder = new CoapEndpoint.Builder();
            TbCoapDtlsSettings dtlsSettings = coapServerContext.getDtlsSettings();
            DtlsConnectorConfig dtlsConnectorConfig = dtlsSettings.dtlsConnectorConfig();
            DTLSConnector connector = new DTLSConnector(dtlsConnectorConfig);
            dtlsCoapEndpointBuilder.setConnector(connector);
            CoapEndpoint dtlsCoapEndpoint = dtlsCoapEndpointBuilder.build();
            server.addEndpoint(dtlsCoapEndpoint);
            if (dtlsConnectorConfig.isClientAuthenticationRequired()) {
                tbDtlsCertificateVerifier = (TbCoapDtlsCertificateVerifier) dtlsConnectorConfig.getAdvancedCertificateVerifier();
                dtlsSessionsExecutor = Executors.newSingleThreadScheduledExecutor();
                dtlsSessionsExecutor.scheduleAtFixedRate(this::evictTimeoutSessions, new Random().nextInt((int) getDtlsSessionReportTimeout()), getDtlsSessionReportTimeout(), TimeUnit.MILLISECONDS);
            }
        }
        Resource root = server.getRoot();
        TbCoapServerMessageDeliverer messageDeliverer = new TbCoapServerMessageDeliverer(root);
        server.setMessageDeliverer(messageDeliverer);

        server.start();
        return server;
    }

    private boolean isDtlsEnabled() {
        return coapServerContext.getDtlsSettings() != null;
    }

    private void evictTimeoutSessions() {
        tbDtlsCertificateVerifier.evictTimeoutSessions();
    }

    private long getDtlsSessionReportTimeout() {
        return tbDtlsCertificateVerifier.getDtlsSessionReportTimeout();
    }

}
