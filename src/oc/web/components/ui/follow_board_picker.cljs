(ns oc.web.components.ui.follow-board-picker
  (:require [rum.core :as rum]
            [cuerdas.core :as string]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]
            [oc.web.actions.user :as user-actions]
            [oc.web.mixins.ui :refer (strict-refresh-tooltips-mixin)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.follow-button :refer (follow-button)]
            [oc.web.actions.notifications :as notification-actions]))

(defn- sort-boards [boards]
  (sort-by :name boards))

(defn- search-string [v q]
  (-> v string/lower (string/includes? q)))

(defn- search-board [board q]
  (or (-> board :name (search-string q))
      (-> board :slug (search-string q))))

(defn- filter-board [s board q]
  (and (not= (:slug board) utils/default-drafts-board-slug)
       (not (:publisher-board board))
       (or (not (seq q))
           (search-board board q)
           (some (partial search-board board) (string/split q #"\s")))))

(defn- filter-sort-boards [s boards q]
  (sort-boards (filterv #(filter-board s % (string/lower q)) boards)))

(rum/defcs follow-board-picker < rum/reactive

 (drv/drv :org-data)
 (drv/drv :follow-boards-list)
 (drv/drv :followers-boards-count)
 (rum/local "" ::query)
 (rum/local false ::saving)
 strict-refresh-tooltips-mixin
 {:init (fn [s]
   ;; Refresh the following list
   (user-actions/load-follow-list)
   (user-actions/load-followers-count)
   s)
  :will-unmount (fn [s]
   (user-actions/refresh-follow-containers)
   s)}

  [s]
  (let [org-data (drv/react s :org-data)
        follow-boards-list (map :uuid (drv/react s :follow-boards-list))
        followers-boards-count (drv/react s :followers-boards-count)
        all-boards (:boards org-data)
        with-follow (map #(assoc % :follow (utils/in? follow-boards-list (:uuid %))) all-boards)
        sorted-boards (filter-sort-boards s with-follow @(::query s))
        is-mobile? (responsive/is-mobile-size?)
        following-boards (filter #(->> % :uuid (utils/in? follow-boards-list)) sorted-boards)
        unfollowing-boards (filter #(->> % :uuid (utils/in? follow-boards-list) not) sorted-boards)]
    [:div.follow-board-picker
      [:div.follow-board-picker-modal
        [:button.mlb-reset.modal-close-bt
          {:on-click #(nav-actions/close-all-panels)}]
        [:div.follow-board-picker-header
          [:button.mlb-reset.create-board-bt
            {:on-click #(nav-actions/show-section-add)}
            "Create a new team"]
          [:h3.follow-board-picker-title
            "Teams"]]
        [:div.follow-board-picker-body
          (if (zero? (count all-boards))
            [:div.follow-board-picker-empty-boards
              [:div.follow-board-picker-empty-icon]
              [:div.follow-board-picker-empty-copy
                "There are no teams to follow yet. "
                (when (utils/link-for (:links org-data) "create")
                  [:button.mlb-reset.follow-board-picker-empty-invite-bt
                    {:on-click #(nav-actions/show-org-settings :invite-picker)}
                    "Add a team to get started."])]]
            [:div.follow-board-picker-body-inner.group
              [:input.follow-board-picker-search-field-input.oc-input
                {:value @(::query s)
                 :type "text"
                 :ref :query
                 :placeholder "Find a team..."
                 :on-change #(reset! (::query s) (.. % -target -value))}]
              [:div.follow-board-picker-boards-list.group
                ;; Following
                (when (seq following-boards)
                  [:div.follow-board-picker-row-header
                    (str "Following (" (count following-boards) ")")])
                (when (seq following-boards)
                  (for [b following-boards]
                    [:div.follow-board-picker-board-row.group
                      {:key (str "follow-board-picker-" (:uuid b))
                       :class (when (:follow b) "selected")}
                      [:div.follow-board-picker-board
                        (:name b)]
                      (let [followers-count (:count (some #(when (= (:resource-uuid %) (:uuid b)) %) followers-boards-count))]
                        [:span.followers-count
                          (if (pos? followers-count)
                            (str followers-count " follower" (when (not= followers-count 1) "s"))
                            "No followers")])
                      (follow-button {:following true :resource-type :board :resource-uuid (:uuid b)})]))
                ;; Unfollowing
                (when (seq unfollowing-boards)
                  [:div.follow-board-picker-row-header
                    (str "Other teams (" (count unfollowing-boards) ")")])
                (when (seq unfollowing-boards)
                  (for [b unfollowing-boards]
                    [:div.follow-board-picker-board-row.group
                      {:key (str "unfollow-board-picker-" (:uuid b))
                       :class (when (:follow b) "selected")}
                      [:div.follow-board-picker-board
                        (:name b)]
                      (let [followers-count (:count (some #(when (= (:resource-uuid %) (:uuid b)) %) followers-boards-count))]
                        [:span.followers-count
                          (if (pos? followers-count)
                            (str followers-count " follower" (when (not= followers-count 1) "s"))
                            "No followers")])
                      (follow-button {:following false :resource-type :board :resource-uuid (:uuid b)})]))]])]]]))
