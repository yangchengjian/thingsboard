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

import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  NgZone,
  OnInit,
  QueryList,
  ViewChild,
  ViewChildren,
  ViewContainerRef
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { WidgetAction, WidgetContext } from '@home/models/widget-component.models';
import {
  DataKey,
  Datasource,
  DatasourceData,
  DatasourceType,
  WidgetActionDescriptor,
  WidgetConfig
} from '@shared/models/widget.models';
import { UtilsService } from '@core/services/utils.service';
import { TranslateService } from '@ngx-translate/core';
import { hashCode, isDefined, isNumber, isObject, isUndefined } from '@core/utils';
import cssjs from '@core/css/css';
import { PageLink } from '@shared/models/page/page-link';
import { Direction, SortOrder, sortOrderFromString } from '@shared/models/page/sort-order';
import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { BehaviorSubject, fromEvent, merge, Observable, of, Subscription } from 'rxjs';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import { catchError, debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import {
  CellContentInfo,
  CellStyleInfo,
  constructTableCssString,
  getCellContentInfo,
  getCellStyleInfo,
  getRowStyleInfo,
  RowStyleInfo,
  TableWidgetDataKeySettings, TableWidgetSettings
} from '@home/components/widget/lib/table-widget.models';
import { Overlay } from '@angular/cdk/overlay';
import { SubscriptionEntityInfo } from '@core/api/widget-api.models';
import { DatePipe } from '@angular/common';

export interface TimeseriesTableWidgetSettings extends TableWidgetSettings {
  showTimestamp: boolean;
  showMilliseconds: boolean;
  hideEmptyLines: boolean;
}

interface TimeseriesRow {
  [col: number]: any;
  formattedTs: string;
}

interface TimeseriesHeader {
  index: number;
  dataKey: DataKey;
}

interface TimeseriesTableSource {
  keyStartIndex: number;
  keyEndIndex: number;
  datasource: Datasource;
  rawData: Array<DatasourceData>;
  data: TimeseriesRow[];
  pageLink: PageLink;
  displayedColumns: string[];
  timeseriesDatasource: TimeseriesDatasource;
  header: TimeseriesHeader[];
  stylesInfo: CellStyleInfo[];
  contentsInfo: CellContentInfo[];
  rowDataTemplate: {[key: string]: any};
}

@Component({
  selector: 'tb-timeseries-table-widget',
  templateUrl: './timeseries-table-widget.component.html',
  styleUrls: ['./timeseries-table-widget.component.scss', './table-widget.scss']
})
export class TimeseriesTableWidgetComponent extends PageComponent implements OnInit, AfterViewInit {

  @Input()
  ctx: WidgetContext;

  @ViewChild('searchInput') searchInputField: ElementRef;
  @ViewChildren(MatPaginator) paginators: QueryList<MatPaginator>;
  @ViewChildren(MatSort) sorts: QueryList<MatSort>;

  public displayPagination = true;
  public enableStickyHeader = true;
  public enableStickyAction = true;
  public pageSizeOptions;
  public textSearchMode = false;
  public textSearch: string = null;
  public actionCellDescriptors: WidgetActionDescriptor[];
  public sources: TimeseriesTableSource[];
  public sourceIndex: number;

  private cellContentCache: Array<any> = [];
  private cellStyleCache: Array<any> = [];
  private rowStyleCache: Array<any> = [];

  private settings: TimeseriesTableWidgetSettings;
  private widgetConfig: WidgetConfig;
  private data: Array<DatasourceData>;
  private datasources: Array<Datasource>;

  private defaultPageSize = 10;
  private defaultSortOrder = '-0';
  private hideEmptyLines = false;
  public showTimestamp = true;
  private useEntityLabel = false;
  private dateFormatFilter: string;

  private rowStylesInfo: RowStyleInfo;

  private subscriptions: Subscription[] = [];

  private searchAction: WidgetAction = {
    name: 'action.search',
    show: true,
    icon: 'search',
    onAction: () => {
      this.enterFilterMode();
    }
  };

  constructor(protected store: Store<AppState>,
              private elementRef: ElementRef,
              private ngZone: NgZone,
              private overlay: Overlay,
              private viewContainerRef: ViewContainerRef,
              private utils: UtilsService,
              private translate: TranslateService,
              private domSanitizer: DomSanitizer,
              private datePipe: DatePipe) {
    super(store);
  }

  ngOnInit(): void {
    this.ctx.$scope.timeseriesTableWidget = this;
    this.settings = this.ctx.settings;
    this.widgetConfig = this.ctx.widgetConfig;
    this.data = this.ctx.data;
    this.datasources = this.ctx.datasources;
    this.initialize();
    this.ctx.updateWidgetParams();
  }

  ngAfterViewInit(): void {
    fromEvent(this.searchInputField.nativeElement, 'keyup')
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        tap(() => {
          this.sources.forEach((source) => {
            source.pageLink.textSearch = this.textSearch;
            if (this.displayPagination) {
              source.pageLink.page = 0;
            }
          });
          this.loadCurrentSourceRow();
          this.ctx.detectChanges();
        })
      )
      .subscribe();

    this.sorts.changes.subscribe(() => {
      this.initSubscriptionsToSortAndPaginator();
    });

    this.initSubscriptionsToSortAndPaginator();
  }

  public onDataUpdated() {
    this.updateCurrentSourceData();
    this.clearCache();
  }

  private initialize() {
    this.ctx.widgetActions = [this.searchAction ];

    this.actionCellDescriptors = this.ctx.actionsApi.getActionDescriptors('actionCellButton');

    this.searchAction.show = isDefined(this.settings.enableSearch) ? this.settings.enableSearch : true;
    this.displayPagination = isDefined(this.settings.displayPagination) ? this.settings.displayPagination : true;
    this.enableStickyHeader = isDefined(this.settings.enableStickyHeader) ? this.settings.enableStickyHeader : true;
    this.enableStickyAction = isDefined(this.settings.enableStickyAction) ? this.settings.enableStickyAction : true;
    this.hideEmptyLines = isDefined(this.settings.hideEmptyLines) ? this.settings.hideEmptyLines : false;
    this.useEntityLabel = isDefined(this.widgetConfig.settings.useEntityLabel) ? this.widgetConfig.settings.useEntityLabel : false;
    this.showTimestamp = this.settings.showTimestamp !== false;
    this.dateFormatFilter = (this.settings.showMilliseconds !== true) ? 'yyyy-MM-dd HH:mm:ss' :  'yyyy-MM-dd HH:mm:ss.SSS';

    this.rowStylesInfo = getRowStyleInfo(this.settings, 'rowData, ctx');

    const pageSize = this.settings.defaultPageSize;
    if (isDefined(pageSize) && isNumber(pageSize) && pageSize > 0) {
      this.defaultPageSize = pageSize;
    }
    this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];

    let cssString = constructTableCssString(this.widgetConfig);

    const origBackgroundColor = this.widgetConfig.backgroundColor || 'rgb(255, 255, 255)';
    cssString += '.tb-table-widget mat-toolbar.mat-table-toolbar:not([color=primary]) {\n' +
    'background-color: ' + origBackgroundColor + ' !important;\n' +
    '}\n';

    const cssParser = new cssjs();
    cssParser.testMode = false;
    const namespace = 'ts-table-' + hashCode(cssString);
    cssParser.cssPreviewNamespace = namespace;
    cssParser.createStyleElement(namespace, cssString);
    $(this.elementRef.nativeElement).addClass(namespace);
    this.updateDatasources();
  }

  public getTabLabel(source: TimeseriesTableSource){
    if (this.useEntityLabel) {
      return source.datasource.entityLabel || source.datasource.entityName;
    } else {
      return source.datasource.entityName;
    }
  }

  private updateDatasources() {
    this.sources = [];
    this.sourceIndex = 0;
    let keyOffset = 0;
    const pageSize = this.displayPagination ? this.defaultPageSize : Number.POSITIVE_INFINITY;
    if (this.datasources) {
      for (const datasource of this.datasources) {
        const sortOrder: SortOrder = sortOrderFromString(this.defaultSortOrder);
        const source = {} as TimeseriesTableSource;
        source.keyStartIndex = keyOffset;
        keyOffset += datasource.dataKeys.length;
        source.keyEndIndex = keyOffset;
        source.datasource = datasource;
        source.data = [];
        source.rawData = [];
        source.displayedColumns = [];
        source.pageLink = new PageLink(pageSize, 0, null, sortOrder);
        source.header = [];
        source.stylesInfo = [];
        source.contentsInfo = [];
        source.rowDataTemplate = {};
        source.rowDataTemplate.Timestamp = null;
        if (this.showTimestamp) {
          source.displayedColumns.push('0');
        }
        for (let a = 0; a < datasource.dataKeys.length; a++ ) {
          const dataKey = datasource.dataKeys[a];
          const keySettings: TableWidgetDataKeySettings = dataKey.settings;
          const index = a + 1;
          source.header.push({
            index,
            dataKey
          });
          source.displayedColumns.push(index + '');
          source.rowDataTemplate[dataKey.label] = null;
          source.stylesInfo.push(getCellStyleInfo(keySettings, 'value, rowData, ctx'));
          const cellContentInfo = getCellContentInfo(keySettings, 'value, rowData, ctx');
          cellContentInfo.units = dataKey.units;
          cellContentInfo.decimals = dataKey.decimals;
          source.contentsInfo.push(cellContentInfo);
        }
        if (this.actionCellDescriptors.length) {
          source.displayedColumns.push('actions');
        }
        const tsDatasource = new TimeseriesDatasource(source, this.hideEmptyLines, this.dateFormatFilter, this.datePipe, this.ngZone);
        tsDatasource.dataUpdated(this.data);
        this.sources.push(source);
      }
    }
    this.updateActiveEntityInfo();
  }

  private updateActiveEntityInfo() {
    const source = this.sources[this.sourceIndex];
    let activeEntityInfo: SubscriptionEntityInfo = null;
    if (source) {
      const datasource = source.datasource;
      if (datasource.type === DatasourceType.entity &&
        datasource.entityType && datasource.entityId) {
        activeEntityInfo = {
          entityId: {
            entityType: datasource.entityType,
            id: datasource.entityId
          },
          entityName: datasource.entityName,
          entityLabel: datasource.entityLabel,
          entityDescription: datasource.entityDescription
        };
      }
    }
    this.ctx.activeEntityInfo = activeEntityInfo;
  }

  private initSubscriptionsToSortAndPaginator() {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.sorts.forEach((sort, index) => {
      let paginator = null;
      const observables = [sort.sortChange];
      if (this.displayPagination) {
        paginator = this.paginators.toArray()[index];
        this.subscriptions.push(
          sort.sortChange.subscribe(() => paginator.pageIndex = 0)
        );
        observables.push(paginator.page);
      }
      this.updateData(sort, paginator);
      this.subscriptions.push(merge(...observables).pipe(
        tap(() => this.updateData(sort, paginator))
      ).subscribe());
    });
  }

  onSourceIndexChanged() {
    this.updateCurrentSourceData();
    this.updateActiveEntityInfo();
    this.clearCache();
  }

  private enterFilterMode() {
    this.textSearchMode = true;
    this.textSearch = '';
    this.sources.forEach((source) => {
      source.pageLink.textSearch = this.textSearch;
    });
    this.ctx.hideTitlePanel = true;
    this.ctx.detectChanges(true);
    setTimeout(() => {
      this.searchInputField.nativeElement.focus();
      this.searchInputField.nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    this.textSearchMode = false;
    this.textSearch = null;
    this.sources.forEach((source) => {
      source.pageLink.textSearch = this.textSearch;
      if (this.displayPagination) {
        source.pageLink.page = 0;
      }
    });
    this.loadCurrentSourceRow();
    this.ctx.hideTitlePanel = false;
    this.ctx.detectChanges(true);
  }

  private updateData(sort: MatSort, paginator: MatPaginator) {
    const source = this.sources[this.sourceIndex];
    if (this.displayPagination) {
      source.pageLink.page = paginator.pageIndex;
      source.pageLink.pageSize = paginator.pageSize;
    } else {
      source.pageLink.page = 0;
    }
    source.pageLink.sortOrder.property = sort.active;
    source.pageLink.sortOrder.direction = Direction[sort.direction.toUpperCase()];
    source.timeseriesDatasource.loadRows();
    this.clearCache();
    this.ctx.detectChanges();
  }

  public trackByColumnIndex(index, header: TimeseriesHeader) {
    return header.index;
  }

  public trackByRowTimestamp(index: number) {
    return index;
  }

  public trackByActionCellDescriptionId(index: number, action: WidgetActionDescriptor) {
    return action.id;
  }

  public trackBySourcesIndex(index: number, source: TimeseriesTableSource) {
    return source.datasource.entityId;
  }

  public rowStyle(source: TimeseriesTableSource, row: TimeseriesRow, index: number): any {
    let res = this.rowStyleCache[index];
    if (!res) {
      res = {};
      if (this.rowStylesInfo.useRowStyleFunction && this.rowStylesInfo.rowStyleFunction) {
        try {
          const rowData = source.rowDataTemplate;
          rowData.Timestamp = row[0];
          source.header.forEach((headerInfo) => {
            rowData[headerInfo.dataKey.name] = row[headerInfo.index];
          });
          res = this.rowStylesInfo.rowStyleFunction(rowData, this.ctx);
          if (!isObject(res)) {
            throw new TypeError(`${res === null ? 'null' : typeof res} instead of style object`);
          }
          if (Array.isArray(res)) {
            throw new TypeError(`Array instead of style object`);
          }
        } catch (e) {
          res = {};
          console.warn(`Row style function in widget ` +
            `'${this.ctx.widgetConfig.title}' returns '${e}'. Please check your row style function.`);
        }
      }
      this.rowStyleCache[index] = res;
    }
    return res;
  }

  public cellStyle(source: TimeseriesTableSource, index: number, row: TimeseriesRow, value: any, rowIndex: number): any {
    const cacheIndex = rowIndex * (source.header.length + 1) + index;
    let res = this.cellStyleCache[cacheIndex];
    if (!res) {
      res = {};
      if (index > 0) {
        const styleInfo = source.stylesInfo[index - 1];
        if (styleInfo.useCellStyleFunction && styleInfo.cellStyleFunction) {
          try {
            const rowData = source.rowDataTemplate;
            rowData.Timestamp = row[0];
            source.header.forEach((headerInfo) => {
              rowData[headerInfo.dataKey.name] = row[headerInfo.index];
            });
            res = styleInfo.cellStyleFunction(value, rowData, this.ctx);
            if (!isObject(res)) {
              throw new TypeError(`${res === null ? 'null' : typeof res} instead of style object`);
            }
            if (Array.isArray(res)) {
              throw new TypeError(`Array instead of style object`);
            }
          } catch (e) {
            res = {};
            console.warn(`Cell style function for data key '${source.header[index - 1].dataKey.label}' in widget ` +
              `'${this.ctx.widgetConfig.title}' returns '${e}'. Please check your cell style function.`);
          }
        }
      }
      this.cellStyleCache[cacheIndex] = res;
    }
    return res;
  }

  public cellContent(source: TimeseriesTableSource, index: number, row: TimeseriesRow, value: any, rowIndex: number): SafeHtml {
    const cacheIndex = rowIndex * (source.header.length + 1) + index ;
    let res = this.cellContentCache[cacheIndex];
    if (isUndefined(res)) {
      res = '';
      if (index === 0) {
        res = row.formattedTs;
      } else {
        let content;
        const contentInfo = source.contentsInfo[index - 1];
        if (contentInfo.useCellContentFunction && contentInfo.cellContentFunction) {
          try {
            const rowData = source.rowDataTemplate;
            rowData.Timestamp = row[0];
            source.header.forEach((headerInfo) => {
              rowData[headerInfo.dataKey.name] = row[headerInfo.index];
            });
            content = contentInfo.cellContentFunction(value, rowData, this.ctx);
          } catch (e) {
            content = '' + value;
          }
        } else {
          const decimals = (contentInfo.decimals || contentInfo.decimals === 0) ? contentInfo.decimals : this.ctx.widgetConfig.decimals;
          const units = contentInfo.units || this.ctx.widgetConfig.units;
          content = this.ctx.utils.formatValue(value, decimals, units, true);
        }

        if (isDefined(content)) {
          content = this.utils.customTranslation(content, content);
          switch (typeof content) {
            case 'string':
              res = this.domSanitizer.bypassSecurityTrustHtml(content);
              break;
            default:
              res = content;
          }
        }
      }
      this.cellContentCache[cacheIndex] = res;
    }
    return res;
  }

  public onRowClick($event: Event, row: TimeseriesRow) {
    const descriptors = this.ctx.actionsApi.getActionDescriptors('rowClick');
    if (descriptors.length) {
      if ($event) {
        $event.stopPropagation();
      }
      let entityId;
      let entityName;
      let entityLabel;
      if (this.ctx.activeEntityInfo) {
        entityId = this.ctx.activeEntityInfo.entityId;
        entityName = this.ctx.activeEntityInfo.entityName;
        entityLabel = this.ctx.activeEntityInfo.entityLabel;
      }
      this.ctx.actionsApi.handleWidgetAction($event, descriptors[0], entityId, entityName, row, entityLabel);
    }
  }

  public onActionButtonClick($event: Event, row: TimeseriesRow, actionDescriptor: WidgetActionDescriptor) {
    if ($event) {
      $event.stopPropagation();
    }
    let entityId;
    let entityName;
    let entityLabel;
    if (this.ctx.activeEntityInfo) {
      entityId = this.ctx.activeEntityInfo.entityId;
      entityName = this.ctx.activeEntityInfo.entityName;
      entityLabel = this.ctx.activeEntityInfo.entityLabel;
    }
    this.ctx.actionsApi.handleWidgetAction($event, actionDescriptor, entityId, entityName, row, entityLabel);
  }

  public isActiveTab(index: number): boolean {
    return index === this.sourceIndex;
  }

  private updateCurrentSourceData() {
    this.sources[this.sourceIndex].timeseriesDatasource.dataUpdated(this.data);
  }

  private loadCurrentSourceRow() {
    this.sources[this.sourceIndex].timeseriesDatasource.loadRows();
    this.clearCache();
  }

  private clearCache() {
    this.cellContentCache.length = 0;
    this.cellStyleCache.length = 0;
    this.rowStyleCache.length = 0;
  }
}

class TimeseriesDatasource implements DataSource<TimeseriesRow> {

  private rowsSubject = new BehaviorSubject<TimeseriesRow[]>([]);
  private pageDataSubject = new BehaviorSubject<PageData<TimeseriesRow>>(emptyPageData<TimeseriesRow>());

  private allRowsSubject = new BehaviorSubject<TimeseriesRow[]>([]);
  private allRows$: Observable<Array<TimeseriesRow>> = this.allRowsSubject.asObservable();

  constructor(
    private source: TimeseriesTableSource,
    private hideEmptyLines: boolean,
    private dateFormatFilter: string,
    private datePipe: DatePipe,
    private ngZone: NgZone
  ) {
    this.source.timeseriesDatasource = this;
  }

  connect(collectionViewer: CollectionViewer): Observable<TimeseriesRow[] | ReadonlyArray<TimeseriesRow>> {
    if (this.rowsSubject.isStopped) {
      this.rowsSubject.isStopped = false;
      this.pageDataSubject.isStopped = false;
    }
    return this.rowsSubject.asObservable();
  }

  disconnect(collectionViewer: CollectionViewer): void {
    this.rowsSubject.complete();
    this.pageDataSubject.complete();
  }

  loadRows() {
    this.fetchRows(this.source.pageLink).pipe(
      catchError(() => of(emptyPageData<TimeseriesRow>())),
    ).subscribe(
      (pageData) => {
        this.ngZone.run(() => {
          this.rowsSubject.next(pageData.data);
          this.pageDataSubject.next(pageData);
        });
      }
    );
  }

  dataUpdated(data: DatasourceData[]) {
    this.source.rawData = data.slice(this.source.keyStartIndex, this.source.keyEndIndex);
    this.updateSourceData();
  }

  private updateSourceData() {
    this.source.data = this.convertData(this.source.rawData);
    this.allRowsSubject.next(this.source.data);
  }

  private convertData(data: DatasourceData[]): TimeseriesRow[] {
    const rowsMap: {[timestamp: number]: TimeseriesRow} = {};
    for (let d = 0; d < data.length; d++) {
      const columnData = data[d].data;
      columnData.forEach((cellData) => {
        const timestamp = cellData[0];
        let row = rowsMap[timestamp];
        if (!row) {
          row = {
            formattedTs: this.datePipe.transform(timestamp, this.dateFormatFilter)
          };
          row[0] = timestamp;
          for (let c = 0; c < data.length; c++) {
            row[c + 1] = undefined;
          }
          rowsMap[timestamp] = row;
        }
        row[d + 1] = cellData[1];
      });
    }

    let rows: TimeseriesRow[]  = [];
    if (this.hideEmptyLines) {
      for (const t of Object.keys(rowsMap)) {
        let hideLine = true;
        for (let c = 0; (c < data.length) && hideLine; c++) {
          if (rowsMap[t][c + 1]) {
            hideLine = false;
          }
        }
        if (!hideLine) {
          rows.push(rowsMap[t]);
        }
      }
    } else {
      rows = Object.keys(rowsMap).map(itm => rowsMap[itm]);
    }
    return rows;
  }

  isEmpty(): Observable<boolean> {
    return this.rowsSubject.pipe(
      map((rows) => !rows.length)
    );
  }

  total(): Observable<number> {
    return this.pageDataSubject.pipe(
      map((pageData) => pageData.totalElements)
    );
  }

  private fetchRows(pageLink: PageLink): Observable<PageData<TimeseriesRow>> {
    return this.allRows$.pipe(
      map((data) => pageLink.filterData(data))
    );
  }
}
