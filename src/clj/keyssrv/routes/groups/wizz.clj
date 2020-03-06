(ns keyssrv.routes.groups.wizz
  (:require [keyssrv.routes.index.wizzards :as wizz]
            [keyssrv.routes.index.wizz-util :as wizz-util]))

(def hint-init-end-i (wizz/get-step-i ::wizz/init-hints-end))

(defn apply-wizz-group-items-view-updates
  "Wizzard logic for group view"
  [request user wizz-i step-i wizz-k step-k wizz-def]
  (if (= wizz-k ::wizz/init-hints)

    (let [
          next-step-i (wizz/get-step-i (wizz/step-forward wizz-def step-k))
          update-f (partial wizz-util/update-user-wizz request user)
          ]

      (cond
        ;;if before show-exlore, advance by one, don't advance past explore
        (< step-i hint-init-end-i) [(update-f wizz-i next-step-i) true]

        ;; if at end, end wizzard
        (= step-i hint-init-end-i) [(update-f 0 0) false]
        :else [request false]))

    [request false]))

