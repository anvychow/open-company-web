(ns oc.web.components.ui.add-comment
  (:require [rum.core :as rum]
            [goog.events :as events]
            [dommy.core :refer-macros (sel1)]
            [goog.events.EventType :as EventType]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.utils.comment :as cu]
            [oc.web.lib.responsive :as responsive]
            [oc.web.utils.mention :as mention-utils]
            [oc.web.mixins.mention :as mention-mixins]
            [oc.web.actions.comment :as comment-actions]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.components.ui.alert-modal :as alert-modal]
            [oc.web.utils.medium-editor-media :as me-media-utils]
            [oc.web.actions.notifications :as notification-actions]
            [oc.web.components.ui.emoji-picker :refer (emoji-picker)]
            [oc.web.components.ui.giphy-picker :refer (giphy-picker)]
            [oc.web.components.ui.small-loading :refer (small-loading)]
            [oc.web.components.ui.media-video-modal :refer (media-video-modal)]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.carrot-checkbox :refer (carrot-checkbox)]))

;; Add commnet handling
(defn enable-add-comment? [s]
  (when-let [add-comment-div (rum/ref-node s "editor-node")]
    (let [{:keys [activity-data parent-comment-uuid edit-comment-data]} (first (:rum/args s))
          comment-text (.-innerHTML add-comment-div)
          next-add-bt-disabled (or (nil? comment-text) (not (seq comment-text)))]
      (comment-actions/add-comment-change activity-data parent-comment-uuid (:uuid edit-comment-data) comment-text)
      (when (not= next-add-bt-disabled @(::add-button-disabled s))
        (reset! (::add-button-disabled s) next-add-bt-disabled)))))

(defn focus-add-comment [s]
  (enable-add-comment? s)
  (let [{:keys [activity-data parent-comment-uuid]} (first (:rum/args s))]
    (if parent-comment-uuid
      (comment-actions/add-comment-focus parent-comment-uuid)
      (comment-actions/add-comment-focus (:uuid activity-data)))))

(defn disable-add-comment-if-needed [s]
  (when-let [add-comment-node (rum/ref-node s "editor-node")]
    (enable-add-comment? s)
    (when-not (seq (.-innerHTML add-comment-node))
      (comment-actions/add-comment-blur))))

(defn- send-clicked [event s]
  (reset! (::add-button-disabled s) true)
  (let [add-comment-div (rum/ref-node s "editor-node")
        comment-body (cu/add-comment-content add-comment-div true)
        {:keys [activity-data parent-comment-uuid dismiss-reply-cb
         edit-comment-data scroll-after-posting?]} (first (:rum/args s))
        save-done-cb (fn [success]
                      (if success
                        (when add-comment-div
                          (set! (.-innerHTML add-comment-div) ""))
                        (notification-actions/show-notification
                         {:title "An error occurred while saving your comment."
                          :description "Please try again"
                          :dismiss true
                          :expire 3
                          :id (if edit-comment-data :update-comment-error :add-comment-error)})))]
    (reset! (::add-button-disabled s) true)
    (set! (.-innerHTML add-comment-div) "")
    (if edit-comment-data
      (comment-actions/save-comment activity-data edit-comment-data comment-body save-done-cb)
      (comment-actions/add-comment activity-data comment-body parent-comment-uuid save-done-cb))
    (when (fn? dismiss-reply-cb)
      (dismiss-reply-cb false))
    (when (and (not edit-comment-data)
               (not dismiss-reply-cb)
               scroll-after-posting?)
      (.scrollIntoView (rum/dom-node s) (clj->js {:behavior "smooth"})))))

(defn me-options [parent-uuid]
  {:media-config ["gif" "photo" "video"]
   :comment-parent-uuid parent-uuid
   :placeholder (if parent-uuid "Reply…" "Add a comment…")
   :use-inline-media-picker true
   :media-picker-initially-visible false})

(defn add-comment-did-change [s]
  (reset! (::did-change s) true)
  (reset! (::show-post-button s) true)
  (enable-add-comment? s))

(defn- should-focus-field? [s]
  (let [{:keys [activity-data parent-comment-uuid
         parent-comment-uuid edit-comment-data]} (first (:rum/args s))
        add-comment-focus @(drv/get-ref s :add-comment-focus)]
    (or edit-comment-data
        (and (= (:uuid activity-data) add-comment-focus)
             (not parent-comment-uuid))
        (and (seq parent-comment-uuid)
             (= parent-comment-uuid add-comment-focus)))))

