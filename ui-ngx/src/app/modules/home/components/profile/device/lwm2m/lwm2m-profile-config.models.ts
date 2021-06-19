///
/// Copyright © 2016-2021 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

export const PAGE_SIZE_LIMIT = 50;
export const INSTANCES = 'instances';
export const INSTANCE = 'instance';
export const RESOURCES = 'resources';
export const ATTRIBUTE_LWM2M = 'attributeLwm2m';
export const CLIENT_LWM2M = 'clientLwM2M';
export const OBSERVE_ATTR_TELEMETRY = 'observeAttrTelemetry';
export const OBSERVE = 'observe';
export const ATTRIBUTE = 'attribute';
export const TELEMETRY = 'telemetry';
export const KEY_NAME = 'keyName';
export const DEFAULT_ID_SERVER = 123;
export const DEFAULT_ID_BOOTSTRAP = 111;
export const DEFAULT_LOCAL_HOST_NAME = 'localhost';
export const DEFAULT_PORT_SERVER_NO_SEC = 5685;
export const DEFAULT_PORT_BOOTSTRAP_NO_SEC = 5687;
export const DEFAULT_CLIENT_HOLD_OFF_TIME = 1;
export const DEFAULT_LIFE_TIME = 300;
export const DEFAULT_MIN_PERIOD = 1;
export const DEFAULT_NOTIF_IF_DESIBLED = true;
export const DEFAULT_BINDING = 'UQ';
export const DEFAULT_BOOTSTRAP_SERVER_ACCOUNT_TIME_OUT = 0;
export const LEN_MAX_PUBLIC_KEY_RPK = 182;
export const LEN_MAX_PUBLIC_KEY_X509 = 3000;
export const KEY_REGEXP_HEX_DEC = /^[-+]?[0-9A-Fa-f]+\.?[0-9A-Fa-f]*?$/;
export const KEY_REGEXP_NUMBER = /^(\-?|\+?)\d*$/;
export const INSTANCES_ID_VALUE_MIN = 0;
export const INSTANCES_ID_VALUE_MAX = 65535;
export const DEFAULT_OTA_UPDATE_PROTOCOL = 'coap://';
export const DEFAULT_FW_UPDATE_RESOURCE = DEFAULT_OTA_UPDATE_PROTOCOL + DEFAULT_LOCAL_HOST_NAME + ':' + DEFAULT_PORT_SERVER_NO_SEC;
export const DEFAULT_SW_UPDATE_RESOURCE = DEFAULT_OTA_UPDATE_PROTOCOL + DEFAULT_LOCAL_HOST_NAME + ':' + DEFAULT_PORT_SERVER_NO_SEC;


export enum BINDING_MODE {
  U = 'U',
  UQ = 'UQ',
  T = 'T',
  TQ = 'TQ',
  S = 'S',
  SQ = 'SQ',
  US = 'US',
  TS = 'TS',
  UQS = 'UQS',
  TQS = 'TQS'
}

export const BINDING_MODE_NAMES = new Map<BINDING_MODE, string>(
  [
    [BINDING_MODE.U, 'U: UDP connection in standard mode'],
    [BINDING_MODE.UQ, 'UQ: UDP connection in queue mode'],
    [BINDING_MODE.US, 'US: both UDP and SMS connections active, both in standard mode'],
    [BINDING_MODE.UQS, 'UQS: both UDP and SMS connections active; UDP in queue mode, SMS in standard mode'],
    [BINDING_MODE.T, 'T: TCP connection in standard mode'],
    [BINDING_MODE.TQ, 'TQ: TCP connection in queue mode'],
    [BINDING_MODE.TS, 'TS: both TCP and SMS connections active, both in standard mode'],
    [BINDING_MODE.TQS, 'TQS: both TCP and SMS connections active; TCP in queue mode, SMS in standard mode'],
    [BINDING_MODE.S, 'S: SMS connection in standard mode'],
    [BINDING_MODE.SQ, 'SQ: SMS connection in queue mode']
  ]
);

export enum ATTRIBUTE_LWM2M_ENUM {
  dim = 'dim',
  ver = 'ver',
  pmin = 'pmin',
  pmax = 'pmax',
  gt = 'gt',
  lt = 'lt',
  st = 'st'
}

export const ATTRIBUTE_LWM2M_LABEL = new Map<ATTRIBUTE_LWM2M_ENUM, string>(
  [
    [ATTRIBUTE_LWM2M_ENUM.dim, 'dim='],
    [ATTRIBUTE_LWM2M_ENUM.ver, 'ver='],
    [ATTRIBUTE_LWM2M_ENUM.pmin, 'pmin='],
    [ATTRIBUTE_LWM2M_ENUM.pmax, 'pmax='],
    [ATTRIBUTE_LWM2M_ENUM.gt, '>'],
    [ATTRIBUTE_LWM2M_ENUM.lt, '<'],
    [ATTRIBUTE_LWM2M_ENUM.st, 'st=']
  ]
);

export const ATTRIBUTE_LWM2M_MAP = new Map<ATTRIBUTE_LWM2M_ENUM, string>(
  [
    [ATTRIBUTE_LWM2M_ENUM.dim, 'Dimension'],
    [ATTRIBUTE_LWM2M_ENUM.ver, 'Object version'],
    [ATTRIBUTE_LWM2M_ENUM.pmin, 'Minimum period'],
    [ATTRIBUTE_LWM2M_ENUM.pmax, 'Maximum period'],
    [ATTRIBUTE_LWM2M_ENUM.gt, 'Greater than'],
    [ATTRIBUTE_LWM2M_ENUM.lt, 'Lesser than'],
    [ATTRIBUTE_LWM2M_ENUM.st, 'Step'],

  ]
);

