///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { ActivatedRouteSnapshot, Resolve, Router } from '@angular/router';

import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { Customer } from '@app/shared/models/customer.model';
import { CustomerService } from '@app/core/http/customer.service';
import { CustomerComponent } from '@modules/home/pages/customer/customer.component';
import { CustomerTabsComponent } from '@home/pages/customer/customer-tabs.component';

import { Observable, of } from 'rxjs';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { selectAuth } from '@core/auth/auth.selectors';
import { selectAuthUser } from '@core/auth/auth.selectors';
import { map, mergeMap, take, tap } from 'rxjs/operators';
import { AuthService } from '@core/auth/auth.service';
import { Authority } from '@app/shared/models/authority.enum';

@Injectable()
export class CustomersTableConfigResolver implements Resolve<EntityTableConfig<Customer>> {

  private readonly config: EntityTableConfig<Customer> = new EntityTableConfig<Customer>();

  private customerId: string;

  constructor(private store: Store<AppState>,
              private authService: AuthService,
              private customerService: CustomerService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private router: Router) {

    this.config.entityType = EntityType.CUSTOMER;
    this.config.entityComponent = CustomerComponent;
    this.config.entityTabsComponent = CustomerTabsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.CUSTOMER);
    this.config.entityResources = entityTypeResources.get(EntityType.CUSTOMER);

    this.config.columns.push(
      new DateEntityTableColumn<Customer>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<Customer>('title', 'customer.title', '25%'),
      new EntityTableColumn<Customer>('email', 'contact.email', '25%'),
      new EntityTableColumn<Customer>('country', 'contact.country', '25%'),
      new EntityTableColumn<Customer>('city', 'contact.city', '25%')
    );

    this.config.cellActionDescriptors.push(
      {
        name: this.translate.instant('customer.manage-customers'),
        icon: 'device_hub',
        isEnabled: (customer) => !customer.additionalInfo || !customer.additionalInfo.isPublic,
        onAction: ($event, entity) => this.manageCustomers($event, entity)
      },
      {
        name: this.translate.instant('customer.manage-customer-users'),
        icon: 'account_circle',
        isEnabled: (customer) => !customer.additionalInfo || !customer.additionalInfo.isPublic,
        onAction: ($event, entity) => this.manageCustomerUsers($event, entity)
      },
      {
        name: this.translate.instant('customer.manage-customer-assets'),
        nameFunction: (customer) => {
          return customer.additionalInfo && customer.additionalInfo.isPublic
          ? this.translate.instant('customer.manage-public-assets')
          : this.translate.instant('customer.manage-customer-assets');
        },
        icon: 'domain',
        isEnabled: (customer) => true,
        onAction: ($event, entity) => this.manageCustomerAssets($event, entity)
      },
      {
        name: this.translate.instant('customer.manage-customer-devices'),
        nameFunction: (customer) => {
          return customer.additionalInfo && customer.additionalInfo.isPublic
            ? this.translate.instant('customer.manage-public-devices')
            : this.translate.instant('customer.manage-customer-devices');
        },
        icon: 'devices_other',
        isEnabled: (customer) => true,
        onAction: ($event, entity) => this.manageCustomerDevices($event, entity)
      },
      {
        name: this.translate.instant('customer.manage-customer-dashboards'),
        nameFunction: (customer) => {
          return customer.additionalInfo && customer.additionalInfo.isPublic
            ? this.translate.instant('customer.manage-public-dashboards')
            : this.translate.instant('customer.manage-customer-dashboards');
        },
        icon: 'dashboard',
        isEnabled: (customer) => true,
        onAction: ($event, entity) => this.manageCustomerDashboards($event, entity)
      }
    );

    this.config.deleteEntityTitle = customer => this.translate.instant('customer.delete-customer-title', { customerTitle: customer.title });
    this.config.deleteEntityContent = () => this.translate.instant('customer.delete-customer-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('customer.delete-customers-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('customer.delete-customers-text');

    this.config.loadEntity = id => this.customerService.getCustomer(id.id);
    this.config.deleteEntity = id => this.customerService.deleteCustomer(id.id);
    this.config.onEntityAction = action => this.onCustomerAction(action);
    this.config.deleteEnabled = (customer) => customer && (!customer.additionalInfo || !customer.additionalInfo.isPublic);
    this.config.entitySelectionEnabled = (customer) => customer && (!customer.additionalInfo || !customer.additionalInfo.isPublic);
    this.config.detailsReadonly = (customer) => customer && customer.additionalInfo && customer.additionalInfo.isPublic;
  }

