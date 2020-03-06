(ns
  ^{:doc "
  Define wizzards state machines and how they map from int values to keywords

  To create a wizzard state machine:
   1. add a unique number and keyword to the wizzard-mapping def
   2. add the steps as unique keywords and unique numbers to the step-mappings
   3. in wizzards-defs add the wizzard key and flow mappings
      e.g {:my-wizz {:step-1 :step-2
                     :step-2 :step-3}}

   4. To use call the step-forward function, the step function takes no arguments (its contextual)
       and either returns nil, which means next step, or a map with both or one of :message, :error.

   5. The get-wizzard and get-wizzard-ints are used to translate state between keywords and int storage
  "}
  keyssrv.routes.index.wizzards)


(defn map-invert [m]
  (reduce-kv (fn [m k v] (assoc m v k)) {} m))


;The step function takes no arguments and returns {:message str, :error str} if issues
;  or nil if on to the next step
(defn step-forward
  ([wizzard-def step-k]
   (step-forward wizzard-def step-k (fn [] nil)))
  ([wizzard-def step-k step-f]
   (when-let [next-step (get wizzard-def step-k)]
     (let [resp (step-f)]
       (if (empty? resp)                                    ;;false or nil
         next-step                                          ;;next step
         step-k)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; mappings

(def wizzard-mapping
  {0 ::noop
   1 ::setup
   2 ::init-hints})

(def wizzard-mapping-rev (map-invert wizzard-mapping))

;; Please note: Steps should be numbered in order, i.e step1 < step2 < step3
(def step-mappings
  {0 ::noop-noop
   1 ::setup-plan
   2 ::setup-confirm
   3 ::setup-end

   4 ::init-hints-show-group
   5 ::init-hints-show-group-click
   6 ::init-hints-show-explore
   7 ::init-hints-end
   })

(def step-mappings-rev (map-invert step-mappings))

(defn get-step-i [step-k]
  (get step-mappings-rev step-k))

(def wizzard-defs

  {::noop       {::noop ::noop}

   ::setup      {::setup-plan    ::setup-end
                 ::setup-confirm ::setup-end
                 ::setup-end     ::setup-end}

   ::init-hints {::init-hints-show-group       ::init-hints-show-group-click
                 ::init-hints-show-group-click ::init-hints-show-explore
                 ::init-hints-show-explore     ::init-hints-end
                 ::init-hints-end              ::init-hints-end}})

(defn get-wizzard
  "Returns [wizzard-k step-k wizzard-def]"
  [wizz-int step-int]
  (let [wizzard-k (get wizzard-mapping wizz-int)
        step-k (get step-mappings step-int)
        wizzard-def (get wizzard-defs wizzard-k)]

    (when (and wizzard-k
               step-k)
      (when (empty? wizzard-def)
        (throw (RuntimeException. (str "No wizzard definition for key " wizzard-k))))

      [wizzard-k step-k wizzard-def])))


(defn get-wizz-k [wizz-i]
  (get wizzard-mapping wizz-i))

(defn get-wizzard-ints
  "Return [wizz-i step-i]"
  [wizzard-k step-k]
  (let [wizz-i (get wizzard-mapping-rev wizzard-k)
        step-i (get step-mappings-rev step-k)]

    (when-not wizz-i
      (throw (RuntimeException. (str "No mapping found for " wizzard-k))))
    (when-not step-i
      (throw (RuntimeException. (str "No step mapping found for " step-k " mappings " (keys step-mappings-rev)))))

    [wizz-i step-i]))