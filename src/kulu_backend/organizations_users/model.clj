(ns kulu-backend.organizations-users.model
  (:require [kulu-backend.db :as db]
            [honeysql.core :as sql]
            [kulu-backend.tokens.model :as tokens]
            [kulu-backend.users.model :as user]
            [honeysql.helpers :as h-sql]))

(defonce table-name :organizations_users)

(defn store
  [m]
  (->> m
       (db/insert! (keyword table-name))
       first))

(defn create
  [m]
  (store m))

(defn update
  [id attrs]
  (db/update! (keyword table-name)
              attrs
              ["id = ?" id])
  id)

(defn users
  [org-name]
  (-> (h-sql/select :id :user_email :role :is_active)
      (h-sql/from table-name)
      (h-sql/where [:= :organization_name org-name])
      sql/format
      db/query))

(defn lookup-by-email-and-org
  [user-email org-name]
  (-> (h-sql/select :*)
      (h-sql/from table-name)
      (h-sql/where [:and [:= :user_email user-email] [:= :organization_name org-name]])
      sql/format
      db/query
      first))

(defn accept-password-reset [{:keys [id organization_name user_email] :as user} token]
  (when (tokens/verify-token :password_reset [user_email organization_name] token)
    (user/update-password user)
    (tokens/expire-token :password_reset [user_email organization_name])))

(defn lookup
  [id]
  (-> (h-sql/select :*)
      (h-sql/from table-name)
      (h-sql/where [:= :id id])
      sql/format
      db/query
      first))

;; Soft-delete the user
(defn delete
  [id]
  (let [user (lookup id)]
    (assert user
            (format "the record %s you wanted to delete does not exist" id))
    (update id
            (merge user
                   {:is-active false}))
    id))
