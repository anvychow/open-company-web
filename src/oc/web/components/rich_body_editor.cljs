(ns oc.web.components.rich-body-editor
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [cuerdas.core :as string]
            [oc.web.lib.jwt :as jwt]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.image-upload :as iu]
            [oc.web.components.ui.alert-modal :refer (alert-modal)]
            [goog.dom :as gdom]
            [goog.Uri :as guri]
            [goog.object :as gobj]
            [clojure.contrib.humanize :refer (filesize)]))

;; Attachment

(defn media-attachment-dismiss-picker
  "Called every time the image picke close, reset to inital state."
  [s editable]
  (when-not @(::media-attachment-did-success s)
    (.addAttachment editable nil nil)
    (reset! (::media-attachment s) false)))

(defn attachment-upload-failed-cb [state editable]
  (let [alert-data {:icon "/img/ML/error_icon.png"
                    :title "Sorry!"
                    :message "An error occurred with your file."
                    :solid-button-title "OK"
                    :solid-button-cb #(dis/dispatch! [:alert-modal-hide])}]
    (dis/dispatch! [:alert-modal-show alert-data])
    (utils/after 10 #(do
                       (reset! (::media-attachment-did-success state) false)
                       (media-attachment-dismiss-picker state editable)))))

(defn attachment-upload-success-cb [state editable res]
  (reset! (::media-attachment-did-success state) true)
  (let [url (gobj/get res "url")]
    (if-not url
      (attachment-upload-failed-cb state editable)
      (let [size (gobj/get res "size")
            mimetype (gobj/get res "mimetype")
            filename (gobj/get res "filename")
            prefix (str "Uploaded on " (utils/date-string (utils/js-date) [:year]) " - ")
            subtitle (str prefix (filesize size :binary false :format "%.2f" ))
            icon (utils/icon-for-mimetype mimetype)
            attachment-data {:fileName filename
                             :fileType mimetype
                             :fileSize size
                             :title filename
                             :subtitle subtitle
                             :icon icon}]
        (reset! (::media-attachment state) false)
        (.addAttachment editable url (clj->js attachment-data))
        (utils/after 1000 #(reset! (::media-attachment-did-success state) false))))))

(defn attachment-upload-error-cb [state editable res error]
  (attachment-upload-failed-cb state editable))

(defn add-attachment [s editable]
  (reset! (::media-attachment s) true)
  (iu/upload!
   nil
   (partial attachment-upload-success-cb s editable)
   nil
   (partial attachment-upload-error-cb s editable)
   (fn []
     (utils/after 400 #(media-attachment-dismiss-picker s editable)))))

;; Chart

(defn get-chart-thumbnail [chart-id oid]
  (str "https://docs.google.com/spreadsheets/d/" chart-id "/embed/oimg?id=" chart-id "&oid=" oid "&disposition=ATTACHMENT&bo=false&zx=sohupy30u1p"))

(defn media-chart-add [s editable chart-url]
  (if (nil? chart-url)
    (.addChart editable nil nil nil)
    (let [url-fragment (last (clojure.string/split chart-url #"/spreadsheets/d/"))
          chart-proxy-uri (str "/_/sheets-proxy/spreadsheets/d/" url-fragment)
          parsed-uri (guri/parse chart-url)
          oid (.get (.getQueryData parsed-uri) "oid")
          splitted-path (clojure.string/split (.getPath parsed-uri) #"/")
          chart-id (nth splitted-path (- (count splitted-path) 2))
          chart-thumbnail (get-chart-thumbnail chart-id oid)]
      (.addChart editable chart-proxy-uri chart-id chart-thumbnail))))

;; Video

(defn get-video-thumbnail [video]
  (cond
    (= (:type video) :youtube)
    (str "https://img.youtube.com/vi/" (:id video) "/0.jpg")
    (= (:type video) :vimeo)
    (:thumbnail video)))

(defn get-video-src [video]
  (cond
   (= (:type video) :youtube)
   (str "https://www.youtube.com/embed/" (:id video))
   (= (:type video) :vimeo)
   (str "https://player.vimeo.com/video/" (:id video))))

(defn media-video-add [s editable video-data]
  (if (= :dismiss video-data)
    (.addVideo editable nil nil nil nil)
    (.addVideo editable (get-video-src video-data) (:type video-data) (:id video-data) (get-video-thumbnail video-data))))

;; Photo

(defn media-photo-add-error
  "Show an error alert view for failed uploads."
  []
  (let [alert-data {:icon "/img/ML/error_icon.png"
                    :title "Sorry!"
                    :message "An error occurred with your image."
                    :solid-button-title "OK"
                    :solid-button-cb #(dis/dispatch! [:alert-modal-hide])}]
    (dis/dispatch! [:alert-modal-show alert-data])))

(defn media-photo-add-if-finished [s editable]
  (let [image @(::media-photo s)]
    (when (and (contains? image :url)
               (contains? image :width)
               (contains? image :height)
               (contains? image :thumbnail))
      (.addPhoto editable (:url image) (:thumbnail image) (:width image) (:height image))
      (reset! (::media-photo s) nil)
      (reset! (::media-photo-did-success s) false))))

(defn img-on-load [s editable url img]
  (reset! (::media-photo s) (merge @(::media-photo s) {:width (.-width img) :height (.-height img)}))
  (gdom/removeNode img)
  (media-photo-add-if-finished s editable))

(defn media-photo-dismiss-picker
  "Called every time the image picke close, reset to inital state."
  [s editable]
  (when-not @(::media-photo-did-success s)
    (reset! (::media-photo s) false)
    (.addPhoto editable nil nil nil nil)))

(defn add-photo [s editable]
  (reset! (::media-photo s) true)
  (iu/upload! {:accept "image/*"}
   (fn [res]
     (reset! (::media-photo-did-success s) true)
     (let [url (gobj/get res "url")
           img   (gdom/createDom "img")]
       (set! (.-onload img) #(img-on-load s editable url img))
       (set! (.-className img) "hidden")
       (gdom/append (.-body js/document) img)
       (set! (.-src img) url)
       (reset! (::media-photo s) {:res res :url url})
       (iu/thumbnail! url
        (fn [thumbnail-url]
         (reset! (::media-photo s) (assoc @(::media-photo s) :thumbnail thumbnail-url))
         (media-photo-add-if-finished s editable)))))
   nil
   (fn [err]
     (media-photo-add-error))
   (fn []
     ;; Delay the check because this is called on cancel but also on success
     (utils/after 1000 #(media-photo-dismiss-picker s editable)))))

;; Picker cb

(defn on-picker-click [s editable type]
  (cond
    (= type "photo")
    (add-photo s editable)
    (= type "video")
    (do (dis/dispatch! [:input [(:dispatch-input-key (first (:rum/args s))) :media-video] true]) (reset! (::media-video s) true))
    (= type "chart")
    (do (dis/dispatch! [:input [(:dispatch-input-key (first (:rum/args s))) :media-chart] true]) (reset! (::media-chart s) true))
    (= type "attachment")
    (add-attachment s editable)))

(defn body-placeholder []
  (let [first-name (jwt/get-key :first-name)]
    (if-not (empty? first-name)
      (str "What's new, " first-name "?")
      "What's new?")))

(defn body-on-change [state]
  (reset! (::did-change state) true)
  (when-let [body-el (rum/ref-node state "body")]
    ; Attach paste listener to the body and all its children
    ; (js/recursiveAttachPasteListener body-el #(body-on-change state))
    (let [options (first (:rum/args state))
          dispatch-input-key (:dispatch-input-key options)
          on-change (:on-change options)
          inner-html (.-innerHTML body-el)
          $container (.html (js/$ "<div class=\"hidden\"/>") inner-html)
          _ (.append (js/$ (.-body js/document)) $container)
          _ (.remove (js/$ ".rangySelectionBoundary" $container))
          cleaned-html (.html $container)
          _ (.detach $container)
          emojied-body (utils/emoji-images-to-unicode (gobj/get (utils/emojify cleaned-html) "__html"))]
      (on-change emojied-body))))

(defn- setup-editor [s]
  (let [options (first (:rum/args s))
        show-subtitle (:show-h2 options)
        media-config (:media-config options)
        body-el (rum/ref-node s "body")
        media-picker-opts {:buttons (clj->js media-config)
                           :delegateMethods #js {:onPickerClick (partial on-picker-click s)
                                                 :willExpand #(reset! (::did-change s) true)}}
        media-picker-ext (js/MediaPicker. (clj->js media-picker-opts))
        body-placeholder (body-placeholder)
        buttons (if show-subtitle
                  ["bold" "italic" "unorderedlist" "anchor"]
                  ["bold" "italic" "h2" "unorderedlist" "anchor"])
        options {:toolbar #js {:buttons (clj->js buttons)}
                 :buttonLabels "fontawesome"
                 :anchorPreview #js {:hideDelay 500, :previewValueSelector "a"}
                 :extensions #js {:autolist (js/AutoList.)
                                  :media-picker media-picker-ext}
                 :autoLink true
                 :anchor #js {:customClassOption nil
                              :customClassOptionText "Button"
                              :linkValidation true
                              :placeholderText "Paste or type a link"
                              :targetCheckbox false
                              :targetCheckboxText "Open in new window"}
                 :paste #js {:forcePlainText false
                             :cleanPastedHTML false}
                 :placeholder #js {:text body-placeholder
                                   :hideOnClick true}}
        body-editor  (new js/MediumEditor body-el (clj->js options))]
    (reset! (::editable-ext s) media-picker-ext)
    (.subscribe body-editor
                "editableInput"
                (fn [event editable]
                  (body-on-change s)))
    (reset! (::editor s) body-editor)
    ; (js/recursiveAttachPasteListener body-el #(body-on-change s))
    ))

(rum/defcs rich-body-editor  < rum/reactive
                               (rum/local false ::did-change)
                               (rum/local nil ::editor)
                               (rum/local nil ::editable-ext)
                               (rum/local false ::media-photo)
                               (rum/local false ::media-video)
                               (rum/local false ::media-chart)
                               (rum/local false ::media-attachment)
                               (rum/local false ::media-photo-did-success)
                               (rum/local false ::media-attachment-did-success)
                               (drv/drv :entry-editing)
                               (drv/drv :story-editing)
                               {:did-mount (fn [s]
                                             (utils/after 100 #(do
                                              (setup-editor s)
                                              (let [classes (:classes (first (:rum/args s)))]
                                                (when (string/includes? classes "emoji-autocomplete")
                                                  (js/emojiAutocomplete)))))
                                             s)
                                :will-update (fn [s]
                                               (let [dispatch-input-key (:dispatch-input-key (first (:rum/args s)))
                                                     data @(drv/get-ref s dispatch-input-key)
                                                     video-data (:media-video data)
                                                     chart-data (:media-chart data)]
                                                  (when @(::media-video s)
                                                    (if (map? video-data)
                                                      (media-video-add s @(::editable-ext s) video-data)
                                                      (media-video-add s @(::editable-ext s) nil))
                                                    (when (or (= video-data :dismiss)
                                                              (map? video-data))
                                                      (reset! (::media-video s) false)
                                                      (dis/dispatch! [:input [dispatch-input-key :media-video] nil])))
                                                  (when (and @(::media-chart s)
                                                             chart-data)
                                                    (if (map? chart-data)
                                                      (media-chart-add s @(::editable-ext s) chart-data)
                                                      (media-chart-add s @(::editable-ext s) nil))
                                                    (when (or (= chart-data :dismiss)
                                                              (string? chart-data))
                                                      (reset! (::media-chart s) false)
                                                      (dis/dispatch! [:input [dispatch-input-key :media-chart] nil]))))
                                               s)
                                :will-unmount (fn [s]
                                                (when @(::editor s)
                                                  (.destroy @(::editor s)))
                                                s)}
  [s {:keys [dispatch-input-key initial-body on-change classes show-placeholder]}]
  (let [_ (drv/react s dispatch-input-key)]
    [:div.rich-body-editor-container
      [:div.rich-body-editor
        {:ref "body"
         :content-editable true
         :class (str classes (when (or (not show-placeholder) @(::did-change s)) " medium-editor-placeholder-hidden"))
         :dangerouslySetInnerHTML (utils/emojify initial-body)}]]))