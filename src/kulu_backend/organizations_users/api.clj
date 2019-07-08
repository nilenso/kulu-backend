(ns kulu-backend.organizations-users.api
  (:require [kulu-backend.organizations-users.model :as org-users]))

(defn users
  [org-name]
  (map #(-> %
            (assoc :status (if (:is-active %)
                             "active"
                             "de-activated"))
            (dissoc :is-active))
       (org-users/users org-name)))
