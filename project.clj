(defproject org.clojars.jsefler/sm "1.0.0-SNAPSHOT"
  :description "Automated tests for Red Hat Subsciption Manager CLI and GUI"
  :java-source-path "src" ;lein1
  :java-source-paths ["src"]
  :keep-non-project-classes true
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.1.2"]
                 [slingshot "0.8.0"]
                 [net.java.dev.rome/rome "1.0.0"]
                 [com.redhat.qe/json-java "20110202"]
                 [org.jdom/jdom "1.1"]
                 [postgresql/postgresql "8.4-701.jdbc4"]
                 [com.redhat.qe/testng-listeners "1.0.0"]
                 [com.redhat.qe/ssh-tools "1.0.1-SNAPSHOT"]
                 [com.redhat.qe/assertions "1.0.2"]
                 [com.redhat.qe/bz-checker "1.0.3-SNAPSHOT"]
                 [com.redhat.qe/bugzilla-testng "1.0.4"]
                 [com.redhat.qe/verify-testng "1.0.0-SNAPSHOT"]
                 [org.uncommons/reportng "1.1.3"]
                 [gnome.ldtp "1.2.0-SNAPSHOT"
                  :exclusions [org.clojure/clojure]]
                 [test_clj.testng "1.0.1-SNAPSHOT"]
                 [clj-http "0.5.5"]
                 [matchure "0.10.1"]]
  ;lein1
  :dev-dependencies [[slamhound "1.2.0"]
                     [fn.trace "1.3.2.0-SNAPSHOT"]
                     [lein-eclipse "1.0.0"]]
  ;lein2
  :profiles {:dev {:dependencies
                   [[slamhound "1.2.0"]
                    [fn.trace "1.3.2.0-SNAPSHOT"]
                    [lein-eclipse "1.0.0"]]}}
  :repositories {"clojars.org" {:url "http://clojars.org/repo"
                                :snapshots {:update :always}}}
  :javac-options {:debug "on"})


(comment
  (do
    (use '[clojure.repl])
    (use '[clojure.pprint])
    (use '[slingshot.slingshot :only (try+ throw+)])
    (require '[clojure.tools.logging :as log])
    (do
      (require :reload-all '[rhsm.gui.tasks.test-config :as config])
      (require :reload-all '[rhsm.gui.tasks.tasks :as tasks])
      (require :reload-all '[rhsm.gui.tasks.candlepin-tasks :as ctasks])
      (require :reload-all '[rhsm.gui.tasks.rest :as rest])
      (require :reload-all '[rhsm.gui.tests.base :as base])
      (require :reload-all '[rhsm.gui.tests.subscribe-tests :as stest])
      (require :reload-all '[rhsm.gui.tests.register-tests :as rtest])
      (require :reload-all '[rhsm.gui.tests.proxy-tests :as ptest])
      (require :reload-all '[rhsm.gui.tests.rhn-interop-tests :as ritest])
      (require :reload-all '[rhsm.gui.tests.autosubscribe-tests :as atest])
      (require :reload-all '[rhsm.gui.tests.firstboot-tests :as fbtest])
      (require :reload-all '[rhsm.gui.tests.facts-tests :as ftest])
      (require :reload-all '[rhsm.gui.tests.acceptance-tests :as actest])
      (require :reload-all '[rhsm.gui.tests.import-tests :as itest])
      (require :reload-all '[rhsm.gui.tests.system-tests :as systest])

      (import '[rhsm.base SubscriptionManagerCLITestScript])
      )

    (let [cliscript (SubscriptionManagerCLITestScript.)]
      (.setupBeforeSuite cliscript))

    (do
      (config/init)
      (tasks/connect)
      (use 'gnome.ldtp))
    (log/info "INITIALIZATION COMPLETE!!")
    )     ;<< here for all of it

  ;not used
  (require :reload-all '[rhsm.gui.tests.subscription-assistant-tests :as satest])
)
