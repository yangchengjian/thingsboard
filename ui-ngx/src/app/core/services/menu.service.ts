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
import { MenuSection } from '@core/services/menu.models';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { AuthState } from '@app/core/auth/auth.models';
import { AuthUser } from '@shared/models/user.model';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { guid } from '@core/utils';

@Injectable({
  providedIn: 'root'
})
export class MenuService {

  menuSections$: Subject<Array<MenuSection>> = new BehaviorSubject<Array<MenuSection>>([]);

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
          let menuSections: Array<MenuSection>;
          switch (authUser.authority) {
            case Authority.SYS_ADMIN:
              menuSections = this.buildSysAdminMenu(authUser);
              break;
            case Authority.TENANT_ADMIN:
              menuSections = this.buildTenantAdminMenu(authUser);
              break;
            case Authority.CUSTOMER_USER:
              menuSections = this.buildCustomerUserMenu(authUser);
              break;
          }
          this.menuSections$.next(menuSections);
        }
      }
    );
  }

  private buildSysAdminMenu(authUser: any): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'home'
      },
      {
        id: guid(),
        name: 'tenant.tenants',
        type: 'link',
        path: '/tenants',
        icon: 'supervisor_account'
      },
      {
        id: guid(),
        name: 'tenant-profile.tenant-profiles',
        type: 'link',
        path: '/tenantProfiles',
        icon: 'mdi:alpha-t-box',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'widget.widget-library',
        type: 'link',
        path: '/widgets-bundles',
        icon: 'now_widgets'
      },
      {
        id: guid(),
        name: 'admin.system-settings',
        type: 'toggle',
        path: '/settings',
        height: '200px',
        icon: 'settings',
        pages: [
          {
            id: guid(),
            name: 'admin.general',
            type: 'link',
            path: '/settings/general',
            icon: 'settings_applications'
          },
          {
            id: guid(),
            name: 'admin.outgoing-mail',
            type: 'link',
            path: '/settings/outgoing-mail',
            icon: 'mail'
          },
          {
            id: guid(),
            name: 'admin.sms-provider',
            type: 'link',
            path: '/settings/sms-provider',
            icon: 'sms'
          },
          {
            id: guid(),
            name: 'admin.security-settings',
            type: 'link',
            path: '/settings/security-settings',
            icon: 'security'
          },
          {
            id: guid(),
            name: 'admin.oauth2.oauth2',
            type: 'link',
            path: '/settings/oauth2',
            icon: 'security'
          }
        ]
      }
    );
    return sections;
  }

  private buildTenantAdminMenu(authUser: any): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'customer.customers',
        type: 'link',
        path: '/customers',
        icon: 'supervisor_account'
      },
      {
        id: guid(),
        name: 'asset.assets',
        type: 'toggle',
        path: '/assets',
        height: '200px',
        icon: 'domain',
        pages: [
          {
            id: guid(),
            name: 'asset.assets',
            type: 'link',
            path: '/assets',
            icon: 'domain'
          },
          {
            id: guid(),
            name: 'device.devices',
            type: 'link',
            path: '/devices',
            icon: 'devices_other'
          },
          {
            id: guid(),
            name: 'device-profile.device-profiles',
            type: 'link',
            path: '/deviceProfiles',
            icon: 'mdi:alpha-d-box',
            isMdiIcon: true
          },
          {
            id: guid(),
            name: 'entity-view.entity-views',
            type: 'link',
            path: '/entityViews',
            icon: 'view_quilt'
          }
        ]
      }
    );
    if (this.authState.dashboardEditable) {
      sections.push(
        {
          id: guid(),
          name: 'rulechain.rulechains',
          type: 'link',
          divider: true,
          path: '/ruleChains',
          icon: 'settings_ethernet'
        },
        {
          id: guid(),
          name: 'widget.widget-library',
          type: 'link',
          path: '/widgets-bundles',
          icon: 'now_widgets'
        }
      );
    }
    sections.push(
      {
        id: guid(),
        name: 'dashboard.dashboards',
        type: 'link',
        path: '/dashboards',
        icon: 'dashboards'
      },
      {
        id: guid(),
        name: 'audit-log.audit-logs',
        type: 'link',
        divider: true,
        path: '/auditLogs',
        icon: 'track_changes'
      },
      {
        id: guid(),
        name: 'api-usage.api-usage',
        type: 'link',
        path: '/usage',
        icon: 'insert_chart',
        notExact: true
      }
    );
    return sections;
  }

  private buildCustomerUserMenu(authUser: any): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        notExact: true,
        icon: 'home'
      },
      {
        id: guid(),
        name: 'asset.assets',
        type: 'link',
        path: '/assets',
        icon: 'domain'
      },
      {
        id: guid(),
        name: 'device.devices',
        type: 'link',
        path: '/devices',
        icon: 'devices_other'
      },
      {
        id: guid(),
        name: 'entity-view.entity-views',
        type: 'link',
        path: '/entityViews',
        icon: 'view_quilt'
      },
      {
        id: guid(),
        name: 'dashboard.dashboards',
        type: 'link',
        path: '/dashboards',
        icon: 'dashboard'
      }
    );
    return sections;
  }

  public menuSections(): Observable<Array<MenuSection>> {
    return this.menuSections$;
  }

}

