;; Copyright (C) 2009-2011 Brendan Ribera. All rights reserved.
;; Distributed under the MIT License; see the file LICENSE
;; at the root of this distribution.
(ns kdtree.core)

(defrecord Node [dist-fn left right value])
(defrecord Result [point ^double dist])

(defn p-norm
  "Return a p-norm function for the given p"
  [p]
  (fn [^doubles a ^doubles b]
    (loop [res (double 0.0)
           ind (long 0)]
     (if (== ind (alength a))
       (Math/pow res (/ 1.0 p))
       (let [v (Math/abs (- (aget a ind) (aget b ind)))]
         (recur (+ res (Math/pow v p)) (inc ind)))))))

(defn dist-squared
  "Compute the K-dimensional distance between two points"
  [^doubles a ^doubles b]
  (loop [res (double 0.0)
         ind (long 0)]
    (if (== ind (alength a))
      res
      (let [v (- (aget a ind) (aget b ind))]
        (recur (+ res (* v v)) (inc ind))))))

(defn dot
  ""
  [^doubles a ^doubles b]
  (loop [res (double 0.0)
         ind (long 0)]
    (if (== ind (alength a))
      res
      (recur (+ res (* (aget a ind) (aget b ind))) (inc ind)))))

(defn vl [x]
  (Math/sqrt (dot x x)))

(defn inner-product
  "Inner product"
  [a b]
  (let [la (vl a) lb (vl b)]
    (if (and (> la 0) (> lb 0))
      (/ (dot a b) la lb)
      0.0)))

