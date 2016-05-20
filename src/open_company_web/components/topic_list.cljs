(ns open-company-web.components.topic-list
  (:require-macros [cljs.core.async.macros :refer (go)])
  (:require [cljs.core.async :refer (chan <!)]
            [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1)]
            [open-company-web.api :as api]
            [open-company-web.caches :as caches]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.lib.oc-colors :as oc-colors]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.topic :refer (topic)]
            [open-company-web.components.fullscreen-topic :refer (fullscreen-topic)]
            [open-company-web.components.su-preview :refer (su-preview)]
            [open-company-web.components.ui.icon :refer (icon)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.fx.Animation.EventType :as AnimationEventType]
            [goog.fx.dom :refer (Fade)]
            [cljsjs.hammer]))

(defn get-new-sections-if-needed [owner]
  (when-not (om/get-state owner :new-sections-requested)
    (let [slug (keyword (router/current-company-slug))
          company-data (dispatcher/company-data)]
      (when (and (empty? (slug @caches/new-sections))
                 (seq company-data))
        (om/update-state! owner :new-sections-requested not)
        (api/get-new-sections)))))

(defn get-active-topics [company-data category]
  (get-in company-data [:sections (keyword category)]))

(defn remove-topic [owner topic]
  (let [company-data (om/get-props owner :company-data)
        old-categories (:sections company-data)
        new-categories (apply merge (map #(hash-map (first %) (utils/vec-dissoc (second %) topic)) old-categories))]
    (api/patch-sections new-categories)))

(defn update-active-topics [owner category-name new-topic]
  (let [company-data (om/get-props owner :company-data)
        old-categories (:sections company-data)
        old-topics (get-active-topics company-data category-name)
        new-topics (concat old-topics [new-topic])
        new-categories (assoc old-categories (keyword category-name) new-topics)]
    (api/patch-sections new-categories)))

(defn get-state [data current-state]
  (let [company-data (:company-data data)
        categories (:categories company-data)
        active-topics (apply merge (map #(hash-map (keyword %) (get-active-topics company-data %)) categories))]
    {:initial-active-topics active-topics
     :active-topics active-topics
     :new-sections-requested (or (:new-sections-requested current-state) false)
     :selected-topic (or (:selected-topic current-state) (:selected-topic data))
     :tr-selected-topic nil
     :sharing-mode false
     :topic-navigation true
     :show-su-preview false
     :share-selected-topics (:sections (:stakeholder-update company-data))
     :transitioning false}))

(defn topic-click [owner topic selected-metric]
  (if (om/get-state owner :sharing-mode)
    (let [share-selected-topics (om/get-state owner :share-selected-topics)
          new-share-selected-topics (if (utils/in? share-selected-topics (name topic))
                                      (utils/vec-dissoc share-selected-topics (name topic))
                                      (vec (concat share-selected-topics [(name topic)])))]
      (om/set-state! owner :share-selected-topics new-share-selected-topics))
    (do
      (om/set-state! owner :selected-topic topic)
      (om/set-state! owner :selected-metric selected-metric))))

(def scrolled-to-top (atom false))

(defn close-overlay-cb [owner]
  (om/set-state! owner :transitioning false)
  (om/set-state! owner :selected-topic nil)
  (om/set-state! owner :selected-metric nil))

(defn sections-for-category [slug active-category]
  (let [category-data (first (filter #(= (:name %) (name active-category)) (:categories (slug @caches/new-sections))))
        all-category-sections (:sections category-data)]
    (apply merge
           (map #(hash-map (keyword (:section-name %)) %) all-category-sections))))

(defn render-topic [owner section-name company-data active-category & [column]]
  (when section-name
    (if (= section-name "add-topic")
      (om/build topic {:loading false
                       :section "add-topic"
                       :add-topic true
                       :column column
                       :section-data {:title "+ ADD A TOPIC"
                                      :body ""
                                      :updated-at 0
                                      :headline ""}
                        :currency (:currency company-data)
                        :active-topics (om/get-state owner :active-topics)
                        :active-category active-category}
                       {:opts {:section-name section-name
                               :topic-click (partial topic-click owner section-name)
                               :update-active-topics (partial update-active-topics owner)}})
      (let [sharing-mode (om/get-state owner :sharing-mode)
            share-selected-topics (om/get-state owner :share-selected-topics)
            sd (->> section-name keyword (get company-data))]
        (when-not (and (:read-only company-data) (:placeholder sd))
          (dom/div #js {:className "topic-row"
                       :ref section-name
                       :key (str "topic-row-" (name section-name))}
            (om/build topic {:loading (:loading company-data)
                             :section section-name
                             :section-data sd
                             :currency (:currency company-data)
                             :active-category active-category
                             :sharing-mode sharing-mode
                             :share-selected (utils/in? share-selected-topics section-name)}
                             {:opts {:section-name section-name
                                     :topic-click (partial topic-click owner section-name)}})))))))

(defn switch-topic [owner is-left?]
  (when (and (om/get-state owner :topic-navigation)
             (om/get-state owner :selected-topic)
             (nil? (om/get-state owner :tr-selected-topic)))
    (let [selected-topic (om/get-state owner :selected-topic)
          active-topics (om/get-state owner :active-topics)
          topics-list (flatten (vals active-topics))
          current-idx (.indexOf (vec topics-list) selected-topic)]
      (if is-left?
        ;prev
        (let [prev-idx (mod (dec current-idx) (count topics-list))
              prev-topic (get (vec topics-list) prev-idx)]
          (om/set-state! owner :tr-selected-topic prev-topic))
        ;next
        (let [next-idx (mod (inc current-idx) (count topics-list))
              next-topic (get (vec topics-list) next-idx)]
          (om/set-state! owner :tr-selected-topic next-topic))))))

(defn kb-listener [owner e]
  (let [key-code (.-keyCode e)]
    (when (= key-code 39)
      ;next
      (switch-topic owner false))
    (when (= key-code 37)
      (switch-topic owner true))))

(defn animation-finished [owner]
  (let [cur-state (om/get-state owner)]
    (om/set-state! owner (merge cur-state {:selected-topic (:tr-selected-topic cur-state)
                                           :transitioning true
                                           :tr-selected-topic nil}))))

(defn animate-selected-topic-transition [owner]
  (let [selected-topic (om/get-ref owner "selected-topic")
        tr-selected-topic (om/get-ref owner "tr-selected-topic")
        fade-anim (new Fade selected-topic 1 0 utils/oc-animation-duration)
        cur-state (om/get-state owner)]
    (.play (new Fade tr-selected-topic 0 1 utils/oc-animation-duration))
    (doto fade-anim
      (.listen AnimationEventType/FINISH #(animation-finished owner))
      (.play))))

(defn toggle-sharing-mode [owner options]
  (om/update-state! owner :sharing-mode not)
  ((:toggle-sharing-mode options)))

(defcomponent topic-list [data owner options]

  (init-state [_]
    (utils/add-channel "fullscreen-topic-save" (chan))
    (utils/add-channel "fullscreen-topic-cancel" (chan))
    (get-state data nil))

  (did-mount [_]
    (when-not (:read-only (:company-data data))
      (get-new-sections-if-needed owner))
    ; scroll to top when the component is initially mounted to
    ; make sure the calculation for the fixed navbar are correct
    (when-not @scrolled-to-top
      (set! (.-scrollTop (.-body js/document)) 0)
      (reset! scrolled-to-top true))
    (when-not (utils/is-test-env?)
      (let [kb-listener (events/listen js/window EventType/KEYDOWN (partial kb-listener owner))
            swipe-listener (js/Hammer (sel1 [:div#app]))];(.-body js/document))]
        (om/set-state! owner :kb-listener kb-listener)
        (om/set-state! owner :swipe-listener swipe-listener)
        (.on swipe-listener "swipeleft" (fn [e] (switch-topic owner true)))
        (.on swipe-listener "swiperight" (fn [e] (switch-topic owner false))))))

  (will-unmount [_]
    (utils/remove-channel "fullscreen-topic-save")
    (utils/remove-channel "fullscreen-topic-cancel")
    (when-not (utils/is-test-env?)
      (events/unlistenByKey (om/get-state owner :kb-listener))
      (let [swipe-listener (om/get-state owner :swipe-listener)]
        (.off swipe-listener "swipeleft")
        (.off swipe-listener "swiperight"))))

  (will-receive-props [_ next-props]
    (when-not (= (:company-data next-props) (:company-data data))
      (om/set-state! owner (get-state next-props (om/get-state owner))))
    (when-not (:read-only (:company-data next-props))
      (get-new-sections-if-needed owner)))

  (did-update [_ _ _]
    (when (om/get-state owner :tr-selected-topic)
      (animate-selected-topic-transition owner)))

  (render-state [_ {:keys [active-topics selected-topic selected-metric tr-selected-topic transitioning sharing-mode share-selected-topics show-su-preview]}]
    (let [slug            (keyword (router/current-company-slug))
          company-data    (:company-data data)
          active-category (keyword (:active-category data))
          category-topics (flatten (vals active-topics))
          win-width       (.-clientWidth (sel1 js/document :body))
          card-width      (:card-width data)
          columns-num     (:columns-num data)
          ww              (.-clientWidth (sel1 js/document :body))
          add-first-column? (= (count category-topics) 0)
          add-second-column? (= (count category-topics) 1)
          add-third-column? (>= (count category-topics) 2)
          add-topic?      (and (responsive/can-edit?)
                               (not sharing-mode)
                               (not (:read-only company-data)))
          internal-width (case columns-num
                            3 (str (+ (* card-width 3) 40 60) "px")
                            2 (str (+ (* card-width 2) 20 60) "px")
                            1 (if (> ww 413) (str card-width "px") "auto"))]
      (dom/div {:class (str "topic-list group" (when-not sharing-mode " no-sharing"))
                :key "topic-list"}
        (when show-su-preview
          (om/build su-preview {:selected-topics share-selected-topics
                                :company-data company-data}
                               {:opts {:dismiss-su-preview #(om/set-state! owner :show-su-preview false)}}))
        (when sharing-mode
          (dom/div {:class "sharing-header"}
            (dom/div {:class "sharing-header-inner group"
                      :style #js {:width internal-width}}
              (dom/div {:class "sharing-header-left"}
                (dom/label {:class "selected-topics"}
                  (if (zero? (count share-selected-topics))
                    "NO TOPICS SELECTED"
                    (str (count share-selected-topics) " TOPIC" (when (> (count share-selected-topics) 1) "S") " SELECTED"))))
              (dom/div {:class "sharing-header-center"}
                (when (pos? (count share-selected-topics))
                  (dom/button {:class "share-snapshot-bt"
                               :on-click #(om/set-state! owner :show-su-preview true)}
                    "PREVIEW UPDATE")))
              (dom/div {:class "sharing-header-right"}
                (dom/button {:class "close-share"
                             :on-click #(toggle-sharing-mode owner options)}
                  (icon :simple-remove {:stroke "4" :accent-color "white"}))))))
        (when (and (not (responsive/is-mobile))
                   (not (:read-only company-data))
                   (not sharing-mode))
          (dom/div {:class "sharing-button-container"
                    :style #js {:width internal-width}}
            (dom/button {:class "sharing-button"
                         :on-click #(toggle-sharing-mode owner options)} "SHARE A SNAPSHOT")))
        (when selected-topic
          (dom/div {:class "selected-topic-container"
                    :style #js {:opacity (if selected-topic 1 0)}}
            (when selected-topic
              (dom/div #js {:className "selected-topic"
                            :key (str "transition-" selected-topic)
                            :ref "selected-topic"
                            :style #js {:opacity 1 :backgroundColor "rgba(255, 255, 255, 0.98)"}}
                (om/build fullscreen-topic {:section selected-topic
                                            :section-data (->> selected-topic keyword (get company-data))
                                            :selected-metric selected-metric
                                            :read-only (:read-only company-data)
                                            :card-width card-width
                                            :currency (:currency company-data)
                                            :animate (not transitioning)}
                                           {:opts {:close-overlay-cb #(close-overlay-cb owner)
                                                   :topic-edit-cb (:topic-edit-cb options)
                                                   :remove-topic (partial remove-topic owner)
                                                   :toggle-topic-navigation #(om/set-state! owner :topic-navigation %)}})))
            (when tr-selected-topic
              (dom/div #js {:className "tr-selected-topic"
                            :key (str "transition-" tr-selected-topic)
                            :ref "tr-selected-topic"
                            :style #js {:opacity (if tr-selected-topic 0 1)}}
              (om/build fullscreen-topic {:section tr-selected-topic
                                          :section-data (->> tr-selected-topic keyword (get company-data))
                                          :selected-metric selected-metric
                                          :read-only (:read-only company-data)
                                          :card-width card-width
                                          :currency (:currency company-data)
                                          :animate false}
                                         {:opts {:close-overlay-cb #(close-overlay-cb owner)
                                                 :topic-edit-cb (:topic-edit-cb options)
                                                 :remove-topic (partial remove-topic owner)
                                                 :toggle-topic-navigation #(om/set-state! owner :topic-navigation %)}})))))
        ;; Topic list
        (dom/div {:class (utils/class-set {:topic-list-internal true
                                           :sharing-mode sharing-mode
                                           :group true
                                           :content-loaded (not (:loading data))})}
          (case columns-num
            3
            (dom/div {:class "topics-column-container group"
                      :style #js {:width internal-width}}
              (dom/div {:class "topics-column"
                        :style #js {:width (str card-width "px")}}
                (for [idx (range (inc (quot (count category-topics) 3)))
                  :while (< idx (inc (quot (count category-topics) 2)))
                  :let [real-idx (* idx 3)
                        section-name (get (vec category-topics) real-idx)]]
                  (render-topic owner section-name company-data active-category))
                (when (and add-first-column? add-topic?)
                  (render-topic owner "add-topic" company-data active-category 1)))
              (dom/div {:class "topics-column"
                        :style #js {:width (str card-width "px")}}
                (for [idx (range (inc (quot (count category-topics) 3)))
                  :while (< idx (inc (quot (count category-topics) 2)))
                  :let [real-idx (inc (* idx 3))
                        section-name (get (vec category-topics) real-idx)]]
                  (render-topic owner section-name company-data active-category))
                (when (and add-second-column? add-topic?)
                  (render-topic owner "add-topic" company-data active-category 2)))
              (dom/div {:class "topics-column"
                        :style #js {:width (str card-width "px")}}
                (for [idx (range (inc (quot (count category-topics) 3)))
                  :while (< idx (inc (quot (count category-topics) 2)))
                  :let [real-idx (+ (* idx 3) 2)
                        section-name (get (vec category-topics) real-idx)]]
                  (render-topic owner section-name company-data active-category))
                (when (and add-third-column? add-topic?)
                  (render-topic owner "add-topic" company-data active-category 3))))
            2
            (dom/div {:class "topics-column-container columns-2 group"
                      :style #js {:width internal-width}}
              (dom/div {:class "topics-column"
                        :style #js {:width (str card-width "px")}}
                (for [idx (range (inc (quot (count category-topics) 2)))
                  :while (< idx (inc (quot (count category-topics) 2)))
                  :let [real-idx (* idx 2)
                        section-name (get (vec category-topics) real-idx)]]
                  (render-topic owner section-name company-data active-category))
                (when (and add-first-column? add-topic?)
                  (render-topic owner "add-topic" company-data active-category 1)))
              (dom/div {:class "topics-column"
                        :style #js {:width (str card-width "px")}}
                (for [idx (range (inc (quot (count category-topics) 2)))
                  :while (< idx (inc (quot (count category-topics) 2)))
                  :let [real-idx (inc (* idx 2))
                        section-name (get (vec category-topics) real-idx)]]
                  (render-topic owner section-name company-data active-category))
                (when (and (not add-first-column?)
                           add-topic?)
                  (render-topic owner "add-topic" company-data active-category 2))))
            ; 1 column or default
            (dom/div {:class "topics-column-container columns-1 group"
                      :style #js {:width internal-width}}
              (dom/div {:class "topics-column"}
                (for [section-name category-topics]
                  (render-topic owner section-name company-data active-category))
                (when add-topic?
                  (render-topic owner "add-topic" company-data active-category 1))))))))))