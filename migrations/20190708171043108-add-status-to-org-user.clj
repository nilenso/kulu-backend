
;; migrations/20190708171043108-add-status-to-org-user.clj

(defn up []
  ["ALTER TABLE organizations_users ADD COLUMN is_active boolean DEFAULT true"])

(defn down []
  ["ALTER TABLE organizations_users DROP COLUMN is_active"])
