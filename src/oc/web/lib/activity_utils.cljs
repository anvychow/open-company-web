(ns oc.web.lib.activity-utils
  (:require [cuerdas.core :as s]
            [oc.web.router :as router]
            [oc.web.lib.utils :as utils]))

(defn get-first-body-thumbnail
  "Given an entry body get the first thumbnail available.
  Thumbnail type: image, video or chart."
  [activity-body]
  (let [is-ap (= (router/current-board-slug) "all-posts")
        $body (js/$ (str "<div>" activity-body "</div>"))
        thumb-els (js->clj (js/$ "img:not(.emojione), iframe" $body))
        found (atom nil)]
    (dotimes [el-num (.-length thumb-els)]
      (let [el (aget thumb-els el-num)
            $el (js/$ el)]
        (when-not @found
          (if (= (s/lower (.-tagName el)) "img")
            (let [width (.attr $el "width")
                  height (.attr $el "height")]
              (when (and (not @found)
                         (or (<= width (* height 2))
                             (<= height (* width 2))))
                (reset! found
                  {:type "image"
                   :thumbnail (if (and (not is-ap) (.data $el "thumbnail"))
                                (.data $el "thumbnail")
                                (.attr $el "src"))})))
            (reset! found {:type (.data $el "media-type") :thumbnail (.data $el "thumbnail")})))))
    @found))

(defn reset-truncate-body
  "Reset dotdotdot for the give body element."
  [body-el]
  (let [$body-els (js/$ ">*" body-el)]
    (.each $body-els (fn [idx el]
      (this-as this
        (.trigger (js/$ this) "destroy"))))))

(def default-body-height 72)
(def default-all-posts-body-height 144)

(defn truncate-body
  "Given a body element truncate the body. It iterate on the elements
  of the body and truncate the first exceeded element found.
  This is to avoid truncating a DIV with multiple spaced P inside,
  since this is a problem for the dotdotdot library that we are using."
  [body-el]
  (reset-truncate-body body-el)
  (let [is-ap (= (router/current-board-slug) "all-posts")
        $body-els (js/$ ">*" body-el)
        partial-heights (atom [])
        found (atom false)]
    (.each $body-els (fn [idx el]
     (when-not @found
       (this-as this
         (let [$this (js/$ this)
               el-h (.outerHeight $this true) ;; Include margins in height calculation
               container-max-height (if is-ap
                                     default-all-posts-body-height
                                     default-body-height)
               prev-height (apply + @partial-heights)
               actual-height (+ prev-height el-h)
               truncate-height  (cond
                                  (zero? (- container-max-height prev-height))
                                  0
                                  (<= (- container-max-height prev-height) 24)
                                  24
                                  (<= (- container-max-height prev-height) (* 24 2))
                                  (* 24 2)
                                  (<= (- container-max-height prev-height) (* 24 3))
                                  (* 24 3)
                                  (< (- container-max-height prev-height) (* 24 4))
                                  (* 24 4)
                                  (< (- container-max-height prev-height) (* 24 5))
                                  (* 24 5)
                                  (< (- container-max-height prev-height) (* 24 6))
                                  (* 24 6))]
           (swap! partial-heights #(vec (conj % el-h)))
           (when (>= actual-height container-max-height)
             (reset! found true)
             (.dotdotdot $this
               #js {:height truncate-height
                    :wrap "word"
                    :watch true
                    :ellipsis "..."})))))))))