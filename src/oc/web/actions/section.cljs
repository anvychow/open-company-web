(ns oc.web.actions.section
  (:require [taoensso.timbre :as timbre]
            [oc.web.dispatcher :as dispatcher]
            [oc.web.router :as router]
            [oc.web.lib.jwt :as jwt]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.json :refer (json->cljs cljs->json)]
            [oc.web.urls :as oc-urls]
            [oc.web.lib.ws-interaction-client :as ws-ic]
            [oc.web.lib.ws-change-client :as ws-cc]
            [oc.web.api :as api]))

(defn is-currently-shown? [section]
  (= (router/current-board-slug)
     (:slug section)))

(defn watch-single-section [section]
  ;; only watch the currently visible board.
  (ws-ic/board-unwatch (fn [rep]
    (timbre/debug rep "Watching on socket " (:uuid section))
        (ws-ic/board-watch (:uuid section)))))

(defn load-other-sections
  [sections]
  (doseq [section sections
          :when (not (is-currently-shown? section))]
    (api/get-board (utils/link-for (:links section) ["item" "self"] "GET")
      (fn [status body success]
        (let [section-data (json->cljs body)]
              ;; is-loaded is meant for the currently in view board components
              (dispatcher/dispatch!
               [:section (assoc section-data :is-loaded false)]))))))

(defn section-seen
  [uuid]
  ;; Let the change service know we saw the board
  (ws-cc/container-seen uuid)
  (dispatcher/dispatch! [:section-seen uuid]))

