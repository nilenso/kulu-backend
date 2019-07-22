(ns kulu-backend.users.api
  (:require [schema.core :as s :refer [defschema optional-key maybe pred enum Str Inst Num Uuid]]
            [cemerick.url :refer (url url-encode)]
            [ring.util.codec :as ru :refer [form-encode]]
            [kulu-backend.users.model :as user]
            [kulu-backend.invoices.mailer :as mailer]
            [kulu-backend.utils.render-templates :as t]
            [kulu-backend.config :as cfg]))

(defonce user-invite-subject "You have been invited to Kulu")
(defonce forgot-password-subject "Change your password to Kulu")

(defschema LoggedInUser
  {:token Uuid})

(defschema CreateUserBodyParams
  {(optional-key :user_name)      Str
   (optional-key :email)          Str})

(defschema LoginBodyParams
  {:team_name  Str
   (optional-key :user_name) (maybe Str)
   :user_email Str
   :password   Str})

(defschema SignupBodyParams
  {:organization_name Str
   :user_email        Str
   :user_name         Str
   :password          Str
   :confirm           Str})

(defschema MemberSignupBodyParams
  {:organization_name           Str
   :user_email                  Str
   :user_name                   Str
   :password                    Str
   :confirm                     Str
   :token                       Uuid})

(defschema UpdatePasswordParams
  {:organization_name           Str
   :user_email                  Str
   :password                    Str
   :confirm                     Str
   :token                       Uuid})

(defschema ForgotPasswordParams
  {:organization_name Str
   :user_email Str})

(defschema VerifyPasswordParams
  {:organization_name Str
   :user_email Str})

(defschema VerifyInviteParams
  {:organization_name Str
   :user_email Str})

(defn create
  [m]
  (let [user (user/create m)]
    (select-keys user [:id])))

(defn present?
  [user_email]
  (not (empty? (user/lookup-by-email user_email))))

(defn creds-present?
  [creds]
  (and (not-any? clojure.string/blank? (vals (dissoc creds :token)))
       (= (:password creds) (:confirm creds))))

(defn send-user-mail-with-link
  [organization_name user_email token subject path]
  (let [protocol "https://"
        root-link (url (str protocol organization_name "." (cfg/web-client-name)))
        link (-> root-link
                 (assoc :path (str "/" path "/" token "?" "user_email=" user_email))
                 str)]
    (let [body (t/render path {:action_link link :organization_name organization_name})]
      (mailer/send-mail subject user_email body))))

(defn send-invite
  [user_email organization_name token]
  (send-user-mail-with-link organization_name
                            user_email
                            token
                            user-invite-subject
                            "verify_invite"))

(defn send-forgot-password
  [user_email organization_name token]
  (send-user-mail-with-link organization_name
                            user_email
                            token
                            forgot-password-subject
                            "verify_password"))
