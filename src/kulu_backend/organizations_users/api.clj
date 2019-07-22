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

(defn admin?
  [email org-name]
  (= "admin" (:role (org-users/lookup-by-email-and-org email org-name))))

(defn active?
  [email org-name]
  (:is-active (org-users/lookup-by-email-and-org email org-name)))


(defn delete-user
  [id]
  (org-users/delete id))
