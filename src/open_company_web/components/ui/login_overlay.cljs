(ns open-company-web.components.ui.login-overlay
  (:require [rum.core :as rum]
            [dommy.core :as dommy :refer-macros (sel1)]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.components.ui.login-button :as login]))

(defn close-overlay [e]
  (utils/event-stop e)
  (dis/dispatch! [:show-login-overlay false]))

(def dont-scroll
  {:will-mount (fn [s] (dommy/add-class! (sel1 [:body]) :no-scroll) s)
   :will-unmount (fn [s] (dommy/remove-class! (sel1 [:body]) :no-scroll) s)})

(rum/defcs login-with-slack < rum/reactive
                              dont-scroll
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    [:div.login-overlay.center
      {:on-click #(utils/event-stop %)}
      [:button.close {:on-click (partial close-overlay)} [:i.fa.fa-times]]
      [:button.btn-reset.mt2.login-button
        {:on-click #(login/login! (:extended-scopes-url (:auth-settings @dis/app-state)) %)}
        [:img {:src "https://api.slack.com/img/sign_in_with_slack.png"}]]
      [:div.login-overlay-footer.p2.mt1.group
        [:a.left {:on-click #(dis/dispatch! [:show-login-overlay :email])} "DON’T HAVE AN ACCOUNT? SIGN UP NOW"]]]])

(rum/defcs login-with-email < rum/reactive
                              dont-scroll
  [state]
  [:div.login-overlay-container.group
    {:on-click (partial close-overlay)}
    [:div.login-overlay.login-with-email.group
      {:on-click #(utils/event-stop %)}
      [:button.close {:on-click (partial close-overlay)} [:i.fa.fa-times]]
      [:div.p2.group
        [:div.sing-in-cta.mb3 "Sign in"]
        [:form.sign-in-form
          [:div.sign-in-label-container
            [:label.sign-in-label "EMAIL"]]
          [:div.sign-in-field-container
            [:input.sign-in-field {:value "" :type "text" :name "email"}]]
          [:div.sign-in-label-container
            [:label.sign-in-label "PASSWORD"]]
          [:div.sign-in-field-container
            [:input.sign-in-field {:value "" :type "password" :name "pswd"}]]
          [:div.group.pb2.my3
            [:div.left.forgot-password
              [:a {} "FORGOT PASSWORD?"]]
            [:div.right
              [:button.btn-reset.btn-solid "SIGN IN"]]]]]
      [:div.login-overlay-footer.p2.mt1.group
        [:a.left {} "DON’T HAVE AN ACCOUNT? SIGN UP NOW"]]]])
