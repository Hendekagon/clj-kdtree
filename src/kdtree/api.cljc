;; Copyright (C) 2009-2011 Brendan Ribera. All rights reserved.
;; Distributed under the MIT License; see the file LICENSE
;; at the root of this distribution.
(ns kdtree.api
  (:require [kdtree.core :as kd]))

(defn build-tree
  "Construct a Kd-tree from points. Assumes all
points are of the same dimension."
  ([points]
    (build-tree kd/dist-squared points))
  ([dist-fn points]
    (kd/build-tree-internal dist-fn points 0)))

(defn insert
  "Adds a point to an existing tree."
  [tree point]
  (kd/insert-internal tree (double-array point) 0 (meta point)))

(defn delete
  "Delete value at the given point. Runs in O(log n) time for a balanced tree."
  [tree point]
  (kd/delete-internal tree (double-array point) 0))

(defn nearest-neighbor
  "Compute n nearest neighbors for a point. If n is
omitted, the result is the nearest neighbor;
otherwise, the result is a list of length n."
  ([tree point] (first (nearest-neighbor tree point 1)))
  ([tree point n]
     (->> (transient [])
          (kd/nearest-neighbor-internal tree (double-array point) n 0)
          (persistent!)
          (map #(update-in % [:point] vec)))))

(defn interval-search
  "Find all points inside given interval.
Interval is a sequence of boundaries for each dimension.
Example: interval 0≤x≤10, 5≤y≤20 represented as [[0 10] [5 20]]"
  [tree interval]
  (->> (transient [])
       (kd/interval-search-internal tree (vec (map double-array interval)) 0)
       (persistent!)))

