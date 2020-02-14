(ns oc.web.utils.activity
  (:require [cuerdas.core :as s]
            [cljs-time.format :as time-format]
            [oc.lib.html :as html]
            [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.lib.ziggeo :as ziggeo]
            [oc.web.lib.responsive :as responsive]
            [oc.web.utils.comment :as comment-utils]))

;; Posts separators

(defn- post-month-date-from-date [post-date]
  (doto post-date
    ;; Reset day to first of the month
    (.setDate 1)
    ;; Reset time to midnight
    (.setHours 0)
    (.setMinutes 0)
    (.setSeconds 0)
    (.setMilliseconds 0)))

(defn- separator-from-date [d last-monday two-weeks-ago first-month]
  (let [now (utils/js-date)
        month-string (utils/full-month-string (inc (.getMonth d)))]
    (cond
      (> d last-monday)
      {:label "Recent"
       :content-type "separator"
       :date last-monday}
      (> d two-weeks-ago)
      {:label "Last week"
       :content-type "separator"
       :date two-weeks-ago}
      (> d first-month)
      {:label "2 weeks ago"
       :content-type "separator"
       :date first-month}
      (and (= (.getMonth now) (.getMonth d))
           (= (.getFullYear now) (.getFullYear d)))
      {:label "This month"
       :content-type "separator"
       :date (post-month-date-from-date d)}
      (= (.getFullYear now) (.getFullYear d))
      {:label month-string
       :content-type "separator"
       :date (post-month-date-from-date d)}
      :else
      {:label (str month-string ", " (.getFullYear d))
       :content-type "separator"
       :date (post-month-date-from-date d)})))

