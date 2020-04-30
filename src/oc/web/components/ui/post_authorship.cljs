(ns oc.web.components.ui.post-authorship
  (:require [rum.core :as rum]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]
            [oc.web.components.ui.user-avatar :refer (user-avatar-image)]
            [oc.web.components.ui.info-hover-views :refer (user-info-hover board-info-hover)]))

(rum/defc post-authorship < rum/static
  [{{:keys [publisher board-name board-slug board-access publisher-board] :as activity-data} :activity-data
    user-avatar? :user-avatar? user-hover? :user-hover? board-hover? :board-hover?
    current-user-id :current-user-id}]
  [:div.post-authorship
    [:div.user-hover-container
      (when user-hover?
        (user-info-hover {:user-data publisher :current-user-id current-user-id}))
      (when user-avatar?
        (user-avatar-image publisher))
      [:a.publisher-name
        {:class utils/hide-class
         :href (oc-urls/contributions (:user-id publisher))
         :on-click #(do
                      (utils/event-stop %)
                      (router/nav! (oc-urls/contributions (:user-id publisher))))}
        (:name publisher)]]
    (when-not publisher-board
      [:span.in "in "])
    (when-not publisher-board
      [:div.board-hover-container
        (when board-hover?
          (board-info-hover {:activity-data activity-data}))
        [:a.board-name
          {:class utils/hide-class
           :href (oc-urls/board board-slug)
           :on-click #(do
                        (utils/event-stop %)
                        (router/nav! (oc-urls/board board-slug)))}
          (str board-name
               (when (= board-access "private")
                 " (private)")
               (when (= board-access "public")
                 " (public)"))]])])