(ns oc.web.stores.team
  (:require [taoensso.timbre :as timbre]
            [oc.web.dispatcher :as dispatcher]
            [oc.web.lib.jwt :as j]
            [oc.web.lib.utils :as utils]))

(defmethod dispatcher/action :teams-get
  [db [_]]
  (assoc db :teams-data-requested true))

(defmethod dispatcher/action :teams-loaded
  [db [_ teams]]
  (assoc-in db [:teams-data :teams] teams))

(defn- users-info-hover-from-roster
  "Given the previous users map and the new users vector coming from team or roster.
   Create a map of the new users with only some arbitrary data and merge them with the old users."
  [old-users-map roster-data]
  (let [filtered-users (filter #(#{"active" "unverified"} (:status %)) (:users roster-data))
        new-users-map (zipmap
                       (map :user-id filtered-users)
                       (map #(select-keys % [:user-id :first-name :last-name :name :location :timezone :title]) filtered-users))]
    (merge-with merge (or old-users-map {}) new-users-map)))

(defmethod dispatcher/action :team-roster-loaded
  [db [_ org-slug roster-data]]
  (if roster-data
    (-> db
     (assoc-in (dispatcher/team-roster-key (:team-id roster-data)) roster-data)
     (update-in (dispatcher/users-info-hover-key org-slug) #(users-info-hover-from-roster % roster-data)))
    db))

(defn parse-team-data [team-data]
  (let [team-has-bot? (j/team-has-bot? (:team-id team-data))
        slack-orgs (:slack-orgs team-data)
        slack-users (j/get-key :slack-users)
        can-add-bot? (some #(->> % :slack-org-id keyword (get slack-users)) slack-orgs)]
    (-> team-data
     (assoc :can-slack-invite team-has-bot?)
     (assoc :can-add-bot (and (not team-has-bot?) can-add-bot?)))))

(defmethod dispatcher/action :team-loaded
  [db [_ org-slug team-data]]
  (if team-data
    ;; if team is the current org team, load the slack chennels
    (-> db
     (assoc-in (dispatcher/team-data-key (:team-id team-data)) (parse-team-data team-data))
     (update-in (dispatcher/users-info-hover-key org-slug) #(users-info-hover-from-roster % team-data)))
    db))

(defmethod dispatcher/action :channels-enumerate
  [db [_ team-id]]
  (assoc db :enumerate-channels-requested true))

(defmethod dispatcher/action :channels-enumerate/success
  [db [_ team-id channels]]
  (let [channels-key (dispatcher/team-channels-key team-id)]
    (if channels
      (assoc-in db channels-key channels)
      (-> db
        (update-in (butlast channels-key) dissoc (last channels-key))
        (dissoc :enumerate-channels-requested)))))

;; Invite users

(defmethod dispatcher/action :invite-users
  [db [_ checked-users]]
  (assoc db :invite-users checked-users))

(defmethod dispatcher/action :invite-user/success
  [db [_ user]]
  (let [inviting-users (:invite-users db)
        next-inviting-users (utils/vec-dissoc inviting-users user)]
    (assoc db :invite-users next-inviting-users)))

(defmethod dispatcher/action :invite-user/failed
  [db [_ user]]
  (let [invite-users (:invite-users db)
        idx (utils/index-of invite-users #(= (:user %) (:user user)))
        next-invite-users (assoc-in invite-users [idx :error] true)]
    (assoc db :invite-users next-invite-users)))

;; User actions

(defmethod dispatcher/action :user-action
  [db [_ team-id idx]]
  (assoc-in db (concat (dispatcher/team-data-key team-id) [:users idx :loading]) true))

(defmethod dispatcher/action :email-domain-team-add
  [db [_]]
  (assoc db :add-email-domain-team-error false))

(defmethod dispatcher/action :email-domain-team-add/finish
  [db [_ success]]
  (-> db
      (assoc-in [:um-domain-invite :domain] (if success "" (:domain (:um-domain-invite db))))
      (assoc :add-email-domain-team-error (if success false true))))