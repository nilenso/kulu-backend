(ns kulu-backend.handler
  (:require [com.duelinmarkers.ring-request-logging :refer [wrap-request-logging]]
            [compojure.api.legacy :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.handler :as handler]
            [kulu-backend.currencies :as currencies]
            [kulu-backend.invoices.api :as inv-api]
            [kulu-backend.transcriber.api :as tcrb-api]
            [kulu-backend.users.api :as users-api]
            [kulu-backend.organizations.api :as org-api]
            [kulu-backend.utils.api :refer :all]
            [kulu-backend.users.model :as users]
            [kulu-backend.categories.model :as categories]
            [kulu-backend.organizations-users.model :as orgs-users]
            [kulu-backend.organizations.model :as org]
            [kulu-backend.session-tokens.model :as token]
            [kulu-backend.tokens.model :as tokens]
            [kulu-backend.invoices.mailer :as mailer]
            [kulu-backend.invoices.states :as inv-state]
            [kulu-backend.reports.api :as dashboard-reports-api]
            [clj-time.coerce :as time-c]
            [kulu-backend.auth :as auth]
            [kulu-backend.config :as cfg]
            [ring.util.http-response :refer :all]
            [cemerick.friend :as friend]
            [ring.util.codec :refer :all]
            [ring.util.response :as resp]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cheshire.core :as cj :only [decode]]
            [kulu-backend.organizations-users.api :as orgs-users-api])
  (:import java.net.URI))

;; /api/v1/users/login
;; /api/v2/users/signup
;; /api/v2/organizations/nilenso/users/login
(defonce api-version "v1.0.0")

(defn wrap-authorization [handler]
  (fn [req]
    (if (auth/permission? req)
      (handler req)
      (unauthorized {}))))

;; TODO: this header parsing code is repeated in mailer.clj, move it to a single place.
(defn wrap-email-authorization [handler]
  (fn [{:keys [params headers] :as req}]
    (let [headers (into {} (cj/decode (:message-headers params) true))
          from-header (headers "X-Envelope-From")
          to-headers (str/split (headers "To") #", ")

          from (first
                (re-seq #"[a-zA-z0-9_.]+@[a-zA-Z0-9_.]+\.[A-z]{2,}"
                        from-header))
          org-names (mapv #(last
                            (first
                             (re-seq #"<?([^+@.]+)@[a-zA-Z0-9_.]+\.[A-z]{2,}"
                                     %)))
                          to-headers)
          organizations (remove nil?
                                (mapv #(org/lookup-by-email-and-org from %)
                                      org-names))]
      (log/info "Request Params :request-params" params)
      (if (not (empty? (seq organizations)))
        (handler (assoc req :organization (first organizations))) ;; since sending to multiple orgs is not allowed at the moment
        (unauthorized {})))))

(defn binary-ok-response [body type]
  {:status 200 :headers {"Content-Type" type} :body body})

