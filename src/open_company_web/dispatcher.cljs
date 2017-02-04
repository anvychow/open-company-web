(ns open-company-web.dispatcher
  (:require [cljs-flux.dispatcher :as flux]
            [org.martinklepsch.derivatives :as drv]
            [open-company-web.router :as router]))

(defonce app-state (atom {:loading false
                          :mobile-menu-open false
                          :show-login-overlay false
                          :dashboard-sharing false}))

(defn print-app-state []
  (js/console.log @app-state))

;; Derived Data ================================================================

(defn drv-spec [db route-db]
  {:base               [[] db]
   :route              [[] route-db]
   :company-slug       [[:route] (fn [route] (:slug route))]
   :su-share           [[:base] (fn [base] (:su-share base))]
   :su-list            [[:base :company-slug] (fn [base company-slug]
                                                (when company-slug
                                                  (-> company-slug keyword base :su-list :collection :stakeholder-updates)))]
   :user-management    [[:base] (fn [base]
                                 {:um-invite (:um-invite base)
                                  :enumerate-users (:enumerate-users base)
                                  :um-domain-invite (:um-domain-invite base)
                                  :invite-by-email-error (:invite-by-email-error base)
                                  :add-email-domain-team-error (:add-email-domain-team-error base)})]
   :jwt                [[:base] (fn [base] (:jwt base))]
   :current-user-data  [[:base] (fn [base] (:current-user-data base))]
   :subscription       [[:base] (fn [base] (:subscription base))]
   :show-login-overlay [[:base] (fn [base] (:show-login-overlay base))]
   :rum-popover-data   [[:base] (fn [base] (:rum-popover-data base))]
   :company-data       [[:base :company-slug] (fn [base company-slug]
                                                (when company-slug
                                                  (-> company-slug keyword base :company-data)))]})

;; Action Loop =================================================================

(defmulti action (fn [db [action-type & _]] action-type))

(def actions (flux/dispatcher))

(def actions-dispatch
  (flux/register
   actions
   (fn [payload]
     ;; (prn payload) ; debug :)
     (swap! app-state action payload))))

(defn dispatch! [payload]
  (flux/dispatch actions payload))

(def companies-key [:companies])

(defn company-data-key [company-slug]
  [(keyword company-slug) :company-data])

(defn company-section-key [company-slug section]
  [(keyword company-slug) :company-data (keyword section)])

(defn su-list-key [company-slug]
  [(keyword company-slug) :su-list])

(defn latest-stakeholder-update-key [company-slug]
  [(keyword company-slug) :latest-su])

(defn stakeholder-update-key [company-slug update-slug]
  [(keyword company-slug) (keyword update-slug)])

(defn revisions-key [slug]
  [:revisions (keyword slug)])

(defn section-revisions-key [slug section]
  (vec (conj (revisions-key slug) (keyword section))))

(defn revision-key [slug section as-of]
  (vec (conj (section-revisions-key slug section) (str as-of))))

(defn company-data
  ([]
    (company-data @app-state))
  ([data]
    (company-data data (router/current-company-slug)))
  ([data company-slug]
    (get-in data (company-data-key company-slug))))

(defn latest-stakeholder-update
  ([]
    (latest-stakeholder-update @app-state))
  ([data]
    (latest-stakeholder-update data (router/current-company-slug)))
  ([data company-slug]
    (get-in data (latest-stakeholder-update-key company-slug))))

(defn stakeholder-update-list-data
  ([]
    (stakeholder-update-list-data @app-state))
  ([data]
    (stakeholder-update-list-data data (router/current-company-slug)))
  ([data company-slug]
    (get-in data (su-list-key company-slug))))

(defn stakeholder-update-data
  ([]
    (stakeholder-update-data @app-state))
  ([data]
    (stakeholder-update-data data (router/current-company-slug) (router/current-stakeholder-update-slug)))
  ([data company-slug update-slug]
    (get-in data (stakeholder-update-key company-slug update-slug))))

(defn force-edit-topic []
  (:force-edit-topic @app-state))

(defn save-last-company-slug []
  (swap! app-state assoc :last-slug (router/current-company-slug)))

(defn last-company-slug []
  (:last-slug @app-state))

(defn revision
  ([slug section as-of] (revision slug section as-of @app-state))
  ([slug section as-of data] (get-in data (revision-key slug section as-of))))

(defn section-revisions
  ([slug section] (section-revisions slug section @app-state))
  ([slug section data] (get-in data (section-revisions-key slug section))))

(defn revisions
  ([slug] (revisions slug @app-state))
  ([slug data] (get-in data (revisions-key slug))))

(defn foce-section-key []
  (:foce-key @app-state))

(defn foce-section-data []
  (:foce-data @app-state))

(defn foce-section-data-editing? []
  (:foce-data-editing? @app-state))

(set! (.-OCDispatcherPrintAppState js/window) print-app-state)