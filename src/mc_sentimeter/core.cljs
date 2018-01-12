(ns ^:figwheel-always mc-sentimeter.core
    (:require [clojure.string :as str]
              [cljs.core.async :refer [<! >! chan close!]]
              [ajax.core :refer [GET]]
              [hickory.core :as hc]
              [hickory.select :as hs]
              [reagent.core :as r])
    (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; constants

(def index-prefix "http://www.moneycontrol.com/india/stockmarket/pricechartquote/")
(def index-suffixes (conj (mapv char (range 65 (+ 65 26))) "OTHERS")) ; A-Z + OTHERS
(defonce app-state (r/atom nil))

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
  (let [parsed (hc/as-hickory (hc/parse response))]
    (when-let [content (some->> parsed
                                (hs/select (hs/tag :meta))
                                ((fn [x] (when-not (empty? x) x)))
                                (filter #(contains (:content (:attrs %)) "url="))
                                first
                                :attrs
                                :content)]
      (let [url (subs content (.indexOf content "http://"))
            smbl (->> parsed
                      (hs/select (hs/class :company_name))
                      first
                      :content
                      first)
            nse-price (->> parsed
                           (hs/select (hs/id :Nse_Prc_tick))
                           first
                           :content
                           first
                           :content
                           first)

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
        (when dev? (log "&&&&& " smbl "\n" url "\n" buy sell hold "\n" reviews))
        {:smbl smbl :url url :reviews reviews
         :buy buy :sell sell :hold hold :nse-price nse-price}))))

(defn is-helper [response]
  (let [rslt (->> response
                  hc/parse
                  hc/as-hickory
                  (hs/select (hs/class :bl_12))
                  (filter #(= :a (:tag %)))
                  (map (comp :href :attrs))
                  (remove #(= % "javascript:;")))]
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
          ;; ch needs to be of size (>=) 1 because both >! and <! happen in same thread,
          ;; and both producer and consumer will never be available simultaneously
          ;; (on the same thread) if size = 0 (default)
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
                                   (remove #(or (nil? (:nse-price %))
                                                (< (js/parseFloat (:nse-price %)) 100)))
                                   (filter #(>= (or (:buy %) 0) 80))
                                   (take 10))))))))

(defn c-main []
  [:div
   [:table {:class "table table-striped table-bordered"}
    [:tbody
     [:tr [:th "name"] [:th "buy%"] [:th "nse-price"]]
     (for [row @app-state]
       ^{:key (.random js/Math)}
       [:tr [:td [:a {:href (:url row)} (:smbl row)]]
        [:td (:buy row)]
        [:td (:nse-price row)]])]]
   [:input {:id "refresh" :type "button" :value "Refresh"}]])

(defn add-event-listeners []
  (.addEventListener (by-id "refresh") "click" #(do (reset) (main))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; init

(defn mount-root []
  (r/render [c-main] (.getElementById js/document "app")))

;; this happens when js (not page) is reloaded by figwheel
(defn on-js-reload [] (mount-root))

;; this happens when page is reloaded
(mount-root)
(add-event-listeners)
