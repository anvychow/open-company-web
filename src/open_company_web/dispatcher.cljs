(ns open-company-web.dispatcher
  (:require [cljs-flux.dispatcher :as flux]))

(defonce app-state (atom {
  ; :OPEN {
  ;   :name "Transparency, LLC"
  ;   :symbol "OPEN"
  ;   :currency ["USD"]
  ;   :headcount {
  ;     :founders 2
  ;     :executives 0
  ;     :ft-employees 3
  ;     :ft-contractors 0
  ;     :pt-employees 0
  ;     :pt-contractors 2
  ;     :comment "Transparency headcount comment."
  ;   },
  ;   :finances {
  ;     :cash 173228
  ;     :revenue 2767
  ;     :costs 22184
  ;     :burn-rate -19417
  ;     :runway "9 months"
  ;     :comment "Transparency finances comment."
  ;   },
  ;   :compensation {
  ;     :percentage false
  ;     :founders 6357
  ;     :executives 0
  ;     :employees 5899
  ;     :contractors 2582
  ;     :comment "Transparency compensation comment."
  ;   }
  ; }
  ; :BUFFR {
  ;   :name "Buffer"
  ;   :symbol "BUFFR"
  ;   :currency ["USD"]
  ;   :headcount {
  ;     :founders 1
  ;     :executives 2
  ;     :ft-employees 1
  ;     :ft-contractors 2
  ;     :pt-employees 1
  ;     :pt-contractors 4
  ;     :comment "Buffer headcount comment."
  ;   },
  ;   :finances {
  ;     :cash 323232
  ;     :revenue 1234
  ;     :costs 11321
  ;     :burn-rate -10000
  ;     :runway "9 months"
  ;     :comment "Buffer finances comment."
  ;   },
  ;   :compensation {
  ;     :percentage true
  ;     :founders 40
  ;     :executives 40
  ;     :employees 10
  ;     :contractors 10
  ;     :comment "Buffer compensation comment."
  ;   }
  ; }
}))


(def companies (flux/dispatcher))

(def company (flux/dispatcher))

(def companies-list-dispatch
  (flux/register
    companies
    (fn [body]
      (when body
        (doseq [company body]
          (swap! app-state  assoc (keyword (get company "symbol")) company))))))

(def company-dispatch
  (flux/register
    company
    (fn [body]
      (println "asd " body)
      (when body
        (swap! app-state  assoc (keyword (body "symbol")) body)
        (println app-state)))))
