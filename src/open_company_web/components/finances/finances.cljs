(ns open-company-web.components.finances.finances
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.components.finances.utils :as finances-utils]
            [open-company-web.components.finances.cash :refer (cash)]
            [open-company-web.components.finances.cash-flow :refer (cash-flow)]
            [open-company-web.components.finances.costs :refer (costs)]
            [open-company-web.components.finances.runway :refer (runway)]
            [open-company-web.components.finances.finances-edit :refer (finances-edit)]
            [open-company-web.components.update-footer :refer (update-footer)]
            [open-company-web.components.ui.rich-editor :refer (rich-editor)]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.section-utils :as section-utils]
            [open-company-web.components.revisions-navigator :refer (revisions-navigator)]
            [open-company-web.api :as api]
            [open-company-web.components.ui.editable-title :refer (editable-title)]
            [open-company-web.components.ui.utility-components :refer (editable-pen)]
            [open-company-web.components.section-footer :refer (section-footer)]))

(defn get-state [owner data & [initial]]
  (let [section-data (:section-data data)
        notes-data (:notes section-data)]
    {:focus (if initial
              "cash"
              (om/get-state owner :focus))
     :title-editing (if initial
                      (not (not (:oc-editing section-data)))
                      (om/get-state owner :title-editing))
     :notes-editing (if initial
                      (not (not (:oc-editing section-data)))
                      (om/get-state owner :notes-editing))
     :data-editing (if initial
                     (not (not (:oc-editing section-data)))
                     (om/get-state owner :data-editing))
     :finances-data (finances-utils/map-placeholder-data (:data section-data))
     :title (:title section-data)
     :notes-body (:body (:notes section-data))
     :as-of (:updated-at section-data)}))

(defn subsection-click [e owner]
  (.preventDefault e)
  (let [tab  (.. e -target -dataset -tab)]
    (om/update-state! owner :focus (fn [] tab)))
  (.stopPropagation e))

(defn start-title-editing-cb [owner]
  (om/set-state! owner :title-editing true))

(defn start-notes-editing-cb [owner]
  (om/set-state! owner :notes-editing true))

(defn start-data-editing-cb [owner]
  (om/set-state! owner :data-editing true))

(defn cancel-cb [owner data]
  (if (om/get-state owner :oc-editing)
    ; remove an unsaved section
    (section-utils/remove-section (name (:section data)))
    ; revert the edited data to the initial values
    (let [state (get-state owner data)]
      ; reset the growth fields to the initial values
      (om/set-state! owner :title (:title state))
      (om/set-state! owner :notes-body (:notes-body state))
      (om/set-state! owner :finances-data (:finances-data state))
      ; and the editing state flags
      (om/set-state! owner :title-editing false)
      (om/set-state! owner :notes-editing false)
      (om/set-state! owner :data-editing false))))

(defn has-data-changes [owner data]
  (let [section-data (:section-data data)]
    (not= (finances-utils/map-placeholder-data (:data section-data)) (om/get-state owner :finances-data))))

(defn has-title-changes [owner data]
  (let [section-data (:section-data data)]
    (and (not= (:title section-data) (om/get-state owner :title))
         (pos? (count (om/get-state owner :title))))))

(defn has-notes-changes [owner data]
  (let [section-data (:section-data data)
        notes-data (:notes-data section-data)]
    (not= (:body notes-data) (om/get-state owner :notes-body))))

(defn has-changes [owner data]
  (or (has-title-changes owner data)
      (has-notes-changes owner data)))

(defn check-non-zero-value [v]
  (and (not (zero? v))
       (not (nil? v))))

(defn has-not-zero-data [owner]
  (let [finances-data (om/get-state owner :finances-data)]
    (some (fn [[_ v]] (or (check-non-zero-value (:cash v))
                          (check-non-zero-value (:revenue v))
                          (check-non-zero-value (:costs v))))
                 finances-data)))

(defn cancel-if-needed-cb [owner data]
  (when (and (not (has-data-changes owner data))
             (not (has-changes owner data)))
    (cancel-cb owner data)))

(defn change-cb [owner k v & [c]]
  (when c
    ; the body-counter is needed to avoid that a fast editing
    ; could replace a new updated html in the contenteditable field
    ; it is only an incremental prop that discard old html when it comes in
    (om/set-state! owner :body-counter c))
  (om/set-state! owner k v))

(defn get-finances-value [v]
  (if (js/isNaN v)
    0
    v))

(defn fix-row [row]
  (let [fixed-cash (update-in row [:cash] get-finances-value)
        fixed-revenue (assoc fixed-cash :revenue (get-finances-value (:revenue row)))
        fixed-costs (assoc fixed-revenue :costs (get-finances-value (:costs row)))
        fixed-burnrate (assoc fixed-costs :burn-rate (utils/calc-burn-rate (:revenue fixed-costs) (:costs fixed-costs)))
        fixed-runway (assoc fixed-burnrate :runway (utils/calc-runway (:cash fixed-burnrate) (:burn-rate fixed-burnrate)))]
    fixed-runway))

