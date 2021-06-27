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

import { Component, EventEmitter, forwardRef, Input, Output } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR } from '@angular/forms';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { isEmpty, isUndefinedOrNull } from '@core/utils';
import { Lwm2mAttributesDialogComponent, Lwm2mAttributesDialogData } from './lwm2m-attributes-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { AttributesNameValueMap } from './lwm2m-profile-config.models';


@Component({
  selector: 'tb-profile-lwm2m-attributes',
  templateUrl: './lwm2m-attributes.component.html',
  styleUrls: ['./lwm2m-attributes.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => Lwm2mAttributesComponent),
    multi: true
  }]
})
export class Lwm2mAttributesComponent implements ControlValueAccessor {
  attributeLwm2mFormGroup: FormGroup;

  private requiredValue: boolean;

  @Input()
  isAttributeTelemetry: boolean;

  @Input()
  modelName: string;

  @Input()
  disabled: boolean;

  @Input()
  isResource = false;

  @Output()
  updateAttributeLwm2m = new EventEmitter<any>();

  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  private propagateChange = (v: any) => {
  }

  constructor(private dialog: MatDialog,
              private fb: FormBuilder) {}

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.attributeLwm2mFormGroup.disable({emitEvent: false});
    } else {
      this.attributeLwm2mFormGroup.enable({emitEvent: false});
    }
  }

  ngOnInit() {
    this.attributeLwm2mFormGroup = this.fb.group({
      attributes: [{}]
    });
  }

  writeValue(value: AttributesNameValueMap | null) {
    this.attributeLwm2mFormGroup.patchValue({attributes: value}, {emitEvent: false});
  }

  get attributesValueMap(): AttributesNameValueMap {
    return this.attributeLwm2mFormGroup.get('attributes').value;
  }

  isDisableBtn(): boolean {
    return !this.disabled && this.isAttributeTelemetry;
  }

  isEmpty(): boolean {
    const value = this.attributesValueMap;
    return isUndefinedOrNull(value) || isEmpty(value);
  }

  get tooltipSetAttributesTelemetry(): string {
    return this.isDisableBtn() ? 'device-profile.lwm2m.edit-attributes-select' : '';
  }

  get tooltipButton(): string {
    if (this.disabled) {
      return 'device-profile.lwm2m.view-attribute';
    } else if (this.isEmpty()) {
      return 'device-profile.lwm2m.add-attribute';
    }
    return 'device-profile.lwm2m.edit-attribute';
  }

  get iconButton(): string {
    if (this.disabled) {
      return 'visibility';
    } else if (this.isEmpty()) {
      return 'add';
    }
    return 'edit';
  }

  public editAttributesLwm2m = ($event: Event): void => {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<Lwm2mAttributesDialogComponent, Lwm2mAttributesDialogData, AttributesNameValueMap>(Lwm2mAttributesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        readonly: this.disabled,
        attributes: this.attributesValueMap,
        modelName: this.modelName,
        isResource: this.isResource
      }
    }).afterClosed().subscribe((result) => {
      if (result) {
        this.attributeLwm2mFormGroup.patchValue({attributeLwm2m: result});
        this.updateAttributeLwm2m.next(result);
      }
    });
  }
}
