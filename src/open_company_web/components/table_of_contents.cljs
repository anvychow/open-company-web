(ns open-company-web.components.table-of-contents
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.router :as router]
            [open-company-web.lib.utils :as utils]
            [open-company-web.api :as api]))

(def first-cat-placeholder "first-category")



(defn setup-plus-position [e]
  (let [target (.$ js/window (.-target e))
        offset (.position target)
        t-top (- (.-top offset) 5)
        t-left (+ (.-left offset) 208)]
    (-> (.$ js/window ".add-section")
        (.css #js {"left" t-left "top" t-top}))))

(defn show-popover [e owner]
  (when (.-$ js/window) ; avoid tests crash
    (om/update-state! owner :hover-new-section (fn [_]false))
    (om/update-state! owner :hover-add-section (fn [_]false))
    (let [section (or (om/get-state owner :hover-new-section)
                      (om/get-state owner :hover-add-section))
          plus-offset (.position (.$ js/window ".add-section"))
          popover (.$ js/window ".new-section-popover-container")
          window-scrolltop (.scrollTop (.$ js/window js/window))]
      (.click popover (fn [e]
                        (.fadeOut popover 400 #(.css popover #js {"display" "none"}))))
      (.css popover #js {"top" (str (+ (.-top plus-offset) 40 window-scrolltop) "px")
                         "left" (str (+ (.-left plus-offset) 100) "px")})
      (.setTimeout js/window #(.fadeIn popover 400) 0))))

(defn setup-hover-events [owner]
  (when (.-$ js/window) ; avoid tests crash
    (-> (.$ js/window ".new-section")
        (.hover (fn [e]
                  (setup-plus-position e)
                  (om/update-state! owner :hover-new-section (fn [_](.-id (.-target e)))))
                (fn [e]
                  (.setTimeout js/window
                    #(om/update-state! owner :hover-new-section (fn [_]false))
                    1))))
    (-> (.$ js/window ".add-section")
        (.hover (fn [e]
                  (om/update-state! owner :hover-add-section (fn [_](om/get-state owner :hover-new-section))))
                (fn [e]
                  (om/update-state! owner :hover-add-section (fn [_]false)))))))

(defn add-popover-container []
  (when (.-$ js/window) ; avoid tests crash
    (let [popover (.$ js/window "<div class='new-section-popover-container'></div>")
          body (.$ js/window (.-body js/document))]
      (.append body popover))))

(defcomponent table-of-contents [data owner]
  (init-state [_]
    {:hover-add-section false
     :hover-new-section false})
  (did-mount [_]
    (setup-hover-events owner)
    (add-popover-container))
  (did-update [_ _ _]
    (setup-hover-events owner))
  (render [_]
    (let [sections (:sections data)
          categories (:categories data)]
      (dom/div #js {:className "table-of-contents" :ref "table-of-contents"}
        (dom/div {:class (utils/class-set {:add-section true
                                           :show (or (om/get-state owner :hover-add-section)
                                                      (om/get-state owner :hover-new-section))})
                  :on-click #(show-popover % owner)}
          (dom/i {:class "fa fa-plus"}))
        (dom/div {:class "table-of-contents-inner"}
          (for [category categories]
            (dom/div {:class "category-container"}
              (dom/div {:class (utils/class-set {:category true :empty (zero? (count ((keyword category) sections)))})} (dom/h3 (utils/camel-case-str (name category))))
              (dom/div {:id (str "new-section-" first-cat-placeholder)
                        :class (utils/class-set {:new-section true
                                                 :hover (or (= (om/get-state owner :hover-new-section) (str "new-section-" first-cat-placeholder))
                                                            (= (om/get-state owner :hover-add-section) (str "new-section-" first-cat-placeholder)))})
                        :on-click #(show-popover % owner)}
                (dom/div {:class "new-section-internal"}))
              (for [section (into [] (get sections (keyword category)))]
                (let [section-data ((keyword section) data)]
                  (dom/div {}
                    (dom/div {:class "category-section"}
                      (dom/div {:class "category-section-close"
                                :on-click #(api/remove-section (name section))})
                      (dom/a {:href "#"
                              :on-click (fn [e]
                                          (.preventDefault e)
                                          (utils/scroll-to-section (name section)))}
                        (dom/p {:class "section-title"} (:title section-data))
                        (dom/p {:class "section-date"} (utils/time-since (:updated-at section-data)))))
                    (dom/div {:id (str "new-section-" (name section))
                              :class (utils/class-set {:new-section true
                                                       :hover (or (= (om/get-state owner :hover-new-section) (str "new-section-" (name section)))
                                                                  (= (om/get-state owner :hover-add-section) (str "new-section-" (name section))))})
                              :on-click #(show-popover % owner)}
                      (dom/div {:class "new-section-internal"}))))))))))))
