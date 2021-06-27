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
package org.thingsboard.server.transport.lwm2m.server.client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.LwM2mSingleResource;
import org.eclipse.leshan.core.node.codec.LwM2mValueConverter;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.security.SecurityInfo;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.device.data.Lwm2mDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.PowerMode;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.gen.transport.TransportProtos.SessionInfoProto;
import org.thingsboard.server.gen.transport.TransportProtos.TsKvProto;
import org.thingsboard.server.transport.lwm2m.server.LwM2mQueuedRequest;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.lwm2m.LwM2mConstants.LWM2M_SEPARATOR_PATH;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LWM2M_VERSION_DEFAULT;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.convertObjectIdToVersionedId;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.equalsResourceTypeGetSimpleName;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.fromVersionedIdToObjectId;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.getVerFromPathIdVerOrId;

@Slf4j
public class LwM2mClient implements Cloneable {

    private final String nodeId;
    @Getter
    private final String endpoint;
    private final Lock lock;
    @Getter
    @Setter
    private LwM2MClientState state;
    @Getter
    private final Map<String, ResourceValue> resources;
    @Getter
    private final Map<String, TsKvProto> sharedAttributes;
    @Getter
    private final Queue<LwM2mQueuedRequest> queuedRequests;

    @Getter
    private String deviceName;
    @Getter
    private String deviceProfileName;

    @Getter
    private PowerMode powerMode;

    @Getter
    private String identity;
    @Getter
    private SecurityInfo securityInfo;
    @Getter
    private TenantId tenantId;
    @Getter
    private UUID deviceId;
    @Getter
    private SessionInfoProto session;
    @Getter
    private UUID profileId;
    @Getter
    @Setter
    private Registration registration;

    private ValidateDeviceCredentialsResponse credentials;

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public LwM2mClient(String nodeId, String endpoint) {
        this.nodeId = nodeId;
        this.endpoint = endpoint;
        this.lock = new ReentrantLock();
        this.sharedAttributes = new ConcurrentHashMap<>();
        this.resources = new ConcurrentHashMap<>();
        this.queuedRequests = new ConcurrentLinkedQueue<>();
        this.state = LwM2MClientState.CREATED;
    }

    public void init(String identity, SecurityInfo securityInfo, ValidateDeviceCredentialsResponse credentials, UUID sessionId) {
        this.identity = identity;
        this.securityInfo = securityInfo;
        this.credentials = credentials;
        this.session = createSession(nodeId, sessionId, credentials);
        this.tenantId = new TenantId(new UUID(session.getTenantIdMSB(), session.getTenantIdLSB()));
        this.deviceId = new UUID(session.getDeviceIdMSB(), session.getDeviceIdLSB());
        this.profileId = new UUID(session.getDeviceProfileIdMSB(), session.getDeviceProfileIdLSB());
        this.deviceName = session.getDeviceName();
        this.deviceProfileName = session.getDeviceType();
        this.powerMode = credentials.getDeviceInfo().getPowerMode();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void onDeviceUpdate(Device device, Optional<DeviceProfile> deviceProfileOpt) {
        SessionInfoProto.Builder builder = SessionInfoProto.newBuilder().mergeFrom(session);
        this.deviceId = device.getUuidId();
        this.deviceName = device.getName();
        builder.setDeviceIdMSB(deviceId.getMostSignificantBits());
        builder.setDeviceIdLSB(deviceId.getLeastSignificantBits());
        builder.setDeviceName(deviceName);
        deviceProfileOpt.ifPresent(deviceProfile -> updateSession(deviceProfile, builder));
        this.session = builder.build();
        this.powerMode = ((Lwm2mDeviceTransportConfiguration) device.getDeviceData().getTransportConfiguration()).getPowerMode();
    }

    public void onDeviceProfileUpdate(DeviceProfile deviceProfile) {
        SessionInfoProto.Builder builder = SessionInfoProto.newBuilder().mergeFrom(session);
        updateSession(deviceProfile, builder);
        this.session = builder.build();
    }

    private void updateSession(DeviceProfile deviceProfile, SessionInfoProto.Builder builder) {
        this.deviceProfileName = deviceProfile.getName();
        this.profileId = deviceProfile.getUuidId();
        builder.setDeviceProfileIdMSB(profileId.getMostSignificantBits());
        builder.setDeviceProfileIdLSB(profileId.getLeastSignificantBits());
        builder.setDeviceType(this.deviceProfileName);
    }

    private SessionInfoProto createSession(String nodeId, UUID sessionId, ValidateDeviceCredentialsResponse msg) {
        return SessionInfoProto.newBuilder()
                .setNodeId(nodeId)
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setDeviceIdMSB(msg.getDeviceInfo().getDeviceId().getId().getMostSignificantBits())
                .setDeviceIdLSB(msg.getDeviceInfo().getDeviceId().getId().getLeastSignificantBits())
                .setTenantIdMSB(msg.getDeviceInfo().getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(msg.getDeviceInfo().getTenantId().getId().getLeastSignificantBits())
                .setCustomerIdMSB(msg.getDeviceInfo().getCustomerId().getId().getMostSignificantBits())
                .setCustomerIdLSB(msg.getDeviceInfo().getCustomerId().getId().getLeastSignificantBits())
                .setDeviceName(msg.getDeviceInfo().getDeviceName())
                .setDeviceType(msg.getDeviceInfo().getDeviceType())
                .setDeviceProfileIdMSB(msg.getDeviceInfo().getDeviceProfileId().getId().getMostSignificantBits())
                .setDeviceProfileIdLSB(msg.getDeviceInfo().getDeviceProfileId().getId().getLeastSignificantBits())
                .build();
    }

    public boolean saveResourceValue(String pathRezIdVer, LwM2mResource rez, LwM2mModelProvider modelProvider) {
        if (this.resources.get(pathRezIdVer) != null && this.resources.get(pathRezIdVer).getResourceModel() != null) {
            this.resources.get(pathRezIdVer).setLwM2mResource(rez);
            return true;
        } else {
            LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathRezIdVer));
            ResourceModel resourceModel = modelProvider.getObjectModel(registration).getResourceModel(pathIds.getObjectId(), pathIds.getResourceId());
            if (resourceModel != null) {
                this.resources.put(pathRezIdVer, new ResourceValue(rez, resourceModel));
                return true;
            } else {
                return false;
            }
        }
    }