export const ATTRIBUTE_KEYS = Object.keys(ATTRIBUTE_LWM2M_ENUM) as string[];

export enum securityConfigMode {
  PSK = 'PSK',
  RPK = 'RPK',
  X509 = 'X509',
  NO_SEC = 'NO_SEC'
}

export const securityConfigModeNames = new Map<securityConfigMode, string>(
  [
    [securityConfigMode.PSK, 'Pre-Shared Key'],
    [securityConfigMode.RPK, 'Raw Public Key'],
    [securityConfigMode.X509, 'X.509 Certificate'],
    [securityConfigMode.NO_SEC, 'No Security']
  ]
);

export interface ModelValue {
  objectIds: string[];
  objectsList: ObjectLwM2M[];
}

export interface BootstrapServersSecurityConfig {
  shortId: number;
  lifetime: number;
  defaultMinPeriod: number;
  notifIfDisabled: boolean;
  binding: string;
}

export interface ServerSecurityConfig {
  host?: string;
  securityHost?: string;
  port?: number;
  securityPort?: number;
  securityMode: securityConfigMode;
  clientPublicKeyOrId?: string;
  clientSecretKey?: string;
  serverPublicKey?: string;
  clientHoldOffTime?: number;
  serverId?: number;
  bootstrapServerAccountTimeout: number;
}

interface BootstrapSecurityConfig {
  servers: BootstrapServersSecurityConfig;
  bootstrapServer: ServerSecurityConfig;
  lwm2mServer: ServerSecurityConfig;
}

export interface Lwm2mProfileConfigModels {
  clientLwM2mSettings: ClientLwM2mSettings;
  observeAttr: ObservableAttributes;
  bootstrap: BootstrapSecurityConfig;
}

export interface ClientLwM2mSettings {
  clientStrategy: string;
  fwUpdateStrategy: number;
  swUpdateStrategy: number;
  fwUpdateRecourse: string;
  swUpdateRecourse: string;
}

export interface ObservableAttributes {
  observe: string[];
  attribute: string[];
  telemetry: string[];
  keyName: {};
  attributeLwm2m: {};
}

export function getDefaultBootstrapServersSecurityConfig(): BootstrapServersSecurityConfig {
  return {
    shortId: DEFAULT_ID_SERVER,
    lifetime: DEFAULT_LIFE_TIME,
    defaultMinPeriod: DEFAULT_MIN_PERIOD,
    notifIfDisabled: DEFAULT_NOTIF_IF_DESIBLED,
    binding: DEFAULT_BINDING
  };
}

export function getDefaultBootstrapServerSecurityConfig(hostname: string): ServerSecurityConfig {
  return {
    host: hostname,
    port: DEFAULT_PORT_BOOTSTRAP_NO_SEC,
    securityMode: securityConfigMode.NO_SEC,
    serverPublicKey: '',
    clientHoldOffTime: DEFAULT_CLIENT_HOLD_OFF_TIME,
    serverId: DEFAULT_ID_BOOTSTRAP,
    bootstrapServerAccountTimeout: DEFAULT_BOOTSTRAP_SERVER_ACCOUNT_TIME_OUT
  };
}

export function getDefaultLwM2MServerSecurityConfig(hostname): ServerSecurityConfig {
  const DefaultLwM2MServerSecurityConfig = getDefaultBootstrapServerSecurityConfig(hostname);
  DefaultLwM2MServerSecurityConfig.port = DEFAULT_PORT_SERVER_NO_SEC;
  DefaultLwM2MServerSecurityConfig.serverId = DEFAULT_ID_SERVER;
  return DefaultLwM2MServerSecurityConfig;
}

function getDefaultProfileBootstrapSecurityConfig(hostname: any): BootstrapSecurityConfig {
  return {
    servers: getDefaultBootstrapServersSecurityConfig(),
    bootstrapServer: getDefaultBootstrapServerSecurityConfig(hostname),
    lwm2mServer: getDefaultLwM2MServerSecurityConfig(hostname)
  };
}

function getDefaultProfileObserveAttrConfig(): ObservableAttributes {
  return {
    observe: [],
    attribute: [],
    telemetry: [],
    keyName: {},
    attributeLwm2m: {}
  };
}

export function getDefaultProfileConfig(hostname?: any): Lwm2mProfileConfigModels {
  return {
    clientLwM2mSettings: getDefaultProfileClientLwM2mSettingsConfig(),
    observeAttr: getDefaultProfileObserveAttrConfig(),
    bootstrap: getDefaultProfileBootstrapSecurityConfig((hostname) ? hostname : DEFAULT_LOCAL_HOST_NAME)
  };
}

function getDefaultProfileClientLwM2mSettingsConfig(): ClientLwM2mSettings {
  return {
    clientStrategy: '1',
    fwUpdateStrategy: 1,
    swUpdateStrategy: 1,
    fwUpdateRecourse: DEFAULT_FW_UPDATE_RESOURCE,
    swUpdateRecourse: DEFAULT_SW_UPDATE_RESOURCE
  };
}

export interface ResourceLwM2M {
  id: number;
  name: string;
  observe: boolean;
  attribute: boolean;
  telemetry: boolean;
  keyName: string;
  attributeLwm2m?: {};
}

export interface Instance {
  id: number;
  attributeLwm2m?: {};
  resources: ResourceLwM2M[];
}

/**
 * multiple  == true  => Multiple
 * multiple  == false => Single
 * mandatory == true  => Mandatory
 * mandatory == false => Optional
 */
export interface ObjectLwM2M {

  id: number;
  keyId: string;
  name: string;
  multiple?: boolean;
  mandatory?: boolean;
  attributeLwm2m?: {};
  instances?: Instance [];
}
