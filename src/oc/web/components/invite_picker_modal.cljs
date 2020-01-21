(ns oc.web.components.invite-picker-modal
  (:require [rum.core :as rum]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.actions.org :as org-actions]
            [oc.web.actions.team :as team-actions]
            [oc.web.actions.nav-sidebar :as nav-actions]))


(rum/defcs invite-picker-modal <
  ;; Mixins
  rum/reactive
  (drv/drv :query-params)
  (drv/drv :org-data)
  (drv/drv :team-data)
  (drv/drv :current-user-data)
  (drv/drv :invite-add-slack-checked)
  {:will-mount (fn [s]
    (let [query-params @(drv/get-ref s :query-params)
          invite-add-slack-checked @(drv/get-ref s :invite-add-slack-checked)]
      (when-not invite-add-slack-checked
        (dis/dispatch! [:input [:invite-add-slack-checked] true])
        (when (and (= (:org-settings query-params) "invite-picker")
                   (#{"bot" "team"} (:access query-params)))
          (nav-actions/show-org-settings :invite-slack))))
    (let [org-data @(drv/get-ref s :org-data)]
      (org-actions/get-org org-data true)
      (team-actions/teams-get))
    s)}
  [s]
  (let [team-data (drv/react s :team-data)
        current-user-data (drv/react s :current-user-data)]
    [:div.invite-picker-modal
      [:button.mlb-reset.modal-close-bt
        {:on-click nav-actions/close-all-panels}]
      [:div.invite-picker
        [:div.invite-picker-header
          [:div.invite-picker-header-title
            "Invite people"]
          [:button.mlb-reset.cancel-bt
            {:on-click #(nav-actions/show-org-settings nil)}
            "Back"]]
        [:div.invite-picker-body
          [:div.invite-picker-body-description
            "Ready for better team discussions? Invite your team!"]
          [:button.mlb-reset.invite-email-bt
            {:on-click #(nav-actions/show-org-settings :invite-email)}
            "Invite via email"]
          (if (-> team-data :slack-orgs count pos?)
            [:button.mlb-reset.invite-slack-bt
              {:on-click #(nav-actions/show-org-settings :invite-slack)}
              "Invite via Slack"]
            [:button.mlb-reset.invite-slack-bt
              {:on-click #(org-actions/bot-auth team-data current-user-data (str (router/get-token) "?org-settings=invite-picker"))}
              [:span.disabled "Invite via Slack"]
              [:span.enabled "(add Slack)"]])]]]))