import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export class Node {
  children: Node[];
  item: string;
}

export class FlatNode {
  item: string;
  level: number;
  expandable: boolean;
}

@Injectable()
export class ChecklistDatabase {
  dataChange = new BehaviorSubject<Node[]>([]);

  get data(): Node[] { return this.dataChange.value; }

  constructor() {
  }

  initialize(treeData: Object) {
    var newTreeData = {};
    newTreeData["根"] = treeData;
    const data = this.buildTree(newTreeData, 0);
    console.log('initialize data 1: ' + JSON.stringify(treeData));
    console.log('initialize data 2: ' + JSON.stringify(data));

    // Notify the change.
    this.dataChange.next(data);
  }

  buildTree(obj: { [key: string]: any }, level: number): Node[] {
    return Object.keys(obj).reduce<Node[]>((accumulator, key) => {
      const value = obj[key];
      const node = new Node();
      node.item = key;

      if (value != null) {
        if (typeof value === 'object') {
          node.children = this.buildTree(value, level + 1);
        } else {
          node.item = value;
        }
      }

      return accumulator.concat(node);
    }, []);
  }

  insertItem(parent: Node, name: string) {
    if (!parent.children) {
      parent.children = [];
    }
    parent.children.push({ item: name } as Node);
    this.dataChange.next(this.data);
  }

  updateItem(node: Node, name: string) {
    node.item = name;
    this.dataChange.next(this.data);
    console.log('data after update: ' + JSON.stringify(this.data));
  }

  deleteItem(selectedNode: Node) {
    this.data.forEach(function(value){
      this.deleteNode(value as Node, selectedNode);
    }.bind(this));
  }
  deleteNode(node: Node, selectedNode: Node) {
    if (node.children != null) {
      var i;
      for (i = 0; i < node.children.length; i++) {
        if (node.children[i] == selectedNode) {
          node.children.splice(i, 1);
          return;
        }
        else this.deleteNode(node.children[i], selectedNode);
      }
      this.dataChange.next(this.data);
      console.log('data after delete: ' + JSON.stringify(this.data));
    }
    else return;
  }

}