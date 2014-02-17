(ns lt.plugins.css
  (:require [clojure.string :as string]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.objs.editor :as ed]
            [lt.objs.files :as files]
            [lt.objs.clients :as clients]
            [lt.util.dom :refer [$ append]])
  (:require-macros [lt.macros :refer [behavior defui]]))

(def relative-url-pattern
  (re-pattern (str "(?m)[uU][rR][lL]\\(" ; Matches all capitalizations of 'url('
                   "[\"']?"              ; Matches single, double, or no quotation mark
                   "(?!.*?:\\/\\/)"      ; Skips URLs prefixed by a protocol
                   "([^\\/\"']"          ; Skips URLs prefixed by a slash (or quotation marks, to prevent donation)
                   ".+?)"                ; Matches the URL string
                   "[\"']?\\)")))        ; Matches single, double, or no quotation mark followed by ')'

(defn preprocess [file-path client-path code]
  "Preprocess CSS to make it work as expected when injected."
  ; Matches a url() function containing a possibly quoted relative path. Captures just the path in group 1.
  (let [matches (distinct (re-seq relative-url-pattern code))
        diff (files/relative client-path file-path)]
    (reduce (fn [final [url-call path]]
              (string/replace final url-call (str "url(\""  diff "/" path "\")")))
            code
            matches)))

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
                                          nil)
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