(defn change-finances-data-cb [owner row]
  (let [fixed-row (fix-row row)
        period (:period fixed-row)
        finances-data (om/get-state owner :finances-data)
        fixed-data (assoc finances-data period fixed-row)]
    (om/set-state! owner :finances-data fixed-data)))

(defn clean-data [data]
  ; a data entry is good if we have the period and one other value: cash, costs or revenue
  (when (and (not (nil? (:period data)))
             (or (not (nil? (:cash data)))
                 (not (nil? (:costs data)))
                 (not (nil? (:revenue data)))))
    (dissoc data :burn-rate :runway :avg-burn-rate :new :value)))

(defn clean-finances-data [finances-data]
  (remove nil? (vec (map (fn [[_ v]] (clean-data v)) finances-data))))

(defn can-save [owner data]
  (cond
    ; new section
    (om/get-state owner :oc-editing)
    ; finances has data and title is not empty
    (and (has-not-zero-data owner)
         (pos? (count (om/get-state owner :title))))

    ; title data and notes editing
    (and (om/get-state owner :title-editing)
         (om/get-state owner :data-editing)
         (om/get-state owner :notes-editing))
    ; finances has data and at least one btw title data and notes has changes
    (and (has-not-zero-data owner)
         (or (has-title-changes owner data)
             (has-data-changes owner data)
             (has-notes-changes owner data)))

    ; title and data editing
    (and (om/get-state owner :title-editing)
         (om/get-state owner :data-editing))
    ; finances has data and title or data has changes
    (and (has-not-zero-data owner)
         (or (has-title-changes owner data)
             (has-data-changes owner data)))

    ; title and notes editing
    (and (om/get-state owner :title-editing)
         (om/get-state owner :notes-editing))
    ; finances has data and title or data has changes
    (or (has-title-changes owner data)
        (has-notes-changes owner data))

    ; data and notes editing
    (and (om/get-state owner :data-editing)
         (om/get-state owner :notes-editing))
    ; finances has data and data or notes has changes
    (and (has-not-zero-data owner)
         (or (has-data-changes owner data)
             (has-notes-changes owner data)))

    ; title editing
    (om/get-state owner :title-editing)
    ; title has changes
    (has-title-changes owner data)

    ; data editing
    (om/get-state owner :data-editing)
    ; finances has data and changes
    (and (has-data-changes owner data)
         (has-not-zero-data owner))

    ; notes editing
    (om/get-state owner :notes-editing)
    ; notes has changes
    (has-notes-changes owner data)))

(defn save-cb [owner data]
  (when (can-save owner data)
    (let [title (om/get-state owner :title)
          notes-body (om/get-state owner :notes-body)
          finances-data (om/get-state owner :finances-data)
          fixed-finances-data (clean-finances-data finances-data)
          section-data {:title title
                        :data fixed-finances-data
                        :notes {:body notes-body}}]
      (if (om/get-state owner :oc-editing)
        ; save a new section
        (let [slug (keyword (:slug @router/path))
              company-data (slug @dispatcher/app-state)]
          (api/patch-sections (:sections company-data) section-data (:section data)))
        ; save an existing section
        (api/save-or-create-section (merge section-data {:links (:links (:section-data data))
                                                         :section (:section data)}))))
    (om/set-state! owner :title-editing false)
    (om/set-state! owner :notes-editing false)
    (om/set-state! owner :data-editing false)
    (om/set-state! owner :oc-editing false)))

