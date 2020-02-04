(ns oc.web.stores.search
  (:require [taoensso.timbre :as timbre]
            [oc.web.lib.jwt :as jwt]
            [oc.web.dispatcher :as dispatcher]))

(defonce search-limit 20)
(defonce savedsearch (atom ""))

(defonce search-key :search-results)
(defonce search-active? :search-active)

(defn search-results []
  (get-in @dispatcher/app-state search-key))

(defn saved-search []
  (let [tmp @savedsearch]
    (reset! savedsearch "")
    tmp))

(defn should-display []
  " If the user is anonymous or not part of the orginization
    don't display search component.
  "
  (if-not (jwt/jwt)
    false
    (if (jwt/user-is-part-of-the-team (:team-id (dispatcher/org-data)))
      true
      false)))

(defn- cleanup-uuid
  [results]
  (vec (map (fn [result]
              (let [source (:_source result)
                    new-uuid (clojure.string/replace (:uuid source)
                                                     "entry-" "")]
                (assoc result :_source (assoc source :uuid new-uuid))))
            results)))

(defmethod dispatcher/action :search-query
  [db [_ search-query]]
  (dissoc db search-key))


(defmethod dispatcher/action :search-query/start
  [db [_ search-query]]
  (assoc-in db [search-key :loading] true))


(defmethod dispatcher/action :search-query/finish
  [db [_ {:keys [success error body query]}]]

  (let [total-hits (:total body)
        results (vec (sort-by #(:created-at (:_source %)) (:hits body)))]
    (when success
      (reset! savedsearch query))
    (if success
      (assoc db search-key {:count total-hits :results (cleanup-uuid results)})
      db)))

(defmethod dispatcher/action :search-active
  [db [_]]
  (assoc db search-active? true))

(defmethod dispatcher/action :search-inactive
  [db [_]]
  (assoc db search-active? false))

(defmethod dispatcher/action :search-reset
  [db [_]]
  (dissoc db search-key))

(defmethod dispatcher/action :search-result-clicked
  [db [_]]
  (assoc db search-active? false))