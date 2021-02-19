(ns clojure-lsp.features.completion-test
  (:require
    [clojure-lsp.db :as db]
    [clojure-lsp.feature.completion :as f.completion]
    [clojure-lsp.test-helper :as h]
    [clojure.string :as string]
    [clojure.test :refer [deftest testing is]]))

(h/reset-db-after-test)

(defn code [& strings] (string/join "\n" strings))

(deftest test-completion
  (reset! db/db {})
  (h/load-code-and-locs (code "(ns alpaca.ns (:require [user :as alpaca]))"
                              "(def barr)"
                              "(def bazz)") "file:///a.cljc")
  (h/load-code-and-locs (code "(ns user)"
                              "(def alpha)"
                              "alp"
                              "ba") "file:///b.clj")
  (h/load-code-and-locs (code "(ns alpaca.ns)"
                              "(def baff)") "file:///c.cljs")
  (h/load-code-and-locs (code "(ns d (:require [alpaca.ns :as alpaca])) frequen"
                              "(def bar \"some good docs\"123)"
                              "(defn barbaz [a b] 123)"
                              "(def some 123)")
                        "file:///d.clj")
  (h/load-code-and-locs (code "(ns e (:require [alpaca.ns :refer [ba]]"
                              "                [d :as d-alias]))"
                              "Syste"
                              "d-alias/b"
                              "123")
                        "file:///e.clj")
  (h/load-code-and-locs (code "(ns f (:require [alpaca.ns :refer [ba]]"
                              "                 alp))")
                        "file:///f.clj")
  (h/load-code-and-locs (code "(ns g (:require [alpaca.ns :as ba :refer [baq]]))"
                              "(defn bar [baz] ba)")
                        "file:///g.clj")

  (swap! db/db merge {:client-capabilities {:text-document {:hover {:content-format ["markdown"]}}}})
  (testing "complete-a"
    (h/assert-submaps
     [{:label "alpaca" :kind :module :detail "user"}
      {:label "alpaca" :kind :module :detail "alpaca.ns"}
      {:label "alpha" :kind :variable}
      {:label "ba" :detail "alpaca.ns"}]
     (f.completion/completion "file:///b.clj" 3 3)))
  (testing "complete-ba"
    (h/assert-submaps
     [ {:label "ba" :kind :module}
       {:label "ba/baff"}
       {:label "ba/barr"}
       {:label "ba/bazz"}
      {:label "bases" :detail "clojure.core/bases"}]
     (f.completion/completion "file:///b.clj" 4 3)))
  (testing "complete-alpaca"
    (h/assert-submaps
     [{:label "alpaca" :kind :module :detail "user"}
      {:label "alpaca" :kind :module :detail "alpaca.ns"}
      {:label "alpha" :kind :variable}
      {:label "ba" :detail "alpaca.ns"}]
     (f.completion/completion "file:///b.clj" 3 3)))
  (testing "complete-core-stuff"
    (h/assert-submaps
     [{:label "frequencies", :detail "clojure.core/frequencies"}]
     (f.completion/completion "file:///d.clj" 1 49))
    (testing "complete symbols from alias"
      (h/assert-submaps
       [{:label "d-alias/bar"
         :kind :variable
         :documentation {:kind "markdown"
                         :value "```clojure\nd/bar\n```\n\n----\n```clojure\nsome good docs\n```\n----\n*/d.clj*"}}
        {:label "d-alias/barbaz", :kind :function}]
       (f.completion/completion "file:///e.clj" 4 10))))
  (testing "complete-core-stuff"
    (h/assert-submaps
     [{:label "frequencies", :detail "clojure.core/frequencies"}]
     (f.completion/completion "file:///d.clj" 1 49))
    (h/assert-submaps
     [{:label "System", :detail "java.lang.System"}]
     (f.completion/completion "file:///e.clj" 3 6)))
  (testing "complete non symbols doesn't blow up"
    (is (= nil (f.completion/completion "file:///e.clj" 6 3))))
  (testing "complete all available namespace definitions when inside require"
    (h/assert-submaps
      [{:label "alpaca.ns" :kind :module}
       {:label "alpaca.ns" :kind :module} ]
     (f.completion/completion "file:///f.clj" 1 21)))
  (testing "complete locals"
    (h/assert-submaps
      [{:label "ba" :kind :module}
       {:label "ba/baff"}
       {:label "ba/barr"}
       {:label "ba/bazz"}
       {:label "bar"}
       {:label "bases" :detail "clojure.core/bases"}
       {:label "baz"}
       ;; TODO should complete local refer
       #_{:label "baq"}]
      (f.completion/completion "file:///g.clj" 2 18))))
