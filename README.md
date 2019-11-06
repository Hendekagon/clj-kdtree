# clj-kdtree

## Overview

A Kd-tree is a special type of binary tree that partitions points in a
k-dimensional space. It can be used for efficient nearest-neighbor
searches.

For more detail, refer to [Wikipedia on Kd-trees](http://en.wikipedia.org/wiki/Kd-tree).

## Usage

Add this to your deps.edn `:deps` list:

    abscondment/clj-kdtree {:git/url "https://github.com/Hendekagon/clj-kdtree.git" :sha "<latest sha for deps branch>"

#### Build
```clojure
(require 'kdtree.api :as kd)

(def points [[8 8] [3 1] [6 6] [7 7] [1 3] [4 4] [5 5]])
;; Build a kdtree from a set of points
(def tree (kd/build-tree points))
(println "Tree:" tree)
```

#### Nearest neighbor

Use tree to find neighbors for one point given a set of points:
```clojure
(println "Four points closest to [2 2]:\n"
         (kd/nearest-neighbor tree [2 2] 4))
```
output:
```text
Four points closest to [2 2]:
 (#kdtree.Result{:point [1.0 3.0], dist 2.0}
  #kdtree.Result{:point [3.0 1.0], dist 2.0}
  #kdtree.Result{:point [4.0 4.0], dist 8.0}
  #kdtree.Result{:point [5.0 5.0], dist 18.0})
```

#### Interval search

Find all points inside interval:
```clojure
(println "Points 1≤x≤4, 3≤y≤6\n"
         (kd/interval-search tree [[1 4] [3 6]]))
```
output:
```text
Points 1≤x≤4, 3≤y≤6
 [[1.0 3.0] [4.0 4.0]]
```


#### Delete

Delete point and return new version of a tree. Doesn't change old tree.
```clojure
(println "Four points with deletion:\n"
         (-> tree
             (kd/delete [1 3])
             (kd/delete [3 1])
             (kd/nearest-neighbor [2 2] 4)))
```
output:
```text
Four points with deletion:
 (#kdtree.Result{:point [4.0 4.0], dist 8.0}
  #kdtree.Result{:point [5.0 5.0], dist 18.0}
  #kdtree.Result{:point [6.0 6.0], dist 32.0}
  #kdtree.Result{:point [7.0 7.0], dist 50.0})
```

#### Insert

Insert point and return new version of a tree. Doesn't change old tree.
```clojure
(println "\n\nFour points with insertion:\n"
         (-> tree
             (kd/insert [1.5 1.5])
             (kd/nearest-neighbor [2 2] 4)))
```
output:
```text
Four points with insertion:
 (#kdtree.Result{:point [1.5 1.5], dist 0.5}
  #kdtree.Result{:point [3.0 1.0], dist 2.0}
  #kdtree.Result{:point [1.0 3.0], dist 2.0}
  #kdtree.Result{:point [4.0 4.0], dist 8.0})
```

#### Metadata

To store arbitrary information in points you can use metadata attached to the points. All tree modification operations like `build-tree`, `insert` and `delete` retain metadata. All query operation like `nearest-neighbor` and `interval-search` return points with corresponded meta:

```clojure
(def point (with-meta [0 0] {:value "Hello"}))
(def tree (kd/build-tree [point]))
(println "Meta:"
         (meta (kd/nearest-neighbor tree [1 1])))
```
output:
```text
Meta: {:value Hello}
```

## License

Copyright (C) 2009-2015 Brendan Ribera [and contributors](https://github.com/abscondment/clj-kd/graphs/contributors). All rights reserved.

Distributed under the MIT License; see the file LICENSE at the root of
this distribution.
