(ns
  ^{:doc "Extract the limits that each user's plan allows"}
  keyssrv.users.limits)


(defn max-users [{:keys [plan]}]
  {:pre [plan]}
  (:max-users plan))

(defn max-vaults [{:keys [plan]}]
  {:pre [plan]}
  (:max-vaults plan))

(defn max-secrets [{:keys [plan]}]
  {:pre [plan]}
  (:max-secrets plan))

(defn max-envs [{:keys [plan]}]
  {:pre [plan]}
  (:max-envs plan))

(defn max-certs [{:keys [plan]}]
  {:pre [plan]}
  (:max-certs plan))

(defn max-snippets [{:keys [plan]}]
  {:pre [plan]}
  (:max-snippets plan))

(defn max-logins [{:keys [plan]}]
  {:pre [plan]}
  (:max-logins plan))
