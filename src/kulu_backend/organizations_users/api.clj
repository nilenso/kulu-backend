(ns kulu-backend.organizations-users.api
  (:require [kulu-backend.organizations-users.model :as org-users]))

(defn active-users
  [org-name]
  (org-users/active-users org-name))

(defn delete-user
  [id]
  (org-users/delete id))
