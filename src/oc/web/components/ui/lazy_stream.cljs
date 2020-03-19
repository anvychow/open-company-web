(ns oc.web.components.ui.lazy-stream
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]))

(rum/defcs lazy-stream < rum/static
                         rum/reactive
                         (drv/drv :board-data)
                         (drv/drv :contributor-data)
                         (drv/drv :container-data)
                         (drv/drv :activity-data)
                         (drv/drv :foc-layout)
                         {:did-mount (fn [s]
                           (utils/scroll-to-y (:scroll-y @router/path) 0)
                           s)}
  [s stream-comp]
  (let [board-data (drv/react s :board-data)
        contributor-data (drv/react s :contributor-data)
        container-data (drv/react s :container-data)
        activity-data (drv/react s :activity-data)
        foc-layout (drv/react s :foc-layout)
        is-container? (dis/is-container? (router/current-board-slug))
        is-contributor? (dis/is-contributor? (router/current-contributor-id))
        loading? (or ;; Board specified
                     (and (not (router/current-activity-id))
                          (not is-container?)
                          (not is-contributor?)
                          ;; But no board data yet
                          (not board-data))
                     ;; Contrib specified
                     (and (not (router/current-contributor-id))
                          (not is-contributor?)
                          ;; But no board data yet
                          (not contributor-data))
                     ;; Another container
                     (and (not (router/current-activity-id))
                          is-container?
                          ;; But no all-posts data yet
                         (not container-data))
                     ;; Activity loaded
                     (and (router/current-activity-id)
                          (not activity-data)))]
    [:div.lazy-stream
      (if-not loading?
        (stream-comp)
        [:div.lazy-stream-interstitial
          {:class (when (= foc-layout dis/other-foc-layout) "collapsed")
           :style {:height (str (+ (:scroll-y @router/path)
                                   (or (.. js/document -documentElement -clientHeight)
                                       (.-innerHeight js/window)))
                             "px")}}])]))