    public Object getResourceValue(String pathRezIdVer, String pathRezId) {
        String pathRez = pathRezIdVer == null ? convertObjectIdToVersionedId(pathRezId, this.registration) : pathRezIdVer;
        if (this.resources.get(pathRez) != null) {
            return this.resources.get(pathRez).getLwM2mResource().getValue();
        }
        return null;
    }

    public Object getResourceNameByRezId(String pathRezIdVer, String pathRezId) {
        String pathRez = pathRezIdVer == null ? convertObjectIdToVersionedId(pathRezId, this.registration) : pathRezIdVer;
        if (this.resources.get(pathRez) != null) {
            return this.resources.get(pathRez).getResourceModel().name;
        }
        return null;
    }

    public String getRezIdByResourceNameAndObjectInstanceId(String resourceName, String pathObjectInstanceIdVer, LwM2mModelProvider modelProvider) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathObjectInstanceIdVer));
        if (pathIds.isObjectInstance()) {
            Set<Integer> rezIds = modelProvider.getObjectModel(registration)
                    .getObjectModel(pathIds.getObjectId()).resources.entrySet()
                    .stream()
                    .filter(map -> resourceName.equals(map.getValue().name))
                    .map(map -> map.getKey())
                    .collect(Collectors.toSet());
            return rezIds.size() > 0 ? String.valueOf(rezIds.stream().findFirst().get()) : null;
        }
        return null;
    }

    public ResourceModel getResourceModel(String pathIdVer, LwM2mModelProvider modelProvider) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathIdVer));
        String verSupportedObject = registration.getSupportedObject().get(pathIds.getObjectId());
        String verRez = getVerFromPathIdVerOrId(pathIdVer);
        return verRez == null || verRez.equals(verSupportedObject) ? modelProvider.getObjectModel(registration)
                .getResourceModel(pathIds.getObjectId(), pathIds.getResourceId()) : null;
    }

    public ObjectModel getObjectModel(String pathIdVer, LwM2mModelProvider modelProvider) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathIdVer));
        String verSupportedObject = registration.getSupportedObject().get(pathIds.getObjectId());
        String verRez = getVerFromPathIdVerOrId(pathIdVer);
        return verRez == null || verRez.equals(verSupportedObject) ? modelProvider.getObjectModel(registration)
                .getObjectModel(pathIds.getObjectId()) : null;
    }

    public String objectToString(LwM2mObject lwM2mObject, LwM2mValueConverter converter, String pathIdVer) {
        StringBuilder builder = new StringBuilder();
        builder.append("LwM2mObject [id=").append(lwM2mObject.getId()).append(", instances={");
        lwM2mObject.getInstances().forEach((instId, inst) -> {
            builder.append(instId).append("=").append(this.instanceToString(inst, converter, pathIdVer)).append(", ");
        });
        int startInd = builder.lastIndexOf(", ");
        if (startInd > 0) {
            builder.delete(startInd, startInd + 2);
        }
        builder.append("}]");
        return builder.toString();
    }

    public String instanceToString(LwM2mObjectInstance objectInstance, LwM2mValueConverter converter, String pathIdVer) {
        StringBuilder builder = new StringBuilder();
        builder.append("LwM2mObjectInstance [id=").append(objectInstance.getId()).append(", resources={");
        objectInstance.getResources().forEach((resId, res) -> {
            builder.append(resId).append("=").append(this.resourceToString(res, converter, pathIdVer)).append(", ");
        });
        int startInd = builder.lastIndexOf(", ");
        if (startInd > 0) {
            builder.delete(startInd, startInd + 2);
        }
        builder.append("}]");
        return builder.toString();
    }

    public String resourceToString(LwM2mResource lwM2mResource, LwM2mValueConverter converter, String pathIdVer) {
        return lwM2mResource.getValue().toString();
    }

    public Collection<LwM2mResource> getNewResourceForInstance(String pathRezIdVer, Object params, LwM2mModelProvider modelProvider,
                                                               LwM2mValueConverter converter) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathRezIdVer));
        Collection<LwM2mResource> resources = ConcurrentHashMap.newKeySet();
        Map<Integer, ResourceModel> resourceModels = modelProvider.getObjectModel(registration)
                .getObjectModel(pathIds.getObjectId()).resources;
        resourceModels.forEach((resId, resourceModel) -> {
            if (resId.equals(pathIds.getResourceId())) {
                resources.add(LwM2mSingleResource.newResource(resId, converter.convertValue(params,
                        equalsResourceTypeGetSimpleName(params), resourceModel.type, pathIds), resourceModel.type));

            }
        });
        return resources;
    }

    public Collection<LwM2mResource> getNewResourcesForInstance(String pathRezIdVer, Object params, LwM2mModelProvider modelProvider,
                                                                LwM2mValueConverter converter) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathRezIdVer));
        Collection<LwM2mResource> resources = ConcurrentHashMap.newKeySet();
        Map<Integer, ResourceModel> resourceModels = modelProvider.getObjectModel(registration)
                .getObjectModel(pathIds.getObjectId()).resources;
        resourceModels.forEach((resId, resourceModel) -> {
            if (((Map) params).containsKey(String.valueOf(resId))) {
                Object value = ((Map) params).get((String.valueOf(resId)));
                resources.add(LwM2mSingleResource.newResource(resId,
                        converter.convertValue(value, equalsResourceTypeGetSimpleName(value), resourceModel.type, pathIds), resourceModel.type));

            }
        });
        return resources;
    }

    public boolean isValidObjectVersion(String path) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(path));
        String verSupportedObject = registration.getSupportedObject().get(pathIds.getObjectId());
        String verRez = getVerFromPathIdVerOrId(path);
        return verRez == null ? LWM2M_VERSION_DEFAULT.equals(verSupportedObject) : verRez.equals(verSupportedObject);
    }

    /**
     * @param pathIdVer     == "3_1.0"
     * @param modelProvider -
     */
    public void deleteResources(String pathIdVer, LwM2mModelProvider modelProvider) {
        Set<String> key = getKeysEqualsIdVer(pathIdVer);
        key.forEach(pathRez -> {
            LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathRez));
            ResourceModel resourceModel = modelProvider.getObjectModel(registration).getResourceModel(pathIds.getObjectId(), pathIds.getResourceId());
            if (resourceModel != null) {
                this.resources.get(pathRez).setResourceModel(resourceModel);
            } else {
                this.resources.remove(pathRez);
            }
        });
    }

    /**
     * @param idVer         -
     * @param modelProvider -
     */
    public void updateResourceModel(String idVer, LwM2mModelProvider modelProvider) {
        Set<String> key = getKeysEqualsIdVer(idVer);
        key.forEach(k -> this.saveResourceModel(k, modelProvider));
    }

    private void saveResourceModel(String pathRez, LwM2mModelProvider modelProvider) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(pathRez));
        ResourceModel resourceModel = modelProvider.getObjectModel(registration).getResourceModel(pathIds.getObjectId(), pathIds.getResourceId());
        this.resources.get(pathRez).setResourceModel(resourceModel);
    }

    private Set<String> getKeysEqualsIdVer(String idVer) {
        return this.resources.keySet()
                .stream()
                .filter(e -> idVer.equals(e.split(LWM2M_SEPARATOR_PATH)[1]))
                .collect(Collectors.toSet());
    }

    public ContentFormat getDefaultContentFormat() {
        if (registration == null) {
            return ContentFormat.DEFAULT;
        } else if (registration.getLwM2mVersion().equals("1.0")) {
            return ContentFormat.TLV;
        } else {
            return ContentFormat.TEXT;
        }
    }

}