(defn section-get-finish
  [section]
  (let [is-currently-shown (is-currently-shown? section)]
    (when is-currently-shown
      ;; Tell the container service that we are seeing this board,
      ;; and update change-data to reflect that we are seeing this board
      (when-let [section-uuid (:uuid section)]
        (utils/after 10 #(section-seen section-uuid)))
      ;; only watch the currently visible board.
      (when (jwt/jwt) ; only for logged in users
        (watch-single-section section)))

    (dispatcher/dispatch! [:section (assoc section :is-loaded is-currently-shown)])))

(defn section-change
  [section-uuid change-at]
  (timbre/debug "Section change:" section-uuid "at:" change-at)
  (utils/after 0 (fn []
    (let [current-section-data (dispatcher/board-data)]
      (if (= section-uuid (:uuid current-section-data))
        ;; Reload the current board data
        (api/get-board (utils/link-for (:links current-section-data) "self")
                       (fn [status body success]
                         (when success (section-get-finish (json->cljs body)))))
        ;; Reload a secondary board data
        (let [sections (:boards (dispatcher/org-data))
              filtered-sections (filter #(= (:uuid %) section-uuid) sections)]
          (load-other-sections filtered-sections))))))
  ;; Update change-data state that the board has a change
  (dispatcher/dispatch! [:section-change section-uuid change-at]))

(defn section-get
  [link]
  (api/get-board link
    (fn [status body success]
      (when success (section-get-finish (json->cljs body))))))

(defn section-nav-away [uuid]
  (dispatcher/dispatch! [:section-nav-away uuid]))

(defn section-delete [section-slug]
  (api/delete-board section-slug (fn [status success body]
    (if success
      (let [org-slug (router/current-org-slug)]
        (if (= section-slug (router/current-board-slug))
          (router/redirect! (oc-urls/all-posts org-slug))
          (dispatcher/dispatch! [:section-delete org-slug section-slug])))
      (.reload (.-location js/window))))))

(defn refresh-org-data []
  (api/get-org (dispatcher/org-data)
    (fn [{:keys [status body success]}]
      (dispatcher/dispatch! [:org-loaded (json->cljs body)]))))

(defn section-name-error [status]
  ;; Board name exists or too short
  (dispatcher/dispatch!
   [:input
    [:section-editing :section-name-error]
    (cond
      (= status 409) "Section name already exists or isn't allowed"
      :else "An error occurred, please retry.")]))

(defn section-save
  ([section-data note] (section-save section-data note nil))
  ([section-data note success-cb]
    (section-save section-data note success-cb section-name-error))
  ([section-data note success-cb error-cb]
    (timbre/debug section-data)
    (if (empty? (:links section-data))
      (api/create-board section-data note
        (fn [{:keys [success status body]}]
          (let [section-data (when success (json->cljs body))]
            (if-not success
              (when (fn? error-cb)
                (error-cb status))
              (do
                (utils/after 100 #(router/nav! (oc-urls/board (router/current-org-slug) (:slug section-data))))
                (utils/after 500 refresh-org-data)
                (ws-cc/container-watch (:uuid section-data))
                (dispatcher/dispatch! [:section-edit-save/finish section-data])
                (when (fn? success-cb)
                  (success-cb)))))))
      (api/patch-board section-data note (fn [success body status]
        (if-not success
          (when (fn? error-cb)
            (error-cb status))
          (do
            (refresh-org-data)
            (dispatcher/dispatch! [:section-edit-save/finish (json->cljs body)])
            (when (fn? success-cb)
              (success-cb)))))))))

(defn private-section-user-add
  [user user-type]
  (dispatcher/dispatch! [:private-section-user-add user user-type]))

(defn private-section-user-remove
  [user]
  (dispatcher/dispatch! [:private-section-user-remove user]))

(defn private-section-kick-out-self
  [user]
  (when (= (:user-id user) (jwt/user-id))
    (api/remove-user-from-private-board user (fn [status success body]
      ;; Redirect to the first available board
      (let [org-data (dispatcher/org-data)
            all-boards (:boards org-data)
            current-board-slug (router/current-board-slug)
            except-this-boards (remove #(#{current-board-slug "drafts"} (:slug %)) all-boards)
            redirect-url (if-let [next-board (first except-this-boards)]
                           (oc-urls/board (:slug next-board))
                           (oc-urls/org (router/current-org-slug)))]
        (refresh-org-data)
        (utils/after 0 #(router/nav! redirect-url))
        (dispatcher/dispatch! [:private-section-kick-out-self/finish success]))))))

(defn ws-comment-add
  [interaction-data]
  (let [org-slug   (router/current-org-slug)
        board-slug (router/current-board-slug)
        activity-uuid (:resource-uuid interaction-data)
        entry-data (dispatcher/activity-data org-slug board-slug activity-uuid)]
    (when-not entry-data
      (let [board-data (dispatcher/board-data)]
        (section-get (utils/link-for (:links (dispatcher/board-data)) ["item" "self"] "GET"))))))

(defn ws-change-subscribe []
  (ws-cc/subscribe :container/status
    (fn [data]
      (dispatcher/dispatch! [:container/status (:data data)])))

  (ws-cc/subscribe :container/change
    (fn [data]
      (let [change-data (:data data)
            section-uuid (:item-id change-data)
            change-type (:change-type change-data)
            change-at (:change-at change-data)]
        ;; Refresh the section only in case of an update, let the org
        ;; handle the add and delete cases
        (when (= change-type :update)
          (section-change section-uuid change-at)))))
  (ws-cc/subscribe :item/change
    (fn [data]
      (let [change-data (:data data)
            section-uuid (:container-id change-data)
            change-type (:change-type change-data)]
        ;; Refresh the section only in case of items added or removed
        ;; let the activity handle the item update case
        (when (or (= change-type :add)
                  (= change-type :delete))
          (section-change section-uuid (:change-at change-data)))
        ;; On item/change :add let's add the UUID to the unseen list of
        ;; the specified container to make sure it's marked as seen
        (when (= change-type :add)
          (dispatcher/dispatch! [:item-add/unseen (router/current-org-slug) change-data]))
        (when (= change-type :delete)
          (dispatcher/dispatch! [:item-delete/unseen (router/current-org-slug) change-data]))))))

(defn ws-interaction-subscribe []
  (ws-ic/subscribe :interaction-comment/add
                   #(ws-comment-add (:data %))))

;; Section editing

(def min-section-name-length 2)

(defn section-save-create [section-editing section-name success-cb]
  (if (< (count section-name) min-section-name-length)
    (dispatcher/dispatch! [:section-edit/error (str "Name must be at least " min-section-name-length " characters.")])
    (let [next-section-editing (merge section-editing {:slug utils/default-section-slug
                                                       :name section-name})]
      (dispatcher/dispatch! [:input [:section-editing] next-section-editing])
      (success-cb next-section-editing))))