  // resolve(route: ActivatedRouteSnapshot): EntityTableConfig<Customer> {
  //   this.config.tableTitle = this.translate.instant('customer.customers');

  //   const routeParams = route.params;
  //   this.customerId = routeParams.customerId;
  //   console.log('routeParams.customerId: ' + routeParams.customerId);
  //   if(this.customerId === undefined) {
  //     this.config.entitiesFetchFunction = pageLink => this.customerService.getCustomers(pageLink);
  //     this.config.saveEntity = customer => this.customerService.saveCustomer(customer);
  //   } else {
  //     this.config.entitiesFetchFunction = pageLink => this.customerService.getCustomersByParentId(this.customerId, pageLink);
  //     this.config.saveEntity = customer => this.customerService.saveCustomerAfterAddParentId(this.customerId, customer);
  //   }
  //   return this.config;
  // }
  resolve(route: ActivatedRouteSnapshot): Observable<EntityTableConfig<Customer>> {
    const routeParams = route.params;
    this.customerId = routeParams.customerId;
    return this.store.pipe(select(selectAuthUser), take(1)).pipe(
      tap((authUser) => {
          if (this.customerId !== undefined) {
            this.config.entitiesFetchFunction = pageLink => this.customerService.getCustomersByParentId(this.customerId, pageLink);
            this.config.saveEntity = customer => this.customerService.saveCustomerAfterAddParentId(this.customerId, customer);
          } else {
            if (authUser.authority === Authority.TENANT_ADMIN) {
              this.config.entitiesFetchFunction = pageLink => this.customerService.getCustomersByParentId(authUser.tenantId, pageLink);
              this.config.saveEntity = customer => this.customerService.saveCustomerAfterAddParentId(authUser.tenantId, customer);
            } else if(authUser.authority === Authority.CUSTOMER_USER) {
              this.config.entitiesFetchFunction = pageLink => this.customerService.getCustomersByParentId(authUser.customerId, pageLink);
              this.config.saveEntity = customer => this.customerService.saveCustomerAfterAddParentId(authUser.customerId, customer);
            }
          }
      }),
      mergeMap(() =>
        this.customerId ? this.customerService.getCustomer(this.customerId) : of(null as Customer)
      ),
      map((parentCustomer) => {
        if (parentCustomer) {
          this.config.tableTitle = parentCustomer.title + ': ' + this.translate.instant('customer.manage-customers');
        } else {
          this.config.tableTitle = this.translate.instant('customer.manage-customers');
        }
        return this.config;
      })
    );
  }

  manageCustomers($event: Event, customer: Customer) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`customers/${customer.id.id}/childs`);
  }

  manageCustomerUsers($event: Event, customer: Customer) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`customers/${customer.id.id}/users`);
  }

  manageCustomerAssets($event: Event, customer: Customer) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`customers/${customer.id.id}/assets`);
  }

  manageCustomerDevices($event: Event, customer: Customer) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`customers/${customer.id.id}/devices`);
  }

  manageCustomerDashboards($event: Event, customer: Customer) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`customers/${customer.id.id}/dashboards`);
  }

  onCustomerAction(action: EntityAction<Customer>): boolean {
    switch (action.action) {
      case 'manageCustomers':
        this.manageCustomers(action.event, action.entity);
        return true;
      case 'manageUsers':
        this.manageCustomerUsers(action.event, action.entity);
        return true;
      case 'manageAssets':
        this.manageCustomerAssets(action.event, action.entity);
        return true;
      case 'manageDevices':
        this.manageCustomerDevices(action.event, action.entity);
        return true;
      case 'manageDashboards':
        this.manageCustomerDashboards(action.event, action.entity);
        return true;
    }
    return false;
  }

}