(rum/defcs add-comment < rum/reactive
                         ;; Locals
                         (rum/local nil :me/editor)
                         (rum/local nil :me/media-picker-ext)
                         (rum/local false :me/media-photo)
                         (rum/local false :me/media-video)
                         (rum/local false :me/media-attachment)
                         (rum/local false :me/media-photo-did-success)
                         (rum/local false :me/media-attachment-did-success)
                         (rum/local false :me/showing-media-video-modal)
                         (rum/local false :me/showing-gif-selector)
                         ;; Image upload lock
                         (rum/local false :me/upload-lock)
                         (rum/local "" ::add-comment-id)

                         ;; Derivatives
                         (drv/drv :media-input)
                         (drv/drv :add-comment-focus)
                         (drv/drv :add-comment-data)
                         (drv/drv :team-roster)
                         (drv/drv :current-user-data)
                         (drv/drv :attachment-uploading)
                         ;; Locals
                         (rum/local true ::add-button-disabled)
                         (rum/local "" ::initial-add-comment)
                         (rum/local false ::did-change)
                         (rum/local false ::show-post-button)
                         ;; Mixins
                         ui-mixins/first-render-mixin
                         (mention-mixins/oc-mentions-hover)

                         (ui-mixins/on-window-click-mixin (fn [s e]
                          (when (and @(:me/showing-media-video-modal s)
                                     (not (.contains (.-classList (.-target e)) "media-video"))
                                     (not (utils/event-inside? e (rum/ref-node s :video-container))))
                            (me-media-utils/media-video-add s @(:me/media-picker-ext s) nil)
                            (reset! (:me/showing-media-video-modal s) false))
                          (when (and @(:me/showing-gif-selector s)
                                     (not (.contains (.-classList (.-target e)) "media-gif"))
                                     (not (utils/event-inside? e (sel1 [:div.giphy-picker]))))
                            (me-media-utils/media-gif-add s @(:me/media-picker-ext s) nil)
                            (reset! (:me/showing-gif-selector s) false))))
                         {:will-mount (fn [s]
                          (reset! (::add-comment-id s) (utils/activity-uuid))
                          (let [{:keys [activity-data parent-comment-uuid edit-comment-data]} (first (:rum/args s))
                                add-comment-data @(drv/get-ref s :add-comment-data)
                                add-comment-key (str (:uuid activity-data) "-" parent-comment-uuid "-" (:uuid edit-comment-data))
                                activity-add-comment-data (get add-comment-data add-comment-key)
                                add-comment-activity-data (get add-comment-data (:uuid activity-data))]
                            (reset! (::initial-add-comment s) (or activity-add-comment-data ""))
                            (reset! (::show-post-button s) (should-focus-field? s)))
                          s)
                          :did-mount (fn [s]
                           (me-media-utils/setup-editor s add-comment-did-change (me-options (:parent-comment-uuid (first (:rum/args s)))))
                           (let [add-comment-node (rum/ref-node s "editor-node")]
                             (when (should-focus-field? s)
                               (.focus add-comment-node)
                               (utils/after 0
                                #(utils/to-end-of-content-editable add-comment-node))))
                           (utils/after 2500 #(js/emojiAutocomplete))
                           s)
                          :will-update (fn [s]
                           (me-media-utils/setup-editor s add-comment-did-change (me-options (:parent-comment-uuid (first (:rum/args s)))))
                           (let [data @(drv/get-ref s :media-input)
                                 video-data (:media-video data)]
                              (when (and @(:me/media-video s)
                                         (or (= video-data :dismiss)
                                             (map? video-data)))
                                (when (or (= video-data :dismiss)
                                          (map? video-data))
                                  (reset! (:me/media-video s) false)
                                  (dis/dispatch! [:update [:media-input] #(dissoc % :media-video)]))
                                (if (map? video-data)
                                  (me-media-utils/media-video-add s @(:me/media-picker-ext s) video-data)
                                  (me-media-utils/media-video-add s @(:me/media-picker-ext s) nil))))
                           s)
                          :will-unmount (fn [s]
                           (when @(:me/editor s)
                             (.destroy @(:me/editor s))
                             (reset! (:me/editor s) nil))
                           s)}
  [s {:keys [activity-data parent-comment-uuid dismiss-reply-cb edit-comment-data scroll-after-posting?]}]
  (let [_add-comment-data (drv/react s :add-comment-data)
        _media-input (drv/react s :media-input)
        _team-roster (drv/react s :team-roster)
        add-comment-focus (drv/react s :add-comment-focus)
        current-user-data (drv/react s :current-user-data)
        container-class (str "add-comment-box-container-" @(::add-comment-id s))
        is-focused? (should-focus-field? s)
        should-hide-post-button (and ;; Hide post button only for the last add comment field, not
                                     ;; for the reply to comments
                                     (not parent-comment-uuid)
                                     (not @(::show-post-button s))
                                     (not is-focused?))
        is-mobile? (responsive/is-mobile-size?)
        attachment-uploading (drv/react s :attachment-uploading)
        uploading? (and attachment-uploading
                        (= (:comment-parent-uuid attachment-uploading) parent-comment-uuid))
        add-comment-class (str "add-comment-" @(::add-comment-id s))]
    [:div.add-comment-box-container
      {:class container-class}
      [:div.add-comment-box
        [:div.add-comment-internal
          {:class (when-not should-hide-post-button "active")}
          [:div.add-comment.emoji-autocomplete.emojiable.oc-mentions.oc-mentions-hover.editing
           {:ref "editor-node"
            :class (utils/class-set {add-comment-class true
                                     :medium-editor-placeholder-hidden @(::did-change s)
                                     utils/hide-class true})
            :on-focus #(focus-add-comment s)
            :on-blur #(disable-add-comment-if-needed s)
            :on-key-down (fn [e]
                          (let [add-comment-node (rum/ref-node s "editor-node")]
                            (when (and (= (.-key e) "Escape")
                                       (= (.-activeElement js/document) add-comment-node))
                              (if edit-comment-data
                                (when (fn? dismiss-reply-cb)
                                  (dismiss-reply-cb true))
                                (.blur add-comment-node)))
                            (when (and (= (.-activeElement js/document) add-comment-node)
                                       (.-metaKey e)
                                       (= (.-key e) "Enter"))
                              (send-clicked e s))))
            :content-editable true
            :dangerouslySetInnerHTML #js {"__html" @(::initial-add-comment s)}}]
          [:div.add-comment-footer.group
            (when (fn? dismiss-reply-cb)
              [:button.mlb-reset.close-reply-bt
                {:on-click (fn [_]
                            (if @(::did-change s)
                              (let [alert-data {:icon "/img/ML/trash.svg"
                                                :action "cancel-comment-edit"
                                                :message "Are you sure you want to cancel? All your changes to this comment will be lost."
                                                :link-button-title "Keep"
                                                :link-button-cb #(alert-modal/hide-alert)
                                                :solid-button-style :red
                                                :solid-button-title "Yes"
                                                :solid-button-cb (fn []
                                                                  (dismiss-reply-cb true)
                                                                  (alert-modal/hide-alert))}]
                                (alert-modal/show-alert alert-data))
                              (dismiss-reply-cb true)))
                 :data-toggle (if (responsive/is-tablet-or-mobile?) "" "tooltip")
                 :data-placement "top"
                 :data-container "body"
                 :title (if edit-comment-data "Cancel edit" "Close")}])
            [:button.mlb-reset.send-btn
              {:on-click #(when-not @(::add-button-disabled s)
                            (send-clicked % s))
               :disabled @(::add-button-disabled s)
               :class (when uploading? "separator-line")}
              (if edit-comment-data
                "Save"
                (if dismiss-reply-cb
                  "Reply"
                  "Comment"))]
            (when uploading?
              [:div.upload-progress
                (small-loading)
                [:span.attachment-uploading
                  (str "Uploading " (or (:progress attachment-uploading) 0) "%...")]])]]
        (when @(:me/showing-media-video-modal s)
          [:div.video-container
            {:ref :video-container}
            (media-video-modal {:fullscreen false
                                :dismiss-cb #(do
                                              (me-media-utils/media-video-add s @(:me/media-picker-ext s) nil)
                                              (reset! (:me/showing-media-video-modal s) false))
                                :offset-element-selector [(keyword (str "div." container-class))]
                                :outer-container-selector [(keyword (str "div." container-class))]})])
        (when @(:me/showing-gif-selector s)
          (giphy-picker {:fullscreen false
                         :pick-emoji-cb (fn [gif-obj]
                                         (reset! (:me/showing-gif-selector s) false)
                                         (me-media-utils/media-gif-add s @(:me/media-picker-ext s) gif-obj))
                         :offset-element-selector [(keyword (str "div." container-class))]
                         :outer-container-selector [(keyword (str "div." container-class))]}))]]))
