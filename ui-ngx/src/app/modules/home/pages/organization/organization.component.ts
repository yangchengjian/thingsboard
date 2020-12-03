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

import { Component} from '@angular/core';
import { TreeComponent } from '@home/components/tree/tree.component';

@Component({
  selector: 'tb-organization',
  templateUrl: './organization.component.html',
  styleUrls: ['./organization.component.scss']
})
export class OrganizationComponent {
  
  TREE_DATA = {
    华南: {
      '福州': null,
      '广州': null,
      '厦门': null,
      深圳: {
        南山: null,
        宝安: ['校区一', '校区二'],
        福田: null
      }
    },
    华东: [
      '上海',
      '杭州',
      '苏州'
    ]
  };

  organizationTree = this.TREE_DATA;
}