(defroutes* invoice-routes
  (context "/invoices" []
           (middlewares [wrap-authorization]
                        (GET* "/" []
                              :return inv-api/PaginatedInvoiceList
                              :summary "Returns a paginated list of invoices"
                              :query [params inv-api/GetInvoicesQueryParams]
                              (ok (idiomatize-keys inv-api/index params)))

                        (GET* "/:id/next_and_prev_invoices" []
                              :return s/Any
                              :query [params inv-api/GetInvoicesQueryParams]
                              :path-params [id :- inv-api/InvoiceID]
                              :summary "Returns next and previous invoices id"
                              (ok (idiomatize-keys
                                   inv-api/next-and-prev-invoices [id
                                                                   (assoc params
                                                                          :organization_name
                                                                          (:organization_name params))])))

                        (GET* "/states" []
                              :return s/Any
                              :query-params [organization_name]
                              :summary "Returns a list of all invoices states possible"
                              (ok
                               (seq inv-state/known-states)))

                        (GET* "/search" []
                              :return inv-api/PaginatedInvoiceList
                              :summary "Search through invoices"
                              :query [params inv-api/SearchInvoicesQueryParams]
                              (ok (idiomatize-keys inv-api/search params)))

                        (GET* "/export" []
                              :summary "Export search data"
                              :query [params inv-api/SearchInvoicesQueryParams]
                              (future (inv-api/export (dasherize params)))
                              (log/info "Export data started")
                              (ok {}))

                        (GET* "/:id" []
                              :return inv-api/Invoice
                              :summary "Returns the details of a single invoices"
                              :query-params [organization_name]
                              :path-params [id :- inv-api/InvoiceID]
                              (respond-with-found-or-missing
                               (idiomatize-keys inv-api/lookup id) "invoice" "id" id))

                        (POST* "/" []
                               :return inv-api/Invoice
                               :summary "Creates an invoice based on the storage key. Call
this after uploading the user's invoice to the data-store. A storage-key is the uploaded object's
key in the data-store (currently S3). Later on, the invoice can be filled in with more details (such as name, date, amount etc.)."
                               :body-params [organization_name :- s/Str
                                             invoice :- inv-api/CreateInvoiceBodyParams]
                               (created (idiomatize-keys inv-api/create (assoc invoice
                                                                               :organization_name (str/lower-case organization_name)))))

                        (PUT* "/:id" []
                              :return inv-api/Invoice
                              :summary "Updates an invoice's details (such as name,
                              date, amount, currency etc.)."
                              :path-params [id :- inv-api/InvoiceID]
                              :body-params [organization_name :- s/Str
                                            invoice :- inv-api/ChangeInvoiceBodyParams]
                              (respond-with-found-or-missing
                               (idiomatize-keys inv-api/update [id invoice])
                               "invoice" "id" id))

                        (DELETE* "/:id" []
                                 :summary "(soft-) Deletes an invoice.
Returns with a 204 (No content) on success, 404 when no item with uuid found"
                                 :path-params [id :- inv-api/InvoiceID]
                                 (if (inv-api/soft-delete id)
                                   (ok {:id id})
                                   (not-found {:errors "Not Found"}))))))

(defroutes* currency-routes
  (context "/currencies" []
           (middlewares []
                        (GET* "/" []
                              :return [s/Str]
                              :summary "Returns a list of supported currencies"
                              (ok
                               (seq currencies/known-currencies))))))

(defroutes* organization-routes
  (context "/organizations" []
           (middlewares [wrap-authorization]
                        (POST* "/" []
                               :return org-api/Organization
                               :summary "Creates an organization"
                               :body-params [organization :- org-api/CreateOrganizationBodyParams]
                               (created (idiomatize-keys org-api/create organization)))

                        (POST* "/categories" []
                               :return s/Any
                               :summary "Creates a category"
                               :body-params [organization_name :- s/Str
                                             name :- s/Str]
                               (created
                                (idiomatize-keys
                                 categories/add {:name name
                                                 :organization_id (:id (org/lookup-by-name organization_name))})))

                        (GET* "/categories" []
                              :return [s/Any]
                              :query-params [organization_name]
                              (ok (categories/all (:id (org/lookup-by-name organization_name)))))

                        (PUT* "/categories/:id" []
                              :return s/Any
                              :path-params [id :- s/Uuid]
                              :body-params [organization_name :- s/Str
                                            name :- s/Str]
                              (respond-with-found-or-missing
                               (idiomatize-keys
                                categories/update {:id id
                                                   :name name
                                                   :organization_id (:id (org/lookup-by-name organization_name))})
                               "category" "id" id))

                        (DELETE* "/categories/:id" []
                                 :return s/Any
                                 :path-params [id :- s/Uuid]
                                 (if (categories/delete id)
                                   (ok {:id id})
                                   (not-found {:errors "Not Found"})))))

  (defroutes* admin-routes
    (context "/admin" []
             (middlewares [wrap-authorization]
                          (POST* "/invite" [req]
                                 :body-params [organization_name :- s/Str
                                               user_email :- s/Str]
                                 (if (empty? (orgs-users/lookup-by-email-and-org user_email organization_name))
                                   (users-api/send-invite user_email
                                                          organization_name
                                                          (tokens/create-token :invite_token
                                                                               [user_email organization_name]))
                                   (bad-request {:error "User is already present in your company"})))

                          (GET* "/users" [req]
                                :return [s/Any]
                                :query-params [organization_name]
                                (ok (orgs-users-api/users organization_name)))
                          (DELETE* "/users/:id" []
                                 :return s/Any
                                 :path-params [id :- s/Uuid]
                                 (if (orgs-users-api/delete-user id)
                                   (ok {:id id})
                                   (not-found {:errors "Not Found"})))))))

