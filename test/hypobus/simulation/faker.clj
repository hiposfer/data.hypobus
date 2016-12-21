(ns hypobus.simulation.faker
  "functions used to create fake curves"
  (:import [java.util Random])
  (:require [hypobus.utils.tool :as tool]
            [hypobus.basics.geometry :as geo]
            [clojure.test.check.generators :as gen]))

(def ^:private rand-gen (new Random))

(defn- rand-gauss
  "Returns the next pseudorandom, Gaussian ('normally') distributed double
  value with mean 0.0 and standard deviation 1.0 from this random number
  generator's sequence."
  ([] (rand-gauss 1))
  ([deviation] (* deviation (.nextGaussian ^Random rand-gen))))


(defn- rand-intervals
  "create num-curves random intervals that span between 0 (included) and
  length (included). The minimum distance between the start and the end is
  set to span"
  [length amount span]
  (let [start      (gen/choose 0 length)
        end        (gen/choose 0 length)
        interval   (gen/such-that (fn [[a b]] (>= (- b a) span))
                                  (gen/tuple start end)
                                  100)]
    (gen/sample interval amount)))
;(rand-intervals 4 10)
;([0 4] [2 4] [0 2] [0 3] [0 4] [0 3] [2 4] [0 2] [1 3] [2 4])

(defn subcurves
 "create an amount of subcurves that span random continous intervals of the
original one. percentage is the minimum percentage of the curve that the curves
must span"
  ([sample amount]
   (subcurves sample amount 0.2))
  ([sample amount percentage]
   (let [length     (count sample)
         intervals  (rand-intervals length amount (Math/round ^double (* percentage length)))]
     (map #(apply subvec sample %) intervals))))

(defn- point-noise
  "add gaussian noise with sigma=deviation to a point and return the point with
  an estimate of the weight = 1/sigma^2"
  [deviation point]
  (let [new-point (tool/fmap #(+ % (rand-gauss deviation)) point)
        p-dist    (geo/distance point new-point)]
    (conj new-point {:weigth (/ 1 (* p-dist p-dist))})))

(defn add-noise
  "add gaussian noise with sigma=deviation to all points in a curve. The curve
  must be a {:lat y :lon x} hash-map"
  [deviation curve]
  (map #(point-noise deviation %) curve))

(defn add-burst
  "add gaussian noise with sigma=deviation to a part of a curve. A maximum of
  20% of the curve points will habe burst noise."
  [deviation curve]
  (let [vcurve (vec curve)
        length (count vcurve)
        size   (first (gen/sample (gen/choose 1 (Math/floor (* 0.2 length))) 1))
        start  (first (gen/sample (gen/choose 0 (- length size)) 1))
        burst  (add-noise deviation (subvec vcurve start (+ start size)))]
    (concat (subvec vcurve 0 start)
            burst
            (subvec vcurve (+ start size) length))))

(defn- make-box
  "computes the bounding box of a curve. Each bound is return separately with its
  own keyword"
  [curve]
  {:min-long (apply min (map first curve))
   :max-long (apply max (map first curve))
   :min-lat  (apply min (map second curve))
   :max-lat  (apply max (map second curve))})

(defn rand-2Dcurve
  "create a random 2 dimensional curve inside the given bounds. The resulting
  curve is on the form {:lat y :lon x :weigth z}"
  [size {:keys [min-long max-long min-lat max-lat]}]
  (let [longitude      (gen/double* {:infinite? false :NaN? false :min min-long :max max-long})
        latitude       (gen/double* {:infinite? false :NaN? false :min min-lat :max max-lat})
        point          (gen/tuple longitude latitude)]
    (map #(conj % {:weigth (rand)})
         (map #(zipmap [:lon :lat] %) (gen/sample point size)))))

(defn bandal-curves
  "create a certain amount of random 2 dimensional curves using the bounding
  box of the given curve. Each curve contains 10 points"
  [amount curve]
  (repeatedly amount #(rand-2Dcurve 10 (make-box curve))))