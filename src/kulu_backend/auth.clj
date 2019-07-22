(ns kulu-backend.auth
  (:require [kulu-backend.session-tokens.model :as token]
            [kulu-backend.users.api :refer [admin?]]
            [schema.core :as s]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))


(defn authenticated?
  "Checks whether the user/pass are correct.
  Also ensures that the user belongs to the organization they are logging into."
  [user params]
  (and (creds/bcrypt-credential-fn {(:email user) user}
                                   {:username (:user_email params) :password (:password params)})
       (not (empty? (:org-user params)))))

(defn permission?
  "Checks whether the token is present in the system.
  Also ensures that the extracted organization from the token is the same as the one requested from.
  Later on, the tokens can contain role level information."
  [{:keys [params headers] :as req}]
  (let [token (token/get-it (headers "x-auth-token"))]
    (and (contains? params :organization_name)
         (s/validate s/Str (:organization_name params))
         (not (empty? token))
         (= (:organization-name token) (clojure.string/lower-case (:organization_name params))))))

(defn admin-permissions?
  [{:keys [headers]}]
  (let [token (token/get-it (headers "x-auth-token"))]
    (admin? (:user-email token))))