(defroutes* dashboard-routes
  (context "/reports/dashboard" []
           (middlewares [wrap-authorization]
                        (GET* "/all" [req]
                              :return s/Any
                              :query-params [organization_name :- s/Str from :- s/Inst to :- s/Inst]
                              (let [org-id (:id (org/lookup-by-name organization_name))
                                    from (time-c/to-sql-date from)
                                    to (time-c/to-sql-date to)]
                                (ok {:report (idiomatize-keys (partial dashboard-reports-api/all org-id from) to)}))))))

(defroutes* user-routes
  (POST* "/login" [req]
         :return users-api/LoggedInUser
         :summary "Log a user in"
         :body-params [creds :- users-api/LoginBodyParams]
         (let [lower-case-hyphenate (fn [str] (str/join "-" (map str/lower-case (str/split str #" "))))
               org-name (lower-case-hyphenate (:team_name creds))
               user-email (:user_email creds)
               user (idiomatize-keys users/lookup-by-email user-email)
               org-user (idiomatize-keys (partial orgs-users/lookup-by-email-and-org user-email) org-name)]
           (if (auth/authenticated? user (assoc creds :org-user org-user))
             (ok {:token (token/create org-user)})
             (unauthorized {}))))

  (POST* "/logout" []
         :summary "Log a user out"
         :body-params [token :- s/Uuid]
         (token/delete token)
         (ok {}))

  (POST* "/signup" [req]
         :summary "Signup admin user with an organization"
         :body-params [params :- users-api/SignupBodyParams]
         (if (users-api/creds-present? params)
           (do
             (idiomatize-keys org-api/create params)
             (ok {}))
           (bad-request {:error "Passwords do not match"})))

  (POST* "/member_signup" [req]
         :summary "Signup a member user with an organization"
         :body-params [params :- users-api/MemberSignupBodyParams]
         (if (users-api/creds-present? params)
           (do
             (when (idiomatize-keys (partial org/accept-invite params) (:token params))
               (ok {})))
           (bad-request {:error "Passwords do not match"})))

  (POST* "/forgot_password" [req]
         :body-params [params :- users-api/ForgotPasswordParams]
         (let [user_email (:user_email params)
               organization_name (:organization_name params)]
           (if (users-api/present? (:user_email params))
             (let [token (tokens/create-token :password_reset [user_email organization_name])]
               (users-api/send-forgot-password user_email
                                               organization_name
                                               token)
               (ok {:token token}))
             (bad-request {:error "No such user exists in the organization"}))))

  (POST* "/update_password" [req]
         :body-params [params :- users-api/UpdatePasswordParams]
         (if (and (users-api/creds-present? params)
                  (orgs-users/accept-password-reset (assoc params
                                                           :id (:id (users/lookup-by-email (:user_email params))))
                                                    (:token params)))
           (ok {})
           (bad-request {:error "Passwords do not match"})))

  (GET* "/verify_invite/:token" [req]
        :path-params [token :- s/Uuid]
        :query [params users-api/VerifyInviteParams]
        (if (tokens/verify-token :invite_token [(:user_email params) (:organization_name params)] token)
          (ok {})
          (unauthorized {})))

  (GET* "/verify_password/:token" [req]
        :path-params [token :- s/Uuid]
        :query [params users-api/VerifyPasswordParams]
        (if (tokens/verify-token :password_reset [(:user_email params) (:organization_name params)] token)
          (ok {})
          (unauthorized {}))))

;;
;; Transcriber API
;; This is entirely self-contained and does not depend on any other calls, and can be safely deleted in one-go.
;;
(defn wrap-transcriber-authorization [handler]
  (fn [{:keys [params headers] :as req}]
    (if (tokens/verify-token :transcriber [(:user_email params)] (headers "x-auth-token"))
      (handler req)
      (unauthorized {}))))

(defroutes* transcriber-routes
  (context "/transcriber" []
           (POST* "/login" [req]
                  :return tcrb-api/LoggedInUser
                  :body-params [params :- tcrb-api/LoginBodyParams]
                  (let [user-email (:user_email params)
                        user (idiomatize-keys users/lookup-by-email user-email)]
                    (when (not (empty? (orgs-users/lookup-by-email-and-org user-email "transcribers")))
                      (if (cemerick.friend.credentials/bcrypt-credential-fn {(:email user) user}
                                                                            {:username (:user_email params)
                                                                             :password (:password params)})
                        (ok {:token (tokens/create-token :transcriber [user-email])})
                        (unauthorized {})))))

           (middlewares [wrap-transcriber-authorization]
                        (GET* "/" []
                              :return tcrb-api/PaginatedInvoiceList
                              :query [params tcrb-api/GetInvoicesQueryParams]
                              (ok (idiomatize-keys tcrb-api/index params)))

                        (GET* "/:id/next_and_prev_invoices" []
                              :return s/Any
                              :query [params tcrb-api/GetInvoicesQueryParams]
                              :path-params [id :- tcrb-api/InvoiceID]
                              (ok (idiomatize-keys
                                   tcrb-api/next-and-prev-invoices [id params])))

                        (GET* "/:id" []
                              :return tcrb-api/Invoice
                              :path-params [id :- tcrb-api/InvoiceID]
                              (respond-with-found-or-missing
                               (idiomatize-keys tcrb-api/lookup id) "invoice" "id" id))

                        (PUT* "/:id" []
                              :return tcrb-api/Invoice
                              :path-params [id :- tcrb-api/InvoiceID]
                              :body-params [user_email :- s/Str
                                            invoice :- tcrb-api/ChangeInvoiceBodyParams]
                              (respond-with-found-or-missing
                               (idiomatize-keys tcrb-api/update [id invoice])
                               "invoice" "id" id)))))

(defapi app
  (swagger-ui)
  (swagger-docs
   :title "Kulu API"
   :description "This is Kulu API.")
  (middlewares [wrap-request-logging]
               (swaggered "Mailer"
                          :description "Operations for receiving e-mails"
                          (context "/expenses" []
                                   (middlewares [wrap-email-authorization]
                                                (POST* "/mail_callback" {params :params
                                                                         organization :organization}
                                                       (future (inv-api/receive-mail (assoc params
                                                                                            :organization organization)))
                                                       (accepted {})))))

               (swaggered "Invoices"
                          :description "Operations for invoices"
                          invoice-routes)

               (swaggered "Currencies"
                          :description "Operations for fetching currency information"
                          currency-routes)

               (swaggered "Organizations"
                          :description "Operations for organizations"
                          organization-routes)

               (swaggered "Admin"
                          :description "Operations for admin users"
                          admin-routes)

               (swaggered "Transcriber"
                          :description "Operations for the transcriber user"
                          transcriber-routes)

               (swaggered "Dashboard Reports"
                          :description "Operations for fetch dashboard reports"
                          dashboard-routes)

               (swaggered "Users"
                          :description "Operations for authenticating users"
                          user-routes)))

(def app
  (-> (handler/site app)))
