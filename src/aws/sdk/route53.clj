(ns aws.sdk.route53
  "Functions to access the Amazon Route 53 DNS service.

  Each function takes a map of credentials as its first argument. The
  credentials map should contain an :access-key key and a :secret-key
  key."

  (:import com.amazonaws.AmazonServiceException
           com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.route53.AmazonRoute53Client
           com.amazonaws.services.route53.model.Change
           com.amazonaws.services.route53.model.ChangeBatch
           com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
           com.amazonaws.services.route53.model.ResourceRecord
           com.amazonaws.services.route53.model.ResourceRecordSet
           com.amazonaws.AmazonServiceException
           )

  (:require [clojure.string :as string]))


(defn- route53-client*
  "Create an AmazonRoute53Client instance from a map of credentials."
  [cred]
  (let [client (AmazonRoute53Client.
                (BasicAWSCredentials.
                 (:access-key cred)
                 (:secret-key cred)))]
    client))

(def ^{:private true}
  route53-client
  (memoize route53-client*))


;;
;; convert object graphs to clojure maps
;;

(defprotocol ^{:no-doc true} Mappable
  "Convert a value into a Clojure map."
  (^{:no-doc true} to-map [x] "Return a map of the value."))

(extend-protocol Mappable nil (to-map [_] nil))


;;
;; convert clojure maps to object graphs

(defn- keyword-to-method
  "Convert a dashed keyword to a CamelCase method name"
  [kw]
  (apply str (map string/capitalize (string/split (name kw) #"-"))))

(defn set-fields
  "Use a map of params to call setters on a Java object"
  [obj params]
  (doseq [[k v] params]
    (let [method-name (str "set" (keyword-to-method k))
          method (first (clojure.lang.Reflector/getMethods (.getClass obj) 1 method-name false))
          arg-type (first (.getParameterTypes method))
          arg (if (= arg-type java.lang.Integer) (Integer. v) v)]
      (clojure.lang.Reflector/invokeInstanceMember method-name obj arg)))
  obj)

(declare mapper)

(defn map->ObjectGraph
  "Transform the map of params to a graph of AWS SDK objects"
  [params]
  (let [keys (keys params)]
    (zipmap keys (map #((mapper %) (params %)) keys))))

(defmacro mapper->
  "Creates a function that invokes set-fields on a new object of type
   with mapped parameters."
  [type]
  `(fn [~'params] (set-fields (new ~type) (map->ObjectGraph ~'params))))

;;
;; exceptions
;;

(extend-protocol Mappable
  AmazonServiceException
  (to-map [e]
    {:error-code   (.getErrorCode e)
     :error-type   (.name (.getErrorType e))
     :service-name (.getServiceName e)
     :status-code  (.getStatusCode e)}))

(defn decode-exceptions
  "Returns a Clojure map containing the details of an AmazonServiceException"
  [& exceptions]
  (map to-map exceptions))


;;
;; 
;; 

(defn change-resource-record-sets
  ""
  [cred zone-id changes]
 (.changeResourceRecordSets (route53-client cred) (ChangeResourceRecordSetsRequest. zone-id (ChangeBatch. changes))))



(defn create-change
  ""
  ([action resource-record-name resource-record-type value ]
     (Change. action (doto (ResourceRecordSet. resource-record-name resource-record-type)
                       (.setResourceRecords [ (ResourceRecord. value) ]))))
  ([action resource-params]
     (Change. action ((mapper-> ResourceRecordSet) resource-params))))


;; http://docs.aws.amazon.com/Route53/latest/DeveloperGuide/RRSchanges.html

;; http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/route53/AmazonRoute53Client.html

;; http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/route53/model/Change.html

;; http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/route53/model/ResourceRecordSet.html

;; http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/route53/model/ResourceRecord.html
