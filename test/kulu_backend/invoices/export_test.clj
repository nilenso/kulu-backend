(ns kulu-backend.invoices.export-test
  (:require [kulu-backend.invoices.export :as export]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))


(deftest create-xlsx-sheet
  (testing "Create sheet returns a valid xlsx file"
    (let [headers ["Merchant Name" "Expense Date" "Currency" "Amount"
                   "Type" "Status" "Conflict"
                   "Spender" "Category" "Submission Date" "Link"]
          rows [["Chouiia" "2016-2-20" "INR" "1600" "Company" "Submitted"
                 "FALSE" "foo" "Food" "2016-2-21" "http://foo.bar"]]
          data {:headers headers :rows rows}
          search-query [["Search Parameter" "Value"]
                        ["Merchant Name" "Chinita"]]
          params {:name "Chinita", :per-page 15, :page 1}]
      (is (= search-query (export/construct-search-query params)))
      (is (.exists (io/file (export/create-sheet data params)))))))
