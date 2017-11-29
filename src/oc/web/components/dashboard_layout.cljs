(ns oc.web.components.dashboard-layout
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.cookies :as cook]
            [oc.web.lib.responsive :as responsive]
            [oc.web.lib.utils :as utils]
            [oc.web.components.ui.empty-org :refer (empty-org)]
            [oc.web.components.ui.carrot-tip :refer (carrot-tip)]
            [oc.web.components.navigation-sidebar :refer (navigation-sidebar)]
            [oc.web.components.ui.filters-dropdown :refer (filters-dropdown)]
            [oc.web.components.ui.empty-board :refer (empty-board)]
            [oc.web.components.entries-layout :refer (entries-layout)]
            [oc.web.components.drafts-layout :refer (drafts-layout)]
            [oc.web.components.all-posts :refer (all-posts)]
            [oc.web.components.ui.dropdown-list :refer (dropdown-list)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(def min-scroll 50)
(def max-scroll 92)

(defn document-scroll-top []
  (if (.-body js/document)
    (max (.-pageYOffset js/window)
         (.-scrollTop (.-documentElement js/document))
         (.-scrollTop (.-body js/document)))
    0))

(defn calc-opacity [scroll-top]
  (let [fixed-scroll-top (/
                          (*
                           (- (min scroll-top max-scroll) 50)
                           100)
                          (- max-scroll min-scroll))]
    (max 0 (min (/ fixed-scroll-top 100) 1))))

(defn did-scroll [e s]
  (let [entry-floating (js/$ "#new-entry-floating-btn-container")]
    (when (pos? (.-length entry-floating))
      (let [scroll-top (document-scroll-top)
            opacity (if @(::show-floating-boards-dropdown s)
                      1
                      (calc-opacity scroll-top))]
        (.css entry-floating #js {:opacity opacity
                                 :display (if (pos? opacity) "block" "none")})))))

(defn get-first-tooltip-message [org-data]
  (let [create-link (utils/link-for (:links org-data) "create")
        boards (:boards org-data)]
    (if create-link
      (if (> (count boards) 1)
        (str
         "Boards are where you’ll find the announcements, updates "
         "and stories that help connect you with your team. "
         "You can create new posts or react and comment on what you read")
        (str
         "We’ve created a super helpful welcome board for you - it’s "
         "full of ideas on how to get the most out of Carrot!"))
      (str
       "Boards are where you’ll find the announcements, updates and stories "
       "that help connect you with your team. You can react and comment on what you read."))))

(defn get-second-tooltip-message [org-data]
  (let [boards (:boards org-data)]
    (if (> (count boards) 1)
      (str
       "When you need to, you can click here to create new boards "
       "for different areas like Sales, Marketing and Product.")
      (str
       "When you’re ready, click here and get rolling with your first boards. "
       "You can create boards for different areas like Sales, Marketing and Product."))))

