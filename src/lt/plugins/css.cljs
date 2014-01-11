(ns lt.plugins.css
  (:require [clojure.string :as string]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.objs.editor :as ed]
            [lt.objs.files :as files]
            [lt.objs.clients :as clients]
            [lt.util.dom :refer [$ append]])
  (:require-macros [lt.macros :refer [behavior defui]]))

(defn get-prefix
  "Get the longest shared prefix of s1 and s2 and return it."
  ([s1 s2] (get-prefix s1 s2 ""))
  ([s1 s2 memo]
   (if (= (first s1) (first s2))
     (get-prefix (rest s1) (rest s2) (str memo (first s1)))
     memo)))

;; Not currently used.
(defn ensure-end-sep [s]
  "If s is a directory, ensure it ends in a path separator."
  (if (or (= (last s) files/separator) (not (files/dir? s)))
    s
    (str s files/separator)))

; @NOTE: Are the names source and dest backwards here?
(defn diff-paths [source dest]
  "Create a relative path which is the difference of paths source and dest."
  (let [root (get-prefix source dest)
        source (string/replace-first (or source "") root "")
        dest (string/replace-first (or dest "") root "")
        prefix (string/replace dest #"[^/]*/" "../")]
    (str prefix source)))

(defn process-urls [diff-path matches code]
  "Recursively prefix relative URLs in CSS with the given diff-path."
  (if-not (first matches)
    code
    (let [match (first matches)
          token (first match)
          url (second match)]
      (process-urls diff-path (rest matches)
                    (string/replace code token (str "url(\"" (files/join diff-path url) "\")"))))))

(defn preprocess [file-path client-path code]
  "Preprocess CSS to make it work as expected when injected."
  ; Matches a url() function containing a possibly quoted relative path. Captures just the path in group 1.
  (let [matches (distinct (re-seq #"(?m)[uU][rR][lL]\([\"']?([^\/\"'].+?)[\"']?\)" code))]
    (process-urls (diff-paths file-path client-path) matches code)))

(behavior ::on-eval
          :triggers #{:eval
                      :eval.one}
          :reaction (fn [editor]
                      (object/raise css-lang :eval! {:origin editor
                                                     :info (assoc (@editor :info)
                                                             :code (ed/->val (:ed @editor)))})))

(behavior ::eval-on-save
          :triggers #{:save}
          :reaction (fn [editor]
                      (when (and (-> @editor :client :default)
                                 (not (clients/placeholder? (-> @editor :client :default))))
                        (object/raise editor :eval))))

(behavior ::eval!
          :triggers #{:eval!}
          :reaction (fn [this event]
                      (let [{:keys [info origin]} event
                            client (eval/get-client! {:command :editor.eval.css
                                                      :origin origin
                                                      :info info})
                            file-path (:path info)
                            client-path (case (:type @client)
                                   "LT-UI" (files/lt-home "core/")
                                   "")
                            code (if-not client-path
                                   (:code info)
                                   (preprocess (files/parent file-path) client-path (:code info)))]
                        (clients/send client
                                      :editor.eval.css
                                      (assoc info :code code)
                                      :only origin))))

(object/object* ::css-lang
                :tags #{}
                :behaviors [::eval!]
                :triggers #{:eval!})

(def css-lang (object/create ::css-lang))