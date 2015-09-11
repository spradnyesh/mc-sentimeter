(ns ^:figwheel-always mc-sentimeter.core
    (:require [clojure.string :as str]
              [cljs.core.async :refer [<! >! chan close!]]
              [ajax.core :refer [GET]]
              [cljs-http.client :as http]
              ;; [enfocus.core :as ef]
              ;; [dommy.core :refer-macros [sel sel1] :as dommy]
              [hickory.core :as hc]
              [hickory.select :as hs]
              [reagent.core :as r])
    (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; constants

(def a-z "abcdefghijklmnopqrstuvwxyz")
(def index-prefix "http://www.moneycontrol.com/india/stockmarket/pricechartquote/")
(def index-suffixes (concat (map str/upper-case
                                 (rest (str/split a-z #"")))
                            '("others")))
(defonce app-state (r/atom nil))
;; (defonce refreshing? (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; utils

(def dev? true)
(defn reset [] (reset! app-state nil))
(defn log [& args] (.log js/console (apply str args)))
(defn alert [& args] (js/alert (apply str args)))
(defn by-id [id] (.getElementById js/document id))
(defn contains [haystack needle] (when haystack (>= (.indexOf haystack needle) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers

(defn error-handler [{:keys [status status-text]}]
  (log "something bad happened: " status " " status-text))

(defn sd-helper [response]
  (let [parsed (hc/as-hickory (hc/parse response))
        smbl (->> parsed
                  (hs/select (hs/class :b_42))
                  first
                  :content
                  first
                  :content
                  first)
        url (let [content (->> parsed
                               (hs/select (hs/tag :meta))
                               (filter #(contains (:content (:attrs %)) "url="))
                               first
                               :attrs
                               :content)]
              (subs content (.indexOf content "http://")))
        [buy sell hold] (map (comp int #(subs % 0 (- (count %) 2)))
                             (->> parsed
                                  (hs/select (hs/class :pl_bar))
                                  (take 3)
                                  (map (comp #(subs % 6) :style :attrs))))
        reviews (->> parsed
                     (hs/select (hs/class :bl_11))
                     (filter #(contains (:href (:attrs %)) "/comments/"))
                     first
                     :attrs
                     :href)]
    #_(log "&&&&& " smbl url "\n" buy sell hold "\n" reviews)
    {:smbl smbl :url url :reviews reviews
     :buy buy :sell sell :hold hold}))

(defn is-helper [response]
  (let [rslt (remove #(= % "javascript:;")
                     (map (comp :href :attrs)
                          (filter #(= :a (:tag %))
                                  (hs/select (hs/class :bl_12)
                                             (hc/as-hickory (hc/parse response))))))]
    (if dev? (take 10 rslt) rslt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; logic

(defn smbl-details [urls]
  (let [ch (chan)]
    (dorun (map #(GET % {:handler (fn [response] (go (>! ch response)))
                         :error-handler error-handler})
                urls))
    ch))

(defn index->smbls [index-suffixes]
  (let [ch (chan)]
    (dorun (map (comp #(GET % {:handler (fn [response] (go (>! ch response)))
                               :error-handler error-handler})
                      #(str index-prefix %))
                index-suffixes))
    ch))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; view

(defn main []
  (when (empty? @app-state)
    (let [state (atom nil)
          smbls (if dev? ["A" "S"] index-suffixes)
          ch-urls (index->smbls smbls)
          ;; ch needs to be of size 1 because both >! and <! happen in same thread,
          ;; and both producer and consumer will never be available simultaneously
          ;; (on the same thread) if size = 0
          ch (chan 1)]
      (go (>! ch (do (dotimes [_ (count smbls)]
                       (let [urls (is-helper (<! ch-urls))]
                         #_(log "@@@@@ " urls)
                         (let [ch-details (smbl-details urls)]
                           (dotimes [_ (count urls)]
                             (let [details (sd-helper (<! ch-details))]
                               #_(log "##### " details)
                               (swap! state conj details))))))
                     1)) ; cannot put! "nil" on ch
          (let [_ (<! ch) ; wait till all details for all smbls have been collected
                rslt (reverse (sort-by :buy @state))]
            (reset! app-state (->> rslt
                                   (filter #(>= (or (:buy %) 0) 80))
                                   (take 10))))))))

(defn c-main []
  [:div
   [:table {:class "table table-striped table-bordered"}
    [:tbody
     [:tr [:th "name"] [:th "buy"]]
     (for [row @app-state]
       ^{:key (.random js/Math)}
       [:tr [:td [:a {:href (:url row)} (:smbl row)]] [:td (:buy row)]])]]
   [:input {:id "refresh" :type "button" :value "Refresh"}]])

(defn add-event-listeners []
  (.addEventListener (by-id "refresh") "click" #(do (reset)(main))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; init

(defn mount-root []
  (r/render [c-main] (.getElementById js/document "app")))

;; this happens when js (not page) is reloaded by figwheel
(defn on-js-reload [] (mount-root))

;; this happens when page is reloaded
(mount-root)
(add-event-listeners)
