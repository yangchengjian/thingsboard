import {SelectionModel} from '@angular/cdk/collections';
import {FlatTreeControl} from '@angular/cdk/tree';
import {Component, OnDestroy, OnInit, Input} from '@angular/core';
import {MatTreeFlatDataSource, MatTreeFlattener} from '@angular/material/tree';
import {FlatNode, Node, ChecklistDatabase} from './tree.model';


@Component({
  selector: 'tb-tree',
  templateUrl: 'tree.component.html',
  styleUrls: ['tree.component.scss'],
  providers: [ChecklistDatabase]
})
export class TreeComponent implements OnInit, OnDestroy{

  @Input()
  organizationTree: any;

  @Input()
  isEdit: boolean;
  
  flatNodeMap = new Map<FlatNode, Node>();

  nestedNodeMap = new Map<Node, FlatNode>();

  selectedParent: FlatNode | null = null;

  newItemName = '';

  treeControl: FlatTreeControl<FlatNode>;

  treeFlattener: MatTreeFlattener<Node, FlatNode>;

  dataSource: MatTreeFlatDataSource<Node, FlatNode>;

  checklistSelection = new SelectionModel<FlatNode>(true /* multiple */);

  constructor(private database: ChecklistDatabase) {
    
  }

  ngOnInit() {
    console.log('isEdit: ' + this.isEdit);
    console.log('organizationTree: ' + JSON.stringify(this.organizationTree));

    this.database.initialize(this.organizationTree);

    this.treeFlattener = new MatTreeFlattener(this.transformer, this.getLevel, this.isExpandable, this.getChildren);
    this.treeControl = new FlatTreeControl<FlatNode>(this.getLevel, this.isExpandable);
    this.dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);
    this.database.dataChange.subscribe(data => {
      this.dataSource.data = data;
    });
  }

  ngOnDestroy() {

  }

  saveTree() {
    console.log('saveTree');
    console.log('checklistSelection: ' + JSON.stringify(this.checklistSelection.selected));
  }

  deleteTree() {
    console.log('deleteTree');
    console.log('checklistSelection: ' + JSON.stringify(this.checklistSelection.selected));
    this.checklistSelection.selected.forEach(
      function(value) {
        console.log('value: ' + JSON.stringify(value));
        this.deleteItem(value as FlatNode);
      }.bind(this)
    );
    this.checklistSelection.clear();
  }

  getLevel = (node: FlatNode) => node.level;

  isExpandable = (node: FlatNode) => node.expandable;

  getChildren = (node: Node): Node[] => node.children;

  hasChild = (_: number, _nodeData: FlatNode) => _nodeData.expandable;

  hasNoContent = (_: number, _nodeData: FlatNode) => _nodeData.item === '';

  /**
   * Transformer to convert nested node to flat node. Record the nodes in maps for later use.
   */
  transformer = (node: Node, level: number) => {
    const existingNode = this.nestedNodeMap.get(node);
    const flatNode = existingNode && existingNode.item === node.item
        ? existingNode
        : new FlatNode();
    flatNode.item = node.item;
    flatNode.level = level;
    flatNode.expandable = !!node.children;
    this.flatNodeMap.set(flatNode, node);
    this.nestedNodeMap.set(node, flatNode);
    return flatNode;
  }

  /** Whether all the descendants of the node are selected. */
  descendantsAllSelected(node: FlatNode): boolean {
    const descendants = this.treeControl.getDescendants(node);
    const descAllSelected = descendants.every(child =>
      this.checklistSelection.isSelected(child)
    );
    return descAllSelected;
  }

  /** Whether part of the descendants are selected */
  descendantsPartiallySelected(node: FlatNode): boolean {
    const descendants = this.treeControl.getDescendants(node);
    const result = descendants.some(child => this.checklistSelection.isSelected(child));
    return result && !this.descendantsAllSelected(node);
  }

  /** Toggle the to-do item selection. Select/deselect all the descendants node */
  todoItemSelectionToggle(node: FlatNode): void {
    this.checklistSelection.toggle(node);
    const descendants = this.treeControl.getDescendants(node);
    this.checklistSelection.isSelected(node)
      ? this.checklistSelection.select(...descendants)
      : this.checklistSelection.deselect(...descendants);

    // Force update for the parent
    descendants.every(child =>
      this.checklistSelection.isSelected(child)
    );
    this.checkAllParentsSelection(node);
  }

  /** Toggle a leaf to-do item selection. Check all the parents to see if they changed */
  todoLeafItemSelectionToggle(node: FlatNode): void {
    this.checklistSelection.toggle(node);
    this.checkAllParentsSelection(node);
  }

  /* Checks all the parents when a leaf node is selected/unselected */
  checkAllParentsSelection(node: FlatNode): void {
    let parent: FlatNode | null = this.getParentNode(node);
    while (parent) {
      this.checkRootNodeSelection(parent);
      parent = this.getParentNode(parent);
    }
  }

  /** Check root node checked state and change it accordingly */
  checkRootNodeSelection(node: FlatNode): void {
    const nodeSelected = this.checklistSelection.isSelected(node);
    const descendants = this.treeControl.getDescendants(node);
    const descAllSelected = descendants.every(child =>
      this.checklistSelection.isSelected(child)
    );
    if (nodeSelected && !descAllSelected) {
      this.checklistSelection.deselect(node);
    } else if (!nodeSelected && descAllSelected) {
      this.checklistSelection.select(node);
    }
  }

  /* Get the parent node of a node */
  getParentNode(node: FlatNode): FlatNode | null {
    const currentLevel = this.getLevel(node);

    if (currentLevel < 1) {
      return null;
    }

    const startIndex = this.treeControl.dataNodes.indexOf(node) - 1;

    for (let i = startIndex; i >= 0; i--) {
      const currentNode = this.treeControl.dataNodes[i];

      if (this.getLevel(currentNode) < currentLevel) {
        return currentNode;
      }
    }
    return null;
  }

  addNewItem(node: FlatNode) {
    const parentNode = this.flatNodeMap.get(node);
    this.database.insertItem(parentNode!, '');
    // this.treeControl.expand(node);
  }

  checkItem(node: FlatNode, itemValue: string) {
    const nestedNode = this.flatNodeMap.get(node);
    this.database.updateItem(nestedNode!, itemValue);
  }

  deleteItem(node: FlatNode) {
    const nestedNode = this.flatNodeMap.get(node);
    this.database.deleteItem(nestedNode!);
  }
}
