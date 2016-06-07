(ns open-company-web.components.ui.small-loading
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]))

(defcomponent small-loading [{:keys [animating]} owner]
  (render [_]
    (dom/div {:class (str "small-loading-container" (when-not animating " hidden"))}
      (dom/img {:class "small-loading" :src "/img/small_loading.gif"}))))