(defn build-tree [dist-fn points depth]
  (if (empty? points) nil
      (let [point-count (count points)
            k (count (nth points 0))
            dimension (mod depth k)
            points (vec (sort-by #(nth % dimension) points))
            median (quot point-count 2)
            split-point (loop [n median]
                          (cond
                           (= 0 n) n
                           (= (nth (points n) dimension)
                              (nth (points (dec n)) dimension))
                           (recur (dec n))
                           :else n))
            left-tree (build-tree
                       dist-fn
                       (subvec points 0 split-point)
                       (inc depth))
            right-tree (build-tree
                        dist-fn
                        (subvec points (inc split-point))
                        (inc depth))]
        (Node. dist-fn
               left-tree
               right-tree
               (double-array (nth points split-point))
               (meta (nth points split-point))
               nil))))

(defn insert
  ([{dist-fn :dist-fn :or {dist-fn dist-squared} insert? :insert? :as tree} ^doubles point depth point-meta]
    (let [k (alength point)
          dimension (mod depth k)]
       (if (or (nil? tree) insert?)
         (Node. dist-fn nil nil point point-meta nil)
         (let [^doubles value (:value tree)
               go-to-left? (< (aget point dimension)
                             (aget value dimension))
               left (if go-to-left?
                      (insert  (:left tree)  point (inc depth) point-meta)
                      (:left tree))
               right (if-not go-to-left?
                       (insert (:right tree) point (inc depth) point-meta)
                       (:right tree))]
            (Node. dist-fn left right value (meta tree) nil))))))

(defn find-min [tree dimension depth]
  (when tree
    (let [k (count (:value tree))
          min-node (fn [node1 node2]
                     (let [^doubles value1 (:value node1)
                           ^doubles value2 (:value node2)]
                       (if (or (nil? value2)
                               (< (aget value1 dimension)
                                  (aget value2 dimension)))
                         node1 node2)))]
      (if (= dimension (mod depth k))
        ;; if we're at the dimension of interest, follow the left branch or
        ;; take the value - left is always smaller in the current dimension
        (if (:left tree)
          (recur (:left tree) dimension (inc depth))
          tree)
        ;; otherwise, compare min of self & children
        (-> tree
            (min-node (find-min (:left tree) dimension (inc depth)))
            (min-node (find-min (:right tree) dimension (inc depth))))))))

(defn points=
  "Compares 2 points represented by arrays of doubles and return true if they are equal"
  [^doubles a ^doubles b]
  (loop [i 0]
    (cond (== i (alength a)) true
          (== (aget a i) (aget b i)) (recur (inc i))
          :else false)))

(defn delete
  [{dist-fn :dist-fn :as tree} ^doubles point depth]
  (when tree
    (let [^doubles value (:value tree)
          k (alength value)
          dimension (mod depth k)]
      (cond
       ;; point is not here
       (not (points= point value))
       (let [go-to-left? (< (aget point dimension) (aget value dimension))
             left (if go-to-left?
                    (delete (:left tree) point (inc depth))
                    (:left tree))
             right (if-not go-to-left?
                     (delete (:right tree) point (inc depth))
                     (:right tree))]
         (Node. dist-fn left right (:value tree) (meta tree) nil))
       ;; point is here... three cases:
       ;; right is not null.
       (:right tree)
       (let [min (find-min (:right tree) dimension (inc depth))]
         (Node.
          dist-fn
          (:left tree)
          (delete (:right tree) (:value min) (inc depth))
          (:value min)
          (meta min)
          nil))
       ;; left if not null
       (:left tree)
       (let [min (find-min (:left tree) dimension (inc depth))]
         (Node.
          dist-fn
          nil
          (delete (:left tree) (:value min) (inc depth))
          (:value min)
          (meta min)
          nil))
       ;; both left and right are null
       :default nil))))

(defn insert-sorted!
  "Inserts value to sorted transient vector. Vector will not grow
bigger than n elements."
  [vec value ^long n]
  (if (and (== (count vec) n)
           (> (:dist value) (:dist (nth vec (dec n)))))
    vec
    (loop [ind (long 0)
           value value
           vec vec]
     (cond (= ind n) vec
           (= ind (count vec)) (conj! vec value)
           :default (let [existing (nth vec ind)]
                      (if (< (:dist value)
                             (:dist existing))
                        (recur (inc ind) existing (assoc! vec ind value))
                        (recur (inc ind) value vec)))))))

(defn nearest-neighbor [{dist-fn :dist-fn :as tree} ^doubles point n dimension best]
  (if ;; Empty tree? The best list is unchanged.
         (nil? tree) best
         ;; Otherwise, recurse!
         (let [dimension (long dimension)
               next-dimension (unchecked-remainder-int (unchecked-inc dimension) (alength point))
               ^doubles v (:value tree)
               dim-dist (double (- (aget point dimension)
                                   (aget v dimension)))
               closest-semiplane ((if (> dim-dist 0.0) :right :left) tree)
               farthest-semiplane ((if (> dim-dist 0.0) :left :right) tree)
               ;; Compute best list for the near-side of the search order
               best-with-cur (insert-sorted! best (Result. v
                                                           (dist-fn v point)
                                                           (meta tree)
                                                           nil)
                                             n)
               best-near (nearest-neighbor closest-semiplane point n next-dimension best-with-cur)
               worst-nearest (->> (dec (count best-near))
                                  (nth best-near)
                                  :dist)]
           ;; If the square distance of our search node to point in the
           ;; current dimension is still better than the *worst* of the near-
           ;; side best list, there may be a better solution on the far
           ;; side. Compute & combine with near-side solutions.
           (if (< (Math/abs dim-dist) worst-nearest)
             (recur farthest-semiplane point n next-dimension best-near)
             best-near))))

(defn inside-interval? [interval ^doubles point]
  (let [n (alength point)]
    (loop [ind 0]
      (if (== ind n) true
        (let [^doubles axis-intv (nth interval ind)
              left (double (aget axis-intv 0))
              right (double (aget axis-intv 1))
              value (double (aget point ind))]
          (if (<= left value right)
            (recur (inc ind))
            false))))))

(defn interval-search [tree interval ^long depth accum]
  (if (nil? tree)
    accum
    (let [^doubles point (:value tree)
          ;; If current points inside interval - add it to accum.
          accum (if (inside-interval? interval point)
                  (conj! accum (with-meta (vec point) (meta tree)))
                  accum)
          k (unchecked-remainder-int depth (alength point))
          dim-value (double (aget point k))
          ^doubles dim-boundaries (nth interval k)
          left-boundary (double (aget dim-boundaries 0))
          right-boundary (double (aget dim-boundaries 1))

          ;;; Go to right subtree only if current dimension value is not to the right of the interval.
          accum (if (<= dim-value right-boundary)
                  (interval-search (:right tree) interval (unchecked-inc depth) accum)
                  accum)

          ;; Go to left subtree only if current dimension is not to the left of the interval.
          accum (if (> dim-value left-boundary)
                  (interval-search (:left tree) interval (unchecked-inc depth) accum)
                  accum)]
      accum)))

