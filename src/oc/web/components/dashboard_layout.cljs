 (ns oc.web.components.dashboard-layout
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.shared.useragent :as ua]
            [oc.web.lib.cookies :as cook]
            [oc.web.utils.activity :as au]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.stores.search :as search]
            [oc.web.actions.org :as org-actions]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.utils.ui :refer (ui-compose)]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.user :as user-actions]
            [oc.web.components.cmail :refer (cmail)]
            [oc.web.actions.cmail :as cmail-actions]
            [oc.web.actions.search :as search-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.actions.reminder :as reminder-actions]
            [oc.web.components.search :refer (search-box)]
            [oc.web.components.ui.face-pile :refer (face-pile)]
            [oc.web.components.expanded-post :refer (expanded-post)]
            [oc.web.components.paginated-stream :refer (paginated-stream)]
            [oc.web.components.ui.empty-org :refer (empty-org)]
            [oc.web.components.ui.lazy-stream :refer (lazy-stream)]
            [oc.web.components.ui.empty-board :refer (empty-board)]
            [oc.web.components.ui.dropdown-list :refer (dropdown-list)]
            [oc.web.components.user-notifications :as user-notifications]
            [oc.web.components.navigation-sidebar :refer (navigation-sidebar)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(rum/defcs dashboard-layout < rum/static
                              rum/reactive
                              ;; Derivative
                              (drv/drv :route)
                              (drv/drv :org-data)
                              (drv/drv :team-data)
                              (drv/drv :team-roster)
                              (drv/drv :active-users)
                              (drv/drv :board-data)
                              (drv/drv :container-data)
                              (drv/drv :filtered-posts)
                              (drv/drv :items-to-render)
                              (drv/drv :editable-boards)
                              (drv/drv :show-add-post-tooltip)
                              (drv/drv :current-user-data)
                              (drv/drv :cmail-state)
                              (drv/drv :cmail-data)
                              (drv/drv :user-notifications)
                              (drv/drv :mobile-user-notifications)
                              (drv/drv :activity-data)
                              (drv/drv :foc-layout)
                              (drv/drv :activities-read)
                              (drv/drv search/search-active?)
                              ;; Mixins
                              ui-mixins/strict-refresh-tooltips-mixin
                              {:before-render (fn [s]
                                ;; Check if it needs any NUX stuff
                                (nux-actions/check-nux)
                                s)
                               :did-mount (fn [s]
                                ;; Reopen cmail if it was open
                                (when-let [editable-boards @(drv/get-ref s :editable-boards)]
                                  (when (pos? (count editable-boards))
                                    (cmail-actions/cmail-reopen?)))
                                ;; Preload reminders
                                (reminder-actions/load-reminders)
                                (set! (.. js/document -scrollingElement -scrollTop) (utils/page-scroll-top))
                                s)}
  [s]
  (let [org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        container-data (drv/react s :container-data)
        posts-data (drv/react s :filtered-posts)
        _items-to-render (drv/react s :items-to-render)
        foc-layout (drv/react s :foc-layout)
        _activities-read (drv/react s :activities-read)
        current-board-slug (router/current-board-slug)
        ;; Board data used as fallback until the board is completely loaded
        org-board-data (first (filter #(= (:slug %) current-board-slug) (:boards org-data)))
        route (drv/react s :route)
        team-data (drv/react s :team-data)
        team-roster (drv/react s :team-roster)
        active-users (drv/react s :active-users)
        activity-data (drv/react s :activity-data)
        is-inbox (= current-board-slug "inbox")
        is-all-posts (= current-board-slug "all-posts")
        is-bookmarks (= current-board-slug "bookmarks")
        is-board (not (or is-inbox is-all-posts is-bookmarks))
        current-activity-id (router/current-activity-id)
        is-tablet-or-mobile? (responsive/is-tablet-or-mobile?)
        is-mobile? (responsive/is-mobile-size?)
        current-board-data (or board-data org-board-data)
        board-container-data (if (dis/is-container? current-board-slug) container-data board-data)
        empty-board? (and (map? board-container-data)
                          (zero? (count (:posts-list board-container-data))))
        is-drafts-board (= current-board-slug utils/default-drafts-board-slug)
        all-boards (drv/react s :editable-boards)
        can-compose? (pos? (count all-boards))
        board-view-cookie (router/last-board-view-cookie (router/current-org-slug))
        drafts-board (first (filter #(= (:slug %) utils/default-drafts-board-slug) (:boards org-data)))
        drafts-link (utils/link-for (:links drafts-board) "self")
        show-drafts (pos? (:count drafts-link))
        current-user-data (drv/react s :current-user-data)
        is-admin-or-author (utils/is-admin-or-author? org-data)
        should-show-settings-bt (and current-board-slug
                                     (not (dis/is-container? current-board-slug))
                                     (not (:read-only current-board-data))
                                     (not (:direct current-board-data)))
        cmail-state (drv/react s :cmail-state)
        _cmail-data (drv/react s :cmail-data)
        user-notifications-data (drv/react s :user-notifications)
        showing-mobile-user-notifications (drv/react s :mobile-user-notifications)
        show-expanded-post (and current-activity-id
                                activity-data
                                (not= activity-data :404)
                                ;; Do not show the post under the wrong board slug/uuid
                                (or (= (:board-slug activity-data) current-board-slug)
                                    (= (:board-uuid activity-data) current-board-slug)))
        no-phisical-home-button (js/isiPhoneWithoutPhysicalHomeBt)
        dismiss-all-link (when is-inbox
                           (utils/link-for (:links container-data) "dismiss-all"))
        search-active? (drv/react s search/search-active?)]
      ;; Entries list
      [:div.dashboard-layout.group
        {:class (utils/class-set {:expanded-post-view show-expanded-post
                                  :search-active search-active?})}
        [:div.mobile-more-menu]
        [:div.dashboard-layout-container.group
          (navigation-sidebar)
          (when (and is-mobile?
                     (not show-expanded-post)
                     (or (:collapsed cmail-state)
                         (not cmail-state))
                     (jwt/user-is-part-of-the-team (:team-id org-data)))
            [:div.dashboard-layout-mobile-tabbar
              {:class (utils/class-set {:can-compose can-compose?
                                        :ios-app-tabbar (and ua/mobile-app?
                                                             no-phisical-home-button)
                                        :ios-web-tabbar (and (not ua/mobile-app?)
                                                             no-phisical-home-button)})}
              [:button.mlb-reset.tab-button.all-posts-tab
                {:on-click #(do
                              (.stopPropagation %)
                              (nav-actions/nav-to-url! % "all-posts" (oc-urls/all-posts)))
                 :class (when (and (not showing-mobile-user-notifications)
                                   (= current-board-slug "all-posts"))
                          "active")}
                [:span.tab-icon]
                [:span.tab-label "Recent"]]
              [:button.mlb-reset.tab-button.inbox-tab
                {:on-click #(do
                              (.stopPropagation %)
                              (nav-actions/nav-to-url! % "inbox" (oc-urls/inbox)))
                 :class (when (and (not showing-mobile-user-notifications)
                                   (= current-board-slug "inbox"))
                          "active")}
                [:span.tab-icon
                  (when (-> org-data :inbox-count pos?)
                    [:span.count-badge
                      (:inbox-count org-data)])]
                [:span.tab-label "Unread"]]
              [:button.mlb-reset.tab-button.notifications-tab
                {:on-click #(do
                              (.stopPropagation %)
                              (user-actions/show-mobile-user-notifications))
                 :class (when showing-mobile-user-notifications
                          "active")}
                [:span.tab-icon
                  (when (user-notifications/has-new-content? user-notifications-data)
                    [:span.unread-dot])]
                [:span.tab-label "Alerts"]]
              (when can-compose?
                [:button.mlb-reset.tab-button.new-post-tab
                  {:on-click #(do
                                (.stopPropagation %)
                                (ui-compose @(drv/get-ref s :show-add-post-tooltip))
                                (user-actions/hide-mobile-user-notifications))}
                  [:span.tab-icon]
                  [:span.tab-label "Add"]])])
          ;; Mobile notifications
          (when (and is-mobile?
                     showing-mobile-user-notifications)
            (user-notifications/user-notifications))
          ;; Show the board always on desktop except when there is an expanded post and
          ;; on mobile only when the navigation menu is not visible
          [:div.board-container.group
            ; (let [add-post-tooltip (drv/react s :show-add-post-tooltip)
            ;       non-admin-tooltip (str "Carrot is where you'll find key announcements, updates, and "
            ;                              "decisions to keep you and your team pulling in the same direction.")
            ;       is-second-user (= add-post-tooltip :is-second-user)]
            ;   (when (and (not is-drafts-board)
            ;              (not current-activity-id)
            ;              add-post-tooltip)
            ;     [:div.add-post-tooltip-container.group
            ;       [:button.mlb-reset.add-post-tooltip-dismiss
            ;         {:on-click #(nux-actions/dismiss-add-post-tooltip)}]
            ;       [:div.add-post-tooltips
            ;         {:class (when is-second-user "second-user")}
            ;         [:div.add-post-tooltip-box-mobile]
            ;         [:div.add-post-tooltip-title
            ;           "Welcome to Carrot!"]
            ;           [:div.add-post-tooltip
            ;             (if is-admin-or-author
            ;               (if is-second-user
            ;                 non-admin-tooltip
            ;                 "Create your first post now to see how Carrot works. Don't worry, you can delete it anytime.")
            ;               non-admin-tooltip)]
            ;           (when (and is-admin-or-author
            ;                      (not is-second-user))
            ;             [:button.mlb-reset.add-post-bt
            ;               {:on-click #(when can-compose? (ui-compose @(drv/get-ref s :show-add-post-tooltip)))}
            ;               [:span.add-post-bt-pen]
            ;               "New post"])
            ;         [:div.add-post-tooltip-box.big-web-only
            ;           {:class (when is-second-user "second-user")}]]]))
            (when (and (not is-mobile?)
                       can-compose?)
               (cmail))
            (when-not show-expanded-post
            ;; Board name row: board name, settings button and say something button
              [:div.board-name-container.group
                {:class (when is-drafts-board "drafts-board")}
                ;; Board name and settings button
                [:div.board-name
                  (when current-board-slug
                    [:div.board-name-with-icon
                      (when (:direct current-board-data)
                        [:div.direct-board
                          {:data-toggle "tooltip"
                           :data-placement "top"
                           :data-container "body"
                           :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                           :title "Visible only to the included users"}
                          (let [except-me (filter #(not= (:user-id current-user-data) (:user-id %)) (:authors current-board-data))
                                authors-data (map #(->> % :user-id (get active-users)) except-me)]
                            (face-pile {:users-data authors-data}))])
                      [:div.board-name-with-icon-internal
                        {:class (utils/class-set {:private (and (= (:access current-board-data) "private")
                                                                (not is-drafts-board)
                                                                (not (:direct current-board-data)))
                                                  :direct (:direct current-board-data)
                                                  :public (= (:access current-board-data) "public")})
                         :data-toggle (when (and (:direct current-board-data) (not is-mobile?)) "tooltip")
                         :data-placement "top"
                         :data-container "body"
                         :title (or (:complete-name current-board-data) (:original-name current-board-data) (:name current-board-data))
                         :dangerouslySetInnerHTML (utils/emojify (cond
                                                   is-inbox
                                                   "Unread"

                                                   is-all-posts
                                                   "Recent"

                                                   is-bookmarks
                                                   "Bookmarks"

                                                   :default
                                                   ;; Fallback to the org board data
                                                   ;; to avoid showing an empty name while loading
                                                   ;; the board data
                                                   (:name current-board-data)))}]
                      (when (and (= (:access current-board-data) "private")
                                 (not (:direct current-board-data))
                                 (not is-drafts-board))
                        [:div.private-board
                          {:data-toggle "tooltip"
                           :data-placement "top"
                           :data-container "body"
                           :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                           :title (if (= current-board-slug utils/default-drafts-board-slug)
                                   "Only visible to you"
                                   "Only visible to invited team members")}])
                      (when (= (:access current-board-data) "public")
                        [:div.public-board
                          {:data-toggle "tooltip"
                           :data-placement "top"
                           :data-container "body"
                           :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                           :title "Visible to the world, including search engines"}])])
                  (when should-show-settings-bt
                    [:div.board-settings-container
                      ;; Settings button
                      [:button.mlb-reset.board-settings-bt
                        {:data-toggle (when-not is-tablet-or-mobile? "tooltip")
                         :data-placement "top"
                         :data-container "body"
                         :title (str (:name current-board-data) " settings")
                         :on-click #(nav-actions/show-section-editor (:slug current-board-data))}]])]
                [:div.board-name-right
                  (when (and dismiss-all-link
                             (pos? (count posts-data)))
                    [:button.mlb-reset.complete-all-bt
                      {:on-click #(activity-actions/inbox-dismiss-all)
                       :data-toggle (when-not is-mobile? "tooltip")
                       :data-placement "top"
                       :data-container "body"
                       :title "Dismiss all"}])
                  (when (and (not is-drafts-board)
                             is-mobile?)
                    (search-box))
                  [:button.mlb-reset.foc-layout-bt
                    {:on-click #(activity-actions/toggle-foc-layout)
                     :data-toggle (when-not is-mobile? "tooltip")
                     :data-placement "top"
                     :data-container "body"
                     :data-offset-left "-2px"
                     :title (if (= foc-layout dis/other-foc-layout) "Expanded view" "Compact view")
                     :class (when (= foc-layout dis/other-foc-layout) "collapsed")}]]])

            ;; Board content: empty org, all posts, empty board, drafts view, entries view
            (cond
              ;; No boards
              (zero? (count (:boards org-data)))
              (empty-org)
              ;; Empty board
              empty-board?
              (empty-board)
              show-expanded-post
              (expanded-post)
              ;; Paginated board/container
              :else
              (rum/with-key (lazy-stream paginated-stream)
               (str "paginated-posts-component-" (cond is-inbox "IN" is-all-posts "AP" is-bookmarks "BM" :else (:slug current-board-data))))
              )]]]))