(defn has-revenues-or-costs [finances-data]
  (some #(or (not (zero? (:revenue %))) (not (zero? (:costs %)))) finances-data))

(defcomponent finances [data owner options]

  (init-state [_]
    (get-state owner data true))

  (will-receive-props [_ next-props]
    ; this means the section datas have changed from the API or at a upper lever of this component
    (when-not (= next-props (om/get-props owner))
      (om/set-state! owner (get-state owner next-props))))

  (render [_]
    (let [focus (om/get-state owner :focus)
          classes "composed-section-link oc-header"
          section (:section data)
          section-name (utils/camel-case-str (name section))
          section-data (:section-data data)
          notes-data (:notes section-data)
          cash-classes (str classes (when (= focus "cash") " active"))
          cash-flow-classes (str classes (when (= focus "cash-flow") " active"))
          runway-classes (str classes (when (= focus "runway") " active"))
          read-only-section (:read-only section-data)
          read-only (or (:read-only data) read-only-section)
          finances-row-data (:data section-data)
          sum-revenues (apply + (map :revenue finances-row-data))
          first-title (if (pos? sum-revenues) "Cash flow" "Burn rate")
          needs-runway (some #(neg? (:runway %)) finances-row-data)
          needs-cash-flow (has-revenues-or-costs finances-row-data)
          title-editing (om/get-state owner :title-editing)
          notes-editing (om/get-state owner :notes-editing)
          data-editing (om/get-state owner :data-editing)
          cancel-fn #(cancel-cb owner data)
          save-fn #(save-cb owner data)
          notes-body-change-fn (partial change-cb owner :notes-body)
          title-change-fn (partial change-cb owner :title)
          cancel-if-needed-fn #(cancel-if-needed-cb owner data)
          start-title-editing-fn #(when-not read-only
                                    (start-title-editing-cb owner))
          start-notes-editing-fn #(when-not read-only
                                    (start-notes-editing-cb owner))
          start-data-editing-fn #(when-not read-only
                                   (start-data-editing-cb owner))
          subsection-data {:section-data section-data
                           :read-only read-only
                           :currency (:currency data)
                           :start-editing-cb start-data-editing-fn}
          show-title (if (contains? options :show-title) (:show-title options) true)
          show-revisions-navigation (if (contains? options :show-revisions-navigation) (:show-revisions-navigation options) true)
          show-update-footer (if (contains? options :show-update-footer) true)]
      (dom/div {:class "section-container" :id "section-finances"}
        (dom/div {:class "composed-section finances"}
          (when show-title
            (om/build editable-title {:read-only read-only
                                      :editing title-editing
                                      :title (om/get-state owner :title)
                                      :placeholder (or (:title-placeholder section-data) section-name)
                                      :section section
                                      :start-editing-cb start-title-editing-fn
                                      :change-cb title-change-fn
                                      :cancel-cb cancel-fn
                                      :cancel-if-needed-cb cancel-if-needed-fn
                                      :save-cb save-fn}))
          (when-not data-editing
            (dom/div {:class (utils/class-set {:link-bar true
                                               :editable (not read-only)})}
              (dom/a {:href "#"
                      :class cash-classes
                      :title "Cash"
                      :data-tab "cash"
                      :on-click #(subsection-click % owner)} "Cash")
              (when needs-cash-flow
                (dom/a {:href "#"
                        :class cash-flow-classes
                        :title first-title
                        :data-tab "cash-flow"
                        :on-click #(subsection-click % owner)} first-title))
              (when needs-runway
                (dom/a {:href "#"
                        :class runway-classes
                        :title "Runway"
                        :data-tab "runway"
                        :on-click #(subsection-click % owner)} "Runway"))
              (when-not read-only
                (om/build editable-pen {:click-callback start-data-editing-fn}))))
          (dom/div {:class (utils/class-set {:composed-section-body true})}
            (if data-editing
              (om/build finances-edit {:finances-data (om/get-state owner :finances-data)
                                       :change-finances-cb (partial change-finances-data-cb owner)
                                       :currency (:currency data)})
              (case focus

                "cash"
                (om/build cash subsection-data)

                "cash-flow"
                (if (pos? sum-revenues)
                  (om/build cash-flow subsection-data)
                  (om/build costs subsection-data))

                "runway"
                (om/build runway subsection-data)))
            (when-not (:placeholder section-data)
              (om/build update-footer {:updated-at (:updated-at section-data)
                                       :author (:author section-data)
                                       :section :finances
                                       :editing (or title-editing notes-editing data-editing)
                                       :notes false}))
            (when (or (seq (:body notes-data))
                      (not read-only))
              (om/build rich-editor {:editing notes-editing
                                     :section :finances
                                     :body-counter (om/get-state owner :body-counter)
                                     :read-only read-only
                                     :body (om/get-state owner :notes-body)
                                     :placeholder (or (:body-placeholder section-data) (str section-name " notes here..."))
                                     :start-editing-cb start-notes-editing-fn
                                     :change-cb notes-body-change-fn
                                     :cancel-cb cancel-fn
                                     :cancel-if-needed-cb cancel-if-needed-fn
                                     :save-cb save-fn}))
            (when (and show-update-footer (seq (:author notes-data)))
              (om/build
                update-footer
                  {:author (:author notes-data)
                   :updated-at (:updated-at notes-data)
                   :section :finances
                   :editing (or title-editing notes-editing data-editing)
                   :notes true}))
            (if (or title-editing notes-editing data-editing)
              (om/build section-footer {:editing (or title-editing notes-editing data-editing)
                                        :cancel-cb cancel-fn
                                        ; disable save until there are changes depending if the section is new
                                        :save-disabled (not (can-save owner data))
                                        :is-new-section (om/get-state owner :oc-editing)
                                        :save-cb save-fn})
              (when show-revisions-navigation
                (om/build revisions-navigator data)))))))))
