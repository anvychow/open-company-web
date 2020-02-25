(ns oc.web.components.navigation-sidebar
  (:require [rum.core :as rum]
            [clojure.string :as s]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.utils.dom :as dom-utils]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.stores.user :as user-store]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.components.ui.menu :as menu]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.components.ui.trial-expired-banner :refer (trial-expired-alert)]
            [oc.web.components.ui.orgs-dropdown :refer (orgs-dropdown)]))

(defn sort-boards [boards]
  (vec (sort-by :name boards)))

(def sidebar-top-margin 56)

(defn save-content-height [s]
  (when-let [navigation-sidebar (rum/ref-node s :left-navigation-sidebar)]
    (let [height (.-offsetHeight navigation-sidebar)]
      (when (not= height @(::content-height s))
        (reset! (::content-height s) height)))))

(defn- toggle-collapse-sections [s]
  (let [next-value (not @(::sections-list-collapsed s))]
    (cook/set-cookie! (router/collapse-sections-list-cookie) next-value (* 60 60 24 365))
    (reset! (::sections-list-collapsed s) next-value)
    (reset! (::content-height s) nil)
    (utils/after 100 #(save-content-height s))))

(defn filter-board [board-data]
  (let [self-link (utils/link-for (:links board-data) "self")]
    (and (not= (:slug board-data) utils/default-drafts-board-slug)
         (or (not (contains? self-link :count))
             (and (contains? self-link :count)
                  (pos? (:count self-link))))
         (or (not (contains? board-data :draft))
             (not (:draft board-data))))))

(defn filter-boards [all-boards]
  (filterv filter-board all-boards))

(defn save-window-size
  "Save the window height in the local state."
  [s]
  (reset! (::window-height s) (.-innerHeight js/window))
  (reset! (::window-width s) (.-innerWidth js/window)))

(def drafts-board-prefix (-> utils/default-drafts-board :uuid (str "-")))

(rum/defcs navigation-sidebar < rum/reactive
                                ;; Derivatives
                                (drv/drv :org-data)
                                (drv/drv :board-data)
                                (drv/drv :change-data)
                                (drv/drv :current-user-data)
                                (drv/drv :mobile-navigation-sidebar)
                                (drv/drv :drafts-data)
                                (drv/drv :bookmarks-data)
                                ;; Locals
                                (rum/local false ::content-height)
                                (rum/local nil ::window-height)
                                (rum/local nil ::window-width)
                                (rum/local nil ::last-mobile-navigation-panel)
                                (rum/local false ::sections-list-collapsed)
                                ;; Mixins
                                ui-mixins/first-render-mixin
                                (ui-mixins/render-on-resize save-window-size)

                                {:will-mount (fn [s]
                                  (save-window-size s)
                                  (save-content-height s)
                                  (reset! (::sections-list-collapsed s) (= (cook/get-cookie (router/collapse-sections-list-cookie)) "true"))
                                  s)
                                 :before-render (fn [s]
                                  (nux-actions/check-nux)
                                  s)
                                 :did-mount (fn [s]
                                  (save-content-height s)
                                  s)
                                 :will-update (fn [s]
                                  (save-content-height s)
                                  (when (responsive/is-mobile-size?)
                                    (let [mobile-navigation-panel (boolean @(drv/get-ref s :mobile-navigation-sidebar))
                                          last-mobile-navigation-panel (boolean @(::last-mobile-navigation-panel s))]
                                      (when (not= mobile-navigation-panel last-mobile-navigation-panel)
                                        (if mobile-navigation-panel
                                          ;; Will open panel, let's block page scroll
                                          (do
                                            (dom-utils/lock-page-scroll)
                                            (reset! (::last-mobile-navigation-panel s) true))
                                          ;; Will close panel, let's unblock page scroll
                                          (do
                                            (dom-utils/unlock-page-scroll)
                                            (reset! (::last-mobile-navigation-panel s) false))))))
                                  s)}
  [s]
  (let [org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        change-data (drv/react s :change-data)
        filtered-change-data (into {} (filter #(and (-> % first (s/starts-with? drafts-board-prefix) not)
                                                    (not= % (:uuid org-data))) change-data))
        current-user-data (drv/react s :current-user-data)
        left-navigation-sidebar-width (- responsive/left-navigation-sidebar-width 20)
        all-boards (:boards org-data)
        boards (filter-boards all-boards)
        sorted-boards (sort-boards boards)
        selected-slug (or (:back-to @router/path) (router/current-board-slug))
        is-inbox (= selected-slug "inbox")
        is-all-posts (= selected-slug "all-posts")
        is-bookmarks (= selected-slug "bookmarks")
        is-drafts-board (= selected-slug utils/default-drafts-board-slug)
        create-link (utils/link-for (:links org-data) "create")
        show-boards (or create-link (pos? (count boards)))
        user-is-part-of-the-team? (jwt/user-is-part-of-the-team (:team-id org-data))
        show-inbox (and user-is-part-of-the-team?
                        (utils/link-for (:links org-data) "inbox"))
        show-all-posts (and user-is-part-of-the-team?
                            (utils/link-for (:links org-data) "entries"))
        show-bookmarks (and user-is-part-of-the-team?
                            (utils/link-for (:links org-data) "bookmarks"))
        drafts-board (first (filter #(= (:slug %) utils/default-drafts-board-slug) all-boards))
        drafts-link (utils/link-for (:links drafts-board) "self")
        org-slug (router/current-org-slug)
        is-mobile? (responsive/is-mobile-size?)
        is-tall-enough? (not (neg? (- @(::window-height s) sidebar-top-margin @(::content-height s))))
        bookmarks-data (drv/react s :bookmarks-data)
        drafts-data (drv/react s :drafts-data)
        all-unread-items (mapcat :unread (vals filtered-change-data))
        user-role (user-store/user-role org-data current-user-data)
        is-admin-or-author? (#{:admin :author} user-role)
        show-invite-people? (and org-slug
                                 is-admin-or-author?)]
    [:div.left-navigation-sidebar.group
      {:class (utils/class-set {:mobile-show-side-panel (drv/react s :mobile-navigation-sidebar)
                                :absolute-position (not is-tall-enough?)
                                :collapsed-sections @(::sections-list-collapsed s)})
       :on-click #(when-not (utils/event-inside? % (rum/ref-node s :left-navigation-sidebar-content))
                    (dis/dispatch! [:input [:mobile-navigation-sidebar] false]))
       :ref :left-navigation-sidebar}
      [:div.left-navigation-sidebar-content
        {:ref :left-navigation-sidebar-content}
        (when is-mobile?
          [:div.left-navigation-sidebar-mobile-header
            [:button.mlb-reset.mobile-close-bt
              {:on-click #(dis/dispatch! [:input [:mobile-navigation-sidebar] false])}]
            (orgs-dropdown)])
        ;; All posts
        (when show-all-posts
          [:a.all-posts.hover-item.group
            {:class (utils/class-set {:item-selected is-all-posts})
             :href (oc-urls/all-posts)
             :on-click #(nav-actions/nav-to-url! % "all-posts" (oc-urls/all-posts))}
            [:div.all-posts-icon]
            [:div.all-posts-label
              {:class (utils/class-set {:new (seq all-unread-items)})}
              "Recent"]
            ; (when (pos? (count all-unread-items))
            ;   [:span.count (count all-unread-items)])
            ])
        ;; Inbox
        (when show-inbox
          [:a.inbox.hover-item.group
            {:class (utils/class-set {:item-selected is-inbox})
             :href (oc-urls/inbox)
             :on-click #(nav-actions/nav-to-url! % "inbox" (oc-urls/inbox))}
            [:div.inbox-icon]
            [:div.inbox-label
              "Unread"]
            (when (pos? (:inbox-count org-data))
              [:span.count (:inbox-count org-data)])])
        ;; Bookmarks
        (when show-bookmarks
          [:a.bookmarks.hover-item.group
            {:class (utils/class-set {:item-selected is-bookmarks})
             :href (oc-urls/bookmarks)
             :on-click #(nav-actions/nav-to-url! % "bookmarks" (oc-urls/bookmarks))}
            [:div.bookmarks-icon]
            [:div.bookmarks-label
              "Saved"]
            (when (pos? (:bookmarks-count org-data))
              [:span.count (:bookmarks-count org-data)])])
        ;; Drafts
        (when drafts-link
          (let [board-url (oc-urls/board (:slug drafts-board))
                draft-count (if drafts-data (count (:posts-list drafts-data)) (:count drafts-link))]
            [:a.drafts.hover-item.group
              {:class (when (and (not is-inbox)
                                 (not is-all-posts)
                                 (not is-bookmarks)
                                 (= (router/current-board-slug) (:slug drafts-board)))
                        "item-selected")
               :data-board (name (:slug drafts-board))
               :key (str "board-list-" (name (:slug drafts-board)))
               :href board-url
               :on-click #(nav-actions/nav-to-url! % (:slug drafts-board) board-url)}
              [:div.drafts-icon]
              [:div.drafts-label.group
                "Drafts "]
              (when (pos? draft-count)
                [:span.count draft-count])]))
        ;; Boards list
        (when show-boards
          [:div.left-navigation-sidebar-top.group
            ;; Boards header
            [:h3.left-navigation-sidebar-top-title.group
              [:button.mlb-reset.left-navigation-sidebar-sections-arrow
                {:class (when @(::sections-list-collapsed s) "collapsed")
                 :on-click #(toggle-collapse-sections s)}
                [:span.sections "Sections"]]
              (when create-link
                [:button.left-navigation-sidebar-top-title-button.btn-reset
                  {:on-click #(nav-actions/show-section-add)
                   :title "Create a new section"
                   :data-placement "top"
                   :data-toggle (when-not is-mobile? "tooltip")
                   :data-container "body"}])]])
        (when (and show-boards
                   (not @(::sections-list-collapsed s)))
          [:div.left-navigation-sidebar-items.group
            (for [board sorted-boards
                  :let [board-url (oc-urls/board org-slug (:slug board))
                        is-current-board (and (not is-inbox)
                                              (not is-all-posts)
                                              (not is-bookmarks)
                                              (= selected-slug (:slug board)))
                        board-change-data (get change-data (:uuid board))]]
              [:a.left-navigation-sidebar-item.hover-item
                {:class (utils/class-set {:item-selected is-current-board})
                 :data-board (name (:slug board))
                 :key (str "board-list-" (name (:slug board)) "-" (rand 100))
                 :href board-url
                 :on-click #(do
                              (nav-actions/nav-to-url! % (:slug board) board-url))}
                [:div.board-name.group
                  {:class (utils/class-set {:public-board (= (:access board) "public")
                                            :private-board (= (:access board) "private")
                                            :team-board (= (:access board) "team")})}
                  [:div.internal
                    {:class (utils/class-set {:new (seq (:unread board-change-data))
                                              :has-icon (#{"public" "private"} (:access board))})
                     :key (str "board-list-" (name (:slug board)) "-internal")
                     :dangerouslySetInnerHTML (utils/emojify (or (:name board) (:slug board)))}]]
                (when (= (:access board) "public")
                  [:div.public])
                (when (= (:access board) "private")
                  [:div.private])])])]
      (when show-invite-people?
        [:div.left-navigation-sidebar-footer
          [:button.mlb-reset.invite-people-bt
            {:on-click #(nav-actions/show-org-settings :invite-picker)}
            "Invite people"]])]))