(defn- add-post-to-separators [post-data separators-map last-monday two-weeks-ago first-month]
  (let [post-date (utils/js-date (:published-at post-data))]
    (if (and (not (empty? separators-map))
             (> post-date (:date (last separators-map))))
      (update-in separators-map [(dec (count separators-map)) :posts-list] #(-> % (conj (:uuid post-data)) vec))
      (vec
       (conj separators-map
        (assoc (separator-from-date post-date last-monday two-weeks-ago first-month)
         :posts-list [(:uuid post-data)]))))))

(defn grouped-posts [sorted-posts-list]
  (let [now (utils/js-date)

        last-monday (utils/js-date)
        _last-monday (doto last-monday
                       (.setDate (- (.getDate last-monday)
                                    ; First monday before now
                                    (-> (.getDay last-monday) (+ 6) (mod 7))))
                       (.setHours 0)
                       (.setMinutes 0)
                       (.setSeconds 0)
                       (.setMilliseconds 0))

        two-weeks-ago (utils/js-date)
        _two-weeks-ago (doto two-weeks-ago
                         (.setDate (- (.getDate two-weeks-ago)
                                      ;; Monday before last
                                      (-> (.getDay two-weeks-ago) (+ 6) (mod 7) (+ 7))))
                         ;; Reset time to midnight
                         (.setHours 0)
                         (.setMinutes 0)
                         (.setSeconds 0)
                         (.setMilliseconds 0))

        first-month (utils/js-date)
        _first-month (doto first-month
                       (.setDate (- (.getDate first-month)
                                    (-> (.getDay first-month) (+ 6) (mod 7) (+ 14))))
                       ;; Reset time to midnight
                       (.setHours 0)
                       (.setMinutes 0)
                       (.setSeconds 0)
                       (.setMilliseconds 0))

        last-date (:published-at (last sorted-posts-list))]

    (loop [separators []
           posts sorted-posts-list]
      (if (empty? posts)
        separators
        (recur (add-post-to-separators (first posts) separators last-monday two-weeks-ago first-month)
               (rest posts))))))

;; 

(defn is-published? [entry-data]
  (= (:status entry-data) "published"))

(defn board-by-uuid [board-uuid]
  (let [org-data (dis/org-data)
        boards (:boards org-data)]
    (first (filter #(= (:uuid %) board-uuid) boards))))

(defn reset-truncate-body
  "Reset dotdotdot for the give body element."
  [body-el]
  (let [$body-els (js/$ ">*" body-el)]
    (.each $body-els (fn [idx el]
      (this-as this
        (.trigger (js/$ this) "destroy"))))))

(def default-body-height 72)
(def default-all-posts-body-height 144)
(def default-draft-body-height 48)

(defn truncate-body
  "Given a body element truncate the body. It iterate on the elements
  of the body and truncate the first exceeded element found.
  This is to avoid truncating a DIV with multiple spaced P inside,
  since this is a problem for the dotdotdot library that we are using."
  [body-el height]
  (reset-truncate-body body-el)
  (.dotdotdot (js/$ body-el)
    #js {:height height
         :wrap "word"
         :watch true
         :ellipsis "..."}))

(defn icon-for-mimetype
  "Thanks to https://gist.github.com/colemanw/9c9a12aae16a4bfe2678de86b661d922"
  [mimetype]
  (case (s/lower mimetype)
    ;; Media
    "image" "fa-file-image-o"
    "image/png" "fa-file-image-o"
    "image/bmp" "fa-file-image-o"
    "image/jpg" "fa-file-image-o"
    "image/jpeg" "fa-file-image-o"
    "image/gif" "fa-file-image-o"
    ".jpg" "fa-file-image-o"
    "audio" "fa-file-audio-o"
    "video" "fa-file-video-o"
    ;; Documents
    "application/pdf" "fa-file-pdf-o"
    "application/msword" "fa-file-word-o",
    "application/vnd.ms-word" "fa-file-word-o",
    "application/vnd.oasis.opendocument.text" "fa-file-word-o",
    "application/vnd.openxmlformats-officedocument.wordprocessingml" "fa-file-word-o",
    "application/vnd.ms-excel" "fa-file-excel-o",
    "application/vnd.openxmlformats-officedocument.spreadsheetml" "fa-file-excel-o",
    "application/vnd.oasis.opendocument.spreadsheet" "fa-file-excel-o",
    "application/vnd.ms-powerpoint" "fa-file-powerpoint-o",
    "application/vnd.openxmlformats-officedocument.presentationml" "fa-file-powerpoint-o",
    "application/vnd.oasis.opendocument.presentation" "fa-file-powerpoint-o",
    "text/plain" "fa-file-text-o",
    "text/html" "fa-file-code-o",
    "application/json" "fa-file-code-o",
    ;; Archives
    "application/gzip" "fa-file-archive-o",
    "application/zip" "fa-file-archive-o",
    ;; Code
    "text/css" "fa-file-code-o"
    "text/php" "fa-file-code-o"
    ;; Generic case
    "fa-file"))

(defn get-activity-date [activity]
  (or (:published-at activity) (:created-at activity)))

(defn compare-activities [act-1 act-2]
  (let [time-1 (get-activity-date act-1)
        time-2 (get-activity-date act-2)]
    (compare time-2 time-1)))

(defn get-sorted-activities [posts-data]
  (vec (sort compare-activities (vals posts-data))))

(defn readonly-board? [links]
  (let [new-link (utils/link-for links "create")
        update-link (utils/link-for links "partial-update")
        delete-link (utils/link-for links "delete")]
    (and (nil? new-link)
         (nil? update-link)
         (nil? delete-link))))

(defn readonly-entry? [links]
  (let [partial-update (utils/link-for links "partial-update")
        delete (utils/link-for links "delete")]
    (and (nil? partial-update) (nil? delete))))

(defn post-unseen?
  "An entry is new if its uuid is contained in container's unseen."
  [entry changes]
  (let [board-uuid (:board-uuid entry)
        board-change-data (get changes board-uuid {})
        board-unseen (:unseen board-change-data)
        user-id (jwt/user-id)]
    (and (utils/in? board-unseen (:uuid entry))
         (not= (:user-id (:publisher entry)) user-id))))

(defn post-unread?
  "An entry is new if its uuid is contained in container's unread."
  [entry changes]
  (let [board-uuid (:board-uuid entry)
        board-change-data (get changes board-uuid {})
        board-unread (:unread board-change-data)]
    (utils/in? board-unread (:uuid entry))))

(defn body-for-stream-view [inner-html]
  (if (seq inner-html)
    (let [$container (.html (js/$ "<div/>") inner-html)
          _ (.append (js/$ (.-body js/document)) $container)
          has-images (pos? (.-length (.find $container "img")))
          _ (.remove (js/$ "img" $container))
          empty-paragraph-rx (js/RegExp "^(<br\\s*/?>)?$" "i")
          _ (.each (.find $container "p")
             #(this-as this
                (let [$this (js/$ this)]
                  (when (.match (.html $this) empty-paragraph-rx)
                    (.remove $this)))))
          cleaned-body (.html $container)
          _ (.detach $container)]
      [has-images cleaned-body])
    [false inner-html]))

(defn fix-entry
  "Add `:read-only`, `:board-slug`, `:board-name` and `:content-type` keys to the entry map."
  [entry-data board-data changes]
  (let [comments-link (utils/link-for (:links entry-data) "comments")
        add-comment-link (utils/link-for (:links entry-data) "create" "POST")
        fixed-board-uuid (or (:board-uuid entry-data) (:uuid board-data))
        fixed-board-slug (or (:board-slug entry-data) (:slug board-data))
        fixed-board-name (or (:board-name entry-data) (:name board-data))
        [has-images stream-view-body] (body-for-stream-view (:body entry-data))
        is-uploading-video? (dis/uploading-video-data (:video-id entry-data))
        fixed-video-id (:video-id entry-data)
        body-thumbnail (html/first-body-thumbnail (:body entry-data))]
    (when (seq fixed-video-id)
      (ziggeo/init-ziggeo true))
    (-> entry-data
      (assoc :content-type "entry")
      (assoc :unseen (post-unseen? (assoc entry-data :board-uuid fixed-board-uuid) changes))
      (assoc :unread (post-unread? (assoc entry-data :board-uuid fixed-board-uuid) changes))
      (assoc :read-only (readonly-entry? (:links entry-data)))
      (assoc :board-uuid fixed-board-uuid)
      (assoc :board-slug fixed-board-slug)
      (assoc :board-name fixed-board-name)
      (update :board-access #(or % (:access board-data)))
      (assoc :has-comments (boolean comments-link))
      (assoc :can-comment (boolean add-comment-link))
      (assoc :stream-view-body stream-view-body)
      (assoc :body-has-images has-images)
      (assoc :fixed-video-id fixed-video-id)
      (assoc :has-thumbnail body-thumbnail)
      (assoc :body-thumbnail body-thumbnail)
      (assoc :comments (comment-utils/sort-comments (:comments entry-data))))))

(defn fix-board
  "Parse board data coming from the API."
  ([board-data]
   (fix-board board-data {}))
  ([board-data change-data & [direction]]
    (let [links (:links board-data)
          with-read-only (assoc board-data :read-only (readonly-board? links))
          with-fixed-activities (reduce #(assoc-in %1 [:fixed-items (:uuid %2)]
                                          (fix-entry %2 {:slug (:board-slug %2)
                                                         :name (:board-name %2)
                                                         :uuid (:board-uuid %2)}
                                           change-data))
                                 with-read-only
                                 (:entries board-data))
          next-links (when direction
                      (vec
                       (remove
                        #(if (= direction :down) (= (:rel %) "previous") (= (:rel %) "next"))
                        links)))
          link-to-move (when direction
                         (if (= direction :down)
                           (utils/link-for (:old-links board-data) "previous")
                           (utils/link-for (:old-links board-data) "next")))
          fixed-next-links (if direction
                             (if link-to-move
                               (vec (conj next-links link-to-move))
                               next-links)
                             links)
          with-links (-> with-fixed-activities
                       (dissoc :old-links)
                       (assoc :links fixed-next-links))
          new-items (map :uuid (:entries board-data))
          without-items (dissoc with-links :entries)
          with-posts-list (assoc without-items :posts-list (vec
                                                             (case direction
                                                              :up (concat new-items (:posts-list board-data))
                                                              :down (concat (:posts-list board-data) new-items)
                                                              new-items)))
          with-saved-items (if direction
                             (assoc with-posts-list :saved-items (count (:posts-list board-data)))
                             with-posts-list)

          should-group-posts? (and (not (responsive/is-mobile-size?))
                                   (not (= (:slug board-data) utils/default-drafts-board-slug)))
          grouped-posts (when should-group-posts?
                          (grouped-posts (mapv #(or (get-in with-saved-items [:fixed-items %])
                                                    (dis/activity-data %))
                                          (:posts-list with-saved-items))))
          with-posts-separators (if should-group-posts?
                                  (assoc with-saved-items :items-to-render
                                   (vec (rest ;; Remove the first label
                                    (apply concat
                                     (mapv #(concat [(dissoc % :posts-list)] (remove nil? (:posts-list %))) grouped-posts)))))
                                  (assoc with-saved-items :items-to-render (:posts-list with-saved-items)))]
      with-posts-separators)))

(defn fix-container
  "Parse container data coming from the API, like All posts or Must see."
  ([container-data]
   (fix-container container-data {} (dis/org-data)))
  ([container-data change-data org-data & [direction]]
    (let [all-boards (:boards org-data)
          with-fixed-activities (reduce (fn [ret item]
                                          (let [board-data (first (filterv #(= (:slug %) (:board-slug item))
                                                            all-boards))]
                                            (assoc-in ret [:fixed-items (:uuid item)]
                                             (fix-entry item board-data change-data))))
                                 container-data
                                 (:items container-data))
          next-links (when direction
                      (vec
                       (remove
                        #(if (= direction :down) (= (:rel %) "previous") (= (:rel %) "next"))
                        (:links container-data))))
          link-to-move (when direction
                         (if (= direction :down)
                           (utils/link-for (:old-links container-data) "previous")
                           (utils/link-for (:old-links container-data) "next")))
          fixed-next-links (if direction
                             (if link-to-move
                               (vec (conj next-links link-to-move))
                               next-links)
                             (:links container-data))
          with-links (-> with-fixed-activities
                       (dissoc :old-links)
                       (assoc :links fixed-next-links))
          new-items (map :uuid (:items container-data))
          without-items (dissoc with-links :items)
          with-posts-list (assoc without-items :posts-list (vec
                                                             (case direction
                                                              :up (concat new-items (:posts-list container-data))
                                                              :down (concat (:posts-list container-data) new-items)
                                                              new-items)))
          with-saved-items (if direction
                             (assoc with-posts-list :saved-items (count (:posts-list container-data)))
                             with-posts-list)
          should-group-posts? (and (not (responsive/is-mobile-size?))
                                   (not (.match (:href container-data) #"(?i)/(inbox|bookmarks)(/|$)")))
          grouped-posts (when should-group-posts?
                          (grouped-posts (mapv #(or (get-in with-saved-items [:fixed-items %])
                                                    (dis/activity-data %))
                                          (:posts-list with-saved-items))))
          with-posts-separators (if should-group-posts?
                                  (assoc with-saved-items :items-to-render
                                   (vec (rest ;; Remove the first label
                                    (apply concat
                                     (mapv #(concat [(dissoc % :posts-list)] (remove nil? (:posts-list %))) grouped-posts)))))
                                  (assoc with-saved-items :items-to-render (:posts-list with-saved-items)))]
      with-posts-separators)))

(defn get-comments [activity-data comments-data]
  (or (-> comments-data
          (get (:uuid activity-data))
          :sorted-comments)
      (:comments activity-data)))

(defn is-element-visible?
   "Given a DOM element return true if it's actually visible in the viewport."
  [el]
  (let [rect (.getBoundingClientRect el)
        zero-pos? #(or (zero? %)
                       (pos? %))
        doc-element (.-documentElement js/document)
        win-height (or (.-clientHeight doc-element)
                       (.-innerHeight js/window))]
    (or      ;; Item top is more then the navbar height
        (and (>= (.-top rect) responsive/navbar-height)
             ;; and less than the screen height
             (< (.-top rect) win-height))
             ;; Item bottom is less than the screen height
        (and (<= (.-bottom rect) win-height)
         ;; and more than the navigation bar to
         (> (.-bottom rect) responsive/navbar-height)))))

(defn clean-who-reads-count-ids
  "Given a list of items we want to request the who reads count
   and the current read data, filter out the ids we already have data."
  [item-ids activities-read-data]
  (let [all-items (set (keys activities-read-data))
        request-set (set item-ids)
        diff-ids (clojure.set/difference request-set all-items)]
    (vec diff-ids)))

;; Last used section

(defn last-used-section []
  (let [org-slug (router/current-org-slug)
        cookie-name (router/last-used-board-slug-cookie org-slug)]
    (cook/get-cookie cookie-name)))

(defn save-last-used-section [section-slug]
  (let [org-slug (router/current-org-slug)
        last-board-cookie (router/last-used-board-slug-cookie org-slug)]
    (if section-slug
      (cook/set-cookie! last-board-cookie section-slug (* 60 60 24 365))
      (cook/remove-cookie! last-board-cookie))))

(defn has-attachments? [data]
  (seq (:attachments data)))

(defn has-headline? [data]
  (not (clojure.string/blank? (:headline data))))

(defn has-body? [data]
  (not (clojure.string/blank? (:body data))))

(defn has-text? [data]
  (or (has-headline? data)
      (has-body? data)))

(defn has-content? [data]
  (or (some? (:video-id data))
      (has-attachments? data)
      (has-text? data)))

(def iso-format (time-format/formatters :date-time))

(def date-format (time-format/formatter "MMMM d"))

(def date-format-year (time-format/formatter "MMMM d YYYY"))

(defn post-date [timestamp & [force-year]]
  (let [d (time-format/parse iso-format timestamp)
        now-year (.getFullYear (utils/js-date))
        timestamp-year (.getFullYear (utils/js-date timestamp))
        show-year (or force-year (not= now-year timestamp-year))
        f (if show-year date-format-year date-format)]
    (time-format/unparse f d)))