(ns kulu-backend.organizations-users.api
  (:require [kulu-backend.organizations-users.model :as org-users]))

(defn active-users
  [org-name]
  (map #(-> %
            (assoc :status (if (:is-active %)
                             "active"
                             "de-activated"))
            (dissoc :is-active))
       (org-users/active-users org-name)))

(defn delete-user
  [id]
  (org-users/delete id))