(rum/defcs dashboard-layout < rum/reactive
                              ;; Derivative
                              (drv/drv :route)
                              (drv/drv :org-data)
                              (drv/drv :board-data)
                              (drv/drv :all-posts)
                              (drv/drv :board-filters)
                              (drv/drv :show-onboard-overlay)
                              (drv/drv :editable-boards)
                              ;; Locals
                              (rum/local nil ::show-boards-tooltip)
                              (rum/local false ::show-plus-tooltip)
                              (rum/local nil ::force-update)
                              (rum/local nil ::ww)
                              (rum/local nil ::resize-listener)
                              (rum/local nil ::scroll-listener)
                              (rum/local nil ::show-top-boards-dropdown)
                              (rum/local nil ::show-floating-boards-dropdown)
                              {:will-mount (fn [s]
                                (let [show-onboard-overlay @(drv/get-ref s :show-onboard-overlay)]
                                  (reset! (::show-boards-tooltip s) show-onboard-overlay))
                                ;; Get current window width
                                (reset! (::ww s) (responsive/ww))
                                ;; Update window width on window resize
                                (reset! (::resize-listener s)
                                 (events/listen js/window EventType/RESIZE #(reset! (::ww s) (responsive/ww))))
                                s)
                               :did-mount (fn [s]
                                (when-not (utils/is-test-env?)
                                  (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))
                                  (reset! (::scroll-listener s)
                                   (events/listen js/window EventType/SCROLL #(did-scroll % s))))
                                ; (when @(::show-boards-tooltip s)
                                ;   (utils/after 1000 #(reset! (::force-update s) true)))
                                s)
                               :will-unmount (fn [s]
                                (when-not (utils/is-test-env?)
                                  (when @(::resize-listener s)
                                    (events/unlistenByKey @(::resize-listener s)))
                                  (when @(::scroll-listener s)
                                    (events/unlistenByKey @(::resize-listener s))))
                                s)}
  [s]
  (let [org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        all-posts-data (drv/react s :all-posts)
        route (drv/react s :route)
        is-all-posts (or (utils/in? (:route route) "all-posts")
                         (:from-all-posts route))
        board-filters (drv/react s :board-filters)
        show-onboard-overlay (drv/react s :show-onboard-overlay)
        current-activity-id (router/current-activity-id)
        is-mobile-size? (responsive/is-mobile-size?)
        dashboard-layout-container-key (if current-activity-id
                                        (str "dashboard-layout-activity-" current-activity-id)
                                        (str "dashboard-layout-" (if is-all-posts "all-posts" (:slug board-data))))
        empty-board? (zero? (count (:fixed-items board-data)))
        org-data (dis/org-data)
        sidebar-width (+ responsive/left-navigation-sidebar-width
                         responsive/left-navigation-sidebar-minimum-right-margin)
        board-container-style {:marginLeft (str
                                            (max
                                             sidebar-width
                                             (+
                                              (/
                                               (- @(::ww s) responsive/board-container-width sidebar-width)
                                               2)
                                              sidebar-width)) "px")}
        entry-topics (distinct (remove empty? (map :topic-slug (vals (:fixed-items board-data)))))
        is-drafts-board (= (:slug board-data) "drafts")
        all-boards (drv/react s :editable-boards)
        topics (:topics board-data)]
      ;; Topic list
      [:div.dashboard-layout.group
        (when (and @(::show-boards-tooltip s)
                   (not show-onboard-overlay))
          (when-let* [nav-boards (js/$ "h3#navigation-sidebar-boards")
                      offset (.offset nav-boards)
                      boards-left (aget offset "left")]
            (let [create-link (utils/link-for (:links org-data) "create")]
              (carrot-tip {:x (+ boards-left 145 30)
                           :y (- (aget offset "top") 110)
                           :title "Welcome to Carrot"
                           :message (get-first-tooltip-message org-data)
                           :footer (if create-link "1 of 2" "")
                           :button-title (if create-link "Next" "Got It!")
                           :big-circle true
                           :on-next-click (fn []
                                            (reset! (::show-plus-tooltip s) (not (not create-link)))
                                            (reset! (::show-boards-tooltip s) false)
                                            (if create-link
                                              (.addClass (js/$ "button#add-board-button") "active")
                                              (cook/remove-cookie!
                                               (router/should-show-dashboard-tooltips
                                                (jwt/get-key :user-id)))))}))))
        (when @(::show-plus-tooltip s)
          (when-let* [plus-button (js/$ "button#add-board-button")
                      offset (.offset plus-button)
                      plus-button-left (aget offset "left")]
            (carrot-tip {:x (+ plus-button-left 90 30)
                         :y (- (aget offset "top") 110)
                         :title "Creating boards"
                         :message (get-second-tooltip-message org-data)
                         :footer ""
                         :button-title "Got It!"
                         :big-circle false
                         :on-next-click (fn []
                                          (.removeClass (js/$ "button#add-board-button") "active")
                                          (reset! (::show-plus-tooltip s) false)
                                          (cook/remove-cookie!
                                           (router/should-show-dashboard-tooltips
                                            (jwt/get-key :user-id))))})))
        [:div.dashboard-layout-container.group
          {:key dashboard-layout-container-key}
          (when-not is-mobile-size?
            (navigation-sidebar))
          [:div.board-container.group
            {:style board-container-style}
            ;; Board name row: board name, settings button and say something button
            [:div.group
              ;; Board name and settings button
              [:div.board-name
                (when (router/current-board-slug)
                  [:span.board-name-span
                    {:dangerouslySetInnerHTML (if is-all-posts
                                                #js {"__html" "All Posts"}
                                                (utils/emojify (:name board-data)))}])
                ;; Settings button
                (when (and (router/current-board-slug)
                           (not is-all-posts)
                           (not (:read-only board-data)))
                  [:button.mlb-reset.board-settings-bt
                    {:data-toggle "tooltip"
                     :data-placement "top"
                     :data-container "body"
                     :title (str (:name board-data) " settings")
                     :on-click #(dis/dispatch! [:board-edit board-data])}])]
              ;; Add entry button
              (when (and (not is-all-posts)
                         (not (:read-only org-data))
                         (not (responsive/is-tablet-or-mobile?))
                         (or (utils/link-for (:links board-data) "create")
                             is-drafts-board))
                [:div.new-post-top-dropdown-container.group
                  [:button.mlb-reset.mlb-default.add-to-board-top-button.group
                    {:class (when @(::show-top-boards-dropdown s) "active")
                     :on-click (fn [_]
                                (if is-drafts-board
                                  (reset! (::show-top-boards-dropdown s) (not @(::show-top-boards-dropdown s)))
                                  (let [entry-data {:board-slug (:slug board-data)
                                                    :board-name (:name board-data)}
                                        topic-data (when (string? board-filters)
                                                     (first (filter #(= (:slug %) board-filters) topics)))
                                        with-topic (if (string? board-filters)
                                                    (merge
                                                     entry-data
                                                     {:topic-slug (:slug topic-data)
                                                      :topic-name (:name topic-data)})
                                                    entry-data)]
                                    (dis/dispatch! [:entry-edit with-topic]))))}
                    [:div.add-to-board-pencil]
                    [:label.add-to-board-label
                      "New Post"]]
                  (when @(::show-top-boards-dropdown s)
                    (dropdown-list
                     {:items (map
                              #(-> %
                                (select-keys [:name :slug])
                                (clojure.set/rename-keys {:name :label :slug :value}))
                              (vals all-boards))
                      :value ""
                      :on-blur #(reset! (::show-top-boards-dropdown s) false)
                      :on-change (fn [item]
                                   (reset! (::show-top-boards-dropdown s) false)
                                   (dis/dispatch! [:entry-edit {:board-slug (:value item)
                                                                :board-name (:label item)}]))}))])
              ;; Board filters when there is not topic filtering
              (when (and (not is-mobile-size?)
                         (not empty-board?)
                         (not is-all-posts)
                         (not is-drafts-board)
                         (or (string? board-filters) (> (count entry-topics) 1)))
                (filters-dropdown))]
            ;; Board content: empty board, add topic, topic view or topic cards
            (cond
              ;; No boards
              (zero? (count (:boards org-data)))
              (empty-org)
              ;; All Posts
              is-all-posts
              (rum/with-key
               (all-posts all-posts-data)
               (str "all-posts-" (clojure.string/join (keys (:fixed-items all-posts-data)))))
              ;; Empty board
              (and (not is-mobile-size?)
                   (not current-activity-id)
                   empty-board?)
              (empty-board)
              ;; Layout boards activities
              :else
              (cond
                ;; Drafts
                is-drafts-board
                (drafts-layout board-data)
                ;; Entries
                :else
                (entries-layout board-data board-filters)))
            ;; Add entry floating button
            (when (and (not is-all-posts)
                       (not (:read-only org-data))
                       (not (responsive/is-tablet-or-mobile?))
                       (or (utils/link-for (:links board-data) "create")
                           is-drafts-board))
              (let [opacity (if @(::show-floating-boards-dropdown s)
                              1
                              (calc-opacity (document-scroll-top)))]
                [:div.new-post-floating-dropdown-container.group
                  {:id "new-entry-floating-btn-container"
                   :style {:opacity opacity
                           :display (if (pos? opacity) "block" "none")}}
                  [:button.mlb-reset.mlb-default.add-to-board-floating-button
                    {:class (when @(::show-floating-boards-dropdown s) "active")
                     :data-placement "left"
                     :data-container "body"
                     :data-toggle "tooltip"
                     :title "Start a new post"
                     :on-click (fn [_]
                                (utils/remove-tooltips)
                                (if is-drafts-board
                                  (reset!
                                   (::show-floating-boards-dropdown s)
                                   (not @(::show-floating-boards-dropdown s)))
                                  (let [entry-data {:board-slug (:slug board-data)
                                                    :board-name (:name board-data)}
                                        topic-data (when (string? board-filters)
                                                     (first (filter #(= (:slug %) board-filters) topics)))
                                        with-topic (if (string? board-filters)
                                                    (merge
                                                     entry-data
                                                     {:topic-slug (:slug topic-data)
                                                      :topic-name (:name topic-data)})
                                                    entry-data)]
                                    (dis/dispatch! [:entry-edit with-topic]))))}
                    [:div.add-to-board-pencil]]
                  (when @(::show-floating-boards-dropdown s)
                    (dropdown-list
                     {:items (map
                              #(-> %
                                (select-keys [:name :slug])
                                (clojure.set/rename-keys {:name :label :slug :value}))
                              (vals all-boards))
                      :value ""
                      :on-blur #(reset! (::show-floating-boards-dropdown s) false)
                      :on-change (fn [item]
                                   (reset! (::show-floating-boards-dropdown s) false)
                                   (dis/dispatch! [:entry-edit {:board-slug (:value item)
                                                                :board-name (:label item)}]))}))]))]]]))