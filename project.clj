(defproject clj-markdown "0.1"
  :description "A Clojure library to parse the Markdown format."

  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]]
  :dev-dependencies [[swank-clojure "1.2.0"]]

  :source-path "src/main/clojure"
  :library-path "target/dependency"
  :test-path "src/test/clojure"
  :target-dir "target/"
  :local-repo-classpath true)
