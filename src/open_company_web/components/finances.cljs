(ns open-company-web.components.finances
    (:require [om.core :as om :include-macros true]
              [om-tools.core :as om-core :refer-macros [defcomponent]]
              [om-tools.dom :as dom :include-macros true]
              [open-company-web.components.report-line :refer [report-line]]
              [open-company-web.components.comment :refer [comment-component comment-readonly-component]]
              [open-company-web.components.report.finances-section :refer [finances-section]]
              [om-bootstrap.random :as  r]
              [om-bootstrap.panel :as p]
              [open-company-web.lib.utils :as utils]))

(defn calc-run-away [period cash burn-rate]
  (let [period-run-away (/ cash (utils/abs burn-rate))
        period-devider (if (= (first period) "M") 30 (* 30 4))]
    (int (* period-run-away period-devider))))

(defcomponent finances [data owner]
  (will-mount [_]
    (let [fin-data (:finances data)
          cash (:cash fin-data)
          revenue (:revenue fin-data)
          costs (:costs fin-data)]
      (om/set-state! owner :cash (utils/thousands-separator cash))
      (om/set-state! owner :revenue (utils/thousands-separator revenue))
      (om/set-state! owner :costs (utils/thousands-separator costs))))
  (render [_]
    (let [period (:period data)
          period-string (if (= (first period) "M") "month" "quarter")
          fin-data (:finances data)
          cash (:cash fin-data)
          revenue (:revenue fin-data)
          costs (:costs fin-data)
          currency (:currency data)
          burn-rate (- revenue costs)
          burn-rate (if (js/isNaN burn-rate) 0 burn-rate)
          positive-diff (>= burn-rate 0)
          burn-rate-label (if positive-diff "Growth rate" "Burn rate")
          burn-rate-classes (str "num " (if (= burn-rate 0) "" (if positive-diff "green" "red")))
          burn-rate-helper (str "Cash " (if positive-diff "earned" "used") " this " period-string)
          profitable (if (= burn-rate 0) "-" (if positive-diff "Yes" "No"))
          run-away (calc-run-away period cash burn-rate)
          currency-dict (utils/get-currency currency)
          currency-symbol (utils/get-symbol-for-currency-code currency)
          finances-rows [{:label "Cash on hand" :key-name :cash :help-block "Cash and cash equivalents"}
                         {:label "Revenue" :key-name :revenue :help-block (str "All revenue this " period-string)}
                         {:label "Costs" :key-name :costs :help-block (str "All costs this " period-string " including salaries")}]]
      (p/panel {:header (dom/h3 "Finances") :class "finances clearfix"}
        (dom/div {:class "finances row"}
          (dom/form {:class "form-horizontal"}

            (for [section finances-rows]
              (om/build finances-section (merge section {
                :cursor fin-data
                :prefix currency-symbol
                :placeholder (:text currency-dict)})))

            ;; Profitable
            (dom/div {:class "form-group"}
              (dom/label {:class "col-md-2 control-label"} "Profitable?")
              (dom/label {:class "col-md-2 control-label"} profitable)
              (dom/p {:class "help-block"} (if (= profitable "-") "" (str "Costs exceed revenue this " period-string))))

            ;; Burn rate
            (dom/div {:class "form-group"}
              (dom/label {:class "col-md-2 control-label"} burn-rate-label)
              (dom/label {:class "col-md-2 control-label"}
                (dom/span {:class burn-rate-classes} (str currency-symbol (utils/thousands-separator (utils/abs burn-rate)))))
              (dom/p {:class "help-block"} (if (= burn-rate 0) "" burn-rate-helper)))

            ;; Runaway
            (when (< burn-rate 0)
              (dom/div {:class "form-group"}
                (dom/label {:class "col-md-2 control-label"} "Runway")
                (dom/label {:class "col-md-2 control-label run-away"} (utils/thousands-separator run-away))
                (dom/p {:class "help-block"} (str "days of cash remaining"))))))

        ;; Comment textarea
        (om/build comment-component {
          :cursor fin-data
          :placeholder "Comments: explain any recent significant changes in costs or revenue, provide guidance on revenue and profitablity expectations"
          })))))

(defcomponent readonly-finances [data owner]
  (render [_]
    (let [fin-data (:finances data)
          cash (:cash fin-data)
          revenue (:revenue fin-data)
          costs (:costs fin-data)
          currency (:currency data)
          burn-rate (- revenue costs)
          burn-rate-label (if (> burn-rate 0) "Growth rate: " "Burn rate: ")
          burn-rate-classes (str "num " (if (> burn-rate 0) "green" "red"))
          profitable (if (> burn-rate 0) "Yes" "No")
          run-away (if (<= burn-rate 0) (quot cash burn-rate) "N/A")
          currency-symbol (utils/get-symbol-for-currency-code currency)]
      (r/well {:class "report-list finances clearfix"}
        (dom/div {:class "report-list-left"}

          (let [sections [{:number cash :label "cash on hand"}
                          {:number revenue :label "revenue this month"}
                          {:number costs :label "costs this month"}]]
            (for [section sections]
              (when-not (= (:number section) nil)
                (dom/div
                  (om/build report-line (merge section {:prefix currency-symbol :pluralize false}))))))

          (dom/div
            (dom/span {:class "label"} (str "Profitable this month? " profitable)))
          (dom/div
            (dom/span {:class "label"} burn-rate-label)
            (dom/span {:class "label"} currency-symbol)
            (dom/span {:class burn-rate-classes} (utils/thousands-separator (utils/abs burn-rate))))
          (dom/div
            (dom/span {:class "label"} "Runaway: " (if (<= burn-rate 0) (str (utils/abs run-away) " months") "N/A")))
          (om/build comment-readonly-component {:cursor fin-data :key :comment :disabled true}))))))
