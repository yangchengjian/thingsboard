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

import { Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuthUser, selectIsAuthenticated } from '../auth/auth.selectors';
import { take } from 'rxjs/operators';
import { HomeSection } from '@core/services/home.models';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { AuthState } from '@app/core/auth/auth.models';
import { AuthUser } from '@shared/models/user.model';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { guid } from '@core/utils';

@Injectable({
  providedIn: 'root'
})
export class HomeService {

  homeSections$: Subject<Array<HomeSection>> = new BehaviorSubject<Array<HomeSection>>([]);

  authState: AuthState = getCurrentAuthState(this.store);

  constructor(private store: Store<AppState>, private authService: AuthService) {
    this.store.pipe(select(selectIsAuthenticated)).subscribe(
      (authenticated: boolean) => {
        if (authenticated) {
          this.buildMenu();
        }
      }
    );
  }

  private buildMenu() {
    this.store.pipe(select(selectAuthUser), take(1)).subscribe(
      (authUser: AuthUser) => {
        if (authUser) {
          let homeSections: Array<HomeSection>;
          switch (authUser.authority) {
            case Authority.SYS_ADMIN:
              homeSections = this.buildSysAdminHome(authUser);
              break;
            case Authority.TENANT_ADMIN:
              homeSections = this.buildTenantAdminHome(authUser);
              break;
            case Authority.CUSTOMER_USER:
              homeSections = this.buildCustomerUserHome(authUser);
              break;
          }
          this.homeSections$.next(homeSections);
        }
      }
    );
  }

  private buildSysAdminHome(authUser: any): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [];
    homeSections.push(
      {
        name: 'tenant.management',
        places: [
          {
            name: 'tenant.tenants',
            icon: 'supervisor_account',
            path: '/tenants'
          },
          {
            name: 'tenant-profile.tenant-profiles',
            icon: 'mdi:alpha-t-box',
            isMdiIcon: true,
            path: '/tenantProfiles'
          },
        ]
      },
      {
        name: 'widget.management',
        places: [
          {
            name: 'widget.widget-library',
            icon: 'now_widgets',
            path: '/widgets-bundles'
          }
        ]
      },
      {
        name: 'admin.system-settings',
        places: [
          {
            name: 'admin.general',
            icon: 'settings_applications',
            path: '/settings/general'
          },
          {
            name: 'admin.outgoing-mail',
            icon: 'mail',
            path: '/settings/outgoing-mail'
          },
          {
            name: 'admin.sms-provider',
            icon: 'sms',
            path: '/settings/sms-provider'
          },
          {
            name: 'admin.security-settings',
            icon: 'security',
            path: '/settings/security-settings'
          },
          {
            name: 'admin.oauth2.oauth2',
            icon: 'security',
            path: '/settings/oauth2'
          }
        ]
      }
    );
    return homeSections;
  }

  private buildTenantAdminHome(authUser: any): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [];
    homeSections.push(
      {
        name: 'solution.management',
        places: [
          {
            name: 'solution.bot-customer-service',
            icon: 'bot-customer-service',
            isMdiIcon: true,
            path: '/bot-customer-service'
          },
          {
            name: 'solution.bot-shopping-guide',
            icon: 'bot-shopping-guide',
            isMdiIcon: true,
            path: '/bot-shopping-guide'
          },
          {
            name: 'solution.bot-house-keeper',
            icon: 'bot-house-keeper',
            isMdiIcon: true,
            path: '/bot-house-keeper'
          }
        ]
      },
      {
        name: 'customer.management',
        places: [
          {
            name: 'customer.customers',
            icon: 'customer',
            isMdiIcon: true,
            path: '/customers'
          }
        ]
      },
      {
        name: 'asset.management',
        places: [
          {
            name: 'asset.assets',
            icon: 'asset',
            isMdiIcon: true,
            path: '/assets'
          },
          {
            name: 'device.devices',
            icon: 'device',
            isMdiIcon: true,
            path: '/devices'
          },
          {
            name: 'device-profile.device-profiles',
            icon: 'device-profile',
            isMdiIcon: true,
            path: '/deviceProfiles'
          },
          {
            name: 'entity-view.entity-views',
            icon: 'view',
            isMdiIcon: true,
            path: '/entityViews'
          }
        ]
      }
    );
    if (this.authState.dashboardEditable) {
      homeSections.push(
        {
          name: 'dashboard.management',
          places: [
            {
              name: 'rulechain.rulechains',
              icon: 'chain',
              isMdiIcon: true,
              path: '/ruleChains'
            },
            {
              name: 'widget.widget-library',
              icon: 'widget',
              isMdiIcon: true,
              path: '/widgets-bundles'
            },
            {
              name: 'dashboard.dashboards',
              icon: 'app',
              isMdiIcon: true,
              path: '/dashboards'
            }
          ]
        }
      );
    } else {
      homeSections.push(
        {
          name: 'dashboard.management',
          places: [
            {
              name: 'dashboard.dashboards',
              icon: 'app',
              isMdiIcon: true,
              path: '/dashboards'
            }
          ]
        }
      );
    }
    homeSections.push(
      {
        name: 'audit-log.audit',
        places: [
          {
            name: 'audit-log.audit-logs',
            icon: 'log',
            isMdiIcon: true,
            path: '/auditLogs'
          },
          {
            name: 'api-usage.api-usage',
            icon: 'usage',
            isMdiIcon: true,
            path: '/usage'
          }
        ]
      }
    );
    return homeSections;
  }

  private buildCustomerUserHome(authUser: any): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [
      {
        name: 'asset.view-assets',
        places: [
          {
            name: 'asset.assets',
            icon: 'asset',
            isMdiIcon: true,
            path: '/assets'
          },
          {
            name: 'device.devices',
            icon: 'device',
            isMdiIcon: true,
            path: '/devices'
          },
          {
            name: 'entity-view.entity-views',
            icon: 'view',
            isMdiIcon: true,
            path: '/entityViews'
          }
        ]
      },
      {
        name: 'dashboard.view-dashboards',
        places: [
          {
            name: 'dashboard.dashboards',
            icon: 'app',
            isMdiIcon: true,
            path: '/dashboards'
          }
        ]
      }
    ];
    return homeSections;
  }

  public homeSections(): Observable<Array<HomeSection>> {
    return this.homeSections$;
